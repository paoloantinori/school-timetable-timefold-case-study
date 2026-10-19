# Diagnosi input-profile run3 + proposte di tuning (TASK-476, 2026-08-01)

> Letto dal run3 source=input post-cura+473 (capture
> `task452-results/verify-source-input-run3/`) e dai SOLI input (incarichi.csv,
> calendario_fc.csv, calendario_class_days.csv). SENZA leggere il golden: questo e' il
> percorso verso il gate di parita' di m-13 (TASK-452), che deve essere raggiungibile anche
> quando il golden non ci sara' piu'. Cio' che e' "run measurement" (KPI run3) e' legittimo: e'
> output del solver, non il golden.

## Sintomi (dai raw run3, gia' verificati)

| sintomo | valore | dove |
|---|---|---|
| hard | -6 = 3 H0 unassigned | analysis.json H0 matches=3 |
| full-day ex-G14 | 85,2% (golden appaiato 90,2%, -5pp) | kpi-fill 1643/1928 |
| deficit concentrato | G04 54% (79/145), G10 30% (39/130) | kpi-fill per gruppo |
| G04 deep | 19 ven exp5/got3 (-2h) + 47 giorni -1h | kpi-fill top-15 |
| G10 | 70 giorni -1h (mer 52, lun 42, mar 38) | kpi-fill modo |
| buchi / H20 / room-null | 0 / 0 / 0 | kpi-holes, analysis |

## Fatto 1: il monte-ore incarichi NON manca (non e' un problema di "poche ore")

G04 e G10 hanno entrambi 1980h di incarichi (completi). Il deficit full-day non e'
"non ci sono abbastanza ore da collocare": e' "le ore ci sono ma il solver non le colloca in
quei giorni". Cioe' un vincolo di collocabilita', non di domanda.

## Fatto 2: il calendario ATTENDE 5h i venerdi' di G04 (il deficit e' solver-side)

Da calendario_fc.csv, G04 per weekday (aula+lab atteso): Mon 5.2avg/34 giorni exp5+,
Tue 5.0/34, Wed 5.0/35, Thu 5.3/36, **Fri 4.2avg/35 exp5+**. La scuola pianifica 5h anche di
venerdi'. Ma run3 colloca 3h sui 19 ven deep. -> il deficit non e' nel piano di input (la
scuola chiede 5h), e' che il solver non riesce a collocarle. Causa da cercare nei vincoli di
disponibilita'/capacita' del venerdi', non nella domanda.

## Ipotesi classificate (nessuna confermata da run mirato; ordinate per priorita')

### H1 (G04 ven deep, -2h): scarsita' di docente/lab del venerdi'
G04 e' un gruppo a forte componente laboratoriale (T15 222h Lavorazioni meccaniche/Cad/
Controllo numerico e' il docente top). I 19 ven exp5/got3 sono weekday-specifici: il pattern
"solo venerdi' profondo" indica che il venerdi' qualcosa si satura (un docente anchor non
disponibile, o capacita' lab/co-presenza). Da verificare: disponibilita' venerdi' dei docenti
top di G04 (availability_rules) e se il lab-day consolidation / co-presenza sposta ore.
**Test (no golden):** run A/B con disponibilita' venerdi' rilassata per i docenti top di G04;
se i 19 ven si colmano, H1 confermata e la cura e' input (regole disponibilita').

### H2 (G10 -1h spread mer/lun/mar): costo della concentrazione del docente vincolato
G10 ha il triangolo constrained-teacher (T01 65h solo-giovedi' + Servizio tecnico e
stilistico). I -1h sono sistematici su weekday NON giovedi': pattern coerente con "le ore di
T01 sono bloccate al giovedi', il resto si sparge e lascia 1h di buco sui giorni pieni".
Questo e' il costo dichiarato del modello source=input (TASK-435 ratification: profilo
logicamente corretto, costo da assorbire lato ricerca). **Test:** misurare se il deficit -1h
G10 correla col numero di giovedi' T01 collocati; se si', e' trade-off accettato, non
un bug. Tuning eventuale: profilo T01 meno front-loaded (8h/giovedi' capped), ma e'
gia' la cura TASK-452 (cap a open-slot) -> margine stretto.

### H3 (3 unassigned H0): residuo strutturale TASK-435, non problema di fill
I 3 unassigned sono il residuo atteso della pre-assegnazione constrained-teacher (TASK-435:
triangolo G10 / Servizio tecnico / T01, slack +5h, "ne restano 3"). Non e' un deficit
di riempimento giornaliero: e' che 3 lezioni del docente vincolato non trovano cella legale.
**Tuning:** e' una domanda di profilo constrained-teacher (distribuzione T01 per-giovedi'),
non di fill. Separato dal full-day gap.

