import { test, expect } from '@playwright/test';
import { ensureUnpinnedLessonExists, cleanupSolverFixtures } from './helpers';

/**
 * Solver and Pre-assignment State Transition Tests
 *
 * Comprehensive tests for state transitions, button states, toast timing,
 * and fast polling behavior in the UnifiedSolverControls component.
 *
 * TASK-367: these tests must be robust to either the fresh 2026/27 seed
 * (all Lessons unpinned) or the golden-imported profile (100% pinned - the
 * solver has nothing to move and settles into NOT_SOLVING almost instantly).
 * ensureUnpinnedLessonExists() (see ./helpers.ts) creates a throwaway
 * course + Lessons via the API so "Solve" always has at least one entity to
 * work on, regardless of profile state. Separately, the pre-assign flows now
 * expect status COMPLETE only: on this seed the LabRotation table is empty,
 * so HybridPhase1aService.run() completes as a clean no-op (zero pinned)
 * instead of ERROR - see that guard in HybridPhase1aService.
 *
 * NOTE: rewritten for TASK-367, not executed in this session - no running
 * app instance was available (isolated worktree, task instructions forbid
 * starting the app). Run with `./scripts/ui/ui-test.sh solver-state-transitions`
 * against a live dev server before merging.
 */
test.describe('Solver State Transitions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000);
    await ensureUnpinnedLessonExists(page);

    // Additional cleanup: ensure we start from NOT_SOLVING state
    try {
      const response = await page.evaluate(async () => {
        const res = await fetch('/api/schedule');
        return await res.json();
      });
      if (response.solverStatus === 'SOLVING_ACTIVE') {
        await page.evaluate(async () => {
          await fetch('/api/schedule/solve', { method: 'DELETE' });
        });
        await page.waitForTimeout(1500);
      }
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.afterEach(async ({ page }) => {
    // Clean up: stop any running solver after each test
    try {
      const response = await page.evaluate(async () => {
        const res = await fetch('/api/schedule');
        return await res.json();
      });
      if (response.solverStatus === 'SOLVING_ACTIVE') {
        await page.evaluate(async () => {
          await fetch('/api/schedule/solve', { method: 'DELETE' });
        });
        await page.waitForTimeout(1000);
      }
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.describe('Solve Button - Initial State', () => {
    test('solve button is enabled when NOT_SOLVING', async ({ page }) => {
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await expect(solveBtn).toBeEnabled();
    });

    test('status badge shows "not solving" when NOT_SOLVING', async ({ page }) => {
      const statusBadge = page.locator('[role="status"]').first();
      const text = await statusBadge.textContent();
      expect(text?.toLowerCase()).toContain('not solving');
    });
  });

  test.describe('Solve Button - Fast Polling State', () => {
    test('clicking solve updates status after fast poll', async ({ page }) => {
      const solveBtn = page.locator('button:has-text("Solve")').first();
      const statusBadge = page.locator('[role="status"]').first();

      // Get initial status text
      const initialText = await statusBadge.textContent();
      expect(initialText?.toLowerCase()).toContain('not solving');

      // Click solve
      await solveBtn.click();

      // With fast polling (500ms), status should update within ~1 second
      // Wait for backend to confirm SOLVING_ACTIVE and UI to update
      await page.waitForTimeout(1000);
      const newText = await statusBadge.textContent();
      expect(newText?.toLowerCase()).not.toContain('not solving');
      // Status shows phase info (e.g., "1. ch - labblocks") or solving state
      expect(newText?.toLowerCase()).toMatch(/\d+\.|phase|construction|local|solving/i);
    });

    test('solve started toast appears immediately', async ({ page }) => {
      const solveBtn = page.locator('button:has-text("Solve")').first();

      // Click solve and check for toast within 1 second
      await solveBtn.click();

      const toast = page.locator('[role="alert"]').filter({ hasText: /Solver started/i }).first();
      await expect(toast).toBeVisible({ timeout: 2000 });
    });
  });

  test.describe('Solve Button - Transition to SOLVING_ACTIVE', () => {
    test('button changes to Stop when SOLVING_ACTIVE', async ({ page }) => {
      const solveBtn = page.locator('button:has-text("Solve")').first();

      await solveBtn.click();

      // Wait for server status to update
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule/status');
        const data = await response.json();
        return data.solverStatus === 'SOLVING_ACTIVE';
      }, { timeout: 5000 });

      // Should now show Stop button, not Solve
      const stopBtn = page.locator('button:has-text("Stop")').first();
      await expect(stopBtn).toBeVisible();

      const solveBtnAfter = page.locator('button:has-text("Solve")').first();
      await expect(solveBtnAfter).not.toBeVisible();
    });
  });

  test.describe('Solve Button - Transition to NOT_SOLVING', () => {
    test('stopping solver shows Solve button again', async ({ page }) => {
      // Start solver
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for SOLVING_ACTIVE (light endpoint - the full /api/schedule takes
      // ~9s on this dataset, longer than the old 5s wait itself, TASK-380)
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule/status');
        const data = await response.json();
        return data.solverStatus === 'SOLVING_ACTIVE';
      }, { timeout: 15000 });

      // Stop it
      const stopBtn = page.locator('button:has-text("Stop")').first();
      await stopBtn.click();

      // Wait for NOT_SOLVING
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule/status');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 30000 });

      // Solve button should reappear
      const solveBtnAfter = page.locator('button:has-text("Solve")').first();
      await expect(solveBtnAfter).toBeVisible();
    });

    test('solving complete toast appears after transition to NOT_SOLVING', async ({ page }) => {
      // Start solver briefly
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for SOLVING_ACTIVE
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule/status');
        const data = await response.json();
        return data.solverStatus === 'SOLVING_ACTIVE';
      }, { timeout: 5000 });

      // Stop it
      const stopBtn = page.locator('button:has-text("Stop")').first();
      await stopBtn.click();

      // Wait for NOT_SOLVING and toast
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule/status');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 5000 });

      // Check for completion toast (Solving complete or Solver stopped)
      const toast = page.locator('[role="alert"]').filter({ hasText: /Solving complete|Solver stopped/i }).first();
      await expect(toast).toBeVisible({ timeout: 3000 });
    });
  });
});

