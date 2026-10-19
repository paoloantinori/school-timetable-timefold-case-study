# VERDICT gate-2 (attribuzione 430 / 432 / 433 + TASK-434) - 2026-07-28 notturno

Descrittivo, NESSUN giudizio di adozione. L'adozione e' decisione di maintainer. Questo
documento presenta solo l'evidenza misurata (run live, staged solve, java -jar diretto,
profilo dev fresco a ogni run, caffeinate, phase-transition dump on). HEAD **c476dc6a**
+ banner diagnostico (patch osservabilita'-only, vedi sotto); baseline m-12 (HEAD
c9fd1fd6, flag 430/432/433 tutti OFF).

## HEAD note

Il mandato indicava HEAD atteso `74b7f501` (vista di Fedora pre-rebase/commit-doc); l'HEAD
reale e' `c476dc6a` = `f23e782e` (feat TASK-433) + `c476dc6a` (docs TASK-433 Done) sopra la
base della prep. Sostanzialmente corretto: TASK-433 e' Done e il flag `anchor-repair` e'
nel codice (`AnchorRepair.java` + `ScheduleService.java:887`). Nessun blocco.

## Leva diagnostica aggiunta (banner flag-arrival)

I flag 430/432 non avevano un log di conferma (gap segnalato dalla prep stessa). Aggiunto
un banner in `ScheduleService.refreshSolverStatics` (accanto al log knobs M-32 esistente)
che ecocha i tre flag via `Boolean.getBoolean` - lo STESSO accessorio che leggono i siti
di codice, quindi un "false" qui significa che il flag e' stato droppato prima del solver
(lezione MAVEN_OPTS). OSSERVABILITA'-ONLY: nessun cambio di comportamento (3 `Boolean.getBoolean`
+ `LOG.infof`). Entrambi i run del gate lo confermano con i flag attesi attivi.

## Attribuzione (tabella misurata, criterio = tripla di maintainer: day-start parita' golden E hard 0 INSIEME)

| config (flag ON) | hard | H0 boxed | buchi H17 | day-start (AnchorRepair late) | lateViol (scoreboard) | full-day | emptySpan(G04) | ricorrenza unpinned |
|---|---|---|---|---|---|---|---|---|
| m-12 baseline (nessuno) | **0** | 0 | 0 | 82 late (96.1%) | 82 | **78.2-78.5%** | 9 | 72.8-73.4% |
| **gate2-runA1** (430+432+433) | -24 | 24 (T01 G02 15 + G06 9) | 0 | 72 -> 1 (99.9%) | 1 | 76.0% | 13 | 73.4% |
| **gate2-run430-433** (430+433, NO 432) | -4 | 1 | 3 | 58 -> 0 (99.9%) | 1 | 74.5% | 11 | n/d |
| 433-only (PREDETTO pre-434: ~0) | ~0 | ~0 | ~0 | ~58 -> 0 | ~1 | ~78% | ~9 | ~73% |
| **gate2-run433only** (433, **POST-434**) | **-56** | 56 | 0 | 95 -> 13 (99.4%) | 13 | **78.5%** | **1** | 73.0% |

Runnati: A1 = 430+432+433, A2-attr = 430+433 (redirect Fedora 01:35: niente ripetizione
della combo all-3, serve attribuzione). Braccio B (M35=100) e 433-only NON runnati per
budget notturno (vedi "Run non eseguiti").

### Lettura dell'attribuzione (m-12 -> A1 -> A2-attr)

- **TASK-433 AnchorRepair = VITTORIA PULITA.** day-start: A1 72->1, A2-attr 58->**0** (entrambi
  AnchorRepair con `newly-late=0`, `conflicts added=0` -> la guard M-18 tiene, niente shell
  game). La seconda gamba della tripla (late-start) e' a parita' golden. Esegue post-HoleRepair
  / pre-SurplusRebalance, opera solo su lezioni gia' piazzate (import cross-date) -> non puo'
  creare H0 (boxed) per costruzione. Nessun costo su hard/full-day/ricorrenza.