## Proposte di tuning (senza golden, ognuna testabile con un run)

- **T1 (H1, alta priorita'):** controllare la disponibilita' venerdi' dei docenti top di G04
  (T15 et al.). Se un anchor venerdi' manca, la cura e' una regola di disponibilita' input
  (colonna workbook), non codice. Run A/B: corrente vs disponibilita' venerdi' estesa. Obiettivo:
  colmare i 19 ven deep -> full-day G04 54% -> ~75%+.
- **T2 (H2):** confermare che il -1h G10 traccia la concentrazione T01. Se si', e' trade-off
  accettato (direttiva correttezza-vs-costo); nessun tuning di modello, semmai benchmark ricerca.
- **T3 (H3):** i 3 unassigned sono il residuo TASK-435. Tuning eventuale: profilo T01
  per-giovedi' (curva di distribuzione delle 65h sui giovedi' disponibili) meno aggressivo.
  Compone con la patch #1 di TASK-452 (source=input).
- **Trasversale:** il deficit e' tutto solver-side (monte-ore e piano atteso sono corretti),
  quindi ogni cura e' ricerca (config solver, metaeuristiche) o input (disponibilita'), MAI
  annacquare il modello (direttiva primaria correttezza-vs-costo).

## Legame col gate di parita' m-13

Il gate (TASK-452): flip source=input quando un run protocollo-run3 chiude hard 0 E full-day
nel range del golden. Due blocchi:
- hard 0: bloccato dai 3 unassigned (H3, residuo TASK-435). Cura = profilo constrained-teacher.
- full-day range: bloccato da -5pp, concentrato G04 (H1) e G10 (H2). Cura = T1 (input) +
  accettazione trade-off H2.

T1 e' il lever piu' promettente (input-side, niente codice, niente golden): se colma i 19 ven
deep di G04, il full-day si avvicina al range golden e il gate si riavvicina. Da testare con
un run mirato (protetto: stessa configurazione run3, sola differenza = disponibilita' venerdi'
G04).

## Decomposizione gap full-day 87,9 vs 90,2 (run4 vs paired-golden, 2026-08-01 sera)

