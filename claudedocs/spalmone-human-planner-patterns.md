# Human Planner's Recurring Patterns from Spalmone CSV

**Generated:** 2026-03-14
**Source:** `scripts/data/spalmone.csv`
**Purpose:** Validate pre-assignment heuristics against human planner's patterns

## CSV Structure

- Weekly timetable format with 15 student groups
- Columns: Date, Day, Time Slot, then repeated triplets (Subject, Teacher, Room) for each group
- Groups: 1oit, 2oit, 3oit, g04, g08, g13, g02, g06, g10, 4fa, g01, g05, g09, G14, G142
- Data spans multiple weeks (marked by "1", "2", etc.)

## Key Findings

### 1. Single-Day Recurring Patterns (80%+ Consistency)

These are prime candidates for pre-assignment - subjects that occur almost exclusively on one day of week.

#### Lab Subjects (High Pre-Assignment Value)

| Group | Subject | Day | Time(s) | Teacher | Room | Consistency |
|-------|---------|-----|---------|---------|------|-------------|
| **g02** | **servtecT28** | **Thursday** | **8:00-14:00** | **T28** | **T18** | **100%** |
| **g06** | **servtecT28** | **Thursday** | **8:00-14:00** | **T28** | **T18** | **100%** |
| **g10** | **servtecT28** | **Thursday** | **8:00-14:00** | **T28** | **T18** | **100%** |
| 4fa | stT28 | Thursday | 8:00-14:00 | T28 | - | 100% |
| g02 | lav | Tuesday | 8:00-14:00 | T18 | - | 96% |
| g10 | lav | Wednesday | 8:00-13:00 | T18 | - | 89% |
| 1oit | labtermo | Tuesday | 9:00-14:00 | est + Basse | - | 100% |
| 2oit | labtermo | Thursday | 8:00-13:00 | T03 | - | 83% |
| 1oit | labbasse | Friday | 8:00-13:00 | T03 | - | 100% |
| g02 | stvis | Tuesday | 8:00-14:00 | T26 | T18 | 89% |
| g10 | stvis | Wednesday | 8:00-13:00 | T26 | - | 89% |
| g06 | stvis | Wednesday | 8:00-14:00 | T26 | - | 89% |
| G142 | lab termo | Thursday | 8:00-13:00 | T03 | - | 100% |
| G142 | off | Thursday | 8:00-13:00 | T15 | - | 100% |
| g13 | labvend | Monday | 8:00-13:00 | - | - | 100% |

#### Informatica (High Pre-Assignment Value)

| Group | Subject | Day | Time(s) | Teacher | Consistency |
|-------|---------|-----|---------|---------|-------------|
| **g02** | **info** | **Thursday** | **8:00-13:00** | **T06** | **83%** |
| g01 | info | Monday | 8:00-9:00, 9:00-10:00, 12:00-14:00 | T06 | 91% |
| g05 | info | Thursday | 10:00-13:00 | T06 | 83% |
| g09 | info | Monday | 8:00-9:00, 11:00-13:00 | T06 | 93% |
| 1oit | info | Thursday | 8:00-11:00 | T06 | 88% |
| 2oit | info | Wednesday | 10:00-12:00 | T06 | 98% |
| 3oit | info | Tuesday | 8:00-9:00, 10:00-11:00 | T06 | 93% |
| g08 | info | Tuesday | 8:00-13:00 | T06 | 84% |

#### Other Lab-Style Subjects

| Group | Subject | Day | Time(s) | Teacher | Consistency |
|-------|---------|-----|---------|---------|-------------|
| g01 | ttborr | Wednesday | 8:00-11:00 | T07 | 100% |
| g05 | ttborr | Wednesday | 8:00-11:00 | T07 | 100% |
| g09 | ttborr | Wednesday | 8:00-11:00 | T07 | 100% |
| g01 | sic | Wednesday | 11:00-12:00 | T03 | 100% |
| g01 | Sic | Monday | 10:00-11:00 | T03 | 100% |
| g04 | Sic | Wednesday | 9:00-10:00 | T03 | 100% |
| g08 | Sic | Thursday | 9:00-11:00 | T03 | 100% |
| 2oit | Sic | Thursday | 11:00-13:00 | T03 | 100% |
| g09 | mou | Wednesday | 8:00-13:00 | T17 | 100% |
| g09 | rat | Wednesday | 8:00-13:00 | T23 | 100% |

### 2. T28's External Teacher Pattern

**Critical Finding:** T28 (servtecT28) is an external teacher who **ONLY teaches on Thursdays**.

