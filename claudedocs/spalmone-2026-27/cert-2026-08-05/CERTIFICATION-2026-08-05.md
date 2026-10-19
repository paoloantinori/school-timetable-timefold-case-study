# Certificazione orario 2026/27 - source=input (2026-08-05)

Pacchetto finale per la scuola. Solve staged COMPLETO sul path di produzione
(`source=input`, senza golden), con tutti i flag ratified DEFAULT ON.

## Exec summary

- **hard = 0** (GATE MET). L'orario e' feasibile: zero conflitti docente / stanza /
  gruppo / calendario / disponibilita'.
- Il multi-start ha curato il bad basin: attempt 1 non 0-hard (bad seed, atteso su
  source=input), **attempt 2 a 0-hard** (`staged multi-start attempt 2 done: mtc=true,
  score=0hard/...`). Stop al primo 0-hard, come disegnato.
- KPI: full-day **85.1%**, day-start **100.0%**, ricorrenza unpinned **75.0%**,
  buchi interni **0**, cadence burst **41.4%**.
- Nota onesta (sotto): full-day 85.1% e medium -1.30M sono nella **bassa banda** della
  campagna. `hard=0` e' binario e bloccante, ed e' raggiunto; il medium e' il
  pick first-0-hard del multi-start (LS NON_REPRODUCIBLE).

## Procedura (ripetibile)

- Profilo fresco `cert`, app in **PROD mode** (`java -jar`) con
  `-Dplanner.pre-assign.constrained-teacher.source=input` via `PLANNER_JAVA_OPTS`
  (regola CLAUDE.md: un `-D` per un LIVE SOLVE sta sulla riga `java -jar`, non in
  MAVEN_OPTS).
- **source=input verificato LIVE**: banner `TASK-452 source=input: built input-derived
  profiles for 2 constrained teacher(s)` nell'app.log.
- **Tutti i flag ratified ON** (banner `refreshSolverStatics`): `constrained-teacher=true`,
  `first-hour-slot=true`, `ExactFinisher enabled=true`, `Multi-start staged retry
  enabled=true maxAttempts=3`, `M-TC per-retry armed=true`.
- import-calendario: 866 MODA pinned, 312 righe MODA, 909 righe FC; pre-assign COMPLETE.
- Driver: `scripts/benchmark/cert-source-input.sh` (fresh profile -> import-calendario
  -> pre-assign -> solve-staged -> poll -> capture-run.sh).

## KPI

| metrica | valore | riferimento golden / campagna |
|---|---|---|
| **hard** | **0** | 0 (gate binario) |
| medium | -1,299,651 | banda good-seed -963k / -1047k |
| soft | -23,578 | - |
| **full-day** | 85.1% (1665/1957) | golden ~91.9% ex-G14; run-30b 90.1%; campagna 87.5% |
| **day-start ancorato** | 100.0% (2191/2192) | golden ~99.3-100% |
| **ricorrenza unpinned** | 75.0% (7053/9404) | campagna 75.6-75.9% |
| **buchi interni** | 0 giorni / 0 slot | 0 (meglio dei 18 accettati come costo TASK-451) |
| **cadence burst >2x** | 41.4% | golden ~41.6% |

Deficit full-day (senza G14): 173 giorni sotto quota, 138 a -1h (80%), 35 profondi
(>=2h); gruppi peggiori per giorni profondi: G08 11, G04 6, G10 5, G11 5, G13 3.
Day-start: 2179 giorni iniziano alle 8:00, 12 alle 9:00, 1 alle 10:00.

## Confronto golden

`confronto-cert-2026-08-05.html` (14 gruppi, 171 giorni, 1.4 MB): vista affiancata
golden-vs-run per giorno/gruppo. `day-start-anatomy.html` per l'ancoraggio prima ora.

## Valutazione onesta

- **hard=0: gate MET.** L'orario e' fully feasible (nessun conflitto). Questo e' il
  vincolo binario bloccante ed e' soddisfatto.
- day-start 100%, buchi interni 0, ricorrenza 75%, cadence 41.4%: **solidi e in linea**
  con il riferimento campagna.
- **full-day 85.1% e medium -1.30M: bassa banda.** Due cause combinate: (a) la LS e'
  NON_REPRODUCIBLE, quindi varianza run-to-run; (b) questo e' il **pick first-0-hard**
  del multi-start (attempt 2; attempt 1 era il bad basin). Il multi-start stoppa al
  primo 0-hard, non ottimizza il medium oltre. Per migliorare full-day/medium serve un
  re-run con seme diverso o (config diversa) continuare oltre il primo 0-hard.

Per la scuola: l'orario e' **valido e ben ancorato** (0 conflitti, giornata piena al
85%, prime ore al 100%, zero buchi interni). Il fill-day e' ~2.4pp sotto il riferimento
campagna (87.5%): accettabile per la consegna, migliorabile con un re-run.

## Artefatti (in questa directory)

`export.json.gz` (snapshot ricostruibile, TASK-401), `lessons.txt`
(group|date|hour|subject|teacher|pinned), `score.txt`, `phases.json`, `analysis.json`,
`kpi-*.txt` (fill, daystart, resemblance, cadence, holes, moda-knot, sharpness),
`teacher-fingerprint.txt`, `cert-driver.log` + `app.log` (provenienza run).

## Riproducibilita'

```
PROFILE=cert ./scripts/benchmark/cert-source-input.sh   # ~2h per 2 tentativi multi-start
```

## Riproducibilita' (run2, JAR rebuildato fresco da HEAD)

Un secondo run indipendente (stesso driver, JAR rebuildato fresco da HEAD c1139403)
conferma run1 su tutti i KPI:

| metrica | run1 | run2 |
|---|---|---|
| hard | 0 | 0 |
| medium | -1,299,651 | -1,293,310 |
| full-day | 85.1% (1665/1957) | 85.3% (1670/1957) |
| day-start ancorato | 100.0% (2191/2192) | 100.0% (2194/2194) |
| ricorrenza unpinned | 75.0% | 74.5% |
| buchi interni | 0 | 0 |
| cadence burst >2x | 41.4% | 40.2% |

Entrambi i run: bad seed al attempt 1 (non 0-hard), curato al attempt 2 (mtc=true,
0-hard). **hard=0 e' riproducibile**; full-day ~85% e' **stabile** tra i due run, quindi
non e' rumore LS ma una caratteristica del path source=input col pick first-0-hard del
multi-start. Evidence di run2 in `cert-2026-08-05-r2/` (KPI); run1 resta lo snapshot
canonico (export.json.gz, lessons.txt, confronto HTML).
