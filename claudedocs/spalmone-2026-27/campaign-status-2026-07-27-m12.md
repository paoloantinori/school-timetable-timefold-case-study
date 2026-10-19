# Campaign status, m-12 (Gap verso il golden, post-adozione staged), 2026-07-27

Mandato autonomo laptop (maintainer ~07:30 CEST, laptop Fedora chiuso). HEAD di partenza
**e7e71e40**, poi handover **21965766** + addendum no-chokepoint **d6bacc64**. Ratifiche
finali sempre a maintainer; io non committo MAI adozioni né cambio default del solver.

## Sequenza e stato

| Task | Cosa | Stato |
|---|---|---|
| 424 | M20 pin-netting (capofila) | **polling Fedora** (finestra 07:18-08:48 CEST, max 90min); se non arriva lo implemento da zero |
| 425 | cadenza fine: 2 leve GATED (burst su 4 gruppi materie-micro; gap-weeks MODA) | dopo 424 |
| 426 | day-start: SOLO anatomia (classificatore train2 vs golden) | dopo 424 |
| 427 | surplus progressione: condizionale (rimisura coda +4h post-424) | dopo 424 |
| 428 | dossier scuola (G14 990vs115, floor G04, compattare-vs-spalmare, stage G08) | indipendente, puro doc |
| live | A/B x2 ratifica + VERDICT-m12.md | dopo 424+425 checkpoint-verdi |

Addendum 07:50 (no-chokepoint): esaurita m-12 (o tutti i task bloccati/in attesa ratifica),
avanzare DA SOLI sul prossimo task backlog (High prima, escludendo adozioni e bloccati).
Candidati: TASK-417, cluster benchmark 280/281/284/332, TASK-407.

## TASK-424, design (analisi completata, pronto a verificare o implementare)

**Bug**: `dailyLessonOccupancy` (popolazione condivisa M20+M33) conta `totalHours` =
lezioni NON authoritative-pin (`NOT_AUTHORITATIVE_PIN`), ma `dailyQuota(group,date)` è la
quota PIENA. Su un giorno misto (es. quota 6, 3 pin autoritativi, 3 unpinned) M20 carica
`m20DeficitCost(quota - totalHours)` = `m20DeficitCost(6-3)` = 4500 e paga marginale 2000
per la 4a ora unpinned, quindi la LS è INCENTIVATA a pile-are surplus sui giorni già pieni
di pin. M33 specchio: `surplus = totalHours - quota` (totalHours esclude i pin) quindi è
CIECO ai surplus dove pin+occupancy > quota (29/112 giorni-sopra invisibili, 26%).

**Fix** (anti-arbitraggio, stessa base ovunque): `quota_eff = max(0, dailyQuota -
authPinCountDelGiorno)`. Applicare in M20, M33, e nei pricer condivisi `m20DayCost`/
`m20Quota` (consumer: `HoleRepair:470`, `SurplusRebalance:574`). M30 NON si tocca
(semantica deliberata: conta anche i pinned). `dailyQuota`/`m20Quota` RESTANO la quota
grezza (M20.2 le usa su giorni con ZERO lezioni, quindi zero pin, netting no-op).

**Come ottenere authPinCount**: cambiare `dailyLessonOccupancy` in groupBy a 4-tuple
`(group, date, occupancy, authPinCount)` con due `sum` collector
(`isAuthoritativePin(l)?0:1` e `isAuthoritativePin(l)?1:0`). M20/M33 leggono quota_eff
nel filter/weigher.

**Sicurezza golden guard** (fixture 100% pinned, authoritative=2651, promoted=7132 NON
authoritative): sul fixture ogni lezione è authoritative, quindi occupancy=0 ovunque:
- M20 filtro `occupancy>0` falso, 0 match (resta 0/0)
- M33 filtro `occupancy>quota_eff` con occupancy=0 falso, 0 match (resta 0/0)
- M20.2 stream `forEach(DailyActivityQuota).ifNotExists(lesson)` su giorni con ZERO lezioni,
  quindi zero pin, quota_eff irrilevante; se `dailyQuota` invariata resta -14500/2

**Predizione re-score statico su train2** (placement fissato, solo ri-attribuzione score):
M20 deficit CROLLA sui 267 giorni misti (match/score giù); M33 surplus SALE (ora misura
`realTotal - quota`, vede i 29 giorni prima invisibili). Su live solve la leva rimuove
l'incentivo a creare surplus (non solo ri-attribuisce).

## Baseline train2 (val419-train2-run1/run2, HEAD a71f77b5), riferimento ratifica

