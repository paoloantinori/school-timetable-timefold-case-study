# Censimento delle dipendenze dal golden (LAVORO 2, 2026-07-30)

Direttiva di contesto (maintainer 2026-07-30, vedi CLAUDE.md "la soluzione gira sul SOLO INPUT,
il golden e' temporaneo"): in esercizio la scuola ci passera' i **dati di INPUT**, non una
soluzione di esempio. Il golden (`seed/lessons.csv` + i JSON derivati in `data/`) e' un
vantaggio della fase di test e **sparisce**. Ogni meccanismo che deriva comportamento da una
SOLUZIONE ha una data di scadenza; per ognuno ci sono due esiti onesti: (1) e' deducibile
dagli input, allora si cambia sorgente e si verifica la coincidenza ORA; (2) e' una pratica della
scuola non deducibile, allora si CHIEDE e diventa input dichiarato nel workbook (TASK-445).

Questo doc e' un **censimento, non un refactor**: nessun cambio di codice. Il valore sta nella
colonna (c), che diventa la lista delle cose da chiedere alla scuola mentre il workbook e' ancora
in costruzione.

## La tabella a tre colonne

Legenda: **INPUT** = sopravvive cosi' com'e' (incarichi, calendari di gruppo, disponibilita'
docenti, quote attese). **SOLUZIONE** = legge il golden o un suo derivato, ha data di scadenza.
**IBRIDO** = file *typed* come input ma il cui *contenuto* e' sourced dal golden.

| # | Sito (file:symbol) | (a) INPUT / SOLUZIONE | (b) equivalente deducibile dagli input? | (c) domanda / colonna workbook TASK-445 |
|---|---|---|---|---|
| 1 | `ConstrainedTeacherAllocator.loadAllGoldenProfiles` (TASK-435, **DEFAULT ON** da 2026-07-30) | **SOLUZIONE** legge `seed/lessons.csv` per i target di pre-assegnazione (gruppo, giovedi', ore) | **Parziale, e il giorno ora e' input.** CHI+QUALE-GIORNO = si' (T01 solo-giovedi' **CONFERMATO da maintainer 2026-07-30**, non piu' "in attesa": la H5 VACATION e' ora input autoritativo). QUALI giovedi'/quante ore per gruppo = **no**: il foglio e' teacher-agnostic. | "La colonna (c) si restringe: serve solo la ripartizione (gruppo, giovedi') delle ore se la si vuole fissare a priori. Il docente-vincolato poggia ora su input confermato." |
| 2 | `HybridPhase1aService.buildMonoRoomCadence` + `MonoRoomTagAllocator` (TASK-449, default OFF) | **SOLUZIONE** legge `seed/lessons.csv` per le celle (weekday, slot, blocksize) disgiunte per (gruppo, room-tag) | **No (verificato).** La DOMANDA (ore lab per tag) = si' (`incarichi.csv`). Le CELLE = **no**: `LabRotation` (il veicolo input inteso) e' **VUOTO INTENZIONALMENTE** per il 2026/27 (DataGenerator: "seeded with NOTHING"). Nessun input popolato ha weekday/slot/block. | "Popolare `LabRotation` (il veicolo esiste): per ogni gruppo e lab monostanza, giorno settimanale + slot di inizio + dimensione blocco. Una riga (gruppo, tag, weekday, startSlot, hours)." |
| 3 | `ScheduleService.publishCadenceReferenceStatics` verso M34/M35 (`golden-cadence-reference.json`, TASK-425) | **SOLUZIONE** legge il JSON: burst cap settimanale (`max`) + settimane attive per (gruppo, materia). Self-gate: JSON assente allora mappe vuote, M34/M35 **inerte** (degrado graceful, non break) | **No (verificato).** Ore totali per materia = si' (`incarichi.csv`). La CADENZA (`max`/sett, settimane attive) = **no**: e' una scelta di pacing. Gli input danno solo il totale; `max` >> media derivabile per la maggior parte delle coppie (Lingua Italiana avg 2,4 vs max 6; Modello avg 3,4 vs max 6). | "Cadenza settimanale per (gruppo, materia): ore/settimana max e settimane attive. **Condizionale**: se la scuola vuole i capi (M34/M35) li dichiari; se no, restano inerte (nessun danno, self-gate)." |
| 4 | `GoldenScheduleImportService.importGolden` (TASK-346, endpoint manuale `POST /import-golden`) | **SOLUZIONE** legge `seed/lessons.csv` e importa l'intero golden come lezioni pinnate | **N/A.** Non e' una derivazione, e' lo strumento di import. In esercizio input-only questo endpoint **non viene mai chiamato**: esiste solo perche' adesso abbiamo il golden. | Nessuna. Scompare con il golden. Non e' debito, e' meccanismo di test/validazione. |
| 5 | **Famiglia Q22 in `availability_rules.csv`**: T07, T19, T24, T22, T12, T17 (6 docenti; **T01 rimosso, confermato da maintainer 2026-07-30**) | **IBRIDO**, righe VACATION *typed* come input (H5), ma il cui *contenuto* e' `Q22 golden-derived ... in attesa conferma scuola` | **No per i 6 residui.** Il giorno fisso e' una **scelta** della scuola, non deducibile da incarichi/calendari/quote (verificato sotto: il loro carico e' basso, il giorno-fisso non e' load-driven). T01 e' ora input confermato. | "Per i 6 docenti: giorno/i fisso settimanale. Domanda binaria: e' un vincolo di disponibilita' reale o una scelta organizzativa che possiamo cambiare? (Bozza domanda per referente in `domande-brevi.md`.)" |
| 6 | `WeeklyTemplatePlanner.buildReference` verso M32 (TASK-413/416) | **INPUT** deriva il template settimanale da Courses + Timeslots + Holidays + StagePatterns. NON legge il golden | **Sopravvive.** Il template (cella weekday/slot per corso) e' costruito dalla struttura dei corsi e dal calendario. | Nessuna. M32 resta intatto senza golden. |
| 7 | Repair: `BoxRepair`, `HoleRepair`, `SurplusRebalance`, `AnchorRepair`, `StudentDayCompaction` | **N/A** operano sullo stato vivo della soluzione, nessuna lettura del golden | **Sopravvivono.** | Nessuna. |
| 8 | `DataGenerator` / `CalendarioImportService` | **INPUT** (`incarichi.csv`, `calendario_*.csv`) | Sopravvive. | Nessuna. |

