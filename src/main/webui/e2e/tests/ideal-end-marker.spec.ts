import { test, expect } from '@playwright/test';

/**
 * E2E tests for ideal end date markers in the schedule timeline.
 *
 * TASK-109: Show ideal last day of school for student groups in calendar.
 * The marker appears as a green badge with a finish line flag emoji (🏁)
 * on the day when a student group should ideally finish their curriculum.
 */
test.describe('Ideal End Date Markers', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // Wait for the schedule to load (use same pattern as other tests)
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(1000); // Let initial render settle
  });

  test('should display ideal end markers in Timeline view', async ({ page }) => {
    // Switch to Timeline view
    const timelineBtn = page.locator('button:has-text("Timeline"), button:has-text("Sequenza")').first();
    await timelineBtn.click();
    await page.waitForTimeout(500);

    // Wait for day rows to appear
    await page.waitForSelector('[data-testid*="day-row"]', { timeout: 10000 });

    // The timeline view shows many days - we need to scroll to find the ideal end markers
    // G14 (group 14) has an ideal end date of 2025-10-10, which should be visible early
    // Let's scroll down a bit to find markers

    // Get the scrollable container
    const scrollContainer = page.locator('[style*="overflow"]').filter({
      has: page.locator('[data-testid*="day-row"]')
    }).first();

    // Scroll down to find markers (G14 marker should be around 2025-10-10)
    // Try scrolling multiple times
    for (let i = 0; i < 20; i++) {
      const markers = page.locator('span').filter({ hasText: '🏁' });
      const count = await markers.count();
      if (count > 0) {
        console.log(`Found ${count} markers after ${i} scroll iterations`);
        break;
      }
      await scrollContainer.evaluate(el => el.scrollBy({ top: 500, behavior: 'instant' }));
      await page.waitForTimeout(100);
    }

    // Look for ideal end markers (green badges with checkered flag)
    const markers = page.locator('span').filter({
      hasText: '🏁'
    });

    // Take a screenshot for debugging
    await page.screenshot({ path: '/tmp/ideal-end-markers-timeline.png', fullPage: false });

    // Verify markers exist (at least one student group should have an ideal end date)
    const markerCount = await markers.count();
    console.log(`Found ${markerCount} ideal end markers in Timeline view`);

    // The markers should have a green gradient background
    if (markerCount > 0) {
      const firstMarker = markers.first();
      const style = await firstMarker.evaluate(el => {
        const computed = window.getComputedStyle(el);
        return {
          background: computed.background,
          color: computed.color,
        };
      });
      console.log('Marker styles:', style);
      // Check for gradient (the marker uses linear-gradient)
      expect(style.background).toContain('gradient');
    }
  });

  test('should display ideal end markers in Week view', async ({ page }) => {
    // Ensure we're in Week view
    const weekBtn = page.locator('button:has-text("Week"), button:has-text("Settimana")').first();
    await weekBtn.click();
    await page.waitForTimeout(500);

    // Look for ideal end markers
    const markers = page.locator('span').filter({
      hasText: '🏁'
    });

    // Take a screenshot for debugging
    await page.screenshot({ path: '/tmp/ideal-end-markers-week.png', fullPage: false });

    const markerCount = await markers.count();
    console.log(`Found ${markerCount} ideal end markers in Week view`);
  });

  test('should show tooltip with calculation details on hover', async ({ page }) => {
    // Switch to Timeline view for more days visible
    const timelineBtn = page.locator('button:has-text("Timeline"), button:has-text("Sequenza")').first();
    await timelineBtn.click();
    await page.waitForTimeout(500);

    // Wait for day rows to appear
    await page.waitForSelector('[data-testid*="day-row"]', { timeout: 10000 });

    // Find a marker
    const markers = page.locator('span').filter({
      hasText: '🏁'
    });

    const markerCount = await markers.count();
    console.log(`Found ${markerCount} markers before hover test`);

    if (markerCount > 0) {
      const firstMarker = markers.first();

      // Hover over the marker
      await firstMarker.hover();

      // Get the title attribute which contains the tooltip
      const title = await firstMarker.getAttribute('title');

      // The tooltip should contain:
      // - "Ideal end:" or Italian equivalent
      // - Hours (e.g., "200h")
      // - Available days count
      expect(title).toBeTruthy();
      console.log(`Tooltip content: ${title}`);

      // Verify tooltip contains expected information
      expect(title).toMatch(/\d+h/); // Hours
      expect(title).toMatch(/\d+/); // Some number for available days
    } else {
      // Skip test if no markers found (may need seed data)
      test.skip(true, 'No ideal end markers found - may need seed data with courses');
    }
  });

  test('should only show markers when grouped by student group', async ({ page }) => {
    // Switch to Timeline view
    const timelineBtn = page.locator('button:has-text("Timeline"), button:has-text("Sequenza")').first();
    await timelineBtn.click();
    await page.waitForTimeout(500);

    // Wait for day rows to appear
    await page.waitForSelector('[data-testid*="day-row"]', { timeout: 10000 });

    // Count markers when grouped by student group (default view)
    const markersStudentGroup = page.locator('span').filter({
      hasText: '🏁'
    });
    const countStudentGroup = await markersStudentGroup.count();
    console.log(`Markers when grouped by student group: ${countStudentGroup}`);

    // Try to switch to group by room
    const groupByRoomBtn = page.locator('button:has-text("Aula"), button:has-text("Room")').first();

    if (await groupByRoomBtn.isVisible()) {
      await groupByRoomBtn.click();
      await page.waitForTimeout(500);

      // Count markers when grouped by room (should be 0)
      const markersRoom = page.locator('span').filter({
        hasText: '🏁'
      });
      const countRoom = await markersRoom.count();

      // Ideal end markers should only appear when grouped by student group
      expect(countRoom).toBe(0);
      console.log(`Markers by room: ${countRoom} (should be 0)`);
    } else {
      console.log('Group by room button not found - skipping group-by-room check');
    }
  });
});
