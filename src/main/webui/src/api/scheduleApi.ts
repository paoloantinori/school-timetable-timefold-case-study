import axios from 'axios';
import type {
  SchoolSchedule, Room, Teacher, Course, SchoolHoliday,
  StudentGroup, StagePattern, TeacherAvailability, Lesson,
  ConstraintAnalysis, PreAssignmentResult, PreAssignmentStatusResponse,
  CreateCourseInput, CreateTeacherAvailabilityInput, CreateStagePatternInput,
  LessonViolationAnalysis, LabAssistant, CreateLabAssistantInput, CreateProfessionalizzantiDayInput, FeasibilityReport,
  PhaseRecord, SolverTimeoutConfig, ProfessionalizzantiDay, SlotUtilization, BatchCourseUpdate,
  GroupCalendarRule, CreateGroupCalendarRuleInput, SlotSchema, ResolvedGroupCalendarEntry,
  RecurringAvailabilityRule, CreateRecurringAvailabilityRuleInput,
  ReconciliationReport, CourseLessonSummary,
  ValidationResult
} from '../types/domain';

const api = axios.create({
  baseURL: '/api',
  timeout: 300000,  // 5 minutes - violation analysis can be slow with large schedules
});

// Schedule
export const getSchedule = (): Promise<SchoolSchedule> =>
  api.get<SchoolSchedule>('/schedule').then(r => r.data);

/** Lightweight in-memory status poll (TASK-380) — no lessons, no DB, <100ms. */
export interface ScheduleStatusLight {
  solverStatus: string;
  score: string | null;
  lastSolverError: string | null;
  preAssignmentStatus: string;
  preAssignmentError: string | null;
  currentPhase: number;
  currentPhaseName: string;
  /** TASK-419: non-null while a staged solve cycle is running - see SchoolSchedule.stagedStage. */
  stagedStage: string | null;
  /** TASK-483: multi-start attempt X-of-Y; both 0 when multi-start is off (UI hides the indicator). */
  stagedAttempt: number;
  stagedMaxAttempts: number;
  /** TASK-483: multi-start time-budget cap (seconds), 0 when unset. */
  stagedBudgetSeconds: number;
  /** TASK-661: true while the final delivery refinement runs; goes false (or never true) when the delivery is saved. */
  refiningForDelivery: boolean;
  /** TASK-667: terminal outcome of the last staged cycle (COMPLETE | CANCELLED | FAILED), null while running. */
  stagedOutcome: string | null;
  /** TASK-667: true while the async post-restart score restore is in flight (false once done or failed). */
  scoreRestorePending: boolean;
}
export const getScheduleStatus = (): Promise<ScheduleStatusLight> =>
  api.get<ScheduleStatusLight>('/schedule/status').then(r => r.data);
/**
 * The UI drives the CAMPAIGN protocol (2026-08-15, maintainer: the UI is the main interaction
 * channel): the staged cycle with the whole ratified orchestration (pre-assign ensure,
 * multi-start stop-at-first-0hard, MT-LS, repair passes). The plain /solve stays a
 * debug/comparison endpoint the UI deliberately does not expose.
 */
export const startSolving = (): Promise<void> =>
  api.post('/schedule/solve-staged');
export const stopSolving = (): Promise<void> =>
  api.delete('/schedule/solve');
export const reschedule = (teacherId: number, date: string): Promise<void> =>
  api.post('/schedule/reschedule', { teacherId, date });
export const analyzeSchedule = (): Promise<ConstraintAnalysis> =>
  api.get<ConstraintAnalysis>('/schedule/analysis').then(r => r.data);

// Solution legality watchdog (TASK-354)
export const getValidationLatest = (): Promise<ValidationResult | null> =>
  api.get<ValidationResult>('/schedule/validation/latest', { validateStatus: s => s === 200 || s === 204 })
    .then(r => (r.status === 204 ? null : r.data));
export const runValidation = (): Promise<ValidationResult> =>
  api.post<ValidationResult>('/schedule/validation/run').then(r => r.data);
export interface DateRange {
  start: string;  // ISO date string: "2026-02-01"
  end: string;    // ISO date string: "2026-02-07"
}

