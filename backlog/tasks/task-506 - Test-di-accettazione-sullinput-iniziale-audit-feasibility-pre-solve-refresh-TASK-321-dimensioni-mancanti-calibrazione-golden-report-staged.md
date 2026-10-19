---
id: TASK-506
title: >-
  Test di accettazione sull'input iniziale (audit feasibility pre-solve: refresh
  TASK-321 + dimensioni mancanti + calibrazione golden + report staged)
status: To Do
assignee: []
created_date: '2026-08-16 16:19'
updated_date: '2026-08-16 17:17'
labels:
  - claude-planned
  - validation
  - input-quality
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Battery di test di accettazione sull'input iniziale che anticipano, senza girare alcun solve, le condizioni per cui 0-hard e' impossibile o a rischio. Basato sul catalogo di eventi reali della campagna: triangolo G06 overdemand +30h (TASK-435), pin H20 del deriver su finestre piu' strette dei blocchi (TASK-502 round 2), 835-vs-750 di G04 (TASK-497 + domanda referente), saturazione tag mono-stanza (grasp-seed-6). Esiste gia' feasibility-audit.py (TASK-321, 3 dimensioni: docente/gruppo/tag) ma e' FERMO: date hardcodate 2025/26 (mut sul seed 2026/27), offline, non cablato, e gli mancano le dimensioni che quest'anno hanno morso davvero. Proprieta' di calibrazione: ogni check deve passare sul golden (l'unico input noto-feasibile), stessa disciplina dei golden regression; la finestra di calibrazione si chiude col golden. Nota architetturale: ValidationService e' un watchdog di legalita' della soluzione corrente (TASK-354), non un audit dell'input: le due cose coesistono.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 STADIO 1: feasibility-audit.py aggiornato al 2026/27 (2026-09-14..2027-06-10) e le dimensioni mancanti aggiunte: quota-vs-finestra per (gruppo, weekday), coerenza incarichi-vs-calendario (pool vs atteso, la famiglia 835-vs-750), deadline H30 (ore Sicurezza vs capacita' pre-stage) e H25 (ore stage vs giorni stage), domanda quota-bound H23/H24 vs giorni con quota lab>0, offerta tag raffinata per (weekday, slot)
- [ ] #2 Ogni dimensione riporta la classe di triage: INFEASIBLE (contraddizione dimostrata), OVERDEMAND (bound soft superato, si parte ma si sa cosa si paghera'), QUESTION (famiglie di input in disaccordo, voce workbook per referente)
- [ ] #3 STADIO 2: gate di calibrazione golden, ogni check PASSA sul golden importato (test in CI accanto ai golden regression); un check che scatta sul golden e' classificato: bug del check (si corregge) o pratica della scuola (si dichiara nel workbook, NON si sopprime)
- [ ] #4 STADIO 3 (solo dopo gate 1+2): report pre-solve nel ciclo staged, pubblicato accanto allo stage PRE-ASSIGN, visibile in UI, NON bloccante tranne classe INFEASIBLE
- [ ] #5 Limite documentato nel task: la varianza di ricerca (bad-seed tail) NON e' visibile dall'input per costruzione; l'audit completa multi-start e skip-doomed-room, non li sostituisce
- [ ] #6 Stadio 1 eseguibile offline (db-query.sh read-only), quindi compatibile con le finestre di attesa dei solve A/B in corso
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## STADIO 1 eseguito (16/08 sera, seconda finestra di attesa del round 3)

Commit 1e7dec24. Audit eseguito sul profilo fresco del round 3 control (stesso seed 2026/27 del production path).

FIX DI CORRETTEZZA (il primo giro produceva falsi): pseudo-docente Stage escluso dal dim 1; VACATION a tempo parziale (T05/T04/T11 14:30-16:30) non liquidano il giorno intero; dim 5 confronta pool NON-stage vs piano aula+lab; dim 8 MODA = N/A (lab da pin, non da quota).

FINDING SUL SEED (verbatim):

- Verdetto aggregato: FEASIBLE sulle dimensioni strutturali (golden-coerente).

- Dim 4 quota-vs-finestra (H20 family): NESSUNA cella in eccedenza. NEGATIVO VALIOSO: le quadre del round-2 H20 venivano dal NOSTRO piazzamento pin, non da contraddizione input.

- Dim 5 pool-vs-piano: G04 pool 750 vs piano 835 (delta -85), G08 626 vs 666 (-40), G03 990 vs 969 (+21), G07 695 vs 690 (+5), G10 670 vs 666 (+4), G14 -5, G09 -4, resto 0.

- Dim 6 H30: G07 Sicurezza 12h vs 960 pre-stage: passa.

- Dim 7 H25 OVERDEMAND: G04 stage 240h vs 28 giorni x cap 8 = 224 (+16). Unico overbound stage.

- Dim 8 lab-vs-giorni-quota OVERDEMAND: G07 64h vs 6 giorni cap 48 (+16), G08 224 vs 26x8=208 (+16), G12 131 vs 15x8=120 (+11), G03 285 vs 46x6=276 (+9). MODA N/A pin-based.

- Dim 9 tag-per-weekday QUESTION: informatica Gio 9 gruppi vs 2 stanne (+7), informatica Lun/Mar/Ven 5 vs 2, acconciatura Gio 4 vs 1 (+3), meccanica Gio 3 vs 1.

STADIO 2 (calibrazione golden) NON eseguito: differito per priorita' mandato. STADIO 3 mai senza gate.
<!-- SECTION:NOTES:END -->
