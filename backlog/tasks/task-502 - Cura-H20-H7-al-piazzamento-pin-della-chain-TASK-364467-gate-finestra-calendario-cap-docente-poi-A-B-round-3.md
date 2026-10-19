---
id: TASK-502
title: >-
  Cura H20/H7 al piazzamento pin della chain TASK-364+467 (gate
  finestra-calendario + cap-docente, poi A/B round 3)
status: Done
assignee: []
created_date: '2026-08-16 15:59'
updated_date: '2026-08-17 05:07'
labels:
  - claude-planned
  - solver
  - pre-assign
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Esito A/B round 2 (task467-results/ab-chain-2026-08-16/VERDICT.md, verificato indipendentemente da Fedora 2026-08-16): treatment -64hard su 5/5 tentativi (H0 -28, H20 -21, H7 -15), control 0hard al primo tentativo in 23 min. Root cause verificata col grep: AdaptiveSlotFinder/GreedyRotationAssigner non validano la finestra calendario del gruppo (solo bound array slot) ne' il cap giornaliero docente; i blocchi derivati dal golden (fino a 8h) vengono pinnati su weekday la cui finestra e' piu' stretta del blocco. Approvato da maintainer 2026-08-16 sera ("va bene"): nessuna adozione (flag restano DEFAULT OFF), la meccanica enumerator e' prosciolta, si cura il piazzamento.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Smoke chain completo con flag ON: i pin derivati non producono NESSUNA violazione H20/H7 a fine pre-assign (verifica con analysis su post-pre-assign, non solo a fine solve)
- [ ] #2 L'enumerator multi-candidato resta ingaggiato (il fix non deve cortocircuitare la scelta dei piani: piano rifiutato/adottato nei log)
- [ ] #3 A/B round 3 (stesso protocollo baseline-golden.sh appaiato): hard 0 in ENTRAMBE le braccia, KPI in banda, ricorrenza unpinned non degradata
- [ ] #4 Nel run3 verificare coerenza score.txt vs analysis.json su medium (round 2 treatment: -1.717.953 vs -2.099.400, discrepanza -382k specifica del treatment, meccanismo non spiegato): se si ripresenta, aprire diagnosi separata prima di qualunque adozione
- [ ] #5 Test localizzato per il gate (matrice modifica -> catena minima da claudedocs/localized-testing-guidelines.md) + DoD standard (simplify, code-review high, test.sh EXIT 0 da orchestratore)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## ROUND 3 (16/08 sera, HEAD ceefa787): cura CONFERMATA sui bersagli, gate FAILED sul room-tier, 3 bug nuovi documentati

Control 0hard/-1.045.035 in 23 min (KPI in banda: full-day 87,8, ric 74,1, buchi 0). Treatment 5/5 attempt falliti: H29 -7739 dominante, H0 -40, H17 -14, H7 -2; full-day 67,7, ric 69,5, buchi 14, wall ~126 min.

AC#1 PASSA (H20 0 e H7 0 sui pin a fine pre-assign, analysis). AC#2 PASSA (enumerator ingaggiato: piano 1 rifiutato dal fail-fast, piano 2 adottato). La cura: H20 -21 -> 0, H7 -15 -> -2; igiene a monte 0 clipped/0 scartati.

AC#3 FAILED (hard 0 solo nel control). AC#4: discrepanza score.txt(-42hard)/analysis.json(-7795hard) REAPPARSa e AMPILIFICATA all'hard, treatment-specific, documentata nel VERDICT con 2 bug associati: NPE SimpleFieldTracker nella move thread (tentativo 5 abortito, score null) e percorso ship-best-CH-only-rooms. AC#5: catena localizzata 7 classi verdi + unit test H7 tracking.

RACCOMANDAZIONE: cura TENUTA (prerequisito), flag restano DEFAULT OFF, NESSUNA adozione. Precondizioni round 4: diagnosi H29 (blocchi rifiutati -> room-tier collapse), stack NPE multi-thread, percorso no-0-hard + discrepanza score/DB.

