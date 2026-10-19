import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import i18n from '../i18n';
import { constraints } from '../data/constraints';
import { parseISO, format } from 'date-fns';
import type { Lesson, Room, Timeslot, ViolationInfo } from '../types/domain';
import * as api from '../api/scheduleApi';
import { getDisplayName, renderJustification } from '../utils';
import { autoTextColor, hexToRgba } from '../utils/colorUtils';
import { canDropLessonAt, type TimePeriod } from './timeline/types';
import { findTimeslotId } from '../hooks/useScheduleData';

interface Props {
  lesson: Lesson;
  onClose: () => void;
  onUpdate: () => void;
  subjectColor: string;
  totalHours: number;
  onNavigateTeacher?: (teacherId: number) => void;
  onNavigateGroup?: (groupId: number) => void;
  onNavigateRoom?: (roomId: number) => void;
  onNavigateDate?: (date: Date) => void;
  violations?: ViolationInfo;
  /** Manual placement (TASK-357). Omitted for the virtual "lessons" synthesized
   *  by useScheduleData (negative ids) - those aren't real Lesson rows, so
   *  assignLesson can't target them; the Move section hides itself. */
  timeslots?: Timeslot[];
  timePeriods?: TimePeriod[];
  rooms?: Room[];
  isSlotOpenForGroup?: (day: Date, slotIndex: number, groupId: number) => boolean;
  /** Performs assign + (optional) pin + re-validation + refetch; may throw. */
  onMove?: (timeslotId: number, roomId: number | null, pinAfterMove: boolean) => Promise<void>;
  /** Bounds the date picker - reused from useScheduleData.ts rather than re-derived from timeslots. */
  yearRange?: { start: Date; end: Date } | null;
}

interface DragHandleProps {
  onDragStart: (e: React.PointerEvent) => void;
}

/**
 * Drag handle component with visual indicator.
 * Uses pointer events for cross-device support (mouse + touch).
 */
function DragHandle({ onDragStart }: DragHandleProps) {
  return (
    <div
      className="flex items-center justify-center py-2 cursor-grab active:cursor-grabbing
                 touch-none select-none hover:bg-surface-hover rounded-t-lg -mt-1 -mx-1"
      onPointerDown={onDragStart}
      aria-label="Drag to reposition tooltip"
      role="separator"
    >
      <div className="flex gap-1">
        <div className="w-2 h-2 rounded-full bg-border" />
        <div className="w-2 h-2 rounded-full bg-border" />
        <div className="w-2 h-2 rounded-full bg-border" />
      </div>
    </div>
  );
}

const animationStyles = `
@keyframes tooltipEnter {
  from { opacity: 0; transform: scale(0.95) translateY(-10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}
`;

// TASK-662: constraint id -> docs-namespace nameKey, derived from the constraints data
// (data/constraints.ts is the single source for human-readable constraint names,
// together with the docs-namespace translations; special forms like 'M20.2' ->
// 'm20_2.name' and 'H-COPRESENCE' -> 'hcopresence.name' come straight from the
// data, not by hand).
const CONSTRAINT_NAME_KEYS: Record<string, string> = Object.fromEntries(
  constraints.map(c => [c.id, c.nameKey])
);

