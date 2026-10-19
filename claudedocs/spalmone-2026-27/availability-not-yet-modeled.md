# Vincoli Docenti — transcription table and NOT-YET-MODELED registry (TASK-345)

Source: the "Vincoli Docenti" sheet of `Spalmone 2026_2027 FC DUALE condiviso
maintainer.xlsx` (dumped cell-by-cell 2026-07-16; every non-empty row is accounted
for below — either transcribed into `src/main/resources/seed/availability_rules.csv`
or listed here as NOT-YET-MODELED). Slot indices are
`TimeslotSequence.SLOT_START_TIMES`: 0=08-09, 1=09-10, 2=10-11, 3=11-12,
4=12-13, 5=13-14, 6=14:30-15:30, 7=15:30-16:30.

## Transcribed (availability_rules.csv, 23 rows)

| Teacher | Raw text | Rule(s) | Rationale |
|---|---|---|---|
| T04 | "8,00-14,00 dal lunedì al giovedì" (2026-07-17 docx, `vincoli-in-parole.md`) | Mon-Thu VACATION slots 6-7; Fri VACATION 0-7 | **Updated TASK-368 (2026-07-17):** the docx extends the old "8,00-13,00" sheet reading to 14:00 — widened from blocking slots 5-7 to blocking only slots 6-7 Mon-Thu. Friday stays a separate full-day rule (unchanged) — see "Why non-overlapping rules" below |
| T05 | "08:00–14:00 dal lunedì al giovedì. Un giorno a scelta deve essere lasciato senza ore" (2026-07-17 docx) | Mon-Thu VACATION slots 6-7 (uniform); Fri VACATION 0-7 | **Updated TASK-368 (2026-07-17):** the docx drops the old sheet's Tuesday-afternoon-available asymmetry — Tuesday now gets the same slots 6-7 block as Mon/Wed/Thu. The "one chosen day must be fully free" clause is now MODELED as M25 (see below, was NOT-YET-MODELED section 1) |
| T11 | "09:00–14:00 dal lunedì al venerdì. Un giorno a scelta può iniziare alle 08:00" (2026-07-17 docx) | Every weekday: VACATION slots 6-7 only (slot 0 UNBLOCKED) | **Updated TASK-368 (2026-07-17):** the daily slot-0 VACATION block was removed entirely — slot 0 is now generically open, capped by the new weekly M24 constraint instead of a static per-day rule. The "one day may start at 8:00" clause is now MODELED as M24 (see below, was NOT-YET-MODELED section 2) |
| T10 | "Libero il Giovedì mattina per LES e G14 9,00-17,00" | Thursday VACATION slots 0-3 (morning only) | **Updated TASK-374 (2026-07-17):** narrowed from the original full-day block to slots 0-3 (8:00-12:00) — golden-derived, see "Resolved via golden-derivation" below (Q11) |
| T12 | "LUNEDI' 14 SETTEMBRE 2026 / 16 NOVEMBRE 2026 / 23 NOVEMBRE 2026" (x visite mediche) + "Ore al mattino il giorno dell'equipe dalle 10,00" | 3 dated single-day VACATION rows (slots 2-7, narrowed from 0-7) + 2 new recurring VACATION rows (Monday and Tuesday, slots 0-1) | **Updated TASK-374 (2026-07-17):** the 3 dated rows are narrowed from slots 0-7 to 2-7 solely to stop them overlapping the 2 new recurring slots-0-1 rows (same calendar coverage on those 3 Mondays: 0-1 recurring + 2-7 dated = 0-7, unchanged). The equipe-day rule — previously NOT-YET-MODELED because the source gives Monday and Tuesday as alternatives — is now golden-derived by blocking slots 0-1 on BOTH days, sidestepping the ambiguity entirely; see below (Q13) |
| T16 | "Libera 2 mattine dalle 11,00 per adulti" | Tuesday VACATION slots 0-2 | **New TASK-374 (2026-07-17):** golden-derived, Tuesday only — Wednesday was measured and dropped (see "Resolved via golden-derivation" below, Q12) |
| T24 | "Ore al mattino il giorno dell'equipe dalle 8,00 / Terrei equipe martedì" | Tuesday PREFERENCE slots 0-5 | Weak (soft-avoid) transcription — T24 wants Tuesday morning kept for the equipe meeting, but this reads as an organizational preference, not a hard rule; see below for the alternative considered |

