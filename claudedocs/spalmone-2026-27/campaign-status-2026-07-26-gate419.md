# Gate TASK-419 (solve a stadi) + 403, 2026-07-26 - scorecard

**ESITO: ADOTTATO IN PIENO (ratifica maintainer, 2026-07-27 mattina).** Staged + treno-2
completo (419 + M20 progressivo + HoleRepair + A4 + 422 SurplusRebalance + 423 M33) e'
la modalita' di solve della campagna. Contestualmente: TASK-420 eseguita (pipeline a 5
fasi), TASK-424 avviata subito (golden come riferimento). Il resto del documento e' il
percorso di evidenza che ha portato qui.

Nota preparata NEL POMERIGGIO con i dati gia' verificati sui raw; le colonne
smoke2/run1/run2 si riempiono al VERDICT serale. Fix H16b (split timeslot/room,
efd79a21) pushato alle ~13:30; smoke-bis e A/B x2 in coda su zai dietro la FASE 2.

## Scorecard adozione staged (verificata sui raw dove compilata)

| Metrica | GOLDEN | val408 (baseline) | staged SMOKE (pre-fix H16b) | smoke2 | staged r1 | staged r2 |
|---|---|---|---|---|---|---|
| ricorrenza unpinned | (41,5 def. stretta) | 52,8 / 53,6% | **74,6%** | **74,1%** | **73,8%** | **73,8%** |
| cadence burst>2 | **41,6%** | 81,6 / 79,1% | **41,8%** | **43,0%** | **43,4%** | **44,3%** |
| gap-weeks/pair | 5,96 | 9,86 / 9,55 | **7,07** | 7,3 (1.772 tot) | 7,1 (1.721) | 7,1 (1.728) |
| day-start | ~99,3% | 93,7 / 93,7% | **97,4%** | 96,9% | 96,9% | 96,7% |
| nitidezza pooled | 93,4% | 79,7 / 77,5% | **86,4%** | 83,6% | 83,4% | 83,8% |
| full-day | 85,4% (83,7 scala uniforme) | 79,9 / 80,0% | 79,9% (tenuto) | 77,6% | 76,8% | 77,1% |
| giorni con buchi interni | **1 (pinned-esente)** | 1 / 1 | n/d (raw non catturato) | 9/14 slot (pre-HoleRepair) | 9/14 slot | 9/13 slot |
| M32 residuo | n/a | -280k / 2.100 match | **-106k / 696** | -125k / 798 | -124k / 793 | -124k / 792 |
| hard | ~0 | -1 / -1 | -897 (H16b -895: FIXATO con lo split) | **-15** (H17 -14/9m, H0 -1) | **-16** (H17 -14/9m, H0 -2) | **-22** (H17 -13/9m, H0 -9) |
| box | 0 | 0 | 0 | 0 | 0 | 0 |
| promossi / demozioni | n/a | n/a | 7.132 / 4 (1 iter) | 7.132 / 0 | 7.132 / 0 | 7.132 / 0 |
| autoritativi | n/a | intatti | 2.651 -> 2.651 intatti | intatti | intatti | intatti |

**Colonne smoke2/r1/r2 compilate 2026-07-26 sera, VERIFICATE sui raw** (score.txt,
analysis.json nonzero-hard, kpi-resemblance/cadence/fill.txt), non copiate dal VERDICT:
i numeri del VERDICT zai coincidono con i raw su tutte le righe controllate. H16b = 0 in
tutti e 3 i run (fix efd79a21 confermato). Lettura del gate: OGNI metrica di forma e'
al livello promesso dallo smoke (burst golden-zone, ricorrenza +21pp, erosione template
~0: costruzione 75,8% -> finale 75,7%); i due controprezzi sono full-day -3pp e hard
-15/-22 con composizione H17 9-match + H0 1-9. NOTA DECISIVA: questi run girano su HEAD
efd79a21 SENZA il treno di stasera (M20 progressivo + HoleRepair post-release): il
checkpoint locale Task419HoleRepairCheckpointTest chiude ESATTAMENTE questo residuo
(14 slot H17 -> 0 su smoke2, 5 free-import che assorbono anche H0 + 9 on-template,
0 min-slide). Il batch notturno (train completo x2) misura se il residuo si chiude
anche live e se M20 progressivo recupera i 3pp di full-day.

