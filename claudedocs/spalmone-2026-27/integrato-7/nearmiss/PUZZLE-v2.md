# SCHEDULING PUZZLE v2 (self-contained, complete data)

A school timetable has ONE hard-rule violation. Find a sequence of moves that
eliminates it without creating any new hard violation. Medium costs are
secondary: minimize them among hard-legal solutions, but never trade a hard
violation for medium savings.

## RULES (hard unless marked MEDIUM)

R1. One teacher, one lesson at a time (school-wide).
R2. A teacher never teaches inside their UNAVAILABLE windows (listed below).
R3. A teacher teaches at most 6 hours per day.
R4. A class's day must be CONTIGUOUS: no empty hour between two lessons of
    the same class on the same day (a day may end early; it may not have
    internal gaps). CURRENT VIOLATION: class "G04" on Friday 20/11 has an
    internal gap at hour 11 (lessons at 8,9,10,12,13).
R5. A class's day starts at its first slot (no leading gap before the first
    lesson; all days below already comply).
R6. Daily floor (non-Stage hours): every class day has at least 4 hours
    (G04, G11, G13: 4h minimum).
R7. Quota floor: the non-Stage hours of a class day must be >= the day's
    declared quota (table Q). Going OVER quota is legal but costs 100 per
    extra hour (MEDIUM).
R8. Quota-bound subjects: for class G04 the subjects "Lavorazioni
    meccaniche", "Cad", "Recuperi ed Approfondimenti" may sit ONLY on days
    whose lab-quota > 0 for G04. Same rule for G13 with "Lavorazioni
    meccaniche", "Tecniche di saldatura", "Cad".
R9. Rooms: one lesson per room per hour. Subject-room tags: "Lavorazioni
    meccaniche" needs the room R15 (the only meccanica room);
    "Tecniche di saldatura" needs R17; "Informatica" and "Cad" need an
    informatica room (R12 or R13). Untagged subjects may use
    any room. Putting an untagged subject in a tagged lab costs 1000 (MEDIUM).
R10. Moves: move any lesson to any legal empty cell; swap any two lessons;
    chains of moves are allowed. Each moved lesson must satisfy R1-R9 in its
    final position.

## MEDIUM cost model (secondary, for tie-breaks)

- Quota surplus: 100 per hour over the day's quota (R7).
- Same-day same-subject split (non-adjacent repeats of a subject in a class
  day): 20 per gap-minute between the repeats.
- Untagged subject in a tagged lab: 1000 per lesson (R9).
- Teacher second theory hour of the same subject same day: small penalty.

## STATE (the involved slice; every other class day is untouched and legal)

### Class G04, week of 16-20 November 2026

Mon 16: @8 Lingua Italiana (T14, R05); @9 Lingua Italiana
  (T14, R05); @10 Tecnologia Meccanica (T10, R01);
  @11 Informatica (T06, R13); @12 Disegno (T24, R01).
Tue 17: @8 Matematica (T05); @9 Matematica (T05); @10 Disegno (T24);
  @11 Economia e Diritto (T23); @12 Storia (T23). All in R04.
Wed 18: @8 Lingua Inglese (T21, R10); @9 Lingua Inglese
  (T21, R10); @10 Lavorazioni meccaniche (T15,
  R15); @11 Cad (T15, R12); @12 Recuperi ed
  Approfondimenti (T15, R10); @13 Recuperi ed Approfondimenti
  (T15, R10).
Thu 19: @8 Lavorazioni meccaniche (T15, R15); @9
  Lavorazioni meccaniche (T15, R15); @10 Competenze di
  cittadinanza (T11, R06); @11 Educazione Civica (T11, R06);
  @12 Lavorazioni meccaniche (T15, R15); @13 Lavorazioni
  meccaniche (T15, R15).
Fri 20: @8 Scienze (T25, R03); @9 Scienze (T25, R03); @10
  Lingua Italiana (T14, R04); @11 EMPTY (the violation); @12
  Orientamento (T23, R04); @13 Informatica (T06, R13).

### Class G11, Friday 20 November

@8 Disegno Edile ed Impiantistico (T12, R04); @9 Disegno Edile ed
Impiantistico (T12, R04); @10 Informatica (T06, R12);
@11 Personalizzazione (T11, R10).

### Class G13, Friday 20 November

@8 Lavorazioni meccaniche (T10, R15); @9 Lavorazioni
meccaniche (T10, R15); @10 Tecniche di saldatura (T15,
R17); @11 Lavorazioni meccaniche (T10, R15); @12
Lavorazioni meccaniche (T10, R15).

### Teacher engagements on Friday 20 November, hours 8-13 (for R1)

T25: G04 @8,@9. T14: G04 @10 (plus other classes, busy @11).
T23: G03 @10, G06 @11, G04 @12. T06: G10 @8, G11
@10, G03 @11, G03 @12, G04 @13. T10: G13 @8,@9,@11,@12.
T15: G08 @8,@9 (Controllo numerico, R15), G13 @10
(R17). T11: G11 @11. T03, T21, T08, T17,
T26: busy @11 in other classes. T12: G11 @8,@9.
T05: NO lessons Friday. T24: NO lessons Friday.

### Teacher unavailability windows (R2)

T05: FULL DAY Friday 20/11 (08:00-16:30) and roughly one full day per week
day across the year; on 20/11 he is unavailable all day.
T24: unavailable ALL Wednesdays, Thursdays, Fridays (full day).
T11: every day unavailable 14:30-16:30 ONLY (mornings free).
All other teachers listed: no unavailability.

### Quota table Q (non-Stage hours per day; aula, lab)

G04: Mon 16 = 6/0, Tue 17 = 6/0, Wed 18 = 2/4, Thu 19 = 2/4, Fri 20 = 5/0.
G11: Fri 20 = 4/0.
G13: Fri 20 = (its 5 lessons are at quota; treat as 5).

### Rooms

R01, R03, R04, R05, R06, R07, R08, R09, R01
adulti, R10: untagged. R12, R13: informatica.
R15: meccanica. R17: saldatura. R11:
acconciatura. R14: termoidraulica. R16: moda.
All teachers' daily cap: 6 hours. No lesson in the given slice is pinned.

## GOAL

End state: the G04 Friday gap is gone, ZERO new hard violations (check
every rule for every touched class day and teacher), medium costs reported.
OUTPUT: the ordered move list (lesson, from, to), then the final state of
every touched day, then the hard-check and the medium cost delta. If NO
legal solution exists, answer NO SOLUTION and prove it (which rule blocks
every candidate family).
