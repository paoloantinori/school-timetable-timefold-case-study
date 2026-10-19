import { test, expect } from '@playwright/test';

/**
 * View switch performance test.
 *
 * Measures the time to switch between Week and Timeline views.
 * The Timeline view uses react-window virtualization, so the main cost
 * is initial List creation + rendering ~3 visible day rows (each with
 * 14 columns × 8 periods = 112 cells - TASK-367, the 2026/27 seed has
 * 8 slots/day, not the old 6).
 *
 * We measure "time-to-interactive" by waiting for a minimum number of
 * DOM elements PLUS a requestAnimationFrame (paint confirmation), rather
 * than just the first DOM insertion.
 *
 * Realistic targets (with ~8,600 entities):
 *   Timeline → Week: < 400ms
 *   Week → Timeline: < 2000ms (DOM-bound; would need canvas for < 500ms)
 *
 * NOTE: row-count thresholds updated for TASK-367 (8 slots/day on the
 * 2026/27 seed, not the old 6), not executed in this session - no running
 * app instance was available (isolated worktree, task instructions forbid
 * starting the app). Run with `./scripts/ui/ui-test.sh view-switch-perf`
 * against a live dev server before merging.
 */
test.describe('View Switch Performance', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for the schedule to load
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000); // Let initial render settle
  });

  test('measure Week <-> Timeline view switching time', async ({ page }) => {
    const weekBtn = page.locator('button:has-text("Week")').first();
    const timelineBtn = page.locator('button:has-text("Timeline"), button:has-text("Infinite")').first();

    // Ensure we start in Week view
    await weekBtn.click();
    await page.waitForTimeout(500);

    // --- Timeline → Week ---
    console.log('\n=== Timeline -> Week Switch Times ===');
    const timelineToWeekTimes: number[] = [];

    for (let i = 0; i < 3; i++) {
      await timelineBtn.click();
      await page.waitForTimeout(500);

      const start = Date.now();
      await weekBtn.click();

      // Wait for full table render: >= 40 rows (5 days × 8 periods - TASK-367,
      // the 2026/27 seed has 8 slots/day, not the old 6) AND a paint frame via
      // requestAnimationFrame
      await page.waitForFunction(() => {
        const rows = document.querySelectorAll('table tbody tr');
        if (rows.length < 40) return false;
        // Confirm paint has occurred
        return new Promise<boolean>(resolve => {
          requestAnimationFrame(() => resolve(true));
        });
      }, { timeout: 10000 });

      const elapsed = Date.now() - start;
      timelineToWeekTimes.push(elapsed);
      console.log(`  Iteration ${i + 1}: ${elapsed}ms`);
    }

    // --- Week → Timeline ---
    console.log('\n=== Week -> Timeline Switch Times ===');
    const weekToTimelineTimes: number[] = [];

    for (let i = 0; i < 3; i++) {
      await weekBtn.click();
      await page.waitForFunction(() => {
        return document.querySelectorAll('table tbody tr').length >= 40;
      }, { timeout: 10000 });

      const start = Date.now();
      await timelineBtn.click();
      await page.waitForFunction(() => {
        const dayRows = document.querySelectorAll('[data-testid*="day-row"]');
        if (dayRows.length === 0) return false;
        return new Promise<boolean>(resolve => {
          requestAnimationFrame(() => resolve(true));
        });
      }, { timeout: 10000 });
      const elapsed = Date.now() - start;

      weekToTimelineTimes.push(elapsed);
      console.log(`  Iteration ${i + 1}: ${elapsed}ms`);
    }

    // --- Results ---
    const avgTimelineToWeek = timelineToWeekTimes.reduce((a, b) => a + b, 0) / timelineToWeekTimes.length;
    const avgWeekToTimeline = weekToTimelineTimes.reduce((a, b) => a + b, 0) / weekToTimelineTimes.length;

    console.log('\n=== Summary ===');
    console.log(`  Timeline -> Week: avg ${avgTimelineToWeek.toFixed(0)}ms`);
    console.log(`  Week -> Timeline: avg ${avgWeekToTimeline.toFixed(0)}ms`);

    // Targets (with populated calendar)
    const T2W_TARGET = 400;
    const W2T_TARGET = 2000;

    console.log(`\nTargets: Timeline->Week <${T2W_TARGET}ms | Week->Timeline <${W2T_TARGET}ms`);
    console.log(`Timeline -> Week: ${avgTimelineToWeek < T2W_TARGET ? 'PASS' : 'FAIL'} (${avgTimelineToWeek.toFixed(0)}ms)`);
    console.log(`Week -> Timeline: ${avgWeekToTimeline < W2T_TARGET ? 'PASS' : 'FAIL'} (${avgWeekToTimeline.toFixed(0)}ms)`);

    // Assert targets
    expect(avgTimelineToWeek, `Timeline->Week avg ${avgTimelineToWeek.toFixed(0)}ms exceeds ${T2W_TARGET}ms`).toBeLessThan(T2W_TARGET);
    // Week->Timeline target is intentionally generous; DOM-based rendering is bounded at ~1.5-2s
    expect(avgWeekToTimeline, `Week->Timeline avg ${avgWeekToTimeline.toFixed(0)}ms exceeds ${W2T_TARGET}ms`).toBeLessThan(W2T_TARGET);
  });
});
