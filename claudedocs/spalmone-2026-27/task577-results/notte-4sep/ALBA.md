# ALBA notte 4/09 (scritta alle 06:24, ciclo ancora in volo)

Mandato MANDATE-231655 (notturna 4/09, zai autonomo entro i divieti: il laptop misura, non decide).

## Stato del ciclo notte-4sep ALLE 06:24

- Lanciato 01:06 su profilo fresco `notte-4sep`, jar con ENTRAMBE le cure della notte
  (preflight `PREFLIGHT-OK ... classes=[SurplusHarvest,OneOffFinisher] flags-verified`,
  banner `surplus-harvest=true` verificato nel log: la coda del banner restaurata da
  Fedora con cce67200 funziona).
- Fase corrente: B-TIMESLOT-1 (un tentativo intermedio; 3 verdetti DOOMED finora,
  23 righe "attempt done" includendo i reseed veloci del predictor).
- **avr=0 dopo 5h18m di ciclo: NESSUNA IllegalStateException assertValueRange.**
  LA DOMANDA della notte (le eccezioni OOF spariscono?) e' finora in forte positivo:
  il ciclo del 2/09 era gia' crashato 2 volte a quest'ora, il v11 ne aveva 3, il
  v11-n2 2. Qui zero.
- SurplusHarvest: 6 passate sul grezzo reale post-room (PRIMA VOLTA: fino a ieri la
  prima occasione computava su una griglia stantia), tutte moves=0; REJECTS dominati
  da teacherBusy (18-20k) e border (13k); h14=0 ovunque; campo roomBusy ora presente
  nel breakdown (0 finora: la guardia reject-on-busy non ha ancora dovuto rifiutare).
- Consegna attesa verso le 09:00; l'heartbeat (cron ogni ~34 min) eseguira' capture
  canonica in questo direttorio + VERDICT-raw + righe TASK-581 + commit/push.

## Le voci chiuse nella notte (tutte pushate su feature/cartesian-block-entity)

1. **QUINTA VIA OOF** (`c54038ba`, authhash pre-rebase a2d04c00): il canale dei crash
   assertValueRange erano i FOLLOWER di extractAndCompact (slide-back senza guardia
   per-cell), non il quinto argomento di tryMove (imprecisione del mandato: era
   targetDate, e relieveOverloads faceva 0 mosse in entrambi i crash). Guard
   isSlotLegalFresh hoistata, TDD OneOffFinisherTest 2/2, /simplify 4-reviewer,
   code-review high (9C: 5 applicati, 5 deferiti a TASK-578).
2. **BUG ISTANZA** (`bbeeb0c5`): Timefold solve() clona due volte, quindi la catena
   post-B-ROOM (QFR-2/EDF/SH-prima-occasione) era opera-fantasma sulla griglia
   pre-room (mai consegnata, mai salvata; le letture moves=0 del v11 erano sulla
   griglia stantia). Catena spostata su finalSolution + persistTail condiviso;
   il code-review ha ribaltato la prima versione dell'oracolo (full-hard dove le
   stanze sono definitive, timeslot-tier dove sono provvisorie) e chiuso l'H29 del
   limite I3 (reject-on-busy-room).
3. **TASK-581 registro** (`a360eadb`): prime 28 righe (ciclo v11-n2: tentativo-1
   end-to-end + 27 reseed GRASP con i segnali precoce del predictor). Le righe di
   stanotte seguiranno alla capture. La capture v11-n2 del pomeriggio e' `84869121`.

## Deferiti (annotati nel registro TASK-577, perimetro TASK-578)

Asse ROOM della crash class (resolveRoom ignora getAvailableRooms: vive con
lab-fallback ON, esplode con l'opt-out), sweep a confine pass come chokepoint
strutturale (la ~15a guard per-path e' sintomo), previousGridSlot terza copia di
getTimeslotAtOffset, dipendenza dei guard Fresh dai statici pubblicati, banco
PureOneOffCheckpoint env-gated, medium-pure clone-orphan (flag OFF), persistence-tail
unica. Stato operativo dettagliato: /tmp/notte-4sep-voci.md (volatile).

## Cosa resta alla consegna (heartbeat, non a mano)

capture-run.sh qui dentro (export >100MB resta locale), VERDICT-raw.md (score,
tentativi, righe SH con roomBusy, grep -c assertValueRange, sanity), append
attempt-traces.csv, commit+push. Nessun default toccato, nessun revert,
DataGenerator intatto.
