# TASK-474 mappa origine vincoli (spec di trascrizione, 2026-08-01)

> Spec autoritativa per il fill-in TASK-474. Per ogni vincolo registrato (.asConstraint):
> categoria onesta (A/B/C/D) + 3 forme della stringa Origine (javadoc IT, docs.json EN, docs.json IT).
> La trascrizione e' meccanica da questo file. Riferimento formato: spike H7 (riga 122 en/docs.json).
> Verbatim riprodotti TALE QUALE dall'extract (typos inclusi, es. "CONSECUTIVVE", "Govedì").

## Categorie (oneste)
- **A** = quote verbatim nel workbook della scuola (Spalmone xlsx, extract con tab+riga).
- **B** = derivato dai fogli calendario per-gruppo (DailyActivityQuota / GroupCalendarRule, seed calendario CSV).
- **C** = pratica del golden (calibrata/misurata sul golden della scuola, con doc di misura).
- **D** = strutturale (nessun documento scuola; proprieta' base di scheduling).

## Formato uniforme (3 forme)
- **Javadoc** (IT, sul metodo builder del constraint provider):
  `<p><b>Origine.</b> <testo IT con quote verbatim>.`
- **docs.json EN** (campo `origin` nel blocco del vincolo): cornice EN + quote verbatim.
- **docs.json IT** (campo `origin`): cornice IT + quote verbatim.
- Il quote verbatim e' INVARIATO nelle 3 forme; solo la cornice e' localizzata.
- Cornice EN: `... (Spalmone constraints workbook, tab "<Tab>", row <N>).`
- Cornice IT: `... (workbook vincoli Spalmone, tab "<Tab>", riga <N>).`
- In JSON il quote va racchiuso tra virgolette ESCAPATE `\"...\"` (come H7 riga 122).

## Fonti primarie
- Workbook: `Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx`. Estratto verbatim:
  `claudedocs/spalmone-2026-27/vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` (tab e riga sotto).
  Tab: "Vincoli Classi", "Vincoli Docenti", "Vincoli DA ANALIZZARE".
- Calendario: `Copy of Calendario scolastico 2026_2027.xlsx`, tab per-gruppo
  (righe Totali Ore/STAGE/Lab) -> `seed/calendario_class_days.csv`, `seed/calendario_fc.csv`.
- Golden: `seed/lessons.csv` + doc di misura (golden-cadence-reference.json, task*-results).

---

## HARD

### H0 - Unassigned lesson penalty (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una lezione non collocata e' la violazione piu' elementare (introdotto fin dalle origini del modello).`
- EN: `Structural constraint, no school document; an unplaced lesson is the most basic failure (foundational to the model).`
- IT: `Vincolo strutturale, nessun documento scuola; una lezione non collocata e' la violazione piu' elementare (fondamentale per il modello).`

### H1 - Teacher conflict (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un docente non puo' essere in due aule contemporaneamente (fisico, introdotto fin dalle origini).`
- EN: `Structural constraint, no school document; a teacher cannot be in two rooms at once (physical, foundational).`
- IT: `Vincolo strutturale, nessun documento scuola; un docente non puo' essere in due aule contemporaneamente (fisico, fondamentale).`

### H2 - Room conflict (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un'aula ospita una sola classe alla volta (fisico, introdotto fin dalle origini).`
- EN: `Structural constraint, no school document; one classroom holds one class at a time (physical, foundational).`
- IT: `Vincolo strutturale, nessun documento scuola; un'aula ospita una sola classe alla volta (fisico, fondamentale).`

### H3 - Student group conflict (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe non puo' essere in due luoghi contemporaneamente (fisico, introdotto fin dalle origini).`
- EN: `Structural constraint, no school document; a student group cannot be in two places at once (physical, foundational).`
- IT: `Vincolo strutturale, nessun documento scuola; una classe non puo' essere in due luoghi contemporaneamente (fisico, fondamentale).`

### H4 - Stage blackout (D, stage days dal calendario)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale dello stage (studenti fuori sede): le materie fondamentali non possono correre nei giorni di stage. I giorni di stage vengono dal calendario per-gruppo (seed/calendario_*.csv); introdotto TASK-381 (stage on the grid).`
- EN: `Structural property of stage (students off-site): fundamental subjects cannot run on stage days. Stage days come from the per-group calendar (seed/calendario_*.csv); introduced TASK-381 (stage on the grid).`
- IT: `Proprieta' strutturale dello stage (studenti fuori sede): le materie fondamentali non possono correre nei giorni di stage. I giorni di stage vengono dal calendario per-gruppo (seed/calendario_*.csv); introdotto TASK-381 (stage on the grid).`

### H4.3 - Professionalizzanti single-day during stage (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; derivato dalla struttura dello stage (una classe in stage tiene le professionalizzanti in un solo giorno), introdotto TASK-381.`
- EN: `Structural constraint, no school document; derived from stage structure (a staging class runs professional subjects on a single day), introduced TASK-381.`
- IT: `Vincolo strutturale, nessun documento scuola; derivato dalla struttura dello stage (una classe in stage tiene le professionalizzanti in un solo giorno), introdotto TASK-381.`

### H5 - Teacher unavailability (D; disponibilita' dati dal workbook)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale (un docente non e' schedulabile nei suoi slot non disponibili); i dati di disponibilita' vengono dal workbook vincoli Spalmone, tab "Vincoli Docenti" (es. riga 72: "T10 | Libero il Govedì  mattina per LES e G14  9,00-17,00"), seed come TeacherAvailability VACATION.`
- EN: `Structural constraint (a teacher cannot be scheduled in their unavailable slots); availability data comes from the Spalmone constraints workbook, tab "Vincoli Docenti" (e.g. row 72: "T10 | Libero il Govedì  mattina per LES e G14  9,00-17,00"), seeded as TeacherAvailability VACATION.`
- IT: `Vincolo strutturale (un docente non e' schedulabile nei suoi slot non disponibili); i dati di disponibilita' vengono dal workbook vincoli Spalmone, tab "Vincoli Docenti" (es. riga 72: "T10 | Libero il Govedì  mattina per LES e G14  9,00-17,00"), seed come TeacherAvailability VACATION.`

### H6 - School holiday (D; date dal DataGenerator)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale (nessuna lezione nei giorni di chiusura); le date vengono dal calendario scolastico (DataGenerator.CLOSURES_2026_27: Natale, Pasqua, Carnevale, festivita' nazionali), seed come SchoolHoliday.`
- EN: `Structural constraint (no lessons on closure days); dates come from the school calendar (DataGenerator.CLOSURES_2026_27: Christmas, Easter, Carnevale, national holidays), seeded as SchoolHoliday.`
- IT: `Vincolo strutturale (nessuna lezione nei giorni di chiusura); le date vengono dal calendario scolastico (DataGenerator.CLOSURES_2026_27: Natale, Pasqua, Carnevale, festivita' nazionali), seed come SchoolHoliday.`

### H7 - Max teacher daily hours (A, extract riga 108) [GIÀ in UI, aggiungere javadoc]
- quote: `Non piu' di 6 ore di lezione al giorno per ogni docente`
- javadoc: `<p><b>Origine.</b> "Non piu' di 6 ore di lezione al giorno per ogni docente" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", riga 108).`
- EN: `"Non piu' di 6 ore di lezione al giorno per ogni docente" (Spalmone constraints workbook, tab "Vincoli DA ANALIZZARE", row 108).`
- IT: `"Non piu' di 6 ore di lezione al giorno per ogni docente" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", riga 108).`
- NOTA: per-teacher override T01=8h e' pratica del golden (TASK-348), non dal workbook.

### H11 - Max student group daily hours (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe non supera il suo monte-ore giornaliero (campo group.maxHoursPerDay, derivato dal curriculum), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; a class does not exceed its daily hour cap (field group.maxHoursPerDay, from the curriculum), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; una classe non supera il suo monte-ore giornaliero (campo group.maxHoursPerDay, derivato dal curriculum), introdotto fin dalle origini.`

### H11-T28 - T28 up to 6h/day (A, extract riga 107) [non in UI; legacy watch-item]
- quote: `8 ore T28 (T18)`
- javadoc: `<p><b>Origine.</b> "8 ore T28 (T18)" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", riga 107). Eccezione strutturale legacy: T28 (id 14) fino a 6h/giorno sui suoi blocchi lab; raggiabilita' non confermata col seed 2026/27 (H7-a-6 blocca prima), watch-item.`
- EN: `"8 ore T28 (T18)" (Spalmone constraints workbook, tab "Vincoli DA ANALIZZARE", row 107). Legacy structural exception: T28 (id 14) up to 6h/day on lab blocks; reachability unconfirmed on the 2026/27 seed (H7-at-6 blocks first), watch-item.`
- IT: `"8 ore T28 (T18)" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", riga 107). Eccezione strutturale legacy: T28 (id 14) fino a 6h/giorno sui suoi blocchi lab; raggiabilita' non confermata col seed 2026/27 (H7-a-6 blocca prima), watch-item.`

### H14 - Student group start date (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; niente lezioni prima della data di inizio del gruppo (gruppi che partono dopo), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; no lessons before a group's official start date (groups that begin later), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; niente lezioni prima della data di inizio del gruppo (gruppi che partono dopo), introdotto fin dalle origini.`

### H17 - Vertically compact (D, esenzioni golden)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un orario non presenta buchi interni (compattezza verticale). L'esenzione break-level (gap scusato solo tra due lezioni pinnate) e' calibrata sul golden; introdotto TASK-227 (promosso da M13).`
- EN: `Structural constraint, no school document; a schedule has no internal gaps (vertical compactness). The break-level exemption (a gap is excused only between two pinned lessons) is calibrated on the golden; introduced TASK-227 (promoted from M13).`
- IT: `Vincolo strutturale, nessun documento scuola; un orario non presenta buchi interni (compattezza verticale). L'esenzione break-level (gap scusato solo tra due lezioni pinnate) e' calibrata sul golden; introdotto TASK-227 (promosso da M13).`

### H20 - Group calendar closed slot (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (GroupCalendarRule: slot aperti/chiusi per weekday); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo, seed calendar. Rete di sicurezza (il solver evita gia' gli slot chiusi per costruzione); introdotto TASK-342.`
- EN: `School's per-group calendar (GroupCalendarRule: open/closed slots per weekday); "Copy of Calendario scolastico 2026_2027.xlsx", group tab, seeded calendar. Safety net (the solver already avoids closed slots by construction); introduced TASK-342.`
- IT: `Calendario per-gruppo della scuola (GroupCalendarRule: slot aperti/chiusi per weekday); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo, seed calendar. Rete di sicurezza (il solver evita gia' gli slot chiusi per costruzione); introdotto TASK-342.`

### H23 - Quota-bound lab on zero-lab-quota day (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).`
- EN: `School's per-group calendar (DailyActivityQuota.labHours for G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Safety net; introduced TASK-381 (L1-bis).`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).`

### H24 - Aula lesson on zero-aula-quota day (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.aulaHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).`
- EN: `School's per-group calendar (DailyActivityQuota.aulaHours for G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Safety net; introduced TASK-381 (L1-bis).`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota.aulaHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-381 (L1-bis).`

### H25 - Stage lesson outside stage days (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.stageHours: le date con stage); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Confinamento dello stage; introdotto TASK-384.`
- EN: `School's per-group calendar (DailyActivityQuota.stageHours: the stage dates); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Stage confinement; introduced TASK-384.`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota.stageHours: le date con stage); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Confinamento dello stage; introdotto TASK-384.`

### H26 - Lesson on no-session day (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (una data scolastica senza riga DailyActivityQuota = gruppo non in sessione); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-408.`
- EN: `School's per-group calendar (a school date with no DailyActivityQuota row = group not in session); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Safety net; introduced TASK-408.`
- IT: `Calendario per-gruppo della scuola (una data scolastica senza riga DailyActivityQuota = gruppo non in sessione); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Rete di sicurezza; introdotto TASK-408.`

