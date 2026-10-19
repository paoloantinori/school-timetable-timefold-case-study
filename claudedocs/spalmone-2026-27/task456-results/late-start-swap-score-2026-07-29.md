# G11 late-start: delta di punteggio di uno swap di ancoraggio (2026-07-29)

TASK-450/451 follow-up. Domanda: se la mossa che anc ora il venerdi di G11 alle 8
esiste, e' abbondante ed e' generabile, perche' il solver non la fa? Ipotesi (maintainer/Fedora):
il PUNTEGGIO la rifiuta (gain M18 +50, pay M32 fino a 500x2 + S13). MISURATO col motore
vero (`LateStartSwapScoreTest`: carica il cert export, seeda il template M32, applica i 2
changeMove, scorea prima/dopo con breakdown ConstraintMatch). **IPOTESI REFUTATA.**

## Tre righe (la risposta)

1. Il blocco e' HARD (capacity room/teacher a slot0), NON medium. M32 costa solo -50 e
   M18 GUADAGNA +50 (si compensano in medium); il costo dominante e' H2 room conflict +
   H1 teacher conflict (hard -2000/-3000).
2. ZERO anchor conflict-free su 417 candidati value-range-legali (caso 1 e 2 identici):
   nessuna lezione G11 puo' andare a venerdi slot0 senza collidere con il docente o la
   stanza di un altro gruppo gia' li'. Il "late start" e' FORZATO dalla saturazione dello
   slot0 (08:00), non dal punteggio.
3. Corollari: il fix non e' uno swapMove (slot0 vuoto, niente con cui scambiare) ma 2
   changeMove attraverso un intermedio peggiore; e il candidato concreto di Fedora
   (T04) e' addirittura FUORI value-range a slot0. Saturazione strutturale alle
   08:00 del venerdi.

## Tabella 1 - caso 2026-11-20 (Friday 4h, slots [1,2,3,4], tail Matematica T08 @slot4)

Anchor misurato: Orientamento 2026-09-16 @slot2 -> A-date slot0 ; T08 -> slot2.
417 candidati value-range-legali, **0 conflict-free**.

| constraint (delta dopo-prima) | hard | medium | soft |
|---|---|---|---|
| H2 - Room conflict | -2000 | 0 | 0 |
| H1 - Teacher conflict | -1000 | 0 | 0 |
| S1 - Minimize teacher gaps | 0 | 0 | -120 |
| M32 - Template deviation | 0 | -100 | 0 |
| M18 - Day start from first open slot | 0 | +50 | 0 |
| S11 - Student group room affinity | 0 | 0 | -20 |
| S13 - Weekly stability | 0 | 0 | -4 |
| S12 - Weekly subject presence | 0 | 0 | -1 |
| **TOTALE** | **-3000** | **-50** | **-145** |

## Tabella 2 - caso 2026-09-18 (Friday 5h, slots [1,2,3,4,5], tail Informatica @slot5)

Anchor misurato: Orientamento 2026-09-16 @slot2 -> A-date slot0 ; Informatica -> slot2.
416 candidati value-range-legali, **0 conflict-free**.

| constraint (delta dopo-prima) | hard | medium | soft |
|---|---|---|---|
| H1 - Teacher conflict | -1000 | 0 | 0 |
| H2 - Room conflict | -1000 | 0 | 0 |
| S1 - Minimize teacher gaps | 0 | 0 | -120 |
| M32 - Template deviation | 0 | -100 | 0 |
| M18 - Day start from first open slot | 0 | +50 | 0 |
| S11 - Student group room affinity | 0 | 0 | -10 |
| S13 - Weekly stability | 0 | 0 | -4 |
| **TOTALE** | **-2000** | **-50** | **-134** |

## M32 cella template (la variante richiesta)

L'ipotesi M32-domina NON regge, quindi la variante " conserva la cella?" e' moot, ma
riporto comunque cio' che il motore mostra: l'anchor B (Orientamento) era ON cella template
PRIMA e OFF DOPO (slot2 di un altro giorno -> slot0 del venerdi: weekday E slot cambiano),
quindi M32 -100 e' legitimato (cella davvero cambiata, non il caso-defect di "cambio solo
data che M32 penalizza per sbaglio"). A (Matematica/Informatica) era gia' OFF-template
prima e dopo. Nessun difetto di M32 emerge qui.

## METODO / COME RIPRODURRE

`LateStartSwapScoreTest` (env `PLANNER_TEST_LATE_SWAP=1 PLANNER_TEST_LATE_SWAP_EXPORT=<cert>
PLANNER_TEST_LATE_SWAP_ADATE=2026-11-20`). Carica il cert export, seeda i fatti medium-tier
(quota + expected-hours + template M32 via `WeeklyTemplatePlanner.buildReference`), scorea
PRIMA, scannerizza i candidati G11 legali a slot0 con pre-filter conflitti H1/H2/H3,
applica i 2 changeMove col primo conflict-free (o il primo value-range-legale se nessuno e'
conflict-free), scorea DOPO, stampa il delta per vincolo + lo stato cella template.

Snapshot a UNA traiettoria (il cert), trend non adozione; la LS e' stocastica ma i delta
qui sono deterministici (scoring puro, non solve).

## LETTURA PER maintainer/FEDORA

- La "correzione chirurgica" ipotizzata (ritoccare M32) non risolverebbe il late start: M32
  non e' il blocco. Il blocco e' che slot0 (08:00 venerdi) e' saturo di docenti/stanze di
  ALTRI gruppi -> G11 non ha una room+teacher libera li'.
- Questo e' coerente con l'inferenza golden di Fedora (le 44 sovrapposizioni tutte di
  giovedi, slot di picco contesi): la capacita' ai picchi di inizio giornata e' il
  vincolo strutturale, non il modello di punteggio.
- Per "raddrizzare" G11 servirebbe capacita' (piu' docenti/stanze disponibili alle 08:00
  del venerdi) o rilassare il calendar/VRP, non ritoccare M32.
