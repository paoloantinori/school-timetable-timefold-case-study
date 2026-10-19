import { test, expect } from '@playwright/test';

/**
 * TASK-367: audited for 2026/27 seed assumptions (8 slots/day instead of 6,
 * renamed teachers) - none found; this is a navigation + screenshot smoke
 * test with no seed-specific assertions. Not run against a live app in this
 * session (isolated worktree); runtime verification pending.
 */
test('screenshot student groups admin', async ({ page }) => {
  await page.goto('http://localhost:8080/admin');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);
  
  // Click on Student Groups tab
  await page.locator('a:has-text("Student Groups"), button:has-text("Student Groups")').first().click();
  await page.waitForTimeout(1000);
  
  // Take screenshot
  await page.screenshot({ 
    path: '/tmp/student-groups-admin.png',
    fullPage: true 
  });
  
  console.log('Screenshot saved to /tmp/student-groups-admin.png');
});
