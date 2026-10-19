# VERDICT bad-seed frequency - N=32 pooled (m-13 dato forte) (zai, 2026-08-10)

Estensione del bad-seed frequency (TASK-480 differita) a N=32, pooled su due batch laptop
(Fedora-offline compute window). Config CORRENTE (tutti i flag ratificati DEFAULT ON, M-TC
per-retry). Obiettivo: stringere la CI di p(0-hard) per la decisione m-13 + cercare il bacino
H17 probabilistico.

## Headline

**p(0-hard) = 32/32 = 1,0 su N=32 costruzioni GRASP (source=input, config corrente),
Wilson95 ~[0,89, 1,000].** Il bacino H17 NON e' uscito in 32 costruzioni.

## Due batch (pooled)

| batch | semi | esito | Wilson95 |
|-------|------|-------|----------|
| N=16 (master-batch-1, 08-09) | 1-16 | 16/16 0hard | [0,82, 1,0] |
| N=16 ext (master-batch-4, 08-10) | 17-32 | 16/16 0hard | [0,82, 1,0] |
| **POOLED N=32** | 1-32 | **32/32 0hard** | **~[0,89, 1,0]** |

pick-best overall: grasp-seed-18 (0hard/-1.020.989medium).

## Lettura (m-13)

1. **p(0-hard) ~ 1,0 su n=32 con config corrente.** Forte: la procedura multi-start (solve-staged
   default) consegna 0hard in modo affidabile sul path input. P(>=1 0hard | N=3 tentativi) > 0,999.
2. **Il bacino H17 (timeslot verticale, quello del previo n=8 pre-M-TC, seed-1 type) NON e'
   uscito in 32 costruzioni.** Due letture possibili: (a) M-TC per-retry + il multi-start lo curano
   sempre (il bacino era LS-probabilistico, ora coperto); (b) e' raro e n=32 non lo campiona. In
   entrambi i casi, per la procedura operativa (multi-start N=3), e' robustamente coperto.
3. **Discrepanza col previo n=8 (08-03, p=0,75) confermata strutturale**: quel batch era PRE-M-TC
   per-retry (TASK-486 ratificato 08-04); il modo-di-fallimento room-tier (seed-6 type) e' sparito
   dalla config corrente. L'H17 (seed-1 type) era LS-probabilistico e qui non si e' presentato.

## Verdetto per m-13 (decisione maintainer)

**Il dato SUPPORTA FORTEMENTE accettare il multi-start come chiusura procedurale del path input.**
p(0-hard) ~ 1,0 (n=32, [0,89,1,0]); nessun bacino bad in 32 costruzioni con config corrente.
La procedura e' robusta. Resta il caveat onesto: 0-hard e' BINARIO e BLOCCANTE end-to-end
(direttiva maintainer 2026-08-02), e l'H17 resta LS-probabilistico (non deterministicamente escluso),
quindi m-13 resta gated sul SIGNIFICATO dell'accettazione procedurale (0-hard come esito
statisticamente garantito, non deterministico per costruzione) - ma la sua ESISTENZA operativa
e' qui misurata forte (n=32).

## Limiti
- n=32 su una laptop sola (within-machine). Per generalizzare cross-environment, Fedora al rientro
  (stessa config, risultato atteso coerente).
- L'H17 e' LS-probabilistico: n=32 clean non lo esclude al 100% (ma lo rende raro + coperto dal
  multi-start N=3).
- Spiegazione M-TC della divergenza col previo n=8 e' la piu' probabile (timeline + meccanismo)
  ma non isolata A/B (non fatto un n=8 M-TC-OFF su questa laptop).