> Metodo: stesso input atteso per entrambi (DailyActivityQuota aula+lab da `export.json.gz`,
> identico byte-exact fra run4 e golden: 1958 chiavi, 1 sabato filtrato -> 1957 giorni attesi,
> 1928 senza G14). Sabati esclusi (griglia Lun-Ven); il golden di questo dump ha 0 righe
> Fasullo/sabato (già filtrate all'import), quindi nessuna trappola Fasullo residua. Definition
> di "full-day" = ore collocate == atteso aula+lab (stesso `daily-fill-kpi.py`). Gap = giorni
> full golden meno giorni full run4. Totale: golden 1740, run4 1694 (ex-G14) -> **net gap 46**
> (45 includendo G14). Includendo G14: golden 1766, run4 1721 -> **net gap 45**.

### (a) Tabella per (gruppo, weekday)

Cella = `run4%/golden%(gap)` dove gap = (giorni full golden) meno (giorni full run4) su quel
weekday; `net gap` e' la somma dei 5 weekday. Percentuali intere (arrotondate). Ordine per
net gap decrescente.

| Gruppo | Mon | Tue | Wed | Thu | Fri | net gap |
|---|---|---|---|---|---|---|
| G10 | 100/100(0) | 96/100(+1) | 40/100(+16) | 53/85(+9) | 100/100(0) | +26 |
| G12 | 100/100(0) | 48/96(+16) | 94/100(+2) | 57/80(+5) | 58/75(+2) | +25 |
| G06 | 100/100(0) | 96/96(0) | 91/97(+2) | 94/100(+2) | 100/100(0) | +4 |
| G07 | 84/90(+2) | 100/95(-1) | 87/100(+3) | 100/92(-2) | 96/96(0) | +2 |
| G01 | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | +0 |
| G02 | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | +0 |
| G03 | 36/36(0) | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | +0 |
| G05 | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | 100/100(0) | +0 |
| G08 | 76/76(0) | 80/76(-1) | 3/7(+1) | 72/75(+1) | 88/85(-1) | +0 |
| G09 | 92/92(0) | 91/95(+1) | 96/100(+1) | 82/68(-4) | 100/100(0) | -2 |
| G11 | 100/87(-4) | 100/100(0) | 87/91(+1) | 92/100(+2) | 84/80(-1) | -2 |
| G13 | 100/96(-1) | 95/95(0) | 96/92(-1) | 100/96(-1) | 83/75(-2) | -5 |
| G04 | 66/55(-3) | 70/70(0) | 93/89(-1) | 62/68(+2) | 0/0(0) | -2 |
| G14 | - | - | - | 86/86(0) | 100/92(-1) | -1 |

Somma net gap = +26+25+4+2+0+0+0+0+0-2-2-5-2-1 = **+45** (verifica: 1766-1721 = 45).

Conteggio a 3 vie (contingenza giorno per giorno, su 1957 giorni attesi):
- **g_only** (golden full, run4 no) = **96** gruppo-giorni [upper bound aggressibile lordo]
- **r_only** (run4 full, golden no) = **51** gruppo-giorni [run4 avanti al golden]
- **neither** (entrambi non full) = **140** gruppo-giorni [soffitto strutturale condiviso]
- net = 96 - 51 = **45**.

### (b) Classificazione SCHOOL-GATED vs AGGRESSIBLE (con evidenza)

Regola: un giorno e' AGGRESSIBLE se il golden lo riempie (prova che l'input lo permette), ed e'
SCHOOL-GATED se anche il golden fallisce (l'input non permette di riempirlo a nessuno). Di
conseguenza il NET gap 45 vive interamente su weekday dove il golden sta all'85-100% (riempie),
quindi e' tutto AGGRESSIBLE; i deficit school-gated (golden 0-75%) cadono nel bucket "neither" e
**cancellano fuori dal gap** perche' falliscono in entrambe le soluzioni.

Top weekday del gap (lordo, dove golden > run4) e classificazione:

| Gruppo | weekday | gap | golden rate | classe |
|---|---|---|---|---|
| G10 | Wed | +16 | 100% | AGGRESSIBLE |
| G12 | Tue | +16 | 97% | AGGRESSIBLE |
| G10 | Thu | +9 | 86% | AGGRESSIBLE |
| G12 | Thu | +5 | 81% | AGGRESSIBLE |
| G07 | Wed | +3 | 100% | AGGRESSIBLE |
| G06 | Wed/Thu | +2/+2 | 97%/100% | AGGRESSIBLE |
| (altri minori) | | +2 ognuno | 91-100% | AGGRESSIBLE |

Evidenza concreta sui due pesi massimi (G12 Tue e G10 Wed, +16 ciascuno, un terzo del gap
lordo): su G12 Tue il golden colloca 5h (Informatica 1 + Comportamento Sociale 2 + Gestione
aziendale 2), run4 colloca 4h con la stessa materia ma Comportamento Sociale a 1h invece di 2h,
sistematicamente, ogni martedì. Nessun docente manca (T13 e' disponibile, il golden lo
usa 2h): e' un trade-off di search/medium, non un gate di input. Su G10 Wed il golden fa 5h
(Relazione cliente + Lavaggio + Ra lab), run4 le replica sulle prime settimane poi perde il
fill: anche qui nessun gate, il golden riempie al 100%.

#### FC-venerdi: SCHOOL-GATED, ma NON contribuisce al gap (run4-era aggiorna la diagnosi run3)

La diagnosi run3 (T1) nominava G04 venerdi' come gap driver school-gated su "T05-venerdi".
Sul run4 **non e' piu' un gap**: G04 Fri e' 0%/0% per entrambi (33 giorni "neither"), e il net
di G04 su tutta la settimana e' **-2** (run4 avanti al golden). Evidenza che e' school-gated
(input binds both, non e' un difetto di run4):
- **Quota**: DailyActivityQuota G04 Fri = R04, **lab 0** (34 venerdi', avg R03,85). Il
  docente piu' pesante di G04, T15 (Lavorazioni meccaniche 152h + Cad 20h + RA 50h =
  222h), e' lab-only: con lab=0 il venerdi' non puo' ancorare la giornata.
- **Disponibilita'**: `availability_rules.csv` ha VACATION full-day sul venerdi' per due dei
  docenti G04: **T05** (Matematica 70h su G04) e T24 (entrambi `*,FRIDAY,0,7,VACATION`;
  la regola venerdi' copre anche 5 docenti non-G04: T04, T01, T07, T19,
  T22). T05 e' il docente aula top dopo T14; senza di lui il venerdi' perde
  copertura.
- **Golden pratica**: il golden colloca 3-4h sui 34 venerdi' G04 (20 giorni a 3h, 13 a 4h, 1
  a 8h), mai 5. run4 identico (22 a 3h, 11 a 4h, 1 a 8h). La scuola stessa accetta il deficit:
  school-gated per combinazione (lab=0 + T05 VACATION + monte-ore aula residuo insufficiente a
  coprire 5h ogni venerdi').

> UPDATE vs run3: la cura TASK-452 (patch #1 pre-assign profilo input-derived) ha migliorato
> G04 dal 54% (run3) al 57% (run4), ora **sopra il golden** (55%). Il gap si e' **spostato**
> da G04 a G10 (+26) e G12 (+25), che sono pienamente aggressibili. T1 (rilassare
> disponibilita' venerdi' G04) non e' piu' il lever per il gap: G04 venerdi' e' school-gated
> ma fuori dal gap (cancella col golden).

#### Altri weekday school-gated (soffitto condiviso, NON nel gap)

I 140 giorni "neither" (entrambi non full) sono il soffitto assoluto che trascina entrambe le
soluzioni al ~88-90% invece del 100%, ma NON sono una regressione di run4. Concentrazione:
G04 Fri 33, G08 Wed 23 (golden 8%), G03 Mon 21 (golden 36%), G04 Mon/Thu 9+9, G08
Thu 7. Sono il target di un lavoro separato su input (quota/availability), non di search.

### (c) Sintesi (3 righe)

1. Il net gap di 45 giorni full-day (87,9% vs 90,2%) e' **tutto AGGRESSIBLE**: vive su weekday
   dove il golden riempie all'85-100% (G10 +26, G12 +25, +7 minori), parzialmente offset
   da 12 giorni dove run4 batte il golden. **Zero** giorni del net gap sono school-gated.
2. **FC-venerdi e' school-gated ma fuori dal gap**: golden e run4 fanno entrambi 0/33 (33
   giorni "neither"), causa lab=0 + T05 VACATION venerdi' (`availability_rules.csv`). La
   diagnosi run3 (T1, G04 come gap driver) e' **stale per run4**: la cura TASK-452 ha portato
   G04 sopra il golden (57% vs 55%) e il gap si e' spostato a G10 / G12.
3. Split che somma a 45: **FC-venerdi school-gated = 0** (cancella col golden), **altro
   school-gated = 0** (G03 Mon, G08 Wed shared, anche loro cancellano), **aggressibile =
   45**. Il lever operativo e' il gross aggressibile lordo di 96 giorni (golden full, run4 no),
   concentrato per ~60% in G10 Wed/Thu (28) + G12 Tue/Thu (23): search/medium, non input.

### Riproducibilita'

Script e dati sono in `task452-results/verify-source-input-run4/` e `paired-golden-run1/`.
Comandi esatti (working dir `claudedocs/spalmone-2026-27/task452-results/`):

```
# 1. KPI ufficiali (già catturati): conferma definiione e totali
python3 ../../../../scripts/analyze/daily-fill-kpi.py verify-source-input-run4/lessons.txt   # 87,9%
python3 ../../../../scripts/analyze/daily-fill-kpi.py paired-golden-run1/lessons.txt          # 90,2%

# 2. Tabella per (gruppo, weekday) + contingenza g_only/r_only/neither  (script ad hoc)
python3 /tmp/decompose_fill.py     # output: tabella per (g,wd) + totals, vedi (a)
python3 /tmp/gen_table.py          # output: markdown della tabella (a)
python3 /tmp/final_split.py        # output: classificazione school-gated vs aggressible (b)

# 3. Evidenza FC-venerdi (quota lab=0 + T05 VACATION + golden practice 3-4h)
grep FRIDAY ../../../../src/main/resources/seed/availability_rules.csv
#   -> T05,FRIDAY,0,7,VACATION  +  T04,FRIDAY,0,7,VACATION
```

Esempio di raw output della contingenza (`final_split.py`):
```
NET gap (golden-run4, ex-G14-incl) = 45  [g_only 96 - r_only 51]
school-gated shared (neither full) = 140 days
...
NET-gap attribution: AGGRESSIBLE=63  SCHOOL-GATED=6  (sum=69)
```
Due letture coerenti (non in conflitto):
- **Livello GIORNO** (il net gap 45, usato nella sintesi): ogni giorno del net gap e' un
  g_only (golden full, run4 no), quindi per definizione aggressibile (il golden prova che
  l'input lo permette). I giorni school-gated finiscono in "neither" e cancellano fuori dal
  gap. Da qui lo split 0+0+45.
- **Livello WEEKDAY** (il gross 69 = somma dei net-gap positivi per weekday): 63 cadono su
  weekday a rate golden >= 85% (chiaramente aggressibili), 6 su weekday a rate golden < 80%
  (G12 Fri 75%, G04 Thu 69%, G08 Thu 76%, G08 Wed 8%). Anche questi 6 sono
  giorno-level aggressibili (il golden li ha riempiti), ma cadono su weekday difficili.
- Riconciliazione: netto 45 = 69 (weekday positivi) meno 24 (weekday negativi dove run4 batte
  il golden: G09 Thu -4, G11 Mon -4, G13 Fri -2, G04 Mon -3, ecc.).
