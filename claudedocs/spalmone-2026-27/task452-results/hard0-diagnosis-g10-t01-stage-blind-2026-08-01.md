# DIAGNOSI HARD-0 gate m-13: 3 unassigned G10 / T01 (read-only, no new run)

> zai, 2026-08-01 sera. Mandato: diagnosi read-only del bloccante hard-0 del gate m-13
> (TASK-452 flip source=input). Perche' run3 source=input lascia 3 ore di T01 / G10 /
> Servizio tecnico e stilistico senza cella? Deficit aritmetico (le celle non bastano, cura nel
> PROFILO) o di ricerca (le celle ci sono ma LS/repair non le raggiungono)? Catena causale
> link-by-link, poi proposta di cura nel perimetro TASK-435/452. NESSUN nuovo run, nessun codice:
> solo raw run3 + seed + DB.

## ESITO (verdetto dai raw, CONFIRMED end-to-end)

**Deficit ARITMETICO a livello di PROFILO.** Sotto il profilo input-derived, per le 3 ore non
esiste alcuna cella legale: ogni giovedi non-stage ha T01 satura al suo H7 cap (8h), e ogni
giovedi di stage ha G10 fuori griglia. NON e' un deficit di ricerca: la LS ha collocato tutto
il collocabile (62h su 65, saturando T01 a 228h sulle 29 celle raggiungibili). La cura e'
nel PROFILO (TASK-435/452), non nel solver.

**Root cause diretta:** `buildInputDerivedProfile` e' **stage-blind**. Legge `openSlots` dal
GroupCalendarRule SETTIMANALE (G10 giovedi [0,7] = 8 slot, costante), ignora il calendario
per-data. Quindi "brucia" 49h del budget di G10 sui suoi stessi giovedi di stage (Mar 4/11/18,
Apr 8/15/22 = 0 slot reali; Apr 1 = 4 slot reali), dove il pin-loop poi non puo' piazzare nulla.
Combinata con l'**ordine alfabetico** del greedy (G02 e G06 consumano i primi 20 giovedi
Sep-Feb), G10 inizia il suo turno solo a Feb 18 e finisce la sua finestra proprio dentro il suo
stage Mar-Apr.

## SINTOMA (grounded dai raw run3)

- score run3 = `-6hard` = 3x H0 Unassigned (`analysis.json`: H0 matches=3; `score.txt`).
- I 3 unassigned sono identici: teacher id 18 (T01), studentGroup id 10 (**G10**), subject
  "Servizio tecnico e stilistico", course 186 (totalHours 65, isLab, tag acconciatura),
  **pinned=false, timeslot=null** (export.json.gz, 3 record su 12494).
- Confermato `count(*) FROM lesson WHERE timeslot IS NULL` = 3 (VERDICT run3).

## CATENA CAUSALE (7 link, ognuno grounded)

1. **T01 solo-giovedi (H5).** Teacher id 18, 4 corsi Servizio tecnico e stilistico su
   1/2/G10 + G12. Budget totale 231h (60/60/65/46, da `course` via `db-query.sh`).
   H5 = full-day VACATION Mon/Tue/Wed/Fri (Q22).
