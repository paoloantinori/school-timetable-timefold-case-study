import {
  forwardRef, useCallback, useImperativeHandle, useMemo, useRef, useState,
} from 'react';
import { useTranslation } from 'react-i18next';
import { AgGridReact } from 'ag-grid-react';
import type {
  ColDef, GetRowIdParams, GridApi, GridReadyEvent, CellValueChangedEvent,
  SelectionChangedEvent, ICellEditorParams, ICellRendererParams, ValueGetterParams,
  ValueSetterParams, ValueFormatterParams, RowClassParams, RowStyle,
} from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-alpine.css';
import type { Course, Teacher, StudentGroup } from '../../types/domain';
import { TableButton, TableSelect, TableInput, DeleteButton } from './styles';
import { useTheme } from '../../theme';
import { getDisplayName } from '../../utils/teacherUtils';

// ag-grid-community (the "packages" distribution, not @ag-grid-community/core)
// auto-registers ClientSideRowModelModule at import time. Calling
// ModuleRegistry.registerModules() here would set the "module-based" flag on
// top of that, which is exactly the "mixing modules and packages" warning.

/** Synthetic subtotal row inserted between course rows when grouping is active. */
interface CourseGroupRow {
  __group: true;
  id: string;
  label: string;
  hours: number;
  count: number;
}

type CourseRow = Course | CourseGroupRow;

function isGroupRow(row: CourseRow): row is CourseGroupRow {
  return (row as CourseGroupRow).__group === true;
}

// AG Grid's getter/setter/editable callbacks type `data` as possibly
// undefined (a row mid-removal, in theory). These narrow both that and the
// group-row/course-row split in one step, so column defs never need casts.
function asCourse(row: CourseRow | undefined): Course | null {
  return row && !isGroupRow(row) ? row : null;
}

function asGroupRow(row: CourseRow | undefined): CourseGroupRow | null {
  return row && isGroupRow(row) ? row : null;
}

export type GroupByMode = 'none' | 'teacher' | 'group';

type BulkField = 'subject' | 'teacher' | 'studentGroup' | 'totalHours' | 'labBlockSize' | 'isLab' | 'requiredRoomTags' | 'color';

/** Substring match on subject/teacher/group, shared with CoursesAdmin's filtered-count display. */
export function courseMatchesFilter(course: Course, query: string): boolean {
  const q = query.toLowerCase();
  if (!q) return true;
  return course.subject.toLowerCase().includes(q) ||
    (course.teacher?.name || '').toLowerCase().includes(q) ||
    (course.studentGroup?.name || '').toLowerCase().includes(q);
}

interface FieldLookups {
  teachersById: Map<number, Teacher>;
  groupsById: Map<number, StudentGroup>;
}

/**
 * Single source of truth for "what does setting field X to value V mean on a
 * Course" - used by both per-cell valueSetters and the "apply to selected
 * rows" bulk editor, so the two paths can't drift apart.
 *
 * Returns just the changed field(s) as a patch, or `null` to reject the edit
 * and keep the previous value (e.g. an empty/non-numeric totalHours or
 * labBlockSize). Never touches an existing Course object - callers decide how
 * to apply the patch: the per-cell valueSetter merges it onto the AG Grid row
 * (Community edition's valueSetter contract requires the row mutated for the
 * cell to refresh), while the bulk "apply to selected" editor spreads it into
 * a fresh object so `courses` stays an immutable array.
 */
