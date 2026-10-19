import { test, expect, type Page } from '@playwright/test';

/**
 * TASK-367: audited for 2026/27 seed assumptions (8 slots/day instead of 6,
 * renamed teachers) - none found, this spec only depends on theme selection
 * and the score-card testids added alongside this fix.
 *
 * ThemeSelector is a custom listbox (button + role="option" items), not a
 * native <select> - a `select` locator never matches it, and the trigger
 * button's own visible text is just the current theme's label (e.g. "Light"),
 * which doesn't contain "dark" or "theme" either. Both of this file's old
 * "find the theme control" branches silently found nothing, so the dark-mode
 * test never actually switched into dark mode. Fixed via ThemeSelector's own
 * aria-label/role="option" structure.
 *
 * The score cards live in ScheduleSidebar, gated on `schedule` having loaded
 * (~10k lessons on the 2026/27 seed, so the initial GET /api/schedule can take
 * a while, more under solver load) - the old `div` text-filter locator had no
 * generous timeout for that and no stable anchor, so it raced the initial
 * load. `data-testid="score-card-hard"` (added to ScoreCard alongside this
 * fix) is used instead.
 */
async function openThemeMenu(page: Page) {
  await page.locator('button[aria-label^="Theme:"]').click();
}

async function selectTheme(page: Page, label: 'Light' | 'Dark' | 'school') {
  await openThemeMenu(page);
  await page.locator('button[role="option"]').filter({ hasText: label }).click();
  await page.waitForTimeout(300); // theme transition (ThemeSelector's own 100ms+150ms fade)
}

test.describe('Dark Mode Score Cards', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    // The score cards only render once `schedule` has loaded (ScheduleSidebar
    // is conditionally mounted) - wait for the real target, generously, rather
    // than a generic class selector that could match unrelated elements.
    await page.waitForSelector('[data-testid="score-card-hard"]', { timeout: 30000 });
  });

  test('score cards have visible backgrounds in dark mode', async ({ page }) => {
    await selectTheme(page, 'Dark');

    // Take screenshot for visual verification
    await page.screenshot({
      path: 'e2e/screenshots/dark-mode-score-cards.png',
      fullPage: false
    });

    const scoreCard = page.locator('[data-testid="score-card-hard"]');
    await expect(scoreCard).toBeVisible();

    // Get the computed background color
    const bgColor = await scoreCard.evaluate((el) => {
      // Walk up to find the card container with background
      let current: HTMLElement | null = el as HTMLElement;
      while (current) {
        const bg = window.getComputedStyle(current).backgroundColor;
        if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
          return bg;
        }
        current = current.parentElement;
      }
      return 'not found';
    });

    // Background should be a visible color (not transparent or pure black)
    // In dark mode, the score card should have a colored background
    expect(bgColor).not.toBe('rgba(0, 0, 0, 0)');
    expect(bgColor).not.toBe('transparent');

    // The surface color in dark mode is rgb(10, 10, 10) - score card should differ
    expect(bgColor).not.toBe('rgb(10, 10, 10)');
    expect(bgColor).not.toBe('rgb(0, 0, 0)');
  });

  test('score cards are readable in all themes', async ({ page }) => {
    const themes: Array<'Light' | 'Dark' | 'school'> = ['Light', 'Dark', 'school'];

    for (const theme of themes) {
      await selectTheme(page, theme);

      // Take screenshot
      await page.screenshot({
        path: `e2e/screenshots/${theme.toLowerCase()}-mode-score-cards.png`,
        fullPage: false
      });

      // Verify score cards exist
      const scoreCard = page.locator('[data-testid="score-card-hard"]');
      await expect(scoreCard).toBeVisible();
    }
  });
});
