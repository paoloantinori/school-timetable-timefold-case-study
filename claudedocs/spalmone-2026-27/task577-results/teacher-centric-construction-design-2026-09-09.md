# Design draft: costruzione teacher-centric (per la review di maintainer, 9/09)

> ESITO FASE A (banco TeacherPairMajorBenchTest, 9/09): CRITERIO PRIMARIO
> FALLITO e premessa FALSIFICATA. Concentrazione mediana top-3 delle coppie:
> OFF 0.83, ON 0.85 (+2pp, soglia +5pp), golden 0.80. Il builder attuale
> produce GIA' la forma-per-coppia al livello template (OFF e' sopra il
> golden): l'ordinamento pair-major non e' la leva. Costo misurato: H34 a
> costruzione 21 -> 28 (G13 +4 celle). Prototipo resta default-OFF,
> annotato nel codice. La Fase B (stamp per blocchi-coppia) non parte su
> questa premessa: il filone celle vuote resta di distribuzione (pool-cap,
> ratificato) e teacher-locked (ciclo in corso), non di shape.

## L'evidenza che motiva il cambio (misurata oggi sul golden)

La pratica del planner umano, quantificata su `seed/lessons.csv` (escluse le
trappole Stage/Fasullo): **170 coppie (docente, gruppo) con almeno 20h; la
mediana vive in 7 celle (weekday, slot) che da sole tengono l'80% delle ore
della coppia**. Esempi estremi e tipici:

| coppia | ore | celle distinte | top-3 celle |
|--------|-----|----------------|-------------|
| T05 / G03 | 129 | 4 | 83% |
| T17 / G01 | 143 | 6 | 69% |
| T09 / G02 | 130 | 7 | 65% |
| T03 / G03 | 143 | 7 | 57% |
| T16 / G01 | 179 | 6 | 55% |
| T15 / G04 | 222 | 10 | 38% |

Il planner umano non costruisce giorni di gruppo: assegna alla COPPIA un
piccolo pacchetto di celle settimanali e lo ripete per tutto l'anno ("spalmone"
= spalma). Le coppie grosse (T15) usano piu' celle ma restano nell'ordine
di decine, non di centinaia.

La nostra costruzione e' l'opposto: template per GRUPPO (`WeeklyTemplateBuilder`
greedy sui corsi del gruppo), stamp cronologico front-loading. Il censimento
di oggi (40 celle dichiarate vuote a costruzione, di cui 25 FC da
front-loading) e' il sintomo; la cura pool-cap-fill (prototipo DEFAULT OFF, banco verde)
aggiusta la DISTRIBUZIONE ma non la FORMA: le celle del template gruppo-centrico
non sono le celle della coppia.

## Infrastruttura che gia' punta in questa direzione (zero nuovo da inventare)

1. **PairCellGuard + M38 (TASK-576 F7-C, ratificato peso 1)**: le celle PAIR
   `teacherId|groupId|weekday|slot` esistono gia' come concetto di prima classe.
   Il builder pair del template e' OPT-IN (il flip congiunto fu bocciato per
   +18 H32-family in costruzione, quotaTailMovesPlacedOn 59 vs 41): ma quello
   era il vincolo M38 che PUNIVA le deviazioni della LS; qui si parla di far
   NASCERE la costruzione dentro le celle pair, che e' un punto di integrazione
   diverso (a monte, non a valle).
2. **fingerprint-sharpness-kpi.py**: misura la nitidezza settimana-docente, il
   KPI naturale del paradigma (esiste, va solo consumato sul dump di
   costruzione).
3. **construction-vs-golden-viz.py**: la similarita' mensile si vede dal
   POST_CONSTRUCTION (0.992/0.993 oggi: la DISTRIBUZIONE e' gia' giusta; la
   forma per-coppia e' il delta rimasto).

## Proposta incrementale (banco-first, flag-gated, niente rewrite)

**Fase A (ordinamento pair-major del builder esistente)**: `WeeklyTemplateBuilder`
costruisce oggi il template per gruppo con greedy sui corsi. Variante dietro
flag: ordinare i corsi del gruppo per (docente, gruppo) hardest-first (ore
settimanali della coppia decrescenti) e collocare le celle pair PRIMA delle
celle soggetto-generiche. Il template resta per gruppo (M32 e la stamp machine
non cambiano), ma la sua FORMA interna segue le coppie. Costo: giorni-uomo 1-2.
Misura al banco: ricorrenza a fine costruzione (target: > oggi), conflitti H1
aggiunti = 0, celle vuote non peggiori.

**Fase B (stamp per blocchi-coppia)**: le celle pair del gruppo diventano
L'UNITA' di piazzamento dello stamp: il blocco coppia viene timbrato su tutte
le settimane disponibili della coppia (rispetto di quote H32/H34 per giorno e
del pool-cap per-data, che con la ripetizione settimanale e' NATURALMENTE
uniforme: le due cure convergono). I gruppi senza coppie nette (G14, G13)
tengono il path gruppo-centrico. Costo: maggiore; solo se la Fase A mostra
guadagno di ricorrenza senza conflitti.

## Rischi aperti (da decidere con maintainer prima della Fase B)

1. **H1 diventa il vincolo in costruzione**: oggi il template gruppo-centrico
   non puo' creare conflitti docente fra gruppi per costruzione? No: li crea e
   li risolve lo stamp con skip. Con l'ordinamento pair-major, due gruppi che
   vogliono lo stesso docente nella stessa cella collidono PRIMA: serve un
   registro docente-settimana nel builder (esiste gia' in forma parziale nel
   placement greedy).
2. **M32 resta gruppo-keyed**: il reference del vincolo e' per (corso, weekday,
   slot); le celle pair lo alimentano bene, ma la semantica "deviazione dal
   template del corso" va riverificata quando il template nasce da coppie.
3. **La bocciatura del flip congiunto M38-builder NON si riapre da sola**: il
   guard quotaTailMovesPlacedOn (59 vs 41 celle sotto-quota) va reimpostato
   come criterio del banco della Fase A: se la forma pair peggiora il fill
   in costruzione, la Fase A muore li'.

## Piano di misura (tutto al banco, zero cicli)

1. PoolCapFillBenchTest (censimento celle vuote) + WeeklyTemplateConstructionRealSeedTest
   (ricorrenza a fine CH ~76-77% oggi: e' il target da battere).
2. fingerprint-sharpness-kpi.py sul dump di costruzione (nuovo consumo).
3. construction-vs-golden-viz.py: la similarita' mensile non deve regredire.
4. Criterio di morte pre-registrato: conflitti H1 aggiunti > 0 oppure celle
   vuote peggiori di +5 rispetto al controllo (a flag spenti il comportamento
   e' byte-identico, forma opt-in standard).
