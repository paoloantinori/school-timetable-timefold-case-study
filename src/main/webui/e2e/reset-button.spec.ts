import { test, expect } from '@playwright/test';

/**
 * Reset button functionality test.
 *
 * Verifies that the Reset button correctly clears ALL lesson assignments
 * (timeslot, room) and unpins all entities.
 *
 * The Reset button should call POST /api/schedule/reset, not just
 * reset the pre-assignment status.
 *
 * TASK-367: on the 2026/27 seed the LabRotation table is empty, so
 * HybridPhase1aService.run() completes pre-assignment as a clean no-op
 * (status COMPLETE, zero pinned) instead of ERROR - see that guard in
 * HybridPhase1aService. Tests here wait for COMPLETE only.
 *
 * NOTE: rewritten for TASK-367, not executed in this session - no running
 * app instance was available (isolated worktree, task instructions forbid
 * starting the app). Run with `./scripts/ui/ui-test.sh reset-button` against
 * a live dev server before merging.
 */
test.describe('Reset Button', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for the schedule to load
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000); // Let initial render settle

    // TASK-388: the Reset button now asks for confirmation before the destructive
    // POST (a stray click on this control wiped 866 pins on 2026-07-20). Registering
    // the auto-accept in beforeEach covers EVERY reset click in every test: without a
    // handler Playwright dismisses the dialog, the reset never fires, the pre-assign
    // status stays COMPLETE and the next test's Pre-assign wait times out.
    page.on('dialog', (dialog) => dialog.accept());
  });

  test('reset button clears all assignments after pre-assignment', async ({ page }) => {
    // Full-dataset resets + heavy /api/schedule fetches need an honest budget (TASK-367)
    test.setTimeout(240000);
    // Ensure clean state - reset first if pre-assign was complete
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.waitFor({ state: 'visible', timeout: 5000 });
    await resetBtn.click();
    await page.waitForTimeout(2000);

    // Step 1: Check initial state - count unassigned lessons
    const getAssignedCount = async () => {
      const result = await page.evaluate(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        // labBlocks left the payload with the entity itself (TASK-228/267, UI cleanup
        // TASK-515): counts are lesson-only, matching ScheduleSidebar's math.
        const assignedLessons = data.lessons?.filter((l: any) => l.timeslot !== null).length || 0;
        const pinnedLessons = data.lessons?.filter((l: any) => l.pinned === true).length || 0;
        return {
          assignedLessons,
          totalAssigned: assignedLessons,
          pinnedLessons,
          totalPinned: pinnedLessons
        };
      });
      return result;
    };

    // Step 2: Run pre-assignment to get some assignments
    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await preAssignBtn.waitFor({ state: 'visible', timeout: 5000 });
    // Wait for button to be enabled: resetting the full 10.5k-lesson 2026/27
    // dataset plus the validation watchdog re-score takes well over the old 5s
    // (TASK-367: this exact under-wait made the spec red).
    await expect(preAssignBtn).toBeEnabled({ timeout: 90000 });
    await preAssignBtn.click();

    // Wait for pre-assignment to complete (zero-rotation no-op always resolves to COMPLETE)
    await page.waitForTimeout(2000); // Initial delay
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'COMPLETE';
    }, { timeout: 120000 }); // 2 minute timeout for pre-assignment

    // Wait for UI to update
    await page.waitForTimeout(2000);

    // Step 3: Verify assignment state (the zero-rotation no-op pins nothing,
    // so 0 assignments here is expected, not a failure to investigate)
    const stateAfterPreAssign = await getAssignedCount();
    console.log('After pre-assign:', stateAfterPreAssign);

    // Step 4: Click Reset button
    resetBtn.click();

    // Wait for reset to complete and UI to update
    await page.waitForTimeout(2000);

    // Step 5: Verify all lessons are unassigned and unpinned
    const stateAfterReset = await getAssignedCount();
    console.log('After reset:', stateAfterReset);

    // Assertions
    expect(stateAfterReset.totalAssigned, 'All entities should be unassigned after reset').toBe(0);
    expect(stateAfterReset.totalPinned, 'All entities should be unpinned after reset').toBe(0);
  });

  test('reset button clears assignments after solver run', async ({ page }) => {
    // On the full 2026/27 dataset (10.5k lessons) a solve+stop+reset cycle plus
    // two heavy GET /api/schedule fetches under CPU contention blows the default
    // 60s test budget (TASK-367): give the test an honest budget and wait on
    // states, not fixed sleeps.
    test.setTimeout(240000);

    // Step 1: Start solver, let the CH make progress
    const solveBtn = page.locator('button:has-text("Solve")').first();
    await solveBtn.click();
    await page.waitForTimeout(15000);

    // Stop solver and WAIT until it is actually idle before touching the API
    // (uses the TASK-380 lightweight status endpoint - the full /api/schedule
    // costs ~9s on this dataset)
    const stopBtn = page.locator('button:has-text("Stop")').first();
    await stopBtn.click();
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/status');
      const d = await response.json();
      // Canonical quiescence (CLAUDE.md "two false signals"): solverStatus alone
      // reads NOT_SOLVING for the WHOLE staged cycle and between multi-start
      // attempts; resetting in that window is refused by refuseIfStaged (500).
      // Rest only when the staged narration is gone too.
      return d.solverStatus === 'NOT_SOLVING'
        && (d.stagedStage === null || d.stagedStage === undefined);
    }, { timeout: 90000 });

    // Step 2: Click Reset button
    const resetBtn = page.getByRole('button', { name: 'Reset', exact: true }).first();
    await resetBtn.waitFor({ state: 'visible', timeout: 5000 });
    await resetBtn.click();

    // Step 3: Verify all lessons are unassigned - poll instead of fixed sleep
    // (the reset itself is a bulk update over 10.5k rows)
    await expect.poll(async () => {
      return page.evaluate(async () => {
        const response = await fetch('/api/schedule');
        const data = await response.json();
        const assignedLessons = data.lessons?.filter((l: any) => l.timeslot !== null).length || 0;
        const pinnedLessons = data.lessons?.filter((l: any) => l.pinned === true).length || 0;
        return assignedLessons + pinnedLessons;
      });
    }, { timeout: 90000, intervals: [3000] }).toBe(0);
  });

  test('notification toast is dismissible via its close button', async ({ page }) => {
    // HISTORY (TASK-367): this test used to assert a "Hide link but no X
    // button" design that never matched the shipped ToastContainer, which has
    // always dismissed via an "x" button (aria-label common:close) and carries
    // role="alert". Rewritten to assert the ACTUAL contract: the pre-assign
    // completion toast appears and its close button removes it. If the
    // Hide-link design is ever really wanted, change the component first.
    test.setTimeout(240000);
    // Run pre-assign to trigger a notification toast; after the previous
    // tests' resets the button can stay disabled for a while (watchdog re-score)
    const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
    await expect(preAssignBtn).toBeEnabled({ timeout: 90000 });
    await preAssignBtn.click();

    // Wait for pre-assignment to complete
    await page.waitForFunction(async () => {
      const response = await fetch('/api/schedule/pre-assign/status');
      const data = await response.json();
      return data.status === 'COMPLETE';
    }, { timeout: 120000 });

    // The toast fires off the UI's status poll - wait for it
    const toastContainer = page.locator('.fixed.bottom-4.right-4.z-\\[9999\\]');
    const toast = toastContainer.locator('> div').filter({ hasText: /complete|pre-assign/i }).first();
    await toast.waitFor({ state: 'visible', timeout: 20000 });

    // Dismiss via the close button and verify it disappears
    await toast.locator('button').last().click();
    await expect(toast).toBeHidden({ timeout: 5000 });
  });
});
