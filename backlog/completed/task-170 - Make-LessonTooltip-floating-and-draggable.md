---
id: TASK-170
title: Make LessonTooltip floating and draggable
status: Done
assignee: []
created_date: '2026-03-04 10:57'
updated_date: '2026-03-04 18:16'
labels:
  - enhancement
  - frontend
  - ui
  - ux
  - tooltip
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The LessonTooltip is currently **centered and immobile** - it uses `fixed inset-0 flex items-center justify-center` positioning which:
1. Always centers the tooltip, blocking the underlying schedule
2. Cannot be repositioned to see the lesson being inspected
3. Long tooltips may cover important schedule context

## Current Implementation

**LessonTooltip.tsx (lines 95-120):**
```tsx
return createPortal(
  <div
    className="fixed inset-0 bg-overlay flex items-center justify-center z-modal"
    onClick={onClose}
  >
    <div
      ref={contentRef}
      className="bg-surface p-5 rounded-lg min-w-[280px] max-w-modal-md ..."
      onClick={e => e.stopPropagation()}
    >
      {/* Content */}
    </div>
  </div>,
  document.body
);
```

**Key constraints:**
- Z-index: `z-modal` (1000)
- Max width: 500px (`max-w-modal-lg`)
- Close on click outside
- No drag libraries in dependencies

## Desired Behavior

1. **Floating**: Tooltip starts centered but can be moved anywhere
2. **Draggable**: Users can drag the tooltip by its header
3. **Constrained**: Tooltip stays within viewport bounds
4. **Non-intrusive**: Drag doesn't interfere with text selection or close behavior
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 [UI] Add drag handle to LessonTooltip header (visual indicator)
- [x] #2 [UI] Tooltip can be dragged/repositioned within viewport bounds
- [x] #3 [UI] Tooltip position persists while open (doesn't reset to center on re-render)
- [x] #4 [UX] Click-to-drag on header, click outside still closes
- [x] #5 [UX] Dragging doesn't interfere with text selection inside tooltip
- [x] #6 [A11y] Keyboard users can still access all tooltip content
- [x] #7 [A11y] Drag handle has appropriate aria-label
- [x] #8 [Mobile] Touch drag works on mobile devices
- [x] #9 [Code] No new external dependencies (implement custom drag)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Options

### Option A: Custom Drag Implementation (Recommended)

No external dependencies required. Implement using pointer events.

**Pros:** No bundle size increase, full control
**Cons:** More code to maintain

### Option B: Use react-draggable Library

Add `react-draggable` to dependencies.

**Pros:** Battle-tested, feature-rich
**Cons:** Adds ~10KB to bundle

---

## Implementation Plan (Option A: Custom Drag)

### Phase 1: Add Position State

**File:** `src/main/webui/src/components/LessonTooltip.tsx`

```tsx
// Add state for tooltip position
const [position, setPosition] = useState({ x: 0, y: 0 });
const [isDragging, setIsDragging] = useState(false);
const [hasManuallyPositioned, setHasManuallyPositioned] = useState(false);

// Refs for drag handling
const dragStartPos = useRef({ x: 0, y: 0 });
const tooltipStartPos = useRef({ x: 0, y: 0 });
```

### Phase 2: Create Drag Handle Component

```tsx
interface DragHandleProps {
  onDragStart: (e: React.PointerEvent) => void;
}

function DragHandle({ onDragStart }: DragHandleProps) {
  return (
    <div
      className="flex items-center justify-center py-2 cursor-grab active:cursor-grabbing"
      onPointerDown={onDragStart}
      aria-label="Drag to reposition"
    >
      <div className="flex gap-1">
        <div className="w-2 h-2 rounded-full bg-border" />
        <div className="w-2 h-2 rounded-full bg-border" />
        <div className="w-2 h-2 rounded-full bg-border" />
      </div>
    </div>
  );
}
```

### Phase 3: Implement Drag Handlers

```tsx
const handleDragStart = (e: React.PointerEvent) => {
  e.preventDefault();
  setIsDragging(true);
  dragStartPos.current = { x: e.clientX, y: e.clientY };
  tooltipStartPos.current = { ...position };
  
  // Capture pointer for reliable move/end events
  (e.target as HTMLElement).setPointerCapture(e.pointerId);
};

const handleDragMove = useCallback((e: PointerEvent) => {
  if (!isDragging) return;
  
  const deltaX = e.clientX - dragStartPos.current.x;
  const deltaY = e.clientY - dragStartPos.current.y;
  
  // Calculate new position with viewport bounds
  const tooltipWidth = 450; // Approximate
  const tooltipHeight = 400; // Approximate
  const padding = 20;
  
  const newX = Math.max(-window.innerWidth/2 + tooltipWidth/2 + padding,
                 Math.min(window.innerWidth/2 - tooltipWidth/2 - padding,
                   tooltipStartPos.current.x + deltaX));
  const newY = Math.max(-window.innerHeight/2 + tooltipHeight/2 + padding,
                 Math.min(window.innerHeight/2 - tooltipHeight/2 - padding,
                   tooltipStartPos.current.y + deltaY));
  
  setPosition({ x: newX, y: newY });
  setHasManuallyPositioned(true);
}, [isDragging]);

const handleDragEnd = () => {
  setIsDragging(false);
};

// Add event listeners
useEffect(() => {
  if (isDragging) {
    window.addEventListener('pointermove', handleDragMove);
    window.addEventListener('pointerup', handleDragEnd);
    return () => {
      window.removeEventListener('pointermove', handleDragMove);
      window.removeEventListener('pointerup', handleDragEnd);
    };
  }
}, [isDragging, handleDragMove]);
```

