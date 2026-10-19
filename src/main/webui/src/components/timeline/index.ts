// Timeline components
export { TimelineHeader } from './TimelineHeader';
export { WeekViewGrid } from './WeekViewGrid';
export { TimelineView } from './TimelineView';

// Shared cell renderers (plain functions, no component overhead)
export { renderSubColumnContent, renderInlineCell } from './cellRenderer';

// Types
export type {
  TimePeriod,
  GroupByMode,
  ViewMode,
  Column,
  LessonLookup,
  CSSVarsWithSubjectColor,
  TimelineHeaderProps,
  WeekViewGridProps,
  TimelineViewProps,
  ScheduleNavigation,
  ScheduleData,
} from './types';

export { COLUMN_WIDTHS } from './types';
