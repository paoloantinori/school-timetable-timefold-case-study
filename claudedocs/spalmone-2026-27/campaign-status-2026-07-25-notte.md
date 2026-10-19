# Verifica Fedora A/B val413 (M32 quasi-pinning graduato) — 2026-07-25 ~01:30

Verifica critica ESEGUITA SUI RAW (resemblance/cadence ricalcolati qui da lessons.txt,
score/analysis letti dai file, non dal solo VERDICT): tutte le cifre del VERDICT di zai
coincidono con la riverifica indipendente. Baseline: val414-run1/2 (stesso stack senza M32).

## Cifre verificate (val414 → val413)

| Metrica | val414 r1/r2 | val413 r1/r2 | Gate |
|---|---|---|---|
| hard | −1 / −2 | **0 / −1** | ✅ migliorato (primo 0-hard della campagna) |
| box (H0) | 0 / 0 | **0 / 0** (12.301/12.301) | ✅ invariato |
| moda-knot / celle impilate | 0 | **0** | ✅ |
| ricorrenza UNPINNED | 37,9 / 36,6% | **53,5 / 53,1%** | ✅ +16pt; erosione dal template dimezzata (76→53 vs 76→37) |
| burst>2 (cadenza) | 86,9 / 89,3% | **79,1 / 79,1%** | ✅ verso golden (41,6%), strada ancora lunga |
| gap-weeks/pair | 11,59 / 11,36 | **9,84 / 9,49** | ✅ verso golden (5,96) |
| full-day | 77,2 / 77,5% | **79,0 / 78,1%** | ✅ non peggiorato (su) |
| day-start | 95,2 / 94,4% | 93,3 / 93,6% | ⚠️ −1,3/−0,8pt |
| M20+M20.2 | −1,140M / −1,144M | −1,148M / −1,176M | ⚠️ +0,7% / +2,8% |
| M22 | −577k / −585k | −584k / −591k | ⚠️ ~+1% |
| M15 | −64,8k / −87,6k | −55,2k / −55,2k | ✅ giù |
| medium non-M32 (analysis) | −1,806M / −1,843M | −1,817M / −1,851M | ~+0,5% |
| M32 (residuo score−analysis) | n/a | ~−271,0k / −271,6k | knob (K=100k, 20..500) |

## RACCOMANDAZIONE (ratifica a maintainer): ADOTTARE M32 così com'è

Il meccanismo fa esattamente ciò per cui è stato progettato: la LS smette di erodere metà
del template (ricorrenza +16pt), la cadenza si muove nettamente verso il golden (burst −8/−10pt,
gap-weeks −15/−16%), full-day sale, e per la prima volta un run chiude a hard 0 con box 0.
Il prezzo è piccolo e localizzato: day-start −1pt e M20/M22 ~+1% (la quasi-immobilità delle
celle scarse toglie alla LS qualche grado di libertà sui riempimenti marginali) — dentro la
tolleranza "non materialmente peggiorati" del gate. Il peso resta il knob dichiarato
(K=100.000, clamp 20..500): un eventuale ritocco è materia di benchmark, non blocca l'adozione.

## Swing soft (−110k) verificato a posteriori: NON è un regresso, è convergenza al golden

Il VERDICT segnala soft da +63k a −47/−42k, dominato da S1 (gap docenti): 422→1129 istanze.
Verifica sul golden (stesso calcolo, slot-index, esclusi Stage/Fasullo): il golden ha
**1.280 istanze / 103.200 minuti** di gap docenti — PIÙ di val413 (1.129/87.720 run1,
1.118/84.720 run2). Era val414 (422/30.720) a essere più compatto della pratica reale
della scuola. Sotto il principio golden-practice-legitimizes, il livello S1 di val413 è
legittimo: il quasi-pinning ancora le lezioni alle celle template come fa il planner umano,
e produce la stessa frammentazione docente che la scuola stessa accetta. Nessun impatto
sulla raccomandazione. (S13 sale coerentemente con la ricorrenza: 7.300→10.312; S8 −80→−400,
20 lezioni isolate, marginale.)

## Anomalia trovata e da sistemare (TASK-416, non blocca l'adozione)

M32 NON compare nel breakdown di analysis.json e il medium dell'analysis differisce dallo
score del solver esattamente del contributo M32 (~271k). Ipotesi principale (mia, da
verificare): `analyzeScore` post-solve riesegue `refreshSolverStatics` → il planner
seleziona la popolazione con `timeslot == null` → a solve finito la popolazione è vuota →
mappa template VUOTA → M32=0 nella ri-analisi. Ipotesi di zai: gap di ripple nel drill-down
di ScheduleService (spiegherebbe la riga mancante ma NON il totale più basso). Stessa radice
toccherebbe anche un RE-SOLVE su profilo già risolto (M32 inerte al secondo giro). Il
match-count M32 oggi non è osservabile da nessuna capture.

## Stato repo (notte)

- Origin: `c7b161af` (artifact val413 + VERDICT, pushato da zai ~01:05).
- Locale: `d2124508` (TASK-415 infra, rebased sopra) NON pushato — resta il gate: la 415 va
  verificata contro le sue AC prima del push (non fatto stanotte: riservata all'altro agente
  e alla revisione diurna).
- Nessuna adozione committata stanotte, nessun peso/config toccato: decisione a maintainer.