## ROUND 4-PREVIEW (terzo mandato notturno, HEAD 44d25b7f + eca22847): guard forma (a) annuale INERTE sul caso round-3, marker quiescenza VALIDATO, -42 deterministico

Implementata forma (a): applyTeacherBudgetGuard in Phase 2 (flag planner.pre-assign.teacher-budget.enabled DEFAULT OFF, banner), regola pura teacherBudgetAllowsPin (aritmetica annuale: free/week x 34 settimane - lab >= non-lab), fix null-date sul calcolo finestra (effectiveRange NPE su regole date-scoped G13, 44d25b7f). Test TeacherBudgetGuardTest 5/5 + catena localizzata 29/29 + golden regression + real-seed VERDI.

A/B run4 (chain ON entrambe, guard OFF vs ON, n=1/braccio): STESSO ESITO -42hard (H0 -40 = 20 boxed x2 + H7 -2 composizione identica), 20 boxed G04/RA/T15, full-day 74,7/73,9, ricorrenza 69,1/69,0, wall ~2h20. ZERO declini: l'aggregato annuale (646 slot liberi annui vs 172 lab + 50 non-lab) passa il pin perche' i slot liberi stanno nei weekday sbagliati (lun/ven; il golden mette la RA di giovedi'). CONCLUSIONE onesta: la forma (a) annuale non e' la cura; servirebbe l'intersezione per-weekday (quota-spare gruppo ∩ disponibilita' docente residua), da valutare.

BUONE NOTIZIE: quinto tentativo raggiunge -42 da solo in entrambe le braccie (non-doomed, B-ROOM rifinitura completa 603s): maxAttempts=5 esattamente sufficiente, corner-b non necessario. score.txt == analysis.json sull'HARD (marker refiningForDelivery 5846dfbf VALIDATO live: la rifinitura non viene piu' uccisa; il medium resta divergente per il meccanismo TASK-509 latestScore-vs-DB, ora isolato al solo medium). Nessuna NPE tracker in 4h30 MT-LS.

PER maintainer: chain 364+467 arriva da sola a -42 con N=5 (4 run concordi: -64, -42, -42, -42). Domanda: -42 (20 ore RA bloccate dai pin) accettabile, o intersezione per-weekday, o forma (c) sulla condanna? VERDICT sezione ROUND 4-PREVIEW.

## ROUND 5-PREVIEW (quarto mandato, 1f273456 + 0e7a7818 + b0f5a32f): guard raffinato a INTERSEZIONE per-weekday + ablation run5

Filone 1: guard raffinato (stesso flag). Regola: somma_i min(spare gruppo su i, residuo docente su i dopo il pin) >= pool non-lab. ESITO ONESTO pinncato nel test: coi numeri MISURATI run4 l'intersezione somma a 345 >= 50, NEANCHE la granularita' per-weekday declina T15. La famiglia dei test di capacita' aggregata e' REFUTA: il blocco vive a livello di piazzamento esatto. Bug trovato e fissato dal run5 (0e7a7818): teacherPinnedByWeekday fondeva TUTTI i docenti, residuo collassava a 0, over-decline di 6 pair acconciatura.

Run5 (guard buggy in volo, ablation di fatto): -42hard, H0 -40 + H7 -2, STESSI 20 boxed, full-day 76,3, ric 70,5, wall 134 min, tentativi 1-4 a -8027 CH-only, quinto risolve da solo. LETTURA: togliere TUTTI i pin acconciatura non cambia nulla -> i boxed dipendono dai pin mec/inf/ter di T15 (intatti in run5) e nessun test di capacita' li prevede. Follow-up proposto: probe di piazzamento esatto (perche' ExactFinisher depth<=1 non trova cella per la RA nei lun/ven liberi con quota-spare), NON un altro run del guard. Guard corretto committato/testato ma mai eseguito in run (atteso identico a run4).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
