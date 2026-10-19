import { useState, useEffect, useCallback, useMemo } from 'react';

export interface FilterState {
  teachers: Set<number>;
  subjects: Set<string>;
  groups: Set<number>;
}

const STORAGE_KEY = 'scheduleFilterState';

interface StoredFilter {
  teachers: number[];
  subjects: string[];
  groups: number[];
}

export function useScheduleFilter() {
  const [filterState, setFilterState] = useState<FilterState>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed: StoredFilter = JSON.parse(stored);
        return {
          teachers: new Set(parsed.teachers || []),
          subjects: new Set(parsed.subjects || []),
          groups: new Set(parsed.groups || []),
        };
      }
    } catch (e) {
      console.warn('Failed to load filter state:', e);
    }
    return {
      teachers: new Set(),
      subjects: new Set(),
      groups: new Set(),
    };
  });

  // Persist to localStorage
  useEffect(() => {
    const toStore: StoredFilter = {
      teachers: [...filterState.teachers],
      subjects: [...filterState.subjects],
      groups: [...filterState.groups],
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(toStore));
  }, [filterState]);

  const toggleTeacher = useCallback((id: number) => {
    setFilterState(prev => {
      const next = new Set(prev.teachers);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return { ...prev, teachers: next };
    });
  }, []);

  const toggleSubject = useCallback((subject: string) => {
    setFilterState(prev => {
      const next = new Set(prev.subjects);
      if (next.has(subject)) {
        next.delete(subject);
      } else {
        next.add(subject);
      }
      return { ...prev, subjects: next };
    });
  }, []);

  const toggleGroup = useCallback((id: number) => {
    setFilterState(prev => {
      const next = new Set(prev.groups);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return { ...prev, groups: next };
    });
  }, []);

  const removeTeacher = useCallback((id: number) => {
    setFilterState(prev => {
      const next = new Set(prev.teachers);
      next.delete(id);
      return { ...prev, teachers: next };
    });
  }, []);

  const removeSubject = useCallback((subject: string) => {
    setFilterState(prev => {
      const next = new Set(prev.subjects);
      next.delete(subject);
      return { ...prev, subjects: next };
    });
  }, []);

  const removeGroup = useCallback((id: number) => {
    setFilterState(prev => {
      const next = new Set(prev.groups);
      next.delete(id);
      return { ...prev, groups: next };
    });
  }, []);

  const clearAll = useCallback(() => {
    setFilterState({
      teachers: new Set(),
      subjects: new Set(),
      groups: new Set(),
    });
  }, []);

  const hasActiveFilters = useMemo(() => {
    return filterState.teachers.size > 0 ||
           filterState.subjects.size > 0 ||
           filterState.groups.size > 0;
  }, [filterState]);

  const activeFilterCount = useMemo(() => {
    return filterState.teachers.size + filterState.subjects.size + filterState.groups.size;
  }, [filterState]);

  return {
    filterState,
    toggleTeacher,
    toggleSubject,
    toggleGroup,
    removeTeacher,
    removeSubject,
    removeGroup,
    clearAll,
    hasActiveFilters,
    activeFilterCount,
  };
}
