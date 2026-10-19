---
id: task-020
title: ui - analyze button in the ui
status: Done
assignee: []
created_date: '2026-02-18 05:58'
updated_date: '2026-02-18 21:36'
labels:
  - ui
  - enhancement
  - analysis
dependencies: []
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Description

Add an "Analyze" button in the UI that triggers schedule analysis and displays constraint score breakdown.

---

## Redesign Plan

### Original Implementation
- **Commit**: `8723a8e` (Add stage day highlighting and copyable pre-assignment report)
- **Files affected**: `SolverControls.tsx`
- **Current state**: "Analyze" button exists in SolverControls but:
  - No API integration
  - No visual feedback
  - No result display
  - Button does nothing when clicked

### Design Direction
- **Aesthetic**: Data Dashboard - a command center for schedule analysis with clear metrics and drill-down capability
- **Key differentiator**: The analysis opens as an expandable panel (not a modal) that shows constraint violations in a sortable, filterable table
- **Typography**: Monospace for scores, clear hierarchy for constraint names
- **Color**: 
  - Hard constraints: Red badges
  - Medium constraints: Yellow/amber badges
  - Soft constraints: Blue badges
  - Zero violations: Green checkmark
- **Motion**: Smooth panel slide-in (250ms), staggered row animation

### Implementation Steps

1. **Create AnalysisPanel component**
   ```tsx
   // src/main/webui/src/components/AnalysisPanel.tsx
   interface AnalysisPanelProps {
     analysis: ConstraintAnalysis | null;
     isLoading: boolean;
     onClose: () => void;
   }
   ```

2. **Wire Analyze button to API**
   ```tsx
   const handleAnalyze = async () => {
     setIsAnalyzing(true);
     try {
       const result = await api.analyzeSchedule();
       setAnalysis(result);
     } finally {
       setIsAnalyzing(false);
     }
   };
   ```

3. **Design the analysis result display**
   ```tsx
   <div style={styles.panel}>
     <header>
       <h3>Schedule Analysis</h3>
       <ScoreBadge score={analysis.score} />
       <CloseButton onClick={onClose} />
     </header>
     
     <div style={styles.summary}>
       <MetricCard label="Hard Score" value={analysis.hardScore} variant="danger" />
       <MetricCard label="Medium Score" value={analysis.mediumScore} variant="warning" />
       <MetricCard label="Soft Score" value={analysis.softScore} variant="info" />
     </div>
     
     <div style={styles.constraints}>
       <FilterBar onChange={setFilter} />
       <ConstraintTable 
         constraints={filteredConstraints}
         sortBy={sortBy}
         onSort={setSortBy}
       />
     </div>
   </div>
   ```

4. **Constraint table with details**
   ```tsx
   <table>
     <thead>
       <tr>
         <th>Constraint ID</th>
         <th>Type</th>
         <th>Score</th>
         <th>Matches</th>
       </tr>
     </thead>
     <tbody>
       {constraints.map(c => (
         <tr key={c.id}>
           <td><ConstraintBadge id={c.id} /></td>
           <td><TypeBadge type={c.type} /></td>
           <td>{c.score}</td>
           <td>{c.matchCount}</td>
         </tr>
       ))}
     </tbody>
   </table>
   ```

5. **Add constraint type badges**
   ```tsx
   const TypeBadge = ({ type }) => {
     const colors = {
       HARD: '#ef4444',
       MEDIUM: '#f59e0b',
       SOFT: '#3b82f6',
     };
     return (
       <span style={{
         background: colors[type],
         color: '#fff',
         padding: '0.15rem 0.4rem',
         borderRadius: '4px',
         fontSize: '0.7rem',
         fontWeight: 600,
       }}>
         {type}
       </span>
     );
   };
   ```

6. **Add filtering and sorting**
   - Filter by constraint type (All/Hard/Medium/Soft)
   - Filter by violation count (Show only violations)
   - Sort by score, constraint ID, match count

7. **Add loading state**
   - Button shows spinner while analyzing
   - Panel shows skeleton while loading

### Testing Checklist
- [ ] Analyze button triggers API call
- [ ] Loading state shows during API call
- [ ] Analysis panel opens with results
- [ ] Score breakdown displays correctly
- [ ] Constraint table is sortable
- [ ] Filter by type works
- [ ] Close button dismisses panel
- [ ] Results persist until re-analysis

### Files to Modify
- `src/main/webui/src/components/SolverControls.tsx`
- `src/main/webui/src/components/AnalysisPanel.tsx` (new)
- `src/main/webui/src/api/scheduleApi.ts`
- `src/main/webui/src/types/domain.ts`
<!-- SECTION:DESCRIPTION:END -->
