# VERDICT blind-live-r1: round cieco sul residuo CONSEGNATO live (B3, [-9]hard)

Prima applicazione del metodo dei round ciechi a uno stato di CONSEGNA (non piu'
al banco replay): la consegna B3 del gate TASK-613 (arm-b), cioe' ciò che la
produzione consegna OGGI con tutti i pass ratificati ON. Protocollo invariato:
puzzle neutro + verificatore meccanico, agente cieco a conoscenza zero,
arbitrato Java a valle con l'evaluator pieno.

Stato bersaglio (census verificato dallo step24, ASSERT su hard -9):
- H17 6 buchi: G10 2026-12-10@11, G11 2026-10-30@12, G11 2026-12-10@9,
  G12 2026-09-18@11, G12 2026-10-02@11, G12 2026-10-09@11.
- H32 2 celle: G12 2026-11-10 (4/5), G12 2027-04-16 (4/5).
- H34 1 giorno: G09 2027-05-25 (VUOTO, dich 6, tolleranza 1).

## PREDIZIONE pre-registrata (Fedora, dal mandato, PRIMA del round cieco)

> il cieco TROVA catene multi-mossa che chiudono parte del -9 (il precedente r2:
> 21 mosse per -3), ma il bacino live e' piu' povero di slack del replay e la
> diagnosi arm-c dice esplorazione morta su SYM-CODEST-PARENT/pin: atteso un
> numero di mosse SUPERIORE a r2 (25-40) con almeno una famiglia fuori alfabeto
> corrente (slide multipli intra-giorno o parcheggi che nemmeno v0.6 esprime).
> SE il cieco non trova NULLA (consegna volontaria a zero mosse): convergenza
> anche sul live, e la frontiera si sposta su costruzione (TASK-615) o input,
> non su nuovi pass.

## Esito: DUE round (r1 col supporto R11 per-corso, 1b con supporto per-lezione fedele)

### Round r1 (supporto R11 corso-level, ereditato dai round replay): 30 mosse, FALSATO dall'arbitro

Il cieco chiude 9/9 con 30 mosse sul checker (TUTTO PULITO EXIT 0, mosse nel
range previsto 25-40) MA l'arbitrato Java salta 7/30 mosse come fuori dal VRP
REALE per-lezione: tutte atterraggi @13 su G02/G03 (finestre di calendario
per-gruppo + quota-kind/primo-giorno che il tavolo corso-level, essendo una
union, non vedeva). Con 7 anelli di catena saltati le catene si rompono e lo
stato PEGGIORA (post-room -15006). Le "famiglie fuori alfabeto" del round r1
(catene cross-classe con parcheggio @13) erano ARTEFATTI del checker grosso,
non famiglie vere. Strumento curato: step24 dumpa ora lesson_vrp.json
(per-lezione, 12551 voci, 126MB in LOCALE per la regola >100MB, rigenerabile
col comando in step24-dump-vrp.log.txt) e il checker lo usa con priorita'; il
checker fedele bolla ESATTAMENTE le 7 mosse che l'arbitro Java aveva saltato
(convergenza python/java perfetta).

### Round 1b (R11 fedele): 7 mosse, 2/9 difetti chiusi, 7 dichiarati STRUTTURALMENTE inchiusibili

Cieco fresco, stesso puzzle: consegna 7 mosse che verificano pulite e chiudono
i difetti 2 (G11 2026-10-30: catena intra-giorno 2 mosse) e 7 (G09
2027-05-25: 5 import di code @12/@13 da donatori che restano a quota-1, dentro
la tolleranza del gruppo). Gli altri 7 difetti: dichiara l'inchiudibilita' con
enumerazione meccanica (notes2-*.py, locali): (a) le classi fuori scope sono
CONGELATE (zero celle legali VUOTE: le catene cross-classe del round r1 non
esistono legalmente); (b) difetti 4/5/6 (G12 venerdi' @11): i blocchanti H1
(G04/G02 alle 8-11) sono permanenti e l'unico surplus donabile (code
T01) e' confinato R10 ai giovedi'; (c) difetti 8/9 (H32 G12): servono
catene che partono solo da giovedi' confinati e non possono raggiungere
martedi'/venerdi': ZERO mosse singole e ZERO catene a 2 mosse (enumerato);
(d) difetto 1 (G10): tutti e quattro i candidati mobili bloccati
permanente (R1 + R8b stanza unica di famiglia occupata dall'T01 di 4
FD immovibile); (e) difetto 3 (G11 @9): T05 permanente alle 9 via 1
FB congelata, alternativa R4-invalida.

### Arbitrato Java del round 1b (step25, evaluator pieno): CONFERMA TOTALE