Lo smoke (pur fallito sull'hard per il difetto di confine H16b) ha gia' dimostrato che
lo staged CHIUDE O QUASI-CHIUDE ogni gap strutturale col golden: burst AL livello golden,
day-start quasi golden, nitidezza a meta' strada, ricorrenza oltre target (+22pt),
senza pagare full-day. Attesa per smoke2: STESSO profilo + H16b ~0 + hard in zona
baseline. Gate di adozione: i numeri sopra confermati su smoke2 + 2 run.

**Nuova riga "giorni con buchi interni" (2026-07-26, richiesta maintainer, `scripts/analyze/
hole-topology-kpi.py`):** distingue un BUCO INTERNO (slot vuoto con lezioni reali prima E
dopo, mai accettabile) da una semplice fine-anticipata. Golden = 1 (G10, 2026-12-17, la
stessa eccezione H17 pinned-esente gia' documentata), val408 = 1/1 (identico al golden, nessun
buco NUOVO), smoke2 = 9 giorni/14 slot (G04 x7 morning+afternoon con buco-pranzo esteso,
G13 x2). Il raw `/tmp/smoke2/lessons.txt` e' andato perso in un crash/riavvio dell'ambiente
oggi pomeriggio prima della cattura; ricostruito byte-per-byte dal payload gia' incorporato in
`confronto-staged-smoke2.html` (generato alle 15:54, prima del crash) - stesso 9/14 due volte.
Attesa per staged r1/r2 post-fix H16b: 0 (il fix di confine timeslot/room non tocca
direttamente questo meccanismo, ma va confermato sui raw, non assunto).

## Predizione statica pre-batch (2026-07-26 sera, predict-then-compare)

Re-scoring STATICO del grid finale di staged-run1 sotto l'albero del treno (M20/M20.2
progressivi + A4), via ScoreASnapshotTest sul dump committato - stesso identico grid,
solo pesi nuovi:
- medium totale: -1.869.970 (flat, live al gate) -> -2.664.060 (progressivo, statico):
  +794k di pressione nuova, di cui M20 +412k (stessi 390 giorni-deficit) e M20.2 +180k
  (stessi 31 giorni attesi vuoti: -164.000 flat -> -344.500 full-depth).
- Il batch notturno deve CONVERTIRE questa pressione: giorni profondi giu', giorni
  attesi vuoti giu', full-day su verso 79-80%. Se il full-day non si muove con +794k
  di pressione, la leva non e' il peso ma la raggiungibilita' (VRP/calendario).
- Riferimenti watch: M15 reale (esclusi lab) = 23 match/-52.800 al gate; M32 = 793
  match live (829 nella ricostruzione statica, delta da ricostruzione, atteso ~790-830);
  HoleRepair da checkpoint chiude 14->0 con +11k di collaterale M20 (5 free + 9
  on-template, 0 conflitti, 0 min-slide) e BoxRepair con A4 piazza 29/31 boxed come il
  profilo storico.
- Due caveat del tool scoperti e UNO fixato stasera: (a) i fact DailyActivityQuota
  seminati DOPO la costruzione dello snapshot non finivano nella solution -> M20.2/M30
  scoravano 0 (fix in SnapshotFixture.seedMediumTierFacts, verificato: 31/31 match);
  (b) i corsi ricostruiti dal dump pipe perdono il tag lab -> M15 conta anche i ~103
  split lab che il vincolo vero esenta (126 vs 23; verificato per composizione: i top
  sono Lavorazioni meccaniche/Servizio stilistico/Barberia/Cad). Il riferimento M15
  va SEMPRE letto sul non-lab.

## Batteria M15-scatter locale (2026-07-26 sera, COMPLETA) - REVISIONE della predizione

3x600s progressivo (HEAD bffcf412) vs 3x600s flat (worktree 41910bfa), Phase2Smoke
NON_REPRODUCIBLE, stessa partenza fine-costruzione (fixture senza stage/pin: i ~216
giorni a deficit-8 sono strutturali del fixture, il DIFFERENZIALE resta valido).

1. **SCATTER M15 REFUTATO su entrambi i bracci**: split non-lab P={1,3,1} F={4,0,1}.
   Il timore task-349 non si manifesta nemmeno sotto la pressione progressiva piena.
   hard -155 -> 0 in tutti e 6 i run.
2. **La curva progressiva APPIATTISCE la profondita', non alza il full-day**: a monte
   ore totali identiche, deficit totale identico (3.418 vs 3.445h) ma ridistribuito -
   giorni deficit 3..7: P=221 vs F=277 (56 in meno, -20%); giorni -1: P=298 vs F=112;
   full-day: P~62,6% vs F~67,7% (-5pp); ricorrenza P 65,1-65,4 vs F 67,1-67,4 (-2pp).
   E' il comportamento DI PROGETTO del costo triangolare (quattro giorni -1 costano
   4.000, un giorno -4 ne costa 7.000) ed e' golden-coerente: il residuo golden e'
   98x-1h + 1x-2h, cioe' SOLO giorni -1 - il giorno profondo e' l'anomalia, il -1 il
   residuo fisiologico.
