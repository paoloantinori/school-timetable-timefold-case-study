export interface Timeslot {
  id: number;
  startDateTime: string;
  endDateTime: string;
}

export interface Room {
  id: number;
  name: string;
  capacity: number;
  tags: string[];
}

export interface Teacher {
  id: number;
  name: string;
  nickname?: string | null;  // Optional display name (max 20 chars)
  color?: string | null;  // Custom hex color for teacher display (e.g., "#ec4899")
  pattern?: string | null;  // Visual pattern: "plain", "dotted", "hatched", "striped"
  maxDailyHours?: number | null;  // Per-teacher override of H7's max daily hours (1-8); null = default (6)
  /** Synthetic accounting teacher (Stage/Esame/RA, TASK-346) - exempt from M28's afternoon preference (was H21). */
  pseudo?: boolean;
  // TASK-368: backend/CSV-seeded only (teachers.csv columns 4-5, TeacherResource CRUD) -
  // deliberately no TeachersAdmin.tsx UI yet (only 2 named teachers use these today,
  // same as maxDailyHours was CSV-only before TASK-348 added its UI field).
  maxEarlyStartDaysPerWeek?: number | null;  // Per-teacher M24 cap: distinct slot-0 days/week (1-5); null = uncapped
  requiredFreeDaysPerWeek?: number | null;  // Per-teacher M25 requirement: Mon-Fri zero-lesson days/week (1-5); null = unrequired
}

/**
 * Extended information about the ideal end date calculation for a student group.
 * Computed server-side and exposed via REST API for UI display.
 * TASK-109: Show ideal last day of school for student groups.
 */
export interface IdealEndDateInfo {
  /** The earliest date when all required hours can be scheduled */
  idealEndDate: string;
  /** Total required course hours for this group */
  totalHours: number;
  /** Number of school days excluded (holidays + stage days) */
  excludedDays: number;
  /** Number of available school days for scheduling */
  availableDays: number;
}

/** Completion information for a student group */
export interface CompletionInfo {
  /** The last day any lesson exists for this group */
  lastLessonDate: string;  // ISO date string
  /** The number of lessons on the final day */
  finalDayLessonCount: number;
  /** Whether this represents perfect horizontal compaction (no sparse days) */
  isPerfectlyCompacted: boolean;
  /** The first school day after the last lesson (completion marker) */
  completionDate: string;  // ISO date string
  /** Human-readable label for the completion marker */
  label: string;
}

export interface StudentGroup {
  id: number;
  name: string;
  size: number;
  maxHoursPerDay: number | null;
  startDate: string | null;  // ISO date string, null = starts from school year beginning
  idealEndDateInfo?: IdealEndDateInfo;  // Extended calculation data from server
  allowProfessionalizzantiDuringStage: boolean;  // TASK-272: Per-group setting for professionalizzanti courses during stage
  completionInfo?: CompletionInfo;  // Completion data (last lesson day, compaction metrics)
}

export interface Course {
  id: number;
  subject: string;
  teacher: Teacher | null;
  studentGroup: StudentGroup | null;
  totalHours: number;
  isLab: boolean;
  labBlockSize: number;  // Consecutive-hour block size for lab/block courses (0/1 = single lessons)
  requiredRoomTags: string[];
  color?: string | null;  // Custom hex color for subject display (e.g., "#3b82f6")
  professionalizzanti?: boolean;  // Stage-related course
}

export interface SchoolHoliday {
  id: number;
  date: string;
  description: string;
}

export interface StagePattern {
  id: number;
  studentGroup: StudentGroup | null;
  startDate: string;
  endDate: string;
  dayOfWeekMask: string[];
}

export interface TeacherAvailability {
  id: number;
  teacher: Teacher | null;
  startDateTime: string;
  endDateTime: string;
  type: 'SICK' | 'VACATION' | 'PREFERENCE';
}

export interface LabAssistant {
  id: number;
  teacher: Teacher | null;
  roomTag: string;
}

