# TASK-459 dossier di costruzione: le firme bad, il dato T-minus, la variabilita' (laptop, 2026-08-15)

> META-TASK 459: dossier unico dei difetti che nascono in costruzione. Questa bozza
> consolida cio' che il census single-construction n=19 (2026-08-14) ha prodotto,
> piu' il dato T-minus della notte 14/08 (analisi Fedora sugli stream live, non
> riproducibile dai raw committati: il driver di allora buttava via i log, fix a1f8728b).
> laptop raccoglie; le conclusioni restano di maintainer/Fedora.

## 1. Le 5 firme bad censite (n=19: 13 good / 6 bad, p=0.684 Wilson95 [0.460, 0.846])

| # | firma | seed osservati | meccanismo | puntatore raw |
|---|-------|----------------|------------|---------------|
| 1 | **H17 -9/-10 su G11** | 1, 9 | buchi interni tutti su G11 (bacino strutturale TASK-481/482, riprodotto DUE volte) | `task452-results/badseed-frequency-input-2026-08-14/grasp-seed-{1,9}/kpi-holes.txt` |
| 2 | **H29 -1 room-null** | 6 | saturazione tag monostanza (acconciatura), NASCE nella fase stanze (vedi T-minus: unico non-detaccabile al RELEASE) | `grasp-seed-6/score.txt`, analysis |
| 3 | **H0 -32 boxed + H17 -6** | 12 | 16 lezioni UNPLACED che l'intero ciclo non colloca mai (worst case -38hard) | `grasp-seed-12/{score.txt,analysis.json}` |
| 4 | **H17 -7 G13 mercoledi' slot 12** | 17 | giorno-5h spezzato 8-11+13 dove il golden fa 8-12 contigue (stessa famiglia del late-start G13 venerdi') | `grasp-seed-17/kpi-holes.txt` + addendum VERDICT-extension |
| 5 | **H28 -32 no-teacher-return G13** | 18 | A-B-A del docente nella materia co-docente (T10, T10, T15, T10 su Lavorazioni meccaniche, 14 date) | `grasp-seed-18/lessons.txt` + addendum |

Convergenza G13: le firme 4 e 5 (le nuove, n=1 ciascuna) sono TUTTE su G13, che e'
anche il gruppo del late-start cronico (13 celle ven nel confirm run). Oltre a G11,
G13 e' il candidato "gruppo fragile" del path input. Le firme 4 e 5 sono DA RIPRODURRE
(Filone B stanotte: re-run seed 17 e 18 con stream preservati).

## 2. Il dato T-minus (analisi Fedora 14/08, stream live, NON nei raw committati)

- Al confine post-RELEASE (ingresso fase stanze) i GOOD hanno hard esattamente **-9404**
  = la baseline room-null pura (tutte le lezioni collocate, nessuna stanza ancora).
- **5/6 bad hanno residuo PIU' NEGATIVO del -9404 al confine**: la loro firma e' gia'
  presente PRIMA delle stanze. Separazione pulita, ZERO falsi positivi su n=19.
- Eccezione: **seed-6 (H29)** nasce NELLA fase stanze: al RELEASE il suo hard e'
  baseline-puro, la firma appare dopo. Coerente col meccanismo (saturazione tag e'
  un fenomeno room-tier).
- Conseguenza operativa (diventa task backlog, Filone C.2): skip-B-ROOM-on-doomed-attempt.

LIMITI: n=19 per il lato good, 6 bad; le firme 4/5 sono n=1; il numero -9404 e' della
sessione Fedora (da verificare sugli stream del filone B quando saranno preservati).

## 3. Variabilita' di costruzione (boxed a CH-end, numeri mandato)

La coda di arrivo della CH varia 9 / 51 / 82 boxed a parita' di configurazione: la
costruzione GRASP randomizzata ha una dispersione grande del punto di partenza, e la
LS + repair recuperano quasi tutto tranne la firma del seed. Nessuna correlazione
boxed-CH-end vs esito trovata dal filone 2 della scorsa notte (badseed-structure-
correlation-2026-08-14.md: ipotesi early-abort CONFUTATA, good/bad si sovrappongono su
CH-end-hard, LS2-end-hard, iterazioni, wall).

## 4. Sintesi per il dossier originale (late start G11, ritmo tier, buco slot 1)

I tre difetti storici del task e le firme census sono lo stesso fenomeno visto da
angoli diversi: la costruzione puo' lasciare impronte (buchi, stacking, ordine docenti)
che la LS non sempre cura, e la cui SEVERITA' dipende dal seed. Cio' che il census
aggiunge al quadro storico: (a) la probabilita' (1/3), (b) la popolazione di firme
(5 distinte, non una causa unica), (c) il momento rilevabile (T-minus RELEASE, eccetto
room-tier). Le cure mirate per firma restano TASK-481/482 (H17), M-TC (H29), ExactFinisher
(H0-boxed, gia' ratificato); il multi-start resta l'unica copertura trasversale.

## Puntatori

- Census + statistiche: `task452-results/badseed-frequency-input-2026-08-14/VERDICT-extension-n19.md`
- Caratterizzazione firme: addendum nel VERDICT-extension + `task480-results/badseed-structure-correlation-2026-08-14.md`
- Riproduzione firme + traiettorie dense: `task452-results/badseed-frequency-input-2026-08-14/reproduction-2026-08-15/NOTA.md` (Filone B, CONSEGNATO: 6/6 firme riprodotte, tutte n>=2; separazione RELEASE confermata 6/6 con margene 14 e baseline good [-1833,-1809] al posto del -9404 della premessa: la sezione 2 qui sopra va letta con quella correzione)
- Dump per fase (il "momento esatto" di AC#1): resta il veicolo TASK-453
- Proposta operativa derivata: TASK-498 (skip-B-ROOM-on-doomed-attempt, gated, creato 2026-08-15)