### H28 - No teacher return within day (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden non presenta mai sequenze A..B..A nello stesso (gruppo, materia, giorno). Cristallizza l'invariante a delta zero (il run e' gia' a 0); introdotto TASK-448.`
- EN: `Golden practice, no school document: the golden never shows A..B..A sequences within a (group, subject, day). Crystallizes the invariant at delta zero (the run is already at 0); introduced TASK-448.`
- IT: `Pratica del golden, nessun documento scuola: il golden non presenta mai sequenze A..B..A nello stesso (gruppo, materia, giorno). Cristallizza l'invariante a delta zero (il run e' gia' a 0); introdotto TASK-448.`

### H29 - Assigned lesson must have a room (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; ogni lezione collocata deve avere un'aula (un orario senza aula non e' usabile). Promosso a hard (era M36) col TASK-447 per chiudere l'arbitraggio empty-the-schedule.`
- EN: `Structural constraint, no school document; every placed lesson must have a room (a timetable without a room is unusable). Promoted to hard (was M36) in TASK-447 to close the empty-the-schedule arbitrage.`
- IT: `Vincolo strutturale, nessun documento scuola; ogni lezione collocata deve avere un'aula (un orario senza aula non e' usabile). Promosso a hard (era M36) col TASK-447 per chiudere l'arbitraggio empty-the-schedule.`

