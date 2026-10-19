---
id: TASK-578
title: >-
  Audit VRP cache-stale sui pass deterministici sibling + cura profonda
  (invalidazione o epoch)
status: Done
assignee: []
created_date: '2026-09-02 15:29'
updated_date: '2026-09-03 15:09'
labels:
  - tech-debt
  - cache-stale
  - deterministic-passes
  - vrp
milestone: m-13
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla /simplify del fix VRP di SurplusHarvest (2/09): la cache Lesson.cachedAvailableTimeslots e' popolata al primo uso e MAI invalidata (grep: l'unico assignment e' il populate-on-miss); le statiche che alimentano computeAvailableTimeslots vengono ripubblicate mid-cycle (ScheduleService refreshSolverStatics ~3761/3998, refreshPinStatics ~4045), quindi la cache va STALE e qualunque guard di piazzamento che la legga puo' mettere una lezione in una cella che il range vivo vieta: il solver successivo (clone cache-free) esplode con IllegalStateException assertValueRange. GIA' SUCCESSO in produzione: 2 crash nel ciclo SurplusHarvest del 2/09 (Tecnologia Meccanica 10104 @17/09 11:00, Disegno 10136 @24/09 11:00), curati portando SurplusHarvest su getAvailableTimeslotsFresh().

CENSIMENTO DEI CONSUMATORI DA AUDITARE (grep getAvailableTimeslots() nei pass deterministici, situation al 2/09): HoleRepair:366, SurplusRebalance:407, QuotaFillRepair:531, ExactFinisher:303/329/432/548, EmptyDayFinisher:533 (ratificato DEFAULT ON; il suo -Dedf.trace esisteva proprio per il sospetto stale), PureHoleRepair:300, CompositionHoleCloser:348, BoxRepair:211/254/281, OneOffFinisher:162/383/480, StudentDayCompaction:110/161/203, LabDayConsolidation:321, DeclaredDayPreBooker:542.

LAVORO: (1) per ogni consumatore classificare se legge la VRP come GUARD di piazzamento (esposto al crash) o per altro scopo (es. diagnosi/stampa, non esposto); (2) per i guard esposti: stabilire se la cache puo' essere stale NEL CONTESTO di quel pass (posizione rispetto ai confini di ripubblicazione statics vs momento di popolamento della cache); (3) migrate i guard esposti a getAvailableTimeslotsFresh() (pattern SurplusHarvest: hoist per-entita'-per-call, MAI memoizzare oltre perche' il range guadagna lo slot corrente dopo ogni mossa); (4) valutare la CURA PROFONDA (invalidazione cache o epoch/version bump alla ripubblicazione delle statics): tocca l'hot-path del solver (la cache esiste per il move evaluation), va misurata prima di adottarla. NOTA: in produzione le statiche sono sempre pubblicate, quindi il ramo empty-VRP->unrestricted delle guardie non e' la via d'ingresso del bug.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AUDIT COMPLETO (notte 2/09, agente su tutti i 14 siti censiti + grep fresco): MECCANICA: la cache-first return PRECEDE il branch pinned (una lezione cachescata unpinned mantiene il range unpinned-era anche dopo la promotion); computeAvailableTimeslots legge 8 statiche; i writer sono refreshSolverStatics/refreshPinStatics/promotion/demotion. ESPOSIZIONE: nel ciclo staged i guard sono safe PER INCIDENTE DI LIFECYCLE (nessun republish spanna i siti nel ciclo ordinario), ma la classe crash VIVA e' l'invocazione decoupled (banco/live-state/resume-replay su istanza sopravvissuta attraverso un republish): ESATTAMENTE come i 2 crash di produzione (TASK-577 live-state). VERDETTI: EXPOSED (crash-class vivo in banco): EmptyDayFinisher:533 (DEFAULT ON, il piu' esposto), QuotaFillRepair:531 (ON, 2 fuochi), ExactFinisher:303/329/432 (ON: commette piazzamenti di boxed SENZA current-slot che il grandfather non maschera), HoleRepair:366 + StudentDayCompaction:203 (ON, prima maglia), SurplusRebalance:407 (ON), LabDayConsolidation:321 (ON), + i latent DEFAULT OFF (CompositionHoleCloser:348, PureHoleRepair:300, OneOffFinisher:383/480) e gli in-build SAFE-by-lifecycle (BoxRepair:211/254/281, StudentDayCompaction:110/161, DeclaredDayPreBooker:542, siti costruzione DCCP:806/910/1318/1992, StagedPinPolicy:154 INTENZIONALMENTE blinded, SchoolSchedule:1504 diagnosi). SITI EXTRA trovati: il memo per-COURSE DCCP:1318 (la memoizzazione piu' larga del codice: prima cosa che rompe se uno statics writer entra in costruzione). ORDINE DI MIGRAZIONE: 1 EDF:533, 2 QFR:531, 3 EF:303/329/432, 4 HR:366+SDC:203, 5 SR:407, 6 LDC:321, 7 i flag-gated, 8-9 gli in-build per uniformita', 10 i counting. TUTTI i null-check vacui (legal/range != null) da buttare: i getter non ritornano mai null. DEEP FIX (epoch bump su SchoolSchedule + validita' cache per epoch+pinned-flag): FATTIBILE a costo un volatile-read per call con recompute solo ai republish (~6/ciclo): converte ogni safe-by-accident in safe-by-construction. CONDIZIONE: uno spike TRACKED_FULL_ASSERT di 1h con piazzamento stale sintetico per fissare il canale esatto del assertValueRange (sospetti: ValueRangeManager per-working-solution cache e clone identity: il javadoc avverte che le cache non devono sopravvivere a un clone). La migrazione dei guard e' giustificata indipendentemente dalla risposta dello spike.

MIGRAZIONE ESEGUITA (notte 2/09, Fedora in parallelo al ciclo di conferma): tutti i placement guard migrati a getAvailableTimeslotsFresh() col pattern SurplusHarvest (hoist per-entita'-per-call dove il range non dipende dal landing, mai memoizzare oltre). Dettagli nel commit.

QUARTA VIA CHIUSA (3/09, diagnosi delle 2 assertValueRange residue del ciclo v10): erano OneOffFinisher.chainFreeTheTagRoom (traslazione del blocco sulla griglia del giorno SENZA alcun value-range check: l'unica via di piazzamento fuori migrazione: trovata leggendo il log: entrambe le eccezioni subito dopo il summary OOF; vittime Disegno 10155 e Preparazione all'alternanza 10130, entrambe in blocchi). Guardia per-cell isSlotLegalFresh aggiunta (6ce59070), checkpoint OOF 2/2. DEBITO dichiarato: OOF non ha unit test dedicato (il pin del sito va col test del pass). CONSEGUENZA sullo spike epoch: non restano osservazioni non spiegate: tutte le vie di piazzamento note sono per-cell fresche: l'epoch resta PROSCHOOLSI (safe-by-construction vs safe-by-lifecycle), non cura di un canale vivo: priorità abbassata sotto la portata del pass.
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
