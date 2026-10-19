import { useState, useEffect, useMemo, memo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { Lesson, SchoolSchedule, ConstraintMatch, ConstraintAnalysis } from '../types/domain';
import { getDisplayName } from '../utils/teacherUtils';
import { parseScoreParts } from '../utils';
import { getConstraintDescription } from './LessonTooltip';

interface Props {
  schedule: SchoolSchedule;
  onSolve: () => void;
  onStop: () => void;
  onReset: () => void;
  onPreAssign: () => void;
  onAnalyze: () => void;
  onRefresh: () => void;
  analysis: ConstraintAnalysis | null;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
  /** Manual placement (TASK-357): makes unassigned lessons draggable into the grid.
   *  Only meaningful when the grid is groupBy=studentGroup - drops are gated the
   *  same way there (isDragDropViewActive), so a false value here avoids a silent
   *  dead-end drag with no drop target anywhere. */
  onLessonDragStart?: (lesson: Lesson) => void;
  onLessonDragEnd?: () => void;
  dragDropEnabled?: boolean;
}

type Tab = 'stats' | 'unassigned' | 'actions';

interface ScoreCardProps {
  type: 'hard' | 'medium' | 'soft';
  score: number;
  violationCount: number;
  fullScore?: string | null;  // Full score string for tooltip
  t: (key: string, params?: Record<string, string | number>) => string;
}

function ScoreCard({ type, score, violationCount, fullScore, t }: ScoreCardProps) {
  const colors = {
    hard: {
      border: score < 0 ? 'border-[var(--score-hard-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-hard-bg)]' : 'bg-surface',
      label: 'sidebar.hardScore'
    },
    medium: {
      border: score < 0 ? 'border-[var(--score-medium-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-medium-bg)]' : 'bg-surface',
      label: 'sidebar.mediumScore'
    },
    soft: {
      border: score < 0 ? 'border-[var(--score-soft-border)]' : 'border-border',
      bg: score < 0 ? 'bg-[var(--score-soft-bg)]' : 'bg-surface',
      label: 'sidebar.softScore'
    }
  };

  const color = colors[type];

  return (
    <div
      data-testid={`score-card-${type}`}
      className={`flex-1 min-w-0 rounded-lg border-2 ${color.border} ${color.bg} p-2 md:p-3 transition-colors duration-200`}
      title={fullScore || undefined}
    >
      <div className="text-[0.65rem] md:text-xs font-semibold text-muted uppercase tracking-wide mb-1">
        {t(color.label)}
      </div>
      <div className="text-base md:text-lg font-bold text-content truncate">
        {score.toLocaleString()}
      </div>
      {violationCount > 0 && (
        <div className="text-xs text-muted mt-1">
          {t('sidebar.violations', { count: violationCount })}
        </div>
      )}
    </div>
  );
}

interface ViolationItemProps {
  constraintId: string;
  matchCount: number;
  type: 'HARD' | 'MEDIUM' | 'SOFT';
  onClick: () => void;
}

function ViolationItem({ constraintId, matchCount, type, onClick }: ViolationItemProps) {
  const typeColors = {
    HARD: 'text-constraint-hard',
    MEDIUM: 'text-constraint-medium',
    SOFT: 'text-constraint-soft'
  };

  return (
    <button
      onClick={onClick}
      className="w-full text-left px-3 py-2 rounded-md hover:bg-surface-hover transition-colors duration-150 group"
    >
      <div className="flex items-center justify-between">
        <span className="font-mono text-sm font-medium text-text group-hover:text-primary transition-colors">
          {getConstraintDescription(constraintId)}
        </span>
        <span className={`text-xs font-bold ${typeColors[type]}`}>
          {matchCount}
        </span>
      </div>
    </button>
  );
}

interface ActionButtonProps {
  onClick: () => void;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
  icon?: React.ReactNode;
  children: React.ReactNode;
}

function ActionButton({ onClick, disabled, variant = 'secondary', icon, children }: ActionButtonProps) {
  const baseStyles = "w-full px-4 py-2.5 rounded-lg font-medium text-sm transition-all duration-200 flex items-center justify-center gap-2";

  const variants = {
    primary: disabled
      ? "bg-primary/30 text-text cursor-not-allowed"
      : "bg-primary text-white hover:bg-primary/90 active:scale-[0.98] shadow-soft",
    secondary: disabled
      ? "bg-surface text-faint cursor-not-allowed border border-border"
      : "bg-surface text-text hover:bg-surface-hover active:scale-[0.98] border border-border",
    danger: disabled
      ? "bg-danger-bg text-danger-text/50 cursor-not-allowed border border-danger-border"
      : "bg-danger-bg text-danger-text hover:bg-danger-hover-bg active:scale-[0.98] border border-danger-border"
  };

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`${baseStyles} ${variants[variant]}`}
    >
      {icon}
      {children}
    </button>
  );
}