### H-M2 - Room suitability (D, assunzione lab da confermare)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un corso di laboratorio va in un'aula col tag corrispondente (fisico). Assunzione ancora da confermare con la scuola: Disegno meccanico (meccanica) e Disegno edile (informatica) trattati come lab. Introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; a lab course goes in a room with the matching tag (physical). Assumption still to confirm with the school: Disegno meccanico (mechanics) and Disegno edile (IT) are treated as labs. Foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; un corso di laboratorio va in un'aula col tag corrispondente (fisico). Assunzione ancora da confermare con la scuola: Disegno meccanico (meccanica) e Disegno edile (informatica) trattati come lab. Introdotto fin dalle origini.`

### H-COPRESENCE - Guest co-presenza cap (C, golden) [NUOVA CARD UI]
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il tetto di co-presenza di un ospite in un'aula e' la profondita' che il golden stesso pratica per quella cella (loadGoldenCoPresenceDepth). Lettura del golden su percorso di produzione, censita in TASK-452 #7; introdotto TASK-464.`
- EN: `Golden practice, no school document: the cap on a guest sharing a host's room is the depth the golden itself practices for that cell (loadGoldenCoPresenceDepth). Golden read on the production path, censused in TASK-452 #7; introduced TASK-464.`
- IT: `Pratica del golden, nessun documento scuola: il tetto di co-presenza di un ospite in un'aula e' la profondita' che il golden stesso pratica per quella cella (loadGoldenCoPresenceDepth). Lettura del golden su percorso di produzione, censita in TASK-452 #7; introdotto TASK-464.`
- CARD en (name/description/example/origin):
  - name: `Guest Co-presence Cap`
  - description: `A guest teacher may share a host's room only up to the depth the school's own golden timetable practices for that (co-presence tag, weekday, slot) cell. A hard count-based backstop paired with the room-conflict exemption that lets a golden-practiced guest pair share a room.`
  - example: `If the golden places at most 2 guest lessons in R16 on Thursday slot 0, a third guest lesson landing there is penalized.`
- CARD it:
  - name: `Tetto Co-presenza Ospite`
  - description: `Un docente ospite puo' condividere l'aula di un titolare solo fino alla profondita' che il golden stesso pratica per quella cella (tag co-presenza, weekday, slot). Rete di sicurezza hard a conteggio, abbinata all'esenzione di conflitto aula che lascia condividere l'aula a una coppia ospite praticata dal golden.`
  - example: `Se il golden colloca al massimo 2 lezioni ospite in R16 al giovedi' slot 0, una terza lezione ospite li' e' penalizzata.`

---

## MEDIUM

### M3 - Room capacity (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; l'aula deve contenere la classe (capacita' vs numerosita'), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; the room must hold the class (capacity vs size), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; l'aula deve contenere la classe (capacita' vs numerosita'), introdotto fin dalle origini.`

### M5 - Informatica after stage (D, pratica tesine)
- javadoc: `<p><b>Origine.</b> Pratica della scuola per le tesine (MODA/FA), nessun documento scuola citato: le ore di Informatica preferibilmente dopo lo stage. Preferenza non requisito; introdotto TASK-015.`
- EN: `School practice for thesis work (MODA/FA), no cited school document: Informatica hours preferably after stage. A preference, not a requirement; introduced TASK-015.`
- IT: `Pratica della scuola per le tesine (MODA/FA), nessun documento scuola citato: le ore di Informatica preferibilmente dopo lo stage. Preferenza non requisito; introdotto TASK-015.`

### M12 - Overflow beyond target end date (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola: la data fine attesa cumula le ore attese per-data (DailyActivityQuota expected-hours) al totale del corso; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Fact-based dal TASK-394; introdotto TASK-385.`
- EN: `School's per-group calendar: the expected end date cumulates the per-date expected hours (DailyActivityQuota) to the course total; "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Fact-based since TASK-394; introduced TASK-385.`
- IT: `Calendario per-gruppo della scuola: la data fine attesa cumula le ore attese per-data (DailyActivityQuota) al totale del corso; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Fact-based dal TASK-394; introdotto TASK-385.`

### M15 - Same-day same-subject consecutive (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden stesso spezza 98 volte una coppia stesso-giorno stessa-materia, quindi la regola e' una preferenza forte non una feasibility. Demoted da hard (H15) TASK-392; esenzione break-level calibrata sul golden.`
- EN: `Golden practice, no school document: the golden itself splits a same-day same-subject pair 98 times, so the rule is a strong preference, not feasibility. Demoted from hard (H15) in TASK-392; break-level exemption calibrated on the golden.`
- IT: `Pratica del golden, nessun documento scuola: il golden stesso spezza 98 volte una coppia stesso-giorno stessa-materia, quindi la regola e' una preferenza forte non una feasibility. Demoted da hard (H15) TASK-392; esenzione break-level calibrata sul golden.`

### M18 - Day start from first open slot (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden ancora il 99,3% dei giorni-gruppo al primo slot aperto (misure task405-results). Allargato a ogni giorno col gradiente per-offset TASK-405; era gated ai giorni corti (task-247/350).`
- EN: `Golden practice, no school document: the golden anchors 99.3% of group-days at the first open slot (measured in task405-results). Widened to every day with a per-offset gradient in TASK-405; was gated to short days (task-247/350).`
- IT: `Pratica del golden, nessun documento scuola: il golden ancora il 99,3% dei giorni-gruppo al primo slot aperto (misure task405-results). Allargato a ogni giorno col gradiente per-offset TASK-405; era gated ai giorni corti (task-247/350).`

