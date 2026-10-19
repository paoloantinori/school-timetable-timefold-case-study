# Constraint ID Documentation Rule

> 📌 **Session memory** (operational convention). The constraint-ID reference list below was captured ~March 2026 and may lag the authoritative table in `CLAUDE.md` ("Canonical Constraint IDs"). Treat `CLAUDE.md` as the source of truth for current IDs.

## Rule

When mentioning a constraint by its ID (e.g., H11, H17, M18), always append a brief parenthetical description that reminds the reader what the constraint does.

**Why:** The project has many constraints and quick context is needed. Without reminders, it's easy to forget what H11 vs H17 vs H18 means.

**Wrong:**
- "H11 violations increased"
- "The solver fixed H17 but not H11"

**Right:**
- "H11 (Max student group daily hours) violations increased"
- "The solver fixed H17 (Vertically compact) but not H11 (Max student group daily hours)"

## Quick reference (as captured — verify against CLAUDE.md)

**Hard:**
- H1: Teacher conflict — same teacher can't be in two places
- H2: Room conflict — same room can't host two lessons
- H3: Student group conflict — same group can't be in two places
- H4: Stage blackout — non-prof groups can't attend during stage
- H5: Teacher unavailability — VACATION/SICK periods
- H6: School holiday — no lessons on holidays
- H7: Max teacher daily hours — teacher workday limit
- H9: Lab entity valid placement — blocks in correct slots
- H11: Max student group daily hours — student attendance limit
- H14: Student group start date — can't attend before start
- H15: Same-day same-subject consecutive — blocks must be contiguous
- H16: Consecutive same-subject same teacher-room — optimization
- H17: Vertically compact — no gaps in student daily schedule
- H18 → M18: 5-hour groups start from slot 0 — downgraded to medium
- H-D1: Doppio teacher conflict — co-teacher must be present
- H19: Max one lab block per day per pair
- H-M2: Room suitability — labs need tagged rooms

**Medium:**
- M3: Room capacity — can't exceed room size
- M5: Reward Informatica after stage — thesis writing time
- M12: Overflow beyond target end date — horizontal compactness
- M18: 5-hour groups should start from slot 0 (formerly H18)
- M20: No sparse student days — days with lessons should be full
- M21: Max 1h per subject per day — variety

**Soft:**
- S1: Minimize teacher gaps — reduce idle time
- S3: Teacher preference — respect preferences
- S7: Reward consecutive student lessons — denser schedule
- S8: Isolated student lesson — avoid single lessons
- S9: Dilute hours over year — spread out
- S10: Discourage generic lessons in labs — proper room usage
- S11: Student group room affinity — consistency
- S17: Reward same-subject block pair — golden-like blocks (L3, TASK-381)
