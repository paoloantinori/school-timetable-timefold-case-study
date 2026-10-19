# HANDOFF 2026-08-02 alba4 (zai). Diagnosi 6 H17 (G11 mercoledi') + A/B EF final 2/2 hard-0

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (ultimo push dopo questo
> handoff). **App UP** (prod, `java -jar` con `-Dplanner.exact-finisher.enabled=true
> -Dplanner.exact-finisher.max-candidates=1000000 -Dplanner.pre-assign.constrained-teacher.source=input`;
> banner `maxCandidates=1000000`). **DB = A/B run2 finale (0hard/-1057977medium/8652soft)** su
> `planner-data-production`. Container `planner-pg` up (db-query.sh e' intermittente: al primo
> tentativo puo' dire "Container not running" anche se e' up; ritenta). Verdetti dai raw. Regole
> standing invariate. **maintainer sveglio/attivo** (ha pushato 479/480; ha diretto il presidia).

## MANDATO (4 punti) - TUTTO CHIUSO

### (1) DIAGNOSI 6 H17 probe-run2. FATTA + CORRETTA + COMMITTATA
- **NON sono il pattern G13** ipotizzato in alba3. Sono **G11, 6 mercoledi' consecutivi
  (11 nov -> 16 dic), hole fisso ore 10** (shape `8-9 _ 11-12-13`), tutte `pinned=false`.
- **Non e' un pattern della scuola**: golden compatto (8-9-10-11-12) su tutte e 6 le date, **0/34
  mercoledi' con buchi** nell'anno. Artefatto del solver.