### Why non-overlapping rules per teacher

H5 penalizes ONE_HARD per `(Lesson, TeacherAvailability)` pair that overlaps
and has `type == VACATION`. If two VACATION rows for the same teacher/day both
covered the same hour (e.g. a "13:00-16:30 every weekday" rule stacked with a
separate "Friday full day" rule), a Friday lesson at 13:00 would match BOTH
rows and get penalized **twice** for one real violation. Every multi-rule
teacher above is built so the day-specific full-day rule (Friday for
T04/T05) excludes the always-partial rule's days (Mon-Thu only) —
the two never apply to the same date.

This is no longer just a modeling convention: `RecurringAvailabilityRule.overlaps`
(code review, TASK-345) rejects any overlapping rule for the same teacher
outright — at the REST API (400) and at seed load (fails startup) — so a
future edit that reintroduced an overlap for any of the teachers above would
be caught immediately rather than silently double-counting H5/S3.

### Lunch-spanning ranges are one row, not two

A rule spanning slots 5-7 (13:00-16:30, e.g. T04 Mon-Thu) expands into
a **single** `TeacherAvailability` row 13:00-16:30, not two rows split around
the 14:00-14:30 lunch break. This is safe because H5/S3 only ever match a real
`Lesson.timeslot` via `Joiners.overlapping()`, and no `Timeslot` is ever
generated inside 14:00-14:30 (`TimeslotSequence.SLOT_START_TIMES` has no entry
there) — a wider window that happens to include the lunch gap cannot produce a
different match set than two tight rows would, and it's one row instead of
two. See `AvailabilityExpansionService`'s class Javadoc for the same argument
in code.

### T24: PREFERENCE vs NOT-YET-MODELED (design choice)

"Terrei equipe martedì" ("I'd keep the equipe meeting on Tuesday") reads as
T24's own organizational proposal, not a confirmed school policy — unlike
T04/T05/T11, which are stated as plain facts about when the
teacher works. Transcribing it as a soft `PREFERENCE` (S3, 1 soft point per
violation) captures the intent (nudge the solver away from Tuesday 8-14)
without hard-blocking a schedule if it turns out equipe day changes. If the
school confirms this is a strict must-not-teach rule, flip the row's `type`
to `VACATION` in `availability_rules.csv` (and its claudedocs counterpart).

## Re-runnable golden-derivation check (TASK-374 code review E5)

The "Golden measurement (mechanical)" counts cited below for T10, T12,
and T16 were originally computed once, by hand, during the 2026-07-17
session. `scripts/analyze/derive-golden-availability-checks.py` re-derives
the same counts from `lessons.csv` and checks them against whatever is
currently seeded in `availability_rules.csv`, so the numbers can be
re-verified any time either file changes instead of being trusted as a
one-off calculation. Run it with:

```
python3 scripts/analyze/derive-golden-availability-checks.py
```

Output (run 2026-07-17, current seed - matches the per-teacher counts in
sections 4-6 below exactly):