### M20 - Daily quota fill (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota: ore attese aula+lab per (gruppo, data)); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Target netto dei pin autoritativi dal TASK-424; introdotto TASK-385.`
- EN: `School's per-group calendar (DailyActivityQuota: expected classroom+lab hours per (group, date)); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Target net of authoritative pins since TASK-424; introduced TASK-385.`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota: ore attese aula+lab per (gruppo, data)); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Target netto dei pin autoritativi dal TASK-424; introdotto TASK-385.`

### M20.2 - Empty expected day (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (un giorno atteso con ore>0 non puo' restare vuoto); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Introdotto TASK-409.`
- EN: `School's per-group calendar (an expected day with hours>0 cannot be left empty); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Introduced TASK-409.`
- IT: `Calendario per-gruppo della scuola (un giorno atteso con ore>0 non puo' restare vuoto); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Introdotto TASK-409.`

### M21 - Max 2h per subject per day (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce blocchi da 2h, quindi la soglia e' a 2h (alzata da 1h, TASK-381). Materie lab/Informatica/Competenze Digitali/Disegno esenti; pseudo-docente esente.`
- EN: `Golden practice, no school document: the school builds 2h blocks, so the threshold is 2h (raised from 1h, TASK-381). Lab/Informatica/Competenze Digitali/Disegno exempt; pseudo-teacher exempt.`
- IT: `Pratica del golden, nessun documento scuola: la scuola costruisce blocchi da 2h, quindi la soglia e' a 2h (alzata da 1h, TASK-381). Materie lab/Informatica/Competenze Digitali/Disegno esenti; pseudo-docente esente.`

### M22 - Calendar compactness (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce un blocco compatto di giorni di lezione (dal primo all'ultimo, senza giorni vuoti in mezzo). Introdotto task-273.`
- EN: `Golden practice, no school document: the school builds one compact block of teaching days (first to last, no empty days in between). Introduced task-273.`
- IT: `Pratica del golden, nessun documento scuola: la scuola costruisce un blocco compatto di giorni di lezione (dal primo all'ultimo, senza giorni vuoti in mezzo). Introdotto task-273.`

### M23 - Block lesson pair togetherness (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; un blocco pluri-orario modellato come righe separate deve stare nello stesso giorno (integrita' del blocco, es. Disegno edile 2h). Introdotto TASK-183.`
- EN: `Structural constraint, no school document; a multi-hour block modeled as separate rows must land on the same day (block integrity, e.g. Disegno edile 2h). Introduced TASK-183.`
- IT: `Vincolo strutturale, nessun documento scuola; un blocco pluri-orario modellato come righe separate deve stare nello stesso giorno (integrita' del blocco, es. Disegno edile 2h). Introdotto TASK-183.`

### M24 - At most one early-start day per week (A, extract riga 49) [T11, NON T05]
- quote: `T11 | 9-14 sempre e un giorno alle 8.00`
- javadoc: `<p><b>Origine.</b> "T11 | 9-14 sempre e un giorno alle 8.00" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 49). Per-docente maxEarlyStartDaysPerWeek (T11=1); raggruppato per (docente, settimana ISO); introdotto TASK-368.`
- EN: `"T11 | 9-14 sempre e un giorno alle 8.00" (Spalmone constraints workbook, tab "Vincoli Docenti", row 49). Per-teacher maxEarlyStartDaysPerWeek (T11=1); grouped by (teacher, ISO week); introduced TASK-368.`
- IT: `"T11 | 9-14 sempre e un giorno alle 8.00" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 49). Per-docente maxEarlyStartDaysPerWeek (T11=1); raggruppato per (docente, settimana ISO); introdotto TASK-368.`

### M25 - Required free day per week (A, extract riga 45-46) [T05]
- quote (r46): `VENERDI' | Libero  a casa`; contesto (r45): `No lezione in uno di questi giorni AL MASSIMO UN'ORA`
- javadoc: `<p><b>Origine.</b> T05: "VENERDI' | Libero  a casa" e "No lezione in uno di questi giorni AL MASSIMO UN'ORA" (workbook vincoli Spalmone, tab "Vincoli Docenti", righe 45-46). Per-docente requiredFreeDaysPerWeek (T05=2); introdotto TASK-368.`
- EN: `T05: "VENERDI' | Libero  a casa" and "No lezione in uno di questi giorni AL MASSIMO UN'ORA" (Spalmone constraints workbook, tab "Vincoli Docenti", rows 45-46). Per-teacher requiredFreeDaysPerWeek (T05=2); introduced TASK-368.`
- IT: `T05: "VENERDI' | Libero  a casa" e "No lezione in uno di questi giorni AL MASSIMO UN'ORA" (workbook vincoli Spalmone, tab "Vincoli Docenti", righe 45-46). Per-docente requiredFreeDaysPerWeek (T05=2); introdotto TASK-368.`

### M26 - Sicurezza before stage (A, extract riga 42)
- quote: `SICUREZZA | Ore con le 3° (+ 2° OIT) prima di Stage | 2 gg di sicurezza  4 ore con le 1°`
- javadoc: `<p><b>Origine.</b> "SICUREZZA | Ore con le 3° (+ 2° OIT) prima di Stage | 2 gg di sicurezza  4 ore con le 1°" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 42). Le Sicurezze precedono lo stage del gruppo; introdotto TASK-369.`
- EN: `"SICUREZZA | Ore con le 3° (+ 2° OIT) prima di Stage | 2 gg di sicurezza  4 ore con le 1°" (Spalmone constraints workbook, tab "Vincoli Docenti", row 42). Sicurezza lessons precede the group's stage; introduced TASK-369.`
- IT: `"SICUREZZA | Ore con le 3° (+ 2° OIT) prima di Stage | 2 gg di sicurezza  4 ore con le 1°" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 42). Le Sicurezze precedono lo stage del gruppo; introdotto TASK-369.`

### M27 - T20 subject start-period (A, extract riga 98/101)
- quote (r98): `Subito merceologia in 1° e 2°`; quote (r101): `3° fa tecnologia e merceologia ultime`
- javadoc: `<p><b>Origine.</b> "Subito merceologia in 1° e 2°" e "3° fa tecnologia e merceologia ultime" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 98 e 101). Finestra sulla settimana della prima lezione per materie T20; introdotto TASK-369.`
- EN: `"Subito merceologia in 1° e 2°" and "3° fa tecnologia e merceologia ultime" (Spalmone constraints workbook, tab "Vincoli DA ANALIZZARE", rows 98 and 101). Window on the first-lesson week for T20 subjects; introduced TASK-369.`
- IT: `"Subito merceologia in 1° e 2°" e "3° fa tecnologia e merceologia ultime" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 98 e 101). Finestra sulla settimana della prima lezione per materie T20; introdotto TASK-369.`

