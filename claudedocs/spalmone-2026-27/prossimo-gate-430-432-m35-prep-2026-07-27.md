# PREP prossimo gate: A/B x2 con 430-ON + 432-ON (+M35=100 opzionale), 2026-07-27 sera

PREP ONLY, NON LANCIO. Il ciclo live lo decide Fedora/maintainer domattina (runbook fresco,
budget, caffeinate). Questo doc fissa: i flag esatti sulla riga `java -jar`, le attese
quantitative con derivazione, l'ordine di lettura, e come verificare che i flag abbiano
raggiunto la JVM dell'app (non silenziosamente droppati come i MAVEN_OPTS sul fork mvn).

HEAD di riferimento per il gate: **4b9054dd** (TASK-432 Done + DoD, rebasato su
fd233730 che porta TASK-434/viz/spike). Ratifica gia' a terra (0d8d7e67): 424 (M20
pin-netting) e 425 (M34/M35 ai pesi 100/50) ADOTTATI; 429 (SurplusRebalance) adottato
implicitamente; 430 (slot-0 balancing) e 432 (profile-following) NON-adottati, flag
default OFF, materiale per QUESTO gate.

## Base di partenza (ratifica, run2bis n=2, HEAD 0d8d7e67, 424+425 adottati)

| Metrica | run2bis | golden |
|---|---|---|
| hard | 0 | 0 |
| full-day | 78,5% | 85,4 |
| giorni-sopra | 83 | 70 |
| burst>2x | 41,8% | 41,6 |
| ricorrenza unpinned | 72,8% | - |
| day-start | 96,1% | 99,3 |
| gap-weeks | ~1710 (run1; run2bis nel dir) | - |

## Braccia del gate (x2 ciascuna, n=2 per stabilita' come run2bis)

**Braccio A (core): 430-ON + 432-ON.** Leva construction-level, flag-gated default OFF.
**Braccio B (opzionale, terzo): A + M35=100.** Solo se il gate A mostra gap-weeks ancora
alto e si vuole spingere lo spread; M35=100 raddoppia la penalita' weekly-spread-deficit.

### Flag esatti sulla riga java -jar (NON mvn quarkus:run: MAVEN_OPTS non reach il fork)

Perche' java -jar diretto: i flag `-D` letti via `System.getProperty()` / `Boolean.getBoolean()`
raggiungono la JVM dell'app SOLO sulla riga `java -jar`, non via MAVEN_OPTS sul fork mvn
quarkus:run (gap documentato in CLAUDE.md, verificato TASK-326/419). `./planner.sh prod --fresh
<profile>` e' il path one-command equivalente (lancia java -jar diretto); `./planner.sh kill-app`
ferma via `pgrep -f quarkus-run.jar`.

**Braccio A:**
```
java -Xmx2g \
  -Dplanner.construction.slot0-balancing.enabled=true \
  -Dplanner.construction.profile-following.enabled=true \
  -Dplanner.phase-transition-dump.enabled=true \
  -Dquarkus.hibernate-orm.schema-management.strategy=update \
  -jar target/quarkus-app/quarkus-run.jar
```
**Braccio B:** aggiungere `-Dplanner.m35.weight=100`.

Entrambi i bracci: solve in modalita' campagna **STAGED** (`POST /api/schedule/solve-staged`,
ovvero `./scripts/api/api.sh solve-staged` con attesa staged-aware). Cattura canonica:
`./scripts/benchmark/capture-run.sh <outdir>` (score, analysis, export, lessons.txt + tutti
i KPI + viz HTML). x2 run per braccio su profilo FRESCO (runbook: volume wipe + reseed,
`./scripts/api/import-calendario.sh` RC=0, pre-assign COMPLETE = 2651 pinned).

### Verifica che i flag abbiano raggiunto la JVM dell'app (pre-solve)

I flag 430/432 non hanno un log di conferma oggi. Verifica indiretta obbligatoria:
- **432-ON**: il dump `target/construction-lessons.txt` (o KPI resemblance/cadence su G10)
  mostra T01 **concentrata** sui 9 giovedi' monografici (65h li, ~0 altrove), NON spalmata
  sui giovedi' autunnali (baseline flag-OFF: ~24h monografici / ~41h altri). Asserzione del
  checkpoint WeeklyTemplateConstructionRealSeedTest#profileFollowing_concentratesT01
  OnMonographicThursdays: monographic>=55, other<=10.