**Sintesi.** Ha data di scadenza (legge SOLUTION): #1 (DEFAULT ON, in produzione!), #2, #3,
#5. Sopravvive senza golden: #6 (M32), #7 (tutti i repair), #8, la costruzione, i vincoli
hard/medium classici. #4 e' il meccanismo di import stesso, non debito.

## Verifica che vale solo adesso: il docente vincolato (#1 + #5)

La domanda di maintainer: una misura basata **solo su input** (es. rapporto ore-dovute / celle
legali-disponibili) mette T01 in cima da sola? Se si', quando il golden sparisce non cambia
nulla. Se no, la concentrazione e' una scelta della scuola, e va nella colonna (c).

**Risposta: NO (l'apparenza di si' e' circolare).** Misura su `incarichi.csv` (ore dovute) +
`availability_rules.csv` (weekday disponibili = Mon-Fri meno VACATION full-day):

```
teacher            owed avail_wd   ratio  flag
T01            231        1     231  Q22-golden-avail   <- #1 SOLO perche' Q22
T21           660        5     132
T08             640        5     128
T05                496        4     124
T04         485        4     121
...
```

- T01 e' #1 per rapporto (231) **solo** perche' la sua disponibilita' e' 1 weekday
  (giovedi'), che e' essa stessa la regola Q22 derivata dal golden. Le sue ore dovute (231) sono a
  meta' classifica (~16esimo su 26: T21 660, T08 640 gli devono molto di piu').
- Senza la regola Q22 (ipotetico T01 disponibile 5 giorni), il rapporto scende a ~46,
  ~21esimo, per niente "vincolato". Il verdetto "constrained" **flippera'** interamente.
- Degli 9 docenti Q22, **solo T01** e' nel top-5: gli altri (T07 72h, T19 128h, T24
  190h) devono POCHE ore, quindi il loro "giorno fisso" non e' correlato al carico. Il pattern
  "concentrato su un giorno" NON e' deducibile dal carico o dalla capacita': e' una scelta
  per-docente visibile solo nel golden.

