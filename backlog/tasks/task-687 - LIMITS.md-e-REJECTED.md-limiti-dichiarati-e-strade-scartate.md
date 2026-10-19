---
id: TASK-687
title: 'LIMITS.md e REJECTED.md: limiti dichiarati e strade scartate'
status: Done
assignee:
  - claude
created_date: '2026-10-17 08:36'
updated_date: '2026-10-17 20:59'
labels:
  - claude-planned
milestone: m-16
dependencies:
  - TASK-683
  - TASK-684
type: docs
ordinal: 335000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
I limiti sono quasi tutti già auto-dichiarati nei VERDICT (il che è esso stesso parte dell'esempio di onestà metodologica); le strade scartate sono censite in claudedocs/constraint-recalibration-history.md e nei task bocciati. Servono due documenti in inglese che li rendano fruibili come indice puntato alle evidenze pseudonimizzate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 LIMITS.md enumera: tuning su singolo dataset con bracci n=1/n=2, pinnaggio Timefold 2.3.0 con wiring via reflection fragile, economie di wall-clock non portabili, regole hardware-specifiche, scadenza della metodologia golden-first (golden retirement), documentazione mista IT/EN, accoppiamento allo stack agent personale
- [x] #2 REJECTED.md indicizza le strade scartate puntando ai documenti pseudonimizzati come evidenza: entità Shadow/Block rimosse, demozioni H15/H16/H18/H21/H22 e promozione M36, TASK-430/432 bocciate al gate, CompositionHoleCloser shell-game, golden-monthly ritirata, profile predictor disarmato, M-TC flat per wall 2x, forma A dei pesi adattivi, esaurimento delle metaeuristiche con pivot al modello, blind-puzzle v1 senza cost-model
- [x] #3 Entrambi i documenti in inglese, zero PII alla verifica del censimento (TASK-682)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
LIMITS.md e REJECTED.md scritti in publish-prep/splits/ e cablati nel manifest via SPLIT_SOURCES (con la correzione al manifest: i path pubblici NON tracciati come STORY/LIMITS/REJECTED ora entrano esplicitamente dal loop SPLIT_SOURCES, prima solo CLAUDE.md passava perche' gia' tracciato). Zero-PII verificato col matcher su tutti e tre i documenti (REJECTED aveva 'H11-T28': riformulato in 'H11 external-teacher exception'). LIMITS copre: single-dataset tuning con bracci n=1/n=2 dichiarati, scadenza del golden (con puntatore alla direttiva input-only nel CLAUDE.md), pin Timefold 2.3.0 + wiring reflection fragile, wall-clock economics, regole hardware come lezioni non requisiti, mixed-language dichiarato, agent-workflow coupling, no CI (workflow rotto espulso). REJECTED indicizza con link alle evidenze nel manifest: Shadow/Block entities, le demozioni H15/H16/H18/H21/H22 e promozioni M36->H29/M18->H31, rimozioni S2/S4/S5/S6/S9/M4/M17/H11-exception, TASK-430/432 (con la nascita della regola placement-parity), CompositionHoleCloser shell-game, M-TC flat->per-retry, adaptive form A->B, esaurimento metaeuristiche, predictor early-doom falsificato, golden-monthly ritirata (artefatto parsing), blind-puzzle v1 NO SOLUTION, Kempe chains deferred, lab rotation seeding vuoto in attesa scuola.

Reviewer COVERAGE (687): 10/10 e 7/7 item presenti ma tre fix BLOCCANTI + uno mancante load-bearing, TUTTI applicati: (a) CompositionHoleCloser 'default-off forever' era STANTIO (ratificato DEFAULT ON col bundle endgame come bounded no-op: riscritto onestamente); (b) la lezione dei PESI promessa da STORY ora e' in REJECTED ('constraint weight sweeps as a schedule lever': M32/M34/M35/M37 flat su 1000x range, i driver sono i fatti di modello); (c) 4 link aggiunti (golden-monthly verdict, M-TC 455, adaptive task500 VERDICT, campaign-status-08-05); (d) NUOVA sezione LIMITS 'Seed-dependent outcomes' (la piu' load-bearing assente: la stessa code+data consegnano una distribuzione, ~1/4 bad-seed, link al verdict badseed-frequency). Inoltre blind-puzzle rinumerato onestamente ('second-generation near-miss puzzle: NO SOLUTION perche' lo spec non aveva il cost model') e link al VERDICT 636 (ARBITRAGGIO.md non nel manifest). Zero-PII + em-dash OK su tutti e tre i doc dopo le modifiche.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIMITS.md e REJECTED.md consegnati in publish-prep/splits/ e cablati nel manifest. LIMITS: 7/7 item AC + No CI (verificato contro manifest e workflow rotto) + NUOVA sezione Seed-dependent outcomes (la piu' load-bearing assente: ~1/4 bad-seed, link al verdict 480). REJECTED: 10/10 item AC con link evidenza + sezioni bonus (removed roll-up, Tested-and-closed [Kempe: provato 2x chiuso NO 0/6 e 0/10, non 'never needed'], Deferred). Correzioni fattuali applicate: Timefold 2.7.0 (il 2.3.0 era STANTIO: verificato pom.xml:16; anche PROJECT_INDEX.md corretto 3.31.4->3.40.1 e 2.3.0->2.7.0), reflection pin 'verified 2.3.0-2.7.0 range', wall-clock '25-90 minuti', H18->H31 'cinque mesi' (era 'years': da task-247 16/03 a TASK-529 19/08), CompositionHoleCloser riscritto (default-off mesi -> poi ratificato default-on bounded no-op), blind-puzzle rinumerato (v1 soluzione legale-ma-velenosa, v2 NO SOLUTION con prova: era l'inverso), S9 'disabled', Kempe riclassificato, 'most of them'. Le lezione dei PESI aggiunta ('weights move the score, not the schedule': STORY la prometteva). 5 link evidenza aggiunti. Gates: /simplify 3-angle round 1 (fact-check 17 claim: 14 OK 3 WRONG curati; style; coverage) + round finale 3-angle (duplicazione: 18 fatti 17 concordi; consistenza: 1 contraddizione cross-doc curata + virgola; sweep: link/PII/em-dash PASS) + code-review high chiusura. Zero-PII matcher: NESSUN hit su entrambi.
<!-- SECTION:FINAL_SUMMARY:END -->
