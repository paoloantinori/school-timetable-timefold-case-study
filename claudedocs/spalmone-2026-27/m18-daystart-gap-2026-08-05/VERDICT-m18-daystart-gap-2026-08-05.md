# VERDICT: M18 day-start gap (run vs golden), 2026-08-05

**Domanda (maintainer):** il gap M18 day-start (l'unico gap medium significativo run-vs-golden, vedi
`model-gap-characterization-2026-08-05.md`) e' un problema di MODELLO (target M18 troppo
forte/debole) o di RICERCA (AnchorRepair non chiude tutti i late-start)?

**Risposta breve: NESSUNO dei due, nel senso atteso. E' una COMPOSIZIONE/TRADEOFF, e converge
con l'H17.** Il target M18 e' corretto e raggiungibile (il golden lo soddisfa sulle celle del
gap); AnchorRepair chiude cio' che e' chiudibile; il residuo e' un tradeoff medium VOLONTARIO
(peso M18 calibrato 250->50 da TASK-405 proprio per non rompere la ricorrenza) concentrato sugli
stessi colli di bottiglia strutturali dell'H17 (G11 mercoledi'). La leva non e' il modello ne'
il raffinamento di AnchorRepair: e' la composizione di quei gruppi, condivisa con la cura dell'H17.

**Metodo (read-only, su capture esistenti):** confronto (group, date)-level del run ratificato
`task469 ab-cps-on-run1` contro `task452 paired-golden-run1`, con il golden come oracolo di
raggiungibilita' di slot-0. Dump raw completo (per-data, per-cella) in `raw-m18-gap-analysis.txt`.

## La misura esatta

| metrica | run (task469) | golden (task452) | gap |
|---------|--------------|------------------|-----|
| M18 penalty (analysis.json) | -2600 | -650 | **-1950** |
| M18 matches (giorni in ritardo) | 43 | 13 | **+30** |
| day-start anchored rate (kpi-daystart) | 98,06% (2166/2209) | 99,41% (2196/2209) | -1,35pp |
| offset-units totali (penalty/50) | 52 | 13 | +39 |

Il gap e' soprattutto di CONTEGGIO (30 giorni in ritardo in piu'), non di entita' per evento
(offset medio ~1,2 slot nel run vs ~1,0 nel golden). Riferimento: il golden modale del CLAUDE.md
e' 99,86% (questa capture importata misura 99,41%; la differenza e' cattura-vs-riferimento, non
un'incoerenza). Il run 98,06% qui e' peggiore del run TASK-451 (99,9%): parte del gap e' varianza
run-to-run (la LS e' NON_REPRODUCIBLE), parte e' concentrazione strutturale (sotto).

## Per-group breakdown (run late-starts, golden come oracolo)

Categorizzazione dei late-start del run: **A** = golden anche lui in ritardo (pratica accettata,
target corretto); **B** = golden puntuale a slot-0 (slot-0 RAGGIUNGIBILE, il run manca = miss di
ricerca/composizione).

| gruppo | run late | B search-miss | A practiced | lettura |
|--------|---------:|--------------:|------------:|---------|
| G13 | 17 | 6 | 11 | 11 praticati dal golden (G13 ha regole sue); 6 miss |
| G11 | 9 | 8 | 1 | quasi tutti miss (cella strutturale H17) |
| G09 | 8 | 7 | 1 | quasi tutti miss |
| G04 | 7 | 6 | 1 | quasi tutti miss |
| G08 | 4 | 3 | 1 | misto |
| G12 | 3 | 2 | 1 | quasi tutti miss |
| altri (G14, 1/2/G10, 1/G05, 1/G07) | 8 | 0 | 8 | tutti praticati (1 ciascuno) |

**Lettura:** i late-start "praticati" (A, ~24) sono distribuiti uniformemente (1 per gruppo), sono
la pratica della scuola e il modello li accetta correttamente (medium, non hard). I late-start del
**gap** (B, ~32) si concentrano su 5-6 gruppi: G13, G11, G09, G04, G12. Non sono
random: sono sistematici su quei gruppi.

## Dove si concentra (per weekday e per cella strutturale)

B search-miss per weekday: Wed 9, Thu 7, Fri 7, Tue 5, Mon 4. Le celle top:
**G11 mercoledi' (6)**, G09 venerdi' (4), G04 martedi' (3).

**Convergenza con l'H17:** le 6 date G11 mercoledi' in ritardo (2026-10-28, 11-04, 11-11,
11-18, 11-25, 12-02; run inizia a slot 1 / 9am, golden a slot 0 / 8am) cadono nello STESSO
(group, weekday) del residual strutturale H17 bad-seed (G11 mercoledi', finestra 09-16..11-18
nel VERDICT holerepair). Non sono lo stesso evento (qui e' uno shift di 1 slot a inizio giorno,
li' un buco @10/@11 a meta' giorno), ma same cella, same radice: la composizione di G11 il
mercoledi' e' difficilmente compattabile, e questo produce sia il late-start (M18) sia il buco
(H17).

## Modello vs Ricerca: il verdetto

**1. Non e' un problema di TARGET del modello.** Il target M18 ("inizia dal primo slot
calendario-aperto") e' corretto e RAGGIUNGIBILE: il golden lo soddisfa sulle celle del gap (parte
da slot-0 dove il run parte in ritardo). Il golden paga M18 solo 13 volte (pratica accettata).
Quindi M18 non chiede l'impossibile e non e' troppo rigido: cambierlo sarebbe un cambio di modello
ingiustificato (e boccerebbe la direttiva golden-first, perche' il golden stesso lo soddisfa).

**2. Non e' un fallimento di AnchorRepair.** AnchorRepair (TASK-433, DEFAULT ON) e' il pass
deterministico che chiude i late-start, ed e' load-bearing (1-3pp day-start per la sintesi).
Chiude i late-start chiudibili. I 30 residui NON sono "AnchorRepair ha mancato un buco
chiudibile": sono casi in cui spostare la lezione a slot-0 costerebbe piu' medium altrove
(M20 fill, M22 compattezza, M32 template) di quanto M18 risparmi (50/offset). Il solver li accetta
RAGIONEVOLMENTE.

**3. E' un TRADEOFF di peso, GIA' CALIBRATO.** Il peso M18 (50/offset) e' stato tarato da TASK-405
A/B (250->50): a 250 day-start 96%+ ma ricorrenza (M32) sotto soglia (autorita': riga M18 del
`CLAUDE.md`; evidenza `task405-results/`). Quindi M18 NON e' un peso flat (a differenza di
M32/M34/M35/M-TC): e' una leva VIVA che scambia day-start contro ricorrenza. Il 50 attuale e' il
bilancio voluto. Recuperare i 30 late-start alzando M18 romterebbe la ricorrenza (KPI di guardia).
La "cura" qui e' una decisione di POLICY (day-start vale piu' della ricorrenza?), non un refinement
di ricerca.

