import { useState, useEffect, useCallback } from 'react';

export function useHiddenGroups() {
  const [hiddenIds, setHiddenIds] = useState<Set<number>>(() => {
    const stored = localStorage.getItem('hiddenGroupIds');
    return stored ? new Set(JSON.parse(stored)) : new Set();
  });

  useEffect(() => {
    localStorage.setItem('hiddenGroupIds', JSON.stringify([...hiddenIds]));
  }, [hiddenIds]);

  const hideGroup = useCallback((id: number) => {
    setHiddenIds(prev => new Set(prev).add(id));
  }, []);

  const showGroup = useCallback((id: number) => {
    setHiddenIds(prev => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  }, []);

  const showAll = useCallback(() => {
    setHiddenIds(new Set());
  }, []);

  const isHidden = useCallback((id: number) => hiddenIds.has(id), [hiddenIds]);

  return { hiddenIds, hideGroup, showGroup, showAll, isHidden };
}
