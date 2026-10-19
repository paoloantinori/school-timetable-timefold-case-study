# VERDICT: M-BF prototipo banco (wt/mbf-proto, 17/09, laptop)

## Esito: QUALIFICATO-POSITIVO. Il segnale e' forte e pulito, la prova della
## compatibilita' col template resta al ciclo completo. Nessun flip, nessun merge.

## Le quattro domande del banco

### (a) interleaved 54 -> ?

| braccio (stesso harness, 600s, change+swap) | INTERLEAVED | hard | medium |
|---|---|---|---|
| consegna integrato-1 (partenza) | 54 | -4 | -735209 (off) |
| CONTROL: LS con flag OFF | **71** | -1 | -719043 |
| ARMED: LS con M-BF (bill 142800) | **11** | -2 | -819398 |

La lettura corretta e' il DELTA appaiato: la LS nuda CREAVA interleaving
(54 -> 71), la LS armata lo COLLISSA (54 -> 11): **effetto netto attribuibile
a M-BF = -60 giorni** in 600 secondi di ricerca. La frammentazione smette di
essere moneta di scambio gratuita esattamente come previsto dalla spec.

### (b) hard invariato a parita' di catena

M-BF e' medium: nello statico hard -4 identico off/on (assert). Nel dinamico
hard MAI peggiorato (assert, -4 -> -2). Il controllo migliorava hard quanto
l'armata (-1 vs -2): nessun danno hard, nessun guadagno hard: corretto per un
lever medium.

### (c) guardie

- full-day: 94.7 partenza -> 94.1 ARMED, 93.5 CONTROL: l'armata PRESERVA il
  fill meglio della LS nuda (la frammentazione era anche un fill-degrado).
- ricorrenza: 70.1 partenza -> 46.8 CONTROL / 40.5 ARMED. ENTRAMBE le braccia
  collassano: difetto dell'HARNESS, non del lever. Il replay non pubblica le
  statiche M32 (template reference) ne' S13 ha ancora a che fare col template
  costruito: la mini-LS e' cieca al template e rimescola la griglia
  (la trappola "silently-empty statics are how a bench lies", stavolta vista
  prima che mentisse). Il costo residuo M-BF sulla ricorrenza IN QUESTO
  harness e' -6.3pp vs controllo: e' il RISCHIO da gatedare nel ciclo reale.

### (d) golden check (self-gating)

0 match, non 1 come predetto: la divergenza e' NOMINATA. L'unico giorno
interleaved del golden (G05 2027-03-17: T20 Figurino a slot 0 e 2) non
vede M-BF perche' FIGURINO non e' isLab() nel modello (niente requiredRoomTags):
lo scope isLab-based esclude anche l'unica violazione del golden. Golden
residuo 0: perfettamente golden-safe, e la predizione 1 match era sbagliata
per la ragione giusta (divergenza isLab vs famiglie del census, dichiarata in
PREDIZIONE come tolleranza attesa 1-6).

## Il conto (statico, consegna integrato-1)

79 giorni frammentati multi-docente isLab-wide (il census famiglie diceva 54:
isLab copre PIU' materie, es.Informatica/Disegno/RA nei lab), 119 gap-ore,
bill 142800 medium a peso 20/min (M15 parity). Forma decisa: GAP-MINUTI
(20/min), non flat: in famiglia con M15/M20, stessa scala del fill con cui
tratta.

## Che cosa resta da provare (per Fedora/maintainer, nessuna fretta mia)

1. La prova fedele e' il CICLO COMPLETO appaiato (staged, statiche pubblicate,
   M32/S13 attivi) con M-BF ON vs OFF: li' la ricorrenza decide. Il rischio
   dichiarato: -6.3pp del banco, attesi molto minori col template vivo.
2. Lo scope isLab vs famiglie: se si vuole coprire anche i giorni Figurino
   (l'unico golden-interleaved e' Figurino), serve o il tag su Figurino o una
   famiglia esplicita. Da domanda a referente SE il prototipo sopravvive al gate 1.
3. Peso: 20/min e' la scelta M15-parity dichiarata; sweep nel ciclo reale se
   il trade ricorrenza e' stretto.

## Stato del porting (branch wt/mbf-proto, da main e83c49d2)

- src: constraint M-BF (SchoolScheduleConstraintProvider, flag LIVE per-build
  M-TR pattern, default OFF), accessor SchoolSchedule.isMbfEnabled, banner in
  refreshSolverStatics (armed + peso).
- bench: MbfProtoBenchTest (replay statico + mini-LS armata/controllo con
  dump per lab-block-form-kpi.py) + golden arm in GoldenSolutionRegressionTest
  (env-gated, opt-in). Due fix di banco documentati nel codice: analyzeScore
  guardava il DB del servizio e non il problema caricato (wrong-object trap);
  il dump deve leggere problem.getLessons() (il solver muta quelli, non
  snap.lessons: il primo giro ha misurato dump identici byte).
- suite default (flag OFF): VERDE, 1357 run / 0 failures / 0 errors / 97
  skipped, EXIT 0: produzione byte-identica col flag spento.
