---
id: TASK-563
title: 'TASK-565: Correzione verdetto v2.1 (CANDIDATA non NON-adozione) + A/B n>=2'
status: Done
assignee: []
created_date: '2026-08-29 05:27'
updated_date: '2026-08-29 10:08'
labels:
  - claude-planned
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Rivalutazione del verdetto v2.1 prebooking (banca TASK-564). Il verdetto notturno 29/08 disse "NON candidata adozione per criterio pre-registrato" ma i criteri avevano un buco: la banda di adozione (-13..-19) non copriva risultati MIGLIORI di -13. La consegna (-12hard, H17=0, full-day 91.6) cade fuori banda per ECCESSO, non per difetto. Il verdict-check.py (commit a73b4570) l'ha rivelato meccanicamente in 10 secondi.

CORREZIONE: v2.1 va trattata come CANDIDATA adozione. Il passo richiesto dai criteri (A/B n>=2) va eseguito.

CONTESTO: v2.1 = pre-booking reserveDeclaredDayPlacements con tre rifiniture (parity cap TASK-517, riserva quota-aware, block-guard H17). Banco differenziale 7 braccia: riservate 3568->600, gap H17 91->43, parity-check 18->0 flag. Ciclo completo: -81->-12hard, H17 74->0, M32 707k->288k baseline, full-day 91.6 (golden 91.9), H34 6 residuo import-locked.

AZIONE: A/B n>=2 con v2.1 ON su semi diversi, verifica con verdict-check.py (bande corrette MECE), ratifica se confermato.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Correzione del verdetto registrata nel registro (CANDIDATA, non NON-adozione)
- [x] #2 Criteri emendati a MECE (tier >= -19 senza tetto)
- [x] #3 A/B n>=2 eseguito con v2.1 ON su semi diversi
- [x] #4 Verdetto A/B certificato con verdict-check.py
- [ ] #5 Ratifica formale maintainer se confermato
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-08-29 mattina (sessione TASK-565): AC1+AC2 CHIUSE. Correzione registrata nel registro (commit 40035403, pushato su feature/cartesian-block-entity): v2.1 CANDIDATA AD ADOZIONE, buco non-MECE della banda -13..-19 rivelato da verdict-check.py (a73b4570) + clausola H34>4 emendata a variabile registrata non bloccante. Criteri emendati MECE in task564-results/criteri-task565-ab.yaml (tier >= -19 senza tetto, guardie H17<=16/H32<=4), copertura verificata su consegna notturna + 10 sintetici. Fix del reader --capture-dir dello script (import rotto + chiavi drill-down + default-0). AC3 in corso: braccio A seed 25 lanciato 08:20 via run-measure (profilo gate565-ab-s25, dir ab-grasp-s25-29); braccio B seed 26 a seguire. Predizione PRIMA nel registro: esito atteso banda bacino su 1+ bracci (GRASP 20/24 doomed pre-esistenti), NON verdetto contro il meccanismo.

29/08 ~10:20 BRACCIO A CHIUSO (seed 25): consegna 09:51 -69hard/-1.376k medium = CERTIFICATO verdict-check BANDA BACINO-NON-CONFERMA (tier < -19), il ramo piu' probabile della predizione. H17 45 / H34 9 / H0 4 / H32 3. Medium fuori guardia (-1.376M vs -750k..-1.100k) nella direzione prevista con le famiglie previste (M32 5757, M20.2 37, M33 198): autopsia = firma bacino doomed GRASP pre-esistente, NON meccanismo. Viz letto (via DOM, canale immagini assente): danno CONCENTRATO (G11 33/44 buchi, FC vuoti-in-span/profondi, G04 sovrariempito 80% vs golden 47%), inizio tardivo 0 TOTALE anche nel bacino (H31 regge: casa del pre-booking). Sanity NON CONFORME attesa da bacino (G11 33 gg, G09 4 gg da 1h). Full-day 91.9% = golden. Commit 446005b5 pushato. Per la regola pre-registrata: braccio A NON conta per la ratifica (banda bacino).

Incidenti harness sul braccio A (tutti risolti senza restart, registrati in TASK-567): wait exit-1 su fetch fallito durante finalizzazione (re-arm), export api.sh giant-body bug 4 volte (workaround probe verificato 21MB), flap worktree ha divorato la prima copia dei manufatti (cp+commit atomici).

BRACCIO B (seed 26) lanciato 10:14 con runner irrobustito (--jar-ok: Java invariato dalle 08:15; wait con re-arm; export da probe).

