import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { StudentGroup, GroupCalendarRule } from '../../types/domain';
import * as api from '../../api/scheduleApi';
import { getErrorMessage } from '../../utils/apiError';
import {
  TableContainer, TableTitle, TableSelect, TableInput,
  TableButton, AdminTable, AdminTh, AdminTd, DeleteButton, StatusBanner } from './styles';
import { useAdminList } from '../../hooks/useAdminList';
import { useSlotSchema } from '../../hooks/useSlotSchema';
import { lunchBorderTop } from '../timeline/types';
import { DAYS, DAY_LABELS } from '../../data/dayConstants';
import {
  baseRulesToMatrix, matrixToRanges, isBaseRule, emptyOpenMatrix, type CalendarMatrix
} from '../../utils/groupCalendar';


/**
 * Per-group slot-level calendar editor (TASK-358): a 5-weekday x 8-slot
 * checkbox matrix per student group, backed by GroupCalendarRule (TASK-342).
 *
 * Scope: this editor manages "base" rules only (effectiveFrom/effectiveTo both
 * null - the group's normal weekly pattern). Date-scoped overrides (e.g. a
 * shortened first school day) are listed read-only below; editing those is
 * out of scope for this task.
 *
 * Also exposes StudentGroup.maxHoursPerDay, which had no admin UI before this.
 * The PUT endpoint now treats a null/omitted maxHoursPerDay as "leave it
 * alone" rather than "clear it" (TASK-358 code review N1), so this form
 * rejects an empty value client-side (N8) instead of silently saving a no-op -
 * clearing the cap needs a dedicated affordance that doesn't exist yet.
 */