### M28 - Afternoon slots reserved for stage (D, convenzione; demoted TASK-379)
- javadoc: `<p><b>Origine.</b> Convenzione strutturale (il pomeriggio e' riservato allo stage), nessun documento scuola. Demoted da hard (H21) TASK-379: il golden usa 575 lezioni pomeridiane come sfogo di capacita'.`
- EN: `Structural convention (afternoons are reserved for stage), no school document. Demoted from hard (H21) in TASK-379: the golden uses 575 afternoon lessons as capacity relief.`
- IT: `Convenzione strutturale (il pomeriggio e' riservato allo stage), nessun documento scuola. Demoted da hard (H21) TASK-379: il golden usa 575 lezioni pomeridiane come sfogo di capacita'.`

### M29 - Professionalizzanti at day edges (A + modeling, extract riga 14; demoted TASK-379)
- quote (r14, G12): `Professionalizzanti solo il giovedì e il venerdì`
- javadoc: `<p><b>Origine.</b> "Professionalizzanti solo il giovedì e il venerdì" (G12; workbook vincoli Spalmone, tab "Vincoli Classi", riga 14). La forma "al bordo della giornata, blocco fino a 2h" e' modellata sulla pratica; demoted da hard (H22) TASK-379 perche' il golden non la segue strettamente.`
- EN: `"Professionalizzanti solo il giovedì e il venerdì" (G12; Spalmone constraints workbook, tab "Vincoli Classi", row 14). The "day-edge, up-to-2h-block" shape is modeled on practice; demoted from hard (H22) in TASK-379 because the golden does not follow it strictly.`
- IT: `"Professionalizzanti solo il giovedì e il venerdì" (G12; workbook vincoli Spalmone, tab "Vincoli Classi", riga 14). La forma "al bordo della giornata, blocco fino a 2h" e' modellata sulla pratica; demoted da hard (H22) TASK-379 perche' il golden non la segue strettamente.`

### M30.1 / M30.2 - Lab day quota fill (B, calendario)
- javadoc (su entrambi): `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Conta anche i pin (verifica la pratiche storiche della scuola); introdotto TASK-381 (L1-bis).`
- EN: `School's per-group calendar (DailyActivityQuota.labHours for G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Counts pinned lessons too (checks the school's own historical practice); introduced TASK-381 (L1-bis).`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota.labHours per G04/G08/G13); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Conta anche i pin (verifica la pratiche storiche della scuola); introdotto TASK-381 (L1-bis).`

### M31 - Lab day single contiguous run (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola + pratica del blocco lab compatto; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Day-level pinned-exempt; introdotto TASK-381 (L1-bis).`
- EN: `School's per-group calendar + the compact-lab-block practice; "Copy of Calendario scolastico 2026_2027.xlsx", group tab. Day-level pinned-exempt; introduced TASK-381 (L1-bis).`
- IT: `Calendario per-gruppo della scuola + pratica del blocco lab compatto; "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Day-level pinned-exempt; introdotto TASK-381 (L1-bis).`

### M32 - Template deviation (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il template settimanale per corso (cella weekday/slot ricorrente) e' derivato dal golden/stamp e pubblicato pre-solve (WeeklyTemplatePlanner.buildReference). Peso graduato per scarsezza; introdotto TASK-413.`
- EN: `Golden practice, no school document: the per-course weekly template (recurring weekday/slot cell) is derived from the golden/stamp and published pre-solve (WeeklyTemplatePlanner.buildReference). Scarcity-graduated weight; introduced TASK-413.`
- IT: `Pratica del golden, nessun documento scuola: il template settimanale per corso (cella weekday/slot ricorrente) e' derivato dal golden/stamp e pubblicato pre-solve (WeeklyTemplatePlanner.buildReference). Peso graduato per scarsezza; introdotto TASK-413.`

### M33 - Surplus beyond expected day hours (B, calendario)
- javadoc: `<p><b>Origine.</b> Calendario per-gruppo della scuola (DailyActivityQuota expected-hours, target netto dei pin TASK-424); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Il golden stesso sfora ~70 giorni/anno, quindi e' penalty leggera non ban; introdotto TASK-423.`
- EN: `School's per-group calendar (DailyActivityQuota expected-hours, target net of pins TASK-424); "Copy of Calendario scolastico 2026_2027.xlsx", group tab. The golden itself goes above ~70 days/year, so it is a light penalty, not a ban; introduced TASK-423.`
- IT: `Calendario per-gruppo della scuola (DailyActivityQuota expected-hours, target netto dei pin TASK-424); "Copy of Calendario scolastico 2026_2027.xlsx", tab del gruppo. Il golden stesso sfora ~70 giorni/anno, quindi e' penalty leggera non ban; introdotto TASK-423.`

### M34 - Weekly subject hours above golden cadence (C, golden) [NUOVA CARD UI]
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il cap settimanale per (gruppo, materia) e' il massimo settimanale del golden (claudedocs/spalmone-2026-27/data/golden-cadence-reference.json). Self-gating; introdotto TASK-425.`
- EN: `Golden practice, no school document: the weekly cap per (group, subject) is the golden's own weekly maximum (claudedocs/spalmone-2026-27/data/golden-cadence-reference.json). Self-gating; introduced TASK-425.`
- IT: `Pratica del golden, nessun documento scuola: il cap settimanale per (gruppo, materia) e' il massimo settimanale del golden (claudedocs/spalmone-2026-27/data/golden-cadence-reference.json). Self-gating; introdotto TASK-425.`
- CARD en: name `Weekly Subject Hours Above Golden Cadence`; description `A subject's hours in one week should not exceed the highest weekly count the school's own golden timetable ever places for that (group, subject). A self-gating cap: pairs absent from the golden are inert. Penalizes burst concentration beyond what the school itself does.`; example `If the golden places at most 4 hours of Matematica in any week for G03, a 6-hour Matematica week is penalized for the 2 excess hours.`
- CARD it: name `Ore settimanali materia sopra la cadenza golden`; description `Le ore di una materia in una settimana non dovrebbero superare il massimo settimanale che il golden colloca per quel (gruppo, materia). Cap self-gating: le coppie assenti dal golden sono inerti. Penalizza la concentrazione a burst oltre cio' che fa la scuola.`; example `Se il golden colloca al massimo 4 ore di Matematica in una settimana per G03, una settimana da 6 ore di Matematica e' penalizzata per le 2 ore in eccesso.`

