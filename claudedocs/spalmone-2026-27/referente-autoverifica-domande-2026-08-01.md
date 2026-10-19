# Auto-verifica delle domande referente dai dati (Fable, 2026-08-01)

Metodo (direttiva maintainer 01/08, precedente: la Q20 chiusa dal foglio calendario): per ogni
domanda aperta del doc referente, PRIMA si tenta la risposta dai fogli di INPUT (calendario,
incarichi, seed), POI dal golden come oracolo; resta domanda solo cio' che i dati non
decidono. Ogni verdetto qui sotto e' stato ricomputato meccanicamente su
`seed/lessons.csv` (trappole escluse: Fasullo, sabati dove rilevante),
`seed/calendario_class_days.csv` e `Copy of Calendario scolastico 2026_2027.xlsx`.

## Risolte dai dati (escono dalla lista domande, diventano conferme-cortesia o notifiche)

**Q20 (G12, 12h parallele) -> RISOLTA DALL'INPUT: aula normale.**
Tab "G12" del calendario: Ore 604 / STAGE 295 / Lab. 91 (totale 990) = identico al nostro
seed (A 604 / S 295 / L 91). I giorni-L (apr 52, mag 34, giu 5) coincidono mese per mese con
Servizio tecnico e stilistico (32/14/0) + Barberia (20/20/5) del golden. Il Servizio
stilistico di T26 (30h, che include le 12 parallele del giovedi') cade tutto set-mar,
in mesi con **zero ore lab pianificate**: per il piano della scuola quelle ore sono aula.
Il seed gia' lo modella cosi' (`subject_overrides.csv` de-tagga Servizio stilistico per
G12).

**Q19 (G14 ospite) -> RISPOSTA DI referente (01/08 pomeriggio): "G14 andra' in R10".**
referente conferma che G14 non toglie mai il laboratorio a una titolare e CORREGGE la nostra
inferenza dai giorni-L ("ospite nel lab"): le ore co-schedulate di G14 si fanno in aula,
specificamente l'**R10** (stanza esistente nel seed tra le aule regolari). La sostanza
occupancy-wise non cambia (G14 non conta come occupante del lab, gia' cosi' nel modello:
de-tag in subject_overrides.csv); la semantica fisica ora e' decisa. Rifiniture candidate:
(a) escludere le coppie G14 dall'esenzione H2 della co-presenza (464), che per G14 non
serve piu'; (b) steering di stanza delle ore G14 verso R10 (preferenza, da valutare
costo/beneficio). Lezione di metodo: l'inferenza dai giorni-L era plausibile ma sbagliata
sull'etichetta fisica; dove referente puo' rispondere, la conferma resta preziosa.

**T1 (professionalizzanti G12: mer+gio nostro vs gio+ven del tab) -> il golden smentisce
ENTRAMBI, fa fede la pratica.** Materie professionalizzanti G12 dal 19/11 per weekday:
**Mon 66, Thu 51, Tue 20, Wed 18, Fri 0**. Il "solo giovedi' e venerdi'" del tab e' violato
massicciamente dal golden stesso (66 ore di lunedi', zero di venerdi'); anche il nostro
mer+gio non descrive la pratica. Per la chiave H-vs-M (maintainer 01/08): regola scritta non
praticata -> nessun vincolo hard; M29 resta com'e' (shaping day-edge a residuo calibrato).
Notifica a referente della doppia discrepanza, non domanda bloccante.

**T5 (Sicurezza 1e in 2 giorni cumulativi) -> golden non la pratica** (8h spalmate su 8
settimane a 1h). Regola scritta non praticata -> notifica, nessuna modellazione.

**T6 (termo prime 4 ore) -> golden-first decide l'interpretazione**: la pratica e' il blocco
mattutino 8-13 (75% in 8-12, il resto scia del blocco 5h); si adotta l'interpretazione
"blocco mattutino", gia' rispettata. Conferma-cortesia.

