# DIAGNOSI varianza hard run4(0) vs run5(-38) + PROPOSTA leva di ricerca - zai, 2026-08-01 sera

> Sessione notturna 01-02/08. Riprende da handoff sera3. Task di step (1) diagnosi read-only e
> step (2) decisione cura-vs-proposta. HEAD `feature/cartesian-block-entity` `6a660194`. DB = run5
> (hard -38). App UP `java -jar` source=input. Verdetti dai raw, catena causale link-by-link.

## DOMANDA
run4 = hard 0, run5 = hard -38 (16 H0 + 6 H17), protocollo byte-identico (2882 authoritative pin,
source=input, seed deterministico). Perché run4 converge a 0 e run5 no? La pista
Math/Barberia tight-capacity regge? È della cura (TASK-452) o del modello?

## CATENA CAUSALE (ogni link groundato in un artifact)

1. **Pre-solve byte-identico** (2882 authoritative pin, source=input). Seed DataGenerator
   deterministico; reset = fresh-reseed (handoff sera3 "confermato operativo"). [PLAUSIBLE - non
   re-verificato meccanicamente questa sessione, vedi sotto]
2. **Il solver e' non-deterministico sul path di produzione.** `ScheduleService.runTimeslotSolveStage`
   (size<=1, default, portfolio OFF) chiama `runStagedSolver(timeslotPhasesConfig(), ...)` SENZA
   `setRandomSeed`; commento linea 1330: "under NON_REPRODUCIBLE (production default) Timefold uses
   a fresh seed each run". `solverConfig.xml` non dichiara environmentMode. [CONFIRMED da codice]
3. **Divergenza al B-TIMESLOT #1.** run4: timeslot-tier hard -24, **box 0**. run5: timeslot-tier
   hard -46, **box 16**. [VERDICT run4/run5 che citano il log app; phases.json: run4 = 1 ciclo
   LS-Compactness+Compaction, run5 = 3]
4. **Il ciclo di demozione (max 2 round) non ha sbloccato i 16.** `MAX_DEMOTION_ITERATIONS=2`,
   soglia di accettazione `boxed==0 && hard>=-2` (`ScheduleService:802-944`). run5: demozione ha
   liberato 4 pin promossi, ma i 16 restavano boxed -> "residual after 2 demotion round(s)
   ACCEPTED as-is box=16" (log 18:42:04). [CONFIRMED]
5. **I 16 boxed -> 16 H0 unassigned** = T08/G03/Matematica (11) + T19/G06/Barberia (5).
   Verificato dal DB live (`count(*) WHERE timeslotId IS NULL` = 16, breakdown 11+5) e da
   `analysis.json` (H0 -32/16 match). [CONFIRMED]
