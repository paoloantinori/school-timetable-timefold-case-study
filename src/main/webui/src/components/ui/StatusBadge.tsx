interface StatusBadgeProps {
  status: 'idle' | 'ch' | 'ls' | 'pre-assign' | 'pre-assigning' | 'phase1' | 'phase2' | 'phase3' | 'phase4' | 'phase5'
    // TASK-353: reconciliation dashboard row/summary statuses
    | 'OK' | 'MISMATCH' | 'UNSCHEDULED'
    // TASK-419: staged solve cycle in progress (grid is intermediate, not final)
    | 'staged';
  label: string;
}

const statusClasses: Record<string, string> = {
  idle: 'bg-surface-alt text-muted',
  ch: 'bg-phase-ch-bg text-phase-ch-text',
  ls: 'bg-phase-ls-conflict-bg text-phase-ls-conflict-text',
  'pre-assign': 'bg-phase-pre-assign-bg text-phase-pre-assign-text',
  'pre-assigning': 'bg-phase-pre-assign-bg text-phase-pre-assign-text',
  'phase1': 'bg-phase-ch-bg text-phase-ch-text',
  'phase2': 'bg-phase-ch-bg text-phase-ch-text',
  'phase3': 'bg-phase-ls-conflict-bg text-phase-ls-conflict-text',  // Repair + Compaction (TASK-393)
  'phase4': 'bg-phase-ch-bg text-phase-ch-text',                    // CH - Room Assignment
  'phase5': 'bg-phase-ls-compact-bg text-phase-ls-compact-text',    // LS - Room Refinement
  OK: 'bg-feedback-success-bg text-feedback-success-text',
  MISMATCH: 'bg-feedback-warning-bg text-feedback-warning-text',
  UNSCHEDULED: 'bg-feedback-error-bg text-feedback-error-text',
  staged: 'bg-feedback-warning-bg text-feedback-warning-text',
};

export function StatusBadge({ status, label }: StatusBadgeProps) {
  return (
    <div
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold uppercase tracking-wider ${statusClasses[status]}`}
      role="status"
      aria-live="polite"
    >
      <span className="w-1.5 h-1.5 rounded-full bg-current" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
