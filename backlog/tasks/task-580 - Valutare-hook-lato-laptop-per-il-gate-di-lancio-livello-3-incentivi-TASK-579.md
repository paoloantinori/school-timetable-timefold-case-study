---
id: TASK-580
title: Valutare hook lato laptop per il gate di lancio (livello-3 incentivi TASK-579)
status: To Do
assignee: []
created_date: '2026-09-02 16:42'
labels:
  - process-hardening
  - laptop-ops
  - hooks
  - da-decidere
milestone: m-13
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Livello-3 degli incentivi TASK-579 (da valutare con calma, NON modifica frettolosa): un hook PreToolUse lato laptop sulla sessione di zai che intercetti i comandi Bash di lancio ciclo su planner (java -jar target/quarkus-app, prod-start-and-wait) e verifichi che il preflight sia stato eseguito (o lo invochi inline). MOTIVO: il livello-1 (preflight cablato in prod-start-and-wait, 2/09) copre chi passa dal launcher del repo; resta scoperto il lancio java -jar DIRETTO nel worktree, che e' esattamente come partivano i cicli della settimana degli incidenti. CAUTELE (perche' questo task non e' stato fatto subito): (1) la config del laptop e' HOST-SPECIFIC e sensibile (incidente 2026-08-14 settings.json copiato: mai deploy frettoloso); (2) un hook lasco diventa rumore bypassato (controesempio 2/09: hook curl locale che bloccava curl REMOTI per pattern matching); (3) prod-start-and-wait ora GIA' esegue il gate: l'hook coprirebbe solo il bypass esplicito del launcher: misurare prima se il bypass e' un caso reale o teorico. ALTERNATIVA piu' leggera da valutare insieme: il pre-commit hook del repo laptop-side e' l'unico hook che ha funzionato senza fastidi per un mese: studiare se il gate di lancio puo' vivere li' (es. un wrapper planner.sh che rifiuta java -jar diretto). DECISIONE di adozione: maintainer.
<!-- SECTION:DESCRIPTION:END -->
