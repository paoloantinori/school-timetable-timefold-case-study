# DIAGNOSI: i 6 H17 residui di probe-run2 (-6hard). G11 mercoledi' ore 10.

> 2026-08-02, sessione alba4. Read-only dai capture `ab-ef-probe-1m-run2/` + golden
> `seed/lessons.csv` + dump construction `target/construction-lessons.txt`. Catena causale
> verificata link-by-link (skill causal-chain). L'ipotesi dell'handoff alba3 ("pattern lab-day
> G13 TASK-451/463, ortogonale a EF") e' **parzialmente corretta sull'ortogonalita' a EF ma
> ERRATA sull'identita'**: non e' il pattern G13. Sotto, chi sono davvero e perche' restano.

## L'anomalia (dai raw)

`analysis.json`: `H17 - Vertically compact` = **-6hard / 6 matches**. Nessun altro hard residuo
(H0 = 0, EF 18/18 boxed chiusi). `kpi-holes.txt`: i 6 buchi sono **tutti G11, 6 mercoledi'
consecutivi, hole fisso a ore 10**:

```
G11  2026-11-11  occupied=[8,9,11,12,13]  hole=[10]
G11  2026-11-18  occupied=[8,9,11,12,13]  hole=[10]
G11  2026-11-25  occupied=[8,9,11,12,13]  hole=[10]
G11  2026-12-02  occupied=[8,9,11,12,13]  hole=[10]
G11  2026-12-09  occupied=[8,9,11,12,13]  hole=[10]
G11  2026-12-16  occupied=[8,9,11,12,13]  hole=[10]
```

Shape del giorno (run, le 6 settimane identiche a meno del docente delle 11 che cambia periodo):
`8-9 Matematica(T08) | 10 HOLE | 11 EduCiv/Prep(T23/T05) | 12 Storia(T02) | 13 Informatica(T06)`.
Tutte `pinned=false` (solver-placed, non pin autoritativi).

**Non e' un pattern della scuola.** Il golden e' compatto (8-9-10-11-12) su tutte e 6 le date e ha
**0/34 mercoledi' con buchi interni** (controllati su tutto l'anno). Il golden usa una composizione
diversa per quel giorno: `{T05 ProgTermoidraulica, T23, T04 Lingua, T11
Personalizzazione}`. Quindi il buco a ore 10 e' un **artefatto del solver**, non una pratica della
scuola che il solver sta inseguendo.

## Riproducibilita' (falsificabilita' passata)

I 6 H17 di run4 (`ab-ef-run4/kpi-holes.txt`) sono le **STESSE identiche celle** (G11, stesse 6
date, hole=[10]). run1 e run3 (good seed, 0 hard) hanno **0 buchi interni**. Dunque e' un ottimo
locale **riproducibile** del bad seed, non rumore di scatter.

## Catena causale (verificata, non assunta)

**1. La costruzione (fase 1) piazza G11 mercoledi' COMPATTO, senza buco.**
`target/construction-lessons.txt` sulle 6 date:
```
8-9 T11 Personalizzazione | 10-11 T04 Lingua Italiana | 12 T23 Economia
```
Shape 8-9-10-11-12, hole=[]. Composizione {T11, T04, T23}. La costruzione NON
introduce il buco. (A fine costruzione ci sono 105 buchi H17 altrove; il totale scende a 6 nel
finale, quindi la LS ne chiude ~99 e questo e' tra i residui.)

**2. La Local Search (fasi 2-3) sostituisce la composizione.** Il finale ha una composizione
completamente diversa, {T08 Matematica x2, T23, Barucchieri, T06}, piazzata come
8-9 _ 11-12-13. La LS rialloca i docenti per risolvere altri conflitti hard (H1 teacher conflict:
il day parte da -2262hard e la LS porta il timeslot-tier a -2148) e nel farlo sceglie questa
composizione.

**3. Questa composizione e' strutturalmente NON compattabile.** Ricerca esaustiva (tutte le
assegnazioni dei 5 docenti {T08,T08,T23,Barucchieri,T06} sugli slot 8-12 e
9-13, rispettando H1): **nessun arrangement compatto esiste**. Meccanismo (disponibilita' dei
docenti sul mercoledi', dal dump):
- **T06** e' saturo: occupato @8,9,10,11,12,13 (FREE@[]). Insegna 6 ore (G01 x2, G10,
  G07, G13, G11). La sua lezione G11 e' lockata a 13h (unico slot rimasto).
- **T02** libero solo @13, ma @13 e' preso da T06 (G11) -> lockato a 12h.
- **@10 e' bloccato** per T08 (occupato @10 con G12) e T23 (occupato @10 con G04).
- Nessun docente della composizione puo' collocarsi a 10h. Il buco e' intrinseco a questa scelta
  di materie, non alla loro disposizione.

**4. La LS non scappa dal local optimum.** `solverConfig.xml` Phase 2 ha GIA' una metaeuristica:
`<acceptor><entityTabuSize>7>` (tabu search, NON hill-climbing), `<forager><acceptedCountLimit>1000>`,
e **quattro** selettori (`changeMoveSelector`, `swapMoveSelector`, `pillarChangeMoveSelector`,
`pillarSwapMoveSelector`); il nome del branch `feature/cartesian-block-entity` e' storico, NON cabla
alcun `cartesianProductMoveSelector` nel config. Chiudere @10 richiede un **cambio di composizione**
(portare fuori una lezione G11 e portarne dentro una dal docente libero @10): una mossa coordinata
cross-gruppo. Le mosse singole (change/swap) sono H17-neutre:
- riempire @10 spostando la coda sposta solo il buco (T06 non puo' shiftare 13->12, busy
  @12 con G13);
