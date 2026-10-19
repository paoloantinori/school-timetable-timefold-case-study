# VERDICT - Frequenza bad-seed (misura differita TASK-480). source=input, n=8 GRASP + 1 baseline. 2026-08-03.

> Misura differita dichiarata nella chiusura TASK-480 (n=3, Wilson CI larga [0.50, 1.00]). Qui
> n=8 costruzioni GRASP indipendenti + 1 baseline GRASP-off (controllo deterministico). Obiettivo:
> stimare p(bacino buono) con intervallo e N raccomandato, E caratterizzare i bacini cattivi (non
> solo contarli). Numeri dai raw (score.txt / analysis.json / export.json.gz), non dal SUMMARY
> auto-generato (che comunque e' corretto stavolta, vedi §cross-check).

## TL;DR (verdetto in 3 righe)

1. **p(0-hard) STRICT = 0.750 (k=6/n=8), Wilson95 [0.409, 0.929].** Lettura STRUCTURALE
   (escludendo il bacino room-tier) = 0.875, Wilson95 [0.529, 0.978]. N raccomandato per
   P(>=1 0-hard) >= 0.95: **N=3 point (~85 min sequenziali), N=6 conservativo (~170 min)**.
   Wall medio 28 min/run. Procedura multi-start validata come hedge operativo.
2. **I due bacini cattivi sono DUE MODALITA' DI FALLIMENTO DISTINTE**, non un pattern solo:
   grasp-seed-1 (-10hard) = H17 -9 (9 gap, tutti G11 mercoledi, il bacino saturo noto di
   TASK-481/482) + H29 -1; grasp-seed-6 (-1hard) = H29 -1 SOLO (zero H17), una lezione lab senza
   aula per saturazione del tag meccanica (1 stanza, 2 lezioni conflittuali) = territorio M-TC.
3. **Cosa NON dimostra (n=8):** il vero p (CI ancora larga; CI_lo strict 0.409); se il room-null
   di seed-6 sia recuperabile via co-presenza G04+G13 (non verificato); il wall-clock in
   produzione parallela (lo speedup N-container e' non costruito). NESSUN cambio di default:
   source resta golden, GRASP resta opt-in OFF, multi-start resta PROCEDURA operativa.

## Campione e metodo

- **Setup**: profilo `multistart` fresh (seed + import-calendario + pre-assign source=input),
  ricostruito una volta; `deletedPriorPins: 0` conferma fresh reset. 10.686 lesson pinned
  (constrained teachers + rotations + stage), coerente con pre-assign realmente eseguito.
- **Run**: 1 baseline GRASP-off (source=input, costruzione deterministica, controllo) + 8 GRASP
  seed=1..8 (source=input + `-Dplanner.construction.grasp.enabled=true .seed=i`, LIVE sulla riga
  `java -jar`). Sequenziale su un profilo (regola single-DB-actor; 23 campi static volatile
  vietano il parallelismo in-JVM). Boot prod via `planner.sh prod`, `-D` su PLANNER_JAVA_OPTS.
- **Anti-bug**: md5 dei lessons.txt distinti tra seed (TASK-480) => il flag GRASP e' giunto al
  java -jar per ogni seed (banner `grasp=%b seed=%d` verificato sui boot log). Nessun timeout-cap
  (wall max 2205s, cap 5400s). Tutte le 5 fasi complete (phases.json last = LS-Room Refinement).
- **Cross-check SUMMARY**: lo script auto-genera "0-hard GRASP runs: 6/8 (excludes baseline)",
  baseline contato separatamente, pick-best = baseline-grasp-off. Combacia con il conteggio raw
  (il bug di conteggio TASK-480 e' assente: la correzione `baseline contato separatamente` funziona).

## Per-run (dai raw score.txt)

| run | score | wall(s) | 0-hard? | composizione hard |
|-----|-------|---------|---------|-------------------|
| baseline-grasp-off | 0hard/-1043809medium | 1433 | YES | - |
| grasp-seed-1 | -10hard/-1138058medium | 1857 | no | H17 -9 (9 gap, tutti G11) + H29 -1 |
| grasp-seed-2 | 0hard/-1290920medium | 2205 | YES | - |
| grasp-seed-3 | 0hard/-1344272medium | 1423 | YES | - |
| grasp-seed-4 | 0hard/-1193151medium | 1455 | YES | - |
| grasp-seed-5 | 0hard/-1256809medium | 1661 | YES | - |
| grasp-seed-6 | -1hard/-1259388medium | 1794 | no | H29 -1 SOLO (zero H17) |
| grasp-seed-7 | 0hard/-1261478medium | 1808 | YES | - |
| grasp-seed-8 | 0hard/-1227065medium | 1361 | YES | - |

baseline = -1043809medium e' il pick-best (medium migliore tra gli 0-hard), coerente con TASK-480
(la costruzione default deterministica atterra nel bacino buono). La LS e' stocastica: i medium
variano ~270k tra run 0-hard (da -1.04M a -1.34M), atteso.

## Statistica - due letture (perche' i bacini cattivi non sono equivalenti)

I due bacini cattivi hanno natura diversa (H17 strutturale vs H29 room-tier), quindi una sola
lettura di p sarebbe disonesta. Wilson 95% su n=8 costruzioni GRASP indipendenti.

**STRICT (hard==0, la lettura operativa corretta):** k=6/n=8.
- p_hat = 0.750, Wilson95 [0.409, 0.929].
- P(>=1 0-hard | N): N=3 -> 0.984 (point) [0.794, 1.000]; N=4 -> 0.996.
- N raccomandato P>=0.95: point=3, CI_lo=6. N P>=0.99: point=4, CI_lo=9.
- Costo: N=3 = ~85 min, N=6 = ~170 min (sequenziale, wall medio 28 min).

**STRUCTURALE (solo bacini H17-dominanti contano come fallimento; seed-6 H29-only -> good):**
k=7/n=8.
- p_hat = 0.875, Wilson95 [0.529, 0.978].
- N P>=0.95: point=2, CI_lo=4.

**Perche' due letture:** la lettura STRUCTURALE esclude seed-6 dal computo dei fallimenti solo se
il suo room-null e' recuperabile. Il drill (sotto) mostra che NON lo e' via rifinitura stanze
(outcome a, saturazione). Quindi la lettura STRICT e' quella operativamente corretta: un run
-1hard NON e' 0-hard, non aiuta il pick-best a raggiungere 0-hard. La STRUCTURALE resta utile come
borne superiore (se i room-null fossero tutti curabili, p salirebbe a 0.875).

**Estensione del batch?** CI_lo strict = 0.409 (< 0.5, soglia indicativa). NON estendo: la
raccomandazione e' stateable (N=3 point / N=6 conservativo regge su tutto l'intervallo); +2-4 run
(non oltre) non porterebbero CI_lo > 0.5 (servirebbe n~16 per quello, ~4h per un guadagno
marginale di precisione su p, che non e' l'output portante). L'output portante e' la
caratterizzazione dei bacini (due modalita'), gia' ottenuta.

## Caratterizzazione dei due bacini cattivi (il dato piu' informativo del batch)

### grasp-seed-1 (-10hard): bacino strutturale H17, il pattern noto
- H17 -9 (9 gap interni), **tutti in G11** (kpi-holes: BUCO-INTERNO=9, 100% in G11).
  E' il bacino saturo del mercoledi' documentato in TASK-481/482 (G11 mercoledi, giorno
  packato, gap mid-day). Non curabile localmente (depth-3 cascade e Kempe-chain entrambi NO,
  verificati).
- H29 -1: 1 room-null lab, G06 2027-05-24 13:00 "Servizio stilistico" (T26), non-pinnata.

### grasp-seed-6 (-1hard): bacino "quasi buono", room-tier, NON strutturale H17
- H29 -1 SOLO. Zero H17 (kpi-holes: 0 BUCO-INTERNO). Orario compatto valido, un solo room-null.
- **Lezione coinvolta**: G04, 2026-11-04 13:00, "Lavorazioni meccaniche" (T15),
  lab=True, pinned=False, unica non-pinnata room-null di docente reale (verificato: H29 e'
  pinned-exempt e pseudo-exempt, le altre room-null nel dump sono lab MODA pinnati, esenti).

### Drill causale di seed-6: outcome (a) SATURAZIONE, non residuo di rifinitura
Domanda: perche' la lezione e' rimasta senza aula? Stanze ammissibili (H-M2 tag `meccanica` +
`Room.isAssignable` cap>0): **solo R15** (cap 20, unica con tag meccanica). Mappatura
confermata: tutte le 401 "Lavorazioni meccaniche" vanno in R15.

A 2026-11-04T13:00 ci sono **2 lezioni meccaniche** che competono per l'unica stanza:
- G04 (T15) -> room = **None** (la vittima H29)
- G13 (T10) -> room = **R15** (ha preso la stanza)

R15 e' occupata da G13 in quello slot. H2 (room conflict, hard) vieta la condivisione.
**Stanze libere compatibili: 0.**

**Esito: (a) saturazione di capacita' del tag** (1 locale meccanica, 2 lezioni conflittuali),
territorio **M-TC (TASK-455)**. NON e' un residuo di rifinitura (ipotesi (b) confutata): non c'e'
una stanza libera lasciata sul tavolo che un pass deterministico stanze chiuderebbe. Un
ExactFinisher-style room repair **non** la curerebbe (non c'e' dove metterla). Le leve reali,
se mai, sono: co-presenza/co-locazione (G04 ospite di G13? non verificato sul golden), piu'
locali meccanica (input), o M-TC adottato (penalizza l'over-booking del tag). Nessuna stasera.

## Cosa NON dimostra (n=8) + decisioni

- Il **vero p** e' stimato, non misurato: CI larga (strict [0.409, 0.929]). La raccomandazione
  (N=3-6) regge su tutto l'intervallo, ma il point estimate 0.75 ha incertezza.
- **seed-6 via co-presenza non verificato**: non ho controllato se (G04, G13) e' una coppia
  co-presenza praticata dal golden (depth >= 2). Se lo fosse, un'esenzione H2 potrebbe
  co-locarli e recuperare il room-null: indagine differita (non blocca il verdetto, cambia solo
  la lettura STRUCTURALE da ottimistica a reale).
- **Wall-clock parallelo non misurato**: lo speedup N-container (un profilo+JVM per run) e'
  non costruito; in parallelo N=3 potrebbe essere ~30 min invece di ~85. Documentato come
  "NEXT" nello script SUMMARY.
- **Decisioni (a nome di maintainer, da segnalare):** NESSUN cambio di default (source resta golden,
  GRASP opt-in OFF); multi-start resta PROCEDURA operativa per il path source=input. Il bacino
  room-tier di seed-6 mappa su TASK-455 (M-TC, gia' prototipo default-OFF): nessun nuovo task,
  ma l'evidenza (1 stanza meccanica, 2 lezioni conflittuali produce -1hard) sostiene il caso per
  M-TC se la scuola conferma che la scarsita' del tag e' strutturale.

## Puntatori
- Raw: `task480-results/badseed-frequency/{baseline-grasp-off,grasp-seed-1..8}/` (score.txt,
  analysis.json, export.json.gz, kpi-holes.txt, lessons.txt) + SUMMARY.txt.
- Stats helper: `scripts/analyze/badseed-frequency-stats.py` (validato contro TASK-480).
- Batch script: `scripts/benchmark/multistart-ab.sh` (RESULTS_DIR parameterizzato).
- Task sorelle: TASK-480 (multi-start adottato), TASK-481 (depth-3 shell-game), TASK-482
  (Kempe NO), TASK-455 (M-TC prototipo, territorio del bacino room-tier di seed-6).
