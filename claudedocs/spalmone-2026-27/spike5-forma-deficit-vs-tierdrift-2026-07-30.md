# SPIKE 5 - Forma del deficit vs deriva tier (causa o coincidenza?) - 2026-07-30/31 notte

Mandato: determinare se i deficit profondi di fine anno sono lo **stesso fenomeno**
della deriva tier (generici esauriti presto -> buchi di fine anno restano aperti) o un
fenomeno **distinto**. Read-only sui dump gia' catturati (447 ricalcolato, 30b, 30d),
zero run nuovi. Chiude la linea aperta da SPIKE 3 (forma/concentrazione) + SPIKE 4
(G04 ven = ricerca) + TASK-457 (firma chiusa come leva).

## Consegna (3 righe)
- **Ipotesi:** il deep-deficit di fine anno e' causato dalla deriva tier (i generici
  condivisi finiscono presto -> scarsita' -> buchi aperti), quindi curare la deriva
  (bilanciare i generici) chiuderebbe i buchi.
- **Numero misurato (cross-run, su ore totali IDENTICHE run=golden=incarichi per
  gruppo, nessun confondimento):**
  - **(A) pacing del gruppo** - all'**85-88% delle celle deep** il gruppo e' **AVANTI o
    even** sul proprio monte-ore annuo alla data del deficit (NON indietro): 447 23/27,
    30b 28/32, 30d 25/29. Il deficit **non e' scarsita'** di ore.
  - **(B) drenaggio generici cross-gruppo** - i generici sono drenati (`resTot run <
    golden`) al 69-90% delle celle deep, MA e' una proprieta' **globale** del run
    (`teacher-tier-kpi.py`: generici **sopra** golden ott-feb +0,6..+3,2pp, **sotto**
    mar-giu -1,4..-6,5pp, picco mag/giu), presente a tutte le celle tardive, non
    specifica di quelle deep -> **correlazione, non causa**.
  - Celle piu' tarde (mag/giu, es. G04 2027-05-28): budget di gruppo **esaurito**
    (residuo proprio ~0): front-loading del **gruppo**, che persisterebbe anche
    bilanciando i generici alla perfezione.
- **Tenere/buttare: TENERE "distinto come causazione", BUTTARE ogni cura del deficit
  via deriva tier / bilanciamento generici.** Deep-deficit e deriva tier **co-muovono**
  a fine anno (la firma di TASK-457 regge come statistica) ma il deficit **non e'
  scarsita'** (gruppo avanti, ore totali identiche a golden): attaccare i generici non
  riempirebbe i buchi. La leva resta il **coordinamento cross-gruppo del giorno**
  (famiglia prima-ora / tripletta / SPIKE 4 / G04-ven). Ratifica TASK-457 (downgrade
  "causa debole, nessuna leva") e SPIKE 4 ("ricerca non capacita'"), ora cross-run e
  mechanism-tested su dati a totale identico.

## Metodo (il meccanismo, non la correlazione)
Per ogni cella deep-deficit (g, d) [deficit = expected(aula+lab DailyActivityQuota) -
piazzato, >= 2h, esclusi G14 + sabati off-grid], due test run-vs-golden:
- **(A) pacing del gruppo**: `resOwn_R(g,d) = sum_t [incarichi(t,g) - ore che t ha
  speso su g entro d]`. Se il deficit fosse scarsita', g sarebbe **dietro**
  (`resOwn_run > resOwn_gold`, meno speso). Misuriamo il contrario (g avanti).
- **(B) drenaggio cross-gruppo dei generici** (deriva tier classica): per i generici
  (>= 8 gruppi, stessa soglia di `teacher-tier-kpi.py`) che servono g,
  `resTot_R(t,d) = incarichi_totali(t) - ore totali di t (tutti i gruppi) entro d`;
  aggregato e confrontato run vs golden.
Expected = `export.json.gz` dailyActivityQuotas (aula+lab, run-independent, caricato da
30b). Golden da `data/lessons.csv`. Script: `/tmp/spike1_deficit_vs_tierdrift.py`
(ephemeral; numeri riproducibili dai dump).

## Misure per run

| run | deep cells (per gruppo) | (A) g AVANTI/non-scarcita' | (B) generici drenati (globale) |
|-----|-------------------------|----------------------------|--------------------------------|
| 447-recalc | 27 (G04 21, G08 2, G13 2, G12 2) | 23/27 = **85%** | 24/27 = 89% |
| 30b | 32 (G04 23, G08 7, G12 2) | 28/32 = **88%** | 22/32 = 69% |
| 30d | 29 (G04 22, G08 4, G12 2, G13 1) | 25/29 = **86%** | 26/29 = 90% |

Deep count sulla metro export (aula+lab), coerente con serie-storica (~31 per 30b);
G04 resta ~3/4 del deep (conferma SPIKE 3). Cellule tarde G04 mag/giu: residuo
proprio run ~0 (budget esaurito), golden ancora 5h -> golden pacing uniforme a 750h,
run front-loada e si esaurisce prima.

## Interpretazione: co-occorrenza senza causalita'
1. La deriva tier e' **vera e globale** (tier-kpi mensile, firme identiche nei 3 run).
2. Ma **non e' la causa** del deficit: se lo fosse, curarla chiuderebbe i buchi. Invece
   il gruppo **non e' hour-starved** (85-88% avanti; ore totali identiche a golden).
3. Il deficit e' un difetto di **shape/allocazione**: il run distribuisce gli stessi
   750h con forma peggiore del golden (giornate tarde sparse, anche quando il budget
   non e' ancora esaurito; e lo esaurisce presto quando lo e'). Stesso family della
   prima ora e di G04 ven (SPIKE 4): buchi di coordinamento cross-gruppo sul giorno,
   non di ore mancanti.
4. Conservare la distinzione importa per le priorita': bilanciare i generici (le vecchie
   piste "drift variato" cancellate con TASK-457) non e' la cura; la cura e' la
   coordinazione del giorno (TASK-451 / tripletta).

## Robustezza / onesta'
- **Nessun confondimento da totali**: incarichi == golden == run per ogni gruppo (G04
  750=750=750; totale 10501=10501=10498). Quindi "g avanti" e' pacing puro, non artefatto
  di golden sotto-collocante.
- Cross-run agreement stretto sul numero portante (A: 85-88%). (B) piu' variabile (30b
  69% vs 447/30d 89-90%) perche' 30b ha piu' celle deep su G08, i cui generici sono
  meno drenati; non cambia il verdetto (il numero portante e' A).
- Misura allineata al metro serie-storica (DailyActivityQuota aula+lab, sabati esclusi).
- Domanda aperta (scritta, non bloccante): il front-loading del **gruppo** (cellule
  tarde a budget esaurito) e' un suo difetto di pacing agganciabile (es. far rispettare
  meglio lo shape day-fill)? E' la stessa famiglia del difetto day-shape che M20/M33
  gia' attaccano; va misurato a parte se si vuole una leva sul pacing del gruppo, ma NON
  e' la deriva tier e NON si cura bilanciando i generici.

## Riferimenti
- TASK-457 (firma chiusa come leva, downgrade verifica maintainer 2026-07-30): QUADRO colonna 1.
- SPIKE 3 (`spike3-forma-deficit-2026-07-30.md`): concentrazione del deep (G04 ven dominante).
- SPIKE 4 (`spike4-g04-venerdi-feasibility-2026-07-30.md`): G04 ven = ricerca non capacita'.
- `serie-storica-kpi-2026-07-30.md`: metro DailyActivityQuota dichiarato in testa.
