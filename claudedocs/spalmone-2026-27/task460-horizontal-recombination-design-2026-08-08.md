# TASK-460 - Design ricombinazione orizzontale (AC#2: approcci + raccomandazione) (zai, 2026-08-08, Fedora offline)

Deliverable di TASK-460 AC#2 ("proposti 2-3 approcci con compromessi e raccomandazione, sul
meccanismo generale"). Costruito sull'evidenza TASK-453 (`VERDICT-erosione-2026-08-03.md`,
WORK 2 chiuso) + le refutazioni TASK-460 + i approach seeds delle note task. Design read-only,
gated maintainer (AC#3), NESSUNA adozione.

## Il meccanismo generale (maintainer 2026-07-29)

"Uno slot resta vuoto perche' tutti i docenti utili sono gia' impegnati altrove." La saturazione
e' **PRODOTTA** dal nostro piazzamento (run: 0 candidati conflict-free al venerdi' 8am vs golden
6,6 docenti liberi/ora; solo lo 0,1% di ore golden senza alternative), non subita. Non e' prezzo
dello swap (lo swap e' hard-illegale), ne' penuria di aule (le aule non esistono ancora nella
fase orari, TASK-446), ne' scarsita' di docenti.

## Cosa TASK-453 ha chiuso (AC#1 "quando/cosa")

WORK 2 verdetto: cell-retention sul MOBILE set (mai-pinnate a entrambi i cut) construction->finale
= **16,6%** (83,4% mosse), concentrate per l'**83% nella fase LS-Compactness** (1449/1744 cell-
changes), dominate dalle **teoriche generaliste mai-pinnate** (Lingua, Matematica, Scienze,
Recuperi). Il template (resemblance 74%) e' **preservato dai PIN** (promoted+authoritative), non
dalle lezioni libere. Insight: la resemblance si alza riducendo il rimescolamento delle
mai-pinnate (M32), non pinnando di piu'.

**AC#1 residuo (link-by-link per lo SLOT-VUOTO, non cell-erosion):** tracciare il bad-seed
G11 mercoli' ore 10 (H17, TASK-478/481) sui cut per-fase, intersecando il dump ID-tagged
(`task453-results/dumps/`) con gli slot bad-seed noti, per chiudere in quale cut i docenti utili
di quella cella si saturano. Tooling pronto (dump ID-tagged); analisi read-only sul dump
esistente, NON un nuovo run. **De-prioritizzato** (vedi raccomandazione): l'asse e' esausto.

## I 3 approcci (AC#2)

