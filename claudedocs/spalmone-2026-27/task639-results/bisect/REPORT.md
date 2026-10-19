# BISECT 639: quale versione Timefold stalla nel MT-LS del banco lsvar?

Banco: LsVarianceBenchTest n=1, ck=attempt1/POST_CONSTRUCTION (i6), heap 8g,
race NON_REPRODUCIBLE. Launcher: /tmp/bisect-<tag>.log.

| braccio | versione | esito | wall | score | log | n run | stall-fasi |
|---------|----------|-------|------|-------|-----|
| 1 | 2.7.0-rc-1 | **COMPLETA** (3 iterazioni B-TIMESLOT, nessuno stall) | 6818s (1h54m) | -12/0 boxed 1 | braccio1-270rc1.log.txt | 1 | 0/3 |
| 2 | 2.5.0 | **COMPLETA** (3 iterazioni B-TIMESLOT, nessuno stall) | 9470s (2h38m) | -3/0 boxed 0 | braccio2-250.log.txt | 1 | 0/3 |
| 3 | 2.4.0 | in volo (completamento lasciato correre, poi registro) | | | | | |
| 4 | main | SALTATO (ordine maintainer: indagine chiusa) | | | | | |

STATO 22/09 11:02: il braccio 2 e' in volo. I log /tmp/bisect-*.log persistono nel
filesystem: se la sessione si interrompe (restart procedura annunciato), il braccio in
corso completa nel fork surefire e il risultato e' leggibile dal log a posteriori.

## Cautela statistica (emendamento maintainer 22/09: "la varianza non monotonica potrebbe
essere effetto dell'indeterminismo MT, n=1 non dimostra nulla")

Stall-fasi osservati su **2.6.0** (tutti MT-LS, firma offerLoud 5000ms):
1. laptop A/B 636 braccio B (heap 2g, ciclo app): stall dopo ~3 min B-TIMESLOT
2. laptop A/B 637 braccio A fallback single-thread (heap 2g): OOM prima dello stall
3. laptop A/B 637 braccio A retry single-thread (heap 4g): stall dopo ~15 min
4. laptop validazione 639 heap 3g: OOM prima dello stall
5. laptop validazione 639 heap 8g: stall dopo ~7 min
6. Fedora validazione v1 (barrier desync)
7. Fedora validazione v2 (barrier attesa a fette)
Fase pulita su 2.6.0: 1 (la B-TIMESLOT-1 della prima validazione Fedora, poi stall).

Su **2.5.0** e **2.7.0-rc-1**: 0 stall su 1 run ciascuno (3 fasi LS per run = 3
opportunita' di stall per run). Le RIPETIZIONI (emendamento A: 2 extra per 2.5.0,
poi 2 per rc-1 se resta tempo) sono in coda dopo i bracci 2.4.0 e MAIN.

BRACCIO 5 (jstack + istogramma durante stall) attivo su QUALSIASI stall, anche nelle
ripetizioni.

## CHIUSURA

INDAGINE CHIUSA su decisione maintainer 22/09: destinazione 2.7.0-rc-1 (completa il
ciclo, n=1); firma dello stall 2.6.0 (offerLoud 5s canale pieno + runner in
executeTemporaryMove/clone) da tenere d'occhio; riaprire solo se ricompare in modo
riproducibile su rc-1+. Braccio MAIN e ripetizioni annullati.
