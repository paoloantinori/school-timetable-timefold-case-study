# Human Planner Heuristic Pipeline

The human schedulers follow a deterministic, sequential pipeline — they lock down known decisions first, in a specific order, progressively reducing the remaining search space. These heuristics are NOT optimization problems — they are deterministic computations from input parameters.

> **See also**: `pre-assignment-algorithms.md` - Technical implementation of the algorithms used in pre-assignment (SlotCandidate priority ordering, batch preload, backtracking search).

## Pipeline Summary

| Step | Action | Nature | Implementation Status |
|------|--------|--------|----------------------|
| 1 | Set stage days per group | External input (pins/removes slots) | Implemented (StagePattern entity + H4 constraint) |
| 2 | Pin acconciatura lab rotation | Deterministic (teachers x periods x fixed days) | Implemented (`HybridPhase1aService` CSP, acconciatura phase) |
| 3 | Place other lab blocks | Fixed weekly patterns, teacher-centric | Implemented (`HybridPhase1aService` CSP, labs phase; labs are pinned Lesson rows) |
| 4 | Place professionalizzanti on stage-overlap days | Constrained placement | Implemented (`HybridPhase1aService` CSP, professionalizzanti phase + H4 exemption) |
| 5 | Fill remaining hours | Free-form (solver territory) | Implemented (staged solver, `solve-staged` + multi-start) |

## Step 1: Stage Calendar (External Input)

Define which specific days each student group is away on alternanza/stage (work internships). This is an external input decided by the school, not computed. It immediately shrinks the feasible schedule by blocking those group-by-day combinations.

Groups like G07, G11, G12, and G13 have approximately 295 hours each (roughly 49 days removed from scheduling).

The effect is logically "pinning" the stage days, or equivalently, reducing the planning scope.

## Step 2: Acconciatura Lab Rotation (Deterministic)

Three external professional teachers share the acconciatura lab across three fixed rotation cycles:

- **T28** — available Thursday only (truly external, 1 day/week)
- **T26** — available Wednesday (plus other days; 4 days/week total)
- **T18** — available Tuesday (plus other days)

### Rotation mechanism
- Each teacher has a **permanently fixed day of the week** (they never switch days).
- In each rotation period, each teacher teaches a single course to a single student group, in the lab, on their fixed day, for a full-day block (6 hours).
- This assignment repeats weekly for the entire period.
- At the end of each period, **the student groups rotate** — each teacher gets a different group, but keeps the same day.
- There are **3 rotation cycles** (roughly trimester-aligned), with approximate period boundaries:
  - Period 1: 06/10 to 22/12
  - Period 2: 10/01 to 04/04
  - Period 3: 04/05 to 01/06
- **G12** enters the rotation only from February, with fewer hours than the other groups.

Which teacher on which fixed day depends on their availability. This should be a variable parameter that can be changed every year before planning.

## Step 3: Other Lab Blocks (Teacher-Centric, Fixed Weekly Patterns)

> **How courses enter pre-assignment:** The pipeline is driven by `Course.requiredRoomTags`,
> not by `LabRotation` records. Any course with a room tag is automatically picked up by
> `HybridPhase1aService.loadLabSessionsByRoomTag()`. LabRotations are optional hints
> that provide a preferred day — without them, the CSP algorithm tries all valid days.
> See `HybridPhase1aService.loadLabSessionsByRoomTag()` Javadoc for full details.

**Critical insight: lab allocation is per teacher, not per subject.** The human schedulers think in terms of "teacher lab sessions," not individual subject blocks.

Example: "conf T16 / taglio T16 / taglio T16" — this assigns 3 adjacent hours to T16, but technically 2 different subjects are taught by the same person in the same lab.

**The unit of scheduling is: Teacher + Lab room + Student group + Consecutive hours.** Whether the hours within the block are Confezione, Taglio, Modello, etc. is secondary — the human scheduler freezes the teacher-day allocation first.

### Lab families
- **Meccanica** (R15 room) — Lavorazioni meccaniche (4h), Taglio metalli (2h), CAD (2h), Controllo numerico (2h) — teachers: T15, T03, T10
- **Informatica** (Lab Info 1 & 2) — teachers: T06, T29, T12, T15
- **Termoidraulica** (Lab. Termo) — teachers: T03, T31
- **Moda** (R16) — Confezione, Modello, Moulage, Operazioni di taglio, Tecnologia tessile — teachers: T16, T17, T20, T07
- **T27** (G12, acconciatura lab)

### What the human planner does
"What the human planner does is really to pin them, and only move what is left out."

## Step 4: Professionalizzanti on Stage-Overlap Days

During stage periods, a student group is **split** — some students are at their internship, others remain at school. On those days:

- **Professionalizzanti** courses CAN run — because students on stage earn equivalent credits through their internship
- **Fundamentals** (math, Italian, science, etc.) CANNOT run — because students on stage would miss content they can't make up

This is the very last heuristic step before trying to fill the other remaining hours.

### Implementation

Two-part implementation (now in `HybridPhase1aService`, CSP-based; see `pre-assignment-algorithms.md`):

1. **H4 constraint exemption**: H4 (stage blackout) filters out `course.professionalizzanti == true`, so only fundamentals are blocked on stage days. Professionalizzanti courses are allowed.

2. **Professionalizzanti CSP phase**: Runs after the acconciatura and labs phases. For each student group with StagePatterns:
   - Computes all valid stage dates (within pattern periods, matching day mask, excluding holidays)
   - Collects unassigned professionalizzanti Lesson entities (lab lessons excluded, handled in the labs phase)
   - Finds available timeslots on each stage date (not occupied by entities pinned in the earlier phases)
   - Pins professionalizzanti lessons into those slots (room left to solver)

### REST endpoint
```
POST /api/schedule/pre-assign/professionalizzanti
```

### Interaction with H4
H4 acts as the solver-side safety net: even if a fundamental lesson is NOT pre-assigned to a stage day, the solver cannot place it there (hard constraint). The pre-assigner proactively fills stage days with professionalizzanti content.

## Step 5: Fill Remaining Hours (Solver Territory)

Only after all deterministic assignments are locked does free-form optimization begin. The solver handles placement of remaining non-lab, non-professionalizzanti hours (regular classroom subjects like math, Italian, science, etc.).

## Related Concepts

### Doppio (Co-Teaching)
Some lab sessions require a second teacher present simultaneously. The primary teacher "owns" the lab block; the doppio teacher follows (same timeslots, same room). The doppio teacher's own courses cannot be scheduled during those timeslots.

Known doppio pairs:
- **T03** — doppio in termoidraulica
- **T10** — doppio in officina (meccanica)
- **T14** — doppio in R16 (moda)
- **T18** — doppio in acconciatura

### External Teacher Availability Safety Net
For truly external teachers like T28 (1 day/week), the system generates `TeacherAvailability(VACATION)` records for every school day that is NOT their available day. This ensures constraint H5 prevents scheduling on wrong days, even without pre-assignment.

### The "Spalmone" (Master Schedule)
The previous year's complete master schedule — called the "spalmone" ("the big spread"). A reference document for extracting actual lab scheduling patterns that the human planners used. Used to validate and derive the rotation data seeded in DataGenerator.
