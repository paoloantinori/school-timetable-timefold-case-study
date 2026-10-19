import { test, expect } from '@playwright/test';

/**
 * TASK-388: the SIDEBAR Reset (ScheduleTimeline -> ScheduleSidebar, variant
 * danger, the second live destructive control) must ask for confirmation
 * before POST /api/schedule/reset. Dismiss cancels (no POST), accept fires.
 * The controls-bar Reset is covered by reset-button.spec.ts.
 */
test('sidebar reset asks for confirmation before the destructive reset', async ({ page }) => {
  test.setTimeout(120000);
  const dialogs: string[] = [];
  const resets: number[] = [];
  page.on('response', (r) => {
    if (r.url().includes('/api/schedule/reset')) resets.push(r.status());
  });

  await page.goto('/');
  await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
  await page.waitForTimeout(1500);

  // The sidebar is COLLAPSED by default (localStorage-driven): expand it first
  // via its toggle (ScheduleSidebar onToggleCollapse), then match the button
  // (labelled "Reset all" / "Azzera tutto" after the i18n fix; the raw-key
  // fallback keeps the probe robust against a stale bundle).
  const expand = page.locator('button[aria-label="Expand sidebar"]');
  try {
    await expand.click({ timeout: 3000 });
    await page.waitForTimeout(500);
  } catch {
    // already expanded (or the rail is absent): fall through
  }
  // Quick actions live under the sidebar's ACTIONS tab (default tab is stats)
  const actionsTab = page.getByRole('button', { name: /actions/i }).first();
  try {
    await actionsTab.click({ timeout: 3000 });
    await page.waitForTimeout(500);
  } catch {
    // tab not found or already active
  }
  const sidebarReset = page.getByRole('button', { name: /^(Reset all|Azzera tutto|resetAll)$/i }).first();
  await sidebarReset.waitFor({ state: 'visible', timeout: 15000 });

  // DISMISS: the destructive POST must NOT fire
  page.once('dialog', async (d) => {
    dialogs.push('dismiss:' + d.message().slice(0, 25));
    await d.dismiss();
  });
  await sidebarReset.click();
  await page.waitForTimeout(3000);
  const resetsAfterDismiss = resets.length;

  // ACCEPT: the POST fires (app is quiescent, so 200 is expected)
  page.once('dialog', async (d) => {
    dialogs.push('accept:' + d.message().slice(0, 25));
    await d.accept();
  });
  await sidebarReset.click();
  await page.waitForTimeout(6000);

  console.log('DIALOGS:', JSON.stringify(dialogs));
  console.log('RESETS:', JSON.stringify(resets), 'afterDismiss:', resetsAfterDismiss);
  expect(dialogs.length).toBe(2);
  expect(resetsAfterDismiss).toBe(0);
  expect(resets.length).toBe(1);
});
