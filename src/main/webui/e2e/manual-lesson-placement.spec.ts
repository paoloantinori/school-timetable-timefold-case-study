import { test, expect, type Page } from '@playwright/test';

/**
 * Manual lesson placement (TASK-357): drag-drop from the Unassigned sidebar
 * into the grid, drag-drop of an already-placed lesson between cells, and
 * the pinned-move confirmation guard.
 *
 * Uses the Timeline (infinite) view rather than Week view for the drag
 * targets: only the infinite view exposes a date-jump input, which this spec
 * needs to reliably land on a school day the fixture's own group has open
 * without hardcoding a date that might fall on a holiday or an already-solved
 * slot. Both views render cells with the same `cell-<date>-<startTime>-<colId>`
 * testid and share the same drag-drop wiring (WeekViewGrid / VirtualizedDayRow
 * both call the identical canDropLessonAt legality check), so coverage
 * transfers.
 *
 * NOTE: authored, not executed in this session - the task instructions for
 * this worktree forbid starting the app / running Playwright. Run with
 * `./scripts/ui/ui-test.sh manual-lesson-placement` against a live dev
 * server before merging, and fix up any selector drift this finds.
 */

interface SlotDefinition {
  index: number;
  startTime: string;
  isAfternoon: boolean;
}

interface ResolvedEntry {
  groupId: number;
  date: string;
  minSlot: number;
  maxSlot: number;
  closed: boolean;
}

/**
 * Creates a throwaway 1-hour course (-> one unassigned, unpinned Lesson) for
 * teachers[0]/groups[0]. Also returns the school year's earliest timeslot
 * date: "today" (used as the search window's lower bound below) can fall
 * outside the actual timetabled range - e.g. over the summer, before the new
 * year's timeslots exist - and a date with no Timeslot rows never gets a
 * rendered day-row, which is exactly what made this spec time out.
 */
async function createFixtureLesson(page: Page): Promise<{ lessonId: number; groupId: number; teacherId: number; schoolYearStart: string }> {
  return page.evaluate(async () => {
    const [teachers, groups] = await Promise.all([
      fetch('/api/teachers').then(r => r.json()),
      fetch('/api/student-groups').then(r => r.json()),
    ]);
    if (teachers.length === 0 || groups.length === 0) {
      throw new Error('e2e fixture prerequisites missing: no teachers or groups in this profile');
    }
    const course = await fetch('/api/courses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subject: `E2E Manual Placement Fixture ${Date.now()}`,
        teacherId: teachers[0].id,
        studentGroupId: groups[0].id,
        totalHours: 1,
        isLab: false,
      }),
    }).then(r => r.json());
    await fetch(`/api/courses/${course.id}/generate-lessons`, { method: 'POST' });
    const schedule = await fetch('/api/schedule').then(r => r.json());
    const lesson = schedule.lessons.find((l: { course?: { id: number } } ) => l.course?.id === course.id);
    if (!lesson) throw new Error('fixture lesson not generated');
    const timeslots = schedule.timeslots as { startDateTime: string }[];
    if (timeslots.length === 0) throw new Error('e2e fixture prerequisites missing: no timeslots in this profile');
    const schoolYearStart = timeslots.map(t => t.startDateTime.slice(0, 10)).sort()[0];
    return { lessonId: lesson.id, groupId: groups[0].id, teacherId: teachers[0].id, schoolYearStart };
  });
}

async function cleanupFixtures(page: Page): Promise<void> {
  await page.evaluate(async () => {
    const courses = await fetch('/api/courses').then(r => r.json());
    const fixtures = courses.filter((c: { subject: string }) => c.subject?.startsWith('E2E Manual Placement Fixture'));
    for (const c of fixtures) {
      await fetch(`/api/courses/${c.id}`, { method: 'DELETE' });
    }
  });
}

/**
 * [from, to] search window for the open-slot finders below: today (or
 * `earliestDate`, whichever is later) through +60 days, plenty of school
 * days. `earliestDate` clamps the start to the school year's first timeslot
 * date, since /resolved returns an "open" entry for every school-weekday
 * regardless of whether any Timeslot actually exists for it (see
 * createFixtureLesson's comment). Pure date math - computed once here in the
 * Node/test context rather than duplicated inside each page.evaluate closure.
 */