2. **`buildInputDerivedProfile` = greedy disgiunto, alpha-ordinato, stage-blind.** Codice
   `ConstrainedTeacherAllocator.java:449-493`: un gruppo per data, h = min(h7Cap=8, remaining,
   openSlots). `openSlots` viene dal GroupCalendarRule **settimanale** (commento al codice:
   "the open counts are passed in from the REAL GroupCalendarRule rows"; il commento stesso
   ammette che il PLACEMENT reale e' demandato al pin-loop che legge il calendario vero).
3. **Ordine alfabetico -> G02 e G06 mangiano Sep-Feb.** Riproduzione Python fedele (36
   giovedi scolastici da DB, openSlots settimanali): G02 target indici 0-9 (Sep 17-Nov 19),
   G06 10-19 (Nov 26-Feb 11). G10 inizia a indice 20 (Feb 18). **Match esatto coi pin run3**
   (G02 60h su Sep-Nov, G06 60h su Nov-Feb).
4. **Stage G10 = Mar 4 .. Apr 22.** `dailyactivityquota` (EXTRACT(DOW)=4): Mar 4/11/18/25 e
   Apr 8/15/22 stage=8 (full, 0 slot aula/lab); **Apr 1 stage=4** (parziale, 4 slot regolari);
   fine stage Apr 29. (Mar 25 e' chiusura = Holy Thursday, non e' un giovedi scolastico.)
5. **Il profilo brucia 49h di G10 sui suoi stage.** Riproduzione Python: il profilo targetta
   G10 su Feb 18(8), Feb 25(8), Mar 4(8), Mar 11(8), Mar 18(8), Apr 1(8), Apr 8(8), Apr 15(8),
   Apr 22(1) = 65h. Di questi, **49h su giovedi di stage** (24 full + 8 Apr1 + 16 full + 1) dove
   il calendario reale ha 0 (o 4) slot. Solo **20h sono piazzabili** (Feb 18:8, Feb 25:8,
   Apr 1:4). Il pin-loop (HybridPhase1aService, calendario reale) piazza esattamente 20h; i 45h
   residuali diventano lezioni unpinned. **Match esatto: preassign-pin G10 run3 = 20h** sulle
   3 date Feb 18/25/Apr 1.
6. **La LS satura T01 sulle residue non-stage.** Carico T01 per giovedi (export run3):
   20 giovedi Sep-Feb a 8h ciascuno (G02 6h + G10 2h, oppure G06 6h + G10 2h), Feb 18/25
   a 8h (G10), **Apr 1 a 4h** (G10, partial), 5 giovedi Apr 29-May 27 a 8h (G12), Giu 3 a
   8h (G12 6h + G10 2h). Totale = **228h = 28 x 8h + 4h (Apr 1)**. La LS ha riempito i 2h di
   slack di T01 sui giovedi 1/G06 con G10 (40h) + Giu 3 (2h) = 42h dei 45 residuali.
7. **Le ultime 3h non hanno cella.** Non-stage: T01 a H7 cap (8h) su tutte le 29 celle
   raggiungibili (228h piazzate su 28x8+4). Stage (Mar 4/11/18, Apr 8/15/22): G10 fuori griglia
   (H25 stage confinement + H26 no-session + H23 lab-quota-0 per lezioni unpinned). Giu 10:
   G10 **no-session** (nessuna riga `dailyactivityquota` per G10 il 10/06, H26). -> hard -6.

Smentita della ricerca: la LS ha collocato 42h su 45 residuali riempiendo ogni 2h-di-slack di
T01. Lo slack residuo (56h = 7 giovedi di stage x 8h) giace inutilizzato perche' G10 e'
fisicamente a stage. Non e' un fallimento di raggiungimento: le celle proprio non ci sono, sotto
questa distribuzione di pin.

## FEASIBILITY: una distribuzione fattibile ESISTE (il golden la dimostra)

Il golden e' una soluzione completa e valida, quindi una distribuzione disgiunta fattibile per
T01 esiste. Distribuzione golden (seed/lessons.csv, 35 giovedi distinti):
- G02 10 giovedi (Ott 29 -> Giu 10), G06 10 (Sep 24 -> Feb 25), **G10 9 (Dec 3 -> Giu 3)**,
  G12 6 (Apr 8 -> May 13).
- Chiave: il golden **intreccia** i giovedi tra i gruppi lungo tutto l'anno. I 9 giovedi di G10
  (Dec 3 -> Giu 3) EVITANO il suo stage Mar-Apr (sono sparsi in Dic, Gen, Feb, fine-Apr, Mag, Giu).

L'input-derived alpha-greedy NON puo' produrre cio': front-loada 1/G06 su Sep-Feb, lasciando a
G10 solo Feb-Giu, dove il suo stage rimuove Mar-Apr. Le celle ci sono in aggregato (golden =
35 giovedi fattibili su 36), ma la distribuzione del profilo input le rende indisponibili a G10.

> Nota AC#3 TASK-476 (no golden per la diagnosi): il VERDETTO (aritmetico, meccanismo
> stage-blind) e' derivato dai soli input (incarichi, GroupCalendarRule, DailyActivityQuota, H5) +
> raw run3, senza il golden. Il golden e' usato solo per (a) dimostrare che una distribuzione
> fattibile esiste e (b) come riferimento del come. Onesto: il budget incarichi e' circolare
> (TASK-371 normalizzo' al golden), quindi la feasibility "231h su 35 giovedi" non e' prova
> indipendente; la struttura della catena causale pero' si (punti 1-7).

## PROPOSTA DI CURA (perimetro TASK-435/452, profilo input-derived, SENZA leggere il golden)

Il difetto e' doppio: (a) **stage-blindness** (openSlots settimanale brucia budget sui giorni di
stage), (b) **alpha-front-load** (nessun coordinamento cross-gruppo, morde i gruppi tardi il cui
stage rimuove la finestra assegnata). Cura a stadi, entrambe input-derived (leggono calendari
per-data + budget, mai il golden):