3. **REVISIONE ATTESA GATE (predict-then-compare aggiornato PRIMA del VERDICT):** la
   riga "full-day in recupero verso 79-80%" della predizione statica va letta cosi':
   il full-day potrebbe NON recuperare o persino scendere di qualche punto, MENTRE
   migliorano l'istogramma di profondita' (giorni -3h e oltre in calo) e i buchi
   interni (HoleRepair). Il verdetto del mattino NON deve leggere un full-day piatto
   come fallimento del treno: le lenti giuste sono (a) buchi interni = 0, (b) giorni
   profondi (deficit >=3) in calo netto, (c) deficit totale invariato o giu', (d)
   M15/ricorrenza tenuti. Conferma la domanda di maintainer del 26/07 ("forse non stiamo
   usando la metrica corretta"): il full-day rate da solo nasconde DOVE sta il deficit.
   Candidata follow-up: riga "giorni deficit>=3" stabile nella scorecard KPI.

## Watch-items del gate (da leggere sui raw, nessuna ritaratura preventiva)

- **RAPPORTO CON M20 CAMBIATO dalla progressione (2026-07-26):** marginale M20 fino a
  4.500/ora sui giorni profondi (`m20DeficitCost`) contro M15 ~1.200 per split da 1h e
  M32 max 500. La gerarchia su cui furono calibrati i pesi di M15 (x20) e M32 (clamp
  20..500) vale ora solo sulla PRIMA ora di deficit. Watch: **match di M15** e
  **ricorrenza**; ritarare SOLO su evidenza A/B, mai su questa osservazione.
- **H16b e H-D1 non sono gated in `HoleRepair`** (import cross-data): mitigazione per
  H16b = lo stage B-ROOM ri-risolve le stanze subito dopo, salvo mover accanto a un pin
  autoritativo con stanza non replicabile; H-D1 non mitigato (le coppie doppio sono
  sessioni lab pinnate nel seed 2026/27). Watch: **H16b = 0** al gate.
- **Min-slide di `HoleRepair` = ultima risorsa e puo' de-ancorare il giorno** (costo M18,
  hard H17 batte medium M18): ogni de-ancoraggio e' loggato a INFO con gruppo e data.
  Watch: **day-start** e i log `min-slide DE-ANCHORS`.

## TASK-403: chiusa Done con risposta

Repair anticipato ph2/ph3: funziona, riproducibile, rende la fase 4 no-op - ma la fase 3
resta inerte ANCHE hard-pulita (+72/+1.096 medium in 160/133s). Conclusione sposata:
niente adozione dell'arm; la vera azione e' la RIMOZIONE della fase 3 (TASK-420, sesta
misura consecutiva di inerzia, ~2,5 min risparmiati a run), da coordinare con l'eventuale
adozione staged in un'unica revisione di pipeline.

## Verifica critica sui raw (2026-07-26 sera, checklist completa)

Ogni cifra della tabella e' stata ricontrollata sui raw, non presa dal VERDICT:
score.txt e analysis.json (composizione hard: H17 -14/-14/-13 con 9 match + H0 -1/-2/-9,
H16b ASSENTE dai nonzero in tutti e 3 i run); KPI RILANCIATI localmente su lessons.txt
di run1 (ricorrenza/full-day/burst: identici al byte ai kpi-*.txt catturati dal laptop);
buchi interni calcolati qui con hole-topology-kpi.py (9gg/14-14-13 slot: coincidono
1:1 con l'hard H17, quindi TUTTO il residuo H17 e' fatto di buchi interni); conservazione
autoritativi verificata in modo indipendente contando le righe pinned nei dump finali
(2.651 in tutti e 3 i run = promossi rilasciati, autoritativi intatti); erosione:
phase-2-entry 75,8% TOTALE -> finale 75,4% (~0,4pp vs 17pp di val408). Lato 403:
phases.json confermano fase 3 hard-pulita e comunque inerte (-1 -> -1; medium +72 e
+1.096), fase 4 quasi no-op, finali 0hard/-1hard.

## BATCH NOTTURNO TRENO x2 (VERDICT-train 928e889b) - VERIFICATO SUI RAW, 2026-07-27 ~00:30

