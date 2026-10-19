# DIAGNOSTICO DIFF-GUIDATO TASK-613: perche' il builder non riproduce la sequenza D1 (15/09)

step20BlindChainDiff (test-side, env PLANNER_TEST_CHAIN_DIFF=1): (1) catena a
[-3]; (2) le 13 mosse cieche D1 sull'evaluator vero: chiudono D1 (H17 3 -> 2,
hard -2 dopo il room pass fisso: il -4000 pre-pass e' la solita H2 fantasma);
(3) copia test-side strumentata del builder v0.5 (HoleRelayFinisherDiff da
262cc255): stampa la catena COSTRUITA per ogni target + la cella in conflitto
di verifyComposite. HEAD 116cd20a, heap 12g, EXIT 0.

## Il diff, target per target

D1 (G11 04-30, hole@11):
- CIECO (vince): 4 blocchi 3-cicli (G14, G12, G10, G02) + slide
  G11 12->11. Prende come contendente T21 la lezione G14@11 e la
  catena scende di gruppo in gruppo.
- BUILDER: `G02#2819 10->11, G02#2820 11->10, G11#2661 12->11`
  (SOLO 3 mosse). Verify: **H1 T21@11: G02#2819 vs G11#2661**.

D2 (G13 11-04): BUILDER a 6 mosse (swap G04 + inserzione G08 a 4 di
v0.2d); verify: **H1 T15@10: G04#5170 vs G08#5016** (il cieco usa
il solo 3-ciclo G08 {11->13, 10->11, 13->10}).

D3 (G13 03-02): BUILDER a 4 mosse, un 3-ciclo G04 {10->11, 11->12, 12->10};
verify: **H1 T10@12: G04#10085 vs G13#10013** (lo slide deposita De
Lisi a 12 dove la rotazione ha appena messo #10085).

## Prima divergenza e causa meccanica

PRIMA DIVERGENZA (D1): il builder sceglie il contendente sbagliato-per-forma:
ruota il giorno G02 con un'inserzione from=11,to=10 che e' di fatto uno
SWAP di due lezioni adiacenti (#2820 11->10, #2819 10->11). Lo swap NON
libera l'ora 11 per T21: anzi DEPOSITA in 11 proprio la lezione di
T21 (#2819). Il check dei bloccanti NON se ne accorge perche' #2819 e'
nel batch e l'esenzione e' `tryLessons.contains(occ)`: essere nel batch viene
trattato come "sto vacatando", ma il batch puo' DEPOSITARE una lezione
nell'ora critica invece che toglierla.

CAUSA MECCANICA (uniforme su tutti e 3 i target, e' la firma COMP-H1=3):
l'inserzione-rotazione e' un'operazione di FORMA del giorno (sposta l'intero
span), ma il vincolo da soddisfare e' PER-DOCENTE all'ora critica. Le lezioni
trascinate dall'inserzione possono atterrare (a) nell'ora che la catena sta
liberando (D1) o (b) nella destinazione dello slide (D3), e l'esenzione
batch-movers le copre. Il cieco non commette l'errore perche' i suoi 3-cicli
sono scelti consapevolmente per TEACHER: all'ora critica mettono un docente
libero, non spostano span.

DIREZIONE DELLA CURA (per Fedora, NON applicata): l'esenzione va raffinata:
un occupante O della cella destinazione D di una lezione L del batch e'
"permesso" SOLO se O stesso sta davvero lasciando D (la destinazione di O e'
diversa da D) E nessun altro membro del batch atterra in D con conflitto
docente; in pratica il gate deve valutare, per ogni (lezione,dest) del batch,
il teacherOf(lezione.teacher, dest) POST-mossa del batch (che il builder
simbolico sa calcolare) e ricorrere sul vero bloccante. Alternativa piu'
radicale: aggiungere all'alfabeto il 3-ciclo arbitrario (permutazione
consapevole del docente) accanto all'inserzione.

ARTEFATTO DICHIARATO dello harness: lo score finale [built-applied] -1650 con
H34 1647 e' corruzione del solo stato di misura del doppio load nello stesso
JVM (seedMediumTierFacts ripetuto), NON un effetto del builder: le catene e le
collisioni sono stampate PRIMA dell'apply/revert e coincidono col census di
produzione {COMP-H1=3}. Il dato consegnato e' la stampa, non quello score.
