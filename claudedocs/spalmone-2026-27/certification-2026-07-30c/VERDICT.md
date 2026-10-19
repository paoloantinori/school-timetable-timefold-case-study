# VERDICT 30c - TASK-430 slot0-balancing rimisurato sul SOLVE INTERO (stato attuale)

**Run:** 2026-07-30, profilo dev fresco, java -jar con 3 flag via PLANNER_JAVA_OPTS:
`slot0-balancing=true` + `constrained-teacher=true` + `phase-transition-dump=true`.
Banner verificato pre-run: `slot0-balancing=true profile-following=false constrained-teacher=true`.
T01 231/231 pinnate (constrained-teacher effetto confermato). JAR = HEAD `cb17a2f5`
(include 5ee5171f TASK-456 ratifica). Solve staged ~39min (04:45->05:24), B-ROOM 604s.
Score finale congelato in due check. ValidationService newlyViolated=0.

**Confronto:** 30c (ON) vs 30b (OFF). STESSO stato (stessa JAR, stesso input: fresh seed +
import-calendario 866/1993 + pre-assign 231 T01), UNICA differenza il flag slot0-balancing.

## DOMANDA posta (handoff 30/07): l'ipotesi gate2 regge oggi?

Gate2 (stato pre-M-LF/M37/H29/H28/constrained-teacher) bocciò 430 perchê riservare
l'ancora faceva peggiorare hard e full-day (conto netto negativo: toglieva celle a M20=1000/ora).
Ipotesi da testare: nello stato di oggi il conto netto potrebbe essere positivo. **Il run deve dire.**

## RISPOSTA: IPOTESI REFUTATA. Il netto NON e' positivo sulle metriche di applicabilita'.

La cura day-start e' reale e pulita, ma slot0-balancing la paga a un prezzo non accettabile:
introduce 6 buchi interni HARD (regressione di TIER), spacca la contiguita' M15 (-81600),
cala il full-day di 3pp. L'hard migliora (+62) ma per una ragione NON collegata al meccanismo
slot0 (effetto collaterale room-null), e con 6 nuove violazioni hard annesse.

## Tabella 30c vs 30b

| metrica | 30b (OFF) | 30c (ON) | delta | verso |
|---|---|---|---|---|
| hard | -169 | **-107** | +62 | migliore |
| medium | -1069120 | -1222000 | -153880 | peggiore |
| soft | +16104 | -46420 | -62524 | peggiore |
| H0 boxed | 3 (-6) | 6 (-12) | +3 boxed | peggiore |
| **H17 buchi interni** | **0** | **6 (-6)** | +6 | **PEGGIORE (hard!)** |
| H29 room-null | 163 (-163) | 89 (-89) | -74 | migliore (effetto collaterale) |
| room-null NON-LAB | 0 | 0 | = | ok (M-LF regge) |
| **day-start ancorato** | 99.3% | **100.0%** | +0.7pp | migliore |
| day-start ritardi (anatomy) | 15 | **1** | -14 | CURATO |
| day-start scarto>=3 vs golden | G11 ven (15) | **nessuno** | -- | CURATO |
| **full-day ex-G14** | 88.6% | **85.4%** | -3.2pp | peggiore |
| deficit days (ex-G14) | 173 | 203 | +30 | peggiore |
| deep-deficit (>=2h) | 51 | 48 | -3 | ~piatto |
| **ricorrenza UNPINNED** | 74.3% | **76.8%** | +2.5pp | migliore |
| blocchi 1h / 2h | 3626 / 2362 | 5000 / 1890 | frammentati | coerente con M15 |
| M15 contiguita' same-day | 4 (-6000) | 36 (-87600) | **-81600** | **PEGGIORE (grande)** |
| M20 quota fill | 126 (-194500) | 148 (-226500) | -32000 | peggiore (ipotesi) |
| M32 template deviation | 745 (-116295) | 926 (-137226) | -20931 | peggiore |
| M18 day-start | 15 (-750) | 0 (0) | +750 | migliore (cura intesa) |
| M-LF lab-fallback | 112 (-112000) | 65 (-65000) | +47000 | migliore |
| M37 keep-room | 326 (-32600) | 197 (-19700) | +12900 | migliore |

> **Baselines: ATTENZIONE al metro.** I valori full-day/deep in questa tabella (30b 88,6%,
> 30c 85,4%, deep 51/48) sono della VECCHIA serie class_days. La serie autorevole
> (DailyActivityQuota, `serie-storica-kpi-2026-07-30.md`) da' 30b 90,1% / deep 31 e 30c 86,6% /
> deep 29. Il DELTA slot0 (30c ~3-4pp sotto 30b full-day + 6 buchi H17 dove 30b ne ha 0) e'
> invariato fra i due metri: il verdetto slot0-REFUTATO regge uguale. Per i numeri assoluti,
> usa la serie storica, non questa tabella.

Decomposizione hard 30c = H0(-12, 6 boxed) + H17(-6, 6 buchi) + H29(-89, room-null) = -107.

## I 3 finding decisivi

