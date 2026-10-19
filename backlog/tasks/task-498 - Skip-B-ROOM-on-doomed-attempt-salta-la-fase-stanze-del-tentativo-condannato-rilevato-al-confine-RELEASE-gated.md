---
id: TASK-498
title: >-
  Skip-B-ROOM-on-doomed-attempt - salta la fase stanze del tentativo condannato
  rilevato al confine RELEASE (gated)
status: Done
assignee: []
created_date: '2026-08-14 23:27'
updated_date: '2026-08-15 06:10'
labels:
  - claude-planned
  - gated-maintainer
milestone: m-13
dependencies: []
priority: medium
type: enhancement
ordinal: 310000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
PROPOSTA GATED (creare solo, NON implementare stanotte). Al confine post-RELEASE (fine fase orari, ingresso fase stanze B-ROOM), se l'hard del tentativo e' piu' negativo della baseline room-null pura (-9404 sul seed 2026/27 = tutte le lezioni collocate, zero stanze), il tentativo e' CONDANNATO: il pick-best del multi-start lo scartera' comunque a favore di qualunque 0-hard. Saltare la sua fase stanze risparmia ~10 min/tentativo bad senza perdere nulla (la fase stanze non cura le firme timeslot-tier). EVIDENZA (analisi Fedora 14/08 sugli stream live): 5/6 bad del census n=19 rilevati al confine RELEASE (firma gia' presente prima delle stanze), ZERO falsi positivi su n=19. ECCEZIONE: seed-6 H29 room-tier NASCE nella fase stanze: al RELEASE e' baseline-puro, quindi il gate non lo salta e non lo danneggia (la sua fase stanze gira intera: comportamento invariato). ATTENZIONE SPECIFICA per seed-6: se un tentativo avesse SOLO la firma H29, il gate non lo salta (corretto); se un tentativo condannato avesse ANCHE una firma H29 curabile dalle stanze, il salvataggio parziale si perderebbe, ma il pick-best confronta hard TOTALE quindi il tentativo resterebbe comunque perdente contro un 0-hard. Flag default-OFF gated maintainer, stessa forma opt-in delle altre leve (lettura LIVE). Numeri T-minus: claudedocs/spalmone-2026-27/task459-results/dossier-costruzione-2026-08-15.md sezione 2.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Decisione di gating documentata (forma della soglia: hard < -9404 al RELEASE, o firma-specifica)
- [ ] #2 Zero falsi positivi su good verificati su un campione (n>=10 good con hard esattamente baseline al RELEASE)
- [ ] #3 Risparmio wall misurato su almeno 3 tentativi bad (~10 min/tentativo atteso)
- [ ] #4 Nessun impatto sui tentativi good (il flag non cambia il percorso dei good)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-15 06:10
---
MISURA laptop 2026-08-15 (reproduction-2026-08-15, 6 re-run con stream preservati): la separazione al confine RELEASE e' CONFERMATA ma con numeri diversi dalla premessa originale. Good (seed 5, 19): hard all'ultima B-TIMESLOT = -1809/-1833. Bad (seed 1, 9, 17, 18): -1847/-1851/-1848/-1985. Separazione 6/6 con margine SOTTILE di 14 unita' (worst good -1833 vs best bad -1847). Il -9404 della premessa non appare in questo protocollo: la baseline good al confine e' la banda [-1833, -1809] (timeslot-tier residuo dopo il repair, pre-stanze). IMPLICAZIONE per il design: una soglia FISSA e' fragile con margine 14; il gate dovrebbe usare la banda good come riferimento (es. hard < min(banda good del run corrente/campione) o confronto relativo), oppure threshold con isteresi + verify su piu' good. Script: scripts/analyze/score-stream-separation.py (la vista RELEASE-boundary riporta range e tight margin).
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
