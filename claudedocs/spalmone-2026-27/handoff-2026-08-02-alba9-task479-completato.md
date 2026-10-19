# HANDOFF 2026-08-02 alba9 (zai). TASK-479 COMPLETATO (gap-filling + citation fix).

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (commit `877d65a2`, da pushare).
> TASK-479 (pagina architettura layer di orchestration) ora GENUINAMENTE completo. Regole standing
> invariate (verdetti dai raw, commit+push, tempi assoluti, handoff+/clear a ~450k, mai fermo,
> DoD obbligatoria, default source resta golden, GRASP opt-in OFF).

## QUADRO (stato generale)

- **TASK-480 chiuso e verificato (Fedora PASS).** Vedi sotto, sezione dedicata. Le 5 affermazioni
  dell'utente combaciano tutte coi raw.
- **TASK-479 completato per davvero.** La chiusura alba5 era prematura (doc ~90% ma con lacune +
  citazioni stale). L'utente (post-/clear) lo aveva correttamente identificato. Alba9 ha riempito
  i gap e corretto le citazioni. Solo doc, NESSUN cambio di comportamento.
- **TASK-481, TASK-478** restano Done (immutati). **TASK-482** (Kempe-chain) resta To Do (piano B).
- **App DOWN** (invariato dall'alba8; l'A/B terminava col kill-app dell'ultimo run). Profilo
  `multistart` esiste con lo stato dell'ultimo run GRASP. **Per ripristinare production**:
  `./planner.sh stop && ./planner.sh start production`.

## ESITO TASK-480 (Fedora verification, richiesta utente)

Richiesta: verificare che TASK-480 sia chiuso e il verdetto corretto (multi-start = hedge non
miglioramento, pick-best winner = baseline, seed-1 ha stressato l'H17, onesta' apprezzata,
autocorrezione bug SUMMARY con raw preservato). **TUTTE E CINQUE VERIFICATE** contro i raw:

1. **hedge non miglioramento** = VERO. I run GRASP a 0-hard (grasp-2 -1,31M, grasp-3 -1,30M) hanno
   medium PEGGIORE del baseline (-1,03M, ~270k meglio). GRASP = polizza contro bacini cattivi, non
   guadagno di medium. (Verdetto sez 4.2.)
2. **pick-best winner = baseline** = VERO. `baseline-grasp-off = 0hard/-1029611medium`, il medium
   migliore tra gli 0-hard.
3. **seed-1 ha stressato l'H17** = VERO. `grasp-seed-1 = -11hard`, `H17 -10/10match` + `H29 -1`,
   fenotipo G11-mercoledi (10 mercoledi consecutivi 2026-09-16..11-18), slot migrato @10->@11.
   Il verdetto stesso dice "NON e' il caso 'tutti 0-hard, H17 non stressata'".
4. **onesta'** = VERA. Limitazione n=3 / CI larga [0,50; 1,00] dichiarata; stima frequenza bad-seed
   = misura DIFFERITA in background (~10-20 run), non bloccante.
5. **autocorrezione bug SUMMARY con raw preservato** = VERA. Letto direttamente il `SUMMARY.txt`:
   la riga buggata originale ("0-hard GRASP runs: 3 / 3") e' INTATTA in cima, e il blocco
   `=== POST-RUN CORRECTION ===` e' appeso in fondo. Raw truth = 2/3 GRASP (3/4 totale). Lo script
   e' stato fixato (baseline contato separatamente + riga pick-best winner).

Backlog TASK-480: `status: Done`, DoD spuntata 3/3, Final Summary completa. Chiusura corretta.

## COSA E' STATO FATTO (alba9)

1. **TASK-479 RIAPERTO e COMPLETATO.** Riconciliazione onesta: handoff alba8 riga 93 diceva
   "TASK-479 Done" ma era la chiusura prematura alba5. L'utente ha identificato che non era
   completo (credeva solo sez 4.1/4.3 scritte; in realta' il doc esisteva ~90%). Alba9 = completare
   cio' che mancava, NON riscrivere.
2. **GENUINE GAPS filled** in `orchestration-layer-architecture.md`:
   - **CompositionHoleCloser** aggiunto al census operatori (entry/exit/rollback/gate + claim
     "inert on saturated basin" verificato vs `CompositionHoleCloser.java`: assert promoted-pins-empty
     :150-154, two-move revert :235-239, isDepartureSafe rifiuta mid-run blocker, discriminator
     SUSTAINABLE 0/6 / SHELL-GAME 6/6). Era committed default-OFF (opt-in) ma mancava dal census.
   - **TASK-482 Kempe-chain** aggiunto come roadmap sez 4.4 (rotation -> 4.5). Era il documentato
     piano B ma non censito.
   - **Pointer CLAUDE.md** aggiunto in Reference Documentation (esisteva solo in PROJECT_INDEX.md
     Documentation Map + solver-phases.md RELATED).
3. **CITATION DRIFT FIX (critico).** Le ~25 citazioni file:riga di alba5 erano STALE di +7..+30
   righe: il `ScheduleService.java` e' slittato (inserzione CompositionHoleCloser + altri edit), e
   una citazione "AnchorRepair (`ScheduleService.java:1425-1431`)" puntava in realta' al javadoc di
   CompositionHoleCloser. Tutte ricalcolate vs il file reale e verificate meccanicamente (grep
   `sed -n` per ogni citation, 21/22 OK; 1 falso-positivo del grep su un range corretto). Esempi:
   ROOM_TIER_HARD_FAMILIES 841->842, timeslotTierHard 1646->1675, isRoomTierHardConstraint
   1663->1692, rollback repair (HoleRepair 1380->1387, LDC 1404->1411, AnchorRepair 1425->1454,
   Surplus 1450->1479, EF 1471->1500), cycle order 11->12 step (CompositionHoleCloser step 8 a
   :1432), config extractors (1115/1177/1187/1225), coordinator publish (refreshSolverStatics
   1974->1975, M32 publish 2232->2281, co-presenza 2012->2057), DRAFT-005 portfolio 1308->1326.
4. **DoD docs-equivalent** (task docs-only, NESSUN cambio di comportamento, conferma "DoD leggera"
   richiesta = verifica riferimenti file:riga):
   - scope clean: `git diff` solo `.md` (CLAUDE.md +1, arch doc +56/-29), 0 `.java`/`.xml`/`solverConfig`.
   - citazioni file:riga verificate vs codice reale (grep meccanico).
   - 0 em-dash (`grep -c "—" = 0`).
   - **invariant-sweep skill** sui contratti: 9 invarianti tracciati, 0 broken, 0 unverifiable.
   - `/simplify` + `/code-review high` + `test.sh` N/A per diff docs-only (DoD box lasciate
     deselezionate onestamente; la DoD docs-equivalent e' documentata nel commento del task).
5. **Backlog TASK-479**: commento + final-summary append documentano il gap-filling alba9.

## STATO RUN / APP / INFRA

- **App DOWN** (invariato alba8). Profilo `multistart` col stato dell'ultimo run GRASP.
- **Nessun solve, nessuna build** in alba9 (task puramente documentale). planner-pg fermo.
- Risorse: 12 core / 36 GB RAM.
- Commit alba9 `877d65a2` (docs + backlog TASK-479). Handoff in commit separato. **Da pushare.**

## PUNTI DI RIPRESA / DEBITO RESIDUO

- **Misura differita (NON bloccante, background):** stimare frequenza bad-seed con ~10-20 run GRASP
  addizionali sul profilo `multistart` (`multistart-ab.sh` gira detached; caffeinate). Da k/n si
  dimensiona N per target P(>=1 0-hard). Solo wall-clock nei tempi morti.
- **TASK-482 Kempe-chain (piano B):** aprire SOLO se la stima differita mostra p(buono) basso o N
  inaccettabile. Ora e' censito nella roadmap dell'arch doc (sez 4.4).
- **Debito censito (skipped, fuori scope docs):** `planner.sh prod --fresh` dispatch bug
  (`planner.sh:599`, `"$@"`); same-seed GRASP reproducibility test mancante; multistart-ab.sh capture
  su wait-timeout cattura snapshot live (flaggare non-final o kill-app prima del capture).
- **Citazioni file:riga sono fragili per natura** (il file slitta a ogni edit Java): se si censicono
  nuovi operatori o si tocca ScheduleService, ricalcolare. Il doc alba9 e' allineato al commit `877d65a2`.

## PUNTATORI

- Arch doc: `claudedocs/orchestration-layer-architecture.md` (censimento + contratti + letteratura +
  roadmap 4.1-4.5 + rapporto solver-phases). Pointer in `CLAUDE.md` Reference Documentation,
  `PROJECT_INDEX.md` Documentation Map, `solver-phases.md` RELATED.
- TASK-480 verdetto: `task480-results/VERDICT-fase4-multistart-ab-2026-08-02.md`. A/B raw:
  `task480-results/multistart-ab/` + `SUMMARY.txt` (raw + correzione appesa).
- Backlog: TASK-479 Done (completato alba9); TASK-480 Done; TASK-481 Done; TASK-482 To Do (piano B);
  TASK-478 Done.
- Handoff precedenti: alba8 (TASK-480 done), alba7 (A/B in corso), alba6 (TASK-481 shell-game fork).
