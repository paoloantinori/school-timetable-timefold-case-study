# Tab "Vincoli*" del golden xlsx: estrazione + prima classificazione (2026-07-31 sera)

Scoperta di maintainer: il file `Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx` contiene tre tab
di vincoli in testo destrutturato: **Vincoli Classi**, **Vincoli Docenti**, **Vincoli DA
ANALIZZARE**. Estrazione integrale in `vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt`.
Molte voci coincidono col docx del TASK-368/369/370 (probabile stessa fonte); la verifica
riga-per-riga contro `seed/availability_rules.csv`, le tabelle vincoli di CLAUDE.md e
`availability-not-yet-modeled.md` e' IN CODA (zai). Questa e' la prima classificazione di
Fable, da confermare.

## Bucket A: GIA' MODELLATO (spot-check positivo, verifica di dettaglio in coda)
- T04 8-13(14) + venerdi' libero -> availability_rules.csv (TASK-368).
- T05 mer/gio/lun 8-14, venerdi' libero, martedi' -> rules + M25 (2 giorni liberi).
- T11 9-14 sempre + un giorno alle 8 -> rules + M24 (T11=1).
- T10 giovedi' mattina libero (LES/G14) -> rules THURSDAY 0-3 (derived-from-golden, da confermare).
- T12 assenze datate 14/09, 16/11, 23/11 + mattine -> rules (TASK-374) + Q13.
- T16 martedi' dalle 11 -> rules; seconda mattina = Q15 aperta.
- T24 equipe martedi' mattina -> rules PREFERENCE.
- "8 ore T28 (T18)" -> H7 per-teacher maxDailyHours (T01 8, TASK-348).
- "Non piu' di 6 ore al giorno per docente" -> H7 default 6.
- "G14 il giovedi'" -> input calendario (G14 2) + pratica nota.
- Sicurezza prima dello stage (3e + 2 OIT) -> M26.
- T20 "subito merceologia in 1a e 2a", "g10 tecnologia/merceologia ultime" -> M27 (parziale: "ultime" e' piu' forte di "non prima della settimana 4").
- Stage G07 23/03-28/05, G11 13/01-19/03 (4gg/sett, rientro lunedi'), G12 20/11-17/04 -> StagePattern + DailyActivityQuota (verificare date esatte vs seed).
- Prof. G12 ai bordi con salto prime/ultime 2 ore -> M29 (MA vedi discrepanza in bucket C).

## Bucket B: PARZIALE / DA VERIFICARE
- T03 "libero le mattine in cui c'e' lab termo, senza eccezioni" -> M-LA (assistant termoidraulica) copre il conflitto, ma "libero SEMPRE quando c'e' termo" e' piu' forte: verificare copertura.
- T16 "3 ore consecutive in R16 NON 6" -> labBlockSize? verificare il block size dei corsi R16.
- T14 doppio 2a/3a MODA R16 (100+100 ore), T20 idem (100+85) -> doppio/M-LA seeds esistono; i BUDGET ORARI del doppio non sono modellati.
- T06 "non 1 ora info + 1 ora gest, ma stessa materia" -> M15/S17 spingono i blocchi same-subject; regola per-docente non esplicita.
- "Laboratorio termo le prime 4 ore" (+ "non le prime 4 con teoria dopo", "ultima ora T03") -> non modellato esplicitamente; verificare se la pratica emerge dai pin/quote.
- Idraulici 1/2/3 OIT (piani giorni-lab con conteggi per idraulico, T22 16 lunedi', 6a ora lunedi' 13-14 aggiunta da referente) -> in parte nei fact/quote; i conteggi per-docente NO.
- "2 giorni da 5 ore prep esame a giugno" (3 OIT) -> concetto "esame" non modellato; verificare se le quote di giugno lo contengono.
- T20 "figurino quando vuoi (non necessariamente 2 ore)" -> eccezione M21/S17? probabilmente innocua, verificare whitelist.

## Bucket C: NON MODELLATO o IN CONTRADDIZIONE (i piu' importanti)
1. **"ORE DI SICUREZZA NELLE 1e TUTTE IN 2 GG CUMULATIVE"** (+ "2 gg di sicurezza 4 ore con le 1e"):
   concentrazione della Sicurezza dei primi anni in 2 giorni cumulativi. NON modellato (M26 copre
   solo il "prima dello stage" delle terze).
2. **T21 "per T30: non le prime ore in G08 e 4 FA; ore di mezzo nella giornata"**:
   posizionamento mid-day per-docente-per-gruppo. NON modellato.
3. **T11 "con G08, 3 OIT e G10: 6 ore dopo stage per curriculum (anche 2 consecutive)"**:
   ore-curriculum post-stage per gruppi specifici. NON modellato (famiglia M5 ma diverso).
4. **"Non ci devono essere 2 ore vicine della stessa materia teorica"** (lab esclusi):
   DECISIONE maintainer (31/07 sera): si modella come vincolo **MEDIUM** a residuo calibrato sul
   golden (pattern M24/M25), non hard e non solo-notifica. Tracciato in **TASK-468** (misura
   residuo golden -> design con risoluzione interazioni M15/S17/M21 -> prototipo flag OFF -> A/B).
   La classificazione precedente ("solo notifica") e' superata.
5. **"T20: inizia materia e finiscila"** (serializzazione delle materie): NON modellato come
   regola generale (M27 copre solo le finestre di inizio).
6. **Discrepanza M29**: la nostra tabella dice G12 prof. mercoledi'+giovedi'; il tab Vincoli
   dice "Professionalizzanti SOLO il giovedi' e il venerdi'" (nel periodo dal 19/11), con la
   regola mar/mer a bordi. Verificare la fonte della nostra tabella e allineare o notificare.
7. **G10 "stage parru NO LUNEDI SI SABATO"**: il sabato e' fuori griglia nostra (off-grid);
   verificare che StagePattern/off-grid contabilizzi il sabato di stage e il no-lunedi'.

## Prossimi passi (in coda a zai)
Matching sistematico riga-per-riga (estratto vs availability_rules.csv + CLAUDE.md +
availability-not-yet-modeled.md), conferma/smentita dei bucket, gap table finale; i punti del
bucket C diventano candidati task/domande referente secondo il criterio golden-first (se la pratica
golden gia' li rispetta, sono gratis; se li viola, si notifica invece di modellare).

---

## VERDETTO FINALE - matching riga-per-riga (zai, 2026-07-31 notte)

Conferma/smentita dei bucket A/B/C contro `seed/availability_rules.csv` (letto direttamente).
La classificazione di Fable regge; qui sotto gli spot-check verificati e le correzioni.

### Bucket A - GIA' MODELLATO (CONFERMATO, con 2 nuance)
Tutti gli spot-check reggono contro le regole:
- T04 8-14 Lun-Gio + pomeriggio negato + Ven full-day: `availability_rules.csv` righe 2-6. OK.
- T05 8-14 Lun-Gio + Ven: righe 7-11. **NUANCE**: il tab dice anche "MARTEDI 14:30-16:30"
  (pomeriggio disponibile); la regola lo ha **DROPPATO** (note riga 8: "drops the old
  Tue-afternoon-available asymmetry", uniformizzato Lun-Gio). Scelta nostra documentata, non
  un gap.
- T11 9-14 + un giorno alle 8 (M24): righe 12-16 (slot 6-7 vacation, slot 0 sbloccato, M24 cap). OK.
- T10 gio mattina libero: riga 17 (THU 0-3, derived-from-golden, "da confermare referente" Q1). OK con flag.
- T12 assenze 14/09, 16/11, 23/11 + mattine: righe 18-22 (TASK-374). OK.
- T16 mar dalle 11: riga 23 (TUE 0-2). OK.
- T24 equipe mar mattina: riga 24 (TUE 0-5 PREFERENCE). OK.
- T01 solo giovedi': righe 25-28 (Q22 golden-derived, 231/231h; **verificato di nuovo
  indipendentemente in task467-results/estrazione-rotazioni-2026-07-31.md**). OK.
- "8 ore T28 (T18)" -> H7 (TASK-348, maxDailyHours). **NUANCE di naming**: il tab dice
  "T28", ma il docente mono-giovedi' con 8h/giorno oggi e' **T01** (T28 era il docente
  mono-giorno del modello di febbraio, commit `0ceefaf3`). Stesso vincolo, nome stantio nel tab.
- "max 6h/giorno per docente" -> H7 default 6. OK.
- G14 il giovedi' -> calendario + pratica. OK.
- Sicurezza prima stage -> M26. OK.
- T20 "subito merceologia 1a/2a", "g10 tech/merceologia ultime" -> M27 (parziale: "ultime"
  piu' forte di "non prima settimana 4"). **confermato parziale** (bucket B borderline, vedi sotto).
- Stage G07/G11/G12 -> StagePattern + DailyActivityQuota. OK (date da verificare vs seed).
- G12 prof. ai bordi -> M29. **MA vedi discrepanza C6/T1**: la nostra tabella M29 dice G12
  prof **mer+gio**, il tab Vincoli dice "Professionalizzanti SOLO gio+ven" (dal 19/11). Contraddizione
  tra i fogli della scuola -> diventa T1 nel doc referente.

I 6 docenti a giorno fisso golden-derived (T19, T24, T22, T12, T17, T07,
righe 29-43) sono MODELLATI come VACATION ma **golden-derived "da confermare"** (Q22): sono
esattamente l'oggetto di **Q4** del doc domande ("vero vincolo o preferenza organizzativa?").
Quindi stanno in A (modellati) con un flag di conferma referente aperto.

### Bucket B - PARZIALE (confermato, resta da verificare la copertura)
Tutti confermati come parziali, nessuna smentita:
- T03 "libero mattine con lab termo, senza eccezioni" -> M-LA copre il conflitto ma "sempre"
  e' piu' forte: **da verificare** copertura (resta aperto).
- T16 "3 ore consecutive in R16 NON 6" -> labBlockSize non seedato per 2026/27
  (DataGenerator riga 246: "defaults to 0, individual 1-hour lessons"). **Gap reale** ma non
  bloccante: la forma a blocchi emerge dai pin/quote. Tracciato.
- T14/T20 doppio MODA: i seed doppio/M-LA esistono; i budget orari del doppio non
  modellati (non serve, le ore del doppio sono gia' nei corsi).
- T06 "non 1h info + 1h gest ma stessa materia" -> M15/S17 spingono i blocchi; regola
  per-docente non esplicita. Diventa nota in TASK-468 (interazione con "no 2 ore vicine teoria").
- Lab termo prime 4 ore / Idraulici / "2 giorni da 5h prep esame giugno" -> in parte nei fact;
  conteggi per-docente no. Tracciato (non bloccante).
- T20 "figurino quando vuoi" -> probabile eccezione M21/S17 innocua (figurino non e' in
  M21_EXEMPT; da verificare in TASK-468 AC#2).

### Bucket C - NON MODELLATO / CONTRADDIZIONE (mappato a doc referente + TASK-468)
Rispetto al criterio golden-first (golden rispetta -> gratis / golden viola -> notifica o
medium calibrato), il bucket C si risolve cosi':
- **C4 "no 2 ore vicine teoria"**: MISURATO (TASK-468 AC#1, questa notte). Golden viola 432x
  (teoria stretta) -> **MEDIUM calibrato**, deciso da maintainer, tracciato. Non e' piu un gap aperto.
- **C6 discrepanza M29 (G12 prof mer+gio vs gio+ven)**: contraddizione tra i fogli scuola ->
  **T1 doc referente** (quale fa fede).
- **C1 Sicurezza 1° in 2 gg cumulativi**: non modellato -> **T5 doc referente** (prassi da modellare?).
- **C2 T21 "T30 ore di mezzo"**: non modellato -> **T3 doc referente**.
- **C3 T11 "6h post-stage curriculum"**: non modellato -> **T4 doc referente**.
- **C5 "T20 inizia materia e finiscila"**: il golden NON la segue -> **T2 doc referente**
  (desiderio non realizzato o solo alcune materie?).
- **C7 G10 "stage parru NO LUNEDI SI SABATO"**: MISURATO. Golden rispetta (0 stage di lunedi'
  su 300h, deliberato: fuori-stage il lunedi' e' giorno normale per G10). Sabato off-grid,
  gia' contabilizzato. **Quasi-gratis ma basso impatto** (Stage e' gia' date-pinnato via H25 +
  filtro VRP, quindi "no-lunedi" e' quasi-redundant). -> **T7 doc referente** (policy o calendario?).

### Gap table finale (esito del matching)
| voce | stato | dove |
|---|---|---|
| disponibilita' docenti (A) | MODELLATO (con Q4/Q1 da confermare referente) | availability_rules.csv |
| M29 G12 prof giorni | CONTRADDIZIONE fogli -> T1 referente | CLAUDE.md M29 vs tab Vincoli |
| no 2 ore vicine teoria (C4) | MISURATO -> TASK-468 MEDIUM calibrato | task468-results/AC1-*.md |
| T20 serializzazione (C5) | NON modellato (golden non segue) -> T2 referente | - |
| T21 ore-mezzo (C2) | NON modellato -> T3 referente | - |
| T11 post-stage curriculum (C3) | NON modellato -> T4 referente | - |
| Sicurezza 1° 2gg cumulativi (C1) | NON modellato -> T5 referente | - |
| T16 3h R16 (B) | labBlockSize non seedato (gap non bloccante) | DataGenerator:246 |
| T03 sempre-termo (B) | M-LA parziale, copertura da verificare | - |

Tutto il bucket C vivo e' ora misurato o mappato a una domanda referente (T1-T7, doc
`domande-rotazione-e-tensioni-referente-2026-07-31.md`). **Le misure golden di C1/C2/C3/C7 + termo
(B) sono in `misure-golden-bucket-c-2026-07-31.md`** (verdetto per ciascuna: rispettata ->
quasi-gratis, violata -> notifica). Sintesi: C1 Sicurezza VIOLATA (8h/8sett, non 2gg), C2
T21 VIOLATA (26/42h a prime ore), C3 T11 RISPETTATA su G10 (6h/2-consec), C7 stage
no-lunedi RISPETTATA (quasi-gratis ma basso impatto), termo PARZIALE (75% in 8-12). Nessun
nuovo prototipo di vincolo parte da qui senza risposta referente, tranne TASK-468 (C4) che ha gia'
il via di maintainer.

---

## AGGIORNAMENTO BUCKET B (zai, 2026-08-01) - misurato sul golden, riga per riga

Il bucket B (PARZIALE/DA VERIFICARE) e' ora MISURATO sul golden, stesso schema del bucket C.
Dettaglio integrale: `misure-golden-bucket-b-2026-08-01.md`. Sintesi dell'esito per voce:

| voce | esito | azione |
|---|---|---|
| T03 libero-con-termo (i) | **GIÀ COPERTA** | 0 conflitto; M-LA+H1. T03 insegna lui il termo (135) o e' libero negli altri lab. |
| T16 3h R16 NON 6 (ii) | **TENSIONE** | Golden ha 4x6h (G05 apr/mag) + 6x5h (G14). -> **T9 referente**. NON cap 3h (vieterebbe il golden). |
| T14/T20 doppio MODA (iii) | **TENSIONE** | T14=Italiano, T20=materie MODA; nessuno e' teacher-of-record di R16. -> **T10 referente** (chi e' il doppio?). |
| T06 info+gest (iv) | **GIÀ COPERTA / rispettata** | 0/509 giorni di T06 con entrambe; alias M-TR inerte. T8 bullet aggiornato. |
| Idraulici T22 6a ora lun (v) | **PRATICA CONFERMATA** | 20 lun@13:00 nel golden; 21 lun vs 16 del tab. Emergente da pin/quote, non vincolo. |
| G11 2x5h giugno (vi) | **PATTERN PRESENTE** | 2 giorni da 5h (7-8 giugno, termo). "esame" non nel seed; conferma referente. |
| T20 Figurino (vii) | **GIÀ COPERTA + feed 471** | Figurino 92% a 1h/day; "quando vuoi" rispettato; conferma Figurino=teoria (M-TR/471). |

**Netto:** 3 gia' coperte/rispettate (i, iv, vii), 2 pratiche confermate-non-modellate (v, vi),
2 tensioni nuove -> **T9** (T16 6h) e **T10** (doppio R16) nel doc referente. Nessun nuovo
prototipo di vincolo parte dal bucket B (golden-first: le 2 tensioni sono pratiche del golden da
confermare, non divieti da modellare). Figurino (vii) e' dato diretto per il workbook TASK-471.