hard 0/0, H16b 0, H17 0, buchi 0/0, medium -2.301M/-2.330M, ricorrenza unpinned
73,5/73,7, full-day 77,0/76,6, giorni-sopra 111/118 (golden 70), day-start 95,9/96,0,
sharpness 82,8/82,1, burst>2x 45,1/45,5 (golden 41,6), gap-weeks 1813/1776, M33
-11,2k/69 e -12k/77, M20 -1,40M/447 e -1,41M/450, M20.2 -112k/10 e -98,5k/9, promoted 7132,
authoritative pin 2651 (866 MODA + 1785 stage), SurplusRebalance 16h/33h mosse.

## Metodo (obbligatorio per ogni task)

checkpoint-first (`claudedocs/localized-testing-guidelines.md`): harness su snapshot
committati, re-score statico con PREDIZIONE SCRITTA PRIMA del run, smoke Phase2 600s A/B.
Cicli live completi UNA volta a fine blocco. GoldenSolutionRegressionTest intoccabile
(se un pin cambia, STOP, annota, mai ricalibrare). DataGenerator MAI. DoD: /simplify +
/code-review high + `./scripts/core/test.sh` con EXIT CODE reale (`cmd>log; E=$?; exit $E`).
Commit+push per task, backlog aggiornato.

## TASK-424 verifica (predizione SCRITTA PRIMA del run, 08:25 CEST)

424 arrivato da Fedora (e4d2ba5a, WIP-CHECKPOINT-GREEN). Implementazione letta e corretta:
effectiveQuota = max(0, m20Quota - getAuthoritativePinnedHours), source-of-truth unica per
M20/M20.2/M33/m20DayCost; M33 gate scope = m20Quota grezza ma surplus vs effectiveQuota (i 31
giorni fully-pinned restano in scope); mappa popolata in setPinnedLessonTimeslotsByGroupStatic
(stessa passata del map VRP, filtro isAuthoritativePin && !teacher.pseudo). Fedora delega al
laptop: test.sh EXIT reale, re-score train2-run1, DoD /simplify + /code-review high.