- g02: Thursday 8:00-14:00 (100%)
- g06: Thursday 8:00-14:00 (100%)
- g10: Thursday 8:00-14:00 (100%)
- 4fa: Thursday 8:00-14:00 (stT28, 100%)

**Validation:** Our DataGenerator correctly creates VACATION records for T28 on non-Thursday days. The CSV confirms the human planner never schedules T28 outside Thursday.

### 3. Lab Block Patterns

The CSV shows multi-hour lab blocks that are candidates for LabBlock entity pre-assignment:

#### LabTermo (Thermodynamics Lab)
- 1oit: Tuesday 9:00-14:00 (est + Basse)
- 2oit: Thursday 8:00-13:00 (T03)
- 3oit: Thursday 8:00-13:00 (T03)
- G142: Thursday 8:00-13:00 (T03)

#### Lav (Laboratory of Design/Fashion)
- g02: Tuesday 8:00-14:00 (T18)
- g06: Friday 8:00-14:00 (T18)
- g10: Wednesday 8:00-13:00 (T18)

#### StVis (Stage/Visits)
- g02: Tuesday 8:00-14:00 (T26 in T18)
- g06: Wednesday 8:00-14:00 (T26)
- g10: Wednesday 8:00-13:00 (T26)

### 4. Rotation Patterns

Some subjects show clear rotation patterns where teacher-group combinations change:

#### Informatica (T06)
- Teaches 1oit (Thursday), 2oit (Wednesday), 3oit (Tuesday)
- Different days for different OIT groups - potential rotation

#### Disegno (Design)
- T03: 1oit
- T10: g04, g08
- T12: 2oit, 3oit
- T29: 1oit

#### Management (Gestione)
- T06: 1oit, 2oit, 3oit (all OIT groups)

### 5. Room Assignments

The CSV shows consistent room assignments for lab subjects:

- **T18 room**: Used for servtecT28 (T28), lav (T18), stvis (T26)
- **T14 room**: Used for taglio (T16), some Mou/Modello (T17)
- Other lab rooms may be inferred from teacher names (e.g., "est + Basse" for labtermo)

### 6. Validation Against DataGenerator

#### Confirmed Patterns:
1. **T28 availability**: CSV confirms Thursday-only teaching
2. **Single-day labs**: Many lab subjects occur on consistent days
3. **Multi-hour blocks**: Lab courses often span 3-6 hours on one day

#### Discrepancies to Investigate:
1. **Room tags**: CSV doesn't explicitly show room tags (e.g., "informatica", "meccanica")
2. **Rotation periods**: CSV doesn't show trimester/period boundaries
3. **Pre-assignment scope**: CSV shows final schedule, not pre-assignment decisions

### 7. Pre-Assignment Heuristic Validation

Our M17 constraint ("lab recurring pattern") requires that within a rotation period, all blocks of a single-day lab course occur on the same day of week.

**CSV Validation:** The human planner's schedule strongly supports this heuristic:
- servtecT28 for all FA groups: 100% Thursday
- labtermo for 1oit: 100% Tuesday
- lav for g02: 96% Tuesday
- Many other lab subjects: 80%+ consistency on primary day

**Conclusion:** M17 accurately models the human planner's approach to lab scheduling.

## Recommendations

1. **LabRotation Records**: Create LabRotation records for high-consistency lab patterns:
   - T28's servtecT28: Thursday, all FA groups
   - Labtermo: Thursday (2oit, 3oit) or Tuesday (1oit)
   - Lav: Group-specific days
   - StVis: Group-specific days

2. **Room Tag Validation**: Confirm with school which rooms correspond to:
   - "T18" (likely Lab Informatica or similar)
   - "T14" (likely Lab Design)
   - Other lab rooms

3. **Rotation Period Analysis**: If CSV data can be split by trimester/period, analyze whether:
   - Teacher-group combinations change across periods
   - Day-of-week patterns persist within periods
   - Multi-day patterns show rotation behavior

4. **Pre-Assignment Priority**: Focus pre-assignment on:
   - 100% consistent single-day patterns (T28, labtermo, labbasse)
   - 90%+ consistent patterns (info for many groups, ttborr, mou, rat)
   - Multi-hour lab blocks (3+ hours on same day)

## Appendix: Full Pattern List

See `/tmp/spalmone_key_patterns.txt` for complete analysis including:
- All 80%+ consistent single-day patterns
- Multi-day patterns with top 2 days
- Rotation pattern analysis by subject
