# Golden-oracle sulle celle rosse sanity di integrato-3 + scope Figurino M-BF (17/09)

Domanda di maintainer: prima di citare referente, cosa dice il golden? Script:
/tmp/golden-check-celle.py (dati: claudedocs/spalmone-2026-27/data/lessons.csv +
seed/calendario_*.csv). Risposte tutte meccaniche, zero archeologia sui fogli.

## Le 4+1 celle rosse: il golden risponde su tutte

EMENDAMENTO 17/09 notte (dai raw lessons.txt di integrato-3, prima stesura
imprecisa): le tre celle class_days NON sono giorni quasi vuoti. La forma
reale della consegna: **7h Stage + 1h ordinaria in prima ora** (G09 22/02:
Stage slot 9-15 + Confezione T16 slot 8; G10 05/03 e 23/04: Stage slot
9-15 + Educazione Civica T23 slot 8). Il sanity counter conta solo le
ore non-Stage e le etichetta "1h": il difetto reale e' l'ora ordinaria
innestata su un giorno di stage, non la mancanza di ore.

1. **G09 22/02, G10 05/03, G10 23/04**: giorni dichiarati modality S
   nel foglio (8,S) che il golden tiene **100% Stage** (8h pseudo-docente,
   zero ordinarie). La nostra 1h ordinaria li e' **contaminazione di
   giorno-di-stage** (famiglia ordinary-on-stage-day). MECCANISMO IN
   VERIFICA: il VRP permits() con aula=0 dovrebbe RIFIUTARE quelle celle
   (e lo fa: la costruzione consulta isSlotLegalFresh ai gate), quindi
   l'ingresso dev'essere un percorso che bypassa il filtro (pin poi demozione,
   o un pass senza gate VRP) e la permanenza e' garantita dal grandfather
   del current-slot (Lesson.computeAvailableTimeslots riga ~423: la cella
   occupata illegale resta nel range per validazione, la LS puo' uscirne ma
   nulla la spinge: per i gruppi class_days NESSUN vincolo hard addebita
   l'ordinaria su giorno aula=lab=0, H23/H24 sono scoped FCQuota).
   Replay costruzione deterministica in corso per nominare il pass d'ingresso.
2. **G07 09/06 (run 3h ordinarie + 2h Stage)**: contraddizione
   input-vs-golden VERA. Il foglio dichiara (5,S) ma il golden quel giorno fa
   **5h ORDINARIE** (Educazione Civica x3 T23 + Scienze x2 T25, slot
   0-4). Golden-first (TASK-550 famiglia): quelle 5 ore sono aula, non stage;
   la riga va curata a modality A e notificata a referente a posteriori. Il nostro
   3h+2h misto segue il foglio: col input curato il giorno diventa ordinario
   pieno (famiglia fill nota).
3. **G08 01/04 (buco interno 4h)**: il foglio dice (2 aula, 4 stage) e il
   golden lo pratica ESATTAMENTE: Stage 8-12 + RA T15 14:30-16:30
   contigue. Il nostro giorno (Controllo numerico T15 slot 8 + Stage
   9-12 + Storia T23 slot 13) non ha un problema di LUNGHEZZA (2h
   ordinarie sono la quota giusta) ma di FORMA: le 2h ordinarie vanno
   contigue in coda al blocco stage, come fa il golden. Famiglia
   compattazione same-day, NON giorni-corti.

## Scope Figurino di M-BF: il golden CHIUDE la domanda, referente non serve

Il golden pratica Figurino **sparso**: 112 run da 1h contro 9 run da 2h+
(su 1/2/G09). Non e' materia a forma di blocco: includerla nel perimetro
lab-families di M-BF penalizzerebbe una pratica del golden. Lo scope isLab
attuale (Figurino FUORI) e' golden-correct; il golden-check 0 match di M-BF e'
il self-gating che volevamo (l'unico giorno golden interleaved frammenta T20
su Figurino, fuori scope per design). La predizione sbagliata (1 atteso, 0
misure) era sbagliata per la ragione giusta. La domanda referente sullo scope
e' RITIRATA.

## CHIUSURA NOTTE (01:00): la famiglia e' M39 (TASK-604), lever GIA' provato e bocciato

La catena e' completa e cambia la raccomandazione: il vincolo che avremmo
costruito stanotte ESISTE GIA' (M39, TASK-604, BaseConstraintProvider
`ordinaryOnStageOnlyDay`: stage-only day, ~100/lezione M28-scale, medium
golden-first, pseudo/pin esenti, flag opt-in DEFAULT OFF). Il suo javadoc
cita "29 ordinary hours on stage-only days ALL YEAR": il censimento
indipendente di stanotte (script in /tmp/golden-check-celle.py) conferma
ESATTAMENTE 29h su 7 giorni (G08 fuori scope foglio-fc, G07 09/06
= giorno dell'input bug S->A, G10 04/01 + G13 09/06 = pratica mista
genuina). La morte: TASK-604 REPORT 12/09, M39 ON -13 vs -11 con H34
INVARIATA e tassa a 1 solo match in quel bacino: il prezzo medium NON
sposta la famiglia in quella forma.

CENSIMENTO CROSS-RUN (nuovo, stanotte): la famiglia e' sistematica, non
rumore: integrato-1 ha le stesse celle con altri docenti/date (Figurino
T20 G09 23/04 slot 8-9, Ed.Civica T23 G10 09/06 slot 8). E il
replay costruzione deterministica lascia quelle date VUOTE: le ordinarie
entrano in LS/repair, coerenti col meccanismo documentato in M39 ("the
pool runs out, the search has no reason to prefer a declared ordinary
day over a stage day's morning").

RESTA APERTO (per maintainer, in ordine di costo):
1. CURE INPUT (famiglia TASK-550): G07 09/06 (5,S)->(5,A) golden-first
   (il golden quel giorno fa 5h ordinarie T23/T25). PATCH preparata,
   NON applicata (file dati reali). Riduce la divergenza golden della
   famiglia a 3 giorni genuini.
2. Il lever modellico resta morto nella forma prezzo-medium; una forma VRP
   hard per class_days violerebbe il golden sui 3 giorni genuini: NON
   candidata finche' referente non dice se i giorni misti sono voluti.
3. Il difetto di FORMA G08 01/04 (2h sparse attorno allo stage) e'
   compattazione same-day, non questa famiglia.

## Conseguenze immediate

- TASK-623 aggiornato: la parte 1 (ordinary-on-stage-day) e' la famiglia M39
  gia' censita e bocciata nella forma medium; le celle sanity del giorno sono
  quella famiglia, non una nuova.
- Cura input G07 09/06 (5,S -> 5,A): patch pronta, applicazione a maintainer.
- La sanity table va letta col golden accanto: il minimo per-gruppo da solo
  classifica "giorno corto" anche dove il difetto e' altro (contaminazione o
  forma).
