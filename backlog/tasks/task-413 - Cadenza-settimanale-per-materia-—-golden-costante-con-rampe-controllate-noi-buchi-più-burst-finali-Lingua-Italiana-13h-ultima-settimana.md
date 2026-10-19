---
id: TASK-413
title: >-
  Cadenza settimanale per materia — golden costante con rampe controllate; noi
  buchi + burst finali (Lingua Italiana 13h/ultima settimana)
status: Done
assignee: []
created_date: '2026-07-24 12:10'
updated_date: '2026-07-25 04:35'
labels:
  - claude-planned
  - research
  - constraints
  - spalmone-2027
dependencies: []
priority: medium
ordinal: 237000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Rivelata dalla vista "Materie × settimana" di golden-vs-run-viz (2026-07-24): il golden
tiene ogni materia a cadenza settimanale QUASI COSTANTE (es. G01 Lingua Italiana 1-3h/
settimana tutto l'anno, con rampa controllata 4-6h nel finale; "Recuperi" parte SOLO a
gennaio e cresce), mentre noi mostriamo BUCHI di settimane intere seguiti da BURST finali
(G01 Lingua Italiana: settimane vuote poi 8-9-13h nelle ultime settimane; Matematica
quasi esaurita a metà anno poi trickle).

Vincoli esistenti troppo deboli/parziali: S12 (presenza settimanale) è UN SOFT da 1 a
settimana presente — non penalizza le settimane ASSENTI né i burst; M21 cappa il giorno
(2h/materia) ma 13h/settimana su 5 giorni lo aggirano; M27 (finestre di partenza) esiste
solo per le materie di T20, mentre il golden ha disciplina partenze/arresti più ricca
(Recuperi da gennaio, Educazione Civica da gennaio in G01...).

## RIQUADRO DAL PLANNER UMANO (maintainer, 2026-07-24) — la cadenza è un'euristica di COSTRUZIONE "quasi-pinned"

Il planner umano: (1) piazza la ricorrenza dei LAB; (2) SUBITO DOPO fissa la cadenza
settimanale delle altre materie; (3) da quel momento la tratta come QUASI-PINNED — evita di
modificarla, e la tocca SOLO se non riesce a risolvere con i dati rimanenti. Quindi la
cadenza non è (principalmente) un vincolo di score da aggiungere: È GIÀ il nostro template
(il builder spalma hours/activeWeeks per corso, subito attorno ai lab pinnati) — quello che
MANCA è la protezione quasi-pinned. La prova nei numeri: ricorrenza ~77% a FINE COSTRUZIONE
→ ~36% a fine solve: **la LS erode metà del template**, perché spostare una cella-template
costa solo S13=2 soft. I buchi+burst della vista Materie×settimana sono il prodotto
dell'erosione, non (solo) di una costruzione carente.

IMPLEMENTAZIONE CANDIDATE della semantica quasi-pinned (in ordine di fedeltà al processo umano):
1. **Solve a stadi (il processo umano letterale):** solve con le celle-template PINNED
   (Timefold pinning statico per solve) → se resta hard residuo, s-pinnare SELETTIVAMENTE le
   sole celle coinvolte nel residuo e ri-solvare. Oggi è diventato plausibile: il residuo che
   la LS non risolve è ~5-6 lezioni (misura 399/401) e BoxRepair/discriminatore sanno già
   identificare celle e alternative. Riusa l'infra multi-solve dell'A/B; da spikare.
2. **Penalità di deviazione dal template come riferimento FISSO** (non settimana-precedente
   come S13): catturare l'assegnazione template a fine costruzione (mappa statica
   (corso,weekday,slot)) e penalizzare a tier medium alto la lezione fuori dalla sua cella —
   "si muove solo se ripaga un problema più grosso", l'approssimazione a un solo solve della
   semantica umana. Peso da benchmark (famiglia M20/M22 ma sotto, es. 300-500).
3. Leve di contorno dopo 1/2: rinforzo S12 (settimane assenti), cap settimanale per materia
   (M21-style a settimana, elimina i burst 13h), M27 generalizzato (partenze ritardate tipo
   "Recuperi da gennaio").

**RAFFINAZIONE (maintainer 2026-07-24): protezione GRADUATA PER VINCOLATEZZA.** L'approccio umano
completo è: (a) partire dai docenti/lezioni PIÙ VINCOLATI (già presente: il template ordina
per capacità libera crescente, e Q22 spinge T01 in testa), (b) trattarli poi come
quasi-pinned. La protezione quindi NON è uniforme: forza ∝ scarsità (es. peso di deviazione
proporzionale a 1/|VRP| o al conteggio di celle libere della lezione) — una lezione di
T01 (216 celle legali per 60 ore, il più stretto) dev'essere quasi immobile, una con
1.000 celle resta economica da muovere. Evidenza dal run 410-2: costruzione boxa 68, la LS
ne recupera 53 ma nel farlo satura i giovedì di G06 (nessuna protezione delle celle
scarse), ruin-recreate inerte (score invariato ph2→ph3), e le ultime 11 diventano ipotesi B.
La gradazione rende anche l'eviction di TASK-414 coerente: si sfratta sempre l'occupante
MENO vincolato (più alternative) — la stessa logica, dai due lati.

