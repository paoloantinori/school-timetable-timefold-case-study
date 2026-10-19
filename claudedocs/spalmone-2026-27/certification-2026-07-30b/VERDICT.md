# CERTIFICAZIONE 2026-07-30b (run 30b) - TASK-456 aule, terzo run CONSTRAINED-TEACHER ON

Cattura del 2026-07-30 (notte). Terzo run della tripletta TASK-456, lanciato con
`-Dplanner.pre-assign.constrained-teacher.enabled=true` + `-Dplanner.phase-transition-dump.enabled=true`
sulla riga `java -jar` (PLANNER_JAVA_OPTS, NON MAVEN_OPTS). E' il **confronto vero** col riferimento
447: stesso flag acceso, quindi schedule-front CONFRONTABILE (a differenza di 29b/30a che giravano
 coi default e NON erano confrontabili sul fronte orari).

## VERDETTO: TASK-456 (pacchetto aule) CERTIFICATO SUI DUE FRONTI

**1. Fronte ORARI (il confronto nuovo, apples-to-apples vs 447).** Entrambi i run hanno
constrained-teacher ON (231/231 ore di T01 PINNATE in entrambi, verificate sul DB). 30b
(= pacchetto aule TASK-456 + constrained-teacher) eguaglia o batte 447 (= riferimento, constrained-teacher)
su tutti i KPI portanti:

| metrica | 447 (rif, constr-teacher ON) | 30b (constr-teacher + TASK-456 aule) | delta |
|---|---|---|---|
| hard | -195 | **-169** | migliore |
| H0 boxed | 3 | 3 | = |
| H29 room-null | 189 | **163** | -26 (M-LF ripiego) |
| full-day ex-G14 | 87,0% | **87,5%** | +0,5 |
| resemblance unpinned | 74,4% | 74,3% | = (0,1) |
| T01 pinnate | 231/231 | 231/231 | = |

Il pacchetto aule NON peggiora il fronte orari: a parita' di pre-assegnazione, 30b riduce i
room-null di 26 (il ripiego M-LF manda le ordinarie nei lab come ultima risorsa) e migliora
leggermente il full-day. La differenza boxed (3 vs 3) e' la stessa saturazione residuale
strutturale, non varianza.

**2. Fronte AULE (invariante TASK-456, stesso codice del 29b, riconfermata su 30b).**
- **M-LF attivo**: 112 match (-112k medium), co-firma con S10 (112). M37: 326.
- **R18 cap-0 = 0 assegnazioni** (esclusione `Room.isAssignable`, regola generale
  non hardcoded).
- **room-null NON-LAB = 0** (popolazione H29, non esente: i 163 room-null sono TUTTI lab
  `islab=true`, capacity-starved). Il ripiego funziona: ogni lezione ordinaria ha un'aula.

## Score e breakdown

```
score: -169hard/-1069120medium/16104soft
```

| Constraint | matches | hard |
|---|---|---|
| H29 - Assigned lesson must have a room | 163 | -163 |
| H0 - Unassigned lesson (boxed) | 3 | -6 |
| **totale hard** | | **-169** |

Contabilita TASK-447: `hard == boxed*2 + room-null*1` = 3*2 + 163 = 6 + 163 = 169.

## I 3 boxed

Tutti e 3 sono `G06 / Barberia / T19` (verificato sul DB). E' un residuo strutturale TINY e
DIVERSO dalla catena del 29b/30a (`G06 / Servizio tecnico e stilistico / T01`, 35 boxed):
con constrained-teacher ON il corso di T01 e' pinnato (60h di G06 collocate), quindi quella
catena sparisce. Restano 3 lezioni di Barberia/T19 non collocabili per saturazione residua.
Stesso COUNT del riferimento 447 (3 boxed): saturazione strutturale, non varianza.

## KPI notabili

- **Ricorrenza TOTALE 76,4%** (unpinned 74,3%, pinned 80,3%). Stabile vs 29b/30a/447.
- **Buchi interni: 0**. (G14 esente nel motore.)
- **Day-start**: scarto `G11 venerdi run 15 | golden 0`. Il difetto late-start di G11 in
  30b e' tornato sul VENERDI (15 settimane), mentre in 29b/30a era sul MERCOLEDI (14): il
  giorno-settimana del difetto NON e' stabile tra run, ma il meccanismo e' lo stesso (vedi
  TASK-450: slide 8->9 nato in P2 LS-Compactness che chiude il buco H17 lasciato dalla
  costruzione). 30b ha i suoi dump per-fase propri in `phase-transitions/` (flag dump ON).
- **full-day 87,5% ex-G14** (migliore del golden-gap storico; 447 87,0%).

## Osservazione APERTA: la FORMA del deficit peggiora (non spiegare di fretta)

A parita' di script (447 ricalcolato col `daily-fill-kpi.py` attuale, non il suo kpi-fill
stale del 29/07), il confronto mele-con-mele della forma del deficit:

| | giorni sotto quota | frazione a -1h | giorni profondi (>=2h) |
|---|---|---|---|
| golden | - | 67% | 49 |
| 447 (rif, constr-teacher ON) | 180 | 60% | 72 |
| 30b (constr-teacher + TASK-456) | 173 | 55% | 77 |

30b ha MENO giorni in deficit (173 vs 180) ma sono PIU' PROFONDI (55% a -1h contro 60%, e 77
profondi contro 72; entrambi peggiori del golden su profondita'). Cioe' il pacchetto aule
(con l'interazione constrained-teacher) riduce il NUMERO di giorni sotto quota ma sposta il
deficit residuo verso giornate piu' bucate. Numeri verificati meccanicamente: 30b dal suo
`kpi-fill.txt`, 447 ricalcolato dal suo `lessons.txt` con lo script attuale (il suo capture
kpi-fill e' di una versione di script precedente e non riporta la riga FORMA).

OSSERVAZIONE APERTA, non spiegata: non c'e' ancora un meccanismo. E' un candidato per TASK-457
(se la deriva dei tier la causa) o per un'indagine sua. Da tenere distinta dal verdetto aule,
che regge (la popolazione room-tier e' indipendente dalla forma del deficit, che e' timeslot-tier).

## Nota sul flag (RUN parameter, NON adozione)

`-Dplanner.pre-assign.constrained-teacher.enabled=true` e' un **parametro di run**, non un cambio
di default: il default resta OFF (TASK-435 mai ratificato). Questo run lo attiva sulla riga di
comando per produrre il confronto pulito col 447. La **decisione di ratificare il flag e' di
maintainer** (girata coi numeri del triangolo G06/T01: slack -30h senza, +5h con, vedi
`task459-slack-triangolo-g06-t01-2026-07-30.md`): NON va aspettata, e questo run NON adotta
nulla.

## Riferimenti

- Confronto diretto: `claudedocs/spalmone-2026-27/task447-results/cert/` (riferimento 447).
- Non-confrontabilita' dei run default: `task459-runs-non-confrontabili-2026-07-30.md`.
- Verdetto aule 29b (stessa invariante, popolazione sua): `certification-2026-07-29b/VERDICT.md`.
- n=2 default (29b/30a): `certification-2026-07-29b/` + `certification-2026-07-30a/`.
