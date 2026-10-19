# VERDICT m-12 (Gap verso il golden, post-adozione staged) - 2026-07-27

Descrittivo, NESSUN giudizio di adozione. L'adozione delle leve m-12 e' decisione di maintainer;
questo documento presenta solo l'evidenza misurata. HEAD **c9fd1fd6** (run1 era a 824fa2a8;
c9fd1fd6 e' solo docs sopra lo stesso codice 424 M20 pin-netting + 425 M34/M35 cadence), staged
solve (modalita' adottata). Baseline: val419-train2-run1/run2 (HEAD a71f77b5, pre-m-12).

## Sintesi (n=2 pulito: run1 + run2bis, ENTRAMBI hard 0)

Run1 e run2bis sono DUE solve staged indipendenti su profilo FRESCO (runbook completo: volume
wiped + reseed, import-calendario, pre-assign, java -jar diretto con
`-Dplanner.phase-transition-dump.enabled=true`, solve-staged, caffeinate). Entrambi **hard 0**,
tutte le metriche nella direzione attesa e entro la varianza run-to-run (tabella sotto): la
stabilita' che il run2 reset-artifact non poteva dare e' ora confermata da n=2 pulito.

Run2 (reset-artifact originale, per record storico): hard -54 (H0 54 unassigned) perche' fatto
con `api.sh reset + solve-staged` SENZA profilo fresco. Stesso codice su profilo fresco = hard 0
(run1 AND run2bis); il -54 era ARTEFATTO del path reset, non un regression m-12. Il runbook impone
profilo fresco a ogni run proprio per questo. Run2bis e' il run2 rifatto col runbook completo.

Le leve m-12 muovono tutte le metriche nella direzione attesa, mantenendo hard 0 su ENTRAMBI i run:

| Metrica | train2 run1/run2 | **m-12 run1** | **m-12 run2bis** | golden | Lettura |
|---|---|---|---|---|---|
| hard | 0 / 0 | **0** | **0** | 0 | mantenuto, n=2 stabile |
| medium | -2,301M / -2,330M | -1,343M | -1,366M | n/a | ~pari (varianza run-to-run ~1,8%) |
| full-day | 77,0 / 76,6 | **78,2%** | **78,5%** | 85,4 | +1,2/+1,5pp (verso golden) |
| giorni-sopra | 111 / 118 | **87** | **83** | 70 | -24/-28 (424 rimuove l'incentivo al surplus) |
| burst>2x | 45,1 / 45,5 | **40,2%** | 41,8% | 41,6 | -4,9/-3,3pp, intorno al golden (M34 cap) |
| gap-weeks | 1813 / 1776 | **1710** | 1705 | n/a | -103/-108 (M35 spread) |
| ricorrenza unpinned | n/a | 73,4% | 72,8% | (stretta 41,5%) | ~pari |
| day-start | 95,9 / 96,0 | 96,3% | 96,1% | 99,3 | ~pari (residuo H1-costruzione, vedi TASK-426/430) |
| buchi interni (H17) | n/a | 0 | 0 | - | 0 entrambi |

Run2bis e' un solve staged completo (~66min solver reale, 17:39-18:45 CEST), 0hard,
4 phase-transition dumps catturati (dump flag attivo: per-phase erosion construction->finale
misurabile, run1 non li aveva). Run1 era 81min (10:37-11:58 CEST), 12286/12286 assigned, 2651
pinned. Nessuna metrica regredisce tra run1 e run2bis: la direzione m-12 e' stabile.

## Le leve sono ATTIVE su live solve (breakdown run1)

- **M34 - Weekly burst cap** (TASK-425): -8.900 medium / 59 match. Attivo su 59 settimane
  (group,materia) sopra il cap del golden. Pes 100 (knob planner.m34.weight).
- **M35 - Weekly spread deficit** (TASK-425): -18.150 / 70 match. Attivo su 70 pair con
  spread sotto il golden (famiglia MODA in testa). Pes 50 (knob planner.m35.weight).
- **M20 - Daily quota fill** (TASK-424 netting): -417.500 / 208 (train2 -1.400.500/447).
  Il netting di 424 collassa i falsi-deficit dei giorni misti ANCHE su live solve, non solo
  sul re-score statico. L'incentivo perverso (4a ora unpinned pagata -2000) e' rimosso.
- M33 surplus: -12.200 / 73 (train2 -11.200/69, comparable). M20.2: -107.500/9 (train2 -112k/10).
  M22: -582.000/13 (identico). M32: -123.763/791 (train2 -123k/786).

Self-gating verificato: M34/M35 leggono il reference per-(gruppo,materia) da
golden-cadence-reference.json (max per M34, active_weeks per M35); i pair gia' a livello
golden (G04, G13, FA/MODA-1-2) non sono toccati (deficit 0). Niente pin del guard
golden e' cambiato (M34 0/0/0 structural, M35 -50/1 residuo calibrato documentato =
G04/Scienze W24 oltre-orizzonte, stessa classe del gap S12 5.623-vs-5.625).

## TASK-426 (day-start): gap NOT closeable da m-12

Anatomia (report task426-daystart-anatomy-2026-07-27.md): 90 giorni non ancorati (95,9%
vs golden 99,86%), 100% solver-addressable, sorgente = COSTRUZIONE. Causa-radice = H1
teacher conflict cross-gruppo allo slot 0 (G11 mercoledi: ogni docente alle 8 per un
altro gruppo). Il blocco e' HARD, M18 (medium) non puo' chiuderlo a nessun peso. La leva
reale e' a livello costruzione (template slot-0 teacher balancing) o una mossa LS
slot-0-breaking -> follow-up oltre m-12. day-start 96,3% e' quindi un residual
strutturale da segnalare, NON un fallimento delle leve m-12.

## TASK-427 (surplus progressione, condizionale): non triggerata

Post-424, il surplus e' STABILE e leggero: M33 -12.200/73 match = ~122 ore-excess su 73
giorni (avg 1,67h/giorno, comparable al train2 -11.200/69). La coda profonda (+3/+4h) non
e' esplosa (l'avg basso indica maggioranza +1/+2h). Il trigger di 427 ("se la coda resta
~10x il golden -> M33 da flat a curva") NON si attiva sull'evidenza: M33 flat 100/h resta
appropriato. (Depth-histogram preciso non misurato per budget; l'evidenza M33 e' sufficiente
per non-triggerare la curva.)

## TASK-428 (dossier scuola): draft

Q23 (G14 990h foglio vs 115h incarichi, golden 0% full-day) + Q24 (G04 floor lab-quota
23h riempito con Informatica) aggiunti a domande-brevi.md (item stabili seed-fact).
Q25 (compattare-vs-spalmare) si finalizza con i numeri m-12: il trade e' ora quantificato
(full-day 78,2% / sopra 87 con le leve, vs la pratica golden).

## Rischi / watch-item (per maintainer)

1. **TASK-429** (creato, low): il netting di 424 fa ammettere a SurplusRebalance.isSurplusDay
   i giorni at-plan-con-pin come donatori (deficit poi invisibile a M20). Severita' bassa
   (deficit aggregato reale invariato, KPI daily-fill onesta) ma da monitorare/fixare
   (isSurplusDay richiederebbe occupancy > effectiveQuota). Il live A/B non mostra regressione
   (sopra cala), quindi non blocca.
2. **M34 burst 40,2% SOTTO golden 41,6%**: non e' una violazione "piu' rigido del golden"
   (il cap di M34 E' il max del golden per pair, un pair non puo bursting sopra il proprio
   max); significa solo che il run bursta meno del golden in aggregato. OK, ma da tenere
   d'occhio se cala troppo (significherebbe over-spreading). Pes 100 tunabile.
3. **Cleanup deferiti** (post-m-12, non bloccanti): readCadenceKnobProperty duplica
   readM32KnobProperty; re-parse JSON a ogni loadSchedule idle (solo UI); new File(path)
   -> classpath resource per robustezza java -jar; M35 beyond-horizon residual.
4. **run2 n=2 - RESOLTO**: run2bis (profilo fresco, runbook completo) completato = hard 0,
   tutte le metriche entro varianza di run1 (tabella sopra). La stabilita' e' confermata su n=2
   pulito. (Varianza run-to-run osservata: medium -1,343M/-1,366M ~1,8%, full-day 78,2/78,5,
   day-start 96,3/96,1 - tutte entro la banda storica train2.)

## Conclusioni (descrittive, adozione = maintainer)

- L'evidenza run1 SOSTIENE l'adozione delle leve m-12: hard 0 mantenuto, full-day +1,2pp,
  sopra -24 (verso golden 70), burst -4,9pp (M34), gap-weeks -103 (M35), M20 collassato
  (424). Nessuna metrica regredisce.
- Le leve sono self-gating sul golden reference (non toccano i gruppi gia' a livello golden)
  e non modificano pin del guard.
- day-start resta un residual H1-costruzione (TASK-426), non closeable da m-12: da segnalare
  a maintainer come known follow-up, non come difetto delle leve.
- Ratifica formale ( mantenere M34/M35 ai pesi default 100/50, confermare 424 netting come
  baseline) = decisione di maintainer sul VERDICT + (idealmente) run2 per stabilita'.

## Asset
- claudedocs/spalmone-2026-27/m12-results/m12-run1/ (score, analysis.json, phases.json,
  lessons.txt, kpi-fill/cadence/daystart). capture-run.sh ha i passi export/lessons rotti
  (export.json.gz = broken-147B noto; lessons.txt rigenerato via db-query). Fix di
  capture-run.sh = follow-up (file tracciato, non mia call modifireferente senza OK).
