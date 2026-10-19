import { test, expect } from '@playwright/test';
import { ensureUnpinnedLessonExists, cleanupSolverFixtures } from './helpers';

/**
 * Phase Progression Completion Tests (TASK-246)
 *
 * Tests that verify all phases are marked as complete when the solver
 * terminates naturally (not via the Stop button).
 *
 * Bug: When the solver ended naturally, the last phase remained marked
 * as "in progress" in the UI because finalizeCurrentPhase() was only
 * called by stopSolving(), not by the natural termination callback.
 *
 * Fix: Added saveFinalSolution() method that calls finalizeCurrentPhase()
 * and wired it to withFinalBestSolutionConsumer.
 *
 * TASK-367: these tests launch a real solve, so they must work on either the
 * fresh 2026/27 seed (all Lessons unpinned) or the golden-imported profile
 * (100% pinned - the solver naturally terminates almost instantly since
 * there's nothing left to move). ensureUnpinnedLessonExists() (see
 * ./helpers.ts) creates a throwaway course + Lessons via the API so there is
 * always at least one phase transition to observe.
 *
 * NOTE: rewritten for TASK-367, not executed in this session - no running
 * app instance was available (isolated worktree, task instructions forbid
 * starting the app). Run with `./scripts/ui/ui-test.sh phase-progression-complete`
 * against a live dev server before merging.
 */