**T7 (stage G10 no-lunedi') -> GIA' GARANTITO DALL'INPUT.** Il calendario di input marca
le ore S di G10 su Tue 56 / Wed 64 / Thu 60 / Fri 64 / Sat 56, **zero lunedi'**. Lo stage
e' confinato alle date del foglio (filtro VRP + H25/H26), quindi il no-lunedi' e' input-enforced:
nessuna regola nuova. Conferma-cortesia.

**T8a (Figurino / Personalizzazione / Tecnologia e creativita': teoria o pratica?) ->
RISOLTA DAL GOLDEN** (tabella TASK-471): Personalizzazione avg 1,00 (0 giorni-2+ su 60,
tutti i gruppi) e Figurino ~1,0-1,2 si comportano da TEORIA (1h/giorno); Tecnologia e
creativita' avg 3,00 (blocchi 3h al 100%) si comporta da PRATICA (blocco ammesso). Il set
teoria di M-TR/471 si allinea a questo; conferma-cortesia nel workbook.

**T8b (alias T06 info+gest) -> GIA' CHIUSA**: 0/509 giorni con entrambe, alias inerte.

**T9 (T16 3h-non-6) -> chiave H-vs-M applicata**: 93% di rispetto, 4 lunedi' 6h + 6x5h
concentrati nei moduli G05 apr-mag / G14 mar-apr, non capacity-forzati -> giorni
intensivi intenzionali, M-alto-valore a residuo calibrato, NESSUN cap 3h (vieterebbe il
golden). Report occorrenze ripetibile gia' in scripts/analyze. Notifica, non domanda.

**T10 (doppio R16: chi e'?) -> PARZIALMENTE RISOLTA DALL'INPUT**: il seed ha gia'
`LabAssistant(T14, "moda")` (dal docx TASK-368/369), coerente col tab; nel golden
T14 appare solo su Lingua Italiana perche' l'assistente non e' docente-di-record
(il golden non puo' vederlo: nessuna contraddizione). Residuo vero: T20 come secondo
assistente moda (non nel seed) e i budget orari del doppio (100+100 / 100+85) che non
modelliamo. Domanda ristretta.

**T2 (T20 serializzazione) / T3 (T21 ore di mezzo) -> golden non le pratica**
(T2: materie spalmate tutto l'anno; T3: 26/42 ore alle prime ore, l'opposto). Con la chiave
H-vs-M: violazione maggioritaria = non "quasi sempre rispettato" -> non-regola o desiderio,
si notifica senza modellare. Se referente un giorno le vorra', diventeranno M deboli.

**T4 (T11 curriculum post-stage) -> PARZIALE golden-first**: per G10 la pratica e'
pulita (esattamente 6h di Comportamento sociale post-stage in 3x2h) e si puo' modellare
subito (TASK-469, quasi-gratis); per G08 (5h di Competenze di cittadinanza) e G11
(nessun match pulito) il perimetro resta da chiedere. Domanda ristretta al perimetro.

## Le domande VERE rimaste: DECISE DA maintainer (2026-08-01, meta' mattina)

1. **Rotazione acconciatura -> INPUT.** maintainer: "prassi e' passarci l'input"; la rotazione
   diventa un foglio del workbook (docente, giorno, slot, gruppo, periodo). La generazione
   autonoma resta l'extra mile DOPO il resto (TASK-467 fase 2, design pronto, gated).
2. **Curriculum post-stage (era T4) -> TARGET ~6: "almeno 6, ma privilegiare un numero
   vicino a 6"** (precisazione maintainer). Forma del vincolo M ASIMMETRICA: penalita' piena per
   il deficit sotto 6 ore post-stage, penalita' DEBOLE di preferenza per l'eccesso oltre 6
   (il numero non deve gonfiarsi a parita' di condizioni). Cautela di design: dove la
   materia-curriculum prosegue post-stage per volumi propri (G11, 29h di
   Personalizzazione), la preferenza sull'eccesso non deve combattere calendario e
   riempimento: li' vale la riserva minima, non la compressione. Perimetro: {G10
   (Comportamento sociale, pratica golden pulita), G08 (Competenze di cittadinanza +
   Educazione civica), G11 (Personalizzazione)}. Aggancia TASK-469.
3. **Doppio R16 (era T10) -> ASSUNZIONI MULTIPLE, modellazione preparata per ognuna**
   (maintainer: "fai assunzioni tu, eventualmente piu' di una, e prepara la modellazione"):
   - **A1 (minima, default proposto)**: T20 E' un secondo assistente moda, i budget sono
     indicativi -> aggiungere `LabAssistant(T20, "moda")` al seed (patch PREPARATA, non
     applicata: DataGenerator e' file protetto) e M-LA fa il resto.
   - **A2 (budget vincolanti)**: i 100+100 / 100+85 sono monte-ore di co-presenza da
     garantire -> serve un vincolo M nuovo che conti le ore di R16 in cui l'assistente
     e' libero/co-schedulato e penalizzi il deficit rispetto al budget. Design da preparare,
     nessuna implementazione fino a conferma.
   - **A3 (tab stale)**: solo T14 e' il doppio (come gia' nel seed), T20 e' un
     refuso del tab -> nessun cambiamento. Resta il fallback se referente smentisce A1.
   La verifica con referente resta nel pacchetto come conferma a posteriori.

## Effetto sul pacchetto referente

Il doc `domande-rotazione-e-tensioni-referente-2026-07-31.md` va aggiornato di conseguenza:
le voci risolte scendono a conferme-cortesia con l'evidenza in una riga, le 3 vere domande
salgono in testa. La ratifica del combined 463+464 (decisione maintainer 01/08 mattina) non
dipende piu' da nessuna di queste.