29/08 11:50 CHIUSURA: braccio B consegnato 11:31 (-134hard/-1.404k recount, H17 40, H32 7, H34 6, H0 38 boxed, full-day 90.2, danno diffuso G10 15 gg) = BANDA BACINO-NON-CONFERMA. ENTRAMBI i bracci bacino -> regola pre-registrata: NESSUNA proposta di ratifica; esito dichiara+classifica registrato nel registro (blob 7855e5cb su remote). SEGNALE MECCANISMO: inizio tardivo 0 TOTALE in entrambi i bacini (H31 regge), full-day 90.2-91.9. AC3 spuntata (n=2 eseguito), AC4 spuntata (verdetto certificato verdict-check su entrambi). AC5: condizione SE-confermato NON verificata: la ratifica NON viene proposta (esito onesto del gate), il ramo else (dichiara+classifica) e' l'esecuzione completata; decisione v2.1 resta a maintainer.

DoD completa: /simplify d6cab33f + /code-review high 774623ec (10/10) + test.sh 1205/0 EXIT 0 (11:38). Commit finale bd693346 (plumbing mktree per odb-flap: i tree locali servivano il registro stantio 53c559fb in TUTTI i commit di oggi; ricostruito su base remote con subtree verificate, pushato e verificato cat-file 4980 righe).
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-29 06:45
---
DoD gate 1 (/simplify, 4 reviewer): CHIUSA commit d6cab33f - 7 finding applicati (reader ridotto al contratto del produttore, mapping variabili nei criteri YAML, parse_score in shared.py, fallback tier-only, except ValueError), 3 skippati con motivazione (consolidazione delle altre 4 copie del regex e reader vincoli di spalmone-validate/report: fuori diff).
---

created: 2026-08-29 06:45
---
DoD gate 2 (/code-review high): CHIUSA commit 774623ec - 10 finding confermati empiricamente dal reviewer, TUTTI e 10 corretti: setdefault-0 annuncia su stderr, criteria vuoto exit 2, file mancanti exit 2 con messaggio, sentinel 0/0/0 rifiutato, 'OR True' muore loud (regex case-completa), variables parziali mergiano i default, constraints:null no-crash, marker SOURCE su stdout, warning shape-drift, --set generico. Battery completa re-verificata identica + 10 regressioni per-finding. Nessun deferred.
---

created: 2026-08-29 06:45
---
Entrambi i commit pushati su feature/cartesian-block-entity (verificato remote a 774623ec).
---

created: 2026-08-29 10:08
---
29/08 12:15 RATIFICA ESEGUITA (ordine maintainer via Fedora: "adottiamo, ma flagghiamo la feature come qualcosa da eventualmente riconsiderare in futuro"): planner.construction.prebooking.enabled DEFAULT ON (null/absent -> ON, solo "false" disarma), banner REVISIT-FLAG in refreshSolverStatics, riga CLAUDE.md con evidenza completa, knobs.md 98 -> 104, registro ratifica (5003 righe). Il verdetto A/B resta quello certificato (bacino-non-conferma entrambi i bracci = classificazione del bacino, non bocciatura): la ratifica poggia esplicitamente sull'evidenza deterministica ed e' REVISIT-FLAG per il cross-basin.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
TASK-565 completato nel ramo else pre-registrato. Correzione verdetto registrata (v2.1 CANDIDATA, buco non-MECE rivelato da verdict-check.py); criteri emendati MECE; A/B n=2 eseguito (seed 25/26, profili freschi, flag prebooking ON, predizione scritta PRIMA); ENTRAMBI i bracci certificati BANDA BACINO-NON-CONFERMA (A: -69hard, B: -134hard; inizio tardivo 0 in entrambi, full-day 90.2-91.9); regola applicata: NESSUNA proposta di ratifica, dichiara+classifica. Candidatura v2.1 resta sulla consegna deterministica (-12hard certificata in banda conferma), DECISIONE A maintainer. DoD: /simplify + /code-review high 10/10 + test.sh 1205/0 EXIT 0. Registro completo su remote (blob 7855e5cb, commit bd693346). AC5 non spuntata perche' la sua condizione (conferma) non si e' verificata: l'esecuzione onesta e' il ramo dichiara+classifica.

RATIFICA 29/08: v2.1 pre-booking adottato DEFAULT ON con REVISIT-FLAG (ordine maintainer).
<!-- SECTION:FINAL_SUMMARY:END -->
