---
id: TASK-615
title: >-
  TASK-615 - GUIDE A MONTE: usare le famiglie di mosse cieche come bias della
  COSTRUZIONE (prevenire i difetti invece di curarli a valle)
status: To Do
assignee: []
created_date: '2026-09-14 22:01'
labels:
  - claude-planned
  - construction
  - upstream
  - design-question
milestone: m-14
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DOMANDA DI maintainer (15/09 notte): 'invece di implementarle come fix a valle, non possono essere usate come guide a monte nella creazione della soluzione?'. TESI: le famiglie di mosse scoperte col metodo cieco (RELAY-k import+riporto TASK-612, ROTATION-WITH-PERMISSION TASK-613) sono conoscenza sullo STATO FINALE desiderato; oggi curano a valle (finisher sullo stato consegnato) ma potrebbero guidare la COSTRUZIONE a monte cosicche' i difetti non nascano.

IPOTESI CONCRETE da esplorare (dall'evidenza dei due round):
1. SLACK-STRUTTURALE: la round-2 insegna che i buchi H17 si chiudono con lo slack dei gruppi esenti (1/G08, G14) usati come buffer docente. Se la costruzione (DayClusteredConstructionPhase / WeeklyTemplateBuilder / stamp) NON impaccasse i giorni dei gruppi esenti al massimo ma lasciasse 1 cella di flessibilita' nei giorni dove i docenti condivisi lavorano, lo slide della coda diventerebbe diretto (zero rotazioni). Metrica: quante celle H17 nascerebbero.
2. CONTIGUITA'-FIRST NELLO STAMP: i buchi nascono quando lo stamp/coda rompono la contiguita'; una regola di stamping che preferisce estensioni di coda anziane inserimenti a mezzo giorno ridurrebbe i buchi in nascita (l'evidenza: la v0.2 chiude proprio con lo slide della coda, la forma che lo stamp dovrebbe produrre spontaneamente).
3. DONOR-AWARE QUOTA FILL: la famiglia fill (H34/H32) esiste perche' la costruzione front-loada o lascia giorni sotto quota; il relay di TASK-612 dice che il collocamento dovrebbe preferire i giorni che SONO donatori legali (surplus reale, coda estraibile): un biased fill che guarda la stessa geometria del relay in nascita.
METODO: banco di confronto sul replay (conteggio difetti in nascita a costruzione finita, prima dei repair) braccio guida-vs-control; nessun tocco ai pin autoritativi; se la prova regge, A/B a ciclo completo come sempre. DIPENDE DA: nulla (evidenza gia' nei census); SINERGIA con TASK-610 (le stesse famiglie come Move LS) e TASK-611 (LS post-repair: tre posti dove la stessa conoscenza puo' entrare).
<!-- SECTION:DESCRIPTION:END -->