test.describe('Pre-assign Button State Transitions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000);

    // Additional cleanup: ensure we start from NOT_SOLVING state
    try {
      const response = await page.evaluate(async () => {
        const res = await fetch('/api/schedule');
        return await res.json();
      });
      if (response.solverStatus === 'SOLVING_ACTIVE') {
        await page.evaluate(async () => {
          await fetch('/api/schedule/solve', { method: 'DELETE' });
        });
        await page.waitForTimeout(1500);
      }
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.afterEach(async ({ page }) => {
    // Clean up: ensure pre-assign is stopped and reset to IDLE
    try {
      // First, wait for any running pre-assign to complete (up to 60s for long runs).
      // TASK-367: zero-rotation pre-assignment is a clean no-op (status COMPLETE),
      // never ERROR - see HybridPhase1aService.run()'s LabRotation.count() == 0 guard.
      await page.waitForFunction(async () => {
        const res = await fetch('/api/schedule/pre-assign/status');
        const data = await res.json();
        return data.status === 'COMPLETE' || data.status === 'IDLE';
      }, { timeout: 60000 });

      // Then reset to clear COMPLETE state
      await page.evaluate(async () => {
        await fetch('/api/schedule/pre-assign/reset', { method: 'POST' });
      });

      // Wait for IDLE status after reset (up to 5 seconds)
      await page.waitForFunction(async () => {
        const res = await fetch('/api/schedule/pre-assign/status');
        const data = await res.json();
        return data.status === 'IDLE';
      }, { timeout: 5000 });
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test('pre-assign button is enabled when IDLE', async ({ page }) => {
    // Reset first to ensure IDLE state
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();

    // Wait for backend status to become IDLE after reset (the reset itself is
    // a bulk update over 10.5k rows - give it an honest budget, TASK-367)
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'IDLE';
    }, { timeout: 60000 });

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    // The UI reflects backend state within a light-status poll cycle (TASK-380)
    await expect(preAssignBtn).toBeEnabled({ timeout: 15000 });
  });

  test('pre-assign button disabled when COMPLETE', async ({ page }) => {
    // Run pre-assign first
    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.click();

    // Wait for COMPLETE status on backend
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'COMPLETE';
    }, { timeout: 120000 });

    // Reload for a deterministic fresh mount carrying the COMPLETE status -
    // this tests the actual invariant (COMPLETE => disabled) without depending
    // on the in-page poll window (TASK-367)
    await page.reload();
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 60000 });
    await expect(preAssignBtn).toBeDisabled({ timeout: 30000 });
    const title = await preAssignBtn.getAttribute('title');
    expect(title).toMatch(/complete|reset/i);
  });

  test('pre-assign started toast appears immediately', async ({ page }) => {
    // Reset to IDLE first
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();
    // Wait for backend status to become IDLE after reset
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'IDLE';
    }, { timeout: 60000 });
    await page.waitForTimeout(500); // Give React time to re-render

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.click();

    // Toast should appear quickly
    const toast = page.locator('[role="alert"]').filter({ hasText: /Pre-assignment started/i }).first();
    await expect(toast).toBeVisible({ timeout: 2000 });
  });

  test('pre-assign toast appears after completion', async ({ page }) => {
    // Reset to IDLE first
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();
    // Wait for backend status to become IDLE after reset
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'IDLE';
    }, { timeout: 60000 });
    await page.waitForTimeout(500); // Give React time to re-render

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.click();

    // Wait for completion (always COMPLETE - see TASK-367 header note)
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'COMPLETE';
    }, { timeout: 120000 });

    // Wait for toast to appear and animation to complete
    await page.waitForTimeout(3000);

    // Check for appropriate toast based on status
    const finalStatus = await page.evaluate(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      return await response.json();
    });

    // Check for any toast with pre-assign text (success or error)
    const toast = page.locator('[role="alert"]').filter({ hasText: /Pre-assign/i }).first();
    await expect(toast, 'Pre-assignment toast should be visible').toBeVisible({ timeout: 5000 });
  });
});

