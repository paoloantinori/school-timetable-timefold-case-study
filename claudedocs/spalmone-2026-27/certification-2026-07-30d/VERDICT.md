# VERDICT 30d - solve di CONFERMA: constrained-teacher DEFAULT ON regge da solo

**Run:** 2026-07-30, profilo dev fresco (stop+delete+reseed), import-calendario (866/1993
pinnati, modaFailures 0), pre-assign fino a COMPLETE, solve-staged. Lancio `java -jar` con
**UN SOLO** flag: `-Dplanner.phase-transition-dump.enabled=true`. **Niente** `-D` per
constrained-teacher o slot0-balancing: il punto del run e' verificare che i DEFAULT facciano
da soli la cosa giusta. JAR HEAD `87f18379` (feat(435) constrained-teacher DEFAULT ON,
ricompilato a freddo: il JAR era stale del 29/07). Ciclo ~46min (08:42 solve-staged -> 09:28
stagedStage NULL), B-ROOM dal min 31, hard converge -8422 -> -173 negli ultimi ~11min.

## DOMANDA posta (mandato maintainer): il DEFAULT riproduce i run col flag passato a mano?

Fino a ieri il constrained-teacher (TASK-435) si accendeva solo passandolo a mano sulla riga
`java -jar`. Da stamattina e' DEFAULT ON. Non avevamo mai girato un ciclo completo coi soli
default. Se il default non funzionasse, sarebbe la cosa piu' importante da scoprire oggi.

## RISPOSTA: SI'. Il default funziona e coincide col run col flag, nel rumore.

**Cancello critico verificato PRIMA di lasciar girare (il vero punto del run):**
- **Banner** (`ScheduleService.refreshSolverStatics`): `Construction/repair flags:
  slot0-balancing=false profile-following=false constrained-teacher=true`. constrained-teacher
  risulta **true SENZA averlo passato** (slot0/profile-following restano false, i loro default).
- **T01 231/231 pinnate** (query DB dopo pre-assign): la Phase 0 (constrained-teacher pin,
  TASK-435) e' partita e ha collocato tutto il monte-ore di T01. Identico ai run col flag.
- Liveness confermata (thread "staged-solve" RUNNABLE in BavetConstraintSession per tutto il ciclo).

## Tabella 30d (DEFAULT) vs 30b (flag manuale)

| metrica | 30b (flag manuale) | 30d (DEFAULT) | delta | verso |
|---|---|---|---|---|
| hard | -169 | **-173** | +4 | rumore |
| H0 boxed | 3 | **3** | = | = |
| H29 room-null | 163 | **167** | +4 | rumore (tutte lab capacity-starved, NON-LAB 0) |
| full-day ex-G14 (DailyActivityQuota) | 90,1% | **89,8%** | -0,3pp | rumore |
| deep-deficit (>=2h) | 31 | **28** | -3 | rumore |
| mar-giu share deep | 68% | 68% | = | = |
| ricorrenza UNPINNED | 74,3% | **74,3%** | = | = |
| buchi interni H17 | 0 | **0** | = | = |
| day-start scarto>=3 vs golden | G11 ven (16) | G11 ven (16) | = | = (difetto strutturale noto) |

Contabilita TASK-447 regge: `hard == boxed*2 + room-null*1` = 3*2 + 167 = 173. I 3 boxed NON
sono la catena T01 (il default l'ha collocata tutta): sono saturazione residuale, come 30b.
room-null tutte lab (M-LF ripiego attivo di default), NON-LAB 0 (popolazione H29).

## VERDETTO: CONFERMA RIUSCITA. Adozione default SICURA.

L'unica differenza fra 30d e 30b e' **da dove arriva il flag** constrained-teacher (default del
codice vs `-D` sulla riga di comando): il risultato e' lo stesso, dentro il rumore run-to-run
(hard +4, room-null +4, full-day -0,3pp, deep -3, ricorrenza e buchi identici). Il default fa
da solo la cosa giusta. **Conseguenza operativa:** ogni futuro run puo' usare i soli default
(senza passare il flag), e i confronti sono omogenei. L'adozione del TASK-435 e' verificata
end-to-end sul ciclo completo, non solo sulla pre-assegnazione.

Caveat fedelta': n=1 NON_REPRODUCIBLE, il delta hard/room-null (+4) e' within la varianza
gia' osservata (30b 163 / 447 189 / 29b 167 room-null). Conferma da consolidare col prossimo
run di campagna, ma non c'e' segnale di rottura del default.

## Lascito
- Dump per-fase in `phase-transitions/` (8 phase-transition + 4 boxed, TASK-419).
- Score congelato in due check finali (-173). **newlyViolated=0 verificato**: nessun hard
  oltre i 3 boxed (H0) + 167 room-null (H29), zero H1/H2/H3/H5/H7/H17/ecc. (`hard == boxed*2
  + room-null` assorbe tutto, come 30b).
- KPI catturati col `daily-fill-kpi.py` allineato (DailyActivityQuota, vedi riconciliazione
  metodologia TASK 1): i numeri deep/full-day qui sono AUTOREVOLICI (non i vecchi class_days).