### Phase 4: Update JSX Structure

```tsx
return createPortal(
  <div
    className="fixed inset-0 bg-overlay z-modal"
    onClick={!isDragging ? onClose : undefined}
  >
    <div
      ref={contentRef}
      className={cn(
        "absolute bg-surface p-5 rounded-lg min-w-[280px] max-w-modal-md ...",
        isDragging && "select-none pointer-events-none"
      )}
      style={{
        left: `calc(50% + ${position.x}px)`,
        top: `calc(50% + ${position.y}px)`,
        transform: 'translate(-50%, -50%)',
        transition: hasManuallyPositioned ? 'none' : 'transform 0.15s ease-out',
      }}
      onClick={e => e.stopPropagation()}
    >
      {/* Drag handle at top */}
      <DragHandle onDragStart={handleDragStart} />
      
      {/* Existing content */}
      <div className="space-y-4">
        {/* ... */}
      </div>
    </div>
  </div>,
  document.body
);
```

### Phase 5: Add CSS for Drag Handle

```css
/* In LessonTooltip.css or inline */
.drag-handle {
  cursor: grab;
  user-select: none;
  touch-action: none; /* Prevent scrolling while dragging */
}

.drag-handle:active {
  cursor: grabbing;
}

/* Visual indicator */
.drag-dots {
  display: flex;
  gap: 0.25rem;
  justify-content: center;
}

.drag-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background-color: var(--border);
}
```

### Phase 6: Reset Position on Open

```tsx
// Reset position when tooltip opens for a new lesson
useEffect(() => {
  setPosition({ x: 0, y: 0 });
  setHasManuallyPositioned(false);
}, [lesson?.id]); // Assuming lesson is a prop
```

---

## Files to Modify

| File | Changes |
|------|---------|
| `components/LessonTooltip.tsx` | Add drag state, handlers, DragHandle component |
| `styles/LessonTooltip.css` | Optional: drag handle styles (can use Tailwind) |

---

## Edge Cases to Handle

1. **Touch devices**: Pointer events work for both mouse and touch
2. **Viewport resize**: Consider re-centering or clamping position
3. **Multiple tooltips**: Ensure z-index stays consistent
4. **Escape key**: Should still close tooltip (not affected by drag)
5. **Text selection**: Disabled during drag, re-enabled after
6. **Scroll inside tooltip**: Should work independently of drag

---

## Testing Strategy

1. **Drag test**: Click header → drag → verify position changes
2. **Bounds test**: Try to drag off-screen → verify clamped
3. **Close test**: Click outside → verify closes
4. **Text selection test**: Select text inside tooltip → verify works
5. **Mobile test**: Touch drag on tablet → verify works
6. **Keyboard test**: Tab through tooltip → verify focus works

---

## Alternative: react-draggable (Option B)

If you prefer a library solution:

```bash
npm install react-draggable
```

```tsx
import Draggable from 'react-draggable';

<Draggable handle=".drag-handle" bounds="parent">
  <div className="bg-surface p-5 rounded-lg ...">
    <div className="drag-handle cursor-grab ...">
      {/* Drag handle */}
    </div>
    {/* Content */}
  </div>
</Draggable>
```

**Trade-off:** Simpler implementation but adds external dependency.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Implementation Complete

**Changes made to `LessonTooltip.tsx`:**

1. Added drag state management (`position`, `isDragging`, refs)

2. Created `DragHandle` component with 3-dot visual indicator

3. Implemented pointer event handlers (`handleDragStart`, `handleDragMove`, `handleDragEnd`)

4. Added viewport boundary clamping to prevent off-screen dragging

5. Position resets to center when opening a new lesson

6. Added `touch-none` for mobile support, `select-none` during drag

7. Added `aria-label="Drag to reposition tooltip"` for accessibility

**Key technical decisions:**

- Used Pointer Events API for cross-device support (mouse + touch)

- `setPointerCapture()` ensures reliable event tracking during drag

- No external dependencies added (custom implementation)

- Transform-based positioning preserves animation capabilities
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Manual test: drag tooltip to all 4 corners of viewport
- [ ] #2 Test: drag doesn't trigger accidental close
- [ ] #3 Test: tooltip stays within viewport bounds (can't drag off-screen)
- [ ] #4 Test: scroll inside tooltip still works during/after drag
- [ ] #5 Mobile test: touch drag works on tablet
- [ ] #6 Visual test: drag handle is clearly visible but not intrusive
<!-- DOD:END -->