### A. Ridurre il rimescolamento delle mai-pinnate in LS-Compactness (soft anchor / M32 piu' forte)
- **Evidenza:** insight diretto TASK-453 (la resemblance si alza riducendo il reshuffle delle
  mai-pinnate). M32 (template deviation, gia' ADOPTED) agisce gia' su questa popolazione.
- **Come:** un 'soft anchor' sulle celle teoriche mai-pinnate (penalita' media sullo spostamento
  dalla cella template, oltre M32 che gia' penalizza la deviation), o un filtro mosse LS che
  preferisce mantenere la cella template per le mai-pinnate.
- **Pro:** agisce sul meccanismo misurato (LS-Compactness reshuffle); non irrigidisce pinnando
  (le mai-pinnate restano mobili, solo 'preferite'); complementare a M32 adottato.
- **Contro:** la memory `medium-weight-knobs-flat` documenta che i pesi medium (M32 incluso) sono
  FLAT sul timetable (muovono lo score, non l'orario): quindi 'rafforzare M32' probabilmente non
  muove la metrica. Un filtro mosse e' piu' promettente del peso, ma e' metaheuristic-axis.
- **Rischio:** ridurre il reshuffle toglie alla LS il riempitivo mobile con cui chiude i buchi
  (H17/boxed): potenziale regressione hard.

### B. Costruzione: seed placement che eviti la configurazione saturante (TASK-457)
- **Evidenza:** la saturazione e' PRODOTTA dal nostro piazzamento (TASK-460 refutazioni).
- **Come:** ordinamento/placement greedy in WeeklyTemplateBuilder che eviti di concentrare la
  domanda sulle celle scarse (venerdi' 8am) quando un'alternativa esiste (leva candidata TASK-457).
- **Pro:** agisce alla radice (non creare il bacino saturante); construction-side (deterministico,
  riproducibile).
- **Contro:** il verdetto TASK-470 ha mostrato i lever di costruzione PARZIALI (1h+dedup = 1142,
  2,6x golden); placement interagisce con fill-risk + M15/S17 (non banale, TASK-470).
- **Rischio:** spostare la saturazione altrove (shell-game, come TASK-481 depth-3).

### C. Filtro mosse LS 'saturation-aware' (declina la mossa che crea una cella 0-conflict-free)
- **Evidenza:** difende il bacino invece di curare il buco a valle; complementare a TASK-478
  ExactFinisher (cura la coda boxed) e TASK-480 multi-start (selezione bacino).
- **Come:** un CompositeMoveFilter (la famiglia esiste, `GapPreservingMoveFilter`) che declina una
  mossa LS se crea una cella con 0 candidati conflict-free (docenti utili gia' saturati).
- **Pro:** previene direttamente la configurazione saturante in LS; complementare ai cure-a-valle.
- **Contro:** serve un check 'conflict-free-candidate count' per mossa (costo a ogni step LS);
  puo' sovra-costringere la LS (declina mosse che avrebbero aperto spazio altrove); la cella
  0-conflict-free e' uno stato che il multi-start gia' evita probabilisticamente.

## Raccomandazione (onesta, allineata al pivot)

**TASK-460 e' nell'asse metaheuristic+costruzione, che la campagna 2026-08-05 ha dichiarato
ESAURITA** (`campaign-status-2026-08-05.md`: pesi medium flat, ACL non-robust, HoleRepair al
ceiling, accettore inerte sul staged). Il pivot dichiarato e' il **MODELLO** (vincoli, fatti, UX),
non ulteriore tuning del solver. Quindi:

1. **De-prioritizzato.** Procedere su TASK-460 ora significa rientrare nell'asse esausto. Gli
   approcci A/B/C sono tutti metaheuristic/costruction levers di un'indagine chiusa.
2. **Se l'asse MODELLO stallasse e si volesse tornare qui**, l'approccio piu' promettente e' il
   **filtro mosse (C)**, non il peso (A: i pesi medium sono flat) ne' la costruzione (B: levere
   parziali per TASK-470). C e' complementare ai cure-a-valle ratificati (EF + multi-start) e
   opera dove la misura colloca il fenomeno (LS). Ma e' gated maintainer e va misurato (non adottato
   per fede).
3. **AC#1 residuo (bad-seed trace) e' basso valore ora** dato il de-priorizzo: chiuderlo e'
   esercizio analitico su un asse che non si intende inseguire. Si fa se l'asse MODELLO fallisce e
   si torna al solver.
4. **Nessuna adozione, nessun codice.** Questo doc e' il deliverable AC#2; AC#3 (approvazione
   maintainer + spec) e' gated e NON procede in autonomia (il design e' onestamente "non inseguire
   ora", che e' esso stesso un verdetto per Fedora).

## Verifica (evidence, non memory)
- TASK-453 verdetto: `task453-results/VERDICT-erosione-2026-08-03.md` (83,4% MOBILE moved, 83% in
  LS-Compactness, teoriche dominate, PIN preservano).
- Refutazioni TASK-460 (prezzo/penuria/docenti): task-460 description.
- Pesi medium flat: memory `medium-weight-knobs-flat` + synthesis `metaheuristic-campaign-synthesis-2026-08-05.md`.
- TASK-470 levere parziali: `task470-results/VERDICT-construction-gate-2026-08-01.md`.
- Pivot al MODELLO: `campaign-status-2026-08-05.md`.
