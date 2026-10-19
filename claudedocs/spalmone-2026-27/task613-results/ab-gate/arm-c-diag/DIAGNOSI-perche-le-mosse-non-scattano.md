# DIAGNOSI (lancio diagnostico t613c, 15/09 mattina, richiesta diretta di maintainer)

Altro lancio nelle stesse condizioni del gate (profilo fresco t613c, hole-relay
ON, protocollo run-measure completo) con i census di debug gia' previsti dal
codice sulla riga java: -Dhrf.debug=true -Dedf.debug=true (zero modifiche a
src/). Tripla guardia verde (banner 60x, lsof cwd, -D sulla riga del processo).

## Risultato della consegna

[-8/0]hard = H17 -5 (5) + H32 -2 (2) + H34 -1 (1); sanity 2 rossi; full-day
94,2%; doctor CONSISTENT-NONZERO; zero eccezioni. Stesso meta-bacino dei gate
(attempt-1 condannato, predictor scarta i reseed).

## La novita' rispetto al gate: il pass HA MOSSO alla consegna

```
[HRF-trace] G08#10140 10->11 G08#10141 11->12 G08#10527 12->10
            | slide G13#6693 13->12
HoleRelayFinisher [delivery] (TASK-613 v0.6 teacher-aware):
  targets=6, slides=1, rotations=3, moves=4
```

1 dei 6 buchi chiuso con la forma D3 (3-ciclo G08 + slide G13): H17 passa
da 6 a 5 match. Il pass NON e' strutturalmente no-op sul live: dipende dallo
stato consegnato (nel gate B3 i suoi 6 buchi non avevano catene esprimibili,
qui uno si').

## Perche' gli altri 5 non scattano: il census HRF al delivery

```
{HRF-NO-CONTENDER=1, HRF-SYM-HOLE=3, HRF-VISITED=132, HRF-H1=6,
 HRF-ROT-MOVABLE=48, HRF-DEPTH=142, HRF-SYM-CODEST-PARENT=57}
```

Letura per target (6 visti, 1 chiuso, 5 fermi):
- 1x NO-CONTENDER: la coda NON puo' scendere nel buco ma il suo docente NON e'
  conteso a quell'ora: il blocco e' un gate NON-H1 della coda stessa (VRP
  della lezione, cella gruppo, room o H7). Nessuna catena di permessi puo'
  curarlo con l'alfabeto attuale: e' il caso "il buco e' bloccato dalla coda,
  non dal contendente".
- 4x esplorazione morta: la DFS teacher-aware ha girato (VISITED=132 giorni,
  DEPTH=142 tagli del cap) e i gate simbolici hanno respinto tutto:
  SYM-CODEST-PARENT=57 (le co-destinazioni stesso-docente sono il rifiuto
  dominante: ogni inserzione candidata depositerebbe un docente dove un altro
  mover sta andando), SYM-HOLE=3, ROT-MOVABLE=48 (i giorni da ruotare
  contengono lezioni PINNATE, inmovibili), H1=6.
- In breve: sui buchi residui di QUESTI bacini live la forma vincitrice del
  banco (3-ciclo + slide con docente libero all'ora critica) non e' raggiungi-
  bile: o la coda stessa e' illegale nel buco (no-contender), o ogni rotazione
  candidata e' piena di pin o crea co-destinazioni stesso-docente.

Contesto EDF mid-cycle (2 invocazioni, per completezza): la prima con
{RELAY-ACCEPTED=14, REFILL-TERMINAL=14, CHAIN=11, RELAY-GATE-E=37M, H1=298k,
ROOM=99k}, la seconda {ACCEPTED=5, TERMINAL=5, CHAIN=3}: l'EDF armato lavora
forte a meta'-ciclo (la famiglia fill del ratificato 612).

## Confronto coi gate (tutti hole-relay ON tranne A3)

| run | hard | [delivery] 613 | note |
|---|---|---|---|
| A3 (flag OFF) | -14 | assente | controllo |
| B3 (ON, no debug) | -9 | targets=6, 0 mosse | buchi senza catene esprimibili |
| t613c (ON + debug) | -8 | targets=6, 4 mosse, 1 chiuso | 1 catena esprimibile trovata |

La differenza B3-vs-t613c conferma: l'effetto del pass e' STATO-DIPENDENTE
(quali buchi sopravvivono alla consegna e in che forma), non deterministico.

## APPENDICE step23 (naming per-target sul checkpoint, 15/09)

step23NoContenderNaming (env PLANNER_TEST_NC_WALK=1) sull'export t613c
(consegna [-8], CSF+EDF-ext no-op sullo stato gia' consegnato, hard -8
confermato). I 5 buchi H17 superstiti, classificati con tutti i gate della
coda in ordine rejectReason:

| buco | coda | classe | contendente / gate |
|---|---|---|---|
| G10 2026-12-10 @11 | Servizio tecnico e stilistico (T01) @13 | CONTENDER | H1: G12 Servizio tecnico e stilistico (stesso docente, cross-gruppo) + ROOM (nessun tagged room libero: la coda e' LAB) |
| G11 2026-10-16 @12 | Gestione aziendale (T06) @13 | CONTENDER | H1: G03 Gestione aziendale |
| G11 2026-10-23 @12 | Gestione aziendale (T06) @13 | CONTENDER | H1: G03 Gestione aziendale |
| G12 2026-09-18 @11 | Lingua Inglese (T21) @12 | CONTENDER | H1: G02 Lingua Inglese |
| G12 2026-10-09 @10 | Lingua Inglese (T21) @12 | CONTENDER | H1: G02 Lingua Inglese |

IL NO-CONTENDER del census al delivery NON e' riproducibile sullo stato
statico del checkpoint: tutti e 5 i superstiti hanno contendente. Spiegazione
piu' economica: il census del pass accumula sugli stati INTRA-run (il pass
chiude prima il buco G13 con il 3-ciclo G08 che sposta tre celle T15:
l'occupazione docente vista dai target successivi cambia), quindi l'evento
NO-CONTENDER appartiene all'ordinamento di processo del pass, non a una
proprieta' stabile del consegnato. Dato laterale utile: il buco G10 12-10
ha anche ROOM come gate (coda LAB senza tagged room libera a quell'ora).
