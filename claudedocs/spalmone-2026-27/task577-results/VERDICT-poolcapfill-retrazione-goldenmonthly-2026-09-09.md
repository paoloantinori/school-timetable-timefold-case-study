# VERDICT: retrazione golden-monthly + cura pool-cap-fill (9/09 mattina)

## La retrazione (falsa scoperta #10)

La tesi "la costruzione mette ore nei mesi-di-pausa del golden (G10 marzo:
golden=0, run=66)" e' un ARTEFATTO. Le 66 ore erano STAGE: l'analisi di stanotte
caricava la meta-tabella cross-macchina il cui flag pseudo e' codificato 't'/'f',
e filtrava `== 'true'` → il filtro non matchava mai → le ore Stage contavano come
ordinarie. Il censimento rifatto con estrazione corretta (join docente via
`course.teacher_id`: `lesson` NON ha `teacher_id`) sui POST_CONSTRUCTION freschi
del ciclo live (attempt1 deterministico E attempt2 GRASP): **ZERO ore ordinarie
nei mesi-pausa, entrambi i tentativi, tutti i gruppi** (similarita' mensile
costruzione-vs-golden avg 0.992/0.993).

Banco di conferma (BoxDeficitOnlyBenchTest, 4 bracci, flag golden ON globale):
H34 = 22 / 22 / 23 / 23, identico ovunque. Il flag non muove nulla; in piu'
contraddice il pool-cap ratificato (G04 e' sopra-cap golden OGNI mese per
design della forma-b TASK-497: +7..+17h/mese). Famiglia golden-monthly
(gate fd32a39e + enforcer a2808514 + routing 086a859c): **BOCCIATA**, flag
annotato nel codice, raccomandazione RIMUOVERE.

## Il censimento vero (40 celle dichiarate vuote a fine costruzione)

Su POST_CONSTRUCTION attempt1 del ciclo live (profilo slotretry-9sep), celle
dichiarate in-grid con zero piazzamenti ordinari: **40**. Tutte classe B contro
il golden (il golden le serve). Scomposizione:

| famiglia | celle | firma | natura |
|----------|-------|-------|--------|
| G04 | 15 | tutti e 9 i venerdi' dichiarati apr-mag + 6 feriali (1-30/4, 15/6) | distribuzione (golden 5h l'una) |
| G08 | 10 | settimana 18-28/5 + 7-15/6 | distribuzione + 41h corte (626 vs 667) |
| G14 | 6 | gio/ven apr-mag | input-locked (triangolo T16, questionario referente) |
| in-scope H34 | 9 | mag-giu G05/G06/G09/G10/G12 | teacher-locked (il filone del ciclo in corso) |

Totali per gruppo (costruzione vs golden vs foglio): G04 750/750/840 (il pool
lo rispetta, la FORMA no: front-load seguendo il foglio); G08 626/667/676;
G14 86/120/115; gli altri gruppi ±28h dal golden.

## La cura: pool-cap-fill in costruzione (prototipo default-OFF)

Flag `planner.construction.pool-cap-fill.enabled`: `expectedHoursCapFor` consulta
i target per-data pool-cap TASK-497 (già ratificati lato score via capSupply,
input-derived, ZERO golden) e trimma il cap quando il target e' piu' basso della
quota del foglio. Tutte le pass ereditano (pre-booking, stamp, fill tail, box).

Banco PoolCapFillBenchTest (deterministico, H2, stesso harness BoxDeficitOnly):

| metrica | OFF | ON |
|---------|-----|-----|
| celle vuote totali | 55 | 34 |
| G04 | 20 | 9 |
| G08 | 9 | **0** |
| G12 (senza pin al banco) | 13 | 13 |
| boxed | 140 | 136 |
| H33 | 29 | 26 |
| H34 | 22 | 21 |
| placed G04 / G08 | 750 / 626 | 750 / 626 |

Sul perimetro di design (gruppi pool-cap): 29 → 9 celle (-69%), G08 a zero.
Zero regressioni su ogni contatore. Il criterio pre-registrato "empties < 20"
e' MANCATO sul totale per una ragione nota: il banco gira senza i pin del
pre-assign, che sul profilo laptop riempiono 12 delle 13 celle G12 (G12
banco 13 vs laptop 1); sul profilo vivo la stessa cura vale 40 → ~20. Le famiglie
fuori perimetro (G12, G06, G14, tail teacher-locked) non cambiano, come
da design (nessun target pool-cap per gruppi non over-capacity).

## Prossimi passi proposti (decisione maintainer)

1. Ratifica pool-cap-fill DEFAULT ON (o ciclo di conferma sul profilo vivo prima).
2. Rimozione famiglia golden-monthly (gate + enforcer + routing).
3. G14: attendere questionario referente.
4. Tail teacher-locked: ciclo surplus-harvest in corso (B-TIMESLOT-1, attempt 2).