- 0 mosse saltate (il checker fedele e' trasparente al VRP reale).
- post-room-pass: **hard -9 -> -7** = H17 6 -> 5 (chiuso G11 10-30; NESSUN
  nuovo buco dai donatori) + H34 1 -> 0 (G09 2027-05-25 a 5 ore, dentro
  tolleranza) + H32 2 -> 2 (le due celle G12 restano: inchiusibili).
- Il self-report del cieco (2 chiusi, 7 no) e' ESATTO sull'evaluator reale.

## Classificazione delle mosse vincenti contro l'alfabeto meccanizzato (CSF / EDF-relay / HoleRelay v0.6+616)

1. **Catena intra-giorno 2 mosse del difetto 2** (Scienze 11->12 riempie il
   buco, Gestione aziendale 13->11 prende la cella liberata): FAMIGLIA NUOVA,
   piccolo ma reale. HoleRelay v0.6 esplora catene docente-bloccante CROSS-classe
   (rotation-with-permission) e slide della coda; NON esiste nel suo alfabeto lo
   SWAP intra-classe attorno al buco (la lezione SOTTO il buco sale, la coda
   scende). EDF/CSF non lo coprono (non e' un import ne' uno swap same-day
   cross-classe). Specifica della prossima meccanizzazione: compact-swap
   intra-classe nel finisher H17 (2 mosse, gate: H1/H3/VRP gia' presenti).
2. **I 5 import-tail del difetto 7** (code @12/@13 donate a quota-1): il
   MECANISMO e' nell'alfabeto (EDF-relay import: estrazione coda donatore +
   atterraggio giorno vuoto), ma EDF alla consegna ha fatto 0 mosse sui suoi 6
   target: il suo gate donatore rifiuta donazioni che lasciano il donatore a
   quota-1 ANCHE dove la tolleranza del gruppo (G09: 1h) le rende legali.
   GATE GAP, non family gap: la cura e' far leggere al gate donatore la
   TOLLERANZA per-gruppo che il vincolo H32 gia' usa.
3. **Le 7 mosse illegali del round r1**: trovate solo perche' il cieco non
   pagava i nostri gate (VRP per-lezione). Nessuna famiglia vera.

## VERDETTO (tre righe)

1. IPOTESI (Fedora): il cieco trova catene multi-mossa (25-40 mosse) con almeno
   una famiglia fuori alfabeto; oppure, a zero mosse, convergenza anche sul
   live e frontiera su costruzione/input.
2. MISURATO: col checker grosso 30 mosse (nel range) MA 7/30 erano artefatti
   del supporto R11 corso-level; col checker FEDELE il cieco chiude 2/9 difetti
   con 7 mosse (arbitrato: -9 -> -7, zero skip, self-report esatto) e PROVA
   meccanicamente l'inchiudibilita' degli altri 7 (classi donatrici congelate,
   blocchanti permanenti, surplus confinato ai giovedi'): il bacino live e'
   molto piu' vicino al ramo "convergenza" che al ramo "25-40 mosse".
3. RACCOMANDAZIONE: (a) due lever piccole e specificate emergono dall'alfabeto:
   il compact-swap intra-classe (famiglia NUOVA, chiude il tipo difetto-2) e il
   gate donatore EDF tolerance-aware (chiude il tipo difetto-7 senza nuovo
   meccanismo); (b) il residuo DIFETTI 1/3/4/5/6/8/9 e' strutturale per repair
   locale: la frontiera per quei 7 e' costruzione (TASK-615) o input, come la
   predizione alternativa di Fedora; (c) OGNI futuro round cieco live PARTE dal
   supporto R11 per-lezione (il tavolo corso-level e' dichiarato ritirato).
   Nessuna adozione: la meccanizzazione delle due lever e' decisione Fedora.

## Artefatti

- moves-live.txt (round r1, 30 mosse + header classificato) /
  moves-live-recheck-faithful.txt (le stesse 30 contro il checker fedele: le 7
  R11), arb-r1.log.txt (arbitrato r1 con i 7 skip).
- moves-live2.txt (round 1b, 7 mosse + header con l'analisi di inchiudibilita')
  / arb-r1b.log.txt (arbitrato: -9 -> -7).
- step24-dump.log.txt / step24-dump-vrp.log.txt (dump + supporto per-lezione;
  lesson_vrp.json 126MB LOCALE, rigenerabile).
- Codice: step24BlindLivePuzzleDump + step25BlindLiveArbitration +
  applyMovesFileVrpChecked nel banco (env-gated).
- notes-mine-*.py / notes2-*.py: scratch dei ciechi (locale, non committati).
