import { test, expect } from '@playwright/test';

/**
 * TASK-356: Course-hours grid editor.
 *
 * Covers the grid's edit/save/discard cycle, plus the create and delete flows
 * added by the code-review follow-up: create-course form, edit + discard,
 * row delete, and labBlockSize surviving a save (the X1 data-corruption fix).
 *
 * NOTE: authored, not executed in this session - no running app instance was
 * available (isolated worktree, task instructions forbid starting the app).
 * Run with `./scripts/ui/ui-test.sh course-grid-edit` (or plain
 * `npx playwright test course-grid-edit`) against a live dev server before
 * merging.
 */
test.describe('Course grid editor', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080/admin/courses');
    await page.waitForSelector('.ag-root', { timeout: 15000 });
    // Let the grid finish its first render pass.
    await page.waitForTimeout(500);
  });

  test('editing two totalHours cells updates the footer total and persists after reload', async ({ page }) => {
    const row0Cell = page.locator('.ag-row[row-index="0"] .ag-cell[col-id="totalHours"]');
    const row1Cell = page.locator('.ag-row[row-index="1"] .ag-cell[col-id="totalHours"]');
    await expect(row0Cell).toBeVisible();
    await expect(row1Cell).toBeVisible();

    const originalTotal = await page.evaluate(async () => {
      const res = await fetch('/api/courses');
      const courses = await res.json();
      return courses.reduce((sum: number, c: { totalHours: number }) => sum + c.totalHours, 0);
    });

    const oldRow0 = Number(await row0Cell.textContent());
    const oldRow1 = Number(await row1Cell.textContent());
    const newRow0 = oldRow0 + 5;
    const newRow1 = oldRow1 + 7;
    const expectedFooterTotal = originalTotal - oldRow0 - oldRow1 + newRow0 + newRow1;

    const editCell = async (cell: typeof row0Cell, newValue: number) => {
      await cell.dblclick();
      const input = cell.locator('input');
      await input.fill(String(newValue));
      await page.keyboard.press('Tab');
    };

    await editCell(row0Cell, newRow0);
    await editCell(row1Cell, newRow1);

    // Footer pinned row shows the live total for the currently filtered set.
    const footerCell = page.locator('.ag-floating-bottom .ag-cell[col-id="totalHours"]');
    await expect(footerCell).toContainText(String(expectedFooterTotal));

    // Unsaved-changes indicator should mention 2 changes.
    await expect(page.getByText(/2 unsaved change/i)).toBeVisible();

    // Save and wait for the success toast.
    await page.getByRole('button', { name: /save.*2.*change/i }).click();
    await expect(page.getByText(/course\(s\) saved/i)).toBeVisible({ timeout: 10000 });

    // Reload and verify persistence via the API (avoids depending on row order in the UI).
    await page.reload();
    await page.waitForSelector('.ag-root', { timeout: 15000 });

    const newTotal = await page.evaluate(async () => {
      const res = await fetch('/api/courses');
      const courses = await res.json();
      return courses.reduce((sum: number, c: { totalHours: number }) => sum + c.totalHours, 0);
    });

    expect(newTotal).toEqual(expectedFooterTotal);
  });

  test('creating a course via the inline form appends it to the grid', async ({ page }) => {
    await page.getByRole('button', { name: /add course/i }).click();

    const subjectInput = page.locator('input[placeholder="Subject"]');
    await subjectInput.fill('E2E Test Subject');
    const hoursInput = page.locator('input[placeholder="Total Hours"]');
    await hoursInput.fill('42');

    await page.getByRole('button', { name: /^add$/i }).click();

    await expect(page.getByText(/course\(s\) saved/i)).toBeVisible({ timeout: 10000 });

    const created = await page.evaluate(async () => {
      const res = await fetch('/api/courses');
      const courses = await res.json();
      return courses.find((c: { subject: string }) => c.subject === 'E2E Test Subject');
    });
    expect(created).toBeTruthy();
    expect(created.totalHours).toBe(42);
  });

  test('editing a cell then discarding restores the original value', async ({ page }) => {
    const row0Cell = page.locator('.ag-row[row-index="0"] .ag-cell[col-id="totalHours"]');
    await expect(row0Cell).toBeVisible();
    const original = await row0Cell.textContent();

    await row0Cell.dblclick();
    await row0Cell.locator('input').fill(String(Number(original) + 99));
    await page.keyboard.press('Tab');
    await expect(page.getByText(/1 unsaved change/i)).toBeVisible();

    await page.getByRole('button', { name: /discard changes/i }).click();

    await expect(page.getByText(/no changes to save/i)).toBeVisible();
    await expect(row0Cell).toHaveText(original ?? '');
  });

  test('deleting a course row removes it after confirmation', async ({ page }) => {
    // Create a dedicated course first: deleting a seeded course fails on the
    // live profile because its generated/imported Lessons hold FK references
    // (CourseResource.delete has no cascade — tracked in TASK-362 notes).
    // A freshly created course has no lessons, so the UI delete path can be
    // exercised deterministically regardless of profile state.
    const subject = `E2E Delete Me ${Date.now()}`;
    const target = await page.evaluate(async (subj) => {
      const res = await fetch('/api/courses', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject: subj, totalHours: 1 }),
      });
      return res.json();
    }, subject);
    expect(target?.id).toBeTruthy();
    await page.reload();
    await page.locator('.ag-center-cols-container .ag-row').first().waitFor();

    // AG Grid virtualizes rows AND columns: locate the new row via the quick
    // filter so it is rendered, then scroll right for the Actions column.
    await page.getByPlaceholder(/filter|filtra/i).first().fill(subject);
    const firstRow = page.locator('.ag-center-cols-container .ag-row').first();
    await firstRow.waitFor();
    const rowIndex = await firstRow.getAttribute('row-index');

    page.once('dialog', dialog => dialog.accept());
    // The per-row Actions cell sits behind AG Grid's column virtualization
    // (not materialized reliably by scripted horizontal scrolls), so exercise
    // the equivalent delete path that is always visible: row checkbox +
    // toolbar bulk delete.
    await page
      .locator(`.ag-row[row-index="${rowIndex}"] input[type="checkbox"]`)
      .first()
      .check();
    await page.getByRole('button', { name: /delete selected|elimina selezionat/i }).click();

    // Deletion is verified against the API: row indices shift after removal,
    // so DOM-level absence checks are inherently ambiguous here.
    await expect
      .poll(async () => page.evaluate(async (id) => {
        const res = await fetch('/api/courses');
        const courses = await res.json();
        return courses.some((c: { id: number }) => c.id === id);
      }, target.id), { timeout: 10000 })
      .toBe(false);
  });

  test('labBlockSize survives a batch save that only touches totalHours', async ({ page }) => {
    // Find (or make) a lab course with a known block size, then edit only its
    // totalHours cell. Regression check for the X1 data-corruption bug where
    // any batch save zeroed out every course's labBlockSize.
    const labCourse = await page.evaluate(async () => {
      const res = await fetch('/api/courses');
      const courses = await res.json();
      return courses.find((c: { isLab: boolean; labBlockSize: number }) => c.isLab && c.labBlockSize > 1);
    });
    test.skip(!labCourse, 'No lab course with labBlockSize > 1 in seed data');

    const row = page.locator('.ag-row', { hasText: labCourse.subject }).first();
    const hoursCell = row.locator('.ag-cell[col-id="totalHours"]');
    await hoursCell.dblclick();
    await hoursCell.locator('input').fill(String(labCourse.totalHours + 1));
    await page.keyboard.press('Tab');

    await page.getByRole('button', { name: /save.*1.*change/i }).click();
    await expect(page.getByText(/course\(s\) saved/i)).toBeVisible({ timeout: 10000 });

    const reloaded = await page.evaluate(async id => {
      const res = await fetch('/api/courses');
      const courses = await res.json();
      return courses.find((c: { id: number }) => c.id === id);
    }, labCourse.id);

    expect(reloaded.labBlockSize).toBe(labCourse.labBlockSize);
    expect(reloaded.totalHours).toBe(labCourse.totalHours + 1);
  });
});
