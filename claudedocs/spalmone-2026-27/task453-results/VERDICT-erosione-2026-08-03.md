# VERDICT TASK-453 (WORK 2 erosione). Construction-vs-finale, 2026-08-03

> Risponde alla domanda WORK 2: quanta cella settimanale il template costruito PERDE tra la
> costruzione e il finale, composition-controlled (MOBILE = unpinned a entrambi i cut)? Tooling
> consegnato (lesson-identity-erosion.py + PhaseTransitionMarker.writeTransitionDumpWithIds,
> commit f0448e9e); questo run PRODUCE i numeri. RUN LIVE con GATE maintainer (via esplicita).

## Setup e riproducibilita'

UN solve staged con `-Dplanner.phase-transition-dump.enabled=true` sulla riga `java -jar`
(PLANNER_JAVA_OPTS, NON MAVEN_OPTS: CLAUDE.md documenta che i -D su MAVEN_OPTS restano sul JVM
Maven e non raggiungono l'app forked; EARLY-VERIFY ha confermato dump files entro 120s = flag
raggiunto). Profilo `multistart` riusato (source=input, pre-assign pins 3090). Multi-start ON
(good basin -> 1 tentativo -> UN ciclo staged pulito). Score finale **0hard/-1.032.457medium**,
1 tentativo (mtc=false), pin autoritativi conservati 3090->3090, environment NON_REPRODUCIBLE.
Wall 1717s.

Script: `scripts/benchmark/task453-phase-dump-erosion.sh`. Raw: `task453-results/` (erosion-verdict,
dumps/, score, run.log). Riproduci: lo script (pulisce target/phase-transitions, boot flagged,
early-verify, solve, erosion script).

## 4 phase-transition cut (construction -> finale)

PhaseTransitionMarker dumpa allo stato ENTRANTE in ogni fase (= dopo la fase precedente). Cut
rilevati (4, phase-2..5):
- phase-2 (entra LS-Compactness) = **post-construction CH** = il template costruito (FROM)
- phase-3 (entra Compaction)
- phase-4 (entra CH-room-assignment)
- phase-5 (entra LS-room-refinement) = **~finale** (TO; phase-4->phase-5 = 0 cell-changes, e
  room-refinement muove stanze non celle, quindi phase-5 ≈ finale per le celle)

## Headline: cell-retention sul MOBILE set

MOBILE = lezioni UNPINNED a ENTRAMBI i cut (sempre libere, il solver puo' spostarle ovunque; la
misura pulita di erosione LS, isolata dal composition-shift). 1752 lezioni (di 10445 piazzate a
entrambi i cut).

**cell-retention MOBILE (construction -> finale): 291/1752 = 16.6% kept, 1461 moved (83.4% moved).**

La LS sposta l'83.4% delle celle delle lezioni mai pinnate. Composition-shift NULLO nel verso
forward (0 lasciano il mobile set = nessuna autoritativa/promoted viene pinnata tra i cut);
7596 ENTRANO nel mobile set (demoted/unpinned dal RELEASE tra costruzione e finale, flusso reverse).

## Dove si muovono le celle (inter-phase, sempre-mobile 1744)

| cut | cell-changes su always-mobile |
|-----|------------------------------|
| phase-2 -> phase-3 (dopo LS-Compactness) | **1449** (il bulk) |
| phase-3 -> phase-4 (dopo Compaction) | 51 |
| phase-4 -> phase-5 (dopo CH-room) | 0 |

**La fase LS-Compactness da sola sposta 1449/1744 (83%) delle celle sempre-mobili.** Compaction
aggiunge 51; le fasi room non muovono celle (solo stanze). L'erosione e' concentrata nel passaggio
LS-Compactness, non nelle fasi room o nel compaction deterministico.

## Per-(gruppo, materia) worst-erosion (MOBILE, ret% = kept)

- G09 Lingua Italiana: 3.6% (54 moved / 56)
- G03 Matematica: 6.1% (31 moved)
- G12 Scienze: 0.0% (30 moved)
- G01 Recuperi ed Approfondimenti: 8.0%
- G10 Comportamento Sociale: 5.0%
- G13 Orientamento: 5.0%, Competenze Digitali 3.4%
- G12 Igiene e sicurezza: 0.0%, Economia e Diritto del lavoro 6.7%

Materie teoriche generaliste (Lingua, Matematica, Storia, Scienze, Recuperi) dominano l'erosione:
sono le ore "libere" che la LS usa come riempitivo spostabile. Le ore tecnico-lab (pinnate dal
template o autoritative) non sono nel MOBILE set e tengono la cella.

## Riconciliazione con la resemblance KPI (74%)

La ricorrenza settimanale finale e' 74.4% (TASK-488 coda). L'erosione MOBILE (83.4% moved) non
contraddice: la resemblance 74% e' su TUTTE le unpinned finali (incluse le 7596 demoted, che
tennero la cella come pin e la conservarono dopo la demozione), mentre il MOBILE set (1752) e'
solo le MAI pinnate. **L'insight: il template e' preservato dai PIN (promoted + authoritative), non
dalle lezioni libere.** Le 1752 mai-pinnate sono l'83% rimescolate, ma sono una minoranza; la
maggioranza (pinnata) tiene la cella, da cui il 74% aggregate. La leva per alzare la resemblance
senza irrigidire la ricerca e' ridurre il rimescolamento delle mai-pinnate (es. M32 template
quasi-pinning gia' agisce qui), non pinnare di piu'.

## Scope onesto e limiti

- Il TO (phase-5) e' "entrante in LS-room-refinement", non "dopo l'ultima fase". Ma phase-4->phase-5
  = 0 cell-changes + room-refinement muove stanze non celle -> phase-5 ≈ finale per le celle.
- Il FROM (phase-2) e' post-construction-CH (il template stampato), non il raw round-robin (che la
  CH ha gia' fuso nel template). Quindi "construction" = template stampato, come da contract TASK-453.
- environment NON_REPRODUCIBLE: numeri su singolo run (varianza LS), non una media. La forma
  (LS-Compactness domina, teoriche erose, pin preservano) e' strutturale, i numeri assoluti hanno
  varianza.

## Verdetto

WORK 2 CHIUSO. L'erosione construction->finale sul MOBILE set e' 83.4% (16.6% retention),
concentrata nella fase LS-Compactness, dominata dalle ore teoriche generaliste mai-pinnate. Il
template (74% resemblance) e' preservato dai PIN, non dalle lezioni libere. Nessun cambio al
modello o all'adozione (misura diagnostica read-only). Insight pratico: la resemblance si alza
riducendo il rimescolamento delle mai-pinnate (M32), non pinnando di piu'.