```
====================================================================================================
T10 - Thursday slot distribution
====================================================================================================
  T10 total golden lessons: 230
  T10 Thursday golden lessons: 20
    per-slot breakdown: slot0=0, slot1=0, slot2=0, slot3=0, slot4=20, slot5=0, slot6=0, slot7=0
  Currently seeded T10/THURSDAY recurring VACATION ranges: [(0, 3)]
  T10 THURSDAY slots 0-3: 0/230 golden lessons (0.00% of all T10 lessons; 20 lessons total on THURSDAY) -> PASS (0 violations)
    per-slot breakdown: slot0=0, slot1=0, slot2=0, slot3=0

====================================================================================================
T12 - Monday/Tuesday slots 0-1 usage
====================================================================================================
  T12 total golden lessons: 125
  Currently seeded T12/MONDAY recurring VACATION ranges: [(0, 1)]
  T12 MONDAY slots 0-1: 0/125 golden lessons (0.00% of all T12 lessons; 24 lessons total on MONDAY) -> PASS (0 violations)
    per-slot breakdown: slot0=0, slot1=0
  Currently seeded T12/TUESDAY recurring VACATION ranges: [(0, 1)]
  T12 TUESDAY slots 0-1: 0/125 golden lessons (0.00% of all T12 lessons; 97 lessons total on TUESDAY) -> PASS (0 violations)
    per-slot breakdown: slot0=0, slot1=0

====================================================================================================
T16 - per-day slots 0-2 usage (all weekdays, not just the seeded one)
====================================================================================================
  T16 total golden lessons: 495
  (measured on all weekdays for comparison, not just the seeded Tuesday rule)
  T16 MONDAY slots 0-2: 98/495 golden lessons (19.80% of all T16 lessons; 196 lessons total on MONDAY) -> FAIL (98 violations, 19.80%)
    per-slot breakdown: slot0=32, slot1=33, slot2=33
  T16 TUESDAY slots 0-2: 0/495 golden lessons (0.00% of all T16 lessons; 1 lessons total on TUESDAY) -> PASS (0 violations)
    per-slot breakdown: slot0=0, slot1=0, slot2=0
  T16 WEDNESDAY slots 0-2: 34/495 golden lessons (6.87% of all T16 lessons; 36 lessons total on WEDNESDAY) -> FAIL (34 violations, 6.87%)
    per-slot breakdown: slot0=12, slot1=11, slot2=11
  T16 THURSDAY slots 0-2: 99/495 golden lessons (20.00% of all T16 lessons; 111 lessons total on THURSDAY) -> FAIL (99 violations, 20.00%)
    per-slot breakdown: slot0=33, slot1=33, slot2=33
  T16 FRIDAY slots 0-2: 99/495 golden lessons (20.00% of all T16 lessons; 151 lessons total on FRIDAY) -> FAIL (99 violations, 20.00%)
    per-slot breakdown: slot0=33, slot1=33, slot2=33
  Currently seeded T16/TUESDAY recurring VACATION ranges: [(0, 2)]

====================================================================================================
Summary vs. the >2% weakening threshold (TASK-374 WHAT #2)
====================================================================================================
  A seeded rule PASSes if it causes 0 golden violations. A rule that was
  deliberately NOT seeded (e.g. T16/Wednesday) is expected to show a
  nonzero, >2% count above - that is what justified dropping it, not a bug.
```

**TASK-368 addendum (2026-07-17):** the script now also covers the
T04/T05 static-rule revisions and the M24/M25 weekly constraints
(sections 1-2 below cite these numbers). Output:

