---
id: TASK-624
title: >-
  TASK-624 - Ricalibratura soglia profile-predictor (150 vs mediana ~296
  H17-costruzione)
status: Done
assignee: []
created_date: '2026-09-18 10:14'
updated_date: '2026-09-19 05:01'
labels:
  - claude-planned
  - m-15
milestone: m-15
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla discussione maintainer 18/09 (m-15). Il census CH del 17/09 ha mostrato che TUTTI i bracci stanno 10/10 semi sopra la soglia 150 del profile predictor (mediana ~296 buchi H17 a costruzione, baseline 240-445): la soglia scarta quasi ogni seed GRASP (24-32 discards per profilo osservati in serie) e la serie sopravvive sull'attempt deterministico. O la soglia e' miscalibrata rispetto alla CH corrente (era calibrata su un CH diverso), o lo scarto e' su una firma che non predice piu' la consegna. Criterio del Task: verificare se la soglia 150 separa ancora i bacini buoni dai condannati sulle consegne RECENTI (serie integrato: -4,-1,0,0 con discards 0-32), e ricalibrare o dichiarare. Banco, N>=10 semi, zero cicli.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 Censimento della distribuzione H17-costruzione corrente su N>=10 seed (census CH 17/09 come base: mediana ~296, 10/10 sopra 150)
- [ ] #2 #2 Curva di ritaglio: per ogni soglia candidata (150/200/250/300), quanti scarti risparmia il wrapper e quante consegne peggiorano su N>=10 semi replay
- [ ] #3 #3 Soglia emendata adottata (o la 150 confermata) con A/B banco appaiato e PIN aggiornati
- [ ] #4 #4 Se la soglia resta: documento del perche' (es. la firma del predictor misura altro dall'H17 grossolano)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
18/09 (Fedora, analisi zero-cost sui CSV del census 17/09): PASSO 1 COMPLETO. Distribuzione BASELINE (10 seed): min 240, mediana 296, max 445; tutti e 4 i bracci (baseline + OFF-577/564/599) sovrapponibili (median 276-296). CURVA DI RITAGLIO su BASELINE: soglia 150 -> 10/10 scartati (il predictor oggi non lascia passare NESSUN seed GRASP: il multi-start e' degenerato a N=1 sull'attempt deterministico, coerente con i 24-32 discards osservati); 250 -> 7/10; 296 -> 5/10; 325 -> 3/10; 380 -> 1/10. COSTO dello scarto ~10s (cheap): il danno vero e' la DIVERSITA' PERSA (zero retry utilizzati quando la coda brucia tutta). SOGLIE CANDIDATE per il replay-test (passo 2, laptop): 250 / 300 / 325, criterio = qualita' di consegna dei seed che passano, non solo il numero di scarti risparmiati.

19/09 mattina (maintainer approva): proposte soglie 250/300/325 RITIRATE (il verdetto notturno 137525a0 ha mostrato il metro non-monotono a livello di singolo seed: soglia calibrabile non esiste); la proposta 'salta tutti i GRASP' resta SOSPESA per decisione futura (NON adottata: il predictor resta a 150, accidentalmente corretto). TASK-624 CHIUSO: AC#1/#2 soddisfatti (census + curva di ritaglio + replay discriminato con controllo deterministico), AC#3 risolto come 'nessuna soglia emendata: il metro non predice', AC#4 soddisfatto dal verdetto stesso.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