Verifica critica: run1 letto via ssh sui raw del laptop PRIMA del push (hard/holes/KPI/pinned
2.651 contati nel dump), run2 e VERDICT ricontrollati sui raw pullati (analysis.json,
kpi-*, istogrammi ricalcolati localmente). Ogni cifra del VERDICT combacia.

| Lente (rivista post-batteria) | train-run1 | train-run2 | sera (flat, r1/r2) | golden |
|---|---|---|---|---|
| hard | -3 (solo H0) | **0** | -16 / -22 | 0 |
| H17 / H16b | 0 / 0 | 0 / 0 | -14/0, -13/0 | 0/0 |
| buchi interni | **0/0** | **0/0** | 9gg/14, 9gg/13 | 1/1 |
| HoleRepair | 2->0 (0 free+2 tmpl) | 4->0 (2+2) | n/a | n/a |
| giorni profondi (deficit 3-7h) | 235 | 235 | 251 / 254 | (sheet-vs-golden ~15%) |
| giorni -1h | 162 | 157 | 86 / 82 | n/a |
| giorni sopra-atteso | 126 | 136 | n/a | **70** |
| full-day | 75,5% | 75,0% | 76,8 / 77,1 | 85,4 |
| day-start | 95,9% | 95,7% | 96,9 / 96,7 | ~99,3 |
| ricorrenza unpinned | 73,9% | 73,4% | 73,8 / 73,8 | n/a |
| burst>2 | 45,9% | 45,9% | 43,4 / 44,3 | 41,6 |
| M15 match | 11 | 32 | 23 (statico) | ~98 |
| M32 | 787/-123,5k | 803/-126,1k | 793/-124k | n/a |
| demozioni | 2 giri/4 pin | 0/0 | 0/0 | n/a |
| autoritativi | 2.651 | 2.651 | 2.651 | n/a |

**Verdetto della predizione (predict-then-compare):** la predizione statica originale
("full-day verso 79-80") e' SBAGLIATA e la REVISIONE della batteria (scritta PRIMA del
VERDICT) e' CONFERMATA run-per-run: la pressione M20 progressiva (+420/490k medium) si
converte in profondita' appiattita (giorni 3-7h: 251->235, -6%; -1h: 86->162) e buchi
interni azzerati, NON in full-day (75,0-75,5, -1,5pp). I giorni profondi residui sono in
larga parte strutturali (G14/FC/fogli), come da decomposizione differenziale in
TASK-408. Costi minori confermati: day-start -1pp, sharpness -3pp, burst +2pp (45,9
stabile sui 2 run, vs golden 41,6 e baseline ~80).

## TRENO-2 x2 (422+M33, VERDICT-train2 9217cc61) - VERIFICATO SUI RAW, 2026-07-27 ~05:10

KPI rilanciati localmente su entrambi i lessons.txt, hard da analysis.json, pin contati:
il VERDICT combacia su ogni riga. Confronto completo (treno-2 vs treno vs sera flat):

| Lente | train2-r1 | train2-r2 | train r1/r2 | sera r1/r2 | golden |
|---|---|---|---|---|---|
| hard | **0** | **0** | -3 / 0 | -16 / -22 | 0 |
| buchi interni | 0/0 | 0/0 | 0/0 | 9gg/14, 9gg/13 | 1/1 |
| full-day | **77,0%** | **76,6%** | 75,5 / 75,0 | 76,8 / 77,1 | 85,4 |
| giorni sopra-atteso | **111** | **118** | 126 / 136 | n/a | 70 |
| ricorrenza unpinned | 73,5% | 73,7% | 73,9 / 73,4 | 73,8 / 73,8 | n/a |
| burst>2 | 45,1% | 45,5% | 45,9 / 45,9 | 43,4 / 44,3 | 41,6 |
| M15 match | 22 | 26 | 11 / 32 | 23 | ~98 |
| M32 | 786 | 793 | 787 / 803 | 793 | n/a |
| M33 (nuovo) | 69/-11,2k | 77/-12k | n/a | n/a | (70 sopra) |
| SurplusRebalance | 16h, 79->69 | 33h, 99->77 | n/a | n/a | n/a |
| autoritativi | 2.651 | 2.651 | 2.651 | 2.651 | n/a |

Fail-closed MAI scattato, DE-ANCHORS 0, HoleRepair 2->0 pulito in entrambi. Il treno-2
recupera il full-day che il treno-1 pagava (+1,5-2pp, parita' con la sera flat) TENENDO
tutti i guadagni (hard 0 doppio, buchi 0, ricorrenza, burst perfino -0,4pp). Giorni-sopra
in calo 126-136 -> 111-118; il residuo verso il golden 70 e' il surplus strutturale che
il rebalance non puo' spostare (nessun giorno-deficit legale ricevente) - la leva
successiva e' TASK-424 (M20 pin-blind, causa-radice: 267 giorni misti dove M20 PAGA la
LS per sovra-riempire).

