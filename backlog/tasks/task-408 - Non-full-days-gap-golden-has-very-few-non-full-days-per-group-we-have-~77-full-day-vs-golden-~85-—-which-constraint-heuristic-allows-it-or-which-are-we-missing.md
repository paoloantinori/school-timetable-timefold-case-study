---
id: TASK-408
title: >-
  Non-full-days gap: golden has very few non-full days per group, we have ~77%
  full-day vs golden ~85% — which constraint/heuristic allows it, or which are
  we missing?
status: Done
assignee: []
created_date: '2026-07-24 08:26'
updated_date: '2026-07-26 20:59'
labels:
  - claude-planned
  - research
  - constraints
  - spalmone-2027
  - golden-first
milestone: spalmone-2027
dependencies: []
priority: medium
ordinal: 232000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
EXPLORATIVE — Fedora to investigate (maintainer insight, 2026-07-24, observed during the TASK-406 run-1 solve).

OBSERVATION: the golden solution has a VERY SMALL number of non-full days per group; ours has many more (full-day rate ~77.2% in 405b vs the golden's ~85.4%). The gap is consistent and per-group, not noise.

THE QUESTION: which of our constraints ALLOW non-full days to survive, or which are we MISSING to disallow them?
- ALLOW: M20 (daily quota fill) and M22 (calendar compactness) are both "logical hard" requirements encoded as MEDIUM (M20 = ofMedium(1000) per deficit-hour on a day with 0<placed<expected; M22 = 1000/excess-day). Because they are medium-tier, the solver tolerates non-full days whenever a hard constraint (or the search trajectory) makes a full day costly. The golden may treat "every active day is full" as a HARDER requirement than our medium encoding captures.
- MISSING: is there a golden-practice rule we have no constraint for at all (e.g. the golden never leaves a day at 1-3h if it could be full)?

HISTORICAL CONTEXT (maintainer, why this may be reconsiderable now): compaction constraints were historically DOWNGRADED H->M because the solver could not reach feasibility at hard tier (M13->H17 was the one promotion; M20/M22 stayed medium). That downgrade was decided BEFORE this week's human-heuristics stack (weekly-template stamp TASK-381, box-the-residual TASK-398, left-anchor TASK-405) — which now make full, compact days achievable BY CONSTRUCTION. So the H-vs-M tier of M20/M22 may be worth re-examining in the new landscape. (Per the primary directive: a tier change is judged on golden-faithfulness + logical correctness, never on compute cost alone — but the compute feasibility argument that forced the original downgrade may no longer hold.)

RELATIONSHIP TO TASK-406: TASK-406 (construction repair-tail deficit-aware cap) attacks this gap from the CONSTRUCTION side only (cut construction-end M20 deficit ~54% in the no-LS fixture). This task is the BROADER question across the whole pipeline: even with a better construction start, the LS still leaves non-full days — is that because M20/M22 are too weak (medium), or because a golden rule is missing entirely?

EVIDENCE: claudedocs/spalmone-2026-27/task406-results/val406-run{1,2}/kpi-fill.txt (per-group full/under/over + deficit histogram) vs the golden's own per-group full-day breakdown. The 405b baseline (claudedocs/spalmone-2026-27/task405b-results/) is the "before TASK-406" reference.

OUT OF SCOPE: re-promoting M20/M22 to hard without (a) the golden comparison and (b) a benchmark proving the solver still reaches hard=0 with the human-heuristics stack.
<!-- SECTION:DESCRIPTION:END -->

## CONFRONTO GOLDEN PER-GRUPPO ESEGUITO (Fedora 2026-07-24) — il gap è CONCENTRATO, non uniforme

Stessa metrica daily-fill-kpi su golden convertito vs val406-run1 (golden totale 83,7% su
questa scala uniforme):
- Quasi pari o MEGLIO noi: G03 91 vs 88 (meglio noi), G04 **57 vs 32 (meglio noi di
  25pt** — perché noi compattiamo dove il golden spalma, vedi 407), G08 87 vs 90,
  G12 80 vs 85, primi anni ~97-99 vs 100.
- **Il gap vero sta in TRE gruppi stage-window: G09 59 vs 95 (−36pt!), G13 74 vs 89
  (−15), G10 82 vs 94 (−12)**, più G07 94 vs 100 (−6). G14 0 vs 0 (questione dati).

DIAGNOSI: la firma coincide col limite GIÀ DOCUMENTATO in TASK-385 (fixture): per i gruppi
con finestra stage, `countActiveWeeks`/`buildWeeklyTargets` spalma le ore del corso su TUTTE
le settimane invece che sulle sole settimane non-stage → target settimanale troppo basso →
giorni strutturalmente sotto-riempiti. Era filed come follow-up "stage-aware active-weeks";
i numeri live ora mostrano la stessa firma. QUESTA è la prima leva (meccanica, construction),
non il tier dei vincoli.

SULLA PROMOZIONE M20/M22 → HARD (il ripensamento storico): il confronto golden la BOCCIA
direttamente — il golden stesso ha **320 giorni sotto-pieni** (83,7%, non ~100%): "ogni
giorno attivo è pieno" NON è pratica golden, e un hard flat renderebbe infeasibile il golden
(o, pinned-exempt, forzerebbe box/infeasibilità sui giorni dove disponibilità docenti rende
il pieno impossibile). Il vecchio downgrade H→M resta corretto per direttiva primaria, non
solo per compute. La strada è: (1) stage-aware weekly targets nel template builder;
(2) eventuale ricalibrazione del PESO M20 ora che M12 fact-based non comprime più (la
diagnosi 406 fase A ha identificato proprio questo come driver post-LS); (3) il
deficit-aware repair della 406 già landed. Ordine consigliato: (1) subito dopo il verdetto
406, con guard golden e A/B.

## FINDING DALLO STRUMENTO VISIVO (Fedora 2026-07-24, scetticismo di maintainer confermato)

Lo strumento di confronto puntuale (`scripts/analyze/golden-vs-run-viz.py` →
`claudedocs/spalmone-2026-27/confronto-golden-vs-run.html`) ha rivelato un pattern che gli
aggregati nascondevano: le nostre strisce hanno **giorni attesi COMPLETAMENTE VUOTI** (G04,
G08, G07, concentrati da marzo in poi) che il golden non ha quasi mai. Verifica sul
codice: **M20 è Lesson-sourced con filtro `totalHours > 0 && totalHours < quota` → un giorno
atteso svuotato del tutto NON è penalizzato** (uno stream sourced sulle Lesson non può vedere
un giorno senza lezioni — lo stesso buco che M30.2 risolse per i giorni-lab FC con il
complemento `ifNotExists` da DailyActivityQuota, MAI esteso a M20). Incentivo perverso: un
giorno a 1h costa (quota−1)×1000 ≈ 5.000 medium, svuotarlo costa 0 → il solver è premiato a
EVACUARE i giorni quasi vuoti invece di riempirli. Questo è "il vincolo mancante" della
domanda originale, complementare (non alternativo) al fix stage-aware weekly targets.

**Fix candidato:** complemento M20.2-style (`ifNotExists` da DailyActivityQuota expected>0,
scope NON-FCQuota incluso) che penalizza il giorno atteso vuoto ALMENO quanto il giorno a
1h (≥ quota×1000), con la stessa esenzione pinned-coerente di M20 da ragionare (un giorno
vuoto non ha lezioni da esentare: valutare day-level rispetto ai pin). Golden guard: il
golden convertito ha pochissimi giorni attesi vuoti — misurare e pinnare.

**Secondo pattern (fingerprint):** il golden è un template quasi rigido (colonne al 97-100%
+ blocchi pomeridiani piccoli e netti, es. G04 lun/mar 18% = pattern periodico lab), il
nostro run è diffuso (52-89% ovunque, nessuna struttura netta). Metrica visiva della
ricorrenza che il KPI numerico (~36%) sottorappresenta.

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-07-25 ~09:30: avviata fase DISCOVERY offline (solo dump val413/val414 + golden, nessun solve) - report atteso in claudedocs/spalmone-2026-27/task408-discovery-2026-07-25.md.

DISCOVERY COMPLETATA E VERIFICATA (2026-07-25 ~10:30, report: claudedocs/spalmone-2026-27/task408-discovery-2026-07-25.md). Causa dominante del gap full-day NON e' la debolezza dei medium ma il meccanismo "fact mancante = giorno aperto": data (gruppo,data) senza riga nei fogli -> dailyQuota ricade su maxHoursPerDay -> M20 (1000/h) PREMIA il riempimento di giorni che il golden lascia SEMPRE a 0 (26 coppie, ~140h, 81% del deficit dei gruppi puliti in val413; G12 spiegato al 100%: 64h su giovedi' fantasma in finestra stage). Verificato di prima mano dall'orchestratore: typo chiusura DataGenerator:127 (2026-01-22 -> doveva essere 2027-01-22; golden 15/15 closed quel giorno, foglio FC 0,0,0, class_days S; il run piazza 61 lezioni di cui ~46 ore reali su 9 gruppi senza fact - e i gruppi CON fact a zero correttamente non piazzano nulla). Escluso il ramo "ore finite" (12/14 gruppi piazzano ore totali >= golden). M32 ortogonale (cambia QUALI giorni, non quanti).

