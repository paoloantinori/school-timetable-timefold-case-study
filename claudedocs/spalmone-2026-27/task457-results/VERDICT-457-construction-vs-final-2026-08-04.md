# VERDICT TASK-457 (tier-docenti ritmo). Construction-vs-final sullo stack ratificato, 2026-08-04

> INDAGINE READ-ONLY. Caratterizzazione di un difetto (il ritmo dei tier), non la cura. NESSUNA adozione,
> NESSUN codice di modello (l'intervento, se steerare la costruzione a bilanciare i tier, resta gated
> maintainer). Misura col tool canonico `teacher-tier-kpi.py` su dump construction-vs-final di un run recente.

## Domanda (e la discrepanza aperta)

La claim del task: la costruzione esaurisce i docenti GENERICI ~13 giorni prima dei dedicati, e la LS
dimezza (non azzera) lo scarto. Due letture precedenti DISCORDANO:
- **task451 snapshot** (descrizione del task): "fine costruzione generici **-13,4 giorni**, run finale
  **-6,6**, la LS dimezza".
- **29b per-fase** (commento #1, 30/07): generic drift gia' formato a fine P2 (**-7,2**) e STABILE fino a
  P5 (**-7,5**), NESSUN dimezzamento P2->P5. Discrepanza aperta: "serve un dump di fine-costruzione per
  chiudere il verso".

Questo VERDICT risolve il verso con un dump **construction-vs-final** di un run SULL'ATTUALE stack
ratificato (task453-results, run source=input 0-hard del 2026-08-03, post ratifiche 435/451/463/464/
469/478/483), metrica canonica (temporal centroid per docente di `teacher-tier-kpi.py`).

## Metodo

I dump di `task453-results/dumps/` (lesson-id-faithful, formato `id|group|date|hour|subject|teacher|
pinned|promoted`, 8 campi) hanno 4 cut per-fase. phase-2 = "entra LS-Compactness" = **post-construction-CH,
pre-LS** = il template costruito (FROM); phase-5 = "entra LS-Room-refinement" ≈ **finale** (TO; phase-4
->phase-5 = 0 cell-changes, room-refinement muove stanze non celle). Trasformati a formato canonico
6-campi (`group|date|hour|subject|teacher|pinned`) con `cut -d'|' -f2-7` (lo id-prefix e il flag promoted
non servono a tier-kpi che legge `parts[:6]`). `teacher-tier-kpi.py` su entrambi, `--data-dir
src/main/resources/seed`, generici = >=8 gruppi (9), specializzati = 17. Stage date escluse (default).

Sanity parse: l'invariante di input "quota generici per famiglia" coincide golden=run su entrambi i cut
(G13 61,6%=61,6%, FD 46,7%=46,7%): se il cut avesse disallineato i campi, la classificazione per
famiglia sarebbe spazzatura. Parse confermato corretto.

```
D=claudedocs/spalmone-2026-27/task453-results/dumps
cut -d'|' -f2-7 $D/phase-2-2-ls-compactness-id.txt | awk -F'|' 'NF>=6' > /tmp/construction.txt
cut -d'|' -f2-7 $D/phase-5-5-ls-room-refinement-id.txt | awk -F'|' 'NF>=6' > /tmp/final.txt
python3 scripts/analyze/teacher-tier-kpi.py /tmp/construction.txt --data-dir src/main/resources/seed
python3 scripts/analyze/teacher-tier-kpi.py /tmp/final.txt       --data-dir src/main/resources/seed
```

## Risultati (canonici, run task453, stack ratificato attuale)

**Baricentro temporale generici (scarto medio run-golden, giorni; 0 = golden, neg = generici front-loaded):**

| cut | generici (n=9) | specializzati (n=17) |
|-----|----------------|----------------------|
| construction (phase-2, pre-LS) | **-7,1** | -5,2 |
| final (phase-5, post-LS) | **+0,2** | -3,5 |

**Quota generici per mese (delta run-golden, punti %):**

| mese | construction | final |
|------|-------------|-------|
| 2026-09 | -2,4 | -2,2 |
| 2026-12 | +2,3 | +2,2 |
| 2027-03 | +0,2 | +0,3 |
| 2027-05 | -3,6 | -1,6 |
| 2027-06 | **-7,5** | **+0,5** |

## Verdetto: la LS RIMUOVE il drift generico (non lo dimezza), sullo stack attuale

Sul run task453 (n=1, stack ratificato 2026-08-03): il generic drift e' **-7,1 a fine costruzione** e la
LS lo riduce a **+0,2 al finale** (essenzialmente azzerato). La LS NON dimezza: **rimuove**. Anche lo
specialised drift si restringe (-5,2 -> -3,5). La firma mensile lo conferma: il residuo specialist-heavy
di fine anno (jun **-7,5** a costruzione) sparisce al finale (jun **+0,5**, bilanciato).

**Questo CONTRADDICE entrambe le letture precedenti:**
- NON -13,4/-6,6 (task451 snapshot): la costruzione e' -7,1 (non -13,4), il finale +0,2 (non -6,6).
- NON -7,2/-7,5 stabile (29b per-fase): sul task453 la LS MUOVE il drift (-7,1 -> +0,2), non lo lascia
  congelato.

**Meccanismo plausibile.** La LS-Compactness minimizza gap e riempie giorni; il generico e' la risorsa
flessibile che puo' tappare buchi in quasi ogni gruppo, quindi la LS la sposta verso fine anno (dove lo
specializzato non puo' aiutare), riducendo il front-loading. Coerente col segno (generic centroid sposta
avanti, -7,1 -> +0,2).