- la swap @10<->@11 tra gruppi dello stesso docente (T23 G04 <-> G11) sposta il buco da
  G11 a G04, hard-neutra.
Tabu + pillar + accepted-forager GIA' girano e lasciano comunque il buco (verifica meccanica
`solverConfig.xml:46-131`, alba5 2026-08-02): la strategia di accettazione laterale NON allarga il
vicinato delle mosse, quindi non compone la cascata cross-gruppo a 3+ mosse che chiude @10. Spike
`H17HoleCloseDiscriminatorTest` (export bad-seed fedele H1/H3=0 H17=-6): 1-move fill 0/6, 2-move
evict+fill 0/6; la cella @10 e' saturata di docenti d'aula G11. La cura NON e' la metaeuristica
(e' gia' attiva) ma un composition-aware hole-closer a profondita' 2-3: **TASK-481 Opzione A**
(ratificata Fedora 02/08). Vedi `task481-results/PROPOSTA-cura-h17-2026-08-02.md`.

## EF e' prosciolto (ipotesi handoff #3 confutata)

L'handoff alba3 elencava 3 ipotesi per "perche' il ciclo li lascia", tra cui: *"sono nati dal
placement EF stesso, che piazza su celle libere senza guardare la compattezza"*. **Confutata
meccanicamente**:
- EF piazza **solo lezioni boxed** (unassigned) in celle conflict-free. La cella @10 mercoledi' e'
  H1-bloccata per tutti i docenti G11 della composizione, quindi EF non avrebbe mai potuto
  piazzarci una lezione.
- Il cambio di composizione (T11/T04 -> T08/Barucchieri/T06) e' una mossa
  di **Local Search** (change cross-date), non di EF: EF non scambia lezioni gia' piazzate.
- Riscontro cronologico: la costruzione (pre-LS, pre-EF) e' compatta; il buco appare nel finale
  (post-LS). EF agisce tra B-TIMESLOT e B-ROOM sui boxed, non sulla composizione piazzata.

EF non crea ne' puo' correggere il buco. **L'adozione di EF e' ortogonale a questi 6 H17.**

## Verdetto (riframato 2026-08-02, direttiva maintainer: 0-hard BINARIO e BLOCCANTE end-to-end)

**I 6 H17 sono ORTOGONALI a EF (attribuzione causale: EF non li crea/non li corregge, verificato
meccanicamente) ma NON sono trade-off accettabile.** 0-hard e' binario e bloccante end-to-end:
qualunque hard>0, non importa quanto piccolo (6 buchi / 12.494 lezioni = 0,048%; 1 gruppo su 14), e'
un **difetto aperto BLOCCANTE** per il path input. EF e' ratificato DEFAULT ON perche' chiude la
coda boxed (H0) in modo deterministico (probe 18/18 + discriminatore uncapped 5/5 + A/B 2/2), ma
l'hard TOTALE del path input resta seed-dipendente (good seed 0, bad seed -6 H17) finché questi 6
H17 non sono curati: **il path input NON e' hard-0 end-to-end**. m-13 (source=input default) richiede
hard-0 end-to-end, quindi dipende anche dalla cura di questi H17, non solo da EF.

I punti che reggono (attribuzione causale, invariati):
- pre-esistenti (run4 @100k, identiche celle, **prima** del knob EF);
- ortogonali a EF (meccanicamente dimostrato);
- non un pattern della scuola (golden 0/34);
- con un arrangement compatto **possibile** (la costruzione lo raggiunge, il golden lo mantiene):
  e' un difetto di ricerca, non del modello.

**La cura e' lato search, non EF, non modello.** Per la direttiva primaria (correttezza logica del
modello giudicata solo su se': H17 e' corretto, il golden fa 0), il model non si tocca. Cura =
**TASK-481** (Opzione A ratificata Fedora 02/08: composition-aware hole-closer deterministico a
profondita' 2-3). CORREZIONE FATTUALE (alba5 2026-08-02): una versione precedente di questo verdetto
diceva "la leva e' la metaeuristica (forager di default -> tabu/late-acceptance; manca
l'accettazione laterale)". FALSO: tabu (`entityTabuSize 7`), pillar (4 selettori) e forager
`acceptedCountLimit 1000` SONO gia' attivi in Phase 2 e lasciano comunque il buco. L'accettazione
laterale non allarga il vicinato delle mosse e non compone la cascata cross-gruppo a 3+ mosse che
chiude @10 (cella saturata di docenti d'aula; spike 1/2-mosse 0/6). La cura e' il composition-aware
hole-closer (libera un docente @cell cross-gruppo + riempi + backfill), non la metaeuristica.
Da prototipare flag-gated default OFF con A/B su bad seed, separato dalla ratifica EF (H0).

## Puntatori ai raw
- Capture: `task478-results/ab-ef-probe-1m-run2/{analysis.json,kpi-holes.txt,lessons.txt,export.json.gz}`
- Riproducibilita': `task478-results/ab-ef-run4/kpi-holes.txt` (identico), `ab-ef-run{1,3}` (0 buchi)
- Golden: `src/main/resources/seed/lessons.csv` (G11 mercoledi' compatto, 0/34 hole)
- Construction: `target/construction-lessons.txt` (G11 mercoledi' compatto, composizione diversa)
- Config: `src/main/resources/solverConfig.xml` Phase 2 (tabu `entityTabuSize 7` + forager `acceptedCountLimit 1000` + 4 selettori change/swap/pillarChange/pillarSwap; NESSUN `cartesianProductMoveSelector`, il nome del branch e' storico)