LEVE DECISE: (1) fix typo DataGenerator AUTORIZZATO da maintainer 2026-07-25 (~10:45) - in testa al prossimo treno post-416, con ripin motivato dei numeri che si muovono (172->171 giorni, timeslot 1376->1368, fixture costruzione, eventuali pin guard); (2) fix modello: per i gruppi coperti dai fogli, data senza DailyActivityQuota = giorno CHIUSO (atteso 0, niente fallback maxHoursPerDay) - golden-conforme su tutte le 26 coppie, catena locale: guard + fixture costruzione + score-a-snapshot, gate finale nel prossimo A/B laptop cumulativo.

TRENO 2026-07-25: entrambe le leve implementate e reviewate (3 Opus, zero bug): typo chiusura (171 giorni/1368 timeslot, ripin motivati, guard golden INVARIATO al pin) + isNoSessionDate/VRP/H26/dailyQuota=0/expectedHoursCapFor=0. Copertura caveat verificata empiricamente: FC/G14 hanno 171/171 righe -> filtro no-op esatto per loro. Fixture costruzione: +77 skip date fantasma, scoped 86,1->86,5 (il grosso del guadagno e' effetto LS -> gate laptop). RESIDUO NOTO (teorico, fuori diff): WeeklyTemplatePlanner.countActiveWeeks tratta ancora una data senza row come giorno di lezione - imprecisione minore del target settimanale sulle ~12 date fantasma, nessun drift del reference (fatti statici); da valutare in un follow-up se l'A/B mostra segnali.

A/B val408 x2 VERIFICATO SUI RAW (2026-07-25 ~14:50): full-day 79,9/80,0 (record; val413 79,0/78,1), zero lezioni 2027-01-22 (erano 61), hard -1/-1 (solo H17), box 0, ricorrenza 52,8/53,6 stabile, day-start 93,7/93,7, sharpness 79,7/77,5 (su), burst 81,6/79,1 (r2 = val413: varianza, non regressione), M20 -983k (da -1.148k). Erosione per fase (2 run concordi): 74,3 -> 52,8/53,7 TUTTA in fase 2, fasi 3-6 immobili, ruin-recreate inerte per la terza volta. RACCOMANDAZIONE: ADOZIONE (ratifica maintainer).

RISPOSTA QUANTITATIVA alla domanda del task (decomposizione train-run1, 2026-07-26 notte, script su raw + calendari seed): il gap full-day 75,5% vs golden 85,4% si decompone in 1.420h di deficit in-piano = 62% G14 (875h: foglio 990h vs incarichi 115h - DATI, domanda scuola), 22% famiglia FC (318h: floor G04 >=23h + tensioni fogli, domande gia' agli atti), 16% margine solver reale (227h su 6 gruppi, di cui 164h con surplus corrispondente su 126 giorni sopra-atteso della stessa classe). Nessuna ora fuori piano. G01/G02/G05 gia' al 100% full. CAUSA solver-side: M20 punisce il deficit ma nulla punisce il giorno sopra-atteso -> la LS parcheggia ore in surplus. IMPLEMENTAZIONE della parte solver: TASK-422 (pass surplus->deficit rebalance, checkpoint-validabile, upside +2-3pp). Il resto del gap si sblocca SOLO con le risposte della scuola (G14, FC).

CORREZIONE (2026-07-26 ~23:15, pushback maintainer 'il golden fa fede'): la decomposizione precedente era sul deficit ASSOLUTO, non sul DIFFERENZIALE vs golden - errore di lente. Rimisurato il golden per-gruppo con lo stesso daily-fill-kpi (golden convertito pipe, 83,7% base uniforme): G14 golden=0% come noi -> SI CANCELLA dal gap (il golden conferma il corso breve; il foglio 990h e' irrealistico per entrambi, resta solo rumore di base del KPI); G04 noi 43% vs golden 32% -> siamo MEGLIO (+18 giorni; il finding H24-piu'-rigido-del-golden resta vero ma NON guida il gap, declassato a osservazione). GAP REALE = 197 giorni (8,2pp): G10 -56, G08 -39, G09 -37, G13 -27, G12 -22, G11 -18, G06 -9, G07 -7 - gruppi stage-window + famiglia FC. ~164h del deficit differenziale ha surplus in-piano nella stessa classe = bersaglio diretto TASK-422 (in implementazione stanotte su richiesta maintainer); il residuo per-gruppo (~130h) e' da attribuire (candidati: delta incarichi-vs-foglio per gruppo, ore oltre target M12).
<!-- SECTION:NOTES:END -->
