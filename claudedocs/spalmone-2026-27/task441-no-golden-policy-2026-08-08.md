# TASK-441 - Policy per l'anno senza golden (zai, 2026-08-08, turno Fedora offline)

Deliverable di TASK-441 ("C5 - processo riferimenti golden annuali: policy anno-senza-golden +
derivazione riproducibile M21/T20"). Consolidamento della mappa delle dipendenze golden
(`censimento-dipendenze-golden-2026-07-30.md`, #1-#8) + la verifica M21 fatta questa sessione
(`scripts/analyze/derive-m21-exempt-no-golden.py`). Policy operativa per l'anno in cui la scuola
ci passa solo gli INPUT e il golden sparisce (direttiva solo-input, `CLAUDE.md`).

Questa e' una **policy + censimento**, non un refactor. Il refactor di ogni sito e' gated
(TASK-452 nominato #1/#2; #3/#5 qui aggiunti). Fedora al rientro valuta keep/stop.

## Principio (due esiti onesti per ogni riferimento golden-derived)

Per ogni meccanismo che oggi deriva comportamento da una SOLUZIONE (golden):
1. **Deducibile dagli input** -> si cambia sorgente e si verifica la coincidenza ORA
   (finestra aperta finché il golden 2026/27 esiste).
2. **Pratica della scuola non deducibile** -> si CHIEDE e diventa input dichiarato nel
   workbook (TASK-445).

## Cosa SCADE col golden (legge SOLUTION, ha data di scadenza)

Dal censimento #1-#8 (colonne a/b/c verificate 2026-07-30):

| # | Sito | Stato | Deducibile? | Azione no-golden |
|---|------|-------|-------------|------------------|
| 1 | `ConstrainedTeacherAllocator` (TASK-435, **DEFAULT ON, in produzione**) | SOLUZIONE | Parziale. CHI+QUALE-GIORNO si' (T01 solo-giovedi' **confermato input** da maintainer 2026-07-30, H5 VACATION ora autoritativa). Ripartizione (gruppo, giovedi') ore = **no** | Dichiarare ripartizione ore nel workbook se la si vuole fissare |
| 2 | `MonoRoomTagAllocator` (TASK-449, default OFF) | SOLUZIONE | **No.** Le celle (weekday, slot, block) per (gruppo, tag) non sono in nessun input: `LabRotation` e' VUOTO INTENZIONALMENTE nel 2026/27 | Popolare `LabRotation` (il veicolo c'e'): per (gruppo, lab monostanza) giorno+slot+block |
| 3 | M34/M35 (`golden-cadence-reference.json`, TASK-425) | SOLUZIONE | **No.** Cadenza (max/sett, settimane attive) e' pacing, non deducibile dal totale (misurato: max >> media derivabile per la maggior parte delle coppie) | **Condizionale**: se la scuola vuole i capi li dichiara; se no restano inerte. MA oggi il self-gate e' SILENT (vede policy) |
| 5 | Famiglia Q22 in `availability_rules.csv` (6 docenti: T07, T19, T24, T22, T12, T17) | IBRIDO | **No.** Giorno fisso = scelta organizzativa, non load-driven (carico basso). T01 gia' confermato input | Domanda binaria per i 6: giorno/i fisso settimanale, e vincolo reale o scelta organizzativa? |

(#4 `GoldenScheduleImportService` e' lo strumento di import, non debito: in esercizio input-only
non viene mai chiamato.)

## Cosa SOPRAVVIVE senza golden (non legge SOLUTION)

- **#6 M32 template** (`WeeklyTemplatePlanner.buildReference`): deriva da Courses+Timeslots+
  Holidays+StagePatterns. Intatto.
- **#7 Repair** (BoxRepair, HoleRepair, AnchorRepair, SurplusRebalance, ExactFinisher,
  StudentDayCompaction, LabDayConsolidation): operano sullo stato vivo. Intatti.
- **#8 DataGenerator / CalendarioImportService**: input (incarichi, calendari). Intatti.
- **Costruzione** (DayClusteredConstructionPhase, WeeklyTemplateBuilder) + **vincoli
  hard/medium classici** (H1-H29, M15, M20, M22, ...): non leggono golden. Intatti.

## M21 / T20: NON sono letture golden-runtime (verifica questa sessione)

TASK-441 menziona "script di derivazione per soglia M21 e finestre T20, oggi non
riproducibili". Verifica questa sessione (`scripts/analyze/derive-m21-exempt-no-golden.py`):

- **M21_EXEMPT_SUBJECTS** = {Competenze Digitali, Disegno, Disegno Edile ed Impiantistico} e'
  una **costante Java** (`SchoolScheduleConstraintProvider.java:90`), NON una lettura runtime
  del golden. Quindi **non scade col golden**: sopravvive come codice.
- **Non e' derivabile da input**: le 3 materie non condividono una proprieta' coerente di
  `subjects.csv` (Competenze Digitali/Disegno = no-tag+non-prof; Disegno Edile = no-tag+prof).
  Nessuna regola su (roomTags, professionalizzanti) riproduce il set (best rule "no roomTag":
  precision 7,5%, recall 100%; nessuna al 100/100). E' una **scelta della scuola** (quali
  materie ammettono blocchi >2h).
- **La via no-golden e' la colonna dichiarata**, non una derivazione: `08_Materie.capOreGiorno`
  (=0 per esempt, default `sistema`, la scuola conferma). La colonna c'e' gia'.
- **T20** (`T20_EARLY_SUBJECTS`/`_LATE_SUBJECTS` + week 3/4, riga 1274-1277): stessa
  forma. Costante Java, NON golden-runtime. Sequenza pedagogica di un docente, non derivabile.
  Via no-golden: colonne `08_Materie.settimanaInizioMax/Min` (gia' presenti, default sistema).

**Conclusione M21/T20:** "riproducibilita'" qui non significa derivazione dati (impossibile:
scelta della scuola), significa **colonna workbook dichiarata** che sopravvive al cambio anno
(la scuola la rivede sui nuovi subject set). Il debito e' la conferma scuola, non un refactor.

## POLICY operativa per l'anno senza golden

1. **Auto-disable LOUD, non silent.** Il caso da correggere e' #3 (M34/M35): oggi se il
   `golden-cadence-reference.json` manca, M34/M35 si auto-spengono **silenziosamente**
   (mappe vuote, degrado graceful). In un anno senza golden questo e' il comportamento di
   default e **nessuno lo vede**. Policy: quando un riferimento golden-derived self-gate
   inerte per assenza sorgente, il sistema deve DICHIARARLO (banner in
   `ScheduleService.refreshSolverStatics` tipo "cadence caps OFF: no golden reference" + un
   check `99_Validazione` che lo renda visibile). Il degrado graceful va bene; il degrado
   invisibile no. (Azione concreta: gated, task nuovo o estensione TASK-452.)
2. **Ogni riferimento in scadenza: due esiti onesti** (sopra). La verifica "input-only ==
   golden" si fa ORA: fatta per #1 (docente vincolato, censimento), M21 (questa sessione),
   #3 cadenza (censimento). Resta aperto il confronto **end-to-end** input-only vs golden
   (solve source=input vs source=golden sulla batteria KPI): non ancora fatto in questo giro.
3. **Le dichiarazioni necessarie** (colonna c, vanno nel workbook TASK-445 mentre e' in
   costruzione, cosi' la scuola le compila):
   - 6 docenti Q22: giorno/i fisso settimanale (vincolo o scelta?).
   - `LabRotation`: per (gruppo, lab monostanza) giorno+slot+block (#2, sblocca TASK-449).
   - Cadenza (gruppo, materia) max/sett + settimane attive: **condizionale** (#3, solo se la
     scuola vuole i capi M34/M35).
   - M21-exempt / T20: conferma dei default sistema su 08_Materie (capOreGiorno=0,
     settimanaInizioMax/Min).
   - Ripartizione (gruppo, giovedi') ore docente vincolato (#1, se la si vuole fissare).
4. **Survival list** (nessuna azione): #6 M32, #7 repair, #8 seed, costruzione, vincoli
   classici, M21/T20 (costanti + colonne workbook).

## Stato della sessione (per Fedora)

- **TASK-471** done (workbook teoria come input dichiarato): la pratica "no 2h teoria vicine"
  e' ora colonna workbook, complementare alla cura costruttiva di TASK-470.
- **TASK-470** DEFERRED: la dedup e' gia' committed default-OFF; il verdetto 470 raccomanda
  di NON adottarla (lever parziali, 2,6x golden); il suo valore e' bloccato sulla conferma
  referente di 471. Non prematuro.
- **TASK-449** DEFERRED: celle mono-room non derivabili, `LabRotation` vuoto: bloccato sulla
  popolazione del workbook (dichiarazione scuola). Si sblocca quando il workbook raccoglie
  le celle lab.
- **TASK-441** = questa policy + `derive-m21-exempt-no-golden.py`.
- **TASK-460** (ricombinazione orizzontale): prossimo, design doc.
- **Baseline** su questa laptop: vedi handoff (certify laptop come solve host).

## Verifica (evidence, non memory)

- Censimento #1-#8: `censimento-dipendenze-golden-2026-07-30.md` (colonne a/b/c).
- M21 non-derivabilita': `scripts/analyze/derive-m21-exempt-no-golden.py` (run 2026-08-08,
  best rule precision 7,5%).
- M21/T20 come costanti Java: `SchoolScheduleConstraintProvider.java:90,1274-1277`.
- M21/T20 come colonne workbook: `generate_workbook.sheet_08_materie`
  (`capOreGiorno`/`settimanaInizioMax`/`settimanaInizioMin`, origine sistema).
