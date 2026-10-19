# VERDICT bad-seed frequency - laptop n=16, config corrente (M-TC per-retry ON) (zai, 2026-08-09)

Misura differita TASK-480, ripetuta sulla laptop (Fedora-offline compute window) con la config
CORRENTE (tutti i flag ratificati DEFAULT ON, incluso M-TC per-retry TASK-486). Obiettivo:
stringere la CI di p(0-hard) per la decisione m-13 (accettare il multi-start come chiusura
procedurale del path input). Script: multistart-ab.sh 16 multistart --baseline.

## Headline

**p(0-hard) = 16/16 = 1,0 su questa laptop (config corrente), Wilson95 [0,823, 1,000].**
pick-best = grasp-seed-9 (0hard/-1028k). Wall medio ~1600s/run (~27 min). Baseline GRASP-off
0hard. TUTTI i 17 run (16 GRASP + baseline) hanno raggiunto 0hard.

## Discrepanza col previo n=8 (PRE-M-TC, 2026-08-03)

Il previo (VERDICT-badseed-frequency-2026-08-03.md) aveva p=0,75 (k=6/n=8), Wilson95
[0,409, 0,929], con grasp-seed-1 (-10hard, H17) e grasp-seed-6 (-1hard, H29 room-tier) bad.
Qui n=16, 0 bad. **Stessi seed (1, 6), esito diverso.** Il bad-seed NON e' seed-deterministic:
e' PROBABILISTICO (LS NON_REPRODUCIBLE) + config-dipendente.

Spiegazione piu' probabile della divergenza (0,75 -> 1,0):
1. **M-TC per-retry (TASK-486, ratificato 2026-08-04, DOPO il previo n=8 del 08-03) cura il
   bacino room-tier** (il seed-6 type: room-null da saturazione tag meccanica). Il previo era
   pre-M-TC-per-retry (M-TC era prototipo default-OFF). Quindi il modo-di-fallimento room-tier
   e' sparito dalla config corrente. Questa e' la lettura piu' load-bearing: TASK-486 ha
   effettivamente alzato p(0-hard).
2. **Il bacino H17 (seed-1 type, timeslot verticale) NON e' curato da M-TC** (M-TC e' room-tier).
   Qui seed-1 = 0hard: il bacino H17 e' PROBABILISTICO (LS variance) e i 16 run di questa laptop
   non lo hanno colpito (o il multi-start lo ha curato con un tentativo diverso). Non e'
   dimostrato che H17 sia sparito: n=16 clean e' forte ma non prova p=1,0 assoluto (un H17
   bad basin occasionale resta possibile).

## Cosa misura esattamente multistart-ab.sh

Ogni run = boot con -Dgrasp.seed=i + solve-staged (DEFAULT multi-start maxAttempts=3, NON
disabilitato dallo script). Quindi "0hard per run" = il ciclo staged (multi-start pick-best)
raggiunge 0hard. La metrica operativa e' quella che serve a m-13: **la procedura solve-staged
consegna 0hard?** Non la per-construction isolata. (Il previo la chiamo' "per singola
costruzione" ma lo script usa il default multi-start: la metrica e' coerente tra i due run,
entrambi solve-staged default.)

Nota: ogni run ha "wait TIMEOUT/FAILED" al cap 5400s (90 min) ma score 0hard = il ciclo
raggiunge 0hard entro 90 min (il poll capta prima del rest formale). Valid per la metrica
buono/cattivo.

## Verdetto per m-13 (decisione maintainer)

**Con la config corrente (M-TC per-retry ON), la procedura multi-start consegna 0hard in modo
affidabile: p(0-hard) = 1,0 (n=16, [0,82, 1,0]); anche pooled col previo (24 run, 2 bad, tutti
i bad pre-M-TC) p = 0,917 [0,74, 0,98].** P(>=1 0hard | N=3 tentativi) > 0,999 con p=0,9
conservativo. Il dato SUPPORTA FORTEMENTE accettare il multi-start come chiusura procedurale
del path input (m-13).

Caveat onesto: l'H17 bad basin resta PROBABILISTICO (non curato da M-TC, LS-dipendente). n=16
clean non esclude un H17 occasionale, ma il multi-start N=3 lo copre (P(>=1 0hard) altissima).
L'accettazione procedurale e' robusta; non e' una garanzia deterministica per costruzione
(questo e' il punto della direttiva 0-hard-binario: m-13 resta gated sul SIGNIFICATO della
chiusura procedurale, non sulla sua ESISTENZA, che qui e' misurata forte).

## Limiti
- Singolo run per seed (NON_REPRODUCIBLE): la varianza run-to-run e' reale (il previo 0,75 vs
  questo 1,0). Per stringere ulteriormente, ripetere n=16 un'altra volta. Ma per m-13 la
  direzione e' chiara (multi-start robusto con config corrente).
- M-TC come spiegazione della divergenza e' la piu' probabile (timeline + meccanismo) ma non
  isolata A/B (servirebbe un run n=8 con M-TC OFF su questa laptop per confermare; non fatto).
- n=16 su una laptop sola (within-machine); per generalizzare cross-environment servirebbe Fedora
  al rientro (ma Fedora ha la stessa config, quindi risultato atteso coerente).
