# TASK-609 gate2: CompositeSwapFinisher alla posizione riposizionata (A/B due bracci)

Nottata 14/09/2026, laptop (mandato notturno 13/09 sera, Fedora). Secondo gate a ciclo reale del
pass `CompositeSwapFinisher` (flag `planner.construction.composite-swap-finisher.enabled`,
DEFAULT OFF), questa volta alla posizione RIPPOSIZIONATA dal commit 93c1aa97: pre-save del
wrapper multi-start, post corner-b refinement (il punto che il banco replay ha provato:
m39-off -11->-8, m39-on -13->-10). Il gate1 (bf3b2fda) aveva mostrato che alla vecchia
posizione (tail chain) il pass vedeva targets=0 SEMPRE perche' l'ora boxed nasceva a valle,
nella rifinitura di consegna.

## Protocollo

- JAR da HEAD `93c1aa97`, PREFLIGHT-OK su entrambi i boot (appjar 21:39:22 = HEAD; braccio B
  anche `flags-verified` sul flag della riga java).
- **Doppia guardia 2/2 per braccio**: (A) lsof cwd = `/Users/user/planner`, comando java
  SENZA flag, banner `enabled=false` alle 21:41:22, solve alle 21:41:40; (B) cwd ok, comando
  java CON `-Dplanner.construction.composite-swap-finisher.enabled=true`, banner
  `enabled=true` alle 01:10:09, solve alle 01:10:26.
- Profili freschi `t609c` / `t609d` (seed DataGenerator verificato nei log di entrambi i
  boot), ciclo completo staged default (multi-start maxAttempts=5, budgetSeconds=0,
  maxDiscards=32), un solve alla volta, caffeinate attivo.
- Guardia TASK-541 al boot A: container `planner-pg` stale sul volume t609b (braccio B del
  gate1, ore della notte precedente); fermato e rimosso come da istruzioni della guard, una
  volta sola. Al boot B la stessa guardia ha guidato lo switch di profilo t609c -> t609d
  (procedura standard di cambio profilo, non un incidente).
- Wall: **A 3h24m** (21:41:40 -> 01:05:12; tentativi 1 e 2 CORSI PER INTERO, tentativo 3
  bruciato nei 32 scarti del predictor), **B 1h53m** (01:10:26 -> 03:02:53; tentativo 1
  intero, tentativo 2 bruciato nei 32 scarti). Vincitore tentativo 1 in entrambe le braccia,
  consegnato con rifinitura stanze corner-b (~10 min).

## Risultati

| | A (flag OFF, controllo) | B (flag ON, trattamento) |
|---|---|---|
| hard consegna | **-5** | **-8** |
| H34 empty declared day | -3 (3 match) | -2 (2 match) |
| H32 day-fill deficit | -2 (2 match) | -1 (1 match) |
| H17 vertically compact | 0 | -4 (4 match) |
| H7 max teacher daily hours | 0 | -1 (1 match) |
| **H0 unassigned (boxed)** | **0** | **0** |
| full-day | 93,7% | 94,2% |
| buchi interni | 1 gg / 4 slot | 4 gg / 4 slot |
| no-stanza | 0 | 0 |
| sanity (TASK-535) | NC: 4 gruppi (G07, G08, G09, G10) | NC: 6 gruppi (G07, G09, G10, G11, G12, G13) |
| concordance TASK-526 (hard mem vs db) | OK (-5 = -5; H29 mem=0 db=0) | OK (-8 = -8; H29 mem=0 db=0) |
| medium mem vs db | -1.114.715 vs -583.497 (residuo noto TASK-509) | -1.088.947 vs -579.695 (residuo noto TASK-509) |
| riga counter del pass | ASSENTE (solo banner enabled=false) | **1 riga: `targets=0, relay2=0, relay3=0, direct=0`** |
| esecuzioni del pass | 0 | **1** (03:00:09,261: post rifinitura di consegna, pre-save/restore 03:00:09,539) |

Raw: `arm-a-default-off/` e `arm-b-flag-on/` (capture canonica completa + `planner-prod.log`
per braccio; export 106MB+ per braccio NON committati, restano sul laptop come da limite GitHub).

## Predizione pre-registrata vs esito

1. **"la riga counter appare nel log, DOPO la rifinitura di consegna; in A e' assente"**:
   VERIFICATA nel meccanismo e SMENTITA nel contenuto. La riga c'e', esattamente una, nel
   punto giusto (03:00:09,261, dentro il wrapper: la rifinitura era partita alle 02:50:01 e la
   LS era gia' convergenza a -8 dalle 02:56:46; il restore/save arriva 278ms dopo la riga del
   pass). Ma il contenuto e' **targets=0**, non targets>=1. In A la riga e' assente (0
   occorrenze; il flag-off blocca la call).
