# VERDICT TASK-496 (G14 fill exemption) - NEUTRO su G14: l'esenzione e' rimovibile come pulizia (laptop, 2026-08-16)

> laptop MISURA, ratifica di maintainer/Fedora. Flag
> planner.constraints.g14-fill-exempt.enabled DEFAULT TRUE (= esenzione attiva =
> comportamento ratificato; opt-out con =false include G14 nel fill family).
> Domanda del task: l'esenzione e' ancora necessaria ora che la quota G14 e' il
> part-time reale 120h (TASK-462)?

## Setup

- A/B appaiato baseline-golden.sh, source=golden default, tutto ratificato ON
  (multi-start, MT-LS, skip-doomed-room, pool-cap) in ENTRAMBE le braccia.
- Control ab-exempt-run1: esenzione ON (default). Treatment ab-included-run1:
  -Dplanner.constraints.g14-fill-exempt.enabled=false (G14 entra in
  M20/M33/M20.2/pool-cap). Banner verificati per braccio.
- NOTA OPERATIVA (documentata per il runbook): la prima coppia di run e' stata
  SCARTATA: due driver baseline-golden concorrenti (il primo era nel loop di
  readiness quando ho rilanciato il secondo) hanno doppio-importato il calendario
  (15.349 lezioni invece di 12.494, -5,8M hard). NON un difetto dell'app: artefatto
  da driver doppio. Profili wiped e rilancio a driver singolo: import=1 per braccio,
  conteggio lezioni sano.

## Risultati (coppia pulita)

| metric | exempt (control) | included (treatment) |
|--------|------------------|----------------------|
| **hard finale** | **0** | **0** | gate binario superato |
| medium / soft | -961.552 / +6.007 | -991.777 / +8.634 | -30k (varianza: banda stasera -957..-992 fra run good) |
| wall | 55 min | 41 min | varianza LS |
| full-day TOTALE | 90,0% | 90,2% | in banda |
| **full-day G14** | 26/29 = 90% | **27/29 = 93%** | MIGLIORA |
| **deficit G14** | 3 giorni / 7h | **2 giorni / 6h** | MIGLIORA |
| empty expected days G14 | 1 | 1 | invariato |
| M20 totale | -95,0k | -100,0k | +5k (non-G14: varianza) |
| M33 totale | -11,5k | -12,0k | +0,5k |
| M20.2 totale | -2,5k | -7,0k | +4,5k (1 empty day in piu', NON su G14: G14 ne ha 1 per entrambe) |

## Lettura

1. **Il gate del task e' superato in tutti e tre i criteri**: hard 0 entrambi;
   full-day G14 in banda (anzi migliora 90->93%); NESSUN nuovo residuo M20/M33 su
   G14 (il deficit G14 scende 7h -> 6h). Il +30k medium totale vive su altri
   gruppi ed e' dentro la banda di varianza dei run good di stanotte.
2. **PERCHE' funziona**: con la quota TASK-462 (120h part-time gio/ven), il target
   M20 di G14 e' PICCOLO e RAGGIUNGIBILE (30 giorni attesi a 3-5h): il solver
   incluso riempie G14 come qualunque gruppo e l'esenzione non protegge piu' nulla.
   Il commento storico ("under-fill BY NATURE") descriveva il dato sbagliato pre-462.
3. **Raccomandazione per maintainer: RIMUOVERE l'esenzione come pulizia** (il flag resta
   DEFAULT TRUE finche' maintainer ratifica la rimozione: la forma opt-out consente il
   flip senza codice). La rimozione semplifica il modello (una sotto-condizione in
   meno) e il KPI G14 guadagna. Alternativa conservativa: lasciare il flag cosi'
   (esenzione di default) e dichiararla nel workbook come proprieta': costoso in
   complessita' per zero beneficio misurato.

## Artefatti

- ab-exempt-run1/, ab-included-run1/ (capture completa, stream preservati).
- Codice: fillExempt() nei 6 siti fill-family + G14FillExemptionTest (3/3).
- La coppia SCARTATA double-driver NON e' conservata nei risultati (log nel driver
  log): l'artefatto e' documentato qui per il runbook (lezione: mai rilanciare un
  driver baseline-golden senza pkill del precedente).
