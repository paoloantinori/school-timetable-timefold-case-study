---
id: TASK-556
title: >-
  Riempimento dei giorni vuoti (M20.2/M22 family): costruire la capacita' che il
  golden pratica
status: Done
assignee: []
created_date: '2026-08-25 20:37'
updated_date: '2026-08-26 07:14'
labels:
  - claude-planned
dependencies: []
priority: high
type: enhancement
ordinal: 314000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DECISIONE maintainer 22:35: "i giorni pieni del golden noi li vogliamo anche noi. I nostri vincoli non li ha chiesti (forse), ma la scuola li vuole: dobbiamo trovare un modo, sia CH, LS, late phases o qualunque altro approccio."

OBIETTIVO: chiudere il gap ~0,9M concentrato su GIORNI emerso dalla rimisura (task-555 backlog): M20.2 empty-expected-day 51 celle nostre vs 3 golden (-503,5k vs -21,5k), M22 calendar-compactness 14 giorni vs 0 (-415k).

APPROCCIO (suggerito da maintainer/via):
(a) riprendere il filone day-compact (TASK-543, fermato per doppia smentita: ma il NOSTRO residuo era strutturale boxed/H29, non "solo il metro" - la forma del residuo oggi e' diversa, 0hard consegna con giorni buchi);
(b) ANALISI DA EXPORT PRIMA DI CODICE: per le 51 celle M20.2 e i 14 giorni M22: quali gruppi/date, e cosa occorrerebbe per riempirli (materie con ore residue disponibili? docenti liberi in quelle celle? cap H7? capacita' aula? e' packing o saturazione?);
(c) candidatos: QuotaFillRepair potenziato (oggi chiude 26->8 celle, i.e. ~2/3 del suo banco: cosa gli manca?), block-shift T549 (gia' prototipato default-OFF), pre-booking in costruzione (celle scarse prenotate a monte).

REGOLE: predizione PRIMA nel registro per ogni misura; prototipo dietro flag default-OFF (le 2 domande referente collegate - G12 giorni-vuoti voluti-o-subiti, RA back-load - restano il modo per sapere SE quei giorni vadano riempiti: si costruisce il meccanismo comunque, si attiva dopo la risposta).

NOTA DI QUADRO: questo filone e' la controparte del relaxed-solve (TASK-546: meno vincoli all'inizio): qui si tratta di PIU' capacita' da trovare/attivare, non togliere. Riferimenti: registro 25/08 (rimisura golden, ispezione visiva maintainer, sanity giorni-corti); task550-roomtail/golden-remisura-metro-attuale.json; ciclo-medium-pura-on/analysis.json (breakdown per famiglia).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
REVISIONE NOTTURNA 25/08 (verdetto completo in claudodocs/spalmone-2026-27/task556-results/VERDICT-analisi-giorni-2026-08-25.md, commit f5915e2b): l'analisi da export HA CADUTO la premessa del DayConsolidation. (1) M22 e' finzione strutturale: il golden unpinned-equivalente pagherebbe 262 giorni-eccesso vs nostri 240 (min-days usa maxHoursPerDay contro quote dichiarate sottili; penalizza la forma del foglio; nessun consolidamento possibile senza rompere H32 floor). (2) M20.2 = 28 celle FC/G14 over-declare pool-cap (~-293k, by design) + 21 reali (~-200k), riempibili SOLO dal surplus sopra-quota (160h, per-gruppo stretti) perche' la conservazione e' perfetta. PIVOT: l'item implementativo diventa lo sprint QFR (donor ordering + riprove, atteso -100..-150k), dietro gate maintainer. Proposta emendamento al metro: M22 min-days quota-aware (NON applicato). Le domande referente restano la via per decidere se il foglio vada cambiato.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-26 07:14
---
26/08 mattina, FORMALITA' same-seed completata (mandato item 1): ciclo dci-556e2e col gate (e) end-to-end, consegna -6hard/-1.309.447. QFR occ.1 57 mosse/14 celle/0 conflitti/0 revert (vs 52/14 localizzato), occ.2 pulita, M20.2 49->38 confermato, medium in banda, sanity 3 rossi (bacino, meno dei 5: causalita' QFR gia' confutata col controllo). Hard -6 = lotteria del single-attempt (same-seed non implica same-basin: MT-LS non byte-exact), classe coperta dal multi-start N=5. VERDETTO: gate di adozione CHIUSO con evidenza pulita; flag resta DEFAULT OFF, decide maintainer. Doc: task556-results/ciclo-gate-e-e2e/VERDICT-2026-08-26.md (commit 69baf0bd).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