MISURA PRIMA (golden-first): per (gruppo, materia) media/varianza ore-settimana, max
settimana, prima/ultima settimana sul golden → pinnare i riferimenti; e la metrica di
EROSIONE (ricorrenza costruzione-end vs finale, per gruppo) come KPI del fix.
ASSORBE di fatto TASK-411 (la rigidità del fingerprint è la stessa cosa vista per gruppo):
tenere 411 come misura/gate, questa come il MECCANISMO.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore + A/B laptop con metriche di cadenza per materia
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Ciclo 2026-07-24 (sera): MISURA + MECCANISMO implementati su Fedora.

MISURA (golden-first, pinnata):
- Nuovo scripts/analyze/cadence-kpi.py: per (gruppo, materia) media/stddev ore-settimana, max, burst ratio (=max/mean), gap-weeks nello span attivo. Riferimenti golden pinnati in claudedocs/spalmone-2026-27/data/golden-cadence-reference.json (+md con tabelle).
- Numeri: burst>2 golden 41,6% vs run val414 86,9/89,3%; gap-weeks medi 5,96 vs 11,59/11,36. Sanity G01 Lingua Italiana confermato (golden max 6h, run max 11/9h con 18/15 settimane-gap su 38).
- resemblance-kpi.py esteso con breakdown ricorrenza per gruppo (output preesistente byte-identico); peggiori G09 e G10 ~28% (coerente col KPI cadenza, cross-check indipendente).
- EROSIONE misurabile: WeeklyTemplateConstructionRealSeedTest scrive target/construction-lessons.txt; ricorrenza fine-costruzione 76,0% vs 37,9% finale (val414): la LS erode meta' del template.

MECCANISMO (candidate 2 con gradazione, design: claudedocs/design/task-413-quasi-pinning-graduato-2026-07-24.md):
- WeeklyTemplatePlanner estratto (puro, byte-identico alla fase: real-seed test invariato); ScheduleService.refreshSolverStatics lo esegue PRE-solve e pubblica courseId->celle template + courseId->peso (mai a meta' solve: score corruption evitata by design).
- M32 "Template deviation" (medium): lezione unpinned/docente-reale fuori dalla cella (weekday,slot) del suo corso; cambiare data a parita' di cella NON e' deviazione. Peso = clamp(100000/|celle VRP corso|, 20, 500): T01 ~462, corsi liberi 20-80. Esenzioni: pinned, pseudo, corsi non templati, slot non canonici.
- Candidate 1 (solve a stadi) deliberatamente rimandato: si spika solo se l'A/B di M32 non chiude.

DoD parziale: /simplify fatto (4 finding applicati, 2 skippati con motivo); code-review high equivalente fatto (3 finder Opus, 1 confermato corretto: clearStaticState non azzerava le mappe M32; resto teorico); test.sh EXIT=0 x2 dall'orchestratore (721 test), terzo run finale in corso.

PENDING: A/B laptop x2 (braccio A = val414 esistenti; braccio B = 2 run con M32) con capture-run.sh canonica + cadence/erosione; gate: hard/box invariati, ricorrenza finale su dal 37%, cadenza verso golden, M20/M22/full-day non peggiorati.

A/B laptop val413 CHIUSO e VERIFICATO SUI RAW (2026-07-25 ~01:30, Fedora). Cifre riverificate indipendentemente (resemblance/cadence ricalcolati dai lessons.txt): hard 0/-1 (val414: -1/-2, primo 0-hard), box 0/0, ricorrenza unpinned 53,5/53,1% (da 37,9/36,6: erosione dimezzata), burst>2 79,1% (da 86,9/89,3, golden 41,6), gap-weeks/pair 9,84/9,49 (da ~11,5, golden 5,96), full-day 79,0/78,1 (su), day-start 93,3/93,6 (-1pt), M20/M22 +~1%, M32 residuo ~-271k medium/run. VERDICT zai == riverifica su tutte le cifre. RACCOMANDAZIONE: ADOZIONE (ratifica a maintainer, mattina). Anomalia non bloccante -> TASK-416: M32 assente dal breakdown analysis (mappa template ricalcolata vuota post-solve; tocca anche re-solve). Nota completa: claudedocs/spalmone-2026-27/campaign-status-2026-07-25-notte.md.

ADOZIONE RATIFICATA da maintainer 2026-07-25 mattina, sul quadro verificato (campaign-status-2026-07-25-notte.md: gate passati, S1 riverificato = convergenza al golden 1.280 vs 1.129 istanze gap docenti). M32 resta in HEAD; peso knob da benchmark; follow-up TASK-416 (analysis gap). DoD: /simplify applicato, code-review-high equivalente (3 finder Opus + fix confermati), test.sh EXIT=0 x3 orchestratore + ui-typecheck 0, A/B laptop x2 con KPI cadenza: TUTTO FATTO.
<!-- SECTION:NOTES:END -->
