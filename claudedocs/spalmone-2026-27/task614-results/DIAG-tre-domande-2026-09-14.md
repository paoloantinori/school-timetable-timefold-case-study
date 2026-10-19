# DIAG mandato Fedora 14/09 pomeriggio: TRE domande, misurate sul laptop

HEAD d276912e. Zero modifiche a src/main (solo i due step diagnostici nel banco
test-side: `step15CrashVrpWalk` doppio-gated `PLANNER_TEST_CRASH_WALK=1` +
`PLANNER_TEST_REPLAY_609`, e `step16ArmBEdfCensus` nel banco gia' gated).
Log completi sul laptop: /tmp/bench-t614-walk2.log, /tmp/bench-t613-hrfdebug.log,
/tmp/bench-t612-step16.log, /tmp/bench-t612-edfdebug-armb.log.

## DOMANDA 1 (TASK-614, crash delivery): RISPOSTA = filtro 2, H5.1 teacher unavailability (T10)

Il walk (`step15CrashVrpWalk` sull'export arm-a, heap 12g, EXIT 0):

```
crash lesson id=10060 pinned=false persistedTs=2026-10-06T14:30
  teacher=T10 group=G13 prof=true tags=[meccanica]
(a) fresh.contains(crashSlot)=false isSlotLegalFresh=false freshSize=372
(d) cached.contains(crashSlot)=false cachedSize=372
(b) 1-H6.1-holiday           pass
    2-H5.1-teacher-unavail   ** REJECT **   <- L'UNICO CHE BUTTA 2027-06-10T11
    3-H4.1-stage-blackout    pass
    4-H3-pinned-cell         pass
    5-H20-calendar-window    pass
    6-L1bis-quota-permits    pass
    7-stage-confinement      pass
    8-no-session             pass
(c) group=G13 quotaRow2027-06-10 aula=0 lab=4 stage=0 fc=true
    quotaBoundLabSubject=true, 4 slot legali su 2027-06-10 per la lezione
```

Fatti aggiuntivi misurati:
- La placement illegale NON e' nell'export: l'entity 10060 e' PERSISTITA alla
  sua ultima posizione salvata (2026-10-06T14:30, legale). La posizione illegale
  2027-06-10T11 e' esistita SOLO in memoria (mai salvata): il walk quindi valuta
  il VRP di QUELLA lezione contro lo slot del crash (selettore per id, env
  PLANNER_TEST_CRASH_LESSON_ID default 10060).
- Classe della cura (il dato che la domanda voleva): lo slot e' legale per 7
  filtri su 8 ed e' respinto SOLO dall'indisponibilita' del docente. Un pass
  deterministico ha mosso la lezione li' con un landing gate che non consulta
  il VRP fresh (famiglia TASK-577 v5: "il pass assegna a slot fuori dal value
  range della lezione"). La quota NON c'entra: la riga 06-10 (aula=0 lab=4)
  permette il giorno.
- Nota meccanica (INFERENZA dichiarata, non misura): il "grandfather" di
  computeAvailableTimeslots ri-aggiunge SEMPRE lo slot corrente al range, quindi
  su cache fredda l'assert del solver non puo' mai partire. E' partita live =>
  il range visto dal solver di consegna non era ricalcolato: candidato naturale
  la cache clonata stantia (classe TASK-578), da confermare su Fedora. Concorde:
  il messaggio del crash mostra il range abbreviato primo..ultimo, non un range
  di 2 elementi.

## DOMANDA 2 (TASK-613, no-op banco): RISPOSTA = H1 mangia i pochissimi candidati censiti; il vero killer e' il gate NON censito della coda

step14 m39-off con -Dhrf.debug=true (heap 12g, EXIT 0):

```
[HRF-debug] reject census: {HRF-H1=2}
step14: targets=3 imports=0 quota-swaps=0 moves=0 | hard -11 -> -3 -> -3
```

- Sul lato censito, H1 (conflitto docente) e' l'unico gate che respinge: 2 reject.
- Lettura aritmetica (dalla struttura del codice, verificabile): findImporter
  censisce OGNI candidato valutato e ritorna alla prima passa; un fallimento
  completo della scansione produrrebbe DECINE di voci (una per giorno sorgente).
  Con soli 2 reject totali, findImporter ha TROVATO un importer legale (dopo
  1-2 rifiuti H1) per i target processati; con imports=0 e quota-swaps=0
  l'unico esito possibile e' che il ramo swap sia morto sul gate DELLA CODA
  (`legalLanding` del tail nella cella svuotata), che NON censuisce.
- Strumento: una riga di census in legalLanding (chiave HRF-TAIL-*) distinguere
  H1/H7/ROOM/GROUP/VRP della coda. Gap di strumentazione, non misurato qui.

## DOMANDA 3 (TASK-612, perche' 0 mosse sul live): RISPOSTA = 3 relay ACCETTATI ma tutti revertiti; la massa muore in H1/ROOM

step16 (export arm-b consegnato, CSF no-op, EDF relay+h32 armato con census,
heap 12g, EXIT 0):

```
[EDF-debug] reject census: {GATE-E=772, SLIDES=17, RELAY-GATE-E=2366480,
  RELAY-NO-REFILL=227, RELAY-REFILL-TERMINAL=3, RELAY-REFILL-CHAIN=3,
  RELAY-ACCEPTED=3, VRP=12847, H1=63117, ROOM=60071}
[EDF-H32] under-quota targets=2
step16: moves=0 cellsClosed=0 | hard -15 -> -15 (CSF) -> -15 (EDF ext)
```

Celle fill residue (vista H32 del test, concordanti con analysis.json):
- G12 2026-09-15 quota=5 placed=4 deficit=1 (H32)
- G12 2027-04-16 quota=5 placed=4 deficit=1 (H32)
- G09 2027-06-03 quota=5 placed=0 deficit=4 (giorno vuoto dichiarato = H34)

Interpretazione misurata:
- NON e' "nessun candidato": il relay ha valutato ~2,4M verdetti GATE-E (hoist),
  63k reject H1, 60k ROOM, 12,8k VRP, 227 NO-REFILL, e ha ACCETTATO 3 import
  (con 3 refill terminal + 3 refill-chain formati).
- Eppure moves=0/cellsClosed=0 e hard -15 invariato: i 3 relay formati sono
  stati REVERTITI (improve-or-noop: chiudere una cella H32 da deficit-1 qui non
  migliora l'hard netto, o lo peggiora altrove; H7 -2 residuo suggerisce che
  l'import urterebbe il cap giornaliero di qualche docente). L'oracolo tiene.
- Controllo incrociato: step10 (flag spenti) sullo stesso export stampa
  {GATE-E=119, SLIDES=3, H1=12, ROOM=15}: il path plain-extraction e' vivo ma
  ugualmente sterile.

## Artefatti

- Test diagnostici (test-side, nel banco gia' gated): `step15CrashVrpWalk`
  (doppio gate, non gira mai nella suite normale), `step16ArmBEdfCensus`
  (gated come il resto del banco, assert solo non-regressione). Nessun default
  toccato, nessuna modifica a src/main, classe intera skipped senza
  PLANNER_TEST_REPLAY_609 (impatto suite: zero, compile EXIT 0 verificato).
- Questo doc + i quattro log /tmp citati (stralcio: le righe chiave sono qui).