function computeFieldPatch(field: BulkField, rawValue: unknown, lookups: FieldLookups): Partial<Course> | null {
  switch (field) {
    case 'subject':
      return { subject: String(rawValue ?? '') };
    case 'teacher': {
      const id = rawValue ? Number(rawValue) : null;
      return { teacher: id != null && !Number.isNaN(id) ? lookups.teachersById.get(id) ?? null : null };
    }
    case 'studentGroup': {
      const id = rawValue ? Number(rawValue) : null;
      return { studentGroup: id != null && !Number.isNaN(id) ? lookups.groupsById.get(id) ?? null : null };
    }
    case 'totalHours': {
      if (rawValue === '' || rawValue == null) return null;
      const n = Number(rawValue);
      return Number.isFinite(n) ? { totalHours: n } : null;
    }
    case 'labBlockSize': {
      if (rawValue === '' || rawValue == null) return null;
      const n = Number(rawValue);
      return Number.isFinite(n) ? { labBlockSize: n } : null;
    }
    case 'isLab':
      return { isLab: typeof rawValue === 'boolean' ? rawValue : rawValue === 'true' };
    case 'requiredRoomTags':
      return { requiredRoomTags: String(rawValue ?? '').split(',').map(s => s.trim()).filter(Boolean) };
    case 'color':
      return { color: (rawValue as string) || null };
    default:
      return null;
  }
}

interface EntityOption {
  id: number;
  label: string;
}

interface EntitySelectCellEditorParams extends ICellEditorParams<Course, number | null> {
  options: EntityOption[];
  unassignedLabel: string;
}

/**
 * Id-keyed dropdown editor for teacher/studentGroup columns. AG Grid
 * Community's built-in agSelectCellEditor only supports a flat list of
 * values with no separate id/label, which would force keying courses by
 * teacher/group NAME (risky if two ever share a name/nickname) instead of
 * their actual id. This mirrors ColorCellEditor's custom-editor pattern to
 * key by id while still displaying the teacher's nickname-aware label.
 */
