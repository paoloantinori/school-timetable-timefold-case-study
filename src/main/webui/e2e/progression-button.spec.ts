import { test, expect } from '@playwright/test';
import { promises as fs } from 'fs';

/**
 * Progression button functionality test.
 *
 * Verifies that the "Progression" button opens the phase progression modal
 * and displays solver phase history data correctly.
 *
 * The progression modal shows:
 * - Timeline of solver phases (CH lesson placement, LS phases)
 * - Duration, start/end times, and score changes for each phase
 * - Export functionality for phase data as JSON
 *
 * TASK-367: the pre-assignment-completion test drives pre-assign to COMPLETE
 * itself rather than assuming it - on the 2026/27 seed, an empty LabRotation
 * table makes pre-assignment a clean no-op (status COMPLETE), never ERROR.
 *
 * NOTE: that test was rewritten for TASK-367, not executed in this session -
 * no running app instance was available (isolated worktree, task
 * instructions forbid starting the app). Run with
 * `./scripts/ui/ui-test.sh progression-button` against a live dev server
 * before merging.
 */
test.describe('Progression Button', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for the schedule to load
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000); // Let initial render settle
  });

  test('progression button opens modal (empty or populated state)', async ({ page }) => {
    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await expect(progressionBtn, 'Progression button should be visible').toBeVisible();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal, 'Progression modal should appear').toBeVisible({ timeout: 5000 });

    // Verify modal shows content (either empty state OR phase data)
    // Note: Phase data persists in memory across tests, so we accept either state
    const emptyState = modal.locator('text=/No phase data available/i');
    const phaseLabels = modal.locator('text=/Phase \\d+/');
    const hasEmptyState = await emptyState.isVisible().catch(() => false);
    const hasPhaseData = await phaseLabels.count() > 0;
    expect(hasEmptyState || hasPhaseData, 'Modal should show either empty state or phase data').toBe(true);

    // Verify Export button exists
    const exportBtn = modal.locator('button:has-text("Export")');
    await expect(exportBtn, 'Export button should be visible').toBeVisible();

    // Close modal
    const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
    await closeBtn.click();

    // Verify modal is closed
    await expect(modal, 'Modal should close after clicking X').toBeHidden({ timeout: 3000 });
  });

  test('progression button opens modal after solver run', async ({ page }) => {
    // Start solver briefly to generate phase data
    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();

    // Wait for solver to complete first phase (at least 30 seconds for phase completion)
    await page.waitForTimeout(30000);

    // Stop solver
    const stopBtn = page.locator('button:has-text("Stop")').first();
    await stopBtn.click();
    await page.waitForTimeout(2000);

    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal, 'Progression modal should appear').toBeVisible({ timeout: 5000 });

    // Verify phase data is displayed (not empty state)
    const emptyState = modal.locator('text=/No phase data available/i');
    const emptyStateVisible = await emptyState.isVisible().catch(() => false);
    expect(emptyStateVisible, 'Should NOT show empty state after solver run').toBe(false);

    // Verify at least one phase is shown
    const phaseLabels = modal.locator('text=/Phase \\d+/');
    const phaseCount = await phaseLabels.count();
    expect(phaseCount, 'Should show at least one phase').toBeGreaterThan(0);

    // Verify Export button exists
    const exportBtn = modal.locator('button:has-text("Export")');
    await expect(exportBtn, 'Export button should be visible').toBeVisible();

    // Close modal by clicking overlay
    const overlay = page.locator('.fixed.inset-0.bg-overlay');
    await overlay.click({ position: { x: 0, y: 0 } }); // Click overlay itself

    // Verify modal is closed
    await expect(modal, 'Modal should close after clicking overlay').toBeHidden({ timeout: 3000 });
  });

  test('progression modal closes with Escape key', async ({ page }) => {
    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal, 'Progression modal should appear').toBeVisible({ timeout: 5000 });

    // Press Escape to close
    await page.keyboard.press('Escape');

    // Verify modal is closed
    await expect(modal, 'Modal should close after Escape key').toBeHidden({ timeout: 3000 });
  });

  test('API endpoint returns valid JSON', async ({ page }) => {
    // Direct API test to verify the endpoint works
    const response = await page.request.get('http://localhost:8080/api/phase-progression');

    expect(response.status(), 'Phase progression API should return 200').toBe(200);
    expect(response.headers()['content-type'], 'Should return JSON').toContain('application/json');

    const data = await response.json();
    expect(Array.isArray(data), 'Response should be an array').toBe(true);
  });

  test('export button downloads JSON file', async ({ page }) => {
    // Start download listener
    const downloadPromise = page.waitForEvent('download', { timeout: 30000 });

    // Click Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Click Export button
    const exportBtn = modal.locator('button:has-text("Export")');
    await exportBtn.click();

    // Wait for download
    const download = await downloadPromise;

    // Verify download
    expect(download.suggestedFilename(), 'File should be JSON').toMatch(/phase-progression.*\.json/);

    // Read and verify content
    const path = await download.path();
    const content = await fs.readFile(path as string, 'utf-8');
    const parsed = JSON.parse(content);
    expect(Array.isArray(parsed), 'Downloaded JSON should contain array').toBe(true);
  });

  test('timeout configuration is displayed', async ({ page }) => {
    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Verify overall limit is shown in summary section
    const overallLimitLabel = modal.locator('text=/Overall Limit/i');
    await expect(overallLimitLabel, 'Overall limit label should be visible').toBeVisible();

    // The overall limit value should be displayed (e.g., "480m 0s" for 28800 seconds, or "8h 0s")
    const timePattern = /\d+[hms]\s+\d+[hms]/;
    const timeDisplay = modal.locator('.text-lg.font-bold, .font-bold.text-lg').filter({ hasText: timePattern });
    // If not found with class selector, try broader search
    const hasTimeDisplay = await timeDisplay.count() > 0;
    if (!hasTimeDisplay) {
      // Try finding any element with the time pattern in the modal
      const fallbackTimeDisplay = modal.locator('div, span').filter({ hasText: timePattern }).first();
      await expect(fallbackTimeDisplay, 'Overall limit time should be displayed (fallback)').toBeVisible();
    } else {
      await expect(timeDisplay.first(), 'Overall limit time should be displayed').toBeVisible();
    }

    // Close modal
    const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
    await closeBtn.click();
  });

  test('per-phase timeout limits are displayed after solver run', async ({ page }) => {
    // Start solver briefly to generate phase data with timeouts
    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();

    // Wait for solver to complete first phase with timeout data (30 seconds for phase completion)
    await page.waitForTimeout(30000);

    // Stop solver
    const stopBtn = page.locator('button:has-text("Stop")').first();
    await stopBtn.click();
    await page.waitForTimeout(2000);

    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal).toBeVisible({ timeout: 5000 });

    // Check for timeout display in phase cards
    // LS phases (2, 3, 5 in the 5-phase pipeline, TASK-378) should have explicit timeout configs
    const limitLabel = modal.locator('text=/Limit:/i');
    const unimprovedLabel = modal.locator('text=/Unimproved:/i');

    // At least one of these should be visible for the LS phases (2/3/5)
    const hasLimit = await limitLabel.count() > 0;
    const hasUnimproved = await unimprovedLabel.count() > 0;

    expect(hasLimit || hasUnimproved, 'Phase cards should show timeout limits for LS phases').toBe(true);

    // Close modal
    const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
    await closeBtn.click();
  });

  test('progression shows pre-assignment completion message', async ({ page }) => {
    // TASK-367: drive pre-assignment to COMPLETE deterministically instead of
    // relying on incidental state left over from earlier tests. The
    // zero-rotation no-op (LabRotation empty on the 2026/27 seed) always
    // resolves to COMPLETE - see HybridPhase1aService.run().
    await page.evaluate(async () => {
      await fetch('/api/schedule/pre-assign/reset', { method: 'POST' });
      await fetch('/api/schedule/pre-assign', { method: 'POST' });
    });
    await page.waitForFunction(async () => {
      const res = await fetch('/api/schedule/pre-assign/status');
      const data = await res.json();
      return data.status === 'COMPLETE';
    }, { timeout: 60000 });

    // Click the Progression button
    const progressionBtn = page.locator('button:has-text("Progression")').first();
    await progressionBtn.click();

    // Wait for modal to appear
    const modal = page.locator('[role="dialog"]').filter({ hasText: 'Phase Progression' });
    await expect(modal, 'Progression modal should appear').toBeVisible({ timeout: 5000 });

    // Take a screenshot to see what's actually displayed
    await modal.screenshot({ path: 'e2e/screenshots/progression-after-preassign.png' });

    const preAssignCompleteMessage = modal.locator('text=/Pre-assignment completed/i');
    await expect(preAssignCompleteMessage, 'Should show "Pre-assignment completed" message').toBeVisible({ timeout: 5000 });

    // Verify the "Start the solver" message is also shown
    const startSolverMessage = modal.locator('text=/Start the solver/i');
    await expect(startSolverMessage, 'Should show "Start the solver" message').toBeVisible();

    // Close modal
    const closeBtn = modal.locator('button[aria-label="Close"], button:has-text("×")').first();
    await closeBtn.click();
  });
});
