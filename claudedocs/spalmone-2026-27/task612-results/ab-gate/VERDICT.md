# VERDICT: TASK-612 banco-verify + gate A/B (laptop, 14/09/2026, mandato da Fedora)

laptop misura-soltanto: NESSUNA adozione, NESSUN default flip, NESSUNA modifica a src/.
HEAD: 406a7f20 (feat TASK-612). Le decisioni restano a Fedora.

## STEP 1: banco post-hoist in finestra dedicata, VERDE 13/13

```
PLANNER_TEST_REPLAY_609=.../task604-results/m39-off/export.json.gz \
  ./scripts/core/test.sh -c CompositeRepairReplay609Test -mva "-Dsurefire.heap=12g"
Tests run: 13, Failures: 0, Errors: 0  =>  EXIT 0  (laptop 36GB, heap dedicato 12g)
```

Pin tutti confermati, identici a quelli del commit:

| step | pin atteso | misurato |
|---|---|---|
| step1 replay m39-off | hard=-11 | -11 (placed=12550 boxed=1; H17/H32/H34 -3 x3, H0 -2) |
| step7 CSF | -11 -> -8 | -8 (delta +3) |
| step11 EDF esteso | -8 -> **-3** | **-3** (delta +5, moves=39, cellsClosed=5, medium -798121 -> -774362) |
| step8 m39-on | -13 -> -10 | -10 |
| step12 EDF ext m39-on | -10 -> **-6** | **-6** (moves=36, cellsClosed=4) |
| step9/13 no-op | moves=0 byte-identical | moves=0, hard -1 -> -1 |

Census step11: `RELAY-ACCEPTED=13` (>0, pin soddisfatto). Nota ambiente: al primo
giro step8/12 davano `NoSuchFile /tmp/m39on/export.json.gz` (fixture ripristinata
solo su Fedora); ho messo una copia identica dal repo tree in /tmp, gzip-OK, poi
13/13. L'hoist GATE-E e' quindi VERIFICATO contro il banco: la finestra aperta
dichiarata nel commit e' CHIUSA.

## STEP 2: gate A/B, BRACCIO CONTROLLO CRASHATO, braccio trattamento consegnato

### Braccio A (t612a, controllo, flag default): THREAD DEATH, NESSUNA CONSEGNA