export const analyzeLessonViolations = (
  dateRangeOrConstraint?: DateRange | string,
  secondArg?: string | DateRange
): Promise<LessonViolationAnalysis[]> => {
  const params = new URLSearchParams();

  // Handle overloaded parameters:
  // - analyzeLessonViolations()
  // - analyzeLessonViolations(dateRange)
  // - analyzeLessonViolations(constraintId)
  // - analyzeLessonViolations(dateRange, constraintId)
  // - analyzeLessonViolations(dateRange, lang)
  // - analyzeLessonViolations(constraintId, lang)

  let dateRange: DateRange | undefined;
  let constraintId: string | undefined;
  let lang: string | undefined;

  // Parse arguments based on type
  if (typeof dateRangeOrConstraint === 'string') {
    // First arg is constraint ID
    constraintId = dateRangeOrConstraint;
    if (typeof secondArg === 'string') {
      lang = secondArg;
    } else if (secondArg) {
      dateRange = secondArg;
    }
  } else if (dateRangeOrConstraint) {
    // First arg is DateRange
    dateRange = dateRangeOrConstraint;
    if (typeof secondArg === 'string') {
      // Could be lang or constraintId - heuristics:
      // If it looks like a constraint ID (H/M/S followed by numbers), treat as constraint
      if (/^[HMS]\d/.test(secondArg)) {
        constraintId = secondArg;
      } else {
        lang = secondArg;
      }
    } else if (secondArg) {
      dateRange = secondArg;
    }
  }

  if (dateRange) {
    params.append('startDate', dateRange.start);
    params.append('endDate', dateRange.end);
  }
  if (constraintId) {
    params.append('constraint', constraintId);
  }
  if (lang) {
    params.append('lang', lang);
  }

  const query = params.toString() ? `?${params.toString()}` : '';
  return api.get<LessonViolationAnalysis[]>(`/schedule/analysis/lessons${query}`).then(r => r.data);
};

// Lessons
export const togglePin = (id: number): Promise<Lesson> =>
  api.put<Lesson>(`/lessons/${id}/pin`).then(r => r.data);
export const assignLesson = (id: number, timeslotId: number | null, roomId: number | null): Promise<Lesson> =>
  api.put<Lesson>(`/lessons/${id}/assign`, { timeslotId, roomId }).then(r => r.data);

// Rooms
export const getRooms = (): Promise<Room[]> =>
  api.get<Room[]>('/rooms').then(r => r.data);
export const createRoom = (room: Partial<Room>): Promise<Room> =>
  api.post<Room>('/rooms', room).then(r => r.data);
export const updateRoom = (id: number, room: Partial<Room>): Promise<Room> =>
  api.put<Room>(`/rooms/${id}`, room).then(r => r.data);
export const deleteRoom = (id: number): Promise<void> =>
  api.delete(`/rooms/${id}`);

// Teachers
export const getTeachers = (): Promise<Teacher[]> =>
  api.get<Teacher[]>('/teachers').then(r => r.data);
export const createTeacher = (t: Partial<Teacher>): Promise<Teacher> =>
  api.post<Teacher>('/teachers', t).then(r => r.data);
export const updateTeacher = (id: number, t: Partial<Teacher>): Promise<Teacher> =>
  api.put<Teacher>(`/teachers/${id}`, t).then(r => r.data);
export const deleteTeacher = (id: number): Promise<void> =>
  api.delete(`/teachers/${id}`);

// Student Groups
export const getStudentGroups = (): Promise<StudentGroup[]> =>
  api.get<StudentGroup[]>('/student-groups').then(r => r.data);
export const createStudentGroup = (g: Partial<StudentGroup>): Promise<StudentGroup> =>
  api.post<StudentGroup>('/student-groups', g).then(r => r.data);
export const updateStudentGroup = (id: number, g: Partial<StudentGroup>): Promise<StudentGroup> =>
  api.put<StudentGroup>(`/student-groups/${id}`, g).then(r => r.data);
export const deleteStudentGroup = (id: number): Promise<void> =>
  api.delete(`/student-groups/${id}`);

// Courses
export const getCourses = (): Promise<Course[]> =>
  api.get<Course[]>('/courses').then(r => r.data);
export const createCourse = (c: CreateCourseInput): Promise<Course> =>
  api.post<Course>('/courses', c).then(r => r.data);
export const deleteCourse = (id: number): Promise<void> =>
  api.delete(`/courses/${id}`);
export const batchUpdateCourses = (courses: BatchCourseUpdate[]): Promise<Course[]> =>
  api.put<Course[]>('/courses/batch', courses).then(r => r.data);
export const generateLessons = (courseId: number): Promise<{ lessonsGenerated: number }> =>
  api.post<{ lessonsGenerated: number }>(`/courses/${courseId}/generate-lessons`).then(r => r.data);

