# Heap-floor rc-1 a 3g: COMPLETA (TASK-640 residuo-config, 22/09, laptop)

Mandato: MISURA, nessuna decisione (passo 6 catena autonomia). Domanda: il ciclo
completo su Timefold 2.7.0-rc-1 regge heap 3g, o il floor operativo e' salito?
Contesto: 2.3.0 girava il ciclo a 2g; 2.6.0 OOM-ava a 2g e 4g (laptop, task637);
rc-1 era validato solo a 8g (bisect braccio1).

## Setup

- pom LOCALE (mai committato in questo passo): `timefold-solver.version` 2.6.0 -> 2.7.0-rc-1,
  ripristinato con `git checkout pom.xml` a fine run.
- Banco: `LsVarianceBenchTest` n=1 trio=0 dal checkpoint integrato-6
  (`claudedocs/spalmone-2026-27/integrato-6/catalog/attempt1/POST_CONSTRUCTION.json`),
  stessa forma dei bracci bisect TASK-639:
  `mvn test -Dtest=LsVarianceBenchTest -Dnet.bytebuddy.experimental=true -Dsurefire.heap=3g
  -Dlsvar.run=true -Dlsvar.n=1 -Dlsvar.trio=0 -Dlsvar.ck=...` (via `./scripts/core/test.sh`).
- laptop (fedora-box), fork partito 14:34:23, JDK 25.0.4, G1, 8 move threads MT-LS single-writer.

## Esito: COMPLETA, zero OOM

- `Tests run: 1, Failures: 0, Errors: 0` -> `BUILD SUCCESS`, `EXIT=0` (in coda al log).
- `[LSVAR] run 1 DONE wall=6143s boxed=2 hard=-19 medium=-600546 zero=false`.
  Confronto braccio1 rc-1 a 8g: wall=6818s hard=-12. Differenza hard e' variabilita'
  della race MT-LS (il banco misura proprio quella; trio=0 in entrambi, quindi il
  residuo atteso non passa dalla terna di consegna).
- Stage transitions: A-CONSTRUCTION +5s, PROMOTION +10s, B-TIMESLOT-1 +15s,
  B-TIMESLOT-2 +3627s, B-TIMESLOT-3 +4969s, RELEASE +5490s, B-ROOM +5516s, DONE 6143s.
  B-ROOM 606s. Nessun full-GC death spiral, nessun revert anomalo (CHC improve-or-noop
  revertito una volta: comportamento operativo normale).
- A 3g non e' solo sopravvissuto: wall 6143s vs 6818s dell'8g (stesso banco, stesso
  checkpoint, macchina diversamente carica: non e' una misura di velocita', solo
  assenza di penalita' dominante).

## Campioni heap (jcmd GC.heap_info, fork 33659)

Committed fisso 3145728K = 3g (il `-Xmx3g` e' arrivato al fork).

| ora | used heap | RSS |
|-----|-----------|-----|
| 14:40 | 2166241K (~2,07g) | - |
| 14:45 | 2560799K (~2,44g) | 3306560K (~3,15g) |
| 14:51 | 2501217K (~2,39g) | 3051776K (~2,91g) |
| 15:35 (fine LS1, confine fase) | 2613248K (~2,49g) | 3359584K (~3,20g) |

Picco osservato used ~2,49g su 3g con RSS ~3,2g: margine presente in ogni campione,
nessuna pressione vicino al tetto.

## Nota tecnica: perche' i campioni storici erano vuoti

`pgrep -f surefirebooter` su macOS ritorna il PID del WRAPPER (~560KB RSS), non il
JVM forkato: attach fallisce con `AttachNotSupportedException ... not ready to
participate in attach handshake` (firma identica nei campioni vuoti di
`640-heap-8g.txt` e `640-heap-samples.txt`). Il fork vero e' visibile solo in
`jcmd -l` (wrapper 33658, JVM 33659). Formazione corretta: risolvere il PID con
`jcmd -l | grep surefirebooter`, mai con pgrep.

I thread-dump nel log sono il Quarkus hang detector (test lungo in un solo @Test:
4 dump anche nel log del braccio rc-1 8g VERDE). Il main thread `staged-solve-resume`
era RUNNABLE dentro la LS in ogni dump: non e' la firma stall TASK-639.

## Lettura per il runbook (misura, decisione a maintainer)

Il ciclo completo su rc-1 a heap 3g COMPLETA sul laptop affollato. Il floor 2g del
runbook 2.3.0 NON e' stato ri-misurato su rc-1 (fuori mandato). Aggiornare il
runbook/mandati a 3g (o misurare 2g) e' decisione del proprietario.

Log integrale: `heapfloor-3g.log.txt` (campioni raw in /tmp/heapfloor-samples.txt,
riportati nella tabella).