/**
 * Per-group, per-weekday slot-level calendar opening pattern (TASK-342).
 * A closed day is represented as maxSlot < minSlot (see GroupCalendarRule.java).
 * effectiveFrom/effectiveTo null means the rule applies to every date ("base" rule);
 * a non-null date range is a one-off override (e.g. a shortened first school day).
 */
export interface GroupCalendarRule {
  id: number;
  studentGroup: StudentGroup | null;
  dayOfWeek: string;  // 'MONDAY'..'FRIDAY'
  minSlot: number;
  maxSlot: number;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}

export interface CreateGroupCalendarRuleInput {
  studentGroupId: number | null;
  dayOfWeek: string;
  minSlot: number;
  maxSlot: number;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
}

/** One slot's schema, from /api/config/slots (TASK-341's 8-slot/day calendar). */
export interface SlotDefinition {
  index: number;
  startTime: string;  // "08:00"
  endTime: string;    // "09:00"
  isAfternoon: boolean;
}

export interface SlotSchema {
  slots: SlotDefinition[];
  lunchBreakStart: string;
  lunchBreakEnd: string;
}

/**
 * One resolved [minSlot, maxSlot] opening range for a student group on a
 * single date, from GET /api/group-calendar-rules/resolved (TASK-358 code
 * review N2). Replaces the frontend's own port of GroupCalendarService's
 * most-specific-rule-wins resolution - the backend now resolves for a whole
 * date range in one call.
 */
export interface ResolvedGroupCalendarEntry {
  groupId: number;
  date: string;  // "yyyy-MM-dd"
  minSlot: number;
  maxSlot: number;
  closed: boolean;
}

export interface ProfessionalizzantiDay {
  id: number;
  studentGroup: StudentGroup | null;
  date: string;
  dayOfWeek: string;
  description: string;
}

export interface Lesson {
  id: number;
  course: Course | null;
  pinned: boolean;
  timeslot: Timeslot | null;
  room: Room | null;
}

export type PreAssignmentStatus =
  | 'IDLE'
  | 'ACCONCIATURA'
  | 'LABS'
  | 'PROFESSIONALIZZANTI'
  | 'COMPLETE'
  | 'ERROR';

export interface SchoolSchedule {
  timeslots: Timeslot[];
  rooms: Room[];
  stagePatterns: StagePattern[];
  teacherAvailabilities: TeacherAvailability[];
  schoolHolidays: SchoolHoliday[];
  studentGroups: StudentGroup[];
  lessons: Lesson[];
  score: string | null;
  /** Non-null when the last solve DIED (exception) instead of terminating (TASK-378). */
  lastSolverError?: string | null;
  solverStatus: 'NOT_SOLVING' | 'SOLVING_ACTIVE' | 'SOLVING_SCHEDULED';
  /**
   * Non-null only while a staged solve (TASK-419, POST /solve-staged) is running:
   * A-CONSTRUCTION, PROMOTION, B-TIMESLOT-<n>, C-DEMOTION-<n>, RELEASE, B-ROOM.
   * Every stage runs on an ad-hoc solver, so `solverStatus` reports NOT_SOLVING
   * for the whole cycle - poll this field instead to detect an in-progress cycle.
   */
  stagedStage: string | null;
  /** TASK-661: true during the final delivery refinement; false (or never true) once the delivery is saved. */
  refiningForDelivery?: boolean;
  /** TASK-667: terminal outcome of the last staged cycle (COMPLETE | CANCELLED | FAILED), null while running. */
  stagedOutcome?: string | null;
  /** TASK-667: server-declared in-flight score restore after a restart (false once done or failed). */
  scoreRestorePending?: boolean;
  /** TASK-483: current multi-start attempt (1-based) and cap; both 0 when multi-start is off. */
  stagedAttempt: number;
  stagedMaxAttempts: number;
  /** TASK-483: multi-start time-budget cap (seconds), 0 when unset. */
  stagedBudgetSeconds: number;
  solveStartTime: string | null;
  chCompletionTime: string | null;
  cumulativeElapsedSeconds: number;
  cumulativeChSeconds: number;
  cumulativeLsSeconds: number;
  preAssignmentDurationSeconds: number;
  preAssignmentStatus: PreAssignmentStatus | null;
  preAssignmentStartTime: string | null;
  preAssignmentError: string | null;
  preAssignmentFailureAnalysis: FailureAnalysis | null;
  // Solver phase tracking (1-5, 0 = not started, 1-5 = active phase)
  currentPhase: number;
  // Human-readable phase name (e.g., "Lab Block Placement")
  phaseName: string | null;
}