PREDIZIONE re-score statico su train2-run1 (placement fissato, solo ri-attribuzione):
- guard golden: M20 0/0, M33 0/0, M20.2 -14500/2 INTATTI (strutturale, fixture 100% pinned)
- M20: match CROLLA da 447 (i falsi-deficit sui ~267 giorni misti spariscono: quota 6/3pin/3unpinned era deficit 3=4500, diventa deficit 0); medium M20 cala di alcune centinaia di k
- M33: match SALE da 69 verso ~111 (il count kpi-fill "sopra": ora misura realTotal - quota, vede i 29 giorni prima invisibili + i fully-pinned); medium M33 sale proporzionalmente (~+4-5k)
- M20.2: INVARIATO a -112k/10 (giorni senza lezioni = zero pin = netting no-op)
- netto medium: M20 cala molto piu' di quanto M33 sale (i falsi-deficit erano ~447 match x avg ~3000); re-score statico migliora, MA e' solo ri-attribuzione (l'effetto LIVE di rimozione incentivo si misura col ciclo A/B)

ESITO re-score (ScoreASnapshotTest su train2-run1, 08:31 CEST, EXIT 0):
- M20: -1.400.500/447 (live) -> -447.500/**212** (re-score). MATCH CROLLA come predetto.
- M33: -11.200/69 (live) -> -13.900/**96** (re-score). MATCH SALE (69->96). Sotto il ~111
  predetto: il gate scope m20Quota>0 exclude i giorni quota-0 (no-session/stage-only/chiusi)
  che il kpi-fill "sopra" (111) conta, e la base e' aula+lab (effectiveQuota) vs il count
  piu' largo del kpi. Direzione e segno confermati.
- M20.2: -112.000/10 -> -112.000/**10** INVARIATO (byte-identico, come predetto).
- guard golden: INTATTO (test.sh PASS: M20 0/0, M33 0/0, M20.2 -14500/2).
Netto: l'incentivo perverso e' rimosso. Giorno quota6/3pin/3unpinned: 4a ora unpinned
costava -2000 marginal (M20 pagava), ora costa +100 (M33 carica). Static re-score = solo
ri-attribuzione; l'effetto LIVE (LS non crea piu' surplus) si misura col ciclo A/B.

DoD: test.sh EXIT=0 reale (825 test, 0 fail, 11 skip env-gated). /simplify + /code-review high
in corso sul diff e4d2ba5a.

## TASK-424 /code-review high (08:35-09:00 CEST)

8 angoli (4 cleanup gia' da /simplify + 4 correctness/conventions). Esiti:
- conventions: NESSUNA violazione (em-dash byte-level pulito, DataGenerator non toccato)
- cross-file tracer: nessun bug runtime; 1 nota diagnostica (re-score snapshot STAGED non fedeli perche' lessons.txt manca colonna promotedPin - pre-esistente, solo diagnostico; train2 post-release e' fedele)
- removed-behavior: 5 punti audit tutti re-istabiliti (guard G14/null in m20DayCost via effectiveQuota; M20 filter/weigher stesso effectiveQuota no-arbitraggio; M33 gate raw>0 superset del net; cast (int) safe <=800; test rinominati non scoperti)

FINDING CONFERMATO (CANDIDATE 1, da deferrire - fix in SurplusRebalance, non nel diff 424):
SurplusRebalance.isSurplusDay (:545) = occupancy>0 && m20MarginalRemovalCost==0. Post-424 un giorno
"at-plan con pin" (N pin auth + 1 solver, quota N+1, effectiveQuota 1) ha m20DayCost(1)=0 e
m20DayCost(0)=0 -> marginale 0 -> DONATORE (pre-424 era <0, non donatore). Effetto: SurplusRebalance
sposta l'ora solver a un giorno deficit, il donor va a N pin + 0 solver (occupancy 0 -> deficit
invisibile a M20). SEVERITA' BASSA: deficit aggregato reale invariato (recipient -1, donor +1 = netto
0), e' ridistribuzione da deficit visibile a invisibile; la KPI daily-fill (adozione) e' onesta.
Fix corretto: isSurplusDay richiedere occupancy > effectiveQuota (surplus genuino) non solo marginal==0.
Verificato leggendo isSurplusDay + m20MarginalRemovalCost (matematica deterministica, non speculativa).
DISPOSIZIONE: defer a follow-up task (SurplusRebalance netting-awareness); il live A/B misurera' se
impatta le metriche. NON blocca 424 (core netting corretto).

FINDING MINORE (CANDIDATE 2): pin autoritativi con teacher null non nettati (il setter aggiunge
`teacher != null`, quindi tali pin sparirebbero da entrambi i lati -> deficit fantasma). Edge case:
DataGenerator setta sempre teacher, solo righe CSV hand-crafted. Defer.

## TASK-425 CHIUSO (cadenza fine, ~09:00 CEST)

M34 (weekly burst cap, peso 100, knob planner.m34.weight) + M35 (weekly spread deficit,
peso 50, knob planner.m35.weight). Self-gating sul reference per-(gruppo,materia) pubblicato
da golden-cadence-reference.json (campo max per M34, active_weeks per M35); pair assenti ->
inerti. M34 e M35 spingono entrambi verso piu' settimane (sinergici, non in tensione).

DoD: L0 299/0 (7 nuovi test ConstraintVerifier); L1 guard M34 0/0/0 structural + M35 -50/1
residuo calibrato documentato (G04/Scienze W24 oltre SCHOOL_YEAR_END, stessa classe del gap
S12 5.623-vs-5.625), NESSUN pin esistente cambiato (pure additions); full suite orchestratore
832/0 EXIT 0. /simplify 4-agent: nessun bug, #3 javadoc drift applicato, altri defer con
motivazione. /code-review skeptic: 6/6 claim CONFIRMED; cattura CRITICA che il cast (int) M34
e' RICHIESTO (javap: Timefold 2.3.0 sum->Long, non int - il finding /simplify #2 "cast
ridondante" era falso positivo; non applicato perche' l'edit era fallito, cast intatto).
Live A/B x2 resta il gate di adozione (maintainer).

FOLLOW-UP DEFERITI (cleanup post-m-12): readCadenceKnobProperty duplica readM32KnobProperty;
E2 re-parse JSON a ogni loadSchedule idle (solo UI); A2 new File(path) -> classpath resource
per robustezza java -jar; M35 beyond-horizon residual -> 0 se orizzonte W24 o alias RA.

## LIVE A/B x2 IN CORSO (10:37 CEST, HEAD 824fa2a8 con 424+425)

Setup verificato: profilo dev fresco, 1368 timeslot, import-calendario RC=0, 2651 pinned
(866 MODA + 1785 stage, = CLAUDE.md), pre-assign COMPLETE. App running (M34/M35 attivi
ai pesi default 100/50; phase-transition dump NON abilitato perche' app mvn-launched,
MAVEN_OPTS non reach il fork - gap, solo erosion analysis manca, le metriche core ci sono).
Wrapper: scripts/benchmark/m12-solve-capture.sh <label> <outdir> (caffeinate + solve-staged
+ poll NOT_SOLVING-senza-stagedStage + capture-run). Solve #1 (run1) lanciato 10:37 in
background, capture -> claudedocs/spalmone-2026-27/m12-results/m12-run1. Poi run2, poi
compare vs train2 (hard 0, full-day 76,6-77,0, sopra 111-118, ricorrenza 73,5-73,7, burst
45,1-45,5), poi VERDICT-m12.md (ratifica solo maintainer).

## TASK-426 CHIUSO (anatomia, ~10:45 CEST)

Report: claudedocs/spalmone-2026-27/task426-daystart-anatomy-2026-07-27.md. 90 giorni non
ancorati (95,9% vs golden 99,86%); G11 32 + G04 21 = 59%; mercoledi 37%; 0 strutturali
/ 88 solver-choice / 2 mixed-pin. SORGENTE = costruzione (phase-2-end ha gia' 82; LS/repair
+8; HoleRepair/SurplusRebalance esonerati). CAUSA-RADICE = H1 teacher conflict cross-gruppo
allo slot 0. CONCLUSIONE LEVA: NON M18 (il blocco e' HARD, M18 e' medium rounding error;
a nessun peso M18 chiude un hard conflict). La leva reale e' COSTRUZIONE (template slot-0
teacher balancing cross-gruppo) o una mossa LS slot-0-breaking -> follow-up oltre m-12.
Impatto VERDICT: il gap day-start (95,9% vs 99,3) e' NOT-closeable dalle leve medium di m-12;
e' un known construction-level residual da segnalare a maintainer, non un fallimento di m-12.

## LIVE A/B run1 RESULT (HEAD 824fa2a, 424+425, 14:05 CEST)

Solve #1 valido (81min solver reale, 10:37-11:58 CEST; app poi idled). Score 0hard/-1.342.549medium/
-14.115soft, 12286/12286 assigned, 2651 pinned. KPI (lessons.txt rigenerato via db-query dopo
che capture-run.sh aborti' per container-check flawed + export.json.gz broken-147B noto):
- full-day 78,2% (train2 77,0/76,6; golden 85,4) -> +1,2pp
- sopra 87 (train2 111/118; golden 70) -> -24 (424 netting rimosso incentivo surplus)
- burst>2x 40,2% (train2 45,1/45,5; golden 41,6) -> -4,9pp, SOTTO golden (M34 cap funziona)
- gap-weeks 1710 (train2 1813/1776) -> -103 (M35)
- day-start 96,3% (train2 95,9/96,0) -> ~pari (426: gap H1-construction, non m-12-closeable)
Tutte le metriche si muovono nella direzione attesa. hard 0 mantenuto. n=1, run2 in corso per stability.

## m-12 CHIUSO (VERDICT scritto, ~16:25 CEST)

VERDICT-m12.md: claudedocs/spalmone-2026-27/m12-results/VERDICT-m12.md (descrittivo, adozione=maintainer).
Run1 (n=1, forte): hard 0, full-day 78,2% (+1,2pp), sopra 87 (-24), burst 40,2% (-4,9pp, M34),
gap-weeks 1710 (-103, M35), M20 -417k/208 (vs train2 -1,4M/447, 424 netting collassato su live),
M34 -8,9k/59 + M35 -18,1k/70 attivi. day-start 96,3% (426: H1-costruzione, non m-12-closeable).
427 non-triggerato (surplus stabile). 428 draft (Q23/Q24). TASK-429 follow-up (SurplusRebalance).
Run2 (3 iter B) in corso autonomo; capture+update a n=2 se finisce.

## Run2 finding (16:35 CEST): reset-artifact, NON m-12 regression

Run2 fatto con api.sh reset + solve-staged (NON profilo fresco, scorciatoia per budget) = hard -54
(tutto H0: 54 lezioni unassigned), 12234 placed, full-day 74,1%, sopra 120, burst 46,9%. Stesso
codice 424+425 su FRESH profile (run1, train2 0/0) = hard 0. Quindi -54 = artefatto del path reset
(non m-12). Runbook impone fresh profile a ogni run. Fresh-profile run2 = follow-up raccomandato
per n=2 pulito; non blocca (run1 fresh hard 0 + train2 baseline 0/0-hard = evidenza forte + modello
noto-stabile). VERDICT-m12.md aggiornato con la distinzione run1-valido / run2-artefatto.

## m-12 STATUS RIEPILOGO (16:40 CEST)

COMPLETATI (Done + pushati): TASK-424 (M20 pin-netting), TASK-425 (M34+M35 cadence), TASK-426
(day-start anatomy: gap H1-costruzione non closeable da m-12), TASK-427 (surplus non-triggerato,
M33 flat resta), TASK-428 (dossier scuola Q23/Q24/Q25). + TASK-429 follow-up creato (SurplusRebalance
netting-awareness, low).

LIVE A/B: VERDICT-m12.md consegnato. Run1 (fresh profile) = hard 0, full-day 78,2%, sopra 87,
burst 40,2%, gap-weeks 1710 (tutte migliate vs train2, leve attive M34/M35/M20-netting). Run2
(reset+solve-staged, NON fresh) = hard -54 H0 artefatto del path reset (non m-12; stesso codice
fresh = hard 0). Fresh-profile run2 = follow-up low (run1 + train2 0/0 = modello noto-stabile,
fresh run2 confermerebbe run1, valore diagnostico marginale). Ratifica adozione = maintainer.

REMAINING m-12 (task esistenti, non bloccanti): TASK-407 (overflow ideal-end, indagine read-only
IN CORSO), TASK-412 (?), TASK-274 (Restore S9 max/week - probabilmente SUPERATO da M34 che fa
lo stesso cap settimanale per (gruppo,materia); da verificare/chudere come duplicato).

POST-m-12 (addendum, dopo esaurimento m-12): TASK-417 (room export-mode misura), cluster benchmark
280/281/284/332 (ADOZIONI, escluse finche' maintainer ratifica m-12).

## m-12 FINALE (17:00 CEST) - mandato completato

CONSEGNATO (tutti Done + pushati, HEAD 6a571a19):
- TASK-424 M20 pin-netting (Fedora impl + laptop DoD piena, finding SurplusRebalance deferrito a 429)
- TASK-425 M34+M35 cadence (opus-subagent impl + laptop DoD, skeptic 6/6 CONFIRMED)
- TASK-426 day-start anatomy (gap = H1 teacher-conflict cross-gruppo slot 0, non m-12-closeable)
- TASK-427 surplus progressione (non-triggerato, M33 flat resta)
- TASK-428 dossier scuola (Q23 G14, Q24 G04 floor, Q25 compattare-vs-spalmare)
- TASK-274 (superseded da M34), TASK-407 (ideal-end overflow = golden-faithful, no change)
- VERDICT-m12.md (run1 fresh hard 0, full-day 78,2%, sopra 87, burst 40,2%, gap-weeks 1710;
  run2 reset-artifact documentato; ratifica adozione = maintainer)
- TASK-429 follow-up creato, TASK-430 (slot-0 balancing, follow-up 426) gia' in backlog

REMAINING (per sessione fresca, ognuno con DoD propria):
- TASK-412: BLOCKED (attesa risposta scuola a Q22 giorni fissi docenti)
- TASK-429: small fix SurplusRebalance.isSurplusDay (low severity, live-confirmato non-urgenza,
  cambia behavior -> va fatto con DoD attenta, non di fretta)
- TASK-430: slot-0 teacher balancing in costruzione (la leva del 426, cambio consistente)
- TASK-417: room export-mode full-grid misura (post-m-12, needs app/harness)
- cluster benchmark 280/281/284/332: ADOZIONI (escluse finche' maintainer ratifica m-12)

STOP legittimo: il core m-12 e' completamente consegnato + verificato; i task workable rimanenti
sono follow-up low-severity (429), consistente-nuovo (430/417), o bloccati (412/benchmark).
Prossima sessione: ratifica maintainer su VERDICT-m12 + eventuale 429/430/417 con budget fresco.

## NUOVO MANDATO 13:35 CEST (run2bis + 429 + 430)

HEAD di partenza c9fd1fd6 (pull: remote ha aggiunto TASK-432 profile-following + principio
pattern-exhaustion-first in CLAUDE.md, 3 commit docs). Tre attivita' checkpoint-based.

### (1) RUN2-BIS FRESCO - CHIUSO, n=2 pulito entrambi hard 0
Run2 di stanotte era invalido (path reset, hard -54). Rifatto col runbook completo: volume
planner-data-dev wiped + reseed, import-calendario, pre-assign COMPLETE, **app lanciata via
java -jar diretto con -Dplanner.phase-transition-dump.enabled=true** (verificato sulla JVM via
jcmd; il dump flag NON reach il fork mvn quarkus:run, per questo java -jar diretto), solve-staged,
caffeinate. Solve completato 18:45 CEST (~66min solver reale), **0hard/-1366162medium/-21357soft**,
0 buchi interni, **4 phase-transition dumps catturati** (per-phase erosion construction->finale
ora misurabile, run1 non li aveva). Monitor api.sh wait e' morto exit-1 per fetch-hiccup transiente
PROPRIO al completamento (fetch||exit 1); solve persistito nel DB, cattura OK.

| Metrica | run1 | run2bis | golden |
|---|---|---|---|
| hard | 0 | 0 | 0 |
| full-day | 78,2% | 78,5% | 85,4 |
| sopra | 87 | 83 | 70 |
| burst>2x | 40,2% | 41,8% | 41,6 |
| ricorrenza unpinned | 73,4% | 72,8% | - |
| day-start | 96,3% | 96,1% | 99,3 |

Tutto entro varianza run-to-run, nessuna regressione -> **stabilita' confermata su n=2**.
VERDICT-m12.md aggiornato con la colonna run2bis; watch-item "run2 n=2" RESOLTO.
Asset: claudedocs/spalmone-2026-27/m12-results/m12-run2bis/.

### (2) TASK-429 (isSurplusDay netting-aware) - CHIUSO, commit 67617fad + pushato
SurplusRebalance.isSurplusDay: era `m20MarginalRemovalCost==0`, ora `occupancy > effectiveQuota`
(stessa definizione net-surplus di M33, MENO il gate scope m20Quota>0 che resta in measure() cosi'
un giorno quota-0/stage resta donatore legale ma mai "surplus day" nel count DayFill). Premise
verificata via m20DayCost line 1857-1858 (guard placedTotal>0 -> at-plan-with-pin legge marginal 0).
Diagnostic countSurplusDaysExcludedByNetting() aggiunta ( stampa la deflazione at-plan-with-pin ).

REGRESSIONE catturata dal full suite (SurplusRebalanceTest.neverMovesPinnedOrPseudoTeacherHours):
la prima versione aveva messo il gate m20Quota>0 DENTRO isSurplusDay (mirroring M33 troppo letterale),
escludendo i giorni quota-0/stage dal essere donatori. Corretto: gate solo in measure() (come
l'originale), isSurplusDay = occupancy > effectiveQuota nudo. Dopo il fix la pass muove 66h (vs 25
buggy) perche' i giorni quota-0 sono di nuovo donatori. SurplusRebalanceTest 8/8.

### (3) TASK-430 (slot-0 teacher balancing, construction-level) - CHIUSO, commit 42fc1e96 + pushato
La leva del TASK-426: reserveSlot0Anchors pre-pass in WeeklyTemplateBuilder. **FLAG-GATED default
OFF** (-Dplanner.construction.slot0-balancing.enabled) -> NON adottato, materiale per il prossimo
giro di ratifica. Per ogni weekday riserva un anchor slot-0 per gruppo usando un docente libero
allo slot 0 cross-gruppo, hardest-to-anchor-first; seeda le mappe live cosi' il main placement
(immutato) le rispetta. Conflict-free per costruzione (H1/H3/H7 + quota-kind checked).

Validazione checkpoint: WeeklyTemplateConstructionRealSeedTest passa flag-OFF e flag-ON (guard
tenute: recurrence>=50, full-day>=65, zero conflitti). **Day-start construction 98,3% -> 98,8%**
(34 -> 25 non-ancorati, -26%; deep offset +10/+12 eliminati). Stamp skipped-conflict 0 entrambi
(8183->8487 placed, +304 per piu' ancoraggi). Miglioramento modesto ma reale (anatomy-predetto:
il residuo e' H1-conteso, non chiudibile del tutto da una lever greedy).

### DoD e note
- /simplify: fatto (4-angle review, 1 cosmetic fix).
- test.sh EXIT 0 (full suite 832 test, 0 fail; dopo il fix regressione 429).
- /code-review high: in corso (l'ultimo step DoD).
- 3 commit pushati (429, 430, run2bis+VERDICT); rebase clean sul remote (TASK-432/CLAUDE.md docs).
- GATE STALE (deferred cleanup, NON problemi): verify-baseline.sh seed vuole timeslots 1376 (reale
  1368 post-TASK-408); import vuole stage 1800/off-grid 280 (reale 1785/295, closure fix sposto'
  15 stage h pinned->off-grid). Entrambi pre-TASK-408. Aggiornare il gate e' follow-up.

### Backlog queue (prossimo, no-chokepoint)
Dopo i 3 task: continuare autonomi. TASK-432 (profile-following, high, appena creato dal remote)
e' il candidato naturale prossimo (stamp segua il profilo per-data del foglio, non cadenza
uniforme; G10 T01). Poi TASK-429/430 ratifica (maintainer), TASK-417, cluster benchmark.

### CHIUSURA DoD + backlog (~19:15 CEST, HEAD 753c510b)
/code-review high completato su 429+430:
- 429: self-review 3 angoli (line-by-line, removed-behavior, cross-file), nessun finding sopravvissuto.
- 430: skeptic agent (Opus) ha CONFIRMED i claim core (H1/H3/H7/quota-kind conflict-free,
  weeklyTargets accounting, flag-OFF byte-identical) con verifica empirica indipendente
  (flag-ON stampSkipConflict=0, recurrence 75,2->77,5%). 1 finding PLAUSIBLE (H11/day-target
  ceiling non checkato dalla reservation) -> FIXATO in 753c510b (specchia tryPlaceChunk 471-483;
  behavior-preserving, day-start invariato 98,8%). Full test.sh EXIT 0 post-fix.

TASK-429 e TASK-430 chiusi nel backlog (Done, DoD spuntate, finalSummary). 429 e' adottato
implicitamente (consumer-side di 424, fix corretto); 430 e' NON-adottato (flag default OFF,
materiale per il prossimo giro di ratifica di maintainer). Tutti i commit pushati (429, 430, H11-fix,
run2bis+VERDICT, campaign-status).

Backlog continuation: TASK-432 (profile-following stamp, high, construction-level come 430) e'
il prossimo naturale MA e' sostanziale (allocazione a due fasi, principio pattern-exhaustion-first
di maintainer). Affidato a sessione fresca con budget di contesto adeguato per impl + DoD; non iniziato
qui per non rischiare errori su un cambio construction-level con contesto depleto. Stale
verify-baseline.sh gates (1376/1800/280 pre-TASK-408) restano deferred cleanup documentato.

## MANDATO NOTTURNO ~20:15 CEST (ratifica + TASK-432)

Ratifica maintainer 20:15 (HEAD 0d8d7e67): **424 (M20 pin-netting) e 425 (M34/M35 100/50) ADOTTATI**
(registrati in CLAUDE.md + ratifica-m12-2026-07-27.md). Verifiche Fedora sui claim laptop:
**429 CONFERMATO** (checkpoint 40 mosse, H17 invariato, netting-excluded 0), **430 flag-OFF
CONFERMATO** (harness verde 75,2%), **watch G11 CHIUSO** (run2bis 47,1 = rumore n=1 di run1).
Day-start 96,3 accettato come residuo construction-level noto (426).

### TASK-432 (profile-following, HIGH, m-12) - CHIUSO, NON-ADOPTED (flag default OFF)

`DayClusteredConstructionPhase.reserveProfileFollowingPlacements`: pre-pass flag-gated
(`-Dplanner.construction.profile-following.enabled`) dentro `stampWeeklyTemplate`, dopo
`unplacedByCourse`/`availableTimeslotsByCourse` e prima del loop cronologico. Per ogni corso
"must-concentrate" (profilo per-data non-uniforme: max-modal>=2, AND teacher single-weekday via
VRP), ordina le date VRP-legali per `expectedHoursCapFor` decrescente (tiebreak cronologico) e
riempie ciascuna fino alla quota attesa (gating hard identico allo stamp: canPlaceLesson H1/H2/H3
+ exceedsDailyCap H7/H11 + expectedHoursCap pseudo-netted), consumando la coda per-course.

**KEY DESIGN - single-weekday filter**: senza il filtro, TUTTI i 21 corsi di G10 competono per
la stessa quota totale-gruppo (aula+lab) sui giovedì 8L e vince l'ordine alfabetico (misurato:
T01 affamata, 0h sui monografici / 65h sull'autunno). L'astrazione profonda sarebbe
subject-kind-aware (labHours via Course.isLab, aulaHours) MA getRoomTags/isLabSubject sono private
nel protetto DataGenerator e isLab e' unset nel fixture di costruzione -> single-weekday proxy
(approssima "la materia che la scuola concentra": caso canonico T01 solo-giovedì via VACATION).
Documentato come proxy nel javadoc (fail-safe: un lab multi-giorno che si concentra cade nel stamp
uniform, non viene forzato male).

**VALIDAZIONE** (real seed, construction-only):
- flag-ON concentra G10/Servizio tecnico e stilistico (T01, 65h) sui **9 giovedì monografici**
  (12-03/10 5L, 12-17 7L, 01-07/14/21 8L, 05-20/27 8L, 06-03 8L = 5+5+7+8x6 = 65h ESATTE) e libera
  i giovedì autunnali: **monographic=65h, other=0h** (baseline flag-OFF: 24h/41h).
- ricorrenza **74,7%** (== flag-OFF; i cluster monografici dic/gen/mag-giu sono weekly-consistenti).
- zero conflitti H1/H3 accettati (popolazione finale). stampSkipConflict 84 (celle template dei
  corsi multi-giorno spostate sui giorni riempiti - atteso, repair tail li ricolloca), giu' da 665
  prima del filtro single-weekday.
- **flag-OFF byte-identical**: stamp 8183/0/321/1538/358, ricorrenza 74,7%, full-day 86,3% ==
  baseline pre-cambio (misurato da solo per escludere la varianza inherente del repair-tail
  HashMap/identity-hashCode nello stesso JVM).

**M21 (verificato moot)**: giorni 8h mono-materia -> M21 (max 2h/materia) penalizzerebbe, MA
"Servizio tecnico e stilistico" e' **LAB** (getRoomTags -> "acconciatura" -> isLab=true in
produzione) e M21 esente i lab -> nessuna penalità in produzione. Il fixture construction-only
non valuta M21 in ogni caso.

**DoD PIENA**:
- /simplify (4 agent): 4 fix applicati + verificati (single-weekday anticipato x efficiency;
  modalKey rimosso -> riuso WeeklyTemplatePlanner.mostFrequentKey resa package-private;
  assertNoTeacherGroupConflicts estratto dai 2 metodi test; javadoc proxy isLab).
- /code-review high (4 angoli correttezza + conventions): 1 finding FIXED (isolamento flag:
  System.clearProperty in clean() belt-and-suspenders col try/finally); M21 REFUTED (lab-exempt);
  2 REFUTED (precondizione documentata mostFrequentKey / singola call-site).
- **full ./scripts/core/test.sh EXIT 0** (833 test, 0 fail, 0 error, 11 skip env-gated, 2:07 min).

Adozione = maintainer al prossimo giro (430-ON + 432 + eventuale M35=100 in un A/B x2). Default OFF ->
non tocca alcun solve finche' non ratificato.

### Coda mandato notturno (pending, autonomo)
(2) batteria knob M35 50-vs-100 via checkpoint (smoke Phase2 600s x2 su base pinned
val419-staged-smoke2); se -Dplanner.m35.weight non reach il test JVM dal runner, documentare e
saltare. (3) PREP (solo piano, non lancio) del ciclo live A/B x2 con 430-ON + 432-ON (+M35=100 se
misurato buono): flags esatti su riga java -jar, attese quantitative con derivazione, ordine di
lettura. Lancio deciso da Fedora/maintainer domattina.

## TASK-433 (AnchorRepair, HIGH, m-12) - CHIUSO, NON-ADOPTED (flag default OFF)

Go di maintainer (~20:55 CEST, "vai su 433 flag-gated come proposto"; 434 domani deliberato).
`AnchorRepair.java`: pass deterministico flag-gated (`-Dplanner.construction.anchor-repair.enabled`)
nel ciclo staged post-HoleRepair / pre-SurplusRebalance (`ScheduleService.runAnchorRepair`). Chiude
i buchi LEADING M18 late-start per import cross-date (stesso-gruppo, unpinned, da un'altra data nel
leading slot) - il sibling REPAIR del TASK-430 (prevenzione+riparazione convergenti sullo stesso
bersaglio slot-0 cross-gruppo; spike misurava 89->16 train2, 82->20 m12).

DESIGN (riuso massimo, sibling di HoleRepair): pass thin che riuse HoleRepair.findImport (gate chain
+ costo A4: conflict-free -> passesPlacementGates -> isDepartureSafe -> VRP-last, off-template prima
di on-template cheapest), HoleRepair.buildCandidatesByGroup (estratto condiviso), HoleRepair.
commitCrossDayMove, index BoxRepair/StudentDayCompaction. Logica AnchorRepair-specifica: targeting
leading-slot (fill minSlot-1 -> minOpen, adjacent-first) + M18-guard SEMPRE ON (riusa SurplusRebalance
.keepsDayAnchored, resa package-private) - senza e' shell game (lo spike OFF: 53 donatori de-ancorati).
Le chiamate findImport di HoleRepair passano m18Guard=false (H17 hard > M18 medium -> de-anchor
donatore permesso per chiudere un gap mid-day) - no-op quando false -> HoleRepair byte-identical.

DEDUP /simplify (finding principale applicato): findAnchorImport deduplicata -> riusa HoleRepair.
findImport con nuovo param m18Guard + HoleRepair.buildCandidatesByGroup estratto condiviso. Cosi' la
gate chain del donatore e' un solo posto, non due.

VALIDATION (real seed, construction-end grid, test anchorRepair_closesLateStartsWithNoDeanchor):
late-start ~34-42 -> 0 (fully closed; il fixture no-pin chiude tutto, in produzione con 2651 pin
sarebbe ~89->16 per saturazione H1 cross-gruppo), donorDaysNewlyLate 0 (M18-guard regge), conflictsAdded
0. HoleRepair byte-identical (m18Guard=false no-op + estrazione behavior-neutral; stamp 8183/0/321/
1538/358). flag-OFF byte-identical.

DoD PIENA: /simplify (dedup findImport+buildCandidatesByGroup; minor skip); /code-review high A+B+C+
conventions = 0 bug reali (2 candidati A+B verificati: SCHOOL_YEAR_START==2026-09-14, stale-pool
falso-positivo ragionato via gate-safety - keepsDayAnchored blocca l'anchor, isDepartureSafe blocca
il mid-day; cross-file tutte pass; convenzioni clean); **full ./scripts/core/test.sh EXIT 0** (835
test, 0 fail, 12 skip env-gated; HoleRepairTest 6/0 + SurplusRebalanceTest 8/0 -> dedup preservata).

Adozione = maintainer al prossimo gate. Il gate-prep (`prossimo-gate-430-432-m35-prep-2026-07-27.md`)
nota che il braccio day-start diventa **430+433** (prevenzione+repair) quando 433 sara' ratificato.
Default OFF -> non tocca alcun solve.

## Stato sessione notturna (~22:57 CEST)
TASK-432 (profile-following) DONE + pushato. TASK-433 (AnchorRepair) DONE + pushato (questo commit).
Entrambi NON-adottati (flag default OFF), materiale per il prossimo gate A/B (430+433 day-start,
432 fill, +M35=100 opzionale). Coda: 434 (G04 stage, autorizzato) domani deliberato; M35 knob
BLOCKED in test-JVM (static-final class-init + test.sh -D rejection) -> terzo braccio del gate.
Gate-prep doc pronto (solo prep, lancio Fedora/maintainer domattina).