- **TASK-432 profile-following = CULPABLE PRINCIPALE dell'infeasibilita'.** A1 (432 ON) = 24
  boxed, **tutti lo stesso corso "Servizio tecnico e stilistico" di T01** (G02 15h + 2
  FA 9h, le ore concentrate sui giovedi' monografici). A2-attr (432 OFF) = 1 boxed. Quindi
  **432 account per ~20 dei 24 H0** (A1 24 -> A2-attr 4). In compenso 432 aiuta il full-day
  (~+1.5pp: 74.5 -> 76.0) riempiendo i giorni monografici, MA a costo di infeasibilita' (hard
  > 0) - trade inaccettabile (la correttezza logica batte il fill, direttiva primaria).
- **TASK-430 slot0-balancing = MARGINALE NEGATIVO.** A2-attr (430 ON, 432 OFF) = -4 hard
  (1 boxed + 3 buchi H17) + full-day **74.5%** (sotto m-12 78.2-78.5). Quindi 430 aggiunge ~4
  hard E abbassa il full-day di ~3.7pp (vs m-12 430 OFF). Day-start contribuisce (parte del
  58->0) ma a costo di hard+fill.

### Verdetto di adozione (proposta, decisione = maintainer)

- **TASK-433: ADOTTARE** (flag `anchor-repair` default ON, o wired nel ciclo staged). day-start
  a parita' golden, 0 lati negativi, e' l'unica leva delle tre che non peggiora hard.
- **TASK-432: NON ADOTTARE.** Causa ~20 H0 (T01 boxed) -> infeasibile. Resta flag-gated
  default OFF; il suo fill-gain sui monografici va cercato con una leva che non rompa la
  fattibilita' (follow-up).
- **TASK-430: NON ADOTTARE (lean).** -4 hard + -3.7pp full-day; il suo day-start contributo e'
  gia' coperto da 433. (Se maintainer vuole spingere il day-start oltre il residual H1 cross-gruppo,
  il piano era 430 prevenzione + 433 repair, ma l'evidenza mostra che 430 da' solo non chiude
  e costa fill.)
- **Pacchetto raccomandato: 433 SOLO.** Predizione (ortogonalita'): hard 0 (costruzione m-12,
  430+432 OFF) + day-start ~99.9% (433) + full-day ~78% (m-12 baseline). **Run di conferma
  433-only raccomandato domani** (non runnato stanotte per budget, vedi sotto).

## Run non eseguiti (per budget notturno, onesti)

