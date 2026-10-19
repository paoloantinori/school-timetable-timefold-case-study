# Censimento flag DEFAULT OFF vs DIRETTIVA BOUNDED-NO-OP (15/09 sera; aggiornato 16/09 post-ratifica)

Procedura della direttiva primaria "ATTIVA TUTTO CIO' CHE E' BOUNDED A NO-OP" (CLAUDE.md,
6b101421): censimento dei flag default-OFF da knobs.md, classificazione, e destino di ogni
classe. Classi: (a) pass delivery sotto oracolo improve-or-noop full-hard con gate per-mossa
616-style -> candidati ON; (b) costruzione (cambiano lo stato da cui parte il ciclo, niente
revert-or-improve); (c) vincolo/modello/pesi (cambiano il paesaggio); (d) compute-negativi;
(e) rollback-only o by-design opt-in.

## Classe (a): oracle-bounded, RATIFICATI DEFAULT ON 16/09 (endgame bundle, maintainer: re-A/B 258ac6cbe)

| Flag | Meccanismo | Stato |
|---|---|---|
| planner.construction.hole-relay.compact-swap.enabled | leva A, compact-swap intra-classe nel HRF (gate 616 dentro, verifyComposite) | RATIFICATO ON (no-op pieno re-A/B; positivo B3 -9->-8 / A3) |
| planner.construction.edf-relay.tol-donor.enabled | leva B, gate donatore tolerance-aware nell'EDF (same-group only, cross-group strict) | RATIFICATO ON (scoping Boundary.DELIVERY: divergenza mid-cycle rimossa) |
| planner.composition-hole-closer.enabled (SENZA .construction: nome esatto beccato dal preflight dell'A/B) | CompositionHoleCloser TASK-481: scaffold CORRETTO con guard anti-shell-game (isDepartureSafe), misurato inerte sul bacino noto, oracle improve-or-noop. NOTA A/B 16/09: STRUCTURALMENTE DISARMATO dentro il multi-start (clearAttemptLevers TASK-503 cancella la proprieta' a ogni tentativo: CLI-non-armabile finche' non curato) | RATIFICATO ON (cura clearAttemptLevers nel tree: banner 45/45 true nel re-A/B) |
| planner.construction.empty-day-finisher.v2-swap.enabled | swap-compound cross-group dell'EDF, misurata INERTE (0 compounds), improve-or-noop | RATIFICATO ON come assicurazione censita ('0 swap-compound(s)' nel [delivery]) |
| planner.construction.hole-relay.import-cross-date.enabled (16/09) | TASK-619 terzo braccio HRF: import stesso-gruppo cross-date nel buco | RATIFICATO ON (banco A3 -10/B3 -8 puliti, gate+re-cert 5/5; banner proprio) |

## Classe (b): costruzione (dietro le loro gate, mai regola-bounded)

planner.construction.grasp.enabled (+seed), box-deficit-only, coda-prima, day-compact,
planner.construction.empty-day-finisher.v2... no: v2-swap e' delivery; qui stanno anche
pair-cells builder (M38, +18 H32-family misurate = netto negativo), scan-forward (615,
falsificato raw, cap-2 borderline), teacher-weekday-spread e' gia' ratificato ON (non in
lista). Regola: nessuna attivazione senza il suo gate di adozione; il cap-2 selettivo ha la
condizione di riesame scritta nel task 615.

## Classe (c): vincolo/modello

planner.constraints.theory-adjacent.enabled (M-TR prototipo, peso inerte), m39-stage-day
(prototipo), tag-capacity M-TC flat (= classe (d): wall 2x), quasi-tier (sperimentale,
gia' in forma ratificata dove adottato). Cambiano lo score landscape: mai regola-bounded.

## Classe (d): compute-negativi

planner.tag-capacity.enabled (M-TC flat: 2x wall, bocciato; la forma per-retry TASK-486 e'
gia' ratificata ON altrove).

## Classe (e): rollback-only / by-design

planner.constraints.g14-fill-exempt.enabled (ripristina il comportamento rimosso, solo
emergenze), pre-assign.source=golden (rollback del flip ratificato), multistart opt-out,
opt-out delle ratifiche (tutti i 'false' disarmano: non sono attivazioni).

## Manutenzione

Ogni nuovo flag default-OFF va classificato qui ALLA CREAZIONE (stessa regola del
banner/census: niente ON o OFF silenziosi). Il bundle (a) e' STATO RATIFICATO il 16/09 (commit di ratifica sui verdetti del re-A/B 258ac6cbe): la classe e' ora vuota di candidati.