**Stadio 1 (necessario, piccolo, ~20 righe + plumbing mappa statica): rendere `buildInputDerivedProfile` consapevole dello stage per-data.** Passare a `openSlotsByGroupDate`
i conteggi REALI per-(gruppo, data) che includono stage/no-session (il pin-loop li legge gia';
il builder no). Effetto: il budget di G10 non si brucia piu' su Mar 4-Apri 22, fluisce sui suoi
giovedi non-stage. FERMA l'emorragia ma NON sufficiente da sola: G10 e G12 competerebbero
sui tardivi Apr 29-Giu (alpha-greedy darebbe G10 a G12, spostando il deficit G10 -> G12).

**Stadio 2 (sufficiente, piu' grande, ~40-60 righe): assegnazione bilanciata/interleaved.**
Sostituire il greedy alpha-sequenziale con uno che assegni ciascun giovedi al gruppo fattibile col
maggior rapporto residual/capacita'-non-stage, rispettando disgiunzione + H7. Replica la struttura
del golden (intreccio lungo l'anno) partendo pero' dai soli input (calendari di stage + budget).

**Verifica:** estendere `ConstrainedTeacherInputSourceRealSeedTest` ad assertire che OGNI pin
target e' su una data non-stage del gruppo (il test oggi cristallizza solo i totali per-gruppo +
delta date; la nuova AC = nessun target su data stage). Stadio 1 coperto da questo test; Stadio 2
richiede AC aggiuntiva sul bilanciamento.

**Stima e decisione:** Stadio 1 e' piccolo e chiaramente corretto (ferma il budget-burning), ma
NON chiude hard-0 da solo (composizione con G12). Stadio 2 e' la cura vera ma non-banale
(tocca placement + interazione con H7/disgiunzione, rischia di non tenere in LS come M-TR). Nessuno
dei due e' "piccolo + coperto dal test di equivalenza esistente" -> **restano PROPOSTA con stima**,
nessun prototipo in questa sessione. Prototipare Stadio 1 richiede il flip temporaneo del default
o un run source=input post-cura per misurare se sposta o sposta-e-rialloca il deficit (gate = hard
0). Da approvare con maintainer come prossimo step m-13, dopo la ratifica M-CPS.

## RIFERIMENTI

- Raw: `task452-results/verify-source-input-run3/{export.json.gz, analysis.json, score.txt}`.
- Codice: `ConstrainedTeacherAllocator.java:449-493` (buildInputDerivedProfile), `:110-190`
  (plan), `HybridPhase1aService.indexOpenSlotsByGroupDate` (pin-loop calendario reale).
- Seed: `src/main/resources/seed/lessons.csv` (golden), `dailyactivityquota`/`course`/
  `groupcalendarrule` (DB, profilo production).
- Task: TASK-452 commento #5 (gate di parita', AC = questa diagnosi), TASK-476 AC#2 (solver-side
  vs input-nascosto: verdict = input-side, profilo stage-blind).
