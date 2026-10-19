import { test, expect, type Page } from '@playwright/test';

/**
 * Test pre-assignment toast notifications.
 *
 * TASK-367: the 2026/27 seed has an empty LabRotation table (rotations are
 * deferred to TASK-364), so HybridPhase1aService.run() now completes as a
 * clean no-op (status COMPLETE, zero pinned) instead of ERROR - see
 * HybridPhase1aService's LabRotation.count() == 0 guard. These tests assert
 * the success path only; an ERROR terminal status here would itself be a
 * regression on either seed state (fresh or golden-imported).
 *
 * Verifies that toast notifications appear when pre-assignment starts and
 * when it completes.
 *
 * NOTE: rewritten for TASK-367, not executed in this session - no running
 * app instance was available (isolated worktree, task instructions forbid
 * starting the app). Run with `./scripts/ui/ui-test.sh preassign-toast`
 * against a live dev server before merging.
 */

/**
 * Waits for any in-flight pre-assign to leave RUNNING, then resets and
 * reloads if it left a COMPLETE result behind, so each test starts from a
 * clean IDLE state. Shared by both tests below - they only diverge in what
 * they assert about the resulting toast.
 */
async function resetPreAssignIfComplete(page: Page) {
  await page.waitForFunction(async () => {
    const response = await fetch('/api/schedule/pre-assign/status');
    const data = await response.json();
    return data.status !== 'RUNNING';
  }, { timeout: 60000 });

  const currentStatus = await page.evaluate(async () => {
    const response = await fetch('/api/schedule/pre-assign/status');
    return await response.json();
  });
  if (currentStatus.status === 'COMPLETE') {
    console.log('Resetting pre-assignment status...');
    await page.evaluate(async () => {
      await fetch('/api/schedule/pre-assign/reset', { method: 'POST' });
    });
    console.log('Reloading page after reset...');
    await page.reload();
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
    await page.waitForTimeout(500);
    console.log('Page reloaded');
  }
}

test('pre-assignment toast appears on completion', async ({ page }) => {
  await page.goto('http://localhost:8080');
  await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
  await page.waitForTimeout(1000);

  console.log('=== Testing Pre-assign Toast Notification ===');

  await resetPreAssignIfComplete(page);

  // Get initial toast count - correct selector for toast container
  const toastContainer = page.locator('.fixed.bottom-4.right-4.z-\\[9999\\]');
  const initialToastCount = await toastContainer.locator('> div').count().catch(() => 0);
  console.log('Initial toast count:', initialToastCount);

  // Click pre-assign button
  console.log('Clicking Pre-assign button...');
  const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
  await preAssignBtn.click();

  // Verify "started" toast appears immediately
  await page.waitForTimeout(500);
  const startedToasts = await toastContainer.locator('> div').all();
  let foundStartedToast = false;
  for (const toast of startedToasts) {
    const text = await toast.textContent();
    if (text && (text.includes('started') || text.includes('Started'))) {
      foundStartedToast = true;
      console.log('✓ Found started toast:', text);
      break;
    }
  }
  expect(foundStartedToast, 'Started toast should appear immediately after clicking button').toBe(true);

  // Wait for pre-assignment to complete. With the zero-rotation no-op, this
  // always resolves to COMPLETE (see HybridPhase1aService.run() guard).
  console.log('Waiting for pre-assignment to complete...');
  await page.waitForFunction(async () => {
    const response = await fetch('/api/schedule/pre-assign/status');
    const data = await response.json();
    return data.status === 'COMPLETE';
  }, { timeout: 60000 });

  // The completion toast fires from the UI's own schedule poll, which can lag
  // the API status by several seconds - WAIT for the toast instead of sampling
  // after a fixed 1s sleep (TASK-367: this exact race made the spec red).
  const completionToast = toastContainer.locator('> div').filter({ hasText: /complete/i });
  await completionToast.first().waitFor({ state: 'visible', timeout: 20000 });
  const toastText = (await completionToast.first().textContent()) ?? '';
  console.log('✓ Found completion toast:', toastText);
  expect(toastText, 'Success toast should mention completion').toMatch(/complete/i);
  console.log('✓ Success toast verified');

  // Take screenshot for verification
  await page.screenshot({ path: 'test-results/preassign-toast-verification.png' });
  console.log('Screenshot saved to test-results/preassign-toast-verification.png');
});

/**
 * Test success toast content after pre-assignment completion.
 *
 * Historically this test also handled an ERROR terminal state, back when
 * pre-assignment reliably failed. TASK-367 made the zero-rotation case a
 * clean no-op, so only the success toast is exercised now.
 */
test('pre-assignment toast appears on success', async ({ page }) => {
  await page.goto('http://localhost:8080');
  await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
  await page.waitForTimeout(1000);

  console.log('=== Testing Pre-assign Success Toast ===');

  await resetPreAssignIfComplete(page);

  // Clear any existing toasts by waiting
  await page.waitForTimeout(2000);

  // Click pre-assign button
  const preAssignBtn = page.locator('button:has-text("Pre-assign")').first();
  await preAssignBtn.click();

  // Wait for COMPLETE status
  await page.waitForFunction(async () => {
    const response = await fetch('/api/schedule/pre-assign/status');
    const data = await response.json();
    return data.status === 'COMPLETE';
  }, { timeout: 120000 });

  // Same UI-poll lag as the first test: wait for the toast, don't sample.
  const toastContainer = page.locator('.fixed.bottom-4.right-4.z-\\[9999\\]');
  const successToast = toastContainer.locator('> div').filter({ hasText: /complete/i });
  await successToast.first().waitFor({ state: 'visible', timeout: 20000 });
  const successToastText = (await successToast.first().textContent()) ?? '';
  console.log('✓ Success toast verified successfully:', successToastText);
});