const EntitySelectCellEditor = forwardRef<unknown, EntitySelectCellEditorParams>((props, ref) => {
  const [value, setValue] = useState<number | null>(props.value ?? null);

  useImperativeHandle(ref, () => ({
    getValue: () => value,
    isPopup: () => false,
  }));

  return (
    <select
      autoFocus
      value={value ?? ''}
      onChange={e => {
        setValue(e.target.value ? Number(e.target.value) : null);
        props.stopEditing();
      }}
      className="px-2 py-1 border border-border rounded text-sm bg-surface text-content w-full h-full"
    >
      <option value="">{props.unassignedLabel}</option>
      {props.options.map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
    </select>
  );
});
EntitySelectCellEditor.displayName = 'EntitySelectCellEditor';

interface ColorCellEditorParams extends ICellEditorParams<Course, string | null> {}

/**
 * Custom color-picker cell editor. AG Grid Community has no built-in color
 * editor, so this wraps the same <input type="color"> the old single-row
 * form used.
 */
const ColorCellEditor = forwardRef<unknown, ColorCellEditorParams>((props, ref) => {
  const [value, setValue] = useState<string>(props.value || '#3b82f6');

  useImperativeHandle(ref, () => ({
    getValue: () => value,
    isPopup: () => true,
  }));

  return (
    <div className="p-2 bg-surface border border-border rounded shadow-elevated">
      <input
        type="color"
        value={value}
        autoFocus
        onChange={e => setValue(e.target.value)}
        onBlur={() => props.stopEditing()}
        className="w-16 h-10 border border-border rounded cursor-pointer"
      />
    </div>
  );
});
ColorCellEditor.displayName = 'ColorCellEditor';

function ColorCellRenderer(params: ICellRendererParams<CourseRow>) {
  if (!params.data || isGroupRow(params.data)) return null;
  const color = params.data.color;
  return color ? (
    <div className="flex items-center gap-1 h-full">
      <div className="w-4 h-4 rounded border border-border shrink-0" style={{ backgroundColor: color }} />
      <span className="text-xs text-muted">{color}</span>
    </div>
  ) : <span className="text-xs text-faint">auto</span>;
}

function IsLabCellRenderer(params: ICellRendererParams<CourseRow>) {
  if (!params.data || isGroupRow(params.data)) return null;
  return (
    <input type="checkbox" checked={!!params.data.isLab} readOnly disabled className="cursor-default align-middle" />
  );
}

interface ActionsCellContext {
  onGenerateLessons: (courseId: number) => void;
  generateLabel: string;
  dirtyIds: Set<number>;
  saveFirstLabel: string;
  onDeleteRow: (courseId: number, subject: string) => void;
  deleteLabel: string;
}

function ActionsCellRenderer(params: ICellRendererParams<CourseRow> & { context: ActionsCellContext }) {
  if (!params.data || isGroupRow(params.data)) return null;
  const { id: courseId, subject } = params.data;
  const { onGenerateLessons, generateLabel, dirtyIds, saveFirstLabel, onDeleteRow, deleteLabel } = params.context;
  const isDirty = dirtyIds.has(courseId);
  return (
    <div className="flex items-center gap-1 h-full">
      <button
        className="px-2 py-1 bg-green-500 text-white border-none rounded cursor-pointer text-xs
                   hover:opacity-80 disabled:opacity-50 disabled:hover:opacity-50 disabled:cursor-not-allowed"
        onClick={() => onGenerateLessons(courseId)}
        disabled={isDirty}
        title={isDirty ? saveFirstLabel : undefined}
      >
        {generateLabel}
      </button>
      <DeleteButton data-testid="delete-course-button" skipConfirm onClick={() => onDeleteRow(courseId, subject)}>
        {deleteLabel}
      </DeleteButton>
    </div>
  );
}

export interface CourseGridProps {
  courses: Course[];
  onCoursesChange: (courses: Course[]) => void;
  teachers: Teacher[];
  studentGroups: StudentGroup[];
  filter: string;
  isSaving: boolean;
  onSave: (dirty: Course[]) => Promise<boolean>;
  onGenerateLessons: (courseId: number) => void;
  onDeleteCourses: (courseIds: number[]) => Promise<void>;
  onDiscardChanges: () => void;
}

export function CourseGrid({
  courses, onCoursesChange, teachers, studentGroups, filter, isSaving, onSave, onGenerateLessons,
  onDeleteCourses, onDiscardChanges,
}: CourseGridProps) {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const { mode } = useTheme();
  const gridApiRef = useRef<GridApi<CourseRow> | null>(null);

  const [groupBy, setGroupBy] = useState<GroupByMode>('none');
  const [dirtyIds, setDirtyIds] = useState<Set<number>>(new Set());
  const [selectedCount, setSelectedCount] = useState(0);
  const [bulkField, setBulkField] = useState<BulkField | ''>('');
  const [bulkValue, setBulkValue] = useState('');

  const teachersById = useMemo(() => new Map(teachers.map(tc => [tc.id, tc])), [teachers]);
  const groupsById = useMemo(() => new Map(studentGroups.map(g => [g.id, g])), [studentGroups]);
  const lookups = useMemo<FieldLookups>(() => ({ teachersById, groupsById }), [teachersById, groupsById]);

  const filteredCourses = useMemo(
    () => courses.filter(c => courseMatchesFilter(c, filter)),
    [courses, filter]
  );

  const markDirty = useCallback((courseId: number) => {
    setDirtyIds(prev => {
      if (prev.has(courseId)) return prev;
      const next = new Set(prev);
      next.add(courseId);
      return next;
    });
  }, []);

  // Row data: flat list of courses, or courses interleaved with synthetic
  // subtotal rows. Real row grouping/aggregation is an AG Grid Enterprise
  // feature - this manual approach replicates the Excel "Classi" rollups
  // in Community edition.
  const rowData = useMemo<CourseRow[]>(() => {
    if (groupBy === 'none') {
      return [...filteredCourses].sort((a, b) => a.subject.localeCompare(b.subject));
    }
    const keyOf = (c: Course) =>
      groupBy === 'teacher' ? (c.teacher?.name ?? null) : (c.studentGroup?.name ?? null);
    const buckets = new Map<string, Course[]>();
    for (const c of filteredCourses) {
      const key = keyOf(c) ?? t('admin:gridUnassigned');
      const bucket = buckets.get(key);
      if (bucket) bucket.push(c); else buckets.set(key, [c]);
    }
    const sortedKeys = [...buckets.keys()].sort((a, b) => a.localeCompare(b));
    const rows: CourseRow[] = [];
    for (const key of sortedKeys) {
      const bucket = buckets.get(key)!.sort((a, b) => a.subject.localeCompare(b.subject));
      const hours = bucket.reduce((sum, c) => sum + c.totalHours, 0);
      rows.push({ __group: true, id: `group:${groupBy}:${key}`, label: `${key} (${hours}h)`, hours, count: bucket.length });
      rows.push(...bucket);
    }
    return rows;
  }, [filteredCourses, groupBy, t]);

  const pinnedBottomRowData = useMemo<CourseRow[]>(() => {
    const hours = filteredCourses.reduce((sum, c) => sum + c.totalHours, 0);
    return [{ __group: true, id: 'total', label: t('admin:gridTotalHours'), hours, count: filteredCourses.length }];
  }, [filteredCourses, t]);

  const hoursByTeacher = useMemo(() => {
    const totals = new Map<string, number>();
    for (const c of filteredCourses) {
      const key = getDisplayName(c.teacher) || t('admin:gridUnassigned');
      totals.set(key, (totals.get(key) ?? 0) + c.totalHours);
    }
    return [...totals.entries()].sort((a, b) => b[1] - a[1]);
  }, [filteredCourses, t]);

  const hoursByGroup = useMemo(() => {
    const totals = new Map<string, number>();
    for (const c of filteredCourses) {
      const key = c.studentGroup?.name ?? t('admin:gridUnassigned');
      totals.set(key, (totals.get(key) ?? 0) + c.totalHours);
    }
    return [...totals.entries()].sort((a, b) => b[1] - a[1]);
  }, [filteredCourses, t]);

  const getRowId = useCallback((params: GetRowIdParams<CourseRow>) =>
    isGroupRow(params.data) ? params.data.id : `course-${params.data.id}`, []);

  const getRowStyle = useCallback((params: RowClassParams<CourseRow>): RowStyle | undefined => {
    if (!params.data) return undefined;
    if (isGroupRow(params.data)) {
      return { fontWeight: '700', background: 'var(--surface-alt)' };
    }
    if (dirtyIds.has(params.data.id)) {
      return { background: 'var(--action-bg)' };
    }
    return undefined;
  }, [dirtyIds]);

  const columnDefs = useMemo<ColDef<CourseRow>[]>(() => {
    const teacherOptions: EntityOption[] = teachers
      .map(tc => ({ id: tc.id, label: getDisplayName(tc) }))
      .sort((a, b) => a.label.localeCompare(b.label));
    const groupOptions: EntityOption[] = studentGroups
      .map(g => ({ id: g.id, label: g.name }))
      .sort((a, b) => a.label.localeCompare(b.label));
    const unassignedLabel = t('admin:gridUnassigned');

    // Every editable column follows the same shape: a guard that bails out
    // (via asCourse) for group/subtotal rows, then delegates the actual
    // patch computation to computeFieldPatch - the same helper the bulk
    // "apply to selected" toolbar uses, so the two paths can't drift apart.
    const editCol = (field: BulkField) => ({
      editable: (p: { data?: CourseRow }) => !!asCourse(p.data),
      valueSetter: (p: ValueSetterParams<CourseRow>) => {
        const course = asCourse(p.data);
        if (!course) return false;
        const patch = computeFieldPatch(field, p.newValue, lookups);
        if (!patch) return false;  // rejected (e.g. empty totalHours/labBlockSize) - keep previous value
        Object.assign(p.data as Course, patch);
        return true;
      },
    });

    const subjectCol: ColDef<CourseRow> = {
      colId: 'subject',
      headerName: t('schedule:subject'),
      minWidth: 200,
      flex: 1,
      ...editCol('subject'),
      valueGetter: (p: ValueGetterParams<CourseRow>) => asGroupRow(p.data)?.label ?? asCourse(p.data)?.subject ?? '',
    };

    const teacherCol: ColDef<CourseRow> = {
      colId: 'teacher',
      headerName: t('schedule:teacher'),
      minWidth: 150,
      ...editCol('teacher'),
      cellEditor: EntitySelectCellEditor,
      cellEditorParams: { options: teacherOptions, unassignedLabel },
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.teacher?.id ?? null,
      valueFormatter: (p: ValueFormatterParams<CourseRow>) =>
        p.value != null ? getDisplayName(teachersById.get(p.value as number)) : '—',
    };

    const groupCol: ColDef<CourseRow> = {
      colId: 'studentGroup',
      headerName: t('schedule:studentGroup'),
      minWidth: 130,
      ...editCol('studentGroup'),
      cellEditor: EntitySelectCellEditor,
      cellEditorParams: { options: groupOptions, unassignedLabel },
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.studentGroup?.id ?? null,
      valueFormatter: (p: ValueFormatterParams<CourseRow>) =>
        (p.value != null ? groupsById.get(p.value as number)?.name : null) ?? '—',
    };

    const totalHoursCol: ColDef<CourseRow> = {
      colId: 'totalHours',
      headerName: t('admin:totalHours'),
      width: 120,
      ...editCol('totalHours'),
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: { min: 0 },
      valueGetter: (p: ValueGetterParams<CourseRow>) => asGroupRow(p.data)?.hours ?? asCourse(p.data)?.totalHours ?? 0,
    };

    const labBlockSizeCol: ColDef<CourseRow> = {
      colId: 'labBlockSize',
      headerName: t('admin:labBlockSize'),
      width: 110,
      ...editCol('labBlockSize'),
      cellEditor: 'agNumberCellEditor',
      cellEditorParams: { min: 0 },
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.labBlockSize ?? 0,
    };

    const isLabCol: ColDef<CourseRow> = {
      colId: 'isLab',
      headerName: t('admin:lab'),
      width: 80,
      ...editCol('isLab'),
      cellEditor: 'agCheckboxCellEditor',
      cellRenderer: IsLabCellRenderer,
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.isLab ?? false,
    };

    const tagsCol: ColDef<CourseRow> = {
      colId: 'requiredRoomTags',
      headerName: t('admin:tags'),
      minWidth: 140,
      ...editCol('requiredRoomTags'),
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.requiredRoomTags.join(', ') ?? '',
    };

    const colorCol: ColDef<CourseRow> = {
      colId: 'color',
      headerName: t('admin:color'),
      width: 110,
      ...editCol('color'),
      cellEditor: ColorCellEditor,
      cellRenderer: ColorCellRenderer,
      valueGetter: (p: ValueGetterParams<CourseRow>) => asCourse(p.data)?.color ?? null,
    };

    const actionsCol: ColDef<CourseRow> = {
      colId: 'actions',
      headerName: t('common:actions'),
      width: 190,
      sortable: false,
      filter: false,
      cellRenderer: ActionsCellRenderer,
      valueGetter: () => '',
    };

    const selectCol: ColDef<CourseRow> = {
      colId: 'select',
      headerCheckboxSelection: true,
      headerCheckboxSelectionFilteredOnly: true,
      checkboxSelection: (p: { data?: CourseRow }) => !!asCourse(p.data),
      width: 44,
      pinned: 'left',
      sortable: false,
      filter: false,
      resizable: false,
      suppressHeaderMenuButton: true,
      valueGetter: () => '',
    };

    return [selectCol, subjectCol, teacherCol, groupCol, totalHoursCol, labBlockSizeCol, isLabCol, tagsCol, colorCol, actionsCol];
  }, [teachers, studentGroups, teachersById, groupsById, lookups, t]);

  const onCellValueChanged = useCallback((event: CellValueChangedEvent<CourseRow>) => {
    if (isGroupRow(event.data)) return;
    if (event.oldValue === event.newValue) return;
    markDirty(event.data.id);
    onCoursesChange([...courses]);
  }, [courses, onCoursesChange, markDirty]);

  const onSelectionChanged = useCallback((event: SelectionChangedEvent<CourseRow>) => {
    setSelectedCount(event.api.getSelectedRows().filter(r => !isGroupRow(r)).length);
  }, []);

  const onGridReady = useCallback((event: GridReadyEvent<CourseRow>) => {
    gridApiRef.current = event.api;
  }, []);

  const applyToSelected = useCallback(() => {
    const api = gridApiRef.current;
    if (!api || !bulkField) return;
    const selected = api.getSelectedRows().filter((r): r is Course => !isGroupRow(r));
    if (selected.length === 0) return;
    const selectedIds = new Set(selected.map(c => c.id));

    const next = courses.map(course => {
      if (!selectedIds.has(course.id)) return course;
      const patch = computeFieldPatch(bulkField, bulkValue, lookups);
      if (!patch) return course;
      markDirty(course.id);
      return { ...course, ...patch };
    });
    onCoursesChange(next);
  }, [courses, onCoursesChange, bulkField, bulkValue, markDirty, lookups]);

  const handleSave = useCallback(async () => {
    const dirty = courses.filter(c => dirtyIds.has(c.id));
    if (dirty.length === 0) return;
    const success = await onSave(dirty);
    if (success) setDirtyIds(new Set());
  }, [courses, dirtyIds, onSave]);

  const handleDiscard = useCallback(() => {
    onDiscardChanges();
    setDirtyIds(new Set());
  }, [onDiscardChanges]);

  const unmarkDirty = useCallback((ids: number[]) => {
    setDirtyIds(prev => {
      if (ids.every(id => !prev.has(id))) return prev;
      const next = new Set(prev);
      ids.forEach(id => next.delete(id));
      return next;
    });
  }, []);

  const confirmAndDelete = useCallback((ids: number[], message: string) => {
    if (!window.confirm(message)) return;
    unmarkDirty(ids);
    void onDeleteCourses(ids);
  }, [unmarkDirty, onDeleteCourses]);

  const handleDeleteRow = useCallback((courseId: number, subject: string) => {
    confirmAndDelete([courseId], `${t('admin:confirmDeleteCourse')}\n\n${subject}`);
  }, [t, confirmAndDelete]);

  const handleBulkDelete = useCallback(() => {
    const api = gridApiRef.current;
    if (!api) return;
    const selected = api.getSelectedRows().filter((r): r is Course => !isGroupRow(r));
    if (selected.length === 0) return;
    confirmAndDelete(selected.map(c => c.id), t('admin:confirmDeleteCourses', { count: selected.length }) as string);
  }, [t, confirmAndDelete]);

  const gridContext = useMemo<ActionsCellContext>(() => ({
    onGenerateLessons,
    generateLabel: t('admin:generateLessons'),
    dirtyIds,
    saveFirstLabel: t('admin:gridSaveFirst'),
    onDeleteRow: handleDeleteRow,
    deleteLabel: t('common:delete'),
  }), [onGenerateLessons, t, dirtyIds, handleDeleteRow]);

  const themeClass = mode === 'dark' ? 'ag-theme-alpine-dark' : 'ag-theme-alpine';

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <label className="flex items-center gap-2 text-sm text-muted">
          {t('admin:gridGroupBy')}
          <TableSelect value={groupBy} onChange={e => setGroupBy(e.target.value as GroupByMode)}>
            <option value="none">{t('admin:gridGroupByFlat')}</option>
            <option value="teacher">{t('admin:gridGroupByTeacher')}</option>
            <option value="group">{t('admin:gridGroupByGroup')}</option>
          </TableSelect>
        </label>

        <div className="flex items-center gap-2 text-sm" title={t('admin:gridApplyToSelectedHint') as string}>
          <TableSelect value={bulkField} onChange={e => { setBulkField(e.target.value as BulkField | ''); setBulkValue(''); }}>
            <option value="">{t('admin:gridSelectField')}</option>
            <option value="subject">{t('schedule:subject')}</option>
            <option value="teacher">{t('schedule:teacher')}</option>
            <option value="studentGroup">{t('schedule:studentGroup')}</option>
            <option value="totalHours">{t('admin:totalHours')}</option>
            <option value="labBlockSize">{t('admin:labBlockSize')}</option>
            <option value="isLab">{t('admin:isLab')}</option>
            <option value="requiredRoomTags">{t('admin:tags')}</option>
            <option value="color">{t('admin:color')}</option>
          </TableSelect>
          {bulkField === 'isLab' ? (
            <TableSelect value={bulkValue} onChange={e => setBulkValue(e.target.value)}>
              <option value="">--</option>
              <option value="true">{t('common:yes')}</option>
              <option value="false">{t('common:no')}</option>
            </TableSelect>
          ) : bulkField === 'color' ? (
            <input
              type="color"
              value={bulkValue || '#3b82f6'}
              onChange={e => setBulkValue(e.target.value)}
              className="w-9 h-9 border border-border rounded cursor-pointer"
            />
          ) : bulkField === 'teacher' ? (
            <TableSelect value={bulkValue} onChange={e => setBulkValue(e.target.value)}>
              <option value="">{t('admin:gridUnassigned')}</option>
              {teachers.map(tc => <option key={tc.id} value={tc.id}>{getDisplayName(tc)}</option>)}
            </TableSelect>
          ) : bulkField === 'studentGroup' ? (
            <TableSelect value={bulkValue} onChange={e => setBulkValue(e.target.value)}>
              <option value="">{t('admin:gridUnassigned')}</option>
              {studentGroups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
            </TableSelect>
          ) : (
            <TableInput
              className="w-[150px]"
              type={bulkField === 'totalHours' || bulkField === 'labBlockSize' ? 'number' : 'text'}
              value={bulkValue}
              onChange={e => setBulkValue(e.target.value)}
              disabled={!bulkField}
            />
          )}
          <TableButton
            variant="cancel"
            onClick={applyToSelected}
            disabled={!bulkField || selectedCount === 0}
            title={selectedCount === 0 ? (t('admin:gridNoSelection') as string) : undefined}
          >
            {t('admin:gridApplyToSelected')} ({t('admin:gridSelectedRows', { count: selectedCount })})
          </TableButton>
          <TableButton
            variant="cancel"
            onClick={handleBulkDelete}
            disabled={selectedCount === 0}
            title={selectedCount === 0 ? (t('admin:gridNoSelection') as string) : undefined}
          >
            {t('admin:gridDeleteSelected')} ({t('admin:gridSelectedRows', { count: selectedCount })})
          </TableButton>
        </div>

        <div className="flex-1" />

        <span className="text-xs text-muted">
          {dirtyIds.size > 0 ? t('admin:gridUnsavedChanges', { count: dirtyIds.size }) : t('admin:gridNoChanges')}
        </span>
        <TableButton variant="cancel" onClick={handleDiscard} disabled={isSaving || dirtyIds.size === 0}>
          {t('admin:gridDiscardChanges')}
        </TableButton>
        <TableButton onClick={handleSave} disabled={isSaving || dirtyIds.size === 0}>
          {isSaving ? '...' : t('admin:gridSaveChangesCount', { count: dirtyIds.size })}
        </TableButton>
      </div>

      <div className={themeClass} style={{ height: 520, width: '100%' }}>
        <AgGridReact<CourseRow>
          rowData={rowData}
          columnDefs={columnDefs}
          getRowId={getRowId}
          getRowStyle={getRowStyle}
          pinnedBottomRowData={pinnedBottomRowData}
          rowSelection="multiple"
          suppressRowClickSelection
          onGridReady={onGridReady}
          onCellValueChanged={onCellValueChanged}
          onSelectionChanged={onSelectionChanged}
          context={gridContext}
          animateRows={false}
        />
      </div>

      <div className="flex flex-wrap gap-6 mt-4 text-sm">
        <div>
          <h4 className="font-semibold text-content mb-1">{t('admin:gridHoursPerTeacher')}</h4>
          <ul className="text-muted space-y-0.5 max-h-[160px] overflow-y-auto pr-2">
            {hoursByTeacher.map(([name, hours]) => (
              <li key={name} className="flex justify-between gap-4 min-w-[180px]">
                <span>{name}</span><span className="font-medium text-content">{hours}h</span>
              </li>
            ))}
          </ul>
        </div>
        <div>
          <h4 className="font-semibold text-content mb-1">{t('admin:gridHoursPerGroup')}</h4>
          <ul className="text-muted space-y-0.5 max-h-[160px] overflow-y-auto pr-2">
            {hoursByGroup.map(([name, hours]) => (
              <li key={name} className="flex justify-between gap-4 min-w-[180px]">
                <span>{name}</span><span className="font-medium text-content">{hours}h</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