### 1. La cura day-start e' reale e pulita (ma non basta)
30c: ancoraggio 100.0% (meglio del golden 99.3%), G11 venerdi' **0 ritardi** (vs 15),
UNICO ritardo = G08 giovedi' (1 giorno). Nessuno scarto >=3 vs golden. Il caveat del PASSO 1
(zero-sum: G09 0->9, G12 1->8) era un fenomeno di COSTRUZIONE che le fasi LS risolvono:
al finale quei gruppi sono a 0 ritardi. Misura read-only sul dump phase-2 (costruzione reale):
buco@slot1 totale 19, **G11 ven buco@slot1 = 2** (vs i ~17 che 30b generava) -> la cura
opera gia' a costruzione nel solve reale (coerente col PASSO 1 su test-harness: 25).

### 2. REGRESSIONE DI TIER: ha scambiato un difetto MEDIUM con uno HARD
I 6 buchi interni sono TUTTI **G11 MERCOLEDI'**, allo slot1 (ora 9): ore occupate
[8,10,11,12(+13)] con buco [9] (date 2026-11-11..12-16, 6 mercoledi' consecutivi). Ironia:
slot0-balancing ha curato il **late-start del venerdi'** (slide slot0->slot1, M18 medium)
creando un **buco interno al mercoledi'** (stesso slot1, ma come H17 HARD). Ha spostato il
difetto di G11 da ven-late-start (medium) a mer-internal-hole (HARD): rigorosamente peggiore
nella gerarchia hard>medium. Il golden ha 0 buchi interni; 30b ne aveva 0; 30c ne ha 6.
Per la direttiva golden-first e per "hard=0 paramount", questo e' il dato che squalifica.

### 3. M15 contiguita' same-day SPEZZETTATA (-81600, il costo medium dominante)
M15 (same-day same-subject should be consecutive, ofMedium(20) x minuti-gap): 4 match in 30b
-> 36 in 30c. Lo shuffle di slot0 riserva le celle ma non preserva l'adiacienza delle materie:
un blocco 2h di Matematica contiguo viene spezzato. La frammentazione e' visibile anche nei
blocchi ricorrenza (5000x1h vs 3626; 1890x2h vs 2362). Costo non anticipato dal gate2 (li'
M15 non era nel modello cosi' com'e' oggi). Si somma all'M20 -32000 (ipotesi confirmata: celle
perse dal riempimento) e M32 -20931.

## Perche' hard migliora comunque (+62)
L'hard improvement viene INTERAMENTE da H29 room-null che crolla (163->89, +74), parzialmente
offset da H0 (3->6 boxed, -6) e H17 (0->6, -6). La riduzione room-null e' un EFFETTO
COLLATERALE del ripiego stanze (il rimescolamento slot0 cambia il pattern di domanda delle
aule e B-ROOM trova stanza a piu' lab), NON il meccanismo slot0 inteso. CAVETE (fedelta', n=1
NON_REPRODUCIBLE): il delta room-null/hard puo' includere varianza run-to-run (30b/447 stavano
a 163/189); M15/6-buchi/full-day sono invece attribuibili meccanicisticamente a slot0-balancing.

## VERDETTO: NON RATIFICARE slot0-balancing default ON. Tenere OFF.

La bandierina risolve pulitamente i 4 AC day-start di TASK-450 (vedi sotto), MA lo fa a costo
di una regressione di tier (6 buchi H17 dove prima 0), dello spezzettamento di M15 (-81600,
il costo medium dominante), e di -3.1pp full-day. Il conto netto e' NEGATIVO sulle metriche di
applicabilita' (full-day, contiguita', buchi): l'ipotesi "oggi il netto e' positivo" e' refutata.
La cura profonda resta TASK-451 (allocazione anticipata coordinata cross-gruppo della prima
ora, 435-style: coordina la risorsa scarsa invece di spostarla shufflando slot0, che e' cio'
che spacca M15 e apre i buchi mer). Decisione di ratifica = maintainer; se si volesse adottare non
si scende sotto n>=3 run (varianza room-null/hard da isolare).

## AC TASK-450 (day-start) - status su 30c
- #1 Nessuna cella (gruppo,giorno-settimana) con scarto >=3 vs golden: **PASS** (nessuna).
- #2 G11 ven 0 ritardi (golden 0): **PASS** (0).
- #3 day-start-anatomy in capture-run.sh: **gia' soddisfatta** (capture-run riga 54).
- #4 Totale giornate in ritardo <= golden+2 (golden 4 -> <=6): **PASS** (1).
Nota: i 4 AC day-start SONO soddisfatti, ma l'adozione e' sconsigliata per i costi
sovraesposti (tier + M15 + full-day). Gli AC misurano il difetto day-start, non il bilancio
complessivo: questa bandierina e' l'esempio canonico di "curano la metrica misurata,
rompono le altre".

## Lascito
- Dump per-fase completi in `phase-transitions/` (phase-2..5; phase-2 = construction-end del
  solve reale, chiude l'anello P1-vs-P2 che mancava per 29b).
- Script room-null lab-vs-nonlab (H29 filter: non-pinnato, non-pseudo) replicato su 30c:
  89 room-null tutte LAB, NON-LAB 0 -> ripiego M-LF regge anche con slot0-balancing.
- Measure slot1-hole su dump construction-end (buco@slot1=19, G11 ven=2) in questo VERDICT.
