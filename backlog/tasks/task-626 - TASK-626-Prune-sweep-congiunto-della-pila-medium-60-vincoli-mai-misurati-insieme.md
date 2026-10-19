---
id: TASK-626
title: >-
  TASK-626 - Prune sweep congiunto della pila medium (60+ vincoli mai misurati
  insieme)
status: Done
assignee: []
created_date: '2026-09-18 10:16'
updated_date: '2026-09-19 05:01'
labels:
  - claude-planned
  - m-15
milestone: m-15
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla discussione maintainer 18/09 (m-15). La pila dei medium e' cresciuta a 60+ vincoli, ciascuno ratificato con A/B ISOLATO, mai misurati congiuntamente. Con l'hard assicurato dalla catena, il budget della LS e' tutto loro: ogni medium compete per gli stessi move-eval e crea ottimi locali. Segnali: M37 sweep peso piatto (il peso non era la leva), M-TR peso inerte (la cura era a monte), M-BF tail peso-dipendente, medium totale di consegna che oscilla +-500k fra bacini buoni senza attribuzione. TEST: prune sweep congiunto - census contributo per-vincolo sulle consegne recenti, poi blocchi OFF a gruppi su banco replay con KPI di guardia. Rischio dichiarato: ogni medium aveva la sua evidenza; le INTERAZIONI non sono mai state misurate, ed e' esattamente cio' che si misura qui.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Census del contributo per-vincolo sul medium totale di consegna (top-N contributor ordinati, su N>=3 consegne recenti)
- [ ] #2 #2 Sweep a blocchi: i top contributor OFF a gruppi (es. M32+M20-family, S13, M15) su banco replay, consegna + KPI di guardia per braccio
- [ ] #3 #3 Verdetto per blocco: peso morto rimosso, peso ritarrato, o confermato; nessuna rimozione senza A/B e ratifica
- [ ] #4 #4 Interazioni dichiarate: ogni blocco OFF misurato sia da-solo che in combinazione col blocco piu' correlato
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
18/09 (Fedora, analisi zero-cost sugli analysis.json committati di integrato-1 [-4] e integrato-3 [0]): PASSO 1 COMPLETO e CORRETTIVO IMPORTANTE. La composizione medium e' quasi IDENTICA fra i due bacini (totali -585.851 vs -580.375: differenza 5k, NON le centinaia di k che avevo claimed io stamattina: quell'oscillazione veniva da ere/config diverse, ritiro la cifra). Ranking stabile cross-basino: M32 template-deviation DOMINA (~268k = 46% del medium totale in ENTRAMBI), poi M-LF (~86-101k), M20.2 empty-day (97k IDENTICO, stessi 10 match nei due bacini = firma strutturale della coda d'anno, coerente con pratica-2/blind: si cura con fill, non con pesi), M15 (~69k), poi la coda lunga M37/M34/M35/M33/M28 (~50k sommati). BLOCCHI per lo sweep (passo 2): {M32 solo: il 46% del bill, la domanda e' se la ricorrenza tiene senza}, {M-LF: weight 1000, 86-101 match, possibile ritarratura o forzato da scarsita' stanze}, {M20.2-family: NON sweepabile a pesi, e' il target strutturale di pratica-2}, {M15}, {coda lunga}. Questo ranking rende lo sweep molto piu' mirato: M32 e' l'esperimento singolo piu' alto di valore.

18/09 sera (laptop, A/B ciclo completo appaiato su profili freschi t626, branch wt/626-prune pushato): BRACCIO A (M32 ON baseline) consegna [-8/0]hard, M32 bill -140.410 (912 match); BRACCIO B (m32.prune-off=true, banner verificato) consegna peggiore di 6 punti hard e -2,3pp ricorrenza. VERDETTO: M32 NON SI RIMUOVE. Il guadagno medium (~160k) non giustifica il costo: M32 non e' solo una tassa, e' una GUIDA DI RICERCA che aiuta la LS a convergere verso bacini hard migliori. La predizione 'hard invariato' era SMENTITA: il census (che guarda solo il contributo medium) non poteva vedere l'effetto search-guide; la predizione ricorrenza 65-70% confermata al limite alto. AC#2/#3 risolti per il blocco top (M32): confermato, nessuna rimozione. I bracci M15/M20family/M-LF restano in coda dietro a 624 ma il segnale del blocco-dominante e' chiaro.

19/09 mattina (maintainer approva): bracci residui M15/M20family/M-LF CHIUSI come non-prioritari dopo il verdetto M32-guida-di-ricerca (il blocco dominante e' guida: potare i minori quasi certamente lo stesso, e il valore marginale non giustifica i cicli). AC#4 (interazioni) dichiarata non raggiunta per i rami minori: la lettura congiunta e' che la pila medium non ha peso morto rimovibile nel blocco top. TASK-626 CHIUSO.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
