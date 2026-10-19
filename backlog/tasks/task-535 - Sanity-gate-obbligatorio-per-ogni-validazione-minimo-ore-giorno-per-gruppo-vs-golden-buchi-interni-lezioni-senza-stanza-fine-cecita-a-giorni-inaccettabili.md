---
id: TASK-535
title: >-
  Sanity gate obbligatorio per ogni validazione: minimo ore/giorno per gruppo vs
  golden, buchi interni, lezioni senza stanza (fine cecita' a giorni
  inaccettabili)
status: Done
assignee: []
created_date: '2026-08-20 04:44'
labels:
  - claude-planned
  - kpi
  - validation-gate
  - dataviz
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Post-rimprovero maintainer 20/08 mattina ("IO VOGLIO CHE TU NON SIA PIU' CIECO"): la validazione delle soluzioni ha escluso per costruzione cio' che l'occhio di maintainer trova in 10 minuti (giorni sotto il minimo golden, buchi H17, lezioni senza stanza). Il prototipo dello script esiste gia' (sanity.py, girato su export live 20/08: G04 11 giorni sotto min 4h con giorni da 2h; G10 43 buchi; G07 19; G11 18; G08 5; 72 lezioni pin aula senza stanza nei G09). Il gate deve essere meccanico e BLINDANTE: la tabella e' la prima cosa letta in ogni validazione, un rosso impedisce il verdetto positivo. Nessuna eccezione per "bacino sfortunato": con hard residuo il run e' non conforme.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 scripts/analyze/solution-sanity-check.py: per ogni gruppo calcola min ore/giorno run vs min golden, giorni sotto minimo, conteggio buchi interni 1h (H17-like), lezioni senza stanza; output tabellare + exit code non-zero se presente almeno un rosso
- [ ] #2 Integrato in capture-run.sh: ogni capture produce sanity-report.txt
- [ ] #3 Il gate diventa protocollo: nessun verdetto di validazione senza la tabella; un rosso = verdetto non conforme (0hard binario)
- [ ] #4 Verificato retrospettivamente su h32-run1, qfr-run1, qfr3: il gate avrebbe bocciato tutti e tre con i rossi giusti (qfr3: G04 11 giorni, G10 43 buchi, MODA 72 roomless)
- [ ] #5 DoD standard
<!-- AC:END -->

created: 2026-08-20 07:15
---
TASK-535 IMPLEMENTATO E VALIDATO (250be485): scripts/analyze/solution-sanity-check.py (gate blindante: min ore/giorno run vs pavimento per-gruppo, giorni sotto minimo, buchi interni 1h su SLOT INDEX non ora wall-clock - la pausa pranzo 12-14 non e' buco - lezioni senza stanza con split pinnate) + integrazione capture-run.sh (sanity-report.txt; rosso = exit 1 ma la capture sopravvive). RETROSPETTIVA AC#4 su h32-run1 / qfr-run1 / qfr3: il gate boccia TUTTI e tre con i rossi giusti. qfr3 = G04 11 giorni sotto min 4h (incl. i due da 2h: 17/09 e 02/04), G07 19 buchi, G10 40 giorni-con-buco, G11 15, MODA 72 pin roomless (24x3). Nota metodo: buchi 40/15 vs occhio 43/18 differiscono per criterio di conteggio (qui = giorni-con-buco, slot-based); direzione e severita' identiche.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
