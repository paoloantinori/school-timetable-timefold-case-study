# TASK-609 gate a ciclo reale: CompositeSwapFinisher v0.1 (A/B due bracci)

Nottata 13/09/2026, laptop (mandato notturno Fedora). Il pass `CompositeSwapFinisher`
(flag `planner.construction.composite-swap-finisher.enabled`, DEFAULT OFF, commit
020bac90+896a0ea1+57745e5b su HEAD 57745e5b) misurato NEL CICLO VERO: wiring,
timing, log shape e delta sulla consegna, appaiato su profili freschi.

## Protocollo

- JAR da HEAD `57745e5b` (i tre commit del pass), `PREFLIGHT-OK` (jar-fresh + flag-existence) su ENTRAMBI i boot.
- **Doppia guardia 2/2**: (A) `lsof` cwd = `/Users/user/planner` + banner `enabled=false` nel log alle 16:40:18, solve alle 16:40:39; (B) cwd ok + banner `enabled=true` alle 18:33:30, solve alle 18:33:45, preflight `flags-verified`.
- Profili freschi `t609a` / `t609b` (seed DataGenerator verificato nei log), ciclo completo staged default (multi-start maxAttempts=5, budgetSeconds=0), un solve alla volta, caffeinate attivo.
- Wall: **A 1h50m** (16:40:39→18:30:41), **B 1h53m** (18:33:45→20:26:24). Vincitore: tentativo 1 in ENTRAMBE le braccia; il tentativo 2 brucia i 32 start tutti scartati dal profile predictor (163-224 buchi H17 in costruzione > 150), poi consegna con rifinitura stanze del vincitore (corner-b).

## Risultati

| | A (flag OFF, controllo) | B (flag ON, trattamento) |
|---|---|---|
| hard consegna | **-9** | **-7** |
| H32 day-fill deficit | -4 (4 match) | -4 (4 match) |
| H34 empty declared day | -3 (3 match) | -1 (1 match) |
| H0 unassigned (boxed) | -2 (1 lezione) | 0 |
| H7 max teacher daily hours | 0 | **-2** |
| full-day | 93,5% | 93,7% |
| buchi interni / no-stanza | 0 / 0 | 0 / 0 |
| sanity (TASK-535) | NC: G07, G09, G10 (4 gg) | NC: G10 (1 gg) |
| concordance TASK-526 | OK (H29 mem=0 db=0) | OK (H29 mem=0 db=0) |
| riga del pass | ASSENTE (solo banner OFF) | `targets=0, relay2=0, relay3=0, direct=0` |
| esecuzioni del pass | 0 | **1** (20:09:18, tentativo 1, confine fine catena deterministica) |

Raw: `arm-a-default-off/` e `arm-b-flag-on/` (capture canonica completa + `planner-prod.log` per braccio).

## Predizione pre-registrata vs esito

1. **"B migliora 2..3 hard"**: numericamente VERIFICATA (-9 → -7 = +2), MA l'attribuzione al pass NON regge: il pass ha trovato **zero target** e non ha mosso nulla. Il +2 e' varianza del bacino (rifinitura di consegna LS room, intrinsecamente non byte-exact, ratifica TASK-543) piu' composizione: il -3→-1 di H34 e il -2 di H0 compensati da +2 di H7.
2. **"riga con targets>=1 (dipende dal bacino del vincitore)"**: SMENTITA sul punto (`targets=0`); ha retto la clausola di salvataggio pre-registrata. Nessuna lezione boxed arriva viva al punto del pass in NESSUNA delle due braccia.
3. **"nessuna nuova famiglia hard in B"**: alla lettera FALSO (H7 -2 in B, assente in A), MA meccanicamente NON attribuibile al pass: `targets=0` = zero mosse = il pass non puo' ne' creare ne' curare nulla. L'H7 -2 era gia' nel bacino B prima del pass (compare nel contesto del revert del pass one-off fratello alle 20:09:11, sette secondi prima). **Segnalato a Fedora**; nessun criterio di morte scatta perche' la sua ratio ("il pass e' difettato nel wiring") e' esclusa dal contatore a zero.
4. **"timing trascurabile"**: VERIFICATA (una sola esecuzione, istantanea a targets=0, scan VRP memoizzato).

## Criteri di morte

Nessuno scatta:
- B NON consegna peggio di A (-7 vs -9).
- Nessun `[REVERTED]` del pass (il revert delle 20:09:11 e' del pass one-off fratello, comportamento identico gia' osservato in arm A alle 18:13:36; oracle improve-or-noop sano).
- L'H7 -2 di B e' di bacino, non del pass (alibi meccanico: zero mosse).

## Verdetto operativo per Fedora (laptop misura soltanto)

- **Il wiring a ciclo reale e' VALIDATO**: banner gate 2/2, il pass esegue esattamente una volta al punto giusto della catena (tentativo 1), contatori loggati nel formato atteso, costo zero, nessun effetto collaterale.
- **L'efficacia NON e' dimostrata da questo gate**: sul bacino vincitore il residuo boxed non raggiunge mai il punto del pass. Nota strutturale: la consegna A contiene 1 lezione boxed (-2 H0) **nata a valle** del punto del pass (la rifinitura di consegna libera 2775 stanze non-autoritative e ricostruisce), mentre il banco ha provato il valore del pass sullo STATO CONSEGNATO (replay m39-off -11→-8, m39-on -13→-10, step9 no-op su stato pulito).
- Letture possibili, decisione a Fedora:
  - (a) riposizionare il pass dopo la rifinitura-di-consegna / pre-save, dove il banco l'ha provato (lo stato che il replay curava e' lo stato consegnato);
  - (b) tenere il flag opt-in e trattare il banco come evidenza primaria di efficacia;
  - (c) accettare che nel regime attuale (bacini con 0-1 boxed) il pass resti quasi-inerte in-ciclo.

## Incidenti

- Guard TASK-541 al primo boot A: container `planner-pg` stale sul volume `task584` (notte precedente); fermato e rimosso come da istruzioni della guard, rilancio pulito. Una sola volta.
- Due exit silenziosi di `api.sh wait` (fetch transitorio sotto carico MT-LS a 5 core): riscontrato vivo via jstack + CPU + mtime del log, nessun impatto.
- Prima build fallita per `npm: command not found` (PATH di questa shell senza homebrew); rilanciata con PATH corretto. Ambiente locale, non repo.

Gates: measurement-only (nessun sorgente toccato, JAR da HEAD committato), report-only → gate-exempt.
