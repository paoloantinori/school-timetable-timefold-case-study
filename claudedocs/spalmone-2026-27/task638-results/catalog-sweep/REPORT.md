# REPORT catalog-sweep TASK-638 steepest (22/09 notte, laptop, rc-1)

Validazione del ViolationScopedSteepestFinisher su TUTTI gli stati del catalogo
post-LS. Strumento: OffCapture638BenchTest mode=steepest, budget esaustivo
(offrep.candidates=100000), heap 8g (parse export 106MB: finestra dedicata,
macchina scarica). Fixture: export.json.gz quando presente (room/id-faithful:
baseline = stato consegnato REALE), pipe lessons.txt per integrato-1 (unico
senza export; la sua baseline -52 porta il rumore di ricostruzione H4/H25/H32,
dichiarato in ogni riga); la static golden co-presence depth e' impostata su
ogni fixture (la sequenza loadExport del banco trio). Codice: 9e4675cb + le
estensioni strumentali scoperte dallo sweep stesso, tutte sotto gate
(/simplify 3 reviewer + code-review high 6 finding, 4 curati 2 declinati con
motivo, vedi sotto).

## Risultati (codice finale)

| # | Stato | Fixture | Before | After | Eval | Wall | Mosse |
|---|-------|---------|--------|-------|------|------|-------|
| 1 | 636-off | export | -2 | **0** | 23.337 | 2,0s | 1 SWAP Lingua Inglese <-> Educazione Civica (+2hard) |
| 2 | 637-on | export | -1 | -1 | 1.152 | 0,6s | 0 (spazio esausto, best 0) |
| 3 | 637-off | export | -3 | -1 | 484 | 0,4s | 1 CHANGE Servizio tecnico e stilistico -> 2026-09-17 (+2hard) |
| 4 | integrato-5 | export | -1 | **0** | 12.999 | 1,2s | 1 SWAP Matematica <-> Scienze (+1hard) |
| 5 | integrato-1 | pipe | -52* | -51 | 42.014 | 3,1s | 1 SWAP Relazione con il cliente <-> Lingua Inglese (+1hard) |
| 6 | integrato-6 | export | -1 | -1 | 0 | 0,4s | 0 (nessun target: l'H34 e' un'assenza) |

*La -52 di integrato-1 e' quasi tutta rumore di ricostruzione pipe (H4 -37,
H25 -7, H32 -4); le celle REALI del catalogo sono H17x2 + H7 + H28 = -4.

## Predizioni vs esiti

1. 636-off "(gia' fatto, -37->-35)": CONFERMA e MIGLIORATA dalla fixture
   export: la baseline reale e' -2 (non -37: quello era rumore pipe) e la cura
   e' UNA sola swap che chiude ENTRAMBE le celle H7 a 0-hard.
2. 637-on "swap T10@8<->T15TS@10 chiude a 0": MANCATA, con causa
   meccanica: nello stesso capture T15 insegna gia' G08 alle 8 del
   12/02 (riga `G08|2027-02-12|8|Controllo numerico|T15`); la swap
   arbitratia lo fa atterrare su quella cella: H28 +1 e H1 -1, delta 0. Lo
   spazio single-move esausto (1.152 candidate) non contiene alternative
   migliorative (grammatica allargata alle swap intra-giorno per questo
   stato). L'arbitraggio del near-miss non aveva visto la collisione G08.