## PACCHETTO DI RATIFICA FINALE (mattina 27/07, decide maintainer)

1. **Adozione staged + treno-2 COMPLETO (419+M20prog+HoleRepair+A4+422+M33) come
   modalita' di solve della campagna.** Evidenza: 2 run a hard 0 assoluto, buchi interni
   0 (il tuo criterio), erosione template azzerata, ricorrenza/burst in banda, full-day
   al livello flat con profondita' migliore, tutte le meccaniche pulite su 4 run
   consecutivi. Nessuna riserva tecnica.
2. **TASK-420** (rimozione fase 3, inerte 6x): evidenza completa, ripple sync da eseguire.
3. **TASK-424 come prossima priorita'** (M20 pin-blind sui giorni misti): e' la
   causa-radice candidata sia del surplus residuo sia di parte del gap full-day.
4. Coda dopo: progressione-surplus SE la coda +4h sopravvive (watch 423), TASK-417
   (fase 5, riformulata), 280-cluster benchmark, domande scuola (G14/FC).

## Raccomandazione (argomentata, ratifica a maintainer)

1bis. **AGGIORNAMENTO POST-BATCH (00:30): la condizione del punto 1 si e' AVVERATA -
   adozione staged+treno RACCOMANDATA senza riserve tecniche.** Il treno consegna hard
   0/-3 (era -15/-22), H17=0, H16b=0, buchi interni 0/0 (il difetto che maintainer ha
   indicato come inaccettabile), ricorrenza e burst tenuti, meccanica pulita su
   entrambi i run. I controprezzi (full-day -1,5pp, day-start -1pp, sharpness -3pp)
   sono quantificati, spiegati dalla batteria (trade profondita'-vs-pieni, DI PROGETTO
   e golden-coerente: il residuo golden e' fatto di giorni -1) e hanno gia' i loro
   lever di recupero in coda: TASK-422 (rebalance surplus->deficit, ~164h; v2 con
   ricompattazione donatore; implementata stanotte, DoD in corso) e il candidato
   vincolo anti-surplus (golden 70 giorni sopra vs nostri 126-136). Restano di
   PORTATA DI maintainer: (a) ratifica adozione staged+treno come modalita' campagna;
   (b) se includere 422 nel perimetro adottato una volta chiusa la sua DoD.

1. **Staged: adozione RACCOMANDATA, condizionata al batch notturno col treno completo.**
   Il gate ha dimostrato cio' che doveva: erosione del template azzerata, ricorrenza
   unpinned +21pp (73,8-74,1 vs 52,8/53,6), burst in zona golden (43-44 vs 41,6 golden e
   ~80 baseline), day-start +3pp, nitidezza +5pp, meccanica pulita (7.132 promossi, 0
   demozioni, autoritativi conservati). I due controprezzi misurati - hard -15/-22 e
   full-day -3pp - sono ESATTAMENTE i bersagli del treno gia' validato in checkpoint
   locale (HoleRepair chiude 14/14 slot H17 su smoke2 assorbendo anche unassigned;
   M20 progressivo rincara i giorni profondi). Se il batch notturno conferma
   (H17=0, buchi interni=0, H0~0, full-day in recupero, ricorrenza/burst tenuti),
   l'adozione e' matematicamente dominante su val408. Se il full-day NON recupera
   del tutto, il residuo quantificato diventa l'evidenza per la domanda
   compattare-vs-spalmare alla scuola (gia' in coda).
2. **403 arm: NO adozione** (confermato sui raw: il repair anticipato non cambia il
   finale, la fase 3 resta inerte anche hard-pulita). L'azione giusta resta TASK-420
   (rimozione fase 3, ~2,5-10 min risparmiati a run), da eseguire nella stessa
   revisione di pipeline dell'eventuale adozione staged.

## Decisioni in attesa di ratifica (maintainer)

1. Adozione dello staged come modalita' di solve della campagna (a gate verde serale).
2. TASK-420: rimozione fase 3 (evidenza completa, ripple sync da eseguire).
3. Coda post-gate: se staged adottato, i prossimi lever di cadenza diventano i residui
   (gap-weeks 7,07 -> 5,96 e nitidezza 86 -> 93): candidati = leve di contorno 413
   (cap settimanale / S12) su base staged.