// Holidays
export const getHolidays = (): Promise<SchoolHoliday[]> =>
  api.get<SchoolHoliday[]>('/holidays').then(r => r.data);
export const createHoliday = (h: Partial<SchoolHoliday>): Promise<SchoolHoliday> =>
  api.post<SchoolHoliday>('/holidays', h).then(r => r.data);
export const deleteHoliday = (id: number): Promise<void> =>
  api.delete(`/holidays/${id}`);

// Professionalizzanti Days
export const getProfessionalizzantiDays = (): Promise<ProfessionalizzantiDay[]> =>
  api.get<ProfessionalizzantiDay[]>('/professionalizzanti-days').then(r => r.data);
export const createProfessionalizzantiDay = (d: CreateProfessionalizzantiDayInput): Promise<ProfessionalizzantiDay> =>
  api.post<ProfessionalizzantiDay>('/professionalizzanti-days', d).then(r => r.data);
export const deleteProfessionalizzantiDay = (id: number): Promise<void> =>
  api.delete(`/professionalizzanti-days/${id}`);

// Stage Patterns
export const getStagePatterns = (): Promise<StagePattern[]> =>
  api.get<StagePattern[]>('/stage-patterns').then(r => r.data);
export const createStagePattern = (sp: CreateStagePatternInput): Promise<StagePattern> =>
  api.post<StagePattern>('/stage-patterns', sp).then(r => r.data);
export const deleteStagePattern = (id: number): Promise<void> =>
  api.delete(`/stage-patterns/${id}`);

// Teacher Availability
export const getTeacherAvailability = (): Promise<TeacherAvailability[]> =>
  api.get<TeacherAvailability[]>('/teacher-availability').then(r => r.data);
export const createTeacherAvailability = (ta: CreateTeacherAvailabilityInput): Promise<TeacherAvailability> =>
  api.post<TeacherAvailability>('/teacher-availability', ta).then(r => r.data);
export const deleteTeacherAvailability = (id: number): Promise<void> =>
  api.delete(`/teacher-availability/${id}`);

// Recurring Availability Rules (TASK-603): weekly teacher availability grid
export const getRecurringAvailabilityRules = (): Promise<RecurringAvailabilityRule[]> =>
  api.get<RecurringAvailabilityRule[]>('/recurring-availability-rules').then(r => r.data);
export const createRecurringAvailabilityRule = (input: CreateRecurringAvailabilityRuleInput): Promise<RecurringAvailabilityRule> =>
  api.post<RecurringAvailabilityRule>('/recurring-availability-rules', input).then(r => r.data);
export const deleteRecurringAvailabilityRule = (id: number): Promise<void> =>
  api.delete(`/recurring-availability-rules/${id}`);

// TASK-603: atomic bulk replace of one teacher's always-window rules (the grid
// editor's save); date-windowed rules are not touched by the endpoint.
export const replaceAlwaysAvailabilityRules = (
  teacherId: number,
  runs: Array<Pick<CreateRecurringAvailabilityRuleInput, 'dayOfWeek' | 'minSlot' | 'maxSlot' | 'type' | 'note'>>,
): Promise<RecurringAvailabilityRule[]> =>
  api.put<RecurringAvailabilityRule[]>(`/recurring-availability-rules/teacher/${teacherId}`, runs)
    .then(r => r.data);

// Pre-Assignment (CSP-based hybrid approach)
export const preAssign = (): Promise<PreAssignmentResult> =>
  api.post('/schedule/pre-assign').then(r => r.data);
export const getPreAssignmentStatus = (): Promise<PreAssignmentStatusResponse> =>
  api.get('/schedule/pre-assign/status').then(r => r.data);
export const resetPreAssignmentStatus = (): Promise<PreAssignmentResult> =>
  api.post('/schedule/pre-assign/reset').then(() => ({
    message: 'Status reset to IDLE',
    status: 'IDLE' as const,
    currentPhase: null,
    candidatesTried: 0,
    error: null,
    failureAnalysis: null,
  }));

// Full schedule reset (clears all assignments)
export const resetSchedule = (): Promise<{ message: string }> =>
  api.post('/schedule/reset').then(r => r.data);

export const getFeasibility = (): Promise<FeasibilityReport> =>
  api.get<FeasibilityReport>('/schedule/feasibility').then(r => r.data);

// Export/Import for A/B testing solver configurations
export const exportSchedule = async (): Promise<Blob> => {
  const response = await api.get('/schedule/export', {
    responseType: 'blob'
  });
  return response.data;
};

export const importSchedule = async (schedule: any): Promise<{ message: string }> => {
  const response = await api.post('/schedule/import', schedule);
  return response.data;
};

