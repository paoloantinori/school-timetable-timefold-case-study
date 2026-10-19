# HANDOFF 2026-08-01 sera (zai) - hard-0 DIAGNOSTICATO + M-CPS RATIFICATO DEFAULT ON + G14 tracciato

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `fa981ce7` (3 commit
> pushati: `7fd3cafd` diagnosi hard-0, `abbd7f91` ratifica M-CPS + Q19 G14 CLAUDE.md,
> `fa981ce7` G14 TASK-477). Tutti e 3 i mandati della sessione CHIUSI e pushati. Default
> solver: golden; source=input opt-in. Verdetti dai raw. Regole standing invariate.

## COSA E' STATO FATTO (3 commit pushati)

1. **DIAGNOSI HARD-0 del gate m-13 FATTA (read-only, no run, no code).** `7fd3cafd`.
   Perche' run3 source=input lascia 3 ore di T01/G10/Servizio tecnico senza cella?
   Verdetto CONFIRMED end-to-end: **deficit ARITMETICO a livello di PROFILO**, NON di ricerca.
   - Catena causale 7-link grounded: `buildInputDerivedProfile` (TASK-452) e' **stage-blind**
     (legge openSlots dal GroupCalendarRule SETTIMANALE [0,7]=8, ignora lo stage per-data) ->
     brucia 49h del budget di G10 sui suoi stessi giovedi di stage (Mar 4-Apr 22, 0 slot
     reali). Combinato col greedy **alpha-ordinato** (G02 + G06 mangiano i primi 20 giovedi
     Sep-Feb), G10 inizia il turno a Feb 18 e fince la finestra dentro il proprio stage.
   - Pin-loop piazza solo 20h (Feb 18/25/Apr 1, match esatto run3); 45h residuali. La LS satura
     T01 a **228h = 28x8 + 4** sulle 29 celle non-stage raggiungibili (2h add-on sui giovedi
     1/G06), colloca 42h, le **3 ore finali non hanno cella** (non-stage = T01 H7-pieno;
     stage = G10 off-grid H25/H26; Giu 10 = G10 no-session). -> hard -6.
   - NON e' search: la LS ha collocato 62/65 saturando. Feasibility: il golden prova che una
     distribuzione fattibile ESISTE (35 giovedi interleaved, G10 evita il suo stage Dec-Giu).
   - Doc: `task452-results/hard0-diagnosis-g10-t01-stage-blind-2026-08-01.md`. TASK-452
     commento #6, TASK-476 AC#2 chiuso.

2. **M-CPS (TASK-469) RATIFICATO DEFAULT ON (maintainer ha confermato l'adozione).** `abbd7f91`.
   - `SchoolSchedule.isCurriculumPostStageEnabled()` flippato a opt-out (`null/absent -> true`,
     solo `-Dplanner.constraints.curriculum-poststage.enabled=false` disattiva), identico a
     isCoPresenceEnabled/isLabDayConsolidationEnabled. Banner pre-esistente in
     `ScheduleService.refreshSolverStatics` (linea 2075) ora mostra enabled=true di default.
   - Evidenza A/B ab-cps-on-run1: hard 0, curriculum 12/7/24->9/6/21 (G10 verso target, G08
     centra 6, G11 cautela), costo zero (full-day +0,4pp, ricorrenza flat, holes 0, medium
     neutro). CLAUDE.md: riga M-CPS nella tabella Medium + blocco RATIFICA TASK-469.
   - DoD PIENA: /simplify clean (diff meccanico mirror di 5 ratifiche); /code-review high
     (skeptic Opus) 4/4 claim CONFIRMED + 2 finding non-bloccanti FIXED (OBS-1 golden guard M-CPS
     medium=-123 matchCount=3 in GoldenSolutionRegressionTest, verificato verde col banner
     enabled=true stage-end 8 gruppi; OBS-2 frontend docs.json en/it "prototype off"->"ratified
     on"); test.sh **947/0/0/16 EXIT 0**; ui-build EXIT 0.
   - TASK-469 chiuso (Done).

3. **G14->R10 (referente Q19) TRACCIATO.** `fa981ce7` (TASK-477 nuovo).
   - referente Q19 (01/08): "G14 andra' in R10" (corregge la nostra inferenza giorni-L
     "ospite nel lab"). Occupancy invariata (de-tag gia' in subject_overrides.csv). Riga Q19
     CLAUDE.md corretta (commit abbd7f91). Doc referente Q19 gia' aggiornato dal pull `1bb76260`.
   - Due rifiniture candidate TRACCIATE in TASK-477 (NON implementate, gated maintainer): (a)
     escludere coppie G14 dall'esenzione H2 co-presenza (464), per G14 non serve piu'; (b)
     steering stanza G14 -> R10 (preferenza, da valutare costo/beneficio).

## STATO RUN / APP
- App UP su profilo `production` (java -jar). La soluzione in DB e' il run 469 PRE-flip
  (source=golden, M-CPS era ON col flag esplicito; ora M-CPS e' ON di default). Per un run
  post-ratifica: rebuild + fresh cycle (M-CPS ON senza flag esplicito).
- Per leggere run3 source=input (i 3 unassigned): capture `task452-results/verify-source-input-run3/`.
- DB profilo production attivo (planner-pg up). codice ricostruito col flag flip (commit abbd7f91).

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **Cura hard-0 (FOCUS m-13, gated maintainer).** La diagnosi e' la AC del gate (TASK-452 #5). Cura
   PROPOSTA in 2 stadi (perimetro TASK-435/452, input-derived, no golden): Stadio 1
   stage-awareness per-data in `buildInputDerivedProfile` (~20 righe + plumbing mappa statica;
   ferma il budget-burning ma NON chiude hard-0 da solo per composizione G10 vs G12 sui
   tardivi Apr-Giu); Stadio 2 assegnazione bilanciata/interleaved (~40-60 righe, cura vera,
   non-banale, rischia di non tenere in LS). Nessuno dei due e' piccolo + coperto dal test di
   equivalenza esistente -> prototipare SOLO dopo SI maintainer + run source=input (gate = hard 0).
2. **TASK-477 G14**: implementare (a)/(b) dopo decisione maintainer + A/B.
3. **TASK-475** (doppio R16 T20): attendere risposta referente (A1/A1+A2/A3).
4. **Q29 T05-venerdi**: attendere risposta referente (vincolo reale o preferenza?).

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/hard0-diagnosis-g10-t01-stage-blind-2026-08-01.md`
  (hard-0 catena causale + cura proposta) + `QUADRO-2026-07-30.md` sezione SERA 01/08.
- M-CPS: `task469-results/ab-cps-on-run1/VERDICT-2026-08-01.md` + commit abbd7f91 + TASK-469
  finalSummary.
- G14: TASK-477 + doc referente Q19 (chiusa) + riga CLAUDE.md (ratifica 463+464).
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS, non MAVEN_OPTS) + rebuild prima di run misurati (lezione JAR-stale),
  handoff+/clear a ~450k, mai fermo, DoD obbligatoria su codice (questa sessione: codice toccato
  sulla ratifica M-CPS, DoD piena fatta).