2. **"B consegna 2..3 hard migliore di A, delta attribuibile (counter > 0, H0 chiuso in B)"**:
   SMENTITA. B consegna -8 CONTRO -5 di A, con counter tutto a zero e H0=0 in ENTRAMBE le
   braccia: non c'e' nessun delta da attribuire, il pass non ha mosso nulla.
3. **"nessuna nuova famiglia hard in B"**: FALSA alla lettera (H17 -4 e H7 -1 presenti solo
   in B) MA meccanicamente NON attribuibile al pass: targets=0 = zero mosse = nessun canale
   causale (stesso alibi contatore-zero del gate1). Le famiglie del disavanzo (H17, H7) sono
   fuori dalla classe bersaglio del pass (le boxed H0).
4. **"timing trascurabile"**: VERIFICATA (una sola esecuzione, istantanea a targets=0).

## Criteri di morte

- **"targets=0 ANCORA nel braccio B: il riposizionamento non ha risolto la cecita'"**:
  **SCATTA**. Fermato e documentato, nessun rilancio (come da mandato).
- "B peggiore di A con counter > 0": NON scatta. B e' peggiore (-8 vs -5) ma con counter=0:
  il pass non ha mosso e non puo' aver peggiorato nulla. Il -3 e' composizione di bacino
  (H17/H7 presenti solo in B, H34/H32 piu' profondi in A), la stessa variabilita' LS/room
  non-byte-exact documentata dalla ratifica TASK-543 e gia' vista nel gate1 (+2 nell'altra
  direzione).
- "REVERTED del pass con delta visibile": NON scatta (zero righe di revert composito in B; i
  REVERTED nel log sono tutti di pass sibling: hole repair, one-off finisher, surplus
  rebalance, comportamento ordinario).

## Diagnosi: dove nasce il boxed in QUESTO ciclo

**In nessun punto.** H0=0 dal principio alla consegna in ENTRAMBE le braccia. La cecita' del
gate1 era un errore di FASE (il boxed nasceva a valle del punto wired); stanotte il pass gira
nel punto giusto, sullo stato consegnato (post rifinitura, pre-save, verificato dai timestamp
e dal wiring 93c1aa97), e quello stato semplicemente NON CONTIENE nessuna ora boxed. Il
fenomeno-target del pass e' un evento RARO dipendente dal bacino, non un fenomeno sistematico:
sui 4 profili freschi dei due gate (t609a/b/c/d) solo UNA consegna conteneva una boxed (gate1
braccio A, nata a valle della rifinitura: esattamente il punto dove il pass ora gira). La
domanda operativa si sposta quindi da "dove nasce il boxed" a "quanto e' frequente una
consegna con boxed": nella distribuzione attuale dei bacini fresh-profile e' 1/4, e quando
c'e' nasce proprio nella finestra che il pass ora copre.

## Verdetto operativo per Fedora (laptop misura soltanto)

- **Il wiring alla nuova posizione e' VALIDATO e la cecita' strutturale del gate1 e' CURATA**:
  il pass esegue esattamente una volta per consegna (non per tentativo), sullo stato
  consegnato, nel formato log atteso, a costo zero, con oracle improve-or-noop armato. Se una
  boxed nascesse dalla rifinitura di consegna (il caso gate1-A), ora il pass la vedrebbe.
- **L'efficacia in-ciclo stanotte NON e' OSSERVABILE per assenza del fenomeno**, non per
  cecita': zero boxed consegnate in entrambe le braccia, il pass non aveva nulla da curare.
  L'evidenza di efficacia resta quella del banco replay (m39-off -11->-8, m39-on -13->-10,
  no-op puro su stato pulito, step9 57745e5b).
- Letture possibili, decisione a Fedora:
  - (a) tenere il flag opt-in DEFAULT OFF e trattare il banco come evidenza primaria di
    efficacia (lo stato attuale: il pass e' un'assicurazione contro un evento raro);
  - (b) armarlo DEFAULT ON come assicurazione a costo zero (improve-or-noop, no-op
    meccanico su stati senza residuo, zero controcandidi misurati su 2 gate);
  - (c) se si vuole evidenza in-ciclo, cercare/screenare bacini la cui consegna contenga
    boxed (il gate non puo' produrla: su bacino pulito il pass e' per costruzione inerte).

## Incidenti

- Guard TASK-541 al boot A (container stale t609b della notte precedente): fermato come da
  istruzioni della guard, una volta sola; boot B ha ri-usato la stessa procedura per lo switch
  di profilo t609c -> t609d (normale cambio profilo).
- Nessun exit silenzioso di fetch stanotte (heartbeat ~9 min: status sempre rispondente).
  Nei tratti di log silente (buffering stdout, fino a ~48 min in B-TIMESLOT-1) la liveness e'
  stata verificata via CPU del processo (773-929%, decider MT-LS a pieno carico).

Gates: measurement-only (nessun sorgente toccato, JAR da HEAD committato 93c1aa97), report-only -> gate-exempt.