// Lab Assistants
export const getLabAssistants = (): Promise<LabAssistant[]> =>
  api.get<LabAssistant[]>('/lab-assistants').then(r => r.data);
export const createLabAssistant = (input: CreateLabAssistantInput): Promise<LabAssistant> =>
  api.post<LabAssistant>('/lab-assistants', input).then(r => r.data);
export const deleteLabAssistant = (id: number): Promise<void> =>
  api.delete(`/lab-assistants/${id}`);

// CSV Import
export const importCsv = (entityType: string, file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post<{ imported: number; entityType: string }>(`/import/${entityType}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }).then(r => r.data);
};

// Phase Progression
export const getPhaseProgression = (): Promise<PhaseRecord[]> =>
  api.get<PhaseRecord[]>('/phase-progression').then(r => r.data);

export const getPhaseProgressionConfig = (): Promise<SolverTimeoutConfig> =>
  api.get<SolverTimeoutConfig>('/phase-progression/config').then(r => r.data);

export const exportPhaseProgression = async (): Promise<Blob> => {
  const response = await api.get('/phase-progression/export', {
    responseType: 'blob'
  });
  return response.data;
};

// Student Group Completion
export interface CompletionResponse {
  groups: Array<{
    groupName: string;
    totalCurriculumHours: number;
    stageDaysCount: number;
    hoursBeforeStage: number;
    hoursDuringStage: number;
    hoursAfterStage: number;
    actualDaysWithLessons: number;
    actualEndDate: string;
    idealEndDate: string;
    daysDifference: number;
  }>;
  slotUtilization: SlotUtilization[];
  totalGroups: number;
}

export const getCompletionData = (): Promise<CompletionResponse> =>
  api.get<CompletionResponse>('/completion').then(r => r.data);

// Slot schema (TASK-358): data-driven time periods, derived from TimeslotSequence.SLOT_START_TIMES
export const getSlotSchema = (): Promise<SlotSchema> =>
  api.get<SlotSchema>('/config/slots').then(r => r.data);

// Group Calendar Rules (TASK-342/358): per-group, per-weekday slot-level calendar
export const getGroupCalendarRules = (): Promise<GroupCalendarRule[]> =>
  api.get<GroupCalendarRule[]>('/group-calendar-rules').then(r => r.data);
export const createGroupCalendarRule = (r: CreateGroupCalendarRuleInput): Promise<GroupCalendarRule> =>
  api.post<GroupCalendarRule>('/group-calendar-rules', r).then(res => res.data);
export const updateGroupCalendarRule = (id: number, r: CreateGroupCalendarRuleInput): Promise<GroupCalendarRule> =>
  api.put<GroupCalendarRule>(`/group-calendar-rules/${id}`, r).then(res => res.data);
export const deleteGroupCalendarRule = (id: number): Promise<void> =>
  api.delete(`/group-calendar-rules/${id}`);

// Resolved per-group, per-date opening ranges (TASK-358 code review N2) - replaces
// the client-side port of GroupCalendarService's resolution algorithm.
export const getResolvedGroupCalendar = (from: string, to: string): Promise<ResolvedGroupCalendarEntry[]> =>
  api.get<ResolvedGroupCalendarEntry[]>('/group-calendar-rules/resolved', { params: { from, to } }).then(r => r.data);

// Reconciliation dashboard (TASK-353): assigned vs scheduled hours, replicates the school's Classi sheet
export const getReconciliation = (): Promise<ReconciliationReport> =>
  api.get<ReconciliationReport>('/reconciliation').then(r => r.data);
export const getReconciliationLessons = (courseId: number): Promise<CourseLessonSummary[]> =>
  api.get<CourseLessonSummary[]>(`/reconciliation/${courseId}/lessons`).then(r => r.data);

// ─── TASK-528: constraints inventory (one aggregate call) ─────────────────────

export interface ConstraintRow {
  who: string;
  what: string;
  value: string;
  source: string;
}

export interface ConstraintCategory {
  id: string;
  title: string;
  count: number;
  detail?: unknown;
  provenance?: string;
  source?: string;
  emptyMessage?: string;
  rows?: ConstraintRow[];
}

export interface ConstraintSummary {
  categories: ConstraintCategory[];
  daConfermare?: ConstraintRow[];
  generatedNote?: string;
}

export async function fetchConstraintSummary(): Promise<ConstraintSummary> {
  return api.get<ConstraintSummary>('/constraints/summary').then(r => r.data);
}