function searchWindow(earliestDate: string): { from: string; to: string } {
  const today = new Date();
  const earliest = new Date(earliestDate);
  const from = today > earliest ? today : earliest;
  const to = new Date(from.getTime() + 60 * 24 * 60 * 60 * 1000);
  const fmt = (d: Date) => d.toISOString().slice(0, 10);
  return { from: fmt(from), to: fmt(to) };
}

/** First open morning date/slot for `groupId`, skipping holidays - the drag target for a legal placement. */
async function findOpenMorningSlot(page: Page, groupId: number, earliestDate: string): Promise<{ date: string; startTime: string }> {
  const { from, to } = searchWindow(earliestDate);
  return page.evaluate(async ({ gid, from, to }) => {
    const [resolved, slots, holidays] = await Promise.all([
      fetch(`/api/group-calendar-rules/resolved?from=${from}&to=${to}`).then(r => r.json()),
      fetch('/api/config/slots').then(r => r.json()),
      fetch('/api/holidays').then(r => r.json()),
    ]);
    const holidayDates = new Set((holidays as { date: string }[]).map(h => h.date));
    const morningSlots = (slots.slots as { index: number; startTime: string; isAfternoon: boolean }[])
      .filter(s => !s.isAfternoon)
      .sort((a, b) => a.index - b.index);

    for (const entry of resolved as { groupId: number; date: string; minSlot: number; maxSlot: number; closed: boolean }[]) {
      if (entry.groupId !== gid || entry.closed || holidayDates.has(entry.date)) continue;
      const day = new Date(entry.date).getUTCDay();
      if (day === 0 || day === 6) continue; // defensive - resolved rules are Mon-Fri only
      const openMorning = morningSlots.find(s => s.index >= entry.minSlot && s.index <= entry.maxSlot);
      if (openMorning) return { date: entry.date, startTime: openMorning.startTime };
    }
    throw new Error(`no open morning slot found for group ${gid} in the next 60 days`);
  }, { gid: groupId, from, to });
}

/** First open AFTERNOON date/slot for `groupId` - used to prove afternoon drops are accepted (M28, TASK-379). */
async function findOpenAfternoonSlot(page: Page, groupId: number, earliestDate: string): Promise<{ date: string; startTime: string }> {
  const { from, to } = searchWindow(earliestDate);
  return page.evaluate(async ({ gid, from, to }) => {
    const [resolved, slots, holidays] = await Promise.all([
      fetch(`/api/group-calendar-rules/resolved?from=${from}&to=${to}`).then(r => r.json()),
      fetch('/api/config/slots').then(r => r.json()),
      fetch('/api/holidays').then(r => r.json()),
    ]);
    const holidayDates = new Set((holidays as { date: string }[]).map(h => h.date));
    const afternoonSlots = (slots.slots as { index: number; startTime: string; isAfternoon: boolean }[])
      .filter(s => s.isAfternoon)
      .sort((a, b) => a.index - b.index);

    for (const entry of resolved as { groupId: number; date: string; minSlot: number; maxSlot: number; closed: boolean }[]) {
      if (entry.groupId !== gid || entry.closed || holidayDates.has(entry.date)) continue;
      const openAfternoon = afternoonSlots.find(s => s.index >= entry.minSlot && s.index <= entry.maxSlot);
      if (openAfternoon) return { date: entry.date, startTime: openAfternoon.startTime };
    }
    throw new Error(`no open afternoon slot found for group ${gid} in the next 60 days (group calendar closes afternoons)`);
  }, { gid: groupId, from, to });
}

/** Switches to Timeline (infinite) view and jumps the date-picker to `date`. */
async function jumpToDate(page: Page, date: string) {
  await page.locator('button:has-text("Timeline")').click();
  await page.locator('input[type="date"]').first().fill(date);
  await page.waitForSelector(`[data-testid="day-row-${date}"]`, { timeout: 15000 });
}

async function getLessonTimeslot(page: Page, lessonId: number): Promise<{ id: number } | null> {
  return page.evaluate(async (id) => {
    const schedule = await fetch('/api/schedule').then(r => r.json());
    const lesson = schedule.lessons.find((l: { id: number }) => l.id === id);
    return lesson?.timeslot ?? null;
  }, lessonId);
}

