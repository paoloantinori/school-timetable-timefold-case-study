---
id: TASK-604
title: >-
  Cura H34 come preferenza: prezzare l'ordinaria sui giorni S (M-tier
  specchio-M28) per riempire i giorni dichiarati vuoti
status: Done
assignee: []
created_date: '2026-09-12 06:33'
updated_date: '2026-09-12 22:38'
labels:
  - claude-planned
  - zero-hard-recovery
  - bench-first
  - medium-tier
milestone: m-14
dependencies: []
references:
  - TASK-599
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla classificazione completa del residuo spread-11sep ([-11/0]: 10 H34 + 1 H7; doc task599-results/spread-11sep/CLASSIFICAZIONE-RESIDUO-2026-09-12.md, commit 97c6aac8).

MECCANISMO (verificato in DB + fogli + golden): pool esaurito esattamente (slack 0 per ogni gruppo con celle vuote); l'ordinaria sui giorni S e' LEGALE per i gruppi non-FC (filtro quota VRP FC-only, TASK-385) e il golden la pratica al margine (29h/anno, 2 soli giorni misti); quando il pool finisce, niente fa preferire i giorni dichiarati-ordinari alle mattinate dei giorni S per l'ora marginale -> i giorni dichiarati giusti restano vuoti e le ore stanno sui giorni S/lab. H32 cieco dei giorni vuoti (il suo groupBy vede solo giorni CON lezioni), solo H34 paga flat 1/cella: prezzo basso, scambiato dalla ricerca.

CURA (PREFERENZA, non hard: golden-first perche' il golden pratica le 29h/anno): prezzare il collocamento ordinario su giorno quota-0-con-stage sopra il giorno dichiarato ordinario ancora libero. Forme candidate: (a) penalita' M-tier (specchio di M28 pomeridionale, ~100/lezione) sull'ordinaria in giorno con stageHours>0 e aula+lab=0, gruppi non-FC; (b) fill-tail/stamp ordering che antepone i giorni dichiarati. La (a) e' la piu' ortogonale (non tocca la costruzione) e parla alla LS/repair esattamente dove il trade avviene oggi.

METODO: banco sui checkpoint spread-11sep (sul laptop) o fixture, predizione scritta PRIMA. Predizione di massima: M-tier a ~100 sposta le ~16h ordinarie-in-S sui giorni dichiarati vuoti, H34 10 -> <=4 (2 celle G13 restano: giorni aula dichiarati FC-sheet con vicini 8h saturi, pura distribuzione), nessuna nuova violazione (le ore si spostano, il pool non cambia). Criteri di morte: se il banco non muove le ore o apre nuovi H17/H32 sopra il guadagno H34, la direzione muore.

Nota H7=1: drill API sulla consegna (chi sfora il cap giornaliero), minore.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
BANCO CONCLUSO 12/09 con VERDETTO DI MORTE DI FORMA, ratificato da maintainer la sera del 12/09 ("Boccio"). Braccio A (m39-off, controllo, quadro ratificato completo, guard cwd+banner superate) [-11/0]/[-1188844/1420] consegnato 15:09 dopo 3h21m (multi-start, vincitore attempt-1); braccio B (m39-on, banner enabled=true weight=100 verificato nel log alle 15:33:04) [-13/0]/[-1136506/967] dopo 3h12m. PREDIZIONE CONFUTATA sull'unica coppia internamente valida: H34 INVARIATA 3=3 (predizione H34 10-><=4 era calibrata sulla consegna spread-11sep, VOID per contaminazione, il bacino pulito ha composizione diversa: H34 3 + H32 3 + H17 3 + H0 1); tassa M39 = -100 = 1 sola lezione matching (previste ~23 migrazioni); hard PEGGIORE di 2 (H17 3->4, H7 0->1 nuovo). Criteri di morte pre-registrati scattati. ESITO: il flag planner.constraints.m39-stage-day.enabled resta nel codice opt-in DEFAULT OFF; nessuna adozione. Sottoprodotto di valore: il braccio A e' il PRIMO VETTORE PULITO del quadro ratificato (RA 41/41 verificata end-to-end da Fedora sui raw, pool 943=667+276, split 9 docenti byte-exact col golden) e alimenta TASK-596; la classificazione del residuo -11 e' in TASK-608 Fase A (le 3 H34 quasi-strutturali spiegano anche PERCHE' la predizione e' fallita: M39 e' una preferenza di piazzamento, non sposta ore, e le celle residue non hanno ore da preferire). REPORT: claudedocs/spalmone-2026-27/task604-results/REPORT.md. Incidente primo lancio braccio A (JAR worktree 29/08) registrato nel REPORT; guard anti-worktree promossa a protocollo.
<!-- SECTION:FINAL_SUMMARY:END -->