### M35 - Weekly subject spread below golden cadence (C, golden) [NUOVA CARD UI]
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: penalizza le settimane attive mancanti rispetto ad active_weeks del golden per (gruppo, materia) (golden-cadence-reference.json). Self-gating; introdotto TASK-425.`
- EN: `Golden practice, no school document: penalizes missing active weeks vs the golden's active_weeks per (group, subject) (golden-cadence-reference.json). Self-gating; introduced TASK-425.`
- IT: `Pratica del golden, nessun documento scuola: penalizza le settimane attive mancanti rispetto ad active_weeks del golden per (gruppo, materia) (golden-cadence-reference.json). Self-gating; introdotto TASK-425.`
- CARD en: name `Weekly Subject Spread Below Golden Cadence`; description `A subject should be present in at least as many active weeks as the school's own golden timetable spreads it, for each (group, subject). Penalizes missing active weeks vs the golden's spread. Self-gating: pairs absent from the golden are inert.`; example `If the golden spreads Lingua Italiana for G06 over 28 active weeks, a run that concentrates it into 22 weeks is penalized for the 6 missing weeks.`
- CARD it: name `Diffusione settimanale materia sotto la cadenza golden`; description `Una materia dovrebbe essere presente in almeno tante settimane attive quante il golden la distribuisce, per ogni (gruppo, materia). Penalizza le settimane attive mancanti rispetto alla diffusione del golden. Self-gating: le coppie assenti dal golden sono inerti.`; example `Se il golden distribuisce Lingua Italiana per G06 su 28 settimane attive, un run che la concentra in 22 settimane e' penalizzato per le 6 settimane mancanti.`

### M37 - Consecutive same-subject same-teacher keep room (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden non registra le aule, quindi la regola non difende il tier hard, ma "ogni classe ha un'aula" (H29) e' fisicamente irrinunciabile. Demoted da hard (H16b) TASK-446/447; il peso non e' la leva (sweep piatto a ~106 cambi d'aula forzati dalla disponibilita').`
- EN: `Golden practice, no school document: the golden does not record rooms, so the rule does not defend the hard tier, but "every class has a room" (H29) is physically indispensable. Demoted from hard (H16b) in TASK-446/447; the weight is not the lever (flat sweep at ~106 room changes forced by availability).`
- IT: `Pratica del golden, nessun documento scuola: il golden non registra le aule, quindi la regola non difende il tier hard, ma "ogni classe ha un'aula" (H29) e' fisicamente irrinunciabile. Demoted da hard (H16b) TASK-446/447; il peso non e' la leva (sweep piatto a ~106 cambi d'aula forzati dalla disponibilita').`

### M-LA - Lab assistant conflict (A, extract riga 96-97)
- quote (r96): `T14 | Libera per doppio in 2°(100 ore)  e 3° moda (100 ore) in R16`; quote (r97): `T20 | Libero per doppio in 2° (100 ore)  e 3° moda  (85 ore) in R16`
- javadoc: `<p><b>Origine.</b> Doppio/assistente: "T14 | Libera per doppio in 2°(100 ore)  e 3° moda (100 ore) in R16" e "T20 | Libero per doppio in 2° (100 ore)  e 3° moda  (85 ore) in R16" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 96-97). L'assistente non entra in conflitto con un lab nella sua stanza; vive in M-LA a tier medium.`
- EN: `Doppio/assistant: "T14 | Libera per doppio in 2°(100 ore)  e 3° moda (100 ore) in R16" and "T20 | Libero per doppio in 2° (100 ore)  e 3° moda  (85 ore) in R16" (Spalmone constraints workbook, tab "Vincoli DA ANALIZZARE", rows 96-97). The assistant should not conflict with a lab in their room; lives in M-LA at medium tier.`
- IT: `Doppio/assistente: "T14 | Libera per doppio in 2°(100 ore)  e 3° moda (100 ore) in R16" e "T20 | Libero per doppio in 2° (100 ore)  e 3° moda  (85 ore) in R16" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 96-97). L'assistente non entra in conflitto con un lab nella sua stanza; vive in M-LA a tier medium.`

### M-LF - Non-lab lesson in tagged room (D, fallback stanza; TASK-456 / referente Q21) [NUOVA CARD UI]
- javadoc: `<p><b>Origine.</b> Vincolo strutturale di fallback delle stanze, nessun documento scuola; un'aula capienza<=0 non e' assegnabile (oggi R18 cap 0). Domanda 21 (referente, 29/07); introdotto TASK-456, ratificato maintainer 2026-07-29.`
- EN: `Structural room-fallback constraint, no school document; a room with capacity<=0 is not assignable (today R18, cap 0). Question 21 (referente, 29/07); introduced TASK-456, ratified by maintainer 2026-07-29.`
- IT: `Vincolo strutturale di fallback delle stanze, nessun documento scuola; un'aula capienza<=0 non e' assegnabile (oggi R18 cap 0). Domanda 21 (referente, 29/07); introdotto TASK-456, ratificato maintainer 2026-07-29.`
- CARD en: name `Non-lab Lesson in a Tagged Room`; description `A non-lab lesson should preferably not sit in a specialized (tagged) lab room: labs stay the last resort for ordinary lessons. Paired with the rule that a room with capacity zero or less (today R18) is never assignable.`; example `A Matematica lesson in the R16 lab costs a medium penalty when a regular classroom is available; it is never sent to R18.`
- CARD it: name `Lezione non-lab in aula con tag`; description `Una lezione non-lab preferibilmente non sta in un'aula di laboratorio specializzata (con tag): il lab resta l'ultima risorsa per le lezioni ordinarie. Abbinata alla regola che un'aula con capienza zero o meno (oggi R18) non e' mai assegnabile.`; example `Una lezione di Matematica nel laboratorio di R16 costa una penalty medium quando c'e' un'aula normale disponibile; non viene mai mandata in R18.`