Ciclo: pre-assign COMPLETE; tentativo 1 completo ma non 0-hard; multi-start
attempt 2: 3 reseed consecutivi DISCARDATI dal profile predictor (H17 holes
276/306/363 > 150); il wrapper passa a rifinire le stanze del tentativo 1
("best attempt 1 shipped with CH-only rooms - refining its rooms before
delivery") e il solver MUORE all'init di B-ROOM:

```
Exception in thread "staged-solve" java.lang.IllegalStateException: The value
(2027-06-10T11:00 - 2027-06-10T12:00) from the planning variable (timeslot) has
been assigned to the entity (Lavorazioni meccaniche(10060)), but it is outside
of the related value range [null]∪[2026-09-18T08:00-...-2027-06-10T15:30 - 2027-06-10T16:30].
    at ...AbstractScoreDirector.assertValueRangeForBasicVariables
    at ...DefaultSolver.solve  at ScheduleService.runStagedSolver:1755
    at ScheduleService.runRoomSolveStage:3560
```

Punto chiave: una lezione del tentativo 1 era stata collocata fuori dal suo VRP
durante il ciclo (chi l'ha mossa va diagnosticato su Fedora: la assert scatta
solo quando il solver di consegna re-inizializza lo stato CH-only). Il thread
`staged-solve` muore NON catturato: nessun pick-best, nessun salvataggio, UI
ferma su NOT_SOLVING con stagedStage nullo. Banner di controllo corretto
(`relay=false h32=false`): il crash e' sul path di DEFAULT con i flag TASK-612
SPENTI, quindi NON e' causato dal codice opt-in oggetto del gate.

Capture finalizzata come stato crash (NON e' un risultato di braccio):
`DOCTOR-CONSISTENT-NONZERO hard=[-10016/0]` (mid-attempt-2 in memoria, H29
-9437 = stanze CH-only mai rifinite), sanity 14/14 gruppi rossi, full-day
89,3%. Evidence: `arm-a/app-crash.log` + `arm-a/crash-context.txt`.

### Braccio B (t612b, trattamento, `-Dplanner.construction.edf-relay.enabled=true -Dplanner.construction.edf-h32.enabled=true`): CONSEGNATO in ~72 min

Doppia guardia pre-solve VERDE: (1) banner `EmptyDayFinisher relay family
(TASK-612 prototype): relay=true h32=true` (40 occorrenze nel log del run);
(2) lsof cwd della JVM = /Users/user/planner; (3) bonus: i due `-D`
visibili sulla command line del processo (`ps`), heap 2g da PLANNER_HEAP.

Stesso percorso del controllo: tentativo 1 non 0-hard, predictor scarta i
reseed del tentativo 2, consegna il tentativo 1 rifinendone le stanze (lo
STESSO confine dove A e' morto: B lo supera senza eccezione).

**Score consegnato: `[-15/0]hard/[-1079148/-689]soft`** (saveSchedule 14:33:42,
3077/12551 pinned).

Riga `[delivery]` (il dato che il gate doveva produrre):

```
EmptyDayFinisher [delivery] (TASK-612 relay=true h32=true): 0 moves,
0 cell(s) closed to goal, 0 conflicts, 0 swap-compound(s)
```

Famiglie residue B (analysis.json): H17 -9 (8 match), H32 -2 (2 celle), H7 -2,
H33 -1, H34 -1.

### Predizione vs esito (PREDIZIONE.md scritta prima dei risultati)

- Predizione 2 (se c'e' residuo fill-family, B chiude celle e hard B <= hard A):
  PARZIALMENTE FALSIFICATA nel verso interessante. Lo stato consegnato di B
  AVEVA 3 celle fill-family (H32 x2 + H34 x1) e il pass `[delivery]` armato ha
  fatto 0 mosse, 0 chiuse. Sul banco le stesse famiglie si chiudono 5/5; su
  questo residuo live i gate (donor/landing/VRP/GATE-E) respingono tutto: le 3
  celle live non hanno la geometria import-relay delle celle del banco (le H17
  x8 dominanti sono teacher-locked, famiglia TASK-613). NESSUNA regressione
  (improve-or-noop rispettato: 0 mosse = 0 danno), ma la meccanica non
  trasferisce profitto su questo bacino: e' raccolta evidenza; il gate non ha
  controindicazioni e non ha neanche guadagno dimostrato end-to-end.
- Predizione 1 (se 0-hard, no-op atteso): il caso 0-hard non si e' materializzato
  (nessun tentativo a 0-hard in entrambe le braccia; predictor molto aggressivo
  sui reseed: nessun tentativo 2 ha mai girato la LS).
- Predizione 4 (banner): VERDE.

### Confronto A/B: NON APPAIABILE

A non ha consegnato (crash del path di default, NON del codice in gate). L'unica
lettura onesta: (a) il path di default a HEAD ha un crash VRP-assert
stocastico-per-bacino alla rifinitura di consegna (VEDI TASK-565 qui sotto);
(b) il trattamento ha completato lo stesso protocollo senza crash (un solo
run: non prova che il flag protegga, il crash di A e' indipendente dai flag);
(c) il no-op pulito del pass armato conferma la sicurezza (improve-or-noop)
gia' dimostrata dal banco.

### Il crash del braccio A e' la firma di TASK-565, ma su un confine NON catturato

TASK-565 (To Do, 28/08) censiva la stessa identica firma: stesso timeslot
`2027-06-10T11:00-12:00`, stessa finestra VRP, stessa materia `Lavorazioni
meccaniche` (entity 10021 allora, 10060 oggi), n=1, scattata dentro
FinalClosingCampaign e ALLORA CATTURATA dal wrapper ("FinalClosingCampaign
FAILED (continuing with the pre-campaign winner)"): consegna sopravvissuta.
Oggi (n=2) la stessa violazione e' emersa a `runRoomSolveStage` (rifinitura
stanze del tentativo condannato), FUORI da ogni catch: thread morto, NESSUNA
consegna. Escalation di gravita' per TASK-565: la causa radice non e' solo
nella coda di chiusura, la placement fuori VRP sopravvive nello stato
consegnato e l'assert puo' scattare su qualunque re-init di solver
successivo. Stack completo in `arm-a/app-crash.log`.

## KPI batteria braccio B (capture-run.sh)

- score: [-15/0]hard / medium -1079148 (score.txt); vedi concordanza sotto
- full-day: 94,1% (1840/1955; golden ~91,9 ex-G14)
- ricorrenza: totale 72,5%, unpinned 71,3% (6758/9474), pinned 76,2%
- buchi interni: 8 giorni / 9 slot
- day-start scarti >=3: none
- sanity per gruppo: NON CONFORME, 3 rossi (G10: 2 gg con buco interno;
  G12: 3 gg; G14: 1 gg sotto min golden), coerenti con H17 -9/H33 -1;
  gli altri 11 gruppi puliti. (A: 14 rossi, stato crash, non conta.)

## Concordanza hard mem/db

score.txt (memoria) `[-15/0]hard` vs analysis.json (DB) `[-15/0]hard`:
CONCORDI su tutto il vettore hard (doctor: DOCTOR-CONSISTENT-NONZERO).
MEDIUM divergente (-1079148 mem vs -549259 db): divergenza NOTA e documentata
TASK-509 (latestScore in-memory vs riconteggio DB), non bloccante, pre-esistente.

## Lettura visuale del confronto HTML (confronto-arm-b.html, screenshot nel pane)

- Gruppo per gruppo le forme run/golden sono sovrapponibili: stessa banding di
  colore per materia, stessa densita'; i blocchi scuri (stage G12, lab
  G13/FC) cadono negli stessi periodi su entrambi i lati.
- La coda d'anno (apr-giu 2027, crop del terzo inferiore): entrambi i lati si
  assottigliano insieme; il lato run mostra qualche cella bianca in piu' su
  G11/G13/G14 a fine anno (la famiglia residua import teacher-locked
  mag-giu, gia' censita) e il -15 hard non produce alcuno strappo visibile.
- Nessuna divergenza di forma preoccupante: poca differenza dal golden, il
  segno buono secondo la regola della diff come metro di sanita'.

## Incidenti ambiente (documentati, nessuna improvvisazione)

1. run-measure.sh patchata (scripts/, NON src/): l'assegnazione APPJAR faceva
   morire lo script sotto pipefail per il glob di fallback
   `lib/main/planner-*.jar` assente nel layout Quarkus attuale (jar solo in
   app/). Aggiunto `|| true` con commento. Senza questa fix l'harness non
   lanciava NESSUN braccio. Commit separato.
2. Gate verify-baseline STANTII (non patchati, decisione a Fedora): seed si
   aspetta 1376 timeslot ma il seed ratificato TASK-550-ext ne ha 1392; import
   si aspetta 1993 stage-pin/327 off-grid ma il corrente e' 2009/311
   (conservazione esatta 2320h: le 16 ore sono diventate pin reali sui 3 giorni
   della griglia estesa). Verificati a mano con query DB: MODA 866 OK, 27
   docenti, 14 gruppi, 18 aule.
3. arm-b/app.log: la snapshot copiata al lancio precede il banner (stampa a
   solve-start); refreshata col log completo del run, snapshot originale
   conservata come app-launch-snapshot.log.

## Coda per Fedora

- Finestra aperta del commit CHIUSA: banco post-hoist 13/13 sul laptop.
- TASK-565 (già aperto): il crash del braccio A ne e' la seconda occorrenza
  (n=2) e la prima su un confine NON catturato (runRoomSolveStage invece di
  FinalClosingCampaign): da rivalutare in priorita', la consegna del ciclo
  intero puo' morire. Dettagli nella sezione dedicata sopra e in
  arm-a/app-crash.log.
- Aggiornare verify-baseline.sh ai numeri TASK-550-ext (1376->1392,
  1993/327->2009/311).
- Il gate A/B vero e proprio resta DA RIPETERE con un controllo che consegni:
  qui B ha girato su un bacino senza profitto per la famiglia (0 mosse) e A e'
  morto prima della consegna.

## Lettura visiva (occhio, 14/09 pomeriggio)

Render effettivamente guardati (screenshot full-page + crop, nel pane):
confronto-arm-b.html (3 viste: intero, fascia centrale, fascia inferiore),
day-start-anatomy.html (intero + meta' inferiore), kpi-pair-stability.html
(17k px letti a fette, zoom sull'ultimo blocco T10 x G13).

- Q1 (cluster delle H17 x8 + 3 celle fill): le 8 celle H17 NON sono sparse: si
  concentrano su DUE soli gruppi (G10: 2 giorni con buco, G12: 3 giorni,
  date autunno 09/18-10/09 + una a novembre/febbraio), visibili come celle
  bianche singole dentro giorni altrimenti pieni. Le 3 celle fill si vedono:
  G09 06-03 e' un giorno BIANCO nel run dove il golden e' popolato (il piu'
  evidente dei tre); i deficit-1 di G12 (09-15, 04-16) a questa risoluzione
  non staccano dalla riga. Nessun cluster stagionale unico: famiglie diverse,
  conferma la lettura a numeri (H17 teacher-locked + residui puntuali).
- Q2 (venerdi' G13 / famiglia T10): forma NUOVA, non quella storica. Nel
  kpi-pair-stability il blocco "T10 x G13" mostra il golden come una
  banda settimanale fissa e densa (stesso weekday tutto l'anno) e il run come
  celle SPARSE che cambiano giorno da settimana a settimana, con il vuoto di
  stage a meta' anno. Le stats del blocco lo dicono e i numeri aggregati lo
  nascondevano: wd 100%->57%, switch 2->5, e soprattutto CELLE 0% (zero
  concordanza di cella settimanale col golden su una coppia da 115h). E' la
  firma visiva del bacino attempt-1 condannato di QUESTA consegna, non una
  regressione del modello: nessuna delle 0-hard storiche mostrava questa forma.
- Q3 (coda giugno): NON deserto. Golden e run si assottigliano INSIEME; il run
  mostra un po' piu' di bianco su G09/G11/G13 a fine anno (la famiglia
  import teacher-locked mag-giu gia' censita) e il giorno vuoto G09 06-03.
  Settembre (09-15, G12) e' pieno e indistinguibile dal golden.
- Q4 (anomalie che i numeri non raccontavano): (a) la coppia T10 x G13 con
  celle 0% e' il vero finding dell'occhio: l'aggregato 71,3% di ricorrenza
  unpinned nascondeva UNA coppia da 115h a concordanza zero, che merita di
  entrare nella fila delle cose da guardare col prossimo ciclo 0-hard (e'
  bacino-specifico ma e' la coppia delle celle H17 teacher-locked); (b) nel
  day-start-anatomy l'unica deviazione dall'8:00 uniforme e' una banda arancio
  contigua a meta' anno su G08 (piu' sparse su G09/G12): nessun rosso,
  nessun pettine, nessuna colonna vuota, nessun weekend fantasma. Nulla di
  nuovo oltre cio' che i numeri dicevano, SALVO il punto (a).