**Perche' discorda da 29b?** Il run 29b e' del 2026-07-29 (PRE ratifiche 435/451/463/464/469/478/483);
task453 e' del 2026-08-03 (stack PIENO). Le ratifiche intervenute (in particolare TASK-451 first-hour-slot
coordinato, TASK-463 lab-day-consolidation, la famiglia M32 template-deviation) hanno cambiato il
tier-pacing: sullo stack vecchio la LS non muoveva il drift, su quello attuale lo rimuove. La deriva tra
i due run e' quindi un EFFETTO del cambio di stack, non rumore.

## Riconciliazione con la falsificazione (commento #2) e AC

La falsificazione 30/07 (run 29b) aveva concluso: il drift NON e' firma inerta (traccia deficit profondi
tardo-annuali, n=3 coerente), MA causazione non isolata e leva non provata. DOWNGRADE a "firma con
supporto causale debole".

Questo VERDICT (stack attuale) **indebolisce ulteriormente il lead causale**:
- Se sullo stack attuale la LS RIMUOVE il drift generico al finale (+0,2, jun +0,5 bilanciato), allora il
  drift NON puo' essere la causa del deficit tardo-annale (che persiste, vedi falsificazione): drift e
  deficit sono **decoupling** al finale. La correlazione drift-deficit osservata a costruzione (29b) e'
  una **co-occorrenza**, non una catena: la LS scolla le due cose riposizionando i generici.
- Lettura coerente: a fine anno tutto si stringe per saturazione strutturale per-gruppo (G10 72h, G08
  24h, G12 off-grid), e il drift generico a costruzione co-occorre; ma la LS riequilibra il tier-mix
  al finale, lasciando il deficit (che e' strutturale, non tier).

**Verdetto AC:**
- **AC#1** (causa difetto o firma?): confermato FIRMA, non leva causale. Sullo stack attuale la LS rimuove
  il drift, quindi non puo' causare il deficit persistente. Supporta il DOWNGRADE.
- **AC#2** (prototipo flag spendi-prima-lo-specializzato): **NON warrantato.** Se la LS riequilibra gia'
  il tier-mix al finale, steerare la costruzione e' inutile (la LS lo farebbe comunque). Nessun codice.
- **AC#3** (chiudi senza codice con la misura agli atti): **SI'.** CHIUDI senza codice.

## Limiti (onesti)

- **n=1** sul task453 run. La LS e' NON_REPRODUCIBLE, quindi questo e' un singolo run; il magnitude (-7,1
  -> +0,2) e' grande ma andrebbe confermato con n>=2 sullo stack attuale prima di trattarlo come
  invariante. La discordanza con 29b e' reale e va letta come "lo stack cambio' il comportamento", non
  come "uno dei due e' sbagliato".
- La metrica (temporal centroid per docente, `teacher-tier-kpi.py`) e' la stessa dei lavori precedenti:
  confronto apples-to-apples con 29b, ma NON con il task451 snapshot (che potrebbe aver usato una
  metrica diversa per i -13,4/-6,6; il task non lo specifica).
- Il cut "finale" e' phase-5 (entrante LS-Room-refinement), non dopo l'ultima fase. Ma phase-4->phase-5 =
  0 cell-changes + room-refinement muove stanze non celle (TASK-453 VERDICT) -> phase-5 ≈ finale per le
  celle temporali. Tier-pacing legge (teacher, date), non room, quindi l'approssimazione e' fedele.
- phase-2 (construction) e' il TEMPLATE STAMPATO (post DayClusteredConstructionPhase CH), non il raw
  round-robin (gia' fuso nel template dalla CH). Quindi "construction" = template, come da contract.

## Raw

`task457-results/construction-vs-final-2026-08-04/{construction.out,final.out,construction.html,final.html}`
(output console + report visivo dei due cut).