export interface ConstraintMatch {
  constraint: string;
  score: string;
  hard: number;
  /** Hard level 1 (TASK-538 quasi tier): H34/H32 when planner.constraints.quasi-tier.enabled, 0 otherwise. */
  quasi: number;
  medium: number;
  soft: number;
  matches: number;
}

// Per-lesson violation analysis (from /api/schedule/analysis/lessons)
export interface ConstraintViolation {
  constraint: string;   // e.g., "H1.1"
  level: 'HARD' | 'MEDIUM' | 'SOFT';
  score: number;
  message: string | null;
  justification?: AnyJustification;  // TASK-244 Phase 3: Structured justification data
}

export interface LessonViolationAnalysis {
  lessonId: number;
  entityType: 'Lesson';
  teacher: string | null;
  subject: string | null;
  studentGroup: string | null;
  timeslot: string | null;
  room: string | null;
  hasViolations: boolean;
  totalScore: string;
  violations: ConstraintViolation[];
}

// For O(1) lookup in components
export interface ViolationInfo {
  lessonId: number;
  violations: ConstraintViolation[];
  hasHard: boolean;
}

export interface ConstraintAnalysis {
  score: string;
  hard: number;
  medium: number;
  soft: number;
  constraints: ConstraintMatch[];
}

/** Feasibility report: binary verdict (hard == 0), message carries a stable code */
export interface FeasibilityReport {
  feasible: boolean;
  message: string;
  score: string | null;
  hard: number | null;
  medium: number | null;
  soft: number | null;
}

/** Conflict pattern from pre-assignment failure analysis */
export interface ConflictPattern {
  /** Machine-readable pattern type (e.g., NO_VALID_DAY, INSUFFICIENT_CAPACITY) */
  type: string;
  /** Human-readable description of the conflict pattern */
  description: string;
  /** Number of times this pattern occurred */
  occurrenceCount: number;
  /** Example instances of this pattern */
  examples: string[];
}

/** Detailed failure analysis from pre-assignment pipeline */
export interface FailureAnalysis {
  /** The phase that failed (e.g., "1-ACCONCIATURA") */
  phase: string;
  /** High-level reason for failure */
  reason: string;
  /** Teacher names involved in conflicts */
  conflictingTeacherNames: string[];
  /** Student group names involved in conflicts */
  conflictingGroupNames: string[];
  /** Date range of the rotation period */
  periodRange: string;
  /** Actionable suggestion for resolving the issue */
  suggestion: string;
  /** Specific conflict patterns with examples */
  conflictPatterns: ConflictPattern[];
}

// Pre-Assignment Status Response (from hybrid CSP endpoint)
export interface PreAssignmentStatusResponse {
  message: string;
  status: 'IDLE' | 'RUNNING' | 'COMPLETE' | 'ERROR';
  currentPhase: string | null;
  candidatesTried: number;
  error: string | null;
  failureAnalysis: FailureAnalysis | null;
}

export type PreAssignmentResult = PreAssignmentStatusResponse;

// ============================================================================
// Solution Legality Watchdog (TASK-354)
// ============================================================================

/** Match count and score contribution for a single constraint, from a validation run. */
export interface ValidationConstraintCount {
  constraintId: string;
  matchCount: number;
  hard: number;
  medium: number;
  soft: number;
}

/** A constraint whose matchCount increased versus the previous validation snapshot. */
export interface ValidationViolationDelta {
  constraintId: string;
  previousCount: number;
  currentCount: number;
  delta: number;
  /** Hard-score contribution, carried over from the matching perConstraint entry
   *  so consumers don't need to re-join against perConstraint. Negative = hard violation. */
  hard: number;
}

