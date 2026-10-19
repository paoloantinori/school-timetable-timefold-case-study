# HANDOFF 2026-08-01 sera3 (zai) - RUN5 n=2 NON CONFERMA HARD-0 (varianza LS) + PERIMETRO CHIUSO + FLIP READY-WHEN-NEEDED RINFORZATO

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` `240be12e` (2 commit pushati:
> `78b1d134` run5+census, `240be12e` campaign analysis). App UP su profilo `production` via
> `java -jar` con `-Dplanner.pre-assign.constrained-teacher.source=input` (PID 95140, JAR con cura
> 04900742). **DB corrente = run5 (hard -38)**. Default solver: golden; source=input opt-in.
> Verdetti dai raw. Regole standing invariate.

## COSA E' STATO FATTO (mandato serale 5 punti CHIUSI)

1. **TASK-452 census annotato (decisione Fedora)** - `task452-deducibility-census-2026-08-01.md`.
   Flip m-13 = **READY-WHEN-NEEED** (supersede il "GATED A PARITA'" di TASK-452 #5): non gated su
   hard-0, non si fa oggi per scelta (golden strictly better finche' esiste), adozione quando
   servira' (post-golden). **Run5 n=2 qualifica il claim** (vedi punto 2): feasible ma non
   robustamente hard-0.

2. **RUN5 n=2 (source=input post-cura) - HARD-0 NON CONFERMATO**. Protocollo IDENTICO a run4
   (reset+import-calendario 2859 pin + pre-assign source=input +23 T01 = 2882 authoritative,
   **byte-identico a run4**; fresh-reseed non necessario, PG17). Risultato: **-38hard** (run4 era 0).
   - Decomposizione (analysis.json, multi-sorgente): **H0 unassigned -32 (16 match)** + **H17 -6
     (6 match)** = -38. DB unassigned=16, score.txt concordano.
   - Le 16 unassigned NON sono il dominio della cura: **G03/T08/Matematica (11) + 2
     FA/T19/Barberia (5)**. La cura (G10 stage-aware) HA TENUTO, G10 collocato. Residuo =
     varianza LS generica su capacita' stretta.
   - B-TIMESLOT #1 atterrata box=16 (run4 box=0) -> B-TIMESLOT-2 con demozioni (run4 non la fece)
     -> residuo ACCEPTED after 2 round (max) -> B-ROOM 603s -> -38hard.
   - **Paradosso**: run5 full-day 89,5% (MEGLIO di run4 87,9%) ma hard -38: la LS ha scambiato
     hard per medium (deep deficit 12 vs 22, riempendo meglio a costo di 16 unassigned).
   - Doc: `task452-results/verify-source-input-run5/VERDICT-2026-08-01.md`.

3. **TASK-476 decomposizione gap full-day 87,9 vs 90,2** (run4 vs paired-golden) -
   `task476-input-profile-diagnosis-run3-2026-08-01.md` sez. "Decomposizione". **CORREGGE
   l'attribuzione stale**: FC-venerdi' e' school-gated ma **contributo 0 al gap** (golden e run4
   pareggiano 0/33 su G04 Fri; G04 run4 57% > golden 55%). Il gap reale e' **G10 (+26) + 4
   FD (+25), interamente AGGRESSIBLE** (golden riempie al 85-100% li'). Contingenza g_only=96 /
   r_only=51 / neither=140, net=45. (Conferma golden-oracle: ab-cps-on-run1 source=golden ha lo
   STESSO deficit FC 56/65%.) Correzione minore docente (T05+T24 venerdi VACATION, non
   T04 che non e' G04) applicata.

4. **TASK-472 perimetro H-vs-M completato** (5 regole, golden-first) -
   `task472-results/perimetro-residuo-classificazione-2026-08-01.md` + script
   `scripts/analyze/perimetro_residuo_report.py` (nuovo, ripetibile+idempotente) + report
   `perimetro-residuo-golden-report.txt`. Verdetti: R1 termo prime-4h **MEDIUM** (78 viol), R2
   Sicurezza 1a/2gg **NOT-MODEL** (4, golden fa 1h/sett mai 2gg), R3 T21 mid-day
   **NOT-MODEL** (31, golden lo mette allo slot0), R4 M24/M25 **MEDIUM HOLDS** (43 = 16+27,
   byte-exact CLAUDE.md), R5 M29 **MEDIUM HOLDS** (27/38=71%). **Nessun HARD proposto** (tutte
   violano il golden). R2/R3 = notifiche referente (la regola contraddice la pratica).

5. **REFRESH HTML + serie KPI da ab-cps-on-run1** (riferimento ratificato, nessun solve nuovo) -
   `confronto-ab-cps-on-run1.html` rigenerato (byte-identical tranne reorder cosmetico subject-week)
   + riga `serie-storica-kpi-2026-07-30.md` "Run del 01/08" (0hard, full-day 90,8%, ricorrenza
   75,6%, day-start 98,1%, buchi 0, medium -963616).

## VERDETTO SERALE (dai raw)

1. **Il claim "input path feasible hard-0" e' QUALIFICATO, non confermato.** run4=0, run5=-38.
   n=2 split. La strada e' feasible (run4) ma la LS non la raggiunge in modo affidabile: strand
   ~16 lezioni su capacita' stretta (Math T08 / Barberia T19). NON e' la cura (G10 ok).
2. **Il modello source=input e' corretto, il residuo e' ricerca.** La cura ha rimosso il problema
   sistematico (stage-blind). cio' che resta e' varianza LS su tight-capacity: leva = staged repair
   / BoxRepair piu' risolutivo (non il profilo). Direttiva "correttezza logica vs costo" rispettata.
3. **Fedora "golden strictly better" RINFORZATO.** Non solo full-day (90,2 vs 89,5/87,9) e medium,
   ma anche robustezza hard (golden hard-0 affidabile, input con varianza 0/-38). Flip resta
   ready-when-needed; la ricerca renda hard-0 affidabile prima del drop-in.

## STATO RUN / APP / INFRA
- App PID 95140 `java -jar` con source=input. **DB = run5 (hard -38)**. Per run nuovo: reset +
  re-import + pre-assign + solve-staged (fresh-reseed non necessario, PG17; reset = fresh-reseed
  per seed deterministico, confermato operativo: pre-solve run5 byte-identico a run4).
- `api.sh wait <sec>` e' staged-aware (esce su stagedStage null); il timeout e' **posizionale**
  (`api.sh wait 590`), NON `-t` (che e' il timeout di request curl). Default 300s.
- `api.sh wait | grep` in background auto-backgrounda al timeout Bash: evitare il pipe, usare
  `api.sh wait <sec>` senza pipe o poll manuali a finestre.

## PROSSIMI PASSI (lavoro aperto, non bloccato)
1. **n=3 (run6) opzionale** per stabilire se 0 o -38 e' il landing tipico del path input. Non
   bloccante (il verdetto n=2 "feasible ma varianza" regge); affinirebbe la magnitudo. Se -38 e'
   tipico, la leva prioritaria diventa staged repair su tight-capacity.
2. **TASK-476 leva aggressibile**: il gap full-day e' G10 + G12 (entrambi gruppi di T01,
   costo della redistribuzione interleaved della cura). Rifinire il profilo interleaved di T01
   per ridurre il deficit su G10 Wed/Thu e G12 Tue/Thu (search/profilo, non modello).
3. **TASK-477 G14** (R10, referente Q19): rifiniture (a) escludere coppie G14 dall'esenzione
   H2 co-presenza 464, (b) steering stanza G14->R10. Dopo decisione maintainer + A/B.
4. **TASK-475** (doppio R16 T20): attendere referente (A1/A1+A2/A3).
5. **Notifiche referente (TASK-472)**: R2 Sicurezza 1a/2gg (golden fa 1h/sett, la regola contraddice),
   R3 T21 mid-day (golden lo mette allo slot0, opposto di "mezzo"). Q22 T05-venerdi gia'
   noto. R1 termo: chiarire "08-12 tassativo" (78 viol) vs "8-13 block" (20 viol).
6. **Decisione flip m-13**: resta di maintainer. hard-0 n=2 split (0/-38); golden strictly better anche
   su robustezza. Dichiarazione solo, nessuna adozione.

## DoD - NOTA ONESTA
Il nuovo script `scripts/analyze/perimetro_residuo_report.py` (TASK-472) e' read-only (analisi
golden, fuori dal path solver), verificato FUNZIONALMENTE (run idempotente, EXIT 0, riproducibile,
md5-identico cross-run). La DoD PIENA (`/simplify` + `/code-review high` + `test.sh`) NON e' stata
eseguita su di esso: script read-only di analisi, non codice solver/produzione. Se maintainer lo vuole
hardened nel repo permanente, e' un follow-up. Nessun altro codice toccato in questa sessione (tutto
il resto e' doc/KPI/capture).

## PUNTI DI RIPRESA
- Questo handoff + `task452-results/verify-source-input-run5/VERDICT-2026-08-01.md` (run5 raw +
  verdetto n=2) + `task452-deducibility-census-2026-08-01.md` (decisione Fedora + n=2).
- TASK-476: `task476-input-profile-diagnosis-run3-2026-08-01.md` sez. "Decomposizione".
- TASK-472: `task472-results/perimetro-residuo-classificazione-2026-08-01.md` + script
  `scripts/analyze/perimetro_residuo_report.py`.
- Commit: `78b1d134` (run5+census), `240be12e` (campaign), entrambi pushati.
- Regole standing: verdetti dai raw, commit+push a ogni chiusura, tempi assoluti, -D su java -jar
  (PLANNER_JAVA_OPTS), handoff+/clear a ~450k, poll a finestre (no background pipe), DoD obbligatoria
  su codice solver.