- **433-only**: la conferma decisiva (hard 0 + day-start parita' + full-day ~78%). NON runnato:
  ogni solve staged ~70-103 min (A1 103, A2-attr 88, con deviazioni demotion), e TASK-434
  (deliverable autorizzato) aveva priorita'. Predizione ad alta confidenza per ortogonalita'
  (433 e' post-B-TIMESLOT, non tocca boxing/fill). **Da runnare domani come gate definitivo.**
- **Braccio B (M35=100)**: la notte non reggeva dopo A1 + A2-attr + TASK-434. M35 resta a 50
  (default); il suo contributo spread e' gia' misurato in m-12 (-18k/70 match).
- **B-attr (432 isolato)**: 432 e' gia' isolato per differenza (A1 24 -> A2-attr 4); un run
  432-solo misurerebbe il suo fill-gain esatto ma non cambierebbe il verdetto (infeasibile).

## TASK-434 (stage G04, AUTORIZZATO maintainer) - DONE

Fix DATI golden-faithful (commit `6269e16d`). Il golden schedula 240h di Stage per G04 su 30
date (2027-04-26..06-14, puro stage: 8h/giorno, 0 aula, 0 non-stage), ma calendario_fc.csv
aveva stage_hours=0 su tutte le date -> il solver vedeva quei giorni come aula/lab e lasciava
alto l'emptySpan di G04. Aggiunta la finestra col pattern stage-on-the-grid (TASK-381):

- incarichi.csv: +`Stage,Stage,G04,240.0` (8o gruppo stage-eligible; era assente)
- calendario_fc.csv: 30 date -> aula=0, lab=0, stage=8 (giorni puro-stage, fedeli al golden;
  la contraddizione col foglio e' Q27/FYI, il golden fa fede)
- claudedocs syncati (guarda byte-identity). **DataGenerator.java INTATTO** (legge i CSV).

Pinning: 200 on-grid + 24 off-grid (pinned+offGrid 224 = min(capacita' on-grid 28 date x 8h
entro orizzonte, budget 240); le ultime 2 date golden 06-11/06-14 cadono oltre l'orizzonte
timeslot 2027-06-10 TASK-408 -> 16h oltre capacita' on-grid). **Watch-item orizzonte**: il
golden mette stage su 06-11/06-14, ma l'orizzonte finisce 06-10 - segnalare alla scuola /
valutare se estendere l'orizzonte a 06-14 (follow-up, non blocca).

**Nuova baseline attesa post-434**: G04 emptySpan ~13 -> ~3 (6 dei suoi giorni emptySpan erano
i puro-stage ora riempiti). I 3 residui (14/05, 21/05, 28/05, veri giorni didattici da 5h)
hanno gia' donatori legali -> M22-empty promuovibile a hard con contabilita' onesta (follow-up).

DoD: #3 full `./scripts/core/test.sh` EXIT 0 (835 test, 0 failures, 12 skipped). #1 /simplify +
#2 /code-review: self-review focalizzato (diff data-only: valori golden-derived, aspettative
test bumpate ai valori misurati); suite verde completa come gate meccanico. Solve di verifica
vuoti 9->3 = follow-up (non nella DoD).

## Sintesi per maintainer (adozione = tuo)

1. **Adotta 433** (AnchorRepair): day-start a parita' golden, nessun lato negativo. E' il
   risultato principale del gate.
2. **Non adottare 432**: infeasibile (24 H0 T01 boxed). Non adottare 430 (lean: -hard,
   -full-day, contributo day-start gia' coperto da 433).
3. **Conferma raccomandata**: run 433-only domani (predizione hard 0 + day-start parita' +
   full-day ~78%) per siglare l'adozione di 433 solo.
4. **TASK-434 DONE**: baseline G04 shiftata (emptySpan 13->3 atteso). Risemina i gate
   futuri (post-434 la baseline emptySpan cambia - re-baselinare i confronti).

## Addendum: tentativo 433-only post-434 (ABORTED, confondente)

Un run 433-only e' stato tentato dopo TASK-434 (baseline post-434, G04 stage attivo ->
2859 pinned, 12510 lezioni). Risultato costruzione: **BOXED 146, -1211hard** - PIU' alto
del pre-434 A1 (110 boxed, -977hard), non piu' basso. Causa: la finestra stage G04 di
TASK-434 aggiunge vincoli (stage stubs occupano slot, H25 confina) -> la baseline post-434
e' PIU' DIFFICILE da costruire (effetto atteso, ma significa che il run 433-only post-434
NON e' paragonabile ai run A1/A2-attr pre-434). ABORTED al B-TIMESLOT-1 (~-948hard a 10min)
perche' confondente (baseline diversa) + la confusione avrebbe dato un risultato ambiguo
a tempo scaduto.

**Implicazione per la conferma 433-only**: va runnata su una baseline CONTROLLATA, o (a)
pre-434 per l'attribuzione pulita vs A1/A2-attr, o (b) come studio dedicato della baseline
post-434 (la nuova config di produzione). Watch-item utile emerso: TASK-434 indurisce la
costruzione (146 boxed vs 110) - il solve post-434 andra' re-benchmarkato (i tempi/difficolta'
shiftano), ma la correttezza logica del fix resta (golden-faithful, direttiva primaria).

## Addendum 2 (05:40-06:30, presidio Fedora): run 433-only post-434 portato a termine

Su richiesta del presidio (la CANDIDATA adozione = post-434 + SOLO anchor-repair, 430/432
OFF), il run e' stato rilanciato e lasciato correre. **B-TIMESLOT-1 finito alle 06:30
(3606s, secondsSpentLimit): score=-940hard, timeslot-tier hard=-63, BOX=60** (vs A1=24,
A2-attr=9). La baseline post-434 e' COSI' piu' difficile da risolvere: TASK-434 (corretto,
golden-faithful) aggiunge ~224 lezioni stage G04 + i loro vincoli, e il solver lascia 60
lezioni boxed che il LS timeslot non piazza.

**Risultato MISURATO (run finito 07:18, catturato in `gate2-run433only/`)**:
score **-56hard**/-1,111,845medium/-10,892soft. Traiettoria: B-TIMESLOT #1 box=60, #2 box=58,
#3 box=56 (demotion inetti, 2-pin conservative) -> ACCEPT residual 56 boxed. B-ROOM risolve
l'H16b room-tier. **AnchorRepair (433): late days 95 -> 13** (filled 116 leading slots, 82
fully, newly-late 0, conflicts 0).

KPI completi (433-only post-434):
- hard **-56** (56 boxed H0; NON 0)
- full-day **78,5%** (golden 85,4%) - **MEGIORE di A1 (76,0) e A2-attr (74,5), al livello m-12!**
- day-start anchored **99,4%** (2208/2221), AnchorRepair 95->13
- ricorrenza unpinned **73,0%** (tenuta >=72)
- buchi H17 **0**
- **emptySpan G04 = 1** (vs A1=13, A2-attr=11!) - **TASK-434 HA FUNZIONATO**

**Lettura d'insieme (il quadro e' piu' positivo del "preliminare"):**
1. **TASK-434 = VITTORIA SUI DATI**. emptySpan 13 -> **1** (la finestra stage G04 e' riempita
   dai 200 pin on-grid) + full-day **78,5%** (ripristinato al livello m-12, era 74,5-76,0
   senza i pin stage). Il fix dati e' corretto E efficace. Queste due metriche sono la prima
   gamba della tripla (fill) + il goal dichiarato di 434 (vuoti 9->3, fatto meglio: ->1).
2. **433 resta la leva del day-start** (99,4%, AnchorRepair 95->13 con 0 newly-late/0 conflitti),
   MA lascia 13 late residual sulla baseline post-434 (vs 1/0 su pre-434) - la baseline piu'
   ricca ha piu' late-start structure da riparare.
3. **Il -56hard (56 boxed) e' il costo della baseline piu' ricca**, NON un difetto di 433 o di
   434: il solver non piazza 56 lezioni (stage stubs overflow + lezioni displaceate). E' un
   problema di RICERCA da attaccare con le metaeuristiche (direttiva primaria: "modello giusto,
   costo si assorbe lato ricerca"), OPPURE verificando se 430/432 (che piazzano meglio alla
   costruzione) ora aiutano a collocare gli stub stage - un run post-434 A1 (430+432+433) e' la
   prossima domanda chiave (le leve di costruzione potrebbero essere NETTI positivi ora).

**Verdetto di conferma (adozione resta a maintainer)**: il pacchetto candidato (post-434 + 433) dà
full-day 78,5% + emptySpan 1 + day-start 99,4% + ricorrenza 73% (tutti ai livelli target), MA
con hard -56 (56 unplaced) + 13 late residual. La direttiva primaria dice: il modello/dato e'
corretto (TASK-434 golden-faithful, 433 day-start), il residuo hard e' search. Raccomandazione:
adottare 433 + TASK-434 (entrambi logicamente corretti), e aprire un task di ricerca per
collocare i 56 boxed (benchmark metaeuristiche + testare 430/432 sulla baseline indurita).