/** Result of a watchdog validation run (GET/POST /api/schedule/validation/*). */
export interface ValidationResult {
  timestamp: string;
  trigger: string;
  score: string;
  hard: number;
  medium: number;
  soft: number;
  perConstraint: ValidationConstraintCount[];
  newlyViolated: ValidationViolationDelta[];
  /** True when there was no previous snapshot to diff against - newlyViolated is
   *  always empty in that case (nothing legitimate to compare it to), not a signal
   *  that the schedule is actually clean. */
  baseline: boolean;
}

// ============================================================================
// Justification Types (TASK-244 Phase 3)
// ============================================================================

/**
 * Base interface for constraint justification data.
 * The '@type' field is a discriminator set by Jackson's @JsonTypeInfo annotation.
 */
export interface Justification {
  '@type': string;
}

/** H1: Teacher conflict justification */
export interface TeacherConflictJustification extends Justification {
  '@type': 'TeacherConflict';
  teacherId: number;
  lesson1Id: number;
  lesson2Id: number;
}

/** H2: Room conflict justification */
export interface RoomConflictJustification extends Justification {
  '@type': 'RoomConflict';
  roomId: number;
  lesson1Id: number;
  lesson2Id: number;
}

/** H3: Student group conflict justification */
export interface StudentGroupConflictJustification extends Justification {
  '@type': 'StudentGroupConflict';
  groupId: number;
  lesson1Id: number;
  lesson2Id: number;
}

/** H7: Teacher daily hours justification */
export interface TeacherDailyHoursJustification extends Justification {
  '@type': 'TeacherDailyHours';
  teacherId: number;
  date: string;
  lessonCount: number;
  maxHours: number;
  excessHours: number;
}

/** H11: Student group daily hours justification */
export interface StudentGroupDailyHoursJustification extends Justification {
  '@type': 'StudentGroupDailyHours';
  groupId: number;
  date: string;
  lessonCount: number;
  maxHours: number;
  excessHours: number;
}

/** M20: Sparse day justification */
export interface SparseDayJustification extends Justification {
  '@type': 'SparseDay';
  groupId: number;
  date: string;
  actualHours: number;
  requiredHours: number;
}

/** Union type for all justification types */
export type AnyJustification =
  | TeacherConflictJustification
  | RoomConflictJustification
  | StudentGroupConflictJustification
  | TeacherDailyHoursJustification
  | StudentGroupDailyHoursJustification
  | SparseDayJustification;

// API Input Types

// API Input Types

export interface CreateCourseInput {
  subject: string;
  teacherId: number | null;
  studentGroupId: number | null;
  totalHours: number;
  isLab: boolean;
  labBlockSize?: number;
  requiredRoomTags: string[];
  color?: string | null;  // Custom hex color for subject display
}

/** Course-hours grid editor (TASK-356): one row of a batch save request. */
export interface BatchCourseUpdate extends CreateCourseInput {
  id: number;
  labBlockSize: number;
}

export interface CreateTeacherAvailabilityInput {
  teacherId: number | null;
  startDateTime: string;
  endDateTime: string;
  type: 'SICK' | 'VACATION' | 'PREFERENCE';
}

/**
 * Weekly-recurring teacher availability rule (RecurringAvailabilityRule.java).
 * VACATION expands into hard H5 unavailability, PREFERENCE into the soft S3
 * penalty; the model is additive, so the REST layer rejects overlapping rules
 * for the same teacher. effectiveFrom/effectiveTo null means "always".
 */
export interface RecurringAvailabilityRule {
  id: number;
  teacher: Teacher | null;
  dayOfWeek: string;  // 'MONDAY'..'FRIDAY' (java.time.DayOfWeek name)
  minSlot: number;
  maxSlot: number;
  type: 'VACATION' | 'PREFERENCE';
  effectiveFrom: string | null;
  effectiveTo: string | null;
  note: string | null;
}