test.describe('Button Disabled States During Active Operations', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000);
    await ensureUnpinnedLessonExists(page);

    // Additional cleanup: ensure we start from NOT_SOLVING state
    try {
      const response = await page.evaluate(async () => {
        const res = await fetch('/api/schedule');
        return await res.json();
      });
      if (response.solverStatus === 'SOLVING_ACTIVE') {
        await page.evaluate(async () => {
          await fetch('/api/schedule/solve', { method: 'DELETE' });
        });
        await page.waitForTimeout(1500);
      }
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.afterEach(async ({ page }) => {
    // Clean up: stop solver and pre-assign after each test
    try {
      await page.evaluate(async () => {
        await fetch('/api/schedule/solve', { method: 'DELETE' });
        await fetch('/api/schedule/pre-assign/reset', { method: 'POST' });
      });
      await page.waitForTimeout(1000);
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test('solve button disabled during pre-assignment', async ({ page }) => {
    // Reset to IDLE first (previous test may have left pre-assign in COMPLETE state)
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();
    await page.waitForTimeout(2000);

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.click();

    // Wait for pre-assign to start
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'RUNNING' || data.status === 'COMPLETE';
    }, { timeout: 10000 });

    const solveBtn = page.locator('button:has-text("Solve")').first();
    const isDisabled = await solveBtn.isDisabled();
    const title = await solveBtn.getAttribute('title');

    expect(isDisabled).toBe(true);
    expect(title).toMatch(/pre-assign/i);
  });

  test('pre-assign button disabled during solving', async ({ page }) => {
    // Reset first to clear any COMPLETE state from previous test
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();
    // Wait for backend status to become IDLE after reset
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'IDLE';
    }, { timeout: 60000 });
    await page.waitForTimeout(500); // Give React time to re-render

    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();

    // Wait for SOLVING_ACTIVE via the light status endpoint (TASK-380)
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/status');
      const data = await response.json();
      return data.solverStatus === 'SOLVING_ACTIVE';
    }, { timeout: 15000 });

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    // Wait for the UI to observe SOLVING via its status poll (TASK-380)
    await expect(preAssignBtn).toBeDisabled({ timeout: 15000 });
    const title = await preAssignBtn.getAttribute('title');
    expect(title).toMatch(/solving/i);
  });

  test('reset button disabled during active operations', async ({ page }) => {
    // Ensure clean state - stop any running solver
    try {
      await page.evaluate(async () => {
        await fetch('/api/schedule/solve', { method: 'DELETE' });
      });
      await page.waitForTimeout(500);
    } catch (e) {
      // Ignore if solver wasn't running
    }

    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();

    // Wait for SOLVING_ACTIVE via the light status endpoint (TASK-380)
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/status');
      const data = await response.json();
      return data.solverStatus === 'SOLVING_ACTIVE';
    }, { timeout: 15000 });

    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    // The UI reflects the solving state within a status-poll cycle
    await expect(resetBtn).toBeDisabled({ timeout: 15000 });
  });
});