```
====================================================================================================
TASK-368: T04/T05 static-rule revisions vs. the golden
====================================================================================================
  T04 total golden lessons: 485
  T04 Mon-Thu slot5 (13:00-14:00, newly UNBLOCKED by TASK-368): 0/485 -> loosening only, cannot create a new violation
  T04 Mon-Thu slot6-7 (still blocked, unchanged): 0/485 (0.00%) -> PASS
  T05 total golden lessons: 499
  T05 Tuesday slot6-7 (14:30-16:30, newly BLOCKED by TASK-368, drops the old Tue-afternoon-available asymmetry): 0/499 (0.00%) -> PASS
  T05 Mon/Wed/Thu slot6-7 (already blocked, unchanged): 0/499

====================================================================================================
TASK-368: M24 (T11, at most 1 early-start day/week) - Python cross-check
of the Java constraint stream in SchoolScheduleConstraintProvider
====================================================================================================
  T11 total golden lessons: 426
  T11 slot-0 (08:00) golden lessons: 52 (NOTE: this contradicts the TASK-368 task brief's claim of 'ZERO slot-0 days' - see availability-not-yet-modeled.md for the correction)
  M24 cross-check (cap=1/week): 15 weeks exceed the cap, total shortfall 16 (must match the Java GoldenSolutionRegressionTest M24 medium score/match count exactly)

====================================================================================================
TASK-368: M25 (T05, required free days/week) - Python cross-check
of the Java constraint stream in SchoolScheduleConstraintProvider
====================================================================================================
  M25 cross-check (requiredFreeDaysPerWeek=1): 0 weeks fall short, total shortfall 0 (the docx's literal value - shown for comparison, see Teacher.requiredFreeDaysPerWeek javadoc for why 2 is seeded instead)
  M25 cross-check (requiredFreeDaysPerWeek=2): 27 weeks fall short, total shortfall 27 <- Teacher.requiredFreeDaysPerWeek's actual seeded value
```

## NOT-YET-MODELED

Every clause below is a real constraint from the sheet that the current
{day-of-week, slot-range, effective-date-window} rule shape cannot express.
Nothing here is silently dropped — each has a proposed modeling sketch.

### 1. T05 — "at most one light day" (OLD reading) — MODELED as M25, TASK-368 (2026-07-17)

**Old text (original sheet reading):** "No lezione in uno di questi giorni AL
MASSIMO UN'ORA" — of the three days T05 works 8:00-14:00, exactly one
(unspecified which) should be a "light day" with at most 1 hour of lessons.

**Superseded by the 2026-07-17 docx** (`vincoli-in-parole.md`): "Un giorno a
scelta deve essere lasciato senza ore per poter fare altro" — the requirement
is now a fully FREE day (0 lessons), not a "≤1h" light day, among a solver's
choice of the working week.

**Why the old {day-of-week, slot-range} rule shape couldn't express either
reading:** `RecurringAvailabilityRule` is a fixed template; it cannot say
"pick one of these days, whichever the solver finds convenient, and require
it to be free." This is a *weekly selection* constraint, not a fixed
calendar fact.

**Modeled as:** `Teacher.requiredFreeDaysPerWeek` (nullable int, T05=2) +
constraint `M25 - Required free day per week` in
`SchoolScheduleConstraintProvider` — groups Lessons by (teacher, ISO week),
counts distinct days used, and penalizes when `5 - usedDays < requiredFreeDaysPerWeek`.
Medium tier (a scheduling nicety, not a safety rule — hard would over-constrain
if the school's course load doesn't actually allow a free day some weeks).

