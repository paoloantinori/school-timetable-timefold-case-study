---
id: m-13
title: "m-13: Genericita' e riuso multi-anno (data-driven)"
---

## Description

Milestone: m-13: Genericita' e riuso multi-anno (data-driven)

## Stato: APPROVATO (ratifica maintainer 2026-08-11 + revisione indipendente Fedora 2026-08-13)

Il path source=input è chiuso proceduralmente: il multi-start (TASK-483, DEFAULT ON)
consegna 0-hard statisticamente garantito (pick-best su N tentativi), non deterministico
per costruzione.

### Dato che regge la decisione (verificato indipendentemente 2026-08-13)

Bad-seed frequency N=32 (pool laptop, due batch seed 1-16 + 17-32, config con M-TC per-retry ON):
- **p(0-hard) = 32/32 = 1,0**, Wilson95 ~[0,89, 1,0]. Bacino H17 non uscito in 32 costruzioni.
- Verifica Fedora grezza: 32 score.txt letti uno per uno, tutti 0hard. Confermato.
- I 2 run fallenti (-10hard seed-1, -1hard seed-6) appartengono al VECCHIO batch Fedora
  08-03 (pre-ratifica M-TC per-retry), directory diversa, NON nel pool N=32.

### Caveat onesti (registrati)

- Statistically guaranteed, NON deterministic per costruzione. Singola costruzione GRASP
  p~0,75-1,0; è il multi-start N=3 che porta P(>=1 0-hard) > 0,999.
- L'H17 resta LS-probabilistico: n=32 clean non lo esclude al 100%, lo rende raro + coperto
  dal multi-start N=3.
- within-machine (laptop sola); cross-environment non testato ma config identica.

### Cosa cambia

- m-13 si SBLOCCA (era BLOCKED on source=input H17). Path input chiuso proceduralmente.
- Source DEFAULT resta GOLDEN (decisione ready-when-needed invariata: medium migliore +
  vantaggio test finché la finestra golden è aperta). Flip ready-when-needed.
- NESSUN cambio al codice solver: multi-start già DEFAULT ON (TASK-483/486). Questa è una
  decisione di gating (accettazione del significato), non un'implementazione.

### Riferimenti

- Doc ratifica: `claudedocs/spalmone-2026-27/m13-procedural-closure-APPROVED-2026-08-11.md`
- Raw N=32: `task480-results/badseed-frequency-laptop-2026-08-09/` + `...-seeds17-32/`
- Verdict batch: `VERDICT-badseed-frequency-laptop-2026-08-09.md`, `VERDICT-badseed-n32-pooled-2026-08-10.md`

## RE-VALIDAZIONE del claim p(0-hard) sul vincolame corrente (13/09/2026, TASK-584)

**Il claim di sblocco del 11/08 e' SMENTITO sul vincolame attuale.** Batch notturno 12-13/09
(braccio a, SH OFF, seeds 101-108, single-attempt GRASP, protocollo documentato con
import-calendario 3077 pin, doppia guard 8/8): **p(0-hard) = 0/8, Wilson95 [0.000, 0.324]**,
consegne in banda stretta -27..-40, costruzione stabile -9765/-9768 su TUTTI i seed (bacino
cattivo strutturale, non fortuna di seme). Confronto dichiarato: 32/32 = 1,0 del 9-10/08 era
PRE-H31/H32/H33/H34 (i quattro hard DEFAULT ON del 19-26/08 hanno distrutto la raggiungibilita'
dello 0-hard per singola costruzione); anche il p=0,75 del 3/08 sta fuori e sopra il CI attuale.
Il multi-start NON rimedia: il banco M39 della stessa notte con maxAttempts=5 consegna solo
-11/-13 (tentativo deterministico; tutti i retry GRASP condannati). Il collo e' nel
MODELLO/ricerca sui vincoli H31..H34, non nel budget N (coerente con la direttiva primaria
correttezza-logica-sopra-il-costo). La m-13 resta quindi chiusa COME REGISTRO della decisione
dell'11/08, ma la sua premessa quantitativa va letta con questa data di scadenza: il claim
valeva sul vincolame di allora. Report: `task584-results/REPORT.md` (commit bc72b17d).
Sub-prodotti validati live: delivery concordance TASK-526 8/8 OK (prima validazione su
consegne consecutive), doom registry +8 righe (ciclo task584-shoff-13sep).