### M-TC - Lab tag capacity (D, scarsita'; TASK-455) [NUOVA CARD UI, PROTOTIPO flag OFF]
- javadoc: `<p><b>Origine.</b> Vincolo strutturale di scarsita' dei tag, nessun documento scuola; la domanda di lezioni di un tag oltre la capacita' aggregata delle stanze assignable per cella (tag, startDateTime). Prototipo flag planner.tag-capacity.enabled DEFAULT OFF; introdotto TASK-455.`
- EN: `Structural tag-scarcity constraint, no school document; the demand for a tag's lessons beyond the aggregate capacity of assignable rooms per (tag, startDateTime) cell. Prototype flag planner.tag-capacity.enabled DEFAULT OFF; introduced TASK-455.`
- IT: `Vincolo strutturale di scarsita' dei tag, nessun documento scuola; la domanda di lezioni di un tag oltre la capacita' aggregata delle stanze assignable per cella (tag, startDateTime). Prototipo flag planner.tag-capacity.enabled DEFAULT OFF; introdotto TASK-455.`
- CARD en: name `Lab Tag Capacity (prototype, off by default)`; description `Penalizes a lab tag's demand beyond the aggregate room capacity available for that (tag, start-date-time) cell. Scarcity-aware, medium tier (the golden itself accepts ~44 such violations). Prototype, off by default (flag planner.tag-capacity.enabled).`; example `If 5 acconciatura lessons need R16 at Thursday slot 0 but the assignable R16 rooms hold 3, the 2 excess are penalized.`
- CARD it: name `Capienza tag lab (prototipo, off di default)`; description `Penalizza la domanda di un tag lab oltre la capacita' aggregata delle stanze disponibili per quella cella (tag, start-date-time). Scarsita'-aware, tier medium (il golden stesso accetta ~44 violazioni). Prototipo, off di default (flag planner.tag-capacity.enabled).`; example `Se 5 lezioni di acconciatura richiedono R16 al giovedi' slot 0 ma le stanze R16 assegnabili ne tengono 3, i 2 in eccesso sono penalizzati.`

### M-TR - Theory subject repeated same day (A, extract riga 104-105) [NUOVA CARD UI, PROTOTIPO flag OFF]
- quote (r104): `Non ci devono essere 2 ore vicine della stessa materia teorica`; quote (r105): `I laboratori (compreso disegno o informatica) possono avere più ore vicine`
- javadoc: `<p><b>Origine.</b> "Non ci devono essere 2 ore vicine della stessa materia teorica" e "I laboratori (compreso disegno o informatica) possono avere più ore vicine" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 104-105). Soglia-1 teorica (M21-style a soglia 2); prototipo flag planner.constraints.theory-adjacent.enabled DEFAULT OFF; introdotto TASK-468.`
- EN: `"Non ci devono essere 2 ore vicine della stessa materia teorica" and "I laboratori (compreso disegno o informatica) possono avere più ore vicine" (Spalmone constraints workbook, tab "Vincoli DA ANALIZZARE", rows 104-105). Theory threshold-1 (M21-style at threshold 2); prototype flag planner.constraints.theory-adjacent.enabled DEFAULT OFF; introduced TASK-468.`
- IT: `"Non ci devono essere 2 ore vicine della stessa materia teorica" e "I laboratori (compreso disegno o informatica) possono avere più ore vicine" (workbook vincoli Spalmone, tab "Vincoli DA ANALIZZARE", righe 104-105). Soglia-1 teorica (M21-style a soglia 2); prototipo flag planner.constraints.theory-adjacent.enabled DEFAULT OFF; introdotto TASK-468.`
- CARD en: name `Theory Subject Repeated Same Day (prototype, off by default)`; description `Two adjacent hours of the same theory subject should not be placed back-to-back; labs (including Disegno and Informatica) may have longer adjacent runs. Theory threshold-1, distinct from M21's threshold-2 cap. Prototype, off by default (flag planner.constraints.theory-adjacent.enabled).`; example `Lingua Italiana at 9:00 and 10:00 for the same group is penalized; Disegno at 9:00 and 10:00 is not.`
- CARD it: name `Materia teorica ripetuta nello stesso giorno (prototipo, off di default)`; description `Due ore vicine della stessa materia teorica non dovrebbero stare attaccate; i laboratori (compreso Disegno e Informatica) possono avere piu' ore vicine. Soglia-1 teorica, distinta dal cap soglia-2 di M21. Prototipo, off di default (flag planner.constraints.theory-adjacent.enabled).`; example `Lingua Italiana alle 9:00 e alle 10:00 per lo stesso gruppo e' penalizzata; Disegno alle 9:00 e alle 10:00 no.`

### M-CPS - Curriculum post-stage 6h (A, extract riga 50) [NUOVA CARD UI, PROTOTIPO flag OFF]
- quote: `CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)`
- javadoc: `<p><b>Origine.</b> "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 50). Target 6h asimmetrico sulle coorti finali (G10 / G08 / G11, T11); prototipo flag planner.constraints.curriculum-poststage.enabled DEFAULT OFF; introdotto TASK-469.`
- EN: `"CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" (Spalmone constraints workbook, tab "Vincoli Docenti", row 50). Asymmetric 6h target on the final cohorts (G10 / G08 / G11, T11); prototype flag planner.constraints.curriculum-poststage.enabled DEFAULT OFF; introduced TASK-469.`
- IT: `"CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" (workbook vincoli Spalmone, tab "Vincoli Docenti", riga 50). Target 6h asimmetrico sulle coorti finali (G10 / G08 / G11, T11); prototipo flag planner.constraints.curriculum-poststage.enabled DEFAULT OFF; introdotto TASK-469.`
- CARD en: name `Curriculum Post-Stage 6h (prototype, off by default)`; description `After a final-year cohort's stage ends, its curriculum subjects (G10 Comportamento Sociale, G08 Competenze di cittadinanza + Educazione Civica, G11 Personalizzazione, teacher T11) should fill around 6 hours per day. Asymmetric: a deficit costs full weight, an excess costs only a token. Prototype, off by default (flag planner.constraints.curriculum-poststage.enabled).`; example `If G10's stage ends 2027-04-23 and only 3 curriculum hours are placed on a later day, the 3-hour deficit vs the 6h target is penalized at full weight.`
- CARD it: name `Curriculum post-stage 6h (prototipo, off di default)`; description `Dopo la fine dello stage di una coorte finale, le sue materie curriculum (G10 Comportamento Sociale, G08 Competenze di cittadinanza + Educazione Civica, G11 Personalizzazione, docente T11) dovrebbero riempire circa 6 ore al giorno. Asimmetrico: il deficit costa peso pieno, l'eccesso solo un segno. Prototipo, off di default (flag planner.constraints.curriculum-poststage.enabled).`; example `Se lo stage di G10 finisce il 2027-04-23 e in un giorno successivo sono collocate solo 3 ore curriculum, il deficit di 3h rispetto al target 6h e' penalizzato a peso pieno.`