**Conclusione (aggiornata dopo conferma maintainer 2026-07-30).** La verifica ha isolato la domanda
load-bearing: "il giorno fisso e' input o scelta?". Per **T01 maintainer ha confermato il
giovedi'**, quindi il suo constrained-status poggia ora su input autoritativo (la Q22 VACATION e'
promossa da "in attesa" a "confermata"): il criterio non si ribaltera' quando il golden sparisce.
Restano i **6 Q22 residui** (T07 72h, T19 128h, T24 190h, T22, T12, T17):
hanno carico BASSO e giorno fisso, quindi il loro giorno-fisso NON e' load-driven, e' una scelta
organizzativa. Per loro la colonna (c) resta aperta (domanda in `domande-brevi.md`). Script:
`/tmp/measure_constrained_teacher.py`.

## Altre due verifiche fatte nella finestra (M34/M35 + mono-room)

Stessa domanda di maintainer ("deducibile dagli input o scelta della scuola?") per #2 e #3. **Entrambe
sono scelta della scuola**, cioe' colonna (c):

- **#3 M34/M35 (cadenza).** Il `max` (burst cap, M34) e le `active_weeks` (M35) NON sono deducibili
  dagli input. Gli input danno solo `total_hours` per materia (`incarichi.csv`); la cadenza e' una
  scelta di *pacing*. Misurato: per la maggior parte delle 258 coppie `max` >> media derivabile
  dal totale (G01 Lingua Italiana: totale 90h, media ~2,4h/sett, max golden 6h; Modello: media
  ~3,4, max 6). La derivazione senza golden (media piatta) seleziona cose DIVERSE dal golden (burst).
  **Non si coincidono.** Ma M34/M35 self-gate inerte senza il JSON: la colonna (c) e' *condizionale*
  (dichiara la cadenza solo se la scuola vuole i capi).
- **#2 mono-room (celle).** Le celle (weekday, slot, blocksize) per (gruppo, tag) NON sono in
  nessun input popolato: `LabRotation` (il veicolo input inteso) e' **VUOTO INTENZIONALMENTE** per
  il 2026/27 (DataGenerator `generateLabRotations`: "seeded with NOTHING ... empty LabRotation
  makes HybridPhase1aService fallback to golden"). Esistono solo nel golden. Colonna (c):
  **popolare LabRotation** (il veicolo c'e'e'): dichiarare per ogni (gruppo, lab monostanza) il
  giorno + slot + block. Fatto questo, `MonoRoomTagAllocator` puo' cambiare sorgente (TASK-452).

**Sintesi della finestra.** Dei 5 punti con data di scadenza: #1 T01 (giorno) confermato,
#2 e #3 verificati come scelta-della-scuola (colonna c), #4 non e' debito, #5 ridotto a 6 docenti
da chiedere. Tutte le verifiche "input-vs-golden" possibili sono fatte; resta da elaborare le
risposte della scuola man mano arrivano.

## Collegamento al debito censito
- **TASK-452**: nomina gia' #1 (`ConstrainedTeacherAllocator`) e #2 (`MonoRoomTagAllocator`).
  Questo censimento aggiunge #3 (M34/M35), #4 (import), e soprattutto #5 (famiglia Q22, che
  TASK-452 non menziona: e' debito di *contenuto* di un file input, non di *lettura* runtime).
- **TASK-441**: "politica per un anno senza golden". La colonna (c) e' la lista delle domande
  che alimenta quella politica: il workbook le raccoglie mentre e' in costruzione.
- **Direttiva maintainer 2026-07-30**: si procede SENZA aspettare le risposte della scuola; si aspetta
  solo quando una risposta sbagliata renderebbe il lavoro inutile. Nel dubbio si dichiara
  l'assunzione dove e' portante e si annota cosa rimisurare se la risposta arriva diversa.
