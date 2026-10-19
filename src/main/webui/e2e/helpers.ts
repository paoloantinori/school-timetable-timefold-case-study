import type { Page } from '@playwright/test';

/**
 * TASK-367: specs that launch a real solve must be robust to either the
 * fresh 2026/27 seed (all Lessons unpinned) or the golden-imported profile
 * (100% pinned - the solver has nothing to move and settles into
 * NOT_SOLVING almost instantly). This creates a throwaway course + Lessons
 * via the API (course-grid-edit.spec.ts's fixture pattern) so "Solve" always
 * has at least one entity to work on, regardless of profile state.
 */
export async function ensureUnpinnedLessonExists(page: Page) {
  const hasUnpinned = await page.evaluate(async () => {
    const res = await fetch('/api/schedule');
    const data = await res.json();
    return (data.lessons || []).some((l: { pinned: boolean }) => l.pinned === false);
  });
  if (hasUnpinned) return;

  await page.evaluate(async () => {
    const [teachers, groups] = await Promise.all([
      fetch('/api/teachers').then(r => r.json()),
      fetch('/api/student-groups').then(r => r.json()),
    ]);
    if (teachers.length === 0 || groups.length === 0) {
      throw new Error('e2e fixture prerequisites missing: no teachers or groups in this profile — seed the 2026/27 profile first (see scripts/ui/ui-test.sh header)');
    }

    const course = await fetch('/api/courses', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        subject: `E2E Solver Fixture ${Date.now()}`,
        teacherId: teachers[0].id,
        studentGroupId: groups[0].id,
        totalHours: 1,
        isLab: false,
      }),
    }).then(r => r.json());
    await fetch(`/api/courses/${course.id}/generate-lessons`, { method: 'POST' });
  });
}

/**
 * Deletes every course created by ensureUnpinnedLessonExists (subject prefix
 * "E2E Solver Fixture") so the profile's course data stays a faithful copy of
 * the imported golden schedule. Call from afterAll in specs that use the fixture.
 *
 * Relies on DELETE /api/courses/{id} cascading its generated Lesson rows first
 * (CourseResource#delete) — this fixture always calls generate-lessons after
 * creating the course, so without that cascade the cleanup DELETE would 500 on
 * the Lesson.course_id FK instead of actually cleaning up.
 */
export async function cleanupSolverFixtures(page: import('@playwright/test').Page): Promise<void> {
  await page.evaluate(async () => {
    const courses = await fetch('/api/courses').then(r => r.json());
    const fixtures = courses.filter((c: { subject: string }) => c.subject?.startsWith('E2E Solver Fixture'));
    for (const c of fixtures) {
      await fetch(`/api/courses/${c.id}`, { method: 'DELETE' });
    }
  });
}