**Why T05 is seeded 2, not the docx's literal 1:** his Friday is already a
hard full-day `RecurringAvailabilityRule` VACATION block (H5), so Friday is
unconditionally a zero-lesson day for him regardless of anything M25 does.
Counting Mon-Fri with a requirement of 1 would therefore always be satisfied
by Friday alone and never actually constrain Mon-Thu — **empirically
confirmed against the golden**: `derive-golden-availability-checks.py`'s
M25 cross-check shows 0 weeks fall short at requiredFreeDaysPerWeek=1 (fully
vacuous) vs. 27 weeks at requiredFreeDaysPerWeek=2 (the seeded value; matches
`GoldenSolutionRegressionTest`'s M24/M25 assertions exactly). See
`Teacher.requiredFreeDaysPerWeek`'s javadoc for the same reasoning in code.

**Golden measurement:** informational only (M25 is medium, not zero-tolerance)
— 27 of the weeks T05 has any golden lesson fall short of 2 free Mon-Fri
days, total shortfall 27 (all off-by-exactly-one weeks). See
`GoldenSolutionRegressionTest.goldenSolutionMatchesCalibratedConstraintExpectations`.

### 2. T11 — "one day from 8:00" exception — MODELED as M24, TASK-368 (2026-07-17)

**Old text (original sheet reading):** "9-14 sempre e un giorno alle 8.00" —
on one unspecified weekday, T11 is also available from 8:00 (not just
9:00-14:00). Previously NOT-YET-MODELED because which day was ambiguous.

**Resolved by the 2026-07-17 docx** (`vincoli-in-parole.md`): "Un giorno a
scelta può iniziare alle 08:00" — the day is explicitly the SOLVER's choice,
not a fixed-but-unknown weekday. This removes the original ambiguity entirely
(there was never a "right" weekday to guess) and reframes it as the same
*weekly selection* shape as T05's clause above — a fixed
{day-of-week, slot-range} rule still can't express "any one day, solver's
choice."

**Modeled as:** `Teacher.maxEarlyStartDaysPerWeek` (nullable int, T11=1) +
constraint `M24 - At most one early start day per week`. The daily
`T11,<day>,0,0,VACATION` rows were removed from `availability_rules.csv`
(slot 0 unblocked statically) and replaced by M24 capping how many distinct
days/week he actually uses it.

**Golden measurement — IMPORTANT CORRECTION to the TASK-368 task brief:** the
brief that spawned this work claimed "T11's golden has ZERO slot-0 days."
This is **factually wrong** — mechanically re-derived from `lessons.csv`
(`derive-golden-availability-checks.py`), T11 has **52** slot-0 (08:00)
golden lessons, overwhelmingly recurring Tuesdays plus one Friday
(2026-09-18). Grouped by ISO week, 15 distinct weeks have him at slot 0 on 2+
days, for a total M24 shortfall of 16 (matches
`GoldenSolutionRegressionTest`'s M24 assertion of medium score -16 / 15
matches exactly, cross-checked via two independent computations - the Java
constraint stream and a separate Python isocalendar()-based re-derivation).
Not zero-tolerance: M24 caps the SOLVER's own future placements, it does not
retroactively re-litigate real historical data.

### 3. T16 — "max 3 consecutive hours in R16 (not 6)"

**Text:** "3 ore consecutive in R16 NON 6" — a block-size cap on a
specific subject/room (R16), independent of teacher availability.

**Why not expressible:** this isn't an availability constraint at all — it's
a per-subject consecutive-hours cap, unrelated to `TeacherAvailability`/H5.

**Proposed sketch:** a new hard or medium constraint (working name
`H-SUBJECTCAP` / `M-SUBJECTCAP`) that, for a subject flagged with a
`maxConsecutiveHours` field (new nullable int on `Course`, defaulting to
unbounded), penalizes any run of more than that many consecutive same-subject
lessons for the same teacher+room+day. Mirrors the "consecutive" detection
`TimeslotSequence.isAdjacent` already uses for H15/H16, just inverted (cap
instead of require).

### 4. T16 — "2 mornings free from 11:00 (per adulti)" — RESOLVED (partially) via golden-derivation, TASK-374 (2026-07-17)

**Text:** "Libera 2 mattine dalle 11,00 per adulti" — two mornings a week
T16 is unavailable to us before 11:00 (busy with an adult-education
course elsewhere), then available from 11:00 on.

**Why not expressible (original):** which 2 weekdays is unspecified.

**Golden measurement (mechanical, `claudedocs/spalmone-2026-27/data/lessons.csv`):**
T16 has 495 golden lessons total. Tuesday is nearly empty — exactly 1
lesson all year, at slot 3 (11:00-12:00, G05 Recuperi ed Approfondimenti)
— outside slots 0-2, so blocking Tuesday slots 0-2 causes **0 golden
violations (0.0%)**. Wednesday is a different story: 34 golden lessons fall
in slots 0-2 (12 in slot 0, 11 in slot 1, 11 in slot 2) out of 495 —
**6.87%**, well over the 2% weakening threshold, and no single slot within
0-2 drops below 2% on its own (the smallest, slot 2, is still 11/495 =
2.22%). Wednesday morning is therefore NOT "scarico" in the golden — it's
one of T16's regular teaching mornings.

**Rule seeded:** `T16,TUESDAY,0,2,VACATION` only. Wednesday is
deliberately dropped from this pass (weakened per the >2% rule — the golden
is valid by definition) rather than guessed at a narrower slot range, since
no subset of slots 0-2 clears the threshold.

**Open question (narrowed):** Tuesday is confirmed as one of the "2
mornings"; the second (previously assumed Wednesday) is still open — which
other weekday, if any, is T16's second free morning? → domande-per-la-scuola.md Q12 (archived as PARTIALLY DERIVED).

### 5. T12 — equipe-day recurring rule — RESOLVED via golden-derivation, TASK-374 (2026-07-17)

**Text:** "Ore al mattino il giorno dell'equipe dalle 10,00 ... Il martedì ...
Oppure il lunedi dalle 10.00" — mornings on the equipe day are free from
10:00, but the equipe day itself is given as EITHER Tuesday OR (alternatively)
Monday — the sheet doesn't resolve which.

**Why not expressible (original):** the day is genuinely ambiguous in the
source (two candidate days offered as alternatives), so seeding either guess
risks being wrong. Only the three dated absences (unambiguous) were
transcribed.

**Golden measurement (mechanical):** T12 has 125 golden lessons total.
Monday lessons only ever land in slots 2-5 (6 each); Tuesday lessons only
ever land in slots 2-5 as well (24, 24, 25, 24). Neither day has a single
golden lesson in slots 0-1 — blocking slots 0-1 on **both** days causes
**0 golden violations (0.0%)** either way. Since the ambiguity is exactly
"which of these two days," and the golden confirms slots 0-1 are free on
BOTH candidates, blocking both sidesteps the need to pick one — the rule is
correct regardless of which day is the real equipe day.

**Rule seeded:** `T12,MONDAY,0,1,VACATION` + `T12,TUESDAY,0,1,VACATION`
(both recurring, no date bounds). The 3 existing dated full-day Monday rows
(visite mediche) were narrowed from slots 0-7 to 2-7 so they don't overlap
these two new rows for the same teacher/day — `RecurringAvailabilityRule.overlaps`
is enforced at seed load (see class Javadoc) and would otherwise abort
startup. Combined coverage on those 3 specific Mondays is unchanged (0-1
recurring + 2-7 dated = 0-7).

**Open question:** none remaining for the equipe day itself (resolved above
by blocking both candidates); if the school later confirms one specific day,
the other day's slots-0-1 rule can simply be deleted. → domande-per-la-scuola.md Q13 (archived as DERIVED, pending referente confirmation).

### 6. T10 — Thursday range ambiguity — RESOLVED via golden-derivation, TASK-374 (2026-07-17)

**Text:** "Libero il Giovedì mattina per LES e G14 9,00-17,00" literally
combines "free Thursday MORNING" with a "9:00-17,00" range that extends well
past both the morning and past the school day's own last slot (16:30).

**Why flagged rather than resolved (original):** could mean (a) T10
teaches elsewhere Thursday mornings only, i.e. unavailable to us roughly
9:00-13:00, or (b) T10 is unavailable ALL Thursday (9:00-17:00 spans the
entire teaching day). Per the task's own instruction, transcribed as the
safer full-day Thursday `VACATION` (option b) rather than guessing the
narrower morning-only range, and flagged here.

**Golden measurement (mechanical):** T10 has 230 golden lessons total,
20 of them on Thursday — and every single one is at slot 4 (12:00-13:00).
Zero Thursday lessons fall in slots 0-3. The previous full-day (0-7) block
was therefore over-blocking a real, used slot: narrowing to slots 0-3
(8:00-12:00) is a strict **loosening** that causes **0 golden violations
(0.0%)** and unblocks the Thursday 12:00-13:00 slot the golden actually
uses 20x/year.

**Rule seeded:** `T10,THURSDAY,0,3,VACATION` (replaces the prior
`0,7` row in place — same teacher/day, no overlap risk since it's the only
T10 rule).

**Open question:** whether slot 4 (12:00-13:00) specifically, or the whole
afternoon boundary, matches the school's actual "9,00-17,00" reading is
still unconfirmed — the golden only tells us slot 4 is used, not why the
0-7 upper bound in the source text was written. → domande-per-la-scuola.md Q11 (archived as DERIVED, pending referente confirmation).

### 7. T11 — post-stage curriculum hours (out of scope for availability)

**Text:** "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum
(ANCHE 2 CONSECUTIVE)" — 6 hours of T11's subject should be scheduled
AFTER these three groups' stage period ends, for curriculum reasons (2
consecutive hours allowed).

