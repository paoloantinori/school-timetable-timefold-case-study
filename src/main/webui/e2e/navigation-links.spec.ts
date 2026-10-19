import { test, expect } from '@playwright/test';

/**
 * Navigation smoke tests — verify every link in the top bar
 * loads without console errors and renders meaningful content.
 *
 * Each page has a distinct "landmark" element used to confirm
 * it loaded successfully.
 *
 * TASK-367: audited for 2026/27 seed assumptions (8 slots/day instead of 6,
 * renamed teachers) - none found, this spec only checks static nav landmarks
 * and console errors. Not run against a live app in this session (isolated
 * worktree); runtime verification pending.
 */
test.describe('Navigation Links', () => {
  const NAV_LINKS = [
    { href: '/', label: 'Schedule', selector: 'button:has-text("Solve")' },
    { href: '/admin', label: 'Administration', selector: 'h3:has-text("Teachers")' },
    { href: '/docs', label: 'Schedule Constraints Guide', selector: 'h1:has-text("Constraints Guide")' },
    { href: '/complexity', label: 'Complexity', selector: 'h1:has-text("Why Scheduling is Hard")' },
    { href: '/phases', label: 'Phases', selector: 'h1:has-text("Solver Phases")' },
    { href: '/benchmarks', label: 'Benchmarks', selector: 'h1:has-text("Benchmark")' },
  ];

  for (const link of NAV_LINKS) {
    test(`${link.label} page loads without errors`, async ({ page }) => {
      const errors: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') errors.push(msg.text());
      });
      page.on('pageerror', (err) => errors.push(err.message));

      await page.goto(`http://localhost:8080${link.href}`, { waitUntil: 'networkidle' });

      // Wait for a page-specific landmark to appear
      await page.locator(link.selector).first().waitFor({ state: 'visible', timeout: 15_000 });

      // No JS errors allowed
      expect(errors).toHaveLength(0);
    });
  }

  test('nav links cycle through all pages without errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    page.on('pageerror', (err) => errors.push(err.message));

    // Start from a fresh page load to avoid stale chunk cache
    await page.goto('http://localhost:8080/', { waitUntil: 'networkidle' });
    await page.locator('button:has-text("Solve")').first().waitFor({ state: 'visible', timeout: 15_000 });

    for (const link of NAV_LINKS) {
      await page.getByRole('link', { name: link.label, exact: true }).click();
      await page.locator(link.selector).first().waitFor({ state: 'visible', timeout: 15_000 });
    }

    expect(errors).toHaveLength(0);
  });
});