function formatDateTime(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleString('it-IT', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function formatDate(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleDateString('it-IT', {
    weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
  });
}

function formatTime(isoStr: string): string {
  const d = new Date(isoStr);
  return d.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
}

/**
 * Get human-readable description for a constraint ID.
 * Handles both formats:
 * - Full format: "M15 - Same-day same-subject should be consecutive" -> extract description
 * - Short format: "M15" -> resolve via the docs namespace (single source:
 *   data/constraints.ts + its translations); unknown IDs return the raw ID.
 *
 * TASK-662: when the active language has a docs-namespace translation for the
 * constraint name, that wins over the embedded English tail.
 */
export function getConstraintDescription(constraintId: string): string {
  const sep = constraintId.indexOf(' - ');
  const shortId = sep >= 0 ? constraintId.slice(0, sep) : constraintId;
  const nameKey = CONSTRAINT_NAME_KEYS[shortId];
  if (nameKey && i18n.exists(`docs:${nameKey}`)) {
    return i18n.t(`docs:${nameKey}`);
  }
  if (sep >= 0) return constraintId.slice(sep + 3);
  return constraintId;
}

export default function LessonTooltip({
  lesson, onClose, onUpdate, subjectColor, totalHours,
  onNavigateTeacher, onNavigateGroup, onNavigateRoom, onNavigateDate,
  violations,
  timeslots, timePeriods, rooms, isSlotOpenForGroup, onMove, yearRange,
}: Props) {
  const { t } = useTranslation('schedule');
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  // Manual placement (TASK-357). A real Lesson always has a positive id; the guard is
  // defensive since the client-side negative-id virtual entries were removed with the
  // LabBlock entity itself (TASK-228/267, UI cleanup TASK-515).
  const canMove = lesson.id > 0 && !!onMove && !!timeslots && !!timePeriods && !!rooms && !!isSlotOpenForGroup;
  const groupId = lesson.course?.studentGroup?.id;

  const [selectedDate, setSelectedDate] = useState<string>(
    () => lesson.timeslot?.startDateTime.slice(0, 10) ?? ''
  );
  const [selectedSlotIndex, setSelectedSlotIndex] = useState<number | null>(null);
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(lesson.room?.id ?? null);
  const [pinAfterMove, setPinAfterMove] = useState(true); // school desiderata: hand-placed = intoccabile
  const [moving, setMoving] = useState(false);

  // yearRange is already computed once in useScheduleData.ts and threaded down from
  // ScheduleTimeline - reuse it instead of re-deriving min/max from the raw timeslots array.
  const dateBounds = useMemo(() => {
    if (!yearRange) return null;
    return { min: format(yearRange.start, 'yyyy-MM-dd'), max: format(yearRange.end, 'yyyy-MM-dd') };
  }, [yearRange]);

  // Slots legal for this lesson's group on the selected date (H20 calendar;
  // afternoons are allowed since TASK-379 - M28 is a medium preference). The
  // effective-lesson dance below predates that demotion (pinning used to be
  // the only way to unlock an afternoon slot) and is kept: canDropLessonAt
  // may grow pinned-sensitive checks again.
  const legalPeriods = useMemo(() => {
    if (!canMove || groupId == null || !selectedDate) return [];
    const day = parseISO(selectedDate);
    const effectiveLesson = pinAfterMove && !lesson.pinned ? { ...lesson, pinned: true } : lesson;
    return timePeriods!.filter(p => canDropLessonAt(effectiveLesson, groupId, day, p, isSlotOpenForGroup!));
  }, [canMove, groupId, selectedDate, timePeriods, lesson, isSlotOpenForGroup, pinAfterMove]);

  // Default the slot picker to the lesson's current slot (if still legal) or the first legal slot,
  // and re-derive whenever the date or the set of legal slots changes.
  useEffect(() => {
    if (!canMove) return;
    const currentTime = lesson.timeslot?.startDateTime.slice(11, 16);
    const currentPeriod = currentTime ? legalPeriods.find(p => p.startTime === currentTime) : undefined;
    setSelectedSlotIndex((currentPeriod ?? legalPeriods[0])?.index ?? null);
  }, [selectedDate, legalPeriods, lesson]);

  const selectedTimeslotId = useMemo(() => {
    if (!canMove || selectedSlotIndex == null) return null;
    const period = legalPeriods.find(p => p.index === selectedSlotIndex);
    if (!period) return null;
    return findTimeslotId(timeslots!, parseISO(selectedDate), period);
  }, [canMove, selectedSlotIndex, legalPeriods, timeslots, selectedDate]);

  const handleMoveClick = async () => {
    if (selectedTimeslotId == null || !onMove) return;
    if (lesson.pinned && !window.confirm(t('validation:confirmMovePinned'))) return;
    setMoving(true);
    try {
      // On failure the panel stays open (ScheduleTimeline surfaces an error toast) so the
      // user can retry; on success it closes, mirroring handleTogglePin's pattern below.
      await onMove(selectedTimeslotId, selectedRoomId, pinAfterMove);
      onClose();
    } finally {
      setMoving(false);
    }
  };

  // Drag state
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartPos = useRef({ x: 0, y: 0 });
  const tooltipStartPos = useRef({ x: 0, y: 0 });

  // Reset position when tooltip opens for a new lesson
  useEffect(() => {
    setPosition({ x: 0, y: 0 });
    setIsDragging(false);
  }, [lesson?.id]);

  // Drag handlers
  const handleDragStart = (e: React.PointerEvent) => {
    e.preventDefault();
    setIsDragging(true);
    dragStartPos.current = { x: e.clientX, y: e.clientY };
    tooltipStartPos.current = { ...position };

    // Capture pointer for reliable move/end events
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handleDragMove = useCallback((e: PointerEvent) => {
    if (!isDragging) return;

    const deltaX = e.clientX - dragStartPos.current.x;
    const deltaY = e.clientY - dragStartPos.current.y;

    // Calculate viewport bounds
    const tooltipWidth = 450; // Approximate max width
    const tooltipHeight = 500; // Approximate max height
    const padding = 20;

    const maxX = window.innerWidth / 2 - tooltipWidth / 2 - padding;
    const maxY = window.innerHeight / 2 - tooltipHeight / 2 - padding;

    const newX = Math.max(-maxX, Math.min(maxX, tooltipStartPos.current.x + deltaX));
    const newY = Math.max(-maxY, Math.min(maxY, tooltipStartPos.current.y + deltaY));

    setPosition({ x: newX, y: newY });
  }, [isDragging, position]);

  const handleDragEnd = useCallback(() => {
    setIsDragging(false);
  }, []);

  // Add/remove drag event listeners
  useEffect(() => {
    if (isDragging) {
      window.addEventListener('pointermove', handleDragMove);
      window.addEventListener('pointerup', handleDragEnd);
      return () => {
        window.removeEventListener('pointermove', handleDragMove);
        window.removeEventListener('pointerup', handleDragEnd);
      };
    }
  }, [isDragging, handleDragMove, handleDragEnd]);

  // Focus management and keyboard handling
  useEffect(() => {
    // Focus the close button when modal opens
    closeButtonRef.current?.focus();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const handleTogglePin = async () => {
    await api.togglePin(lesson.id);
    onUpdate();
    onClose();
  };

  const teacher = lesson.course?.teacher;
  const studentGroup = lesson.course?.studentGroup;
  const room = lesson.room;
  // TASK-530b: pseudo-Stage slots are out of school: display STAGE, not empty
  const roomDisplayName = room?.name
    || (lesson.course?.teacher?.name === 'Stage' || lesson.course?.teacher?.pseudo ? 'STAGE' : undefined);
  const timeslot = lesson.timeslot;

  const canNavigateTeacher = teacher && onNavigateTeacher;
  const canNavigateGroup = studentGroup && onNavigateGroup;
  const canNavigateRoom = room && onNavigateRoom;
  const canNavigateDate = timeslot && onNavigateDate;

  const subjectName = lesson.course?.subject || t('unknownSubject');

  return (
    <>
      <style>{animationStyles}</style>

      <div
        className="fixed inset-0 bg-overlay z-modal"
        onClick={!isDragging ? onClose : undefined}
        role="presentation"
      >
        <div
          ref={modalRef}
          className={`absolute bg-surface rounded-lg min-w-0 md:min-w-[280px] w-[calc(100vw-2rem)] md:w-auto md:max-w-[450px] lg:max-w-modal-lg max-h-[calc(100vh-2rem)] overflow-y-auto
                     shadow-[0_8px_24px_rgba(0,0,0,0.2)]
                     ${isDragging ? 'select-none' : ''}
                     ${position.x === 0 && position.y === 0 ? 'animate-[tooltipEnter_0.15s_ease-out]' : ''}`}
          style={{
            left: `calc(50% + ${position.x}px)`,
            top: `calc(50% + ${position.y}px)`,
            transform: 'translate(-50%, -50%)',
          }}
          onClick={e => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="lesson-subject"
        >
          {/* Drag handle at top */}
          <DragHandle onDragStart={handleDragStart} />

          {/* Content container with padding */}
          <div className="p-4 md:p-6 pt-0">
          {/* Colored header - extends to left/right edges */}
          <div
            className="-mx-4 md:-mx-6 -mt-4 md:-mt-6 mb-5 px-4 md:px-6 py-4 flex items-center gap-2.5 rounded-t-lg"
            style={{ background: hexToRgba(subjectColor, 0.7), color: autoTextColor(subjectColor) }}
          >
            <div id="lesson-subject" className="text-lg font-bold">
              {subjectName}
            </div>
            {lesson.pinned && (
              <span className="text-xs px-2 py-1 rounded-md ml-auto" style={{ background: 'rgba(255,255,255,0.2)' }}>
                {t('pinned')}
              </span>
            )}
          </div>

          {/* Violation section - appears FIRST if violations exist */}
          {violations && violations.violations.length > 0 && (
            <div className="mb-4 pb-4 border-b border-border-faint">
              <div
                className="text-xs font-semibold uppercase tracking-wide mb-2.5 flex items-center gap-1.5"
                style={{ color: 'var(--violation-text)' }}
              >
                <span>⚠</span>
                {t('hardConstraintViolations', { count: violations.violations.length })}
              </div>
              <div className="flex flex-col gap-2">
                {violations.violations
                  // TASK-530c: replicate the server's H29 exemptions. H29 exempts
                  // pinned (imported declarations) and pseudo-teacher lessons; the
                  // server drill-down already excludes them, this is the same rule
                  // for any client-side path so a healthy pin never renders broken.
                  .filter((v) => {
                    if (!v.constraint.startsWith('H29')) return true;
                    if (lesson.pinned) return false;
                    const t = lesson.course?.teacher;
                    return !(t?.name === 'Stage' || t?.pseudo);
                  })
                  .sort((a, b) => a.constraint.localeCompare(b.constraint))
                  .map((v, idx) => (
                    <div key={idx} className="flex flex-col gap-1 text-xs leading-relaxed">
                      <div className="flex items-start gap-2.5">
                        <span
                          className="font-mono font-bold px-1.5 py-0.5 rounded text-white flex-shrink-0"
                          style={{ background: 'var(--violation-badge-bg)' }}
                        >
                          {v.constraint}
                        </span>
                        <span className="text-muted flex-1">
                          {v.message || getConstraintDescription(v.constraint)}
                        </span>
                      </div>
                      {v.justification && (
                        <div className="ml-9 pl-2 border-l-2 border-border-faint text-content-secondary">
                          <span className="font-medium">{t('details')}:</span> {renderJustification(v.justification)}
                        </div>
                      )}
                    </div>
                  ))}
              </div>
            </div>
          )}

          {/* Navigation section */}
          <div className="mb-4 pb-4 border-b border-border-faint">
            {/* Teacher row */}
            <div className="flex justify-between items-center py-2 text-sm">
              <span className="text-muted font-medium min-w-[80px]">{t('teacher')}</span>
              {teacher ? (
                canNavigateTeacher ? (
                  <button
                    className="bg-none border-none text-action-text cursor-pointer p-0
                               underline text-inherit font-semibold
                               transition-colors duration-150"
                    onClick={() => onNavigateTeacher(teacher.id)}
                    title={t('viewTeacherSchedule')}
                  >
                    {getDisplayName(teacher)}
                  </button>
                ) : (
                  <span className="font-semibold text-content">{getDisplayName(teacher)}</span>
                )
              ) : (
                <span className="font-semibold text-content">--</span>
              )}
            </div>

            {/* Student group row */}
            <div className="flex justify-between items-center py-2 text-sm">
              <span className="text-muted font-medium min-w-[80px]">{t('studentGroup')}</span>
              {studentGroup ? (
                canNavigateGroup ? (
                  <button
                    className="bg-none border-none text-action-text cursor-pointer p-0
                               underline text-inherit font-semibold
                               transition-colors duration-150"
                    onClick={() => onNavigateGroup(studentGroup.id)}
                    title={t('viewGroupSchedule')}
                  >
                    {studentGroup.name}
                  </button>
                ) : (
                  <span className="font-semibold text-content">{studentGroup.name}</span>
                )
              ) : (
                <span className="font-semibold text-content">--</span>
              )}
            </div>

            {/* Room row */}
            <div className="flex justify-between items-center py-2 text-sm">
              <span className="text-muted font-medium min-w-[80px]">{t('room')}</span>
              {roomDisplayName ? (
                canNavigateRoom ? (
                  <button
                    className="bg-none border-none text-action-text cursor-pointer p-0
                               underline text-inherit font-semibold"
                    onClick={() => onNavigateRoom(room!.id)}
                    title={t('viewRoomSchedule')}
                  >
                    {roomDisplayName}
                  </button>
                ) : (
                  <span className="font-semibold text-content">{roomDisplayName}</span>
                )
              ) : (
                <span className="font-semibold text-content">--</span>
              )}
            </div>
          </div>

          {/* Metadata section */}
          <div className="mb-4 pb-4 border-b border-border-faint">
            <div className="flex gap-5 flex-wrap">
              {/* Date */}
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted uppercase tracking-wide">{t('date')}</span>
                {timeslot ? (
                  canNavigateDate ? (
                    <button
                      className="bg-none border-none text-action-text cursor-pointer p-0
                                 no-underline text-inherit font-semibold"
                      onClick={() => onNavigateDate(new Date(timeslot.startDateTime))}
                      title={t('goToWeek')}
                    >
                      {formatDate(timeslot.startDateTime)}
                    </button>
                  ) : (
                    <span className="text-sm font-semibold text-content">
                      {formatDate(timeslot.startDateTime)}
                    </span>
                  )
                ) : (
                  <span className="text-sm font-semibold text-content">--</span>
                )}
              </div>

              {/* Time */}
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted uppercase tracking-wide">{t('time')}</span>
                <span className="text-sm font-semibold text-content">
                  {timeslot
                    ? `${formatTime(timeslot.startDateTime)} - ${formatTime(timeslot.endDateTime)}`
                    : '--'}
                </span>
              </div>

              {/* Total hours */}
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted uppercase tracking-wide">{t('totalHours')}</span>
                <span className="text-sm font-semibold text-content">
                  {totalHours} {t('hours')}
                  {lesson.course?.totalHours && (
                    <span className="text-muted font-normal">
                      {' '}/ {lesson.course.totalHours}
                    </span>
                  )}
                </span>
              </div>
            </div>
          </div>

          {/* Move section (TASK-357): hand-place this lesson into a legal slot. */}
          {canMove ? (
            <div className="mb-4 pb-4 border-b border-border-faint">
              <div className="text-xs font-semibold uppercase tracking-wide mb-2.5 text-muted">
                {t('moveSectionTitle')}
              </div>
              <div className="flex flex-col gap-2">
                <label className="flex flex-col gap-1 text-xs">
                  <span className="text-muted">{t('moveDateLabel')}</span>
                  <input
                    type="date"
                    data-testid="lesson-move-date"
                    className="border border-border rounded px-2 py-1 bg-surface text-content text-xs"
                    value={selectedDate}
                    min={dateBounds?.min}
                    max={dateBounds?.max}
                    onChange={e => setSelectedDate(e.target.value)}
                  />
                </label>
                <label className="flex flex-col gap-1 text-xs">
                  <span className="text-muted">{t('moveSlotLabel')}</span>
                  <select
                    data-testid="lesson-move-slot"
                    className="border border-border rounded px-2 py-1 bg-surface text-content text-xs"
                    value={selectedSlotIndex ?? ''}
                    disabled={legalPeriods.length === 0}
                    onChange={e => setSelectedSlotIndex(Number(e.target.value))}
                  >
                    {legalPeriods.length === 0 && <option value="">{t('moveNoLegalSlots')}</option>}
                    {legalPeriods.map(p => (
                      <option key={p.index} value={p.index}>{p.label}</option>
                    ))}
                  </select>
                </label>
                <label className="flex flex-col gap-1 text-xs">
                  <span className="text-muted">{t('moveRoomLabel')}</span>
                  <select
                    data-testid="lesson-move-room"
                    className="border border-border rounded px-2 py-1 bg-surface text-content text-xs"
                    value={selectedRoomId ?? ''}
                    onChange={e => setSelectedRoomId(e.target.value ? Number(e.target.value) : null)}
                  >
                    <option value="">{t('moveNoRoom')}</option>
                    {rooms!.map(r => (
                      <option key={r.id} value={r.id}>{r.name}</option>
                    ))}
                  </select>
                </label>
                <label className="flex items-center gap-2 text-xs">
                  <input
                    type="checkbox"
                    data-testid="lesson-move-pin"
                    checked={pinAfterMove}
                    onChange={e => setPinAfterMove(e.target.checked)}
                  />
                  {t('movePinCheckbox')}
                </label>
                <button
                  data-testid="lesson-move-submit"
                  className="px-4 py-2 border-none rounded-md cursor-pointer text-xs font-semibold
                             bg-primary text-white hover:bg-primary/90 transition-all duration-150
                             disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={selectedTimeslotId == null || moving}
                  onClick={handleMoveClick}
                >
                  {moving ? t('moving') : t('moveSubmit')}
                </button>
              </div>
            </div>
          ) : null}

          {/* Actions */}
          <div className="flex gap-2.5 mt-4 justify-end">
            <button
              className={`px-4 py-2 border-none rounded-md cursor-pointer text-xs font-semibold
                         transition-all duration-150
                         hover:scale-[1.02] active:scale-[0.98]
                         ${lesson.pinned
                           ? 'bg-amber-500 text-white hover:bg-amber-600'
                           : 'bg-green-500 text-white hover:bg-green-600'}`}
              onClick={handleTogglePin}
              aria-pressed={lesson.pinned}
            >
              {lesson.pinned ? t('unpin') : t('pin')}
            </button>
            <button
              ref={closeButtonRef}
              className="px-4 py-2 bg-cancel-bg text-cancel-text border-none rounded-md
                         cursor-pointer text-xs font-semibold hover:bg-cancel-hover
                         transition-all duration-150"
              onClick={onClose}
              aria-label={t('common:close')}
            >
              {t('common:close')}
            </button>
          </div>
          </div>
        </div>
      </div>
    </>
  );
}
