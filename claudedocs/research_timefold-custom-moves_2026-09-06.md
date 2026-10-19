# Research Report: Pattern custom-moves in Timefold per la HarvestMoveStream (TASK-589)

**Date**: 2026-09-06
**Depth**: exhaustive
**Confidence**: HIGH (meccanica API: docs ufficiali + verifica diretta nel jar 2.3.0 locale; MEDIUM sulle dosi di budget: esempi documentati, nessuno studio controllato trovato)

## Executive Summary

Timefold offre DUE vie per portare le nostre mosse gated dentro la LS, ed entrambe sono utilizzabili sulla nostra 2.3.0 (verificato nel jar: 65 classi `preview.api.*` presenti). La raccomandazione: **prima la via classica** (un figlio `MoveListFactory` nell'`unionMoveSelector` della fase 2 esistente, con `fixedProbabilityWeight` e le statistiche benchmark per-move-type come misura), **in parallelo il prototipo nella via nuova** (Neighborhoods API, preview: `MoveProvider` + `MoveStreamFactory` con enumerazione dichiarativa dei donatori x celle e filtri = esattamente la nostra forma), che e' anche la via per dare a Timefold il feedback che gli dobbiamo (PR 2610 / issue 2254). La domanda "fase principale o separata?" ha risposta netta: **nella fase principale con peso**, perche' il nostro macchinario adaptive-weights (TASK-500) gia' thina per stream nell'unione, e la fase separata perderebbe tabu-state e l'adattivita'.

## Findings

### 1. Le due API disponibili (entrambe sulla 2.3.0)

**Via classica (stabile): `MoveListFactory`** [docs move-selector-reference]
- Interfaccia a un metodo: `List<Move> createMoveList(Solution_)`; si configura come figlio di `unionMoveSelector` ESATTAMENTE come i changeMoveSelector che gia' abbiamo.
- Limiti documentati: `cacheType STEP` di default (la lista si rigenera a ogni step: "scales badly") e l'obbligo di NON muovere entita' pinnate (i nostri gate gia' lo garantiscono).
- Per il nostro caso il limite STEP e' benigno: le mosse gated sono POUCHE (decine-novantina: la cattivita' del filtro e' la nostra ricchezza qui), la rigenerazione per step costa l'enumerazione che il pass paga gia' oggi in 5-14s per sparo.
- Alternativa JIT: `MoveIteratorFactory` (createRandomMoveIterator, mai materializzare la lista) se il costo per-step duolesse al banco.

**Via nuova (preview): Neighborhoods API** [docs neighborhoods; issue #1759 tracking]
- `MoveProvider.build(MoveStreamFactory)`: enumerazione dichiarativa (`factory.forEach(Lesson.class, false)` con `filter((solutionView, lesson) -> ...)`, accessi al `SolutionView.isValueInRange` nei predicati), poi `pick()` per il campionamento JIT (evita i cross-product), poi `asMove(...)` con i built-in `Moves.change/swap` o move custom.
- Il tratto che INCASTRA il nostro caso: i predicati di enumerazione e filtro ricevono il `solutionView` (stato vivo, per decidere gate complessi) e i dataset enumerati restano aggiornati INCREMENTALMENTE dal solver (il nostro problema dell'indice live e' risolto dal framework).
- Via di fuga per logiche non-join (le nostre chain): `asCachedDataset()` + `buildMoveStream((session, random) -> Iterator<Move>)`: controllo totale su come combinare i dataset (dove metteremmo il tail-swap/la catena).
- Testing dedicato: `MoveTester` (execute/executeTemporarily con undo automatico) e `NeighborhoodTester` (producesAllOf/producesNoneOf): il TDD delle mosse custom ha gia' l'attrezzo.
- Configurazione: `<enablePreviewFeature>NEIGHBORHOODS</enablePreviewFeature>` + `<neighborhoodProviderClass>` dentro `<localSearch>`.
- **Limiti attuali documentati** (issue #1759: tracking 3/33 subtask done): niente pesi di probabilita' fra provider (tutti equiprobabili), incompatibile con VARIABLE_NEIGHBORHOOD_DESCENT, e SOSTITUISCE i move-selector classici nella fase in cui e' attivo (non si mescolano).
- Conseguenza del limite sostituzione: scegliere Neighborhoods per la fase 2 oggi significherebbe rinunciare a pillarChange/pillarSwap (il supporto pillar e' proprio il PR 2610 in corso, non merged): per ora NON migrabile in toto.

### 2. Fase principale o separata? (la domanda di maintainer)

Tre pattern documentati/praticati:
- **Unione pesata nella fase principale** [move-selector-reference, esempio ufficiale]: figli con `fixedProbabilityWeight` dentro `unionMoveSelector`; `selectorProbabilityWeightFactory` per pesi dinamici; `FairSelectorProbabilityWeightFactory` per equiprobabilita' PER MOSSA (non per selettore) quando i conteggi differiscono di ordini di grandezza (il nostro caso: 93 mosse harvest vs milioni di change: il fair factory sovracampionerebbe l'harvest di 4 ordini: da EVITARE, meglio un peso fisso piccolo tarato al banco).
- **Fasi LS multiple in sequenza**, ognuna con la sua termination (pattern praticato: issue #1535 mostra config con piu' LocalSearch; nota la rough edge dal 1.20: una termination globale unimproved sul solver solleva eccezione se applicata anche alla CH, i limiti vanno per-fase).
- Il documento SO di GDS sulla differenza (73921116) non e' stato scrapeabile (bot-check): la meccanica documentata altrove e' quella sopra (l'unione sceglie UN figlio per richiesta di mossa, il peso ne determina la frequenza).

**Perche' per NOI vale la fase principale**: (a) il nostro decider MT-LS (TASK-543) e l'adaptive-weights (TASK-500) lavorano per move-stream DENTRO l'unione: un figlio nuovo eredita thinning e pesi adattivi senza codice nuovo; (b) la fase separata ricreerebbe il problema dello stato tabu separato e aggiungerebbe un confine di terminazione da presidiare (cio' che il nostro wrapper fa gia' coi pass: duplicazione di ruolo); (c) il fronte su cui agiamo e' "la LS non campiona MAI il salto lungo-raggio": metterlo NELLA stream campionata e' la cura, non una fase a parte che il campionamento non vede.

### 3. Con quale budget

- **Peso in-fase**: partire con `fixedProbabilityWeight` piccolo (1.0 contro i pesi correnti di change/swap/pillar: il banco dice il valore), MISURARE con `MOVE_COUNT_PER_TYPE` (conteggio valutazioni per tipo) e `PICKED_MOVE_TYPE_BEST_SCORE_DIFF` (quanto migliora il best per tipo: la prova che la stream non solo gira ma VINCE), poi tarare.
- **Per-step**: `selectedCountLimit` sul selettore figlio come rubinetto granulare se il peso non bastasse.
- **Per-fase** (se un domani si volesse la fase dedicata): termination per-fase (`unimprovedSecondsSpentLimit`), MAI globale sul solver (l'eccezione #1535).
- Nessuno studio controllato pubblico sulle dosi: la letteratura sono gli esempi dei docs + il nostro banco. La taratura e' nostra (il banco live-state a parita' di budget, come da TASK-589).

### 4. Richiede benchmark? SI', e il benchmarker li ha gia' costruiti

- `MOVE_COUNT_PER_TYPE` (statistica grafico+CSV): quante mosse valuta ogni tipo [benchmarking-and-tweaking 5.6].
- `PICKED_MOVE_TYPE_BEST_SCORE_DIFF` / `..._STEP_SCORE_DIFF` (6.4/6.5): quale tipo di mossa migliora il best (e di quanto) nel tempo: la metrica del "la stream serve".
- La separazione per tipo viaggia su `Move.describe()` (o il tipo di mossa): le nostre mosse custom dichiarano la loro etichetta e il benchmarker le separa da solo.
- `moveListFactoryCustomProperties`: il benchmarker sa tweakerare i parametri custom della factory (potremmo mettere il peso-filtro del gate come property e farne lo sweep).
- La nostra infrastruttura (`run-benchmarker.sh`, solverBenchmarkConfig*) e' gia' in piedi: si aggiunge la statistica e la coppia di braccia.

### 5. Il contesto nostro che completa il quadro

- Il decider MT-LS (TASK-543) e' costruito SULLE move-stream internals (lo dichiariamo noi stessi nel draft PR 2610): un figlio in piu' nell'unione e' la forma piu' bassa di integrazione possibile.
- Il draft PR 2610 (nostro, in coda di invio) chiede esattamente i pillar custom-key nella Neighborhoods API: sperimentare l'API ORA ci da' la credibilita' e il vocabolario per quel feedback, e l'issue #2254 resta il canale.
- La research interna su H32-H34 (2026-09-01) aveva gia' classificato SurplusHarvest come operatore destroy-and-repair ALNS-like: la stream in LS e' la forma "repair dentro la ricerca" di quella lettura, con l'adaptive-weights come gancio (la riflessione del frontier model la indicava come unica evoluzione sensata).

## Confidence Assessment

- HIGH: meccanica delle due API, configurazione, limiti del preview, esistenza statistiche benchmark per-move-type, presenza dell'API nella nostra 2.3.0 (verifica jar diretta: 65 classi preview).
- MEDIUM: dosi di budget (pesi iniziali, quando il costo STEP del MoveListFactory duole): esempi dei docs, nessuno studio controllato; la taratura e' un compito del nostro banco.
- LOW/UNVERIFIED: la risposta SO di GDS sui pro/contro di union-vs-fasi (pagina dietro bot-check): la meccanica l'abbiamo dal resto delle docs, ma l'argomentazione autoriale originale non e' stata letta.

## Sources

1. https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/neighborhoods - Neighborhoods API (preview): Move/MoveStream/Neighborhood, MoveProvider, enumerazione/campionamento/asMove, asCachedDataset+buildMoveStream, MoveTester/NeighborhoodTester, configurazione enablePreviewFeature, limiti (niente pesi, niente VND, sostituisce i selector).
2. https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/move-selector-reference - MoveListFactory (STEP cache, pinned, customProperties), MoveIteratorFactory (JIT), unionMoveSelector con fixedProbabilityWeight/selectorProbabilityWeightFactory/FairSelectorProbabilityWeightFactory, selectedCountLimit, cartesianProductMoveSelector.
3. https://docs.timefold.ai/timefold-solver/latest/running-timefold-solver/benchmarking-and-tweaking - statistiche MOVE_COUNT_PER_TYPE (5.6), PICKED_MOVE_TYPE_BEST/STEP_SCORE_DIFF (6.4/6.5), separazione via describe(), tweak delle factory properties.
4. https://github.com/TimefoldAI/timefold-solver/issues/1759 - tracking del preview Neighborhoods (3/33 done, minimal change/swap, LS-only).
5. https://github.com/TimefoldAI/timefold-solver/issues/1535 - pattern fasi LS multiple con terminazioni; l'eccezione 1.20+ sulle termination globali.
6. https://github.com/TimefoldAI/timefold-solver/discussions/1423 - PlanningPin non applicato ai custom move: responsabilita' della factory (i nostri gate coprono).
7. https://stackoverflow.com/questions/73921116 - union vs fasi separate (GDS; non scrapeabile: bot-check).
8. Jar locale 2.3.0 (~/.m2): verifica diretta 65 classi preview.api.move/neighborhood (MoveProvider, MoveStreamFactory, NeighborhoodProvider, Move*Provider built-in).
9. claudedocs/spalmone-2026-27/pr-2610-comment-draft.md - il nostro feedback in coda (pillar custom-key, issue #2254): il canale verso upstream.
10. src/main/resources/solverConfig.xml - la fase 2 attuale: unionMoveSelector con change/swap/pillarChange/pillarSwap (dove il figlio nuovo si innesta).