test.describe('Toast Timing and Sequencing', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000);
    await ensureUnpinnedLessonExists(page);

    // Additional cleanup: ensure we start from NOT_SOLVING state
    try {
      const response = await page.evaluate(async () => {
        const res = await fetch('/api/schedule');
        return await res.json();
      });
      if (response.solverStatus === 'SOLVING_ACTIVE') {
        await page.evaluate(async () => {
          await fetch('/api/schedule/solve', { method: 'DELETE' });
        });
        await page.waitForTimeout(1500);
      }
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.afterEach(async ({ page }) => {
    // Clean up: stop solver and pre-assign after each test
    try {
      await page.evaluate(async () => {
        await fetch('/api/schedule/solve', { method: 'DELETE' });
        await fetch('/api/schedule/pre-assign/reset', { method: 'POST' });
      });
      await page.waitForTimeout(1000);
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test('no duplicate solve started toasts', async ({ page }) => {
    const toastMessages: string[] = [];

    // Listen for all toast appearances
    page.on('console', msg => {
      if (msg.type() === 'log') {
        const text = msg.text();
        if (text.includes('toast:') || text.includes('Toast:')) {
          toastMessages.push(text);
        }
      }
    });

    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();

    // Wait a bit and check we only get one started toast
    await page.waitForTimeout(2000);

    const startedToasts = toastMessages.filter(m => m.includes('started'));
    expect(startedToasts.length).toBeLessThanOrEqual(1);
  });

  test('pre-assign toasts appear in correct order', async ({ page }) => {
    // Listen for DOM mutations to capture toast order
    await page.evaluate(() => {
      const observer = new MutationObserver((mutations) => {
        mutations.forEach(mutation => {
          mutation.addedNodes.forEach((node: any) => {
            if (node.nodeType === 1) {
              const toast = node.closest?.('[role="alert"]') || node.querySelector?.('[role="alert"]');
              if (toast) {
                const text = toast.textContent || '';
                (window as any).toastOrder = (window as any).toastOrder || [];
                if (text.includes('Pre-assign')) {
                  (window as any).toastOrder.push(text);
                }
              }
            }
          });
        });
      });

      observer.observe(document.body, { childList: true, subtree: true });
    });

    // Reset to IDLE first
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.click();
    // Wait for backend status to become IDLE after reset
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'IDLE';
    }, { timeout: 60000 });
    await page.waitForTimeout(500); // Give React time to re-render

    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.click();

    // Wait for completion
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'COMPLETE';
    }, { timeout: 120000 });

    await page.waitForTimeout(2000);

    const toastOrder = await page.evaluate(() => (window as any).toastOrder || []);

    // "started" should come before "complete"
    const startedIndex = toastOrder.findIndex(t => t.toLowerCase().includes('started'));
    const completeIndex = toastOrder.findIndex(t => t.toLowerCase().includes('complete'));

    if (startedIndex !== -1 && completeIndex !== -1) {
      expect(startedIndex).toBeLessThan(completeIndex);
    }
  });
});

test.describe('Optimistic UI Reverts on Error', () => {
  test.skip('solve button state reverts if API fails', async ({ page }) => {
    // SKIPPED: Optimistic UI state was removed - no longer relevant
    // Error handling now relies purely on backend state + fast polling

    // Mock API to fail
    await page.route('**/api/schedule/solve', route => route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'Internal server error' })
    }));

    const solveBtn = page.locator('button:has-text("Solve")').first();
    const statusBadge = page.locator('[role="status"]').first();

    await solveBtn.click();

    // Initially shows "solving" (optimistic)
    await page.waitForTimeout(100);
    let text = await statusBadge.textContent();
    expect(text?.toLowerCase()).not.toContain('not solving');

    // Should revert to "not solving" after error
    await page.waitForTimeout(1000);
    text = await statusBadge.textContent();
    expect(text?.toLowerCase()).toContain('not solving');

    // Clean up mock
    await page.unroute('**/api/schedule/solve');
  });

test.afterAll(async ({ browser }) => {
  const page = await browser.newPage();
  try { await cleanupSolverFixtures(page); } finally { await page.close(); }
});
});
