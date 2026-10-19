import { test, expect } from '@playwright/test';

/**
 * Group Calendar Editor test (TASK-358).
 *
 * Verifies the per-group slot-level calendar editor: selecting a group renders
 * the 5-weekday x 8-slot checkbox matrix reflecting its current
 * GroupCalendarRule base rules, toggling a cell and saving persists the
 * change via the /api/group-calendar-rules CRUD API, and maxHoursPerDay is
 * editable from the same page.
 *
 * Authored alongside TASK-358 but not run as part of that change (per task
 * instructions) - run with `npx playwright test group-calendar-editor` once
 * the dev server + seed data are available.
 */
test.describe('Group Calendar Editor', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080/admin/group-calendar');
    await page.waitForSelector('select', { timeout: 15000 });
  });

  test('renders an 8-row x 5-weekday matrix for a selected group', async ({ page }) => {
    const groupSelect = page.locator('select').first();
    // Pick the second option (index 0 is the "select a group" placeholder)
    await groupSelect.selectOption({ index: 1 });

    // 8 slot rows in the matrix table body
    const rows = page.locator('[data-testid="calendar-matrix"] tbody tr');
    await expect(rows).toHaveCount(8);

    // 5 weekday checkboxes per row (Mon-Fri)
    const firstRowCheckboxes = rows.first().locator('input[type="checkbox"]');
    await expect(firstRowCheckboxes).toHaveCount(5);
  });

  test('toggling a cell and saving persists via the API', async ({ page }) => {
    const groupSelect = page.locator('select').first();
    await groupSelect.selectOption({ index: 1 });

    const rows = page.locator('[data-testid="calendar-matrix"] tbody tr');
    await expect(rows).toHaveCount(8);

    // Toggle slot 0 on Monday (first row, first checkbox): removing or adding
    // an EDGE slot always keeps the row's open range contiguous, so the save
    // can never trip the contiguity validation regardless of the group's
    // seeded calendar shape (e.g. short Fridays end at slot 4 — toggling
    // slot 7 there would create a gap and be correctly rejected).
    const mondaySlot0Checkbox = rows.first().locator('input[type="checkbox"]').first();
    const wasChecked = await mondaySlot0Checkbox.isChecked();
    await mondaySlot0Checkbox.click();
    await expect(mondaySlot0Checkbox).toBeChecked({ checked: !wasChecked });

    const saveBtn = page.getByTestId('save-calendar-button');
    await saveBtn.click();

    // Success message confirms the PUT/POST round-trip completed
    await expect(page.locator('text=Calendar saved')).toBeVisible({ timeout: 10000 });
  });

  test('maxHoursPerDay is editable and saves independently of the calendar matrix', async ({ page }) => {
    const groupSelect = page.locator('select').first();
    await groupSelect.selectOption({ index: 1 });

    const maxHoursInput = page.locator('input[type="number"]').first();
    await maxHoursInput.fill('6');

    const saveMaxHoursBtn = page.getByTestId('save-max-hours-button');
    await saveMaxHoursBtn.click();

    await expect(page.locator('text=Max hours/day saved')).toBeVisible({ timeout: 10000 });
  });
});
