---
id: TASK-673
title: >-
  Arma curativa del banco PureOneOff: iniettare one-off nel checkpoint
  integrato-7 e pinnare la chiusura di OneOffFinisher
status: Done
assignee: []
created_date: '2026-09-28 09:53'
updated_date: '2026-09-28 13:27'
labels: []
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla review /simplify+code-review del re-pin TASK-672 (28/09): il re-pin ha reso il banco PureOneOff una guardia di stato (RED -1 + APPLY no-op), ma il percorso CURATIVO end-to-end di OneOffFinisher resta scoperto OVUNQUE: (1) il vecchio braccio APPLY dei round TASK-574 (l'unico pin di chiusura reale delle one-off) e' superseded con il checkpoint perduto; (2) OneOffFinisherTest copre solo rifiuto-piano/atomicita' e una slide legale minimale su fixture sintetica. La catena ratificata previene la ricomparsa delle one-off, quindi "aspetta che riemergano" = mai: l'arma curativa va costruita INIETTANDO i target nello stato pulito.

FORMA: terzo test nel PureOneOffCheckpointTest (env PLANNER_TEST_PURE1OFF_INJECT=1 + EXPORT): parte dal checkpoint integrato-7 (pin -1), inietta una one-off per famiglia (es. room=null su una lezione non-lab con stanza legale disponibile altrove -> H29 + famiglia B; settima ora su un docente con cap 6 -> H7 + famiglia A), gira OneOffFinisher.repair e pinnare la chiusura (score di nuovo -1, movesApplied >= 1, famiglia chiusa). Derivare i pin dal breakdown DIAG che il banco stampa gia'.

NOTA LATENTE del reviewer (per il disegno dell'iniezione): le famiglie B/C del finisher sono PIU' LARGHE dei vincoli H29/H33 (B non esenta le pinned, C conta le ore Stage e ignora lo scope quota-row): su stati futuri puo' legittimamente scattare movesApplied>0 con tutti e tre i punteggi vincolo a 0. L'iniezione H7 (famiglia A) e' l'unica con popolazione IDENTICA al vincolo: preferirla come prima arma.

Dipende da:TASK-672 (checkpoint committato).
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Chiuso 28/09 (decisone Fedora: accettare l'auto-skip). Consegna: terzo braccio INJECT del PureOneOffCheckpointTest (env PLANNER_TEST_PURE1OFF_INJECT=1 + EXPORT, commit e5aa8406, gates completi: /simplify 4-reviewer + code-review high 8-angle con fix applicati, suite 1400/0/0 EXIT 0, tutto costruito e verificate SUL laptop per ordine maintainer). COSA FA: cerca un sito di iniezione H7 deterministico (docente-giorno a cap efficace esatto, lezione same-teacher same-group same-room non-pinned non-Sicurezza presa a fine blocco H31/H17-safe, slot libero gruppo+docente+stanza dentro il VRP range FRESH TASK-578, giorno sorgente a/over quota dichiarata), inietta (-2/0 atteso), gira OneOffFinisher.repair e pinnla chiusura (movesApplied>=1, overloadsRelieved>=1, ritorno a -1/0, shell-game guard: nessuna famiglia nuova). ESITO SU integrato-7: found=false con censimento (199 giorni-docente cap-6; scarti: 6249 removal-underflows-quota-or-floor, 40 teacher-busy, 14 not-end-of-block, 5 same-subject): la consegna e' COSI' convergente (ogni giorno a floor/quota esatta) che nessuna iniezione senza danno esiste; l'arma si auto-skippi onestamente (assumeTrue + censimento in log) e SI ARMA DA SOLA su bacini con slack. Copertura curativa raggiungibile puntando EXPORT a un checkpoint con slack dopo re-deriva dei pin (la struttura ora esiste). Compilazione in suite: auto-skip = il braccio gira skipped nel default, zero impatto.
<!-- SECTION:FINAL_SUMMARY:END -->
