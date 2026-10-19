---
id: TASK-605
title: >-
  Audit contaminazione JAR-worktree: classificare le 17 consegne laptop dal 29/08
  (void/pulite/sconosciute) e correggere i verdetti dipendenti
status: Done
assignee: []
created_date: '2026-09-12 10:30'
updated_date: '2026-09-12 10:54'
labels:
  - claude-planned
  - audit
  - data-integrity
milestone: m-14
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Contaminazione da JAR-worktree: la copia di prod-start-and-wait.sh nel worktree day-compact-542 serve il JAR del worktree (build 29/08) quando il cwd di zai scivola li'. Finestra di esposizione: dal 29/08 (nascita del JAR nel worktree) a oggi. GUARD anti-worktree committata (c6ae19e4): la classe e' chiusa per i lanci futuri. Questo task auditare il PASSATO: 17 consegne laptop nella finestra.

CLASSIFICAZIONE FINO A ORA:
- VOID (prova meccanica Recuperi/pool-626, post-RA): control-11sep, spread-11sep. Cade: "prima misura end-to-end cura RA" (mai avvenuta); "gate TASK-599 sul quadro attuale" (il lever era nel build 29/08, quindi il delta [-26]->[-11] resta leggibile come evidenza di meccanismo su vecchio quadro).
- PULITE (propria evidenza): v12-5sep, v132-7sep, notte-4sep (DATI SurplusHarvest nei VERDICT: codice 1/09 assente dal JAR 29/08); slotretry-9sep (banner surplus-harvest=true verificato nel VERDICT + la bocciatura slot-retry veniva dal BANCO FEDORA H34 22->68, non dal ciclo).
- SCONOSCIUTE (nessun marker nei propri file): control-10sep (flag solo checkpoint, nessun banner verificato nel REPORT), v11-n2, v123-6sep, v131-7sep, edfh33-7/8sep, boxdef-8sep, twin-tail-a1, pin-7sep, ciclo-sh-on/fix2/v10/v11, ab-grasp-s25-29, task576 e1/e1bis.

METODO per ogni sconosciuta: cercare marker meccanico per-data (dati di meccanismi post-29/08 nei REPORT/analysis, banner con valori, firme del formato di consegna che il codice cambiava), oppure classificare per distanza HEAD-dichiarato vs 29/08 rispetto al meccanismo misurato (la contaminazione voida solo dove il meccanismo/quadro misurato differisce fra 29/08 e l'HEAD dichiarato: meccanismi gia' presenti nel build 29/08 misurati su quel build = misura valida).

VERDATTI/DECISIONI dipendenti da cicli nella finestra: slot-retry BOCIATO = SALVO (banco Fedora); residual ledger TASK-539.01 = fixture valide COME STATI (content-addressed), provenance da correggere sui 2 void; TASK-584 affidabilita' produzione = dati in finestra da riclassificare; lettura varianza-bacino -13/-17/-26 = riscrivere (possibili braccia stesso-codice); ratifica TASK-599 = gamba ciclo caduta, gamba banco locale salda (decisione maintainer gia' presa: tenere).

Non serve rilanciare cicli: solo riclassificare i verdetti e correggere la documentazione (REPORT/VERDICT con avviso, memoria, checkpoint).
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso in giornata (12/09, commit d62e79d2, su richiesta maintainer "la 2 non puoi farla oggi?"). Classificazione meccanica completa: 11 PULITE (marker genetico SurplusHarvest/OneOff/banner nei propri artifact: v11-n2, notte-4sep, v12, v123, v131, v132, slotretry, boxdef, ciclo-sh-on, ciclo-sh-fix2, pin-7sep), 2 VOID (control-11sep, spread-11sep: discriminante RA provata), 4 VALIDE-COMUNQUE (edfh33-7/8, twin-tail-a1, ab-grasp: meccanismi misurati pre-29/08 presenti nel JAR-worktree), 1 IGNOTA-BASSO-IMPATTO (control-10sep: checkpoint sovrascritti e nessun banner, ma le affermazioni portanti del [-17] sono pool-cap-fill-indipendenti; cadono l'etichetta quadro e l'attribuzione G08 gia' ritrattata). VERDATTI STORICI TUTTI SALVI (slot-retry bocciato = banco Fedora; SurplusHarvest = pass girato con contatori; pin bocciato = banner + controverifica Fedora; boxdef = banner osservato; ledger = v132 pulita). Correzioni: avvisi contaminazione sui 2 REPORT void, doc AUDIT-CONTAMINAZIONE-2026-09-12.md con evidenza per ciclo. Meccanizzazione: capture-doctor check genetico seed-RA (WARN jar-stale), batteria 14/14 + braccio stale verificato.
<!-- SECTION:FINAL_SUMMARY:END -->
