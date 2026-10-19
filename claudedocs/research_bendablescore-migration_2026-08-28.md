# Research Report: BendableScore migration 2.3.0 (ramo B quasi-hard)

**Date**: 2026-08-28
**Depth**: exhaustive (fonti primarie: sorgente 2.3.0 dal sources-jar locale + docs ufficiali; letteratura secondaria)
**Confidence**: HIGH su semantica/API/serializzazione (verificate sul sorgente del tag esatto in ~/.m2)

## Executive Summary

La forma 4-livelli `2 hard + 2 soft` e' SEMANTICAMENTE CORRETTA e supportata: il confronto
lessicografico di BendableScore confronta TUTTI gli hard levels in ordine POI tutti i soft in
ordine, quindi un hard[1] (quasi) sempre-zero a milestone-1 lascia l'ordinamento identico a
HardMediumSoftScore (docs ufficiali confermano: "A BendableScore with one hard level and two
soft levels is equivalent to a HardMediumSoftScore"). La dichiarazione richiede
`@PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)`. La serializzazione
JSON e' coperta da `timefold-solver-quarkus-jackson` (gia' nel pom alla riga 70, con
BendableScoreJacksonSerializer/Deserializer nel jar 2.3.0 locale): nessun lavoro nuovo per
REST/export. Il formato stringa cambia in `[-123/-456]hard/[-789/-012]soft`: TUTTI i parser
esterni del vecchio formato vanno aggiornati (checkpoint reader, script KPI, api.sh score).

## Findings

### 1. Semantica del confronto (HIGH, sorgente `BendableScore.java:277`)
`compareTo` itera gli hardScores in ordine poi i softScores in ordine, long-compare al primo
diverso. Con hard[1]==0 costante in milestone-1, l'ordinamento e' ESATTAMENTE quello di
HardMediumSoftScore. `validateCompatible` richiede stessa shape (2+2) fra score confrontati:
tutte le inizializzazioni devono usare la stessa shape (zero(2,2), ofHard(2,2,...),
ofSoft(2,2,...)): il subagent ha gia' ricevuto l'emendamento in questo senso.

### 2. Dichiarazione (HIGH, docs 2.x "Constraints and Score: Overview" sez 2.4)
```java
@PlanningScore(bendableHardLevelsSize = 2, bendableSoftLevelsSize = 2)
private BendableScore score;
```
"Il numero di livelli va fissato a compile time". Zero-init: `BendableScore.zero(2, 2)`.

### 3. Costruzione vincoli (HIGH, sorgente BiConstraintStream)
`penalize(Score_ constraintWeight)` generico: si passa `BendableScore.ofHard(2, 2, 0, peso)`
per hard puro (indice 0!), `BendableScore.ofHard(2, 2, 1, peso)` per il quasi (milestone-2),
`ofSoft(2, 2, 0, peso)` per il medium attuale, `ofSoft(2, 2, 1, ...)` per il soft attuale.
ATTENZIONE (trappola segnalata anche dai docs): "un constraint match su soft0 outweighs un
milione di match su soft1": nel nostro mapping e' voluto (medium > soft, come oggi).

### 4. Serializzazione (HIGH, jar verificati)
- Jackson: `timefold-solver-quarkus-jackson` 2.3.0 (pom riga 70) contiene
  BendableScoreJacksonSerializer/Deserializer + ConstraintWeightOverridesSerializer: REST e
  export JSON funzionano senza nuovo codice. NOTA pom righe 100-115: timefold-solver-jackson
  (Jackson 3.x) e' ESCLUSO dal classpath di test del benchmark per conflitto Jackson 2/3:
  l'esclusione resta valida anche per Bendable (il quarkus-jackson usa com.fasterxml).
- Stringa: `[-123/-456]hard/[-789/-012]soft` (toString, sorgente riga 298);
  `parseScore` accetta lo stesso formato. I checkpoint esistenti col vecchio formato
  `-14hard/...` NON si parseranno con BendableScore.parseScore: serve migrazione dei file o
  parse compatibile (scelta da demandare al milestone: i checkpoint pre-migrazione restano
  leggibili solo dal branch pre-B).

### 5. Rischi censiti
1. **Parser esterni del vecchio formato** (api.sh score display, scripts KPI, capture-run.sh,
   Registro/scripts python che leggono score.txt): TUTTI da aggiornare al formato bracket.
   Era gia' la nota "milestone 2" della spec del subagent: qui confermata con sintassi esatta.
2. **ConstraintWeightOverrides<HardMediumSoftScore>** nel nostro SchoolSchedule (riga 1558):
   passa a `ConstraintWeightOverrides<BendableScore>`: i pesi zero/damped del room-tier
   (RoomTierOverrides) vanno riscritti con la nuova shape (ofSoft/ofHard bendable): il subagent
   e' istruito a segnalarlo se incerto.
3. **Shape fissa per validateCompatible**: ogni sito che crea score (INITIAL, oracle asserts,
   PortfolioPickBest, MultiStartRunner) deve usare (2,2): una shape mista esplode a runtime
   con IllegalArgumentException, non silenziosamente.
4. **Suite come guardia di parita'**: i pin numerici dei test restano validi (i valori non
   cambiano, cambia il tipo): se un pin fallisce, e' un bug di mappatura livello, non un drift.

### 6. Letteratura (bassa priorita', nota)
I docs stessi sconsigliano un livello per vincolo ("extremely rare"): il nostro e' il caso
giustificato (2 famiglie hard con semantica diversa: puro vs quasi). Strategic oscillation
(Glover) resta il riferimento teorico gia' citato nel report room-tier.

## Confidence Assessment
HIGH su tutto cio' che e' letto dal sorgente 2.3.0 e dai jar locali (stesso artifact che
compila il progetto). MEDIUM sull'assenza di sorprese in benchmark XML con score bendable
(non verificato: nessun solverBenchmarkConfig con bendable nel repo, il benchmark e' test-scoped
e non parte della suite di parita').

## Sources
1. `~/.m2/.../timefold-solver-core-2.3.0-sources.jar` → BendableScore.java (compareTo:277,
   toString:298, parseScore:25, zero:54, ofHard/ofSoft:66/80) e BendableScoreDefinition.java
2. `~/.m2/.../timefold-solver-quarkus-jackson-2.3.0.jar` → BendableScoreJackson{Serializer,Deserializer}
3. https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/overview (sez 2.4 BendableScore)
4. pom.xml righe 70 e 100-115 (wiring quarkus-jackson + esclusione jackson-3.x)