**4. La radice e' la COMPOSIZIONE dei gruppi collo di bottiglia, condivisa con l'H17.** Il gap si
concentra su G11 mercoledi' e G13/G09 venerdi', le stesse celle dell'H17 strutturale. Una
composizione piu' pulita di QUEI gruppi chiuderebbe insieme H17 e M18. Il multi-start (TASK-483,
ratificato) lo mitiga gia' via selezione del bacino (il run TASK-451 era al 99,9% day-start); la
cura strutturale (composizione di G11/G13/G09) e' il filone TASK-481/480.

## Raccomandazione (gated maintainer; NESSUNA adozione)

- **NON toccare il target M18** (corretto, golden-achievable; cambierlo violerebbe golden-first).
- **NON alzare il peso M18 in isolamento** (gia' calibrato 250->50 da task405; rompe la ricorrenza).
- **Non raffinare AnchorRepair per M18** (non manca buchi chiudibili; il residuo e' tradeoff).
- **La leva e' la composizione dei gruppi collo di bottiglia** (G11 mercoledi', G13/G09
  venerdi'): il filone TASK-481 (CompositionHoleCloser, scaffold corretto default-OFF) / TASK-480
  (multi-start via bacino). E' la STESSA cura dell'H17: un intervento composition-aware su quelle
  celle chiude entrambe le tensioni (hard H17 + medium M18) insieme.
- **Decisione di policy aperta per maintainer**: il tradeoff day-start vs ricorrenza e' attualmente
  sbilanciato su ricorrenza (peso 50). Se la scuola preferisce piu' day-start, e' un cambio di
  POLICY (peso), non di correttezza. Ma e' gated e secondario: il gap reale e' la composizione.

## Dove guardare

- Dump raw per-data/per-cella: `m18-daystart-gap-2026-08-05/raw-m18-gap-analysis.txt`.
- Capture run: `task469-results/ab-cps-on-run1/{analysis.json,kpi-daystart*.txt,lessons.txt}`.
- Capture golden: `task452-results/paired-golden-run1/{analysis.json,kpi-daystart*.txt,lessons.txt}`.
- Caratterizzazione model-gap genitore: `model-gap-characterization-2026-08-05.md`.
- Autorita' peso M18: `CLAUDE.md` riga M18 (calibrazione 250->50, TASK-405); evidenza `task405-results/`.
- Convergenza H17: `task284-results/VERDICT-holerepair-characterization-2026-08-05.md` (G11 mercoledi').
