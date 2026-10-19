---
id: TASK-426
title: >-
  Day-start residuo post-adozione: 95,7-96,9% vs golden 99,3 - anatomia dei
  ~70-95 giorni non ancorati
status: Done
assignee: []
created_date: '2026-07-27 04:45'
updated_date: '2026-07-27 19:07'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: low
ordinal: 249000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
TASK-405 (Done) porto' 81->96,2-96,7 con M18 a peso 50 + left-anchor in costruzione; lo staged adottato misura 95,7-96,9. Restano ~70-95 giorni-gruppo non ancorati per run. PRIMO PASSO: anatomia (chi sono? pattern per gruppo/weekday? quanti nascono dal template stamp vs LS vs repair? il veto M18 di SurplusRebalance li protegge, HoleRepair logga 0 DE-ANCHORS, quindi la sorgente e' a monte). Da checkpoint: classificatore sui dump train2 + confronto con golden day-start per-gruppo. SOLO DOPO l'anatomia decidere la leva (peso M18? left-anchor post-release gated? niente se sono strutturali da calendario). Dipendenza: dopo TASK-424 (il pin-netting cambia gli incentivi di riempimento del primo slot).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANATOMIA COMPLETATA (report: claudedocs/spalmone-2026-27/task426-daystart-anatomy-2026-07-27.md, 164 righe). READ-ONLY, nessun codice.

90 giorni non ancorati (95,9% vs golden 99,86%); G11 32 + G04 21 = 59%; mercoledi 37%; 0 strutturali / 88 solver-choice / 2 mixed-pin (100% solver-addressable, slot 0 sempre calendar-open).

SORGENTE = costruzione (phase-2-end ha gia' 82; LS/repair +8 netto; HoleRepair/SurplusRebalance esonerati, conferma task note upstream).

CAUSA-RADICE = H1 teacher conflict cross-gruppo allo slot 0 (G11 mercoledi: ogni docente prenotato alle 8 per un altro gruppo; golden distribuisce diversamente). 84/90 sono giorni che il golden ancora.

LEVA (decisa DOPO anatomia, ma qui la conclusione): NON M18. Il blocco e' HARD (H1), M18 (medium ~6300/-2,3M = rounding error) non puo' chiuderlo a nessun peso. La leva reale e' a livello COSTRUZIONE (template slot-0 teacher balancing cross-gruppo) o una mossa LS che rompe la contese slot-0 → follow-up oltre m-12.

DoD N/A (anatomia read-only, nessun codice da /simplify/code-review/test.sh). Deliverable = il report.

SPIKE AnchorRepair (2026-07-27 notte, gate reali): import chiude 89->16 / 82->20 (con M18-guard donatori; senza guard e' shell game, 53 de-ancorati). Residuo = saturazione slot-0 cross-gruppo (mercoledi' G11), converge con 430; piano completo in TASK-433. Promozione M18->hard: NON ora, riaprire a parita' golden (<=3).
<!-- SECTION:NOTES:END -->
