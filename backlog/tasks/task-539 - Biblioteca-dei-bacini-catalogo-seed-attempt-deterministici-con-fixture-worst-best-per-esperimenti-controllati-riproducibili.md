---
id: TASK-539
title: >-
  Biblioteca dei bacini: catalogo seed-attempt deterministici con fixture
  worst/best per esperimenti controllati riproducibili
status: Done
assignee: []
created_date: '2026-08-21 09:32'
labels:
  - claude-planned
  - methodology
  - reproducibility
  - harness
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Idea maintainer 21/08 ("oggi che ci misuriamo il metodo"): con i run appaiati REPRODUCIBLE, il numero di tentativo GRASP e' l'indirizzo deterministico di un bacino. Catalogare i bacini del run appaiato 3-livelli (e poi del bendable, stessi seed) come biblioteca di esperimenti riproducibili: worst-case e best-case diventano fixture permanenti per prevalidare ogni nuova leva (vincolo/pass/LS) in minuti ('chiude il peggiore senza rompere il migliore') e come regressione continua (worst-basin test gated). Fonte dati: log per-attempt del run appaiato + export dei tentativi. Caveat: identita' bacino legata al commit di costruzione (ricatalogare dopo cambi tipo density-cap); riproducibilita' richiede la config appaiata (PHASE_ASSERT, LS 1 thread, portfolio off/dichiarato).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Catalogo basin-catalog.md: per ogni attempt K del run appaiato (seed K deterministico in PHASE_ASSERT+LS-1-thread): gate score, H17/H32/H33 residui, classificazione, commit del codice, path del fixture export
- [ ] #2 Fixture export dei tentativi worst e best salvati in claudedocs (o dir fixtures) con nome seed-attempt commit-referenziato
- [ ] #3 Runner/harness documentato (o scriptetto) per rieseguire replay/discriminator su un fixture dato: prevalidazione 'chiudi il worst senza rompere il best' in minuti
- [ ] #4 Il worst-basin entra come test gated nella suite (pattern discriminatori esistenti)
- [ ] #5 Avvertenze in testa al catalogo: identita' bacino legata al commit (ricatalogare dopo cambi di costruzione), riproducibilita' richiede PHASE_ASSERT + LS 1 thread + portfolio dichiarato
- [ ] #6 DoD standard
<!-- AC:END -->
## ESTENSIONE post-CH (maintainer, 21/08 pomeriggio)

Oltre al catalogo, correlare i bacini con lo STATO POST-COSTRUZIONE:
1. FEATURE POST-CH per ogni bacino noto: buchi H17 a fine costruzione, celle sotto-quota, distribuzione surplus, boxed, decomposizione score per famiglia dal construction-lessons.txt.
2. CORRELAZIONE: le feature separano i bacini buoni dai cattivi? (n iniziale 5+5: run appaiato + bendable; la biblioteca cresce a ogni run e la validazione e' continua, non one-shot).
3. Se separano: PREDITTORE EARLY-DOOM al confine CH (il wrapper salta la LS sui tentativi predetti-doomed: ~40 min risparmiati per bacino cattivo, il multi-start campiona piu' bacini nello stesso budget). E' la detection-T-minus del 14/08 spostata da RELEASE a CH.
4. VIZ POST-CH per attempt: fill-parity/holes sul construction-lessons.txt (formato gia' prodotto dal real-seed test), affiancato alla vista finale: la firma visiva del bacino cattivo per l'occhio (metodo chain-journey).

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