6. **I 16 sono collocabili (NON infeasibilita' strutturale).** run4 li ha collocati: lessons.txt
   run4 ha 80 T08/G03/Math e 47 T19/G06/Barberia; run5 ne ha 69 e 42. Diff 11 e 5 =
   **esattamente** gli stranded. Input identico, output diverso -> la non-collocazione e' varianza
   della LS, non impossibilita'. [CONFIRMED - check decisivo]
7. **6 H17 sono separati** (gap verticali da fill piu' aggressivo; HoleRepair 14->6). [analysis.json]
8. **B-ROOM (LS Room Refinement, 600s)** ha risolto il residuo run4 (-9404hard -> 0) ma run5 solo
   -9634 -> -38. [phases.json fase 5]

## VERDETTI (adversarial-verification applicata)

**CONFIRMED (evidenza meccanica questa sessione):**
- I 16 H0 sono T08/G03/Math (11) + T19/G06/Barberia (5). [DB + analysis.json]
- I 16 sono collocabili: run4 li ha piazzati (diff 80->69, 47->42 = 11, 5). [lessons.txt] -> NON
  e' infeasibilita' strutturale, e' varianza LS.
- La cura TASK-452 NON c'entra: G10/T01 collocato in entrambi i run; i 16 sono un
  docente/gruppo/materia diverso (T08/T19, non T01). [DB + logica]
- Divergenza al B-TIMESLOT #1 (box 0 vs 16) e ciclo di demozione che non sblocca. [VERDICT + fasi]
- Non-determinismo del solver sul path produzione (fresh seed, no setRandomSeed). [codice]

**PLAUSIBLE (non re-verificato questa sessione, etichettato non-verificato):**
- Pre-solve byte-identico (2882 pin) tra run4 e run5. Localizza la divergenza alla LS (vs
  pre-assign). **Non blocca la conclusione azionabile**: la collocabilita' dei 16 (link 6) regge
  indipendentemente, e la cura e' esclusa per logica (link CONFIRMED), non per identita' pre-solve.

**CAUSA DIRETTA:** non-determinismo della LS (fresh seed + NON_REPRODUCIBLE). Input identico ->
traiettoria diversa -> run5 boxa 16 lezioni tight-capacity che il cap-di-demotion non rescinde.
**LOCUS (tangenziale, non causa):** le celle tight-capacity T08/G03/Math e T19/G06/Barberia
- sono dove una traiettoria subottimale strand per prime (docente/gruppo saturato). Non sono la
causa: run4 le ha collocate tutte.
**ESCLUSA:** la cura (G10 ok), il modello (feasible, run4 dimostra), setup (byte-identico).

**Falsificazione:** run6 dovrebbe atterrare in un range stocastico (0 o -N), con T08/T19 come
locus ricorrente dei landing non-zero; un rerun REPRODUCIBLE+seed-fisso riprodurrebbe un run dato
esattamente. Se run6 riproducesse deterministicamente -38 o strand su docenti DIVERSI, la diagnosi
"varianza tight-capacity" sarebbe confutata.

## STEP (2): CURA O PROPOSTA? -> PROPOSTA (non c'e' cura piccola)

La diagnosi dice **residuo = varianza LS, modello+cura corretti**. Le leve:

| leva | tipo | size | valore atteso | dottrina |
|---|---|---|---|---|
| **Portfolio multi-start (DRAFT-005)** | ricerca, GIÀ COSTRUITO, flag `-Dplanner.portfolio.size` | nessun cambio codice | alto: N seed -> probabilita' piu' bassa che TUTTI strandino i 16; tiene il best per score decomposto | diretta "correttezza logica vs costo" (search) |
| BoxRepair deeper-eviction (k-way oltre 3-way) | cambio codice BoxRepair, flag default OFF | medio (algo nuovo + cycle-detection) | incerto: tratta il sintomo (occupancy run5), non la varianza | search-side, invasivo |
| `MAX_DEMOTION_ITERATIONS` -> flag -D (2->4) | micro-cambio codice (1-2 righe) | piccolo | basso: la demozione libera solo PIN PROMOSSI; run5 ne libero' 4 e i 16 restavano (il blocco e' occupanza ordinaria) | search-side, low-EV |

**Perché non prototipo piccolo:** la sola leva piccola (piu' round di demozione) ha valore atteso
basso (il blocco run5 non e' pin promosso, e' occupanza LS che la demozione non tocca). BoxRepair
deeper e' medio e speculativo. La leva che rispetta la varianza (portfolio) e' gia' costruita ma e'
codice DRAFT-005 con gate proprio ("NEVER adoption: laptop A/B x2 gate decides"). Nessuna e' una cura
piccola flag-gated adozionabile stasera.

**PROPOSTA:**
1. **Nessun cambio modello/cura.** Il residuo e' ricerca, la direttiva primaria e' rispettata.
2. **Leva primaria = portfolio multi-start.** E' la leva coerente con la natura del problema
   (varianza) e con la dottrina (search). Gia' implementata; manca la misurazione del gate.
3. **Leva secondaria (solo se portfolio non doma abbastanza la varianza) = BoxRepair deeper
   eviction**, flag default OFF, con A/B e guard sul medium (l'eviction profonda rischia di
   peggiorare medium per chiudere hard).
4. **run6 discriminatorio (PRONTO, non lanciato stasera - vedi sotto).**

## run6: DISCRIMINAZIONE FRA DUE IPOTESI (design, non eseguito)

Due ipotesi precise sulla natura del -38:
- **H1 (varianza domabile col search):** il -38 e' un local-optimum della LS; multi-start
  (seed multipli) porta hard-0 in modo affidabile.
- **H2 (tight-capacity al bordo):** la capacita' T08/G03 + T19/G06 e' cosi' stretta che
  anche multi-start strand frequentemente -> la leva e' piu' a valle (repair piu' profondo o
  ridurre la domanda).

**Run discriminatorio:** `solve-staged` con `-Dplanner.portfolio.size=2` (o 3) + source=input,
protocollo run4/run5. Se portfolio>=2 hit hard-0 -> H1 (varianza domata dal search). Se anche
portfolio strand -38 -> H2 (bordo di capacita', serve repair/riduzione domanda). NON e' statistica:
e' un test di trattamento (applica una leva, misura se doma).

**Perché non lanciato stasera (19:13 sabato):**
- La diagnosi e' completa e azionabile senza run6 (varianza, modello+cura ok).
- Il path e' NON-BLOCCANTE: golden e' il default ed e' hard-0 affidabile; la robustezza hard-0 del
  path input e' una preoccupazione di flip futuro ("ready-when-needed"), non imminente.
- Portfolio e' codice DRAFT-005 ("NEVER adoption", gate laptop A/B): va vetato come draft prima di
  un one-off di misura (crash del draft spreca il setup).
- Priorita' della notte: DoD obbligatoria su `perimetro_residuo_report.py` (debt dichiarato) e
  spike di scouting > ricerca su path non bloccante.
- Costo: ~40-60min di solve (B-TIMESLOT Nx sequenziale + B-ROOM).

**Stato run6: READY-TO-RUN quando il flip diventa imminente o come prima cosa di una sessione
dedicata.** Avvio: rebuild (eventuale flag porting se si vuol provare demozione/repair) -> app
`java -jar` con `-Dplanner.portfolio.size=2 -Dplanner.pre-assign.constrained-teacher.source=input`
-> reset + import-calendario + pre-assign + solve-staged -> capture-run.sh.

## NOTE
- Questa diagnosi ribadisce e mechanizza il verdetto sera3 ("varianza LS, cura confermata"): qui
  ogni link e' groundato e la collocabilita' dei 16 e' provata dal placement-diff (run4 80/47 vs
  run5 69/42), non piu' per trust dell'handoff.
- Soft link dichiarato: identita' pre-solve (PLAUSIBLE). Per promuoverlo a CONFIRMED: re-run
  pre-assign due volte e diff dei pin authoritative (stateful, distrugge run5) o isolamento dei
  pin pre-solve dagli export (i pin promossi 7596 inquineranno il diff). Non necessario stasera:
  la conclusione azionabile non ci dipende.