- **430-ON**: day-start construction-end >= 98,8% nel dump (vs 98,3% flag-OFF). Verifica
  checkpoint: WeeklyTemplateConstructionRealSeedTest flag-ON recurrence >= 50, full-day >= 65.
- **M35=100** (braccio B): la riga M35 dell'analysis (`GET /api/schedule/analysis`) mostra
  peso raddoppiato (weight 100 vs 50); il M35 score e' ~2x del braccio A a parita' di placement
  (ma il placement cambiera' perche' la ricerca risponde al peso).
- **phase-transition dump**: 4 file dump appaiono sotto la dir (construction->finale erosion
  misurabile per-fase; run1 non li aveva, run2bis si').
- Se una di queste NON si muove nella direzione attesa, il flag e' droppato: FERMARSI e
  rilanciare via `./planner.sh prod --fresh` (java -jar diretto) invece di mvn quarkus:run.

### Attese quantitative con derivazione (PREDIZIONI pre-run, il gate misura l'effettivo)

**Braccio A (430-ON + 432-ON):**
- **hard 0 mantenuto** (entrambi i pre-pass sono hard-gated, zero conflitti accettati;
  checkpoint: stampSkipConflict 0 a fine stamp, popolazione finale 0 H1/H3).
- **full-day 78,5% -> ~79,5-80,5%** (+1-2pp). Derivazione: 432 riempie i 9 giovedi'
  monografici G10 (12-03/10 5L, 12-17 7L, 01-07/14/21 8L, 05-20/27 8L, 06-03 8L =
  5+5+7+8x6 = 65h esatte) che erano in deficit (T01 sotto-piazzata: 24h vs 65h golden).
  G10 scoped era 101/130 = 78% full; +9 giorni full -> ~110/130 = 85%. Direct overall:
  +9/1954 scoped = +0,5pp; piu' secondary (giovedi' autunnali liberati da T01 -> altri
  corsi G10 li riempiono meglio) e surplus ridotto. G10 e' ~42% del gap full-day residuo
  (ratifica-m12), quindi la spinta e' reale ma non chiude tutto (resta famiglia FC + coda).
- **giorni-sopra 83 -> ~76-79** (-4...-7). Derivazione: 432 elimina i +37h di T01
  spalmati sui giovedi' autunnali a 4h (surplus M33); quei giorni tornano a fill pulito.
  M33 score scende proporzionalmente.
- **day-start 96,1% -> ~96,5-97%** (+0,4...+0,9, modesto). Derivazione: 430 ancoraggio
  construction 98,3->98,8%, ma l'anatomia 426 dimostra che il residuo e' H1-conteso
  cross-gruppo allo slot 0, non chiudibile del tutto da una leva greedy; la LS erode parte
  del guadagno construction. Miglioramento reale ma modesto (anatomy-predetto, come 430).
- **ricorrenza unpinned ~72-73%** (tiene). Derivazione: checkpoint 432 misura ricorrenza
  74,7% construction == flag-OFF (i cluster monografici dic/gen/mag-giu sono weekly-
  consistenti; i giorni liberati vengono presi dai corsi multi-giorno). Nessuna regressione.
- **burst>2x ~41,5-42%** (tiene al golden 41,6; M34 non toccato).

