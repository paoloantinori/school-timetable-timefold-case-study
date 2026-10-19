# Filone 2 - Correlazione firma iniziale bad-seed (read-only su dati Filone 1). 2026-08-14.

> Mandato notturno filone 2. Ipotesi da testare: i bad-seed (hard residuo dopo single-construction)
> potrebbero avere una FIRMA a fine construction che li distingue dai good-seed. Se trovata, si puo'
> bloccare il bad-seed SUL NASCERE (early-abort del multi-start invece di farlo girare ~30min).
> ESPLORATIVO read-only sui dati del Filone 1: nessun codice, nessun early-abort implementato.

## TL;DR (verdetto)

**L'ipotesi e' CONFUTATA. Nessuna firma a fine construction separa good da bad.** Testati 4
segnali (CH-end-hard, LS2-end-hard, numero iterazioni LS2, wall-clock): le distribuzioni good/bad
si SOVRAPPONGONO completamente su tutti. Il bacino bad NON e' prevedibile dalla struttura
iniziale. **Early-abort sul nascere NON e' fattibile** con questi segnali. BUTTA l'idea.

**Conseguenza architetturale (conferma indiretta del multi-start):** non potendo predire/prevenire
il bacino bad alla costruzione, la chiusura 0hard DEVE essere "gira il ciclo e pick-best"
(multi-start). Non c'e' scorciatoia early-abort. Il multi-start e' la via GIUSTA, non un ripiego:
e' l'unica opzione quando la firma non esiste.

## Metodo

Usati gli N=12 run GRASP del Filone 1 + baseline (13 run, source=input single-construction;
seed-12 completato al resume mattutino).
Per ognuno: phases.json (score a fine fase), score.txt (final), kpi-holes.txt (H17 signature).
Good = 0hard finale (9: baseline + seed 2,3,4,5,7,8,10,11). Bad = hard residuo (4: seed-1, seed-6,
seed-9, seed-12). Segnali misurati a fine CONSTRUCTION (fase 1 CH Placement) e dopo il primo LS timeslot.

## Misura: 4 segnali testati, tutti non-separabili

### Segnale 1 - CH-end-hard (score hard a fine costruzione, fase 1)
| gruppo | mean | range |
|--------|------|-------|
| GOOD (n=9) | -2161 | [-2344, -1960] |
| BAD  (n=3) | -2229 | [-2321, -2164] |
Sovrapposizione totale. seed-11 GOOD (-2344) e' PEGGIORE di seed-6 BAD (-2164). Non separabile.

### Segnale 2 - LS2-end-hard (dopo primo LS Compactness): il candidato migliore, ancora inutile
Threshold sweep su T (predizione GOOD se LS2-end-hard <= T):
| T | GOOD-correct | BAD-correct | accuracy | false-early-abort (GOOD ucciso) |
|---|--------------|-------------|----------|---------------------------------|
| -1909 | 1/9 | 2/4 | 25% | 1 |
| -1870 | 3/9 | 3/4 | 50% | 1 |
| -1849 | 3/9 | 1/4 | 25% | 3 |
| -1748 | 9/9 | 0/4 | 75% | 4 |
GOOD range [-1909..-1748], BAD range [-1991, -1871, -1864, -1849]: **sovrapposizione profonda**.
Nessun T raggiunge >75% accuracy; ogni T che cattura BAD uccide GOOD (false-early-abort). Il caso
piu' chiaro della non-separabilita': **seed-12 (il WORST basin, -38hard finale: 16 lezioni
UNPLACED + H17) aveva LS2-end-hard -1871, al CENTRO del range GOOD e quasi identico a seed-3 GOOD
(-1870)**. Se anche il peggior bacino del campione e' indistinguibile dai buoni a fine primo LS,
nessun segnale di questo tipo puo' funzionare.

### Segnale 3 - Numero iterazioni LS2 (il ciclo staged re-entra timeslot LS su hard-residuo)
GOOD LS2-iters [1,3,1,1,1,1,1,1,1] (mean 1.2). BAD LS2-iters [2,1,3,3] (mean 2.25).
seed-9 BAD = 3xLS MA seed-2 GOOD = 3xLS (e seed-12 BAD = 3xLS pure). Non separabile.

