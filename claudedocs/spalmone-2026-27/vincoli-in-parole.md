# "Vincoli Spalmone in parole" — school's plain-language constraints (2026-07-17)

**Source:** `/home/user/Downloads/Vincoli Spalmone in parole.docx`, shared by the school on 2026-07-17 in response to our question sheet. The school also says the **lab/stage assignment sheets are being revised** — the calendario TUTTI data imported by TASK-364 is provisional and will be re-imported when the updated sheets arrive.

## Verbatim content (translated structure, original Italian text)

### Desiderata
> Un bel pannello dove inserire in maniera semplice le ore pensate dall'utente che diventano intoccabili. E le ore che devono rimanere vuote (le ore non compilabili). Poi la macchina pensa al resto.

### Vincoli per docente
- **T11**: 09:00–14:00 dal lunedì al venerdì. Un giorno a scelta può iniziare alle 08:00.
- **T05**: 08:00–14:00 dal lunedì al giovedì. Un giorno a scelta deve essere lasciato senza ore per poter fare altro.
- **T04**: 08:00–14:00 dal lunedì al giovedì.
- **T03**: tutte le ore della materia "Sicurezza" prima dell'inizio dello stage della classe.
- **T20**: iniziare subito Merceologia e Arredi Tessili; Tecnologia tessile e Storia della moda devono iniziare più tardi; quando si vuole Figurino, Consulenza d'immagine e Sviluppo Sostenibile.

### Vincoli per classe
- **G12**: professionalizzanti solo il venerdì, oppure giovedì/mercoledì alla prima o all'ultima ora, oppure alle prime due o alle ultime due ore. Se la 2ª ora del giovedì è professionalizzante lo deve essere anche la 1ª; se la 5ª lo è, anche la 6ª. Stesso schema per il mercoledì.

### Vincoli generali
1. Giornata di 8 ore: 8:00–14:00 e 14:30–16:30. Normalmente la lezione è solo fino alle 14; lo **stage** può arrivare alle 16:30.
2. Non 2 ore adiacenti della stessa materia, **tranne Informatica, Competenze Digitali o Disegno**.
3. Lo stesso docente (salvo il caso sopra) non può avere due ore attaccate, anche con materie diverse. *(Se possibile.)*
4. Non più di 6 ore di lezione al giorno per docente.
5. Lo stesso docente non può essere in due classi a parità di giorno/ora.
6. Le ore segnate come vincolo non si spostano (stage, laboratori, docenti vincolati).
7. Si riempiono solo le caselle segnate DISPONIBILI.
8. Il docente deve fare **esattamente** le ore assegnate per materia.
9. Possibilmente almeno 1 ora/settimana per materia (esclusi periodi di stage) per continuità didattica.
10. Il calendario di apertura può variare classe per classe.
11. **Lo stage può essere fatto anche nei giorni di chiusura della scuola.**

## Clause-by-clause mapping to our model

| Clause | Status | Where |
|---|---|---|
| Desiderata panel (pin + block cells, machine does the rest) | ✅ direction validated | pins = golden/calendario import + TASK-357 manual placement; blocked cells = GroupCalendarRule editor (TASK-358, done) |
| T11 9–14, one day MAY start 08 | 🔧 revision | current rules block slot 0 every day (over-constrained); needs a choice-day disjunction (TASK-368) |
| T05 8–14 Mon–Thu + one day MUST be free | 🔧 revision | current rules encode the OLD sheet (Tue afternoon available, Fri free); docx drops Tue afternoons and adds a solver-chosen free day (TASK-368) |
| T04 8–14 Mon–Thu | 🔧 revision | current rule blocks slot 5 (13–14) per the old "8,00-13,00" note — docx extends to 14 (TASK-368) |
| T03: Sicurezza before class stage start | 🆕 new constraint | subject-before-stage (TASK-369); matches the old free-text note we had parked |
| T20 course ordering (early/late/any) | 🆕 new constraint | soft per-subject start-period preferences (TASK-369) |
| G12 professionalizzanti edge-of-day on Wed/Thu | 🆕 new constraint | edge-contiguity rule (TASK-370); refines the Thu+Fri mask from TASK-343 (docx allows Wed too) |
| 8h day, lunch 14–14:30 | ✅ modeled | TimeslotSequence (TASK-341) |
| Lessons normally until 14; afternoons for stage | ⚠️ CONTRADICTION | golden has 575 real afternoon lessons (9 groups) — Q17 |
| No adjacent same-subject hours except Informatica/Competenze Digitali/Disegno | ⚠️ CONTRADICTION + key input | combined with H15 (same-day repeats must be adjacent) this means MAX 1H/DAY PER SUBJECT outside the whitelist — exactly the intent of the undocumented M21 the golden audit flagged (861 golden violations, -251,700 medium). The docx CONFIRMS M21's purpose and supplies its missing exception whitelist; the golden solution still violates it heavily (1,299 multi-hour blocks) — Q18. Feeds TASK-365 (M21 whitelist + weight) and TASK-349 (H15/H16/S4) |
| Teacher no adjacent hours at all (se possibile) | ⚠️ CONTRADICTION (soft) | golden violates constantly; soft at most — Q18, TASK-349 |
| Max 6h/day per teacher | ✅ H7 (per-teacher since TASK-348; T01=8 is a data-driven exception the docx omits) |
| No teacher in two classes same slot | ✅ H1 |
| Pinned hours immovable | ✅ Lesson.pinned + imports |
| Fill only AVAILABLE cells | ✅ GroupCalendarRule + H20 (TASK-342) |
| Exactly the assigned hours per subject | ✅ H0/lesson generation + reconciliation dashboard (TASK-353) |
| ≥1h/week per subject outside stage (possibilmente) | 🔍 verify | S9 "dilute hours over year" exists — check semantics match (TASK-369) |
| Calendar varies per class | ✅ GroupCalendarRule |
| Stage allowed on school closure days | ⚠️ modeling gap | our Timeslot set excludes closures entirely; golden has 0 closure-day lessons so no 2026/27 impact — documented, revisit only if the revised stage sheets use closure days (note in TASK-364) |

## Contradictions to clarify with the school (added to the question sheet)
- Q17: afternoons "stage only" vs 575 real afternoon lessons in their own spalmone.
- Q18: the no-adjacent-hours rules (subject whitelist + teacher-level) vs the golden solution's 1,299 multi-hour blocks — is the rule aspirational for the NEW solver-generated plan, tolerated-by-necessity, or does the whitelist have more exceptions (labs)?
