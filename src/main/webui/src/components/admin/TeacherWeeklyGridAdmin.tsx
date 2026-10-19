import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { RecurringAvailabilityRule, Teacher } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableSelect, TableButton, AdminTable, AdminTh, AdminTd, StatusBanner
} from './styles';
import { useSlotSchema } from '../../hooks/useSlotSchema';
import { DAYS, DAY_LABELS } from '../../data/dayConstants';
import { useTheme } from '../../theme';

type CellColor = 'red' | 'yellow' | 'green';

const DAYS_BY_NAME = Object.fromEntries(DAYS.map((d, i) => [d, i]));

const NEXT_COLOR: Record<CellColor, CellColor> = { green: 'red', red: 'yellow', yellow: 'green' };

const GRID_NOTE = 'Griglia settimanale docente (TASK-603)';

const CELL_BG: Record<CellColor, { light: string; dark: string }> = {
  red: { light: 'bg-red-500', dark: 'bg-red-800' },
  yellow: { light: 'bg-yellow-400', dark: 'bg-yellow-500' },
  green: { light: 'bg-green-500', dark: 'bg-green-800' },
};

function isAlwaysRule(rule: RecurringAvailabilityRule): boolean {
  return rule.effectiveFrom == null && rule.effectiveTo == null;
}

/** Keys are java.time.DayOfWeek values (MONDAY=1..FRIDAY=5). */
function emptyGrid(slotCount: number): Record<number, CellColor[]> {
  const grid: Record<number, CellColor[]> = {};
  for (let day = 1; day <= DAYS.length; day++) {
    grid[day] = Array<CellColor>(slotCount).fill('green');
  }
  return grid;
}

function deriveGrid(rules: RecurringAvailabilityRule[], teacherId: number, slotCount: number): Record<number, CellColor[]> {
  const grid = emptyGrid(slotCount);
  for (const rule of rules) {
    if (rule.teacher?.id !== teacherId || !isAlwaysRule(rule)) continue;
    const day = (DAYS as readonly string[]).indexOf(rule.dayOfWeek) + 1;
    if (day === 0) continue;
    const color: CellColor = rule.type === 'VACATION' ? 'red' : 'yellow';
    for (let slot = Math.max(0, rule.minSlot); slot <= Math.min(slotCount - 1, rule.maxSlot); slot++) {
      // Red dominance: VACATION (hard H5 exclusion) wins over PREFERENCE (soft S3)
      // on a cell both cover.
      if (grid[day][slot] === 'green' || (color === 'red' && grid[day][slot] === 'yellow')) {
        grid[day][slot] = color;
      }
    }
  }
  return grid;
}

function gridsEqual(a: Record<number, CellColor[]>, b: Record<number, CellColor[]>): boolean {
  return DAYS.every((_, i) => {
    const ra = a[i + 1] ?? [];
    const rb = b[i + 1] ?? [];
    return ra.length === rb.length && ra.every((c, s) => c === rb[s]);
  });
}

interface ColorRun {
  minSlot: number;
  maxSlot: number;
  type: 'VACATION' | 'PREFERENCE';
}

function runsForDay(cells: CellColor[]): ColorRun[] {
  const runs: ColorRun[] = [];
  let slot = 0;
  while (slot < cells.length) {
    const color = cells[slot];
    if (color === 'green') {
      slot++;
      continue;
    }
    let end = slot;
    while (end + 1 < cells.length && cells[end + 1] === color) end++;
    runs.push({ minSlot: slot, maxSlot: end, type: color === 'red' ? 'VACATION' : 'PREFERENCE' });
    slot = end + 1;
  }
  return runs;
}

/**
 * Weekly availability grid editor for teachers (TASK-603), backed by
 * RecurringAvailabilityRule. Clicking a cell cycles green (free) -> red
 * (excluded, VACATION/H5) -> yellow (discouraged, PREFERENCE/S3); saving
 * replaces every always-window rule of the teacher with one rule per maximal
 * contiguous red/yellow run per weekday. Rules with a date window are never
 * created or deleted here and are listed read-only below the grid.
 */
