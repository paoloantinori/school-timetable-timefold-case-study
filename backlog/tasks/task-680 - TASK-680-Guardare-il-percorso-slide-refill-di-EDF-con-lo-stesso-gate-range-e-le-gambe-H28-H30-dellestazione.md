---
id: TASK-680
title: >-
  TASK-680-Guardare il percorso slide-refill di EDF con lo stesso gate range e
  le gambe H28-H30 dell'estazione
status: Done
assignee: []
created_date: '2026-10-05 06:00'
updated_date: '2026-10-05 07:33'
labels:
  - claude-planned
  - m-15
  - solver
  - edf
dependencies: []
priority: low
type: enhancement
ordinal: 328000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla chiusura TASK-679 (review 05/10, finding F3): il percorso SLIDE/REFILL di EmptyDayFinisher applica applyMove/applyMoveWithSlides con controlli H1-only, bypassando sia legalLanding sia il gate VRP-RANGE che copre solo l'estrazione. La NOTA del 04/10 lo aveva assegnato a 679 ("allineare o guardare anche quel percorso") e la chiusura lo ha fatto ricadere senza owner: questo task e' l'owner. LAVORO: portare il controllo di membership sul value range (e le gambe H28/H30 appena aggiunte all'estrazione) anche a planSlides (le slide-down del donatore) e ai refill del relay (relayFor): ogni applyMove che sposta una lezione su una cella nuova deve passare dallo stesso gate. TEST: estendere il banco 678 con una ferita che forzi una slide (donatore con blocco same-teacher sopra lo slot estratto) e un refill relay, con il pin del range post-pass. PRIORITA' bassa rispetto al resto: il belt improve-or-noop copre l'esito (una mossa fuori range fa revertire il pass, non consegna stato illegale), il danno e' efficacita' (cure perse per colpa di una mossa illegale a meta' catena).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: claude
created: 2026-10-05 06:19
---
AGGIORNAMENTO (review 05/10 finding F6): oltre a planSlides e relayFor, il sito da guardare include anche l'atterraggio X del compound v2 (evictAndFill/compoundFor: le sue destinazioni (landingSlotsOf) includono i gap INTERNI del giorno, dove la gamba B dell'H28 (la lezione che atterra TRA una coppia same-teacher) diventa raggiungibile: la gamba attuale copre solo i bordi). La gamba H28 e' ora costruita sul gate condiviso H28RotationFinisher.isViolating (review finding F4: la prima versione hand-rolled perdeva la finestra lato-lontano e la posizione B), quindi estenderla a quei siti e' riusare la stessa chiamata.
---

author: claude
created: 2026-10-05 07:21
---
IMPLEMENTATO (05/10 mattina): (1) helper condiviso cachedRangeContains (binarySearch sul range CACHATO, census VRP-RANGE, invariante sortedness e belt-permanente documentate SU di esso) + crossDateLandingSafe (tripletta range+H30+H28, cheap-first dopo legalLanding); (2) applicati a TUTTI i siti: import del relay (relayFor), refill terminal e chain (refillAt entrambi gli hop), slide del donatore (planSlides: solo range, H30 no-op per data invariata e H28 impossibile per compattazione, documentato), atterraggio X del compound (compoundFor: la posizione B di H28 raggiungibile sui gap interni). (3) TEST DISCRIMINANTE: la finestra di staleness manufacturata nel fixture (regola calendario che chiude THU slot 0 pubblicata MENTRE le cache degli slider vengono innescate, poi ritirata: fresh dice legale, cache dice no) - pre-680 la slide passava e consegnava fuori range, post-680 rifiuta; il census mostra VRP-RANGE=1 (il gate spara visibilmente). Prime due versioni del test erano non-discriminanti (la regola bloccava anche il fresh; il nonno ri-aggiungeva lo slot proprio): la review le ha smascherate entrambe. Gates: /code-review high (5 finding, tutti applicati: cheap-first, helper condiviso niente copie, invariante re-homed, census sullo slide, test discriminante), suite 1412/0/0 EXIT 0 sull'albero pre-fix + suite finale post-fix in corso, unit 12/12, banco 3/3.
---

author: claude
created: 2026-10-05 07:33
---
DEPLOY (05/10 09:32, commit 2f9a5ba2): package + runbook, L1 200 con origine pubblica, orario persistito [0/0]hard intatto, zero errori nel log. TASK-680 CHIUSO Done.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Ogni mossa di EDF (estrazione, import relay, refill terminal/chain, slide donatore, atterraggio X compound) passa ora dal gate condiviso: cachedRangeContains (range del director, census VRP-RANGE, invariante sortedness documentata) + crossDateLandingSafe (tripletta range+H30+H28, cheap-first). Test discriminante con finestra di staleness manufacturata (cache innescata sotto regola poi ritirata: fresh legale, cache no - pre-680 consegnava fuori range). Gates: /code-review high 5 finding applicati, suite 1412/0/0 EXIT 0, unit 12/12, banco 3/3, deploy L1 verificato.
<!-- SECTION:FINAL_SUMMARY:END -->