export interface CreateRecurringAvailabilityRuleInput {
  teacherId: number;
  dayOfWeek: string;
  minSlot: number;
  maxSlot: number;
  type: 'VACATION' | 'PREFERENCE';
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  note?: string;
}

export interface CreateStagePatternInput {
  studentGroupId: number | null;
  startDate: string;
  endDate: string;
  dayOfWeekMask: string[];
}

export interface CreateLabAssistantInput {
  teacherId: number | null;
  roomTag: string;
}

export interface CreateProfessionalizzantiDayInput {
  studentGroupId: number | null;
  date: string;
  dayOfWeek: string;
  description?: string;
}

/**
 * Phase progression record from PhaseProgressionService.
 * Tracks solver phases with timing and score data.
 */
export interface PhaseRecord {
  phaseNumber: number;
  phaseName: string;
  startTime: string;  // ISO timestamp
  endTime?: string | null;  // ISO timestamp, null if still running
  startScore: string;  // Score string like "-1000hard/-200medium/-300soft"
  endScore?: string | null;  // Final score for completed phases
  timeSpentMillis: number;  // Duration in milliseconds
  isComplete: boolean;
  // Timeout configuration from solverConfig.xml
  secondsSpentLimit?: number;  // Hard timeout in seconds (null if no limit)
  unimprovedSecondsSpentLimit?: number;  // Smart termination threshold (null if no limit)
}

/**
 * Solver timeout configuration from /api/phase-progression/config
 */
export interface SolverTimeoutConfig {
  overallSecondsSpentLimit: number;  // Overall solver timeout in seconds (e.g., 28800 = 8 hours)
}

/**
 * Slot utilization data for a student group.
 * Shows how many valid slots are available vs how many are assigned.
 * Used to identify sparse calendars at a glance.
 */
export interface SlotUtilization {
  groupId: number;
  groupName: string;
  totalValidSlots: number;
  assignedSlots: number;
  unassignedSlots: number;
  utilizationPercentage: number;
}

/**
 * Reconciliation dashboard (TASK-353): replicates the school's "Classi"
 * Excel sheet, comparing assigned hours (Course.totalHours) against
 * scheduled hours (lessons with a placed timeslot) per
 * (teacher, subject, group).
 */
export type ReconciliationStatus = 'OK' | 'MISMATCH' | 'UNSCHEDULED';

export interface ReconciliationRow {
  courseId: number;
  teacherId: number | null;
  teacherName: string | null;
  subject: string;
  groupId: number | null;
  groupName: string | null;
  assignedHours: number;
  scheduledHours: number;
  pinnedHours: number;
  delta: number;
  /**
   * Of `delta`, how many hours are structurally unschedulable by design
   * (TASK-373 code review H1/H4) - e.g. the golden Stage course's
   * closure-day and beyond-Timeslot-horizon rows. `delta -
   * expectedUnscheduledHours` is the actionable remainder a UI should judge.
   */
  expectedUnscheduledHours: number;
  status: ReconciliationStatus;
  /** True for synthetic Stage/Esame/RA accounting courses - expected deltas, not alarms. */
  pseudo: boolean;
}

export interface ReconciliationRollup {
  id: number;
  name: string;
  assignedHours: number;
  scheduledHours: number;
  delta: number;
  /** Count of non-OK, non-pseudo rows folded into this rollup - drives subtotal severity. */
  issueCount: number;
}

export interface ReconciliationSummary {
  okCount: number;
  mismatchCount: number;
  unscheduledCount: number;
  totalCourses: number;
}

export interface ReconciliationReport {
  rows: ReconciliationRow[];
  teacherRollups: ReconciliationRollup[];
  groupRollups: ReconciliationRollup[];
  summary: ReconciliationSummary;
}

/** Drill-down: the scheduled lessons behind a single Course row. */
export interface CourseLessonSummary {
  lessonId: number;
  date: string;       // ISO date, e.g. "2027-03-01"
  startTime: string;   // ISO date-time of the timeslot start
  roomName: string | null;
  pinned: boolean;
}