export default function GroupCalendarAdmin() {
  const { t } = useTranslation(['admin', 'common', 'schedule']);
  const { periods: timePeriods, error: slotSchemaError } = useSlotSchema();

  // Secondary reference list (not the entity this page edits) - same pattern as
  // StagePatternsAdmin/SchoolSettingsAdmin's own StudentGroup dropdown fetch.
  const [groups, setGroups] = useState<StudentGroup[]>([]);
  useEffect(() => {
    api.getStudentGroups().then(setGroups).catch(e => {
      console.error('Failed to fetch student groups:', getErrorMessage(e));
    });
  }, []);

  const {
    items: rules,
    isLoading,
    error,
    clearError,
    load: loadRules,
    execute,
  } = useAdminList<GroupCalendarRule>({
    fetchFn: api.getGroupCalendarRules,
    filterFn: () => true, // No filtering - this page edits one group's full week at a time
  });

  const [groupId, setGroupId] = useState<number | null>(null);
  const [matrix, setMatrix] = useState<CalendarMatrix>(() => emptyOpenMatrix(timePeriods.length));
  const [maxHoursPerDay, setMaxHoursPerDay] = useState<string>('');
  const [message, setMessage] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [slotSchemaErrorDismissed, setSlotSchemaErrorDismissed] = useState(false);

  const selectedGroup = useMemo(() => groups.find(g => g.id === groupId) ?? null, [groups, groupId]);

  // Recompute the matrix whenever the selected group, the underlying rules, or the
  // slot count changes (e.g. after a save/reload, or once the real schema replaces
  // the fallback).
  useEffect(() => {
    if (groupId == null) return;
    setMatrix(baseRulesToMatrix(rules, groupId, timePeriods.length));
  }, [groupId, rules, timePeriods.length]);

  useEffect(() => {
    setMaxHoursPerDay(selectedGroup?.maxHoursPerDay != null ? String(selectedGroup.maxHoursPerDay) : '');
  }, [selectedGroup]);

  const overrideRules = useMemo(
    () => rules.filter(r => r.studentGroup?.id === groupId && !isBaseRule(r)),
    [rules, groupId]
  );

  const toggleCell = (dayIndex: number, slotIndex: number) => {
    setMatrix(prev => {
      const next = prev.map(row => [...row]);
      next[dayIndex][slotIndex] = !next[dayIndex][slotIndex];
      return next;
    });
  };

  const handleSaveCalendar = async () => {
    if (groupId == null) return;
    setMessage(null);
    try {
      // matrixToRanges throws on a non-contiguous selection; execute()'s catch
      // surfaces it through the same error banner as an API failure.
      await execute(async () => {
        const ranges = matrixToRanges(matrix);
        const baseRules = rules.filter(r => r.studentGroup?.id === groupId && isBaseRule(r));
        return Promise.all(ranges.flatMap(range => {
          const payload = {
            studentGroupId: groupId,
            dayOfWeek: range.dayOfWeek,
            minSlot: range.minSlot,
            maxSlot: range.maxSlot,
          };
          const matching = baseRules.filter(r => r.dayOfWeek === range.dayOfWeek);
          if (matching.length === 0) {
            return [api.createGroupCalendarRule(payload)];
          }
          // Self-heal duplicate base rules for this weekday (N9): update the first,
          // delete the rest, instead of leaving orphaned duplicates behind.
          const [primary, ...duplicates] = matching;
          return [
            api.updateGroupCalendarRule(primary.id, payload),
            ...duplicates.map(d => api.deleteGroupCalendarRule(d.id)),
          ];
        }));
      }, () => setMessage(t('admin:calendarSaved')));
    } finally {
      // Always reload from the server, success or failure (N9), so the matrix
      // reflects the actual DB state rather than the tentative local edit.
      loadRules();
    }
  };

  const handleSaveMaxHours = async () => {
    if (groupId == null) return;
    setMessage(null);
    setValidationError(null);
    const trimmed = maxHoursPerDay.trim();
    const parsed = Number(trimmed);
    if (trimmed === '' || !Number.isInteger(parsed) || parsed < 1 || parsed > timePeriods.length) {
      setValidationError(t('admin:maxHoursInvalid', { max: timePeriods.length }));
      return;
    }
    await execute(
      () => api.updateStudentGroup(groupId, { maxHoursPerDay: parsed }),
      () => {
        api.getStudentGroups().then(setGroups);
        setMessage(t('admin:maxHoursSaved'));
      }
    );
  };

  const handleDeleteOverride = async (id: number) => {
    await execute(() => api.deleteGroupCalendarRule(id), loadRules);
  };

  const rangeLabel = (rule: GroupCalendarRule): string => {
    if (rule.maxSlot < rule.minSlot) return t('admin:calendarClosedDay');
    const start = timePeriods[rule.minSlot]?.startTime ?? '?';
    const end = timePeriods[rule.maxSlot]?.endTime ?? '?';
    return `${start} - ${end}`;
  };

  return (
    <TableContainer>
      <TableTitle>{t('admin:groupCalendar')}</TableTitle>

      {error && <StatusBanner kind="error" text={error} onDismiss={clearError} dismissLabel={t('common:dismiss')} />}
      {validationError && <StatusBanner kind="error" text={validationError} onDismiss={() => setValidationError(null)} dismissLabel={t('common:dismiss')} />}
      {slotSchemaError && !slotSchemaErrorDismissed && (
        <StatusBanner kind="error" text={slotSchemaError} onDismiss={() => setSlotSchemaErrorDismissed(true)} dismissLabel={t('common:dismiss')} />
      )}
      {message && <StatusBanner kind="success" text={message} onDismiss={() => setMessage(null)} dismissLabel={t('common:dismiss')} />}

      <div className="flex gap-3 mb-4 items-center flex-wrap">
        <TableSelect
          value={groupId ?? ''}
          onChange={e => setGroupId(e.target.value ? Number(e.target.value) : null)}
          disabled={isLoading}
        >
          <option value="">{t('schedule:studentGroup')}</option>
          {groups.map(g => (
            <option key={g.id} value={g.id}>{g.name}</option>
          ))}
        </TableSelect>

        {selectedGroup && (
          <>
            <label className="flex items-center gap-2 text-sm whitespace-nowrap">
              {t('admin:maxHoursPerDay')}
              <TableInput
                type="number"
                className="w-20"
                min={1}
                max={timePeriods.length}
                value={maxHoursPerDay}
                onChange={e => setMaxHoursPerDay(e.target.value)}
                disabled={isLoading}
              />
            </label>
            <TableButton onClick={handleSaveMaxHours} disabled={isLoading} data-testid="save-max-hours-button">
              {isLoading ? '...' : t('common:save')}
            </TableButton>
          </>
        )}
      </div>

      {!selectedGroup && (
        <p className="text-sm text-muted">{t('admin:calendarSelectGroupHint')}</p>
      )}

      {selectedGroup && (
        <>
          <AdminTable data-testid="calendar-matrix">
            <thead>
              <tr>
                <AdminTh>{t('schedule:time')}</AdminTh>
                {DAYS.map(day => (
                  <AdminTh key={day}>{DAY_LABELS[day]}</AdminTh>
                ))}
              </tr>
            </thead>
            <tbody>
              {timePeriods.map(period => {
                const lunchBorder = lunchBorderTop(period.lunchBreakBefore);
                return (
                  <tr key={period.index} style={{ borderTop: lunchBorder }}>
                    <AdminTd>{period.label}</AdminTd>
                    {DAYS.map((day, dayIndex) => (
                      <AdminTd key={day}>
                        <input
                          type="checkbox"
                          checked={matrix[dayIndex]?.[period.index] ?? true}
                          onChange={() => toggleCell(dayIndex, period.index)}
                          disabled={isLoading}
                          aria-label={`${DAY_LABELS[day]} ${period.label}`}
                        />
                      </AdminTd>
                    ))}
                  </tr>
                );
              })}
            </tbody>
          </AdminTable>

          <div className="mt-3">
            <TableButton onClick={handleSaveCalendar} disabled={isLoading} data-testid="save-calendar-button">
              {isLoading ? '...' : t('admin:saveCalendar')}
            </TableButton>
          </div>

          {overrideRules.length > 0 && (
            <div className="mt-8">
              <h4 className="text-base font-semibold mb-2 text-content">{t('admin:calendarOverrides')}</h4>
              <p className="text-sm text-muted mb-3">{t('admin:calendarOverridesHint')}</p>
              <AdminTable>
                <thead>
                  <tr>
                    <AdminTh>{t('admin:dayOfWeek')}</AdminTh>
                    <AdminTh>{t('schedule:time')}</AdminTh>
                    <AdminTh>{t('admin:startDate')}</AdminTh>
                    <AdminTh>{t('admin:endDate')}</AdminTh>
                    <AdminTh>{t('common:actions')}</AdminTh>
                  </tr>
                </thead>
                <tbody>
                  {overrideRules.map(rule => (
                    <tr key={rule.id}>
                      <AdminTd>{DAY_LABELS[rule.dayOfWeek] ?? rule.dayOfWeek}</AdminTd>
                      <AdminTd>{rangeLabel(rule)}</AdminTd>
                      <AdminTd>{rule.effectiveFrom ?? '-'}</AdminTd>
                      <AdminTd>{rule.effectiveTo ?? '-'}</AdminTd>
                      <AdminTd>
                        <DeleteButton onClick={() => handleDeleteOverride(rule.id)} disabled={isLoading}>
                          {t('common:delete')}
                        </DeleteButton>
                      </AdminTd>
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
