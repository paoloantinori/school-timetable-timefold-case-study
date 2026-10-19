import { useEffect, useState, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { BatchCourseUpdate, Course, Teacher, StudentGroup } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import { getDisplayName } from '../../utils/teacherUtils';
import { useToast } from '../../contexts/ToastContext';
import { TableContainer, TableTitle, TableForm, TableInput, TableSelect, TableButton } from './styles';
import { FilterBar } from './FilterBar';
import { CourseGrid, courseMatchesFilter } from './CourseGrid';

export default function CoursesAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const { addToast } = useToast();
  const [courses, setCourses] = useState<Course[]>([]);
  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  const [filter, setFilter] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newSubject, setNewSubject] = useState('');
  const [newTeacherId, setNewTeacherId] = useState('');
  const [newGroupId, setNewGroupId] = useState('');
  const [newTotalHours, setNewTotalHours] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  // Deep snapshot of the last loaded/saved server state, so "Discard changes"
  // can restore it without being affected by in-place edits to `courses`.
  const pristineRef = useRef<Course[]>([]);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getCourses();
      setCourses(data);
      pristineRef.current = structuredClone(data);
    } catch (e) {
      setError(getErrorMessage(e, 'Failed to load data'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    api.getTeachers().then(setTeachers).catch(e => {
      console.error('Failed to fetch teachers:', getErrorMessage(e));
    });
    api.getStudentGroups().then(setGroups).catch(e => {
      console.error('Failed to fetch student groups:', getErrorMessage(e));
    });
  }, [load]);

  const handleSave = useCallback(async (dirty: Course[]): Promise<boolean> => {
    setIsSaving(true);
    setError(null);
    try {
      const payload: BatchCourseUpdate[] = dirty.map(c => ({
        id: c.id,
        subject: c.subject,
        teacherId: c.teacher?.id ?? null,
        studentGroupId: c.studentGroup?.id ?? null,
        totalHours: c.totalHours,
        isLab: c.isLab,
        labBlockSize: c.labBlockSize,
        requiredRoomTags: c.requiredRoomTags,
        color: c.color ?? null,
      }));
      const updated = await api.batchUpdateCourses(payload);
      setCourses(prev => {
        const byId = new Map(updated.map(c => [c.id, c]));
        const next = prev.map(c => byId.get(c.id) ?? c);
        pristineRef.current = structuredClone(next);
        return next;
      });
      addToast(t('admin:gridSaveSuccess', { count: dirty.length }), 'success');
      return true;
    } catch (e) {
      setError(getErrorMessage(e));
      return false;
    } finally {
      setIsSaving(false);
    }
  }, [addToast, t]);

  const handleDiscardChanges = useCallback(() => {
    setCourses(structuredClone(pristineRef.current));
  }, []);

  const handleGenerate = useCallback(async (courseId: number) => {
    try {
      const result = await api.generateLessons(courseId);
      addToast(t('admin:lessonsGenerated', { count: result.lessonsGenerated }), 'success');
    } catch (e) {
      addToast(getErrorMessage(e, t('common:error')), 'error');
    }
  }, [addToast, t]);

  const handleDeleteCourses = useCallback(async (ids: number[]) => {
    try {
      await Promise.all(ids.map(id => api.deleteCourse(id)));
      setCourses(prev => {
        const next = prev.filter(c => !ids.includes(c.id));
        pristineRef.current = structuredClone(next);
        return next;
      });
      addToast(t('admin:coursesDeleted', { count: ids.length }), 'success');
    } catch (e) {
      addToast(getErrorMessage(e), 'error');
      // Reload so the grid reflects whatever subset actually got deleted.
      await load();
    }
  }, [addToast, t, load]);

  const resetCreateForm = () => {
    setNewSubject('');
    setNewTeacherId('');
    setNewGroupId('');
    setNewTotalHours('');
  };

  const handleCreateSubmit = async () => {
    const totalHours = Number(newTotalHours);
    if (!newSubject.trim() || !Number.isFinite(totalHours) || totalHours <= 0) return;
    setIsCreating(true);
    setError(null);
    try {
      const created = await api.createCourse({
        subject: newSubject.trim(),
        teacherId: newTeacherId ? Number(newTeacherId) : null,
        studentGroupId: newGroupId ? Number(newGroupId) : null,
        totalHours,
        isLab: false,
        requiredRoomTags: [],
        color: null,
      });
      setCourses(prev => {
        const next = [...prev, created];
        pristineRef.current = structuredClone(next);
        return next;
      });
      addToast(t('admin:gridSaveSuccess', { count: 1 }), 'success');
      resetCreateForm();
      setShowCreateForm(false);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setIsCreating(false);
    }
  };

  const filteredCount = courses.filter(c => courseMatchesFilter(c, filter)).length;

  return (
    <TableContainer>
      <TableTitle>{t('admin:courses')}</TableTitle>

      {error && (
        <div className="p-2 bg-feedback-error-bg rounded mb-3 text-sm text-feedback-error-text flex justify-between items-center">
          <span>{error}</span>
          <button
            className="bg-none border-none text-feedback-error-text cursor-pointer text-lg leading-none"
            onClick={() => setError(null)}
            aria-label={t('common:dismiss')}
          >
            ×
          </button>
        </div>
      )}

      {!showCreateForm ? (
        <div className="mb-3">
          <TableButton onClick={() => setShowCreateForm(true)}>{t('admin:addCourse')}</TableButton>
        </div>
      ) : (
        <TableForm>
          <TableInput
            placeholder={t('schedule:subject')}
            value={newSubject}
            onChange={e => setNewSubject(e.target.value)}
            disabled={isCreating}
          />
          <TableSelect value={newTeacherId} onChange={e => setNewTeacherId(e.target.value)} disabled={isCreating}>
            <option value="">{t('admin:gridUnassigned')}</option>
            {teachers.map(tc => <option key={tc.id} value={tc.id}>{getDisplayName(tc)}</option>)}
          </TableSelect>
          <TableSelect value={newGroupId} onChange={e => setNewGroupId(e.target.value)} disabled={isCreating}>
            <option value="">{t('admin:gridUnassigned')}</option>
            {groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
          </TableSelect>
          <TableInput
            className="max-w-[120px]"
            type="number"
            min={1}
            placeholder={t('admin:totalHours')}
            value={newTotalHours}
            onChange={e => setNewTotalHours(e.target.value)}
            disabled={isCreating}
          />
          <TableButton
            onClick={handleCreateSubmit}
            disabled={isCreating || !newSubject.trim() || !newTotalHours}
          >
            {isCreating ? '...' : t('common:add')}
          </TableButton>
          <TableButton
            variant="cancel"
            onClick={() => { resetCreateForm(); setShowCreateForm(false); }}
            disabled={isCreating}
          >
            {t('common:cancel')}
          </TableButton>
        </TableForm>
      )}

      <FilterBar
        placeholder={t('admin:filterCourses')}
        value={filter}
        onChange={setFilter}
        totalCount={courses.length}
        filteredCount={filteredCount}
      />

      {isLoading ? (
        <div className="text-sm text-muted">{t('common:loading')}</div>
      ) : (
        <CourseGrid
          courses={courses}
          onCoursesChange={setCourses}
          teachers={teachers}
          studentGroups={groups}
          filter={filter}
          isSaving={isSaving}
          onSave={handleSave}
          onGenerateLessons={handleGenerate}
          onDeleteCourses={handleDeleteCourses}
          onDiscardChanges={handleDiscardChanges}
        />
      )}
    </TableContainer>
  );
}