test.describe('Phase Progression Completion', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000);
    await ensureUnpinnedLessonExists(page);
  });

  test.afterEach(async ({ page }) => {
    // Clean up: stop any running solver after each test
    try {
      await page.evaluate(async () => {
        await fetch('/api/schedule/solve', { method: 'DELETE' });
      });
      await page.waitForTimeout(1000);
    } catch (e) {
      // Ignore cleanup errors
    }
  });

  test.describe('Natural Solver Termination', () => {
    test('all phases marked complete after solver ends naturally', async ({ page }) => {
      // Start the solver
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for solver to become active
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'SOLVING_ACTIVE';
      }, { timeout: 5000 });

      // Wait for solver to complete naturally (NOT_SOLVING)
      // This may take up to ~8 minutes with default config, but we'll use a shorter
      // timeout since the solver may finish quickly on small datasets
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 300000 }); // 5 minute max wait

      // Wait for "Solving complete" toast to confirm natural termination
      const toast = page.locator('[role="alert"]').filter({ hasText: /Solving complete/i }).first();
      await expect(toast, 'Should see "Solving complete" toast').toBeVisible({ timeout: 5000 });

      // Click the Progression button to open the modal
      const progressionBtn = page.locator('button:has-text("Progression")').first();
      await progressionBtn.click();

      // Wait for modal to appear
      const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
      await expect(modal, 'Progression modal should appear').toBeVisible({ timeout: 5000 });

      // Verify all phases are complete (no "in progress" indicator)
      // Incomplete phases have class "bg-feedback-info-bg"
      // Complete phases have class "bg-surface-alt"
      const incompletePhases = modal.locator('.bg-feedback-info-bg.border-feedback-info-border').filter({
        has: modal.locator('text=/Phase \\d+/')
      });
      const incompleteCount = await incompletePhases.count();

      expect(incompleteCount, 'No phases should be marked as in progress after natural termination').toBe(0);

      // Verify we have phase data
      const phaseLabels = modal.locator('text=/Phase \\d+/');
      const phaseCount = await phaseLabels.count();
      expect(phaseCount, 'Should show phase data').toBeGreaterThan(0);

      // Verify "Live" badge is NOT shown (solver is not running)
      const liveBadge = modal.locator('text=/Live/i').filter({ hasText: /Live/i });
      const isLiveVisible = await liveBadge.isVisible().catch(() => false);
      expect(isLiveVisible, 'Live badge should not be visible when solver is not running').toBe(false);

      // Close modal
      const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
      await closeBtn.click();
    });

    test('phase data includes end time after natural termination', async ({ page }) => {
      // Start the solver
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for solver to complete naturally
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 300000 });

      // Wait for completion toast
      const toast = page.locator('[role="alert"]').filter({ hasText: /Solving complete/i }).first();
      await expect(toast).toBeVisible({ timeout: 5000 });

      // Open Progression modal
      const progressionBtn = page.locator('button:has-text("Progression")').first();
      await progressionBtn.click();

      const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
      await expect(modal).toBeVisible({ timeout: 5000 });

      // Verify at least the last phase has an end time
      // Phases show "End: HH:MM:SS" if they have an endTime
      const endTimeLabels = modal.locator('text=/End:\\s*\\d{1,2}:\\d{2}:\\d{2}/i');
      const endTimeCount = await endTimeLabels.count();

      expect(endTimeCount, 'At least one phase should have an end time').toBeGreaterThan(0);

      // Close modal
      const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
      await closeBtn.click();
    });
  });

  test.describe('Manual Stop (Regression Test)', () => {
    test('all phases marked complete after manual stop', async ({ page }) => {
      // Start the solver
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for solver to become active
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'SOLVING_ACTIVE';
      }, { timeout: 5000 });

      // Wait a bit for phases to progress
      await page.waitForTimeout(5000);

      // Manually stop the solver
      const stopBtn = page.locator('button:has-text("Stop")').first();
      await stopBtn.click();

      // Wait for NOT_SOLVING
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 5000 });

      // Wait for "Solver stopped" toast
      const toast = page.locator('[role="alert"]').filter({ hasText: /Solver stopped/i }).first();
      await expect(toast).toBeVisible({ timeout: 5000 });

      // Click the Progression button
      const progressionBtn = page.locator('button:has-text("Progression")').first();
      await progressionBtn.click();

      // Wait for modal
      const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
      await expect(modal).toBeVisible({ timeout: 5000 });

      // Verify all phases are complete (no "in progress" indicator)
      const incompletePhases = modal.locator('.bg-feedback-info-bg.border-feedback-info-border').filter({
        has: modal.locator('text=/Phase \\d+/')
      });
      const incompleteCount = await incompletePhases.count();

      expect(incompleteCount, 'No phases should be marked as in progress after manual stop').toBe(0);

      // Verify we have phase data
      const phaseLabels = modal.locator('text=/Phase \\d+/');
      const phaseCount = await phaseLabels.count();
      expect(phaseCount, 'Should show phase data').toBeGreaterThan(0);

      // Close modal
      const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
      await closeBtn.click();
    });
  });

  test.describe('API Verification', () => {
    test('phase progression API returns complete phases after natural termination', async ({ page }) => {
      // Start the solver
      const solveBtn = page.locator('button:has-text("Solve")').first();
      await solveBtn.click();

      // Wait for solver to complete naturally
      await page.waitForFunction(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        return data.solverStatus === 'NOT_SOLVING';
      }, { timeout: 300000 });

      // Fetch phase progression data via API
      const response = await page.request.get('http://localhost:8080/api/phase-progression');
      expect(response.status()).toBe(200);

      const phases = await response.json() as Array<{
        phaseNumber: number;
        phaseName: string;
        isComplete: boolean;
        endTime: string | null;
      }>;

      // Verify we have phase data
      expect(Array.isArray(phases)).toBe(true);
      expect(phases.length).toBeGreaterThan(0);

      // Verify ALL phases are marked as complete
      const incompletePhases = phases.filter(p => !p.isComplete);
      expect(incompletePhases.length).toBe(0);

      // Verify at least the last phase has an endTime
      const lastPhase = phases[phases.length - 1];
      expect(lastPhase.endTime).not.toBeNull();
    });
  });

test.afterAll(async ({ browser }) => {
  const page = await browser.newPage();
  try { await cleanupSolverFixtures(page); } finally { await page.close(); }
});
});