- **Catena causale verificata (skill causal-chain, link-by-link):**
  1. Costruzione (fase 1): G11 mercoledi' e' **COMPATTO** (T11/T04/T23, 8-9-10-11-12).
  2. LS (fasi 2-3): sostituisce la composizione con {T08 x2, T23, Barucchieri, T06}
     per risolvere altri conflitti hard (H1). Composizione **strutturalmente non compattabile**
     (ricerca esaustiva: nessun arrangement 8-12/9-13; T06 saturo 8-13, @10 bloccato per
     T08+T23).
  3. Hill-climbing (no metaheuristic nel config, change+swap, **zero cartesianProductMoveSelector**:
     il nome del branch e' storico) non attraversa la valle a 2 mosse del cambio composizione.
- **EF PROSCIOLTO meccanicamente** (ipotesi alba3 #3 "EF crea il buco" confutata): EF piazza solo
  boxed in celle conflict-free, la cella @10 e' H1-bloccata per tutti i docenti G11, il cambio
  composizione e' una mossa LS non di EF. Cronologia: costruzione compatta -> buco appare nel
  finale (post-LS).
- **Riproducibile**: run2 e run4 (bad seed) hanno le **identiche 6 celle**; run1/run3 (good seed) 0 buchi.
- Doc: `task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md`.

### (2) Cura o trade-off. FATTO: trade-off dichiarato, cura = nuovo task separato
- **Verdetto: trade-off accettabile per il pacchetto EF, cura = search-side DRAFT-006 (non EF).**
- Numeri: 6 H17 / 12.494 lezioni = 0,048%; 6 / 1.958 giorni-gruppo = 0,31%; 1 gruppo su 14;
  pre-esistenti (run4 @100k, identiche celle, prima del knob EF); ortogonali a EF.
- La cura NON e' una rifinitura EF (EF e' innocente): e' la metaeuristica della LS B-TIMESLOT
  (forager default -> tabu/late-acceptance per attraversare la valle a 2 mosse; move selector gia'
  presenti) o un hole-repair composition-aware. Model corretto (golden 0/34), direttiva primaria:
  search-side, non tocca il modello.
- Task creato: **DRAFT-006** (claude-planned, milestone m-12, ortogonale a TASK-478).

### (3) A/B conferma pacchetto EF (n>=2). FATTO - 2/2 hard-0
- **run1 `ab-ef-final-run1`: 0hard/-1074257medium/8115soft, full-day 88,3%, 0 H17.**
- **run2 `ab-ef-final-run2`: 0hard/-1057977medium/8652soft, full-day 88,7%, 0 H17.**
- Entrambi good seed: **EF inerte** (boxed=0 all'entry, log solo banner, nessun "placed"). Medium/
  full-day puro LS-baseline dentro la banda good-seed (probe-run1 -1047k/87,6%): nessuna regressione
  EF. Target "hard 0 o H17-only residuo stabile" centrato (hard 0).
- Evidenza completa per la decisione: A/B (good seed 2/2) + probe-run2 (bad seed, EF 18/18) +
  discriminatore (deterministico uncapped). **EF @ 1M pronto per l'adozione come chiusura H0
  deterministica.**
- **NESSUN flip effettuato** (regola: senza il si' di maintainer). Pacchetto completo, in attesa.
- Doc: `task478-results/VERDICT-ab-ef-final-2026-08-02.md`.

### (4) Docs/QUADRO/handoff. FATTO
- `QUADRO-2026-08-02.md` aggiornato (TASK-478 con A/B + correzione H17; Colonna 2 EF row; menu
  punto 1 "pacchetto completo, attesa si'").
- Questo handoff + `VERDICT-ab-ef-final-2026-08-02.md` + `diagnosi-h17-g11-mercoledi-2026-08-02.md`.

## VERDETTO (dai raw)
1. **Diagnosi H17 corretta: G11 mercoledi' ore 10, artefatto LS (composizione non compattabile),
   EF prosciolto.** Non G13. Cura search-side DRAFT-006, ortogonale a EF.
2. **A/B EF @ 1M: 2/2 hard-0, stabili, EF inerte su good seed.** Pacchetto adozione completo.
3. **Resta flag OFF default, nessun flip.** Il flip (EF ON default + knob 1M, sblocca m-13) e'
   gated al si' di maintainer. Per TASK-480: EF chiude la coda boxed da solo -> multi-start cintura+
   bretelle per l'H0; l'H17 residuo (-6hard bad seed) e' ortogonale, cura DRAFT-006.

## STATO RUN / APP / INFRA
- **App UP**: `java -jar` ExactFinisher ON + max-candidates=1000000 + source=input. Log
  `/tmp/planner-prod.log`. Banner `maxCandidates=1000000`.
- **DB = A/B run2 finale (0hard)** su `planner-data-production`. Per run nuovo: reset +
  import-calendario + pre-assign-reset + pre-assign + solve-staged (no fresh reseed; LabRotation=0
  norma campaign-side; pre-assign COMPLETE in 0ms con 0 candidati e' normale).
- Container `planner-pg` up. db-query.sh intermittente (ritenta al primo "not running").
- Jar `target/quarkus-app/` = HEAD 15b2e0ec (col test discriminatore; niente codice nuovo in alba4,
  solo docs). caffeinate attivo (m12 wrapper).

## PUNTI DI RIPRESA
- Questo handoff + `task478-results/VERDICT-ab-ef-final-2026-08-02.md` (A/B) +
  `task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md` (H17) + `QUADRO-2026-08-02.md`.
- Capture: `task478-results/ab-ef-final-run{1,2}/` (A/B, hard-0) + `ab-ef-probe-1m-run{1,2}/`
  (probe, good + bad seed).
- Backlog: TASK-478 (commento A/B), **DRAFT-006** (cura H17, search-side, ortogonale).
- Regole standing: verdetti dai raw, commit+push, tempi assoluti, -D su `java -jar`
  (PLANNER_JAVA_OPTS), rebuild prima di run misurati, handoff+/clear a ~450k, mai fermo, DoD
  obbligatoria su codice solver adottato, **flag ExactFinisher resta OFF default finche' maintainer
  non dice si'**.
