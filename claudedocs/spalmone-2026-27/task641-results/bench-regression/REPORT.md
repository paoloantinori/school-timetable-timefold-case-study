# TASK-641 banco regressione room-aware (PASSO 3 coda laptop, 23/09, rc-1, HEAD 7e7e15cb)

OffCapture638BenchTest mode=steepest, candidates=100000,
-Dplanner.construction.violation-steepest.room-aware.enabled=true.

| Stato | Prima (flag OFF, catalog sweep) | Dopo (flag ON) | Predizione | Esito |
|---|---|---|---|---|
| 637-off (export) | -3 -> -1 (H0+H32 chiusi, H29 residuo) | **-3 -> 0** in 456ms, 2 mosse: CHANGE (+2) + **SWAP +ROOM R11** (+1: l'H29 chiuso) | -3 -> 0 | CENTRATA |
| integrato-1/run (pipe) | -52 -> -51 (solo H7) | **-52 -> -41 in 7 mosse** (12,9s): inclusa **SWAP +ROOM -/R07: Lavorazioni meccaniche <-> Tecniche di saldatura** (la cura del blind puzzle, ora esprimibile con la stanza) + cure famiglie stage del rumore pipe | -52 -> -41 in 7 mosse | CENTRATA |
| 636-off (export) | -2 -> 0 (1 swap, 23337 eval) | **-2 -> 0**: IDENTICO, stessa swap Lingua Inglese <-> Educazione Civica, 23337 eval, pass stanza MAI invocato (superficie 638 intatta quando la grammatica semplice basta) | identico al pre | CENTRATA |

3/3 predizioni pre-registrate CENTRATE, zero divergenze da indagare.
roomVariants=58368 censiti nel no-improving di integrato-1 (la popolazione
composita esplorata). Suite su HEAD: 1370/0 EXIT 0 a heap 8g (PASSO 2).
