import { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import type { Teacher, StudentGroup, Lesson } from '../types/domain';
import type { FilterState } from '../hooks/useScheduleFilter';

interface ScheduleFilterBarProps {
  teachers: Teacher[];
  studentGroups: StudentGroup[];
  lessons: Lesson[];
  filterState: FilterState;
  onToggleTeacher: (id: number) => void;
  onToggleSubject: (subject: string) => void;
  onToggleGroup: (id: number) => void;
  onRemoveTeacher: (id: number) => void;
  onRemoveSubject: (subject: string) => void;
  onRemoveGroup: (id: number) => void;
  onClearAll: () => void;
  hasActiveFilters: boolean;
  activeFilterCount: number;
}

export function ScheduleFilterBar({
  teachers,
  studentGroups,
  lessons,
  filterState,
  onToggleTeacher,
  onToggleSubject,
  onToggleGroup,
  onRemoveTeacher,
  onRemoveSubject,
  onRemoveGroup,
  onClearAll,
  hasActiveFilters,
  activeFilterCount,
}: ScheduleFilterBarProps) {
  const { t } = useTranslation('schedule');

  // Extract unique subjects from lessons
  const subjects = useMemo(() => {
    const set = new Set<string>();
    for (const lesson of lessons) {
      if (lesson.course?.subject) {
        set.add(lesson.course.subject);
      }
    }
    return Array.from(set).sort();
  }, [lessons]);

  return (
    <div className="flex flex-col gap-0.5 p-2.5 bg-surface rounded-lg shadow-soft border border-border-light">
      <div className="flex items-center gap-0.5 flex-wrap">
        <FilterDropdown
          label={t('filterTeachers')}
          icon="user"
          items={teachers.map(t => ({ id: t.id, label: t.name }))}
          selectedIds={filterState.teachers}
          onToggle={(id) => onToggleTeacher(Number(id))}
        />
        <FilterDropdown
          label={t('filterSubjects')}
          icon="book"
          items={subjects.map(s => ({ id: s, label: s }))}
          selectedIds={filterState.subjects}
          onToggle={(id) => onToggleSubject(String(id))}
        />
        <FilterDropdown
          label={t('filterGroups')}
          icon="users"
          items={studentGroups.map(g => ({ id: g.id, label: g.name }))}
          selectedIds={filterState.groups}
          onToggle={(id) => onToggleGroup(Number(id))}
        />
        {hasActiveFilters && (
          <button className="px-3 py-1.5 border border-border rounded-md bg-transparent text-muted text-xs font-medium cursor-pointer transition-all duration-150" onClick={onClearAll}>
            {t('filterClearAll')}
          </button>
        )}
      </div>

      {/* Active filter badges */}
      {hasActiveFilters && (
        <div className="flex flex-wrap gap-[0.35rem] pt-[0.35rem] border-t border-border-faint">
          {[...filterState.teachers].map(id => {
            const teacher = teachers.find(t => t.id === id);
            if (!teacher) return null;
            return (
              <FilterBadge
                key={`teacher-${id}`}
                label={teacher.name}
                color="#3b82f6"
                onRemove={() => onRemoveTeacher(id)}
              />
            );
          })}
          {[...filterState.subjects].map(subject => (
            <FilterBadge
              key={`subject-${subject}`}
              label={subject}
              color="#8b5cf6"
              onRemove={() => onRemoveSubject(subject)}
            />
          ))}
          {[...filterState.groups].map(id => {
            const group = studentGroups.find(g => g.id === id);
            if (!group) return null;
            return (
              <FilterBadge
                key={`group-${id}`}
                label={group.name}
                color="#14b8a6"
                onRemove={() => onRemoveGroup(id)}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

// ============================================================================
// FilterDropdown Component
// ============================================================================

interface FilterDropdownProps {
  label: string;
  icon: 'user' | 'book' | 'users';
  items: Array<{ id: string | number; label: string }>;
  selectedIds: Set<string | number>;
  onToggle: (id: string | number) => void;
}

function FilterDropdown({ label, icon, items, selectedIds, onToggle }: FilterDropdownProps) {
  const { t } = useTranslation('schedule');
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const filteredItems = useMemo(() => {
    if (!search.trim()) return items;
    const lower = search.toLowerCase();
    return items.filter(item => item.label.toLowerCase().includes(lower));
  }, [items, search]);

  const selectedCount = selectedIds.size;

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return;
    const handleClick = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
        setSearch('');
      }
    };
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, [isOpen]);

  // Focus search on open
  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setIsOpen(false);
      setSearch('');
    }
  };

  const iconSvg = ICONS[icon];

  return (
    <div ref={containerRef} className="relative" onKeyDown={handleKeyDown}>
      <button
        className={`flex items-center gap-2 px-2.5 py-1.5 border rounded-md bg-surface-alt text-content text-xs font-medium cursor-pointer transition-all duration-150 min-w-[120px] ${isOpen ? 'border-action ring-2 ring-blue-500/15' : ''} ${selectedCount > 0 ? 'bg-blue-500/8 border-blue-500' : ''}`}
        onClick={() => setIsOpen(!isOpen)}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span className="flex items-center opacity-70">{iconSvg}</span>
        <span className="flex-1 text-left">{label}</span>
        {selectedCount > 0 && (
          <span className="bg-blue-500 text-white text-[0.65rem] font-semibold px-2 py-0.5 rounded-[10px] min-w-[18px] text-center">{selectedCount}</span>
        )}
        <span className="text-[0.6rem] opacity-50">{isOpen ? '▲' : '▼'}</span>
      </button>

      {isOpen && (
        <div className="absolute top-[calc(100%+4px)] left-0 z-[100] min-w-[200px] max-w-[280px] max-h-[280px] bg-surface border border-border rounded-lg shadow-elevated overflow-hidden flex flex-col" role="listbox">
          <div className="p-1 border-b border-border-light">
            <input
              ref={inputRef}
              type="text"
              placeholder={t('filter.search')}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full px-2 py-1.5 border border-input-border rounded bg-surface-alt text-content text-xs outline-none"
            />
          </div>
          <div className="overflow-y-auto max-h-[220px]">
            {filteredItems.length === 0 ? (
              <div className="px-3 py-2 text-center text-muted text-xs">{t('filter.noItems')}</div>
            ) : (
              filteredItems.map(item => {
                const isSelected = selectedIds.has(item.id);
                return (
                  <button
                    key={item.id}
                    className={`flex items-center gap-2 w-full px-2.5 py-1.5 border-none bg-transparent text-content text-xs text-left cursor-pointer transition-colors duration-100 ${isSelected ? 'bg-blue-500/8' : ''}`}
                    onClick={() => onToggle(item.id)}
                    role="option"
                    aria-selected={isSelected}
                  >
                    <span className="w-[14px] h-[14px] border-[1.5px] border-border rounded flex items-center justify-center flex-shrink-0 transition-all duration-100">
                      {isSelected && <span className="text-blue-500 text-[0.65rem] font-bold leading-none">✓</span>}
                    </span>
                    <span className="overflow-hidden text-ellipsis whitespace-nowrap">{item.label}</span>
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================================================
// FilterBadge Component
// ============================================================================

interface FilterBadgeProps {
  label: string;
  color: string;
  onRemove: () => void;
}

function FilterBadge({ label, color, onRemove }: FilterBadgeProps) {
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 py-1 bg-surface-alt border border-border-light rounded text-xs font-medium text-content" style={{ borderLeftColor: color }}>
      <span className="max-w-[100px] overflow-hidden text-ellipsis whitespace-nowrap">{label}</span>
      <button
        className="flex items-center justify-center w-[14px] h-[14px] border-none bg-transparent text-muted text-base leading-none cursor-pointer rounded transition-all duration-100"
        onClick={onRemove}
        aria-label={`Remove ${label} filter`}
      >
        ×
      </button>
    </span>
  );
}

// ============================================================================
// Icons
// ============================================================================

const ICONS = {
  user: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  ),
  book: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
      <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
    </svg>
  ),
  users: (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
};
