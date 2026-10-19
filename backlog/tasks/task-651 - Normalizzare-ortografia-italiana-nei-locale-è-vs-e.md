---
id: TASK-651
title: Normalizzare ortografia italiana nei locale (è vs e')
status: Done
assignee: []
created_date: '2026-09-23 05:38'
updated_date: '2026-09-27 18:58'
labels:
  - claude-planned
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'unslop TASK-650 (23/09): i locale italiani mescolano "e/e'" senza apostrofo e "è" con accento, a volte nella stessa stringa e in file diversi. Normalizzarlo sarebbe uno sweep di centinaia di siti, fuori dal contratto unslop della 650. Decisione da prendere: quale forma canonica (probabile: "e'" senza accento, coerente con la maggiornaza dei docs tecnici del repo, oppure "è" vero per la pagina pubblica). Sweep meccanico + validazione JSON + typecheck quando deciso. Priorita' bassa: pura cosmetica.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-27 NORMALIZZAZIONE COMPLETA (1f8456d7): 102 sostituzioni in 8 file locale italiani. Forma canonica: ACCENTATA (è, può, perché, più, già, così, capacità, mercoledì, ecc.) per il prodotto scolastico. Sweep meccanico via Python con validazione JSON post-scrittura, zero residui ASCII-apostrofo. gate-exempt: solo traduzione + typecheck OK.
<!-- SECTION:NOTES:END -->