test.describe('Manual lesson placement (TASK-357)', () => {
  let lessonId: number;
  let groupId: number;
  let schoolYearStart: string;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await page.goto('http://localhost:8080');
    const fixture = await createFixtureLesson(page);
    lessonId = fixture.lessonId;
    groupId = fixture.groupId;
    schoolYearStart = fixture.schoolYearStart;
    await page.close();
  });

  test.afterAll(async ({ browser }) => {
    const page = await browser.newPage();
    await page.goto('http://localhost:8080');
    await cleanupFixtures(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:8080');
    await page.waitForSelector('table, [class*="timeline"]', { timeout: 30000 });
  });

  test('drag an unassigned lesson to a free morning cell places it', async ({ page }) => {
    const target = await findOpenMorningSlot(page, groupId, schoolYearStart);
    await jumpToDate(page, target.date);

    // Open the Unassigned tab in the sidebar and locate this fixture's draggable entry.
    await page.locator('button:has-text("Unassigned")').click();
    const source = page.locator(`[data-testid="unassigned-lesson-${lessonId}"]`);
    await expect(source).toBeVisible();

    const targetCell = page.locator(`[data-testid="cell-${target.date}-${target.startTime}-${groupId}"]`);
    await expect(targetCell).toBeVisible();

    await source.dragTo(targetCell);

    // API confirms the move: the lesson now has a non-null timeslot.
    await expect.poll(async () => getLessonTimeslot(page, lessonId)).not.toBeNull();
  });

  test('dragging a placed lesson onto an open afternoon cell is accepted (M28 is a preference, TASK-379)', async ({ page }) => {
    // Precondition: the previous test placed the lesson in the morning. Re-derive its
    // current cell instead of assuming test order - this test can also run in isolation.
    // Until TASK-379 this asserted client-side REJECTION (hard H21): the demotion to
    // medium M28 means a human planner may deliberately use an open afternoon slot,
    // exactly as the school's own golden timetable does (575 afternoon lessons).
    const before = await getLessonTimeslot(page, lessonId);
    test.skip(!before, 'fixture lesson must already be placed - run after the morning-placement test');

    const afternoon = await findOpenAfternoonSlot(page, groupId, schoolYearStart);
    await jumpToDate(page, afternoon.date);

    const placedLesson = page.locator(`[data-testid^="cell-"] :text("E2E Manual Placement Fixture")`).first();
    await expect(placedLesson).toBeVisible();
    const afternoonCell = page.locator(`[data-testid="cell-${afternoon.date}-${afternoon.startTime}-${groupId}"]`);
    await expect(afternoonCell).toBeVisible();

    await placedLesson.dragTo(afternoonCell);

    // The drop is legal now: assignLesson fires and the timeslot changes.
    await expect.poll(async () => (await getLessonTimeslot(page, lessonId))?.id).not.toBe(before?.id);
  });

  test('moving a pinned lesson asks for confirmation', async ({ page }) => {
    // Precondition: pinning an unassigned lesson now 409s (BUG A guard), so the
    // fixture must already be placed - same isolation guard as the H21 test above.
    const before = await getLessonTimeslot(page, lessonId);
    test.skip(!before, 'fixture lesson must already be placed - run after the morning-placement test');

    // Pin the fixture lesson directly via the API (equivalent to clicking Pin in the tooltip).
    await page.evaluate(async (id) => {
      await fetch(`/api/lessons/${id}/pin`, { method: 'PUT' });
    }, lessonId);
    const target = await findOpenMorningSlot(page, groupId, schoolYearStart);
    await jumpToDate(page, target.date);

    let dialogMessage = '';
    page.once('dialog', async (dialog) => {
      dialogMessage = dialog.message();
      await dialog.dismiss(); // decline the move - lesson must stay put
    });

    const placedLesson = page.locator(`[data-testid^="cell-"] :text("E2E Manual Placement Fixture")`).first();
    const targetCell = page.locator(`[data-testid="cell-${target.date}-${target.startTime}-${groupId}"]`);
    if (await placedLesson.count() > 0) {
      await placedLesson.dragTo(targetCell);
    }

    expect(dialogMessage).toContain('pinned');
    const after = await getLessonTimeslot(page, lessonId);
    expect(after?.id).toBe(before?.id); // dismissed confirm -> no move happened

    // Unpin so afterAll's course deletion isn't blocked by any pinned-entity guard.
    await page.evaluate(async (id) => {
      await fetch(`/api/lessons/${id}/pin`, { method: 'PUT' });
    }, lessonId);
  });
});
