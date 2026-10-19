# GOLDEN MINING - pattern umani che riducono lo spazio delle soluzioni (laptop, 2026-08-15)

> Direttiva maintainer 15/08: accelerare l'autonomia estraendo dal golden altri pattern
> praticati dalla scuola, oltre ai depositi esistenti. Script ripetibile:
> `scripts/analyze/golden-mining.py` (read-only, misure dal golden seed/lessons.csv).
> Ogni pattern: MISURA -> valutazione riduzione-spazio -> PROPOSTA. La maggior parte
> resta proposta per maintainer (foglio workbook o euristica); nessun prototipo se non banale.

## Censimento: cosa e' GIA' estratto (per non duplicare)

| deposito | veicolo | contenuto |
|----------|---------|-----------|
| Cadenza settimanale | foglio 21_CadenzaAttesa + M34/M35 | volume (gruppo, materia) per settimana ISO, burst cap, active weeks |
| Blocco teoria | foglio 22_TeoriaBlocco + M-TR (prototype) | politica 1h/giorno vs 2h per (classe, materia TEORICA) |
| Slot-0 anchors + first-hour block | TASK-451 (runtime golden-read, censito 452) | docente modale prima ora per (gruppo, weekday) |
| Co-presenza depth | TASK-464 (runtime golden-read, censito 452) | profondita' ospite per (tag, weekday, slot) |
| Fingerprint sharpness / hole topology | data/*.json (KPI) | misura, non vincolo |

## Matrice pattern -> misura -> proposta

### 1. Block-length per (gruppo, materia): LAB (il nuovo oltre il foglio 22)

MISURA (8257 runs contigui): 81,2% dei blocchi e' 1h; il 18,8% >=2h e quasi tutto
professionale: Stage 8h fisso (6 gruppi), Modello/Moulage 3h (G09, 26/27 volte),
Lavorazioni meccaniche 4h (G08 25/27), Disegno 2h (G08 25/25), saldatura 2h
(G13 20/20). La teoria e' gia' coperta dal foglio 22.
RIDUZIONE-SPAZIO: alta dove e' deterministico: un blocco 2h fisso collassa le
composizioni interne del giorno (le 25 ore di Disegno G08 hanno UNA forma).
PROPOSTA: estendere il foglio 22 da "teoria" a "blocco per materia"
(colonna lunghezzaBlocco per (classe, materia), teoria E laboratorio). Domanda
referente dentro la stessa estensione. NESSUN vincolo nuovo: M21/S17 esistenti premiano
gia' la forma; il foglio la dichiara per il no-golden year.

### 2. Weekday-set per gruppo: GIA' DICHIARATO (esaurito)

MISURA: lun/ven 5h vs mar/mer/gio 6h per i primini; G06/FC piatti a 5h; G14
gio/ven. MA: calendario_class_days.csv / calendario_fc.csv dichiarano le ore per
DATA: la forma weekday e' interamente derivabile dagli input esistenti.
ESITO: nessun pattern nuovo. Il mining ha CONFUTATO il candidato (e' input).

### 3. Ordine intra-giorno teoria-mattina / lab-pomeriggio

MISURA: teoria 78% AM, lab 70% AM, other 64% AM. SEGNALE DEBOLE: il lab NON e'
pomeriggio (70% sta al mattino); l'AM domina perche' i giorni sono front-loaded
(M18/H17 gia' spingono li'). La differenza teoria-vs-lab (78 vs 70) e' piccola e in
gran parte epifenomeno del riempimento.
RIDUZIONE-SPAZIO: bassa e ambigua. PROPOSTA: NESSUNA (misura registrata, pattern non
praticato abbastanza nettamente per dichiararlo).

### 4. Fixed-weekday per (gruppo, materia): il candidato FORTE

MISURA (67 coppie >=10h con weekday dominante >=90% o unico):
- 27 deducibili dalla cadence gia' depositata (1h/settimana stabile: il weekday e'
  quasi implicato dal volume);
- **40 GENUINAMENTE NUOVE**: il volume non implica il giorno. Esempi: Barberia e
  Servizio tecnico/stilistico degli FA (weekday fisso), Disegno/Elettrotecnica
  G03, Storia/Normativa G11, Ra lab aperto, tutti gli Stage (naturale), G14
  Servizio/Confezione (100% un giorno).
RIDUZIONE-SPAZIO: altissima per coppia: un (gruppo, materia) fissato su 1 weekday su 5
elimina ~80% delle collocazioni possibili di quelle ore; con 40 coppie e' il singolo
pattern col maggior effetto misurato.
PROPOSTA: foglio workbook nuovo "23_GiornoFisso" (classe, materia, giorno,
origine=school-declared). Domanda referente: "queste materie stanno SEMPRE lo stesso
giorno per scelta o per comodita'?" Nessun nuovo constraint per ora (il
pattern-exhaustion-first vieta di irrigidire prima delle eccezioni): forma M32-like
(medium, weekday-deviation) SOLO se maintainer vuole spingere dopo il foglio; il foglio e'
il primo passo a costo zero.

### 5. Cattedre condivise stessi giorni: CONFUTATO

MISURA: 27/27 docenti toccano >=2 gruppi nello stesso weekday (T23 12-13 gruppi
il mar/mer). E' la struttura dell'istituto (docenti su piu classi), non un pattern
praticato: nessuna firma di coordinamento oltre quanto H1 gia' modella.
ESITO: NESSUNA proposta.

## Sintesi per maintainer

| # | pattern | esito | proposta |
|---|---------|-------|----------|
| 1 | block-length LAB | NUOVO (teoria gia' al 22) | estendere foglio 22 a tutte le materie |
| 2 | weekday-set gruppi | gia' input (calendari) | nessuna |
| 3 | teoria-AM/lab-PM | segnale debole | nessuna |
| 4 | fixed-weekday (gruppo, materia) | **40 coppie nuove** | foglio 23_GiornoFisso + domanda referente |
| 5 | cattedre condivise | confutato | nessuna |

Il pattern 4 e' il ritrovamento della giornata: la domanda a referente e' formattabile
subito (domande-brevi.md e' il veicolo). Script: scripts/analyze/golden-mining.py
(stesse misure ripetibili; il filtro deducibile-vs-nuovo del punto 4 e' inline in
questo doc, riportabile nello script se il foglio 23 viene approvato).