export default function TeacherWeeklyGridAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const { periods } = useSlotSchema();
  const { mode } = useTheme();
  const dark = mode === 'dark';

  const [teachers, setTeachers] = useState<Teacher[]>([]);
  const [teacherId, setTeacherId] = useState<number | null>(null);
  const [rules, setRules] = useState<RecurringAvailabilityRule[]>([]);
  const [grid, setGrid] = useState<Record<number, CellColor[]> | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const slotCount = periods.length;

  useEffect(() => {
    api.getTeachers().then(setTeachers).catch(e => {
      console.error('Failed to fetch teachers:', getErrorMessage(e));
    });
  }, []);

  const loadRules = async () => {
    setIsLoading(true);
    try {
      setRules(await api.getRecurringAvailabilityRules());
      setError(null);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRules();
  }, []);

  // Repaint (and discard local edits) whenever the teacher, the rule set behind
  // it, or the slot count changes: after a reload the grid always equals the
  // server's state. The baseline the dirty check compares against is the same
  // derivation, memoized - never a second state slot that could desync.
  useEffect(() => {
    if (teacherId == null) {
      setGrid(null);
      return;
    }
    setGrid(deriveGrid(rules, teacherId, slotCount));
  }, [teacherId, rules, slotCount]);

  const baseline = useMemo(
    () => (teacherId == null ? null : deriveGrid(rules, teacherId, slotCount)),
    [rules, teacherId, slotCount]
  );

  const dirty = grid != null && baseline != null && !gridsEqual(grid, baseline);

  // Rules the 5-day grid cannot express and that a save would still delete:
  // date-windowed rules, and always-rules on days the grid has no column for
  // (SATURDAY/SUNDAY arriving via REST). Surfaced read-only so nothing the
  // teacher owns disappears without being visible.
    const unpaintableRules = useMemo(
    () => rules.filter(r => r.teacher?.id === teacherId
        && (!isAlwaysRule(r) || !(r.dayOfWeek in DAYS_BY_NAME))),
    [rules, teacherId]
  );

  const cycleCell = (day: number, slot: number) => {
    setGrid(prev => prev && {
      ...prev,
      [day]: prev[day].map((c, i) => (i === slot ? NEXT_COLOR[c] : c)),
    });
  };

  const cycleDay = (day: number) => {
    setGrid(prev => {
      if (!prev) return prev;
      const cells = prev[day];
      const next = cells.every(c => c === cells[0]) ? NEXT_COLOR[cells[0]] : 'green';
      return { ...prev, [day]: cells.map(() => next) };
    });
  };

  const handleSave = async () => {
    if (teacherId == null || grid == null) return;
    setMessage(null);
    setIsLoading(true);
    try {
      // One atomic bulk replace: the server deletes the old always-rules and
      // inserts the runs in a single transaction (a client-side loop would
      // leave the teacher with zero rules if a create failed mid-sequence).
      const runs = DAYS.flatMap((dayName, i) =>
        runsForDay(grid[i + 1]).map(run => ({
          dayOfWeek: dayName,
          minSlot: run.minSlot,
          maxSlot: run.maxSlot,
          type: run.type,
          note: GRID_NOTE,
        }))
      );
      await api.replaceAlwaysAvailabilityRules(teacherId, runs);
      setMessage(t('admin:gridSaved'));
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      // Reload so the displayed grid is exactly what the server holds now: a
      // save that fails mid-sequence leaves partial state that must not be
      // masked by the local edit.
      await loadRules();
    }
  };

  const lunchBorder = (p: { lunchBreakBefore?: boolean }): string =>
    p.lunchBreakBefore ? 'border-l-2 border-border' : '';

  const rangeLabel = (rule: RecurringAvailabilityRule): string => {
    const start = periods[rule.minSlot]?.startTime ?? '?';
    const end = periods[rule.maxSlot]?.endTime ?? '?';
    return `${start} - ${end}`;
  };

  const legend: Array<{ color: CellColor; key: string }> = [
    { color: 'red', key: 'admin:gridLegendExcluded' },
    { color: 'yellow', key: 'admin:gridLegendDiscouraged' },
    { color: 'green', key: 'admin:gridLegendFree' },
  ];

  return (
    <TableContainer>
      <TableTitle>{t('admin:teacherWeeklyGrid')}</TableTitle>

      {error && <StatusBanner kind="error" text={error} onDismiss={() => setError(null)} dismissLabel={t('common:dismiss')} />}
      {message && <StatusBanner kind="success" text={message} onDismiss={() => setMessage(null)} dismissLabel={t('common:dismiss')} />}

      <div className="flex gap-3 mb-4 items-center flex-wrap">
        <TableSelect
          value={teacherId ?? ''}
          onChange={e => setTeacherId(e.target.value ? Number(e.target.value) : null)}
          disabled={isLoading}
        >
          <option value="">{t('schedule:teacher')}</option>
          {teachers.map(tc => (
            <option key={tc.id} value={tc.id}>{tc.name}</option>
          ))}
        </TableSelect>
        {grid != null && (
          <>
            <TableButton onClick={handleSave} disabled={isLoading || !dirty} data-testid="save-weekly-grid">
              {isLoading ? '...' : t('common:save')}
            </TableButton>
            <TableButton variant="cancel" onClick={loadRules} disabled={isLoading} data-testid="reset-weekly-grid">
              {t('common:reset')}
            </TableButton>
          </>
        )}
      </div>

      {teacherId == null && (
        <p className="text-sm text-muted">{t('admin:gridSelectTeacherHint')}</p>
      )}

      {teacherId != null && grid != null && (
        <>
          <div className="flex gap-4 items-center flex-wrap text-sm text-muted mb-4">
            {legend.map(({ color, key }) => (
              <span key={color} className="flex items-center gap-1.5">
                <span className={`inline-block w-4 h-4 rounded ${dark ? CELL_BG[color].dark : CELL_BG[color].light}`} />
                {t(key)}
              </span>
            ))}
            <span className="text-faint">{t('admin:gridLegendNote')}</span>
          </div>

          <AdminTable data-testid="weekly-grid">
            <thead>
              <tr>
                <AdminTh>{t('admin:dayOfWeek')}</AdminTh>
                {periods.map(p => (
                  <AdminTh key={p.index} className={lunchBorder(p)}>
                    <span title={p.label}>{p.shortLabel}</span>
                  </AdminTh>
                ))}
              </tr>
            </thead>
            <tbody>
              {DAYS.map((dayName, dayIndex) => {
                const day = dayIndex + 1;
                return (
                  <tr key={dayName}>
                    <AdminTd>
                      <button
                        type="button"
                        className="bg-transparent border-none text-content font-semibold cursor-pointer px-0 py-1 text-sm hover:text-accent"
                        onClick={() => cycleDay(day)}
                        disabled={isLoading}
                        title={t('admin:gridCycleDay')}
                      >
                        {DAY_LABELS[dayName]}
                      </button>
                    </AdminTd>
                    {periods.map(p => (
                      <AdminTd key={p.index} className={lunchBorder(p)}>
                        <button
                          type="button"
                          className={`w-9 h-8 rounded cursor-pointer border-none transition-colors ${dark ? CELL_BG[grid[day][p.index]].dark : CELL_BG[grid[day][p.index]].light}`}
                          onClick={() => cycleCell(day, p.index)}
                          disabled={isLoading}
                          aria-label={`${DAY_LABELS[dayName]} ${p.label}`}
                        />
                      </AdminTd>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </AdminTable>

          {unpaintableRules.length > 0 && (
            <div className="mt-8">
              <h4 className="text-base font-semibold mb-2 text-content">{t('admin:gridWindowedRules')}</h4>
              <p className="text-sm text-muted mb-3">{t('admin:gridWindowedHint')}</p>
              <AdminTable>
                <thead>
                  <tr>
                    <AdminTh>{t('admin:dayOfWeek')}</AdminTh>
                    <AdminTh>{t('schedule:time')}</AdminTh>
                    <AdminTh>{t('admin:type')}</AdminTh>
                    <AdminTh>{t('admin:startDate')}</AdminTh>
                    <AdminTh>{t('admin:endDate')}</AdminTh>
                  </tr>
                </thead>
                <tbody>
                  {unpaintableRules.map(rule => (
                    <tr key={rule.id}>
                      <AdminTd>{DAY_LABELS[rule.dayOfWeek] ?? rule.dayOfWeek}</AdminTd>
                      <AdminTd>{rangeLabel(rule)}</AdminTd>
                      <AdminTd>{rule.type === 'VACATION' ? t('admin:vacation') : t('admin:preference')}</AdminTd>
                      <AdminTd>{rule.effectiveFrom ?? '-'}</AdminTd>
                      <AdminTd>{rule.effectiveTo ?? '-'}</AdminTd>
                    </tr>
                  ))}
                </tbody>
              </AdminTable>
            </div>
          )}
        </>
      )}
    </TableContainer>
  );
}
