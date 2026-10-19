# DESIGN TASK-537: campagna di chiusura finale con gerarchia vero/quasi-hard

Worktree prototipo (Fedora, 20/08 pomeriggio). Semantica valida per TASK-538 (BendableScore).

## Insight cardine (corregge l'AC#5 del task)
I revert di QFR del 19/08 (chiudeva H32 pagando H17) sotto la gerarchia sono MOSSE ILLEGALI
per DIREZIONE: mai pagare un VERO (H17) per chiudere un QUASI (H32). Quei revert erano giusti.
Cio' che la gerarchia sblocca e' il trade OPPOSTO: chiudere un VERO pagando un QUASI entro
budget golden. La direzione del trade e' la semantica, non il segno del delta complessivo.

## Struttura
- FinalClosingCampaign (solver/): pass deterministico sul vincitore (post pick-best), replay
  su export congelato (pattern QuotaFillRepairCheckpointTest).
- Accettazione lessicografica: accetta mossa sse (veriDelta > 0) oppure (veriDelta == 0 AND
  quasiDelta <= 0), e in OGNI caso ogni famiglia quasi resta entro il budget golden.
- VERI: H1/H2/H3, H5, H6, H17, H28, H30, H25, H26 (audit golden 20/08: 0 violazioni, H17 1/1900).
- QUASI col budget golden: H11 cap-praticato 8 (43 giorni oltre 6); H31 ~1 giorno/gruppo;
  H-COPRESENCE 44 celle; H32 1h; H33 4 giorni (G09 x2, G08 x2; G14 fuori).
- Fonte budget: audit golden 20/08 (sessione Fedora), da codificare in tabella DERIVATA
  (non hardcoded per nome-gruppo: conteggio golden, forma input-first).

## Verifica API BendableScore (TASK-538 AC#1, docs Timefold)
- PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2): veri + quasi hard,
  medium + soft. Constraint: penalize(BendableScore).asConstraint(name). Termination:
  [0/0]hard/[-5000/-5000]soft. Feasible = TUTTI gli hard level >= 0 (attenzione: col livello
  quasi NON a zero il run resta "infeasible" per Timefold: il gate lo gestiamo noi col budget,
  non col feasibility nativo). Docs ammoniscono esplicitamente contro lo score folding (il
  nostro hack attuale) e benedicono 3+ livelli.