**Why out of scope:** this is a course-hours-timing requirement (same family
as M5 "Informatica after stage"), not a teacher-availability clause — it
constrains WHEN specific groups' hours land relative to stage, not when
T11 himself can teach. Left for a future task extending M5-style modeling
to T11's subject/these 3 groups; not silently dropped, just out of this
task's scope (teacher availability).

### 8. T03 — "free mornings when Laboratorio Termo is on" (schedule-dependent)

**Text:** "Libero le mattine in cui c'è laboratorio termo senza eccezioni" —
T03 is free (unavailable to other courses) every morning some OTHER
course ("Laboratorio Termoidraulico") is scheduled.

**Why not expressible:** this depends on where a *different* course ends up
being scheduled — it's not a fixed calendar fact, so no
`RecurringAvailabilityRule` (day-of-week template) can express it; it would
need to be evaluated against the actual solved schedule.

**Proposed sketch:** a new hard/medium constraint joining T03's `Lesson`
rows with `Laboratorio Termoidraulico` `Lesson` rows on the same morning
(same date, slots 0-4), penalizing any T03 lesson that overlaps. Not
attempted here — needs the school to confirm T03 isn't ALSO the Lab
Termo teacher/doppio (in which case this is redundant with H1/H-D1).

**Additional un-transcribed annotations on the same row** ("SICUREZZA — ore
con le 3° (+2° OIT) prima di stage, 2 gg di sicurezza 4 ore con le 1°",
"LABORATORIO TERMO — 2° oit e 3° oit ??", "Lunedi e Venerdì 1° OIT") read as
scheduling/rotation notes for OTHER courses (safety-training days,
LabRotation-style patterns), not T03's own availability. Flagged, not
modeled — likely belongs to a `LabRotation`-seeding task, not TASK-345.

