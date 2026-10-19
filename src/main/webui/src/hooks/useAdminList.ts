import { useState, useEffect, useCallback } from 'react';
import { getErrorMessage } from '../utils/apiError';

export interface UseAdminListOptions<T> {
  /** Function to fetch the list of items */
  fetchFn: () => Promise<T[]>;
  /** Function to filter items by search query */
  filterFn: (item: T, query: string) => boolean;
  /** Auto-fetch on mount (default: true) */
  autoFetch?: boolean;
}

export interface UseAdminListResult<T> {
  /** List of all items */
  items: T[];
  /** Filtered list based on current filter query */
  filteredItems: T[];
  /** Current filter query */
  filter: string;
  /** Set filter query */
  setFilter: (query: string) => void;
  /** Currently editing item ID */
  editId: number | null;
  /** Set editing item ID */
  setEditId: (id: number | null) => void;
  /** Whether a CRUD operation is in progress */
  isLoading: boolean;
  /** Whether initial data is loading */
  isInitialLoading: boolean;
  /** Current error message (if any) */
  error: string | null;
  /** Clear current error */
  clearError: () => void;
  /** Reload items from server */
  load: () => Promise<void>;
  /** Execute an async operation with standard error handling */
  execute: <R>(fn: () => Promise<R>, onSuccess?: (result: R) => void) => Promise<R | null>;
}

/**
 * Hook for managing admin list pages with consistent state management.
 *
 * Provides:
 * - Item fetching and caching
 * - Filter state and filtered results
 * - Edit mode tracking
 * - Loading states (initial + operation)
 * - Error handling
 *
 * @example
 * ```tsx
 * const {
 *   items, filteredItems, filter, setFilter,
 *   editId, setEditId, isLoading, error, execute, load
 * } = useAdminList({
 *   fetchFn: api.getTeachers,
 *   filterFn: (teacher, query) =>
 *     teacher.name.toLowerCase().includes(query.toLowerCase()),
 * });
 * ```
 */
export function useAdminList<T extends { id: number }>(
  options: UseAdminListOptions<T>
): UseAdminListResult<T> {
  const { fetchFn, filterFn, autoFetch = true } = options;

  const [items, setItems] = useState<T[]>([]);
  const [filter, setFilter] = useState('');
  const [editId, setEditId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(autoFetch);
  const [error, setError] = useState<string | null>(null);

  const filteredItems = items.filter(item => filterFn(item, filter));

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const load = useCallback(async () => {
    setIsInitialLoading(true);
    setError(null);
    try {
      const data = await fetchFn();
      setItems(data);
    } catch (e) {
      setError(getErrorMessage(e, 'Failed to load data'));
    } finally {
      setIsInitialLoading(false);
    }
  }, [fetchFn]);

  const execute = useCallback(async <R,>(
    fn: () => Promise<R>,
    onSuccess?: (result: R) => void
  ): Promise<R | null> => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await fn();
      onSuccess?.(result);
      return result;
    } catch (e) {
      setError(getErrorMessage(e));
      return null;
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (autoFetch) {
      load();
    }
  }, [autoFetch, load]);

  return {
    items,
    filteredItems,
    filter,
    setFilter,
    editId,
    setEditId,
    isLoading,
    isInitialLoading,
    error,
    clearError,
    load,
    execute,
  };
}
