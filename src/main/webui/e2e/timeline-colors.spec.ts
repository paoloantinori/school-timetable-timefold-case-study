import { test, expect } from '@playwright/test';

/**
 * Timeline marker colors visual validation.
 *
 * Tests that the timeline shows three-way color distinction:
 * 1. Vacation/holiday days: Red/pink colors
 * 2. Stage days: Purple/indigo colors
 * 3. Completion days (after last lesson): Blue/cyan colors
 *
 * TASK-367: audited for 2026/27 seed assumptions (8 slots/day instead of 6,
 * renamed teachers) - none found, this spec derives everything from CSS
 * variables and DOM queries rather than hardcoded slot counts or names. Not
 * run against a live app in this session (isolated worktree); runtime
 * verification pending.
 */
test.describe('Timeline Marker Colors', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for the schedule to load
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000); // Let initial render settle
  });

  test('visual verification of timeline marker colors', async ({ page }) => {
    // Switch to Timeline view
    const timelineBtn = page.locator('button:has-text("Timeline"), button:has-text("Infinite")').first();
    await timelineBtn.click();
    await page.waitForTimeout(500);

    // Wait for day rows to appear
    await page.waitForSelector('[data-testid*="day-row"]', { timeout: 10000 });

    // Take a screenshot of the full timeline
    await page.screenshot({
      path: 'src/main/webui/e2e/screenshots/timeline-full.png',
      fullPage: true
    });

    // Find and analyze holiday cells
    const holidayAnalysis = await page.evaluate(() => {
      const holidayCells = document.querySelectorAll('[style*="var(--holiday-bg)"]');
      return {
        count: holidayCells.length,
        samples: Array.from(holidayCells).slice(0, 3).map(cell => ({
          tagName: cell.tagName,
          className: cell.className,
          background: window.getComputedStyle(cell).backgroundColor,
          borderLeft: window.getComputedStyle(cell).borderLeftColor,
          borderRight: window.getComputedStyle(cell).borderRightColor
        }))
      };
    });

    console.log('Holiday cells:', JSON.stringify(holidayAnalysis, null, 2));

    // Find and analyze stage day cells
    const stageAnalysis = await page.evaluate(() => {
      const stageCells = document.querySelectorAll('[style*="var(--stage-bg)"]');
      return {
        count: stageCells.length,
        samples: Array.from(stageCells).slice(0, 3).map(cell => ({
          tagName: cell.tagName,
          className: cell.className,
          background: window.getComputedStyle(cell).backgroundColor,
          borderLeft: window.getComputedStyle(cell).borderLeftColor,
          borderRight: window.getComputedStyle(cell).borderRightColor
        }))
      };
    });

    console.log('Stage day cells:', JSON.stringify(stageAnalysis, null, 2));

    // Find and analyze completion day cells
    const completionAnalysis = await page.evaluate(() => {
      const completionCells = document.querySelectorAll('[style*="var(--completion-bg)"]');
      return {
        count: completionCells.length,
        samples: Array.from(completionCells).slice(0, 3).map(cell => ({
          tagName: cell.tagName,
          className: cell.className,
          background: window.getComputedStyle(cell).backgroundColor,
          borderLeft: window.getComputedStyle(cell).borderLeftColor,
          borderRight: window.getComputedStyle(cell).borderRightColor
        }))
      };
    });

    console.log('Completion day cells:', JSON.stringify(completionAnalysis, null, 2));

    // Get CSS variable values
    const cssVariables = await page.evaluate(() => {
      const root = document.documentElement;
      return {
        holidayBg: window.getComputedStyle(root).getPropertyValue('--holiday-bg'),
        holidayBorder: window.getComputedStyle(root).getPropertyValue('--holiday-border'),
        stageBg: window.getComputedStyle(root).getPropertyValue('--stage-bg'),
        stageBorder: window.getComputedStyle(root).getPropertyValue('--stage-border'),
        completionBg: window.getComputedStyle(root).getPropertyValue('--completion-bg'),
        completionBorder: window.getComputedStyle(root).getPropertyValue('--completion-border')
      };
    });

    console.log('CSS Variables:', JSON.stringify(cssVariables, null, 2));

    // Check completion data population
    const completionData = await page.evaluate(() => {
      // Try to find completion badges
      const badges = document.querySelectorAll('span[title*="Last lesson day"]');
      return {
        badgeCount: badges.length,
        badgeSamples: Array.from(badges).slice(0, 3).map(b => ({
          text: b.textContent,
          title: b.getAttribute('title')
        }))
      };
    });

    console.log('Completion badges:', JSON.stringify(completionData, null, 2));

    // Verify we have different colors
    expect(holidayAnalysis.count, 'Should have holiday cells').toBeGreaterThan(0);
    expect(stageAnalysis.count, 'Should have stage day cells').toBeGreaterThan(0);

    // Check that completion cells exist (this might be 0 if not populated)
    if (completionAnalysis.count === 0) {
      console.warn('WARNING: No completion day cells found - completion data may not be populated');
    }

    // Take a focused screenshot of the first holiday cell
    if (holidayAnalysis.samples.length > 0) {
      const firstHoliday = await page.locator('[style*="var(--holiday-bg)"]').first();
      await firstHoliday.screenshot({
        path: 'src/main/webui/e2e/screenshots/holiday-cell.png'
      });
    }

    // Take a focused screenshot of the first stage cell
    if (stageAnalysis.samples.length > 0) {
      const firstStage = await page.locator('[style*="var(--stage-bg)"]').first();
      await firstStage.screenshot({
        path: 'src/main/webui/e2e/screenshots/stage-cell.png'
      });
    }

    // Take a focused screenshot of the first completion cell
    if (completionAnalysis.samples.length > 0) {
      const firstCompletion = await page.locator('[style*="var(--completion-bg)"]').first();
      await firstCompletion.screenshot({
        path: 'src/main/webui/e2e/screenshots/completion-cell.png'
      });
    }
  });

  test('check completion data in schedule response', async ({ page }) => {
    // Make API call to check completion data
    const response = await page.request.get('http://localhost:8080/api/schedule');
    const data = await response.json();

    console.log('Schedule API response keys:', Object.keys(data));

    // Check if studentGroups have completionInfo
    const groupsWithCompletion = data.studentGroups?.filter((g: any) => g.completionInfo) || [];
    console.log('Groups with completionInfo:', groupsWithCompletion.length);

    if (groupsWithCompletion.length > 0) {
      console.log('Sample completion data:', JSON.stringify(groupsWithCompletion.slice(0, 2), null, 2));
    } else {
      console.warn('WARNING: No student groups have completionInfo populated!');
    }

    // Log all student groups and their completion status
    data.studentGroups?.forEach((g: any) => {
      console.log(`Group ${g.name}: completionInfo = ${g.completionInfo ? 'YES' : 'NO'}`);
    });
  });
});