### Segnale 4 - Wall-clock
GOOD range [1142..2970]s. BAD range [1499, 2449, 2545, 2787]s. seed-6 BAD (1499s) e' piu' VELOCE
di 7 GOOD run; seed-2 GOOD (2970s) e' piu' LENTO di seed-9 BAD (2449s). Non separabile.

## Perche' la firma non esiste (meccanismo, corretto dopo verifica adversariale)

Verifica adversariale (score trace completo dei 3 bad seed allora noti: 1, 6, 9) - il meccanismo e' piu' sfumato di
una prima lettura "H17 latente fino alle stanze":

- **seed-9**: hard STICKS a ~-1990 attraverso TRE iterazioni LS2+Compaction (mai sotto -1988),
  poi P5 Room Refinement lo porta a -9. L'H17 era ATTIVO e non-risolto gia' nelle fasi timeslot.
- **seed-1**: sticks a -1841 through 2 iterazioni, P5 -> -10.
- **seed-6**: sticks a -1864, P5 -> -1.

Quindi l'hard H17/H29 e' ATTIVO nelle fasi timeslot (le iterazioni LS2 non lo risolvono), NON
solo latente. Ma - ed e' il punto - i GOOD seed STICKANNO anche loro a ~-1800/-1830 nelle fasi
timeslot: quel residuo e' il NORMALE pre-room residual che le fasi stanza poi risolvono. La
differenza GOOD-vs-BAD e' SOLO se P5 Room Refinement finisce per chiuderlo, e quello non e'
prevedibile dal valore hard nelle fasi timeslot perche' sono vicini (GOOD -1834 vs BAD -1901,
sovrapposizione profonda). Un segnale "hard stuck through LS2 iterations" c'e', ma non separa
(seed-2 GOOD ha 3 iterazioni stuck come seed-9 BAD).

**In breve (corretto): il "bacino bad" e' determinato da cosa P5 Room Refinement riesce a
chiudere, una proprieta' del landscape di ricerca POST-timeslot-LS, non prevedibile dalla
struttura a fine costruzione. Nessuna feature di construction-end o timeslot-LS-end lo prevede.**

## Raccomandazione: BUTTA

- **Non costruire early-abort.** Nessun segnale separa; qualsiasi soglia uccide GOOD run.
- **Conferma il multi-start come via corretta.** E' l'unica risposta quando la firma non esiste:
  gira il ciclo (costo ~25min), pick-best sul risultato. L'overhead di un bad-basin run e'
  intrinseco (non eliminabile via predizione), quindi il multi-start early-stop-al-primo-0hard
  (gia' implementato, TASK-483) e' gia' la strategia ottimale.
- **Unica via a ridurre il costo bad-basin:** curare il bacino H17 G11 alla radice (territorio
  TASK-481/482), non predire quale costruzione ci cade. Questo e' anche la via al flip #1 sicuro
  (vedi Filone 1 VERDICT): se H17 e' curato deterministicamente, la coda single-construction
  sparisce e early-abort diventerebbe moot (non ci sara' da abortire).

## Limiti

- n=12 (4 bad): piccolo. Ma la SOVRAPPOSIZIONE e' cosi' profonda su tutti i segnali che +run non
  creerebbe una soglia (le distribuzioni si intersecano al centro, non solo alle code): il caso
  definitivo e' seed-12, il peggior bacino (-38hard) con firma LS2-end -1871 identica ai buoni.
- Segnali testati sono quelli catturati (phases.json score per fase). Un segnale piu' fine
  (es. distribuzione boxed per-gruppo a fine costruzione, cella @10 saturazione) richiederebbe
  un dump construction-lessons.txt per-fase NON catturato in questo batch. Da testare solo se si
  vuole una seconda verifica; il meccanismo (il bacino e' deciso da cosa P5 Room Refinement
  chiude, non dalla struttura iniziale) suggerisce che anche quelli probabilmente fallirebbero,
  ma non e' dimostrato su questo batch (caveat onesto, non conclusione).

## Puntatori
- Dati: `task452-results/badseed-frequency-input-2026-08-14/{run}/phases.json` + `score.txt`
- Filone 1 (il batch): `task452-results/badseed-frequency-input-2026-08-14/VERDICT.md`
- Territory della cura H17: TASK-481 (depth-3 shell-game escluso), TASK-482 (Kempe NO)