**Braccio B (+M35=100):** differenziale vs A.
- **gap-weeks ~1710 -> ~1620-1660** (-50...-90). Derivazione: M35=100 raddoppia la penalta'
  per settimana attiva mancante sotto il golden spread -> la LS spalma di piu' le materie su
  settimane aggiuntive -> meno gap-weeks. M35 e M34 sono sinergici (entrambi spingono verso
  piu' settimane), non in tensione (TASK-425).
- **burst/full-day/day-start: marginali** (M35 agisce sullo spread settimanale, non sul fill
  giornaliero o sul burst). Possibile piccolo trade ricorrenza/surplus se M35=100 over-spread;
  misurare. Se il delta gap-weeks e' <30 o ci sono regressioni su ricorrenza/sopra, M35 resta a 50.

### Ordine di lettura del gate (dopo cattura x2 per braccio)

1. **APPLICABILITY TRIPLE** (criterio maintainer 2026-07-27 sera: "triple a zero vs numeri
   golden prima di ogni ottimizzazione medium"). Leggere dallo scoreboard del viz
   (`golden-vs-run-viz.py`, righe `APPLICABILITY ...` su stdout): **holes (empty-in-span)
   N/G** e **lateViol (late-start) N/G**, run-vs-golden per gruppo + totale. m12-run1
   baseline: 82 late-violazioni / 9 vuoti (G04); golden 0/0 (17 late tutti legittimi).
   Il braccio A (430 day-start + 432 fill) deve muovere late-violations e vuoti verso il
   golden, non solo full-day. E' la metrica di prima classe ora; full-day/sopra/etc. sono
   contesto.
2. **hard = 0** entrambi i run di entrambi i bracci (gate non-negoziable).
3. **full-day** (G10 dovrebbe salire nel braccio A; confronto per-gruppo via daily-fill-kpi).
4. **giorni-sopra** (surplus dovrebbe scendere nel braccio A, M33 score giu').
5. **day-start** (lift modesto nel braccio A da 430).
6. **burst>2x** (deve tenere ~41,6 golden; M34 intatto).
7. **ricorrenza unpinned** (deve tenere >=72%; nessuna regressione).
8. **gap-weeks** (braccio B dovrebbe scendere se M35=100 aiuta; confronto A vs B).
9. **breakdown score M20/M33/M34/M35** (analysis: M33 giu' nel braccio A, M35 su' nel B).
10. **G10 profile** (lessons.txt -> T01 concentrata sui 9 monografici; misura dedicata).
11. **phase-transition dumps** (erosione construction->finale per-fase; quanto la LS erode il
    template/concentrazione).
12. n=2 stability check (run1 vs run2 per braccio entro varianza, come run2bis).

### Cambiamenti di contesto (commits maintainer sera 2026-07-27, su cui questo gate si appoggia)

- **TASK-433 (AnchorRepair, High, m-12, To Do)**: sibling REPAIR di 430 sul day-start
  (spike 681f069f: 89->16 late-start con M18-guard, 0 de-anchor; residuo = saturazione
  slot-0 cross-gruppo, STESSO bersaglio di 430 -> prevenzione+riparazione convergenti).
  Finche' 433 e' To Do (non implementato), il braccio day-start del gate e' **430 solo**;
  se 433 viene implementato prima del gate, il braccio day-start diventa **430+433**
  (prevenzione+repair) e il target late-start si abbassa ulteriormente (<=3 non-legittimi =
  parita' golden -> riapre la promozione M18->hard col repair come rete).
- **TASK-434 (G04 stage, High, m-12, AUTORIZZATO ~22:30)**: fix DATI - aggiunge la finestra
  stage G04 (240h, 30 date 2027-04-26..06-14) ai fact (calendario_fc/DataGenerator,
  file protetto autorizzato). Se atterra prima del gate, la baseline G04 shifta (vuoti-in-span
  9->3): RE-BASELINARE il gate (i 9 vuoti G04 del m12-run1 baseline scendono a 3 di origine
  dati, non di ricerca). Q27 nel dossier 428 (segnalazione scuola foglio-vs-golden, FYI).
- **viz applicability (872d468f)**: scoreboard + marker LATE-START (ambra: solid=violazione,
  dashed=golden-legittimo) + EMPTY-IN-SPAN (hatch). Soglia data-driven per fogli non-autoritativi
  (G14 ratio 8,6 vs <=1,32 altri). Il gate legge la tripla da qui.

### Ratifica
SOLO maintainer. Il gate produce evidenza (A vs baseline, B vs A); l'adozione di 430/432 (e
eventuale M35=100) e' decisione di maintainer sulla base dell'A/B. I flag restano default OFF
fino a ratifica -> nessun solve di produzione e' toccato nel frattempo.

### Nota M35 (perche' M35=100 e' un braccio del gate, non una pre-batteria)
La pre-batteria M35 50-vs-100 "via checkpoint" era BLOCKED dal lato test-JVM: `M35_WEIGHT`
e' `public static final` risolto a JVM class-init (`SchoolScheduleConstraintProviderTest:
4020-4035` documenta il pattern M32/M35: `setProperty` in un test method arriva dopo il
class-load -> nessun effetto; richiede `-D` out-of-process); e `test.sh` rifiuta `-D`
sconosciuti -> knob non raggiungibile nel test JVM dal runner. Clausola escapes del mandato
applicata. Il path live (`java -jar -Dplanner.m35.weight=100`) raggiunge il knob (JVM fresca)
MA e' esattamente il gate A/B stesso -> M35=100 e' un TERZO braccio del gate, non una pre-misura.
