/**
 * Minimal pub/sub so a manual lesson move (TASK-357, ScheduleTimeline) can
 * force the header ValidationBadge (TASK-354) to re-poll GET
 * /schedule/validation/latest immediately, instead of waiting out its 15s
 * interval. The two components are unrelated siblings under App.tsx (badge
 * in the nav, schedule in the routed body) so there's no prop path between
 * them - a tiny module-level event bus is the cheapest correct fix, well
 * short of pulling in a state management library for one signal.
 */
type Listener = () => void;

const listeners = new Set<Listener>();

/** Called after a manual move's POST /validation/run completes. */
export function notifyValidationRefresh(): void {
  listeners.forEach(listener => listener());
}

/** Subscribed by ValidationBadge; returns an unsubscribe function. */
export function subscribeValidationRefresh(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