### 9. T13 — "Comportamento sociale lab due" — RESOLVED (no rule needed), TASK-374 (2026-07-17)

**Text:** unclear whether this is an availability note, a subject/room note,
or something else entirely (no day, time, or type indicator on the row).

**Resolution:** `incarichi.csv` has `T13,Comportamento Sociale,G14,20.0`
— T13's real course IS named "Comportamento Sociale" (G14, 20h).
"Lab due" reads as a room/section reference tied to that course (e.g. "lab
2"), not a day/time availability constraint — there's no day-of-week, slot,
or VACATION/PREFERENCE indicator anywhere in the row for a
`RecurringAvailabilityRule` to express. No rule is modeled or needed.

**Open question (narrowed):** whether "lab due" denotes a specific room
T13's Comportamento Sociale course must use is still open, but that's
a room-affinity question (S11/H-M2 territory), not availability — out of
this task's scope. → domande-per-la-scuola.md Q14 (archived as DERIVED,
pending referente confirmation).

### Rows with nothing to transcribe

Every other named row in the sheet (T06, T21, T08, T09,
T02, T25, T20, T18, T01, T19\*, T26,
T14, "??????" [tracked separately as Q5], T15, T17,
T07, T23, Barbiere, and the pseudo-rows RA/Stage/Esame) had empty
constraint cells in the raw sheet dump — confirmed by direct inspection, not
just the analysis.md summary. Nothing to transcribe or flag for these.

\* T19 isn't a row in Vincoli Docenti at all; listed here only because it's
a real teacher name that could be confused with a missing row.