3. 637-off "piazzare la boxed chiude H0+H32": CONFERMATA NELLA SOSTANZA: il
   finisher piazza la lezione boxed (Servizio tecnico e stilistico; la
   T21/Lingua Inglese del near-miss era l'identificazione sbagliata)
   chiudendo H0+H32 in una mossa (+2 netto: H0 peso 2, H32 +1, H29 -1 perche'
   il finisher e' timeslot-tier e non assegna stanze). Residuo -1 = H29
   bookkeeping delle stanze, rfa della fase B-ROOM della catena reale, non
   del timeslot tier.
4. integrato-5 "chiude a 0": CONFERMATA (una swap, 1,2s).
5. integrato-1 "chiude H7+H28 (-2), H17 resta": PARZIALE: H7 chiusa (+1),
   H28 NON chiusa (come lo stato 2: nessuna single-move nello spazio esausto),
   H17 resta come predetto (strutturale, vacanza T10). Il rumore pipe
   resta intoccabile (mosse fuori modello non proponibili).
6. integrato-6 "NON chiude (strutturale surplus=1)": CONFERMATA: evaluated=0,
   l'H34 e' un'ASSENZA (giorno dichiarato vuoto): non ci sono lezioni-ferita
   da dirigere, fuori dalla grammatica violation-directed per natura.

## Verdetto del meccanismo

- 2 stati portati a 0-hard (636-off con UNA swap che chiude due celle; 
  integrato-5), 1 portato a -1 con H0+H32 chiusi e solo bookkeeping stanze
  residuo (637-off), 1 parziale (integrato-1: H7), 2 correttamente dichiarati
  non curabili (uno per natura di assenza, uno per collisione reale che
  l'arbitraggio umano non aveva visto).
- Il meccanismo violation-directed e' VALIDO sui dati storici: quando esiste
  una mossa singola legale la trova in secondi (0,05-0,1ms/eval); quando non
  esiste lo dichiara esaurendo lo spazio invece di inventarla.
- Le H28 (stati 2 e 5) restano compositive in questa grammatica.

## Lacune trovate e curate dallo sweep stesso (tutto sotto gate)

1. GRAMMATICA: le swap erano cross-date only; la cura H28 attesa dallo stato 2
   e' intra-giorno. Allargato alle same-date (valutazione full-constraint:
   ogni peggioramento resta visibile).
2. GATE VRP: il generatore proponeva valori fuori dal value range dell'entita'
   (lezione Stage fuori finestra: catturato dall'assert di range di
   bench.score). Gate con Lesson.isSlotLegalFresh (TASK-578, la forma di casa
   per i guard contains-shaped; la prima cura con HashSet su
   getAvailableTimeslotsFresh e' stata sostituita su indicazione convergente
   di tutti e tre i reviewer /simplify).
3. TRONCATURA (code-review F1): col gate VRP il flag `added` del round-robin
   poteva spegnersi su un round vuoto (slot chiuso per tutti i target del
   gruppo) e troncare l'enumerazione MENTRE il chiamante la dichiara esausta.
   Questo artefatto aveva prodotto un FALSO "non curabile" sullo stato 3 nella
   prima tirata del report (-3 fermo con diagnosi poi smentita "slot fuori
   VRP"): col loop corretto la cura esiste e viene trovata. Il loop ora corre
   fino all'esaurimento di ENTRAMBI gli indici.
4. Fixture: static golden co-presence depth impostata (la sequenza loadExport
   del banco trio; senza, H-COPRESENCE/H2 punterebbero una depth map vuota);
   offrep.fixture=export senza export ora fallisce con messaggio chiaro; la
   assume ammette le dir export-only.
5. DECLINATI con motivo: (a) gate sulla range CACHED invece che fresh (F2):
   la convenzione di famiglia vieta il getter cached nei move guard
   (SurplusHarvest, ciclo di crash 02/09) e l'abort altisonante su eccezione
   copre la divergenza cache/assert rendendola impossibile da ignorare; nota
   per la forma production: invalidare le cache lesson-level a inizio
   tentativo. (b) O(n) contains dentro isSlotLegalFresh (F6): e' dentro i wall
   misurati (nessun problema visibile); una vista Set statica in Lesson e'
   ottimizzazione propria di TASK-641.

## AGGIORNAMENTO 23/09 notte: target CONTESTO (la cura del cieco H28 applicata)

Dopo il blind puzzle (vedi `../blind-and-moves/REPORT.md`: la H28 di 637-on e'
curabile con una swap che muove la lezione "c" di mezzo, mai target), il
collectTargets del finisher e' stato esteso: per ogni coppia same-teacher
same-subject same-day nella ferita, anche le lezioni CONTESTO (stessa materia,
docente diverso, strettamente tra le due) diventano target. Prima/dopo su
tutti i 6 stati (stesso comando, codice esteso):

| Stato | Prima | Dopo | Nota |
|-------|-------|------|------|
| 636-off | **0** | **0** | stessa swap vincente (Lingua Inglese <-> Educazione Civica, +2) |
| 637-on | -1 | **0** | NUOVO: la swap del puzzle (Lavorazioni <-> Tecniche di saldatura, +1, 551ms) viene generata e applicata: la predizione del mandato centrata |
| 637-off | -1 | -1 | stessa mossa (boxed piazzata, H0+H32 chiusi) |
| integrato-5 | **0** | **0** | stessa swap (Matematica <-> Scienze, +1) |
| integrato-1 | -51 | -51 | stessa cura H7; la sua H28 NON si cura con l'estensione (spazio 43.182 candidate esausto, best 0: quella cella vuole altro) |
| integrato-6 | -1 | -1 | invariato (H34 assenza) |

ZERO regressioni: i 3 stati che chiudevano a 0 continuano a chiudere con le
stesse mosse; il punteggio del catalogo passa da 2 a 3 stati a 0-hard (piu'
637-off a -1 con sole stanze residue).

## Comandi per replicare

`./scripts/core/test.sh -c OffCapture638BenchTest -mva "-Doffrep.run=true -Doffrep.mode=steepest -Doffrep.candidates=100000 -Doffrep.dir=<dir> -Dsurefire.heap=8g"`

con `<dir>` uno di: `claudedocs/spalmone-2026-27/{task636-results/ab-adoption/off,
task637-results/ab-adoption/on, task637-results/ab-adoption/off, integrato-5,
integrato-1/run, integrato-6}`. `offrep.fixture=txt|export` forza la fixture.