function ScheduleSidebarComponent({
  schedule,
  onSolve,
  onStop,
  onReset,
  onPreAssign,
  onAnalyze,
  onRefresh,
  analysis,
  collapsed = false,
  onToggleCollapse,
  onLessonDragStart,
  onLessonDragEnd,
  dragDropEnabled = false,
}: Props) {
  const { t } = useTranslation('schedule');
  const [activeTab, setActiveTab] = useState<Tab>('stats');
  const isCollapsed = collapsed;
  const toggleCollapse = onToggleCollapse || (() => {});

  // Mid-solve honesty (TASK-509 family): during a staged cycle the DB-recomputed
  // analysis is PARTIAL state, not the solution under construction. The sidebar
  // must not show it as if it were the score: fall back to the in-memory score
  // string and flag the numbers as intermediate.
  // TASK-667 (review F5): the delivery refinement runs after stagedStage went
  // null - without the refining term the sidebar would show DB-recomputed
  // partials as if final in exactly that window.
  const cycleActive = !!schedule.stagedStage || !!schedule.refiningForDelivery;

  // Parse score string (e.g., "-500hard/-164medium/0soft") to extract scores
  const parsedScores = useMemo(() => {
    if (analysis && !cycleActive) {
      return {
        hard: analysis.hard ?? 0,
        medium: analysis.medium ?? 0,
        soft: analysis.soft ?? 0
      };
    }
    // Fallback: parse schedule.score string
    const scoreStr = schedule.score;
    if (!scoreStr) {
      return { hard: 0, medium: 0, soft: 0 };
    }
    // Shared dual-grammar parser (TASK-538): bracket "[0/-12]hard/[-500/0]soft" first,
    // legacy flat with k/M suffixes as fallback. The quasi level (second hard) has no
    // slot in this compact stat line; /analysis carries it.
    const parts = parseScoreParts(scoreStr);
    if (parts) {
      return { hard: parts.hard, medium: parts.medium, soft: parts.soft };
    }
    return { hard: 0, medium: 0, soft: 0 };
  }, [analysis, schedule.score]);

  // Calculate assignment progress
  const { assigned, total, percent } = useMemo(() => {
    const total = schedule.lessons.length;
    const assigned = schedule.lessons.filter(l => l.timeslot).length;
    const percent = total > 0 ? Math.round((assigned / total) * 100) : 0;

    return { assigned, total, percent };
  }, [schedule.lessons]);


  // Group unassigned lessons
  const unassignedGroups = useMemo(() => {
    const unassigned = schedule.lessons.filter(l => !l.timeslot);

    return unassigned.reduce((acc, lesson) => {
      const key = lesson.course?.subject || '?';
      if (!acc[key]) acc[key] = [];
      acc[key].push(lesson);
      return acc;
    }, {} as Record<string, Lesson[]>);
  }, [schedule.lessons]);

  // Helper to derive constraint type from scores
  const getConstraintType = (hard: number, medium: number, soft: number): 'HARD' | 'MEDIUM' | 'SOFT' => {
    if (hard < 0) return 'HARD';
    if (medium < 0) return 'MEDIUM';
    return 'SOFT';
  };

  // Top violations
  const topViolations = useMemo(() => {
    if (cycleActive) return []; // DB-recomputed violations are partial mid-cycle
    if (!analysis?.constraints) return [];
    return [...analysis.constraints]
      .filter(c => c.matches > 0)
      .sort((a, b) => {
        const typeOrder = { HARD: 0, MEDIUM: 1, SOFT: 2 };
        const aType = getConstraintType(a.hard, a.medium, a.soft);
        const bType = getConstraintType(b.hard, b.medium, b.soft);
        const typeDiff = typeOrder[aType] - typeOrder[bType];
        if (typeDiff !== 0) return typeDiff;
        return b.matches - a.matches;
      })
      .slice(0, 5)
      .map(c => ({
        constraint: c.constraint,
        matches: c.matches,
        type: getConstraintType(c.hard, c.medium, c.soft)
      }));
  }, [analysis]);

  // Solver state
  const isSolving = schedule.solverStatus === 'SOLVING_ACTIVE';

  return (
    <div className="bg-surface rounded-lg shadow-soft overflow-hidden flex flex-col h-full">
      {/* Tab Navigation */}
      <div className={`flex ${isCollapsed ? 'flex-col items-center py-2 gap-2' : 'border-b border-border'}`}>
        {/* Collapse toggle button */}
        <button
          onClick={toggleCollapse}
          className="text-muted hover:text-primary transition-colors disabled:opacity-50 disabled:cursor-not-allowed p-2"
          title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          <svg className={`w-4 h-4 transition-transform duration-200 ${!isCollapsed ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
          </svg>
        </button>
        {!isCollapsed && (
          <>
            {(['stats', 'unassigned', 'actions'] as Tab[]).map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`flex-1 px-4 py-3 text-sm font-medium transition-colors duration-200 border-b-2 ${
                  activeTab === tab
                    ? 'border-primary text-primary bg-primary/5'
                    : 'border-transparent text-muted hover:text-text hover:bg-surface-hover'
                }`}
              >
                {t(`sidebar.${tab}`)}
              </button>
            ))}
            {/* Refresh button */}
            <button
              onClick={onRefresh}
              disabled={isSolving}
              className="px-3 py-2 text-muted hover:text-primary transition-colors disabled:opacity-50 disabled:cursor-not-allowed border-b border-border"
              title={t('sidebar.refresh')}
              aria-label={t('sidebar.refresh')}
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </>
        )}
      </div>

      {/* Tab Content */}
      <div className={`flex-1 overflow-y-auto ${isCollapsed ? 'hidden' : 'p-4'}`}>
        {activeTab === 'stats' && (
          <div className="space-y-4">
            {/* Score Cards */}
            <div className="flex gap-2">
              <ScoreCard
                type="hard"
                score={parsedScores.hard}
                violationCount={analysis?.constraints?.filter(c => getConstraintType(c.hard, c.medium, c.soft) === 'HARD' && c.hard < 0).length ?? 0}
                fullScore={schedule.score}
                t={t}
              />
              <ScoreCard
                type="medium"
                score={parsedScores.medium}
                violationCount={analysis?.constraints?.filter(c => getConstraintType(c.hard, c.medium, c.soft) === 'MEDIUM' && c.medium < 0).length ?? 0}
                fullScore={schedule.score}
                t={t}
              />
              <ScoreCard
                type="soft"
                score={parsedScores.soft}
                violationCount={analysis?.constraints?.filter(c => getConstraintType(c.hard, c.medium, c.soft) === 'SOFT' && c.soft < 0).length ?? 0}
                fullScore={schedule.score}
                t={t}
              />
            </div>

            {/* Progress Bar */}
            <div className="bg-surface-hover rounded-lg p-3">
              <div className="flex justify-between items-center mb-2">
                <span className="text-sm font-medium text-text">
                  {t('sidebar.progress', { assigned, total, percent })}
                </span>
              </div>
              <div className="w-full bg-border rounded-full h-2 overflow-hidden">
                <div
                  className="h-full bg-primary transition-all duration-500 ease-out"
                  style={{ width: `${percent}%` }}
                />
              </div>
            </div>

            {/* Top Violations */}
            <div>
              <h3 className="text-sm font-semibold text-text mb-2">
                {t('sidebar.topViolations')}
              </h3>
              {topViolations.length === 0 ? (
                <div className="text-sm text-faint italic">
                  {t('sidebar.noViolations')}
                </div>
              ) : (
                <div className="space-y-1">
                  {topViolations.map((v) => (
                    <ViolationItem
                      key={v.constraint}
                      constraintId={v.constraint}
                      matchCount={v.matches}
                      type={v.type}
                      onClick={onAnalyze}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {activeTab === 'unassigned' && (
          <div>
            {Object.keys(unassignedGroups).length === 0 ? (
              <div className="text-sm text-faint text-center py-8">
                {t('common:noData')}
              </div>
            ) : (
              <div className="space-y-3">
                {Object.entries(unassignedGroups).map(([subject, items]) => (
                  <div key={subject} className="bg-surface-hover rounded-lg p-3">
                    <div className="flex justify-between items-center mb-2">
                      <span className="font-medium text-text">{subject}</span>
                      <span className="bg-red-500 text-white rounded-full px-2 py-0.5 text-xs font-bold">
                        {items.length}
                      </span>
                    </div>
                    <div className="space-y-1">
                      {items.slice(0, 3).map((l) => {
                        const canDrag = dragDropEnabled && !!onLessonDragStart; // all unassigned are real Lesson rows since TASK-515
                        return (
                          <div
                            key={l.id}
                            data-testid={`unassigned-lesson-${l.id}`}
                            className={`text-xs text-muted ${canDrag ? 'cursor-grab active:cursor-grabbing' : ''}`}
                            draggable={canDrag}
                            title={!dragDropEnabled ? t('dragRequiresGroupView') : undefined}
                            onDragStart={canDrag ? (e) => {
                              e.dataTransfer.effectAllowed = 'move';
                              onLessonDragStart!(l);
                            } : undefined}
                            onDragEnd={canDrag ? () => onLessonDragEnd?.() : undefined}
                          >
                            {getDisplayName(l.course?.teacher) || '?'} - {l.course?.studentGroup?.name || '?'}
                          </div>
                        );
                      })}
                      {items.length > 3 && (
                        <div className="text-xs text-muted italic">
                          {t('more', { count: items.length - 3 })}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === 'actions' && (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-text mb-2">
              {t('sidebar.quickActions')}
            </h3>

            <ActionButton
              onClick={onSolve}
              disabled={isSolving}
              variant="primary"
            >
              {t('solve')}
            </ActionButton>

            <ActionButton
              onClick={onStop}
              disabled={!isSolving}
              variant="secondary"
            >
              {t('stop')}
            </ActionButton>

            <ActionButton
              onClick={onPreAssign}
              disabled={isSolving}
              variant="secondary"
            >
              {t('preAssign')}
            </ActionButton>

            <ActionButton
              onClick={onAnalyze}
              disabled={!analysis}
              variant="secondary"
            >
              {t('analyze')}
            </ActionButton>

            {!analysis && (
              <p className="text-xs text-faint text-center">
                {t('sidebar.runAnalysis')}
              </p>
            )}

            <div className="pt-2 border-t border-border">
              <ActionButton
                onClick={onReset}
                disabled={isSolving}
                variant="danger"
              >
                {t('resetAll')}
              </ActionButton>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

const ScheduleSidebar = memo(ScheduleSidebarComponent);
export default ScheduleSidebar;