---

## SOFT

### S1 - Minimize teacher gaps (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; minimizza i buchi di attesa del docente tra lezioni (qualita'), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; minimizes a teacher's idle gaps between lessons (quality), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; minimizza i buchi di attesa del docente tra lezioni (qualita'), introdotto fin dalle origini.`

### S3 - Teacher preference (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; onora gli slot preferiti dichiarati dal docente (input), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; honors a teacher's declared preferred slots (input), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; onora gli slot preferiti dichiarati dal docente (input), introdotto fin dalle origini.`

### S7 - Reward consecutive student lessons (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; premia lezioni studente consecutive (qualita'), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; rewards consecutive student lessons (quality), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; premia lezioni studente consecutive (qualita'), introdotto fin dalle origini.`

### S8 - Isolated student lesson (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; penalizza lezioni studente isolate (qualita'), introdotto fin dalle origini.`
- EN: `Structural constraint, no school document; penalizes isolated student lessons (quality), foundational.`
- IT: `Vincolo strutturale, nessun documento scuola; penalizza lezioni studente isolate (qualita'), introdotto fin dalle origini.`

### S9 - Dilute hours over year (D, disabilitato)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; diluire le ore sull'anno (qualita'). DISABILITATO dal task-273 (si oppone alla compattezza del calendario); vedi S12 per la continuita' settimanale distinta e attiva.`
- EN: `Structural constraint, no school document; dilute hours over the year (quality). DISABLED since task-273 (it opposes calendar compactness); see S12 for the distinct, active weekly continuity.`
- IT: `Vincolo strutturale, nessun documento scuola; diluire le ore sull'anno (qualita'). DISABILITATO dal task-273 (si oppone alla compattezza del calendario); vedi S12 per la continuita' settimanale distinta e attiva.`

### S10 - Discourage generic lessons in labs (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; sconsiglia le materie ordinarie nelle aule-lab (qualita'), introdotto task-007.`
- EN: `Structural constraint, no school document; discourages ordinary subjects in lab rooms (quality), introduced task-007.`
- IT: `Vincolo strutturale, nessun documento scuola; sconsiglia le materie ordinarie nelle aule-lab (qualita'), introdotto task-007.`

### S11 - Student group room affinity (D)
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; una classe preferibilmente resta nella stessa aula per le lezioni non-lab (qualita'), introdotto task-008.`
- EN: `Structural constraint, no school document; a class preferably stays in the same room for non-lab lessons (quality), introduced task-008.`
- IT: `Vincolo strutturale, nessun documento scuola; una classe preferibilmente resta nella stessa aula per le lezioni non-lab (qualita'), introdotto task-008.`

### S12 - Weekly subject presence (D, continuita')
- javadoc: `<p><b>Origine.</b> Vincolo strutturale, nessun documento scuola; continuita' settimanale (almeno una lezione/settimana mentre la materia e' attiva, lower bound, NON ripristino di S9); introdotto TASK-369.`
- EN: `Structural constraint, no school document; weekly continuity (at least one lesson/week while a subject is active, a lower bound, NOT a restoration of S9); introduced TASK-369.`
- IT: `Vincolo strutturale, nessun documento scuola; continuita' settimanale (almeno una lezione/settimana mentre la materia e' attiva, lower bound, NON ripristino di S9); introdotto TASK-369.`

### S13 - Weekly stability (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: e' una MISURA della ricorrenza settimanale (cella gruppo/weekday/slot uguale alla settimana precedente su docente e materia). La protezione del template e' M32; introdotto task-375.`
- EN: `Golden practice, no school document: a MEASURE of weekly recurrence (group/weekday/slot cell matching the previous week on teacher and subject). Template protection is M32; introduced task-375.`
- IT: `Pratica del golden, nessun documento scuola: e' una MISURA della ricorrenza settimanale (cella gruppo/weekday/slot uguale alla settimana precedente su docente e materia). La protezione del template e' M32; introdotto task-375.`

### S16 - Consecutive same-subject same teacher (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: il golden passa deliberatamente le ore RA/Recuperi tra docenti back-to-back (15 handoff), quindi il cambio e' ammesso, solo scoraggiato. Demoted da hard (H16a) TASK-377.`
- EN: `Golden practice, no school document: the golden deliberately hands RA/Recuperi hours between teachers back-to-back (15 handoffs), so a handoff is allowed, just discouraged. Demoted from hard (H16a) in TASK-377.`
- IT: `Pratica del golden, nessun documento scuola: il golden passa deliberatamente le ore RA/Recuperi tra docenti back-to-back (15 handoff), quindi il cambio e' ammesso, solo scoraggiato. Demoted da hard (H16a) TASK-377.`

### S17 - Reward same-subject block pair (C, golden)
- javadoc: `<p><b>Origine.</b> Pratica del golden, nessun documento scuola: la scuola costruisce blocchi same-day same-subject (premia la coppia adiacente, lab inclusi a differenza di M21/S16); introdotto TASK-381 (L3).`
- EN: `Golden practice, no school document: the school builds same-day same-subject blocks (rewards the adjacent pair, labs included unlike M21/S16); introduced TASK-381 (L3).`
- IT: `Pratica del golden, nessun documento scuola: la scuola costruisce blocchi same-day same-subject (premia la coppia adiacente, lab inclusi a differenza di M21/S16); introdotto TASK-381 (L3).`

---

## Spot-check AC#4 (5 vincoli a campione contro fonte)
Da verificare che il quote ESISTA verbatim nel file/tab indicato:
1. H7 riga 108 -> `vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` riga 108.
2. M26 riga 42 -> stesso extract riga 42.
3. M-TR righe 104-105 -> extract righe 104-105.
4. M-CPS riga 50 -> extract riga 50.
5. M-LA righe 96-97 -> extract righe 96-97.

## Vincoli NON in UI (legacy/strutturali) -> solo javadoc, nessuna card
H11-T28 (legacy watch-item), H4.3 (sub di H4), M30.1/M30.2 (share la card M30 esistente),
M20.2 (share M20). Per questi: solo blocco Origine nel javadoc; niente originKey/card nuovi.
M30.1/.2 e M20.2 sono sottovincoli: il javadoc va sul rispettivo metodo, la card M30/M20
esistente resta (non si duplicano card).
