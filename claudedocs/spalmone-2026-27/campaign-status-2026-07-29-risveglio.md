# Quadro del risveglio, 2026-07-29 (lavoro notturno del 28/07)

Preparato da Fedora dopo verifica INDIPENDENTE sui raw (export, analysis.json, dump KPI),
non sui VERDICT del laptop. Il laptop ha misurato e implementato; NESSUNA adozione e' stata fatta.

## Riproducibilita': CONFERMATA con n=2

Un secondo run della STESSA configurazione (`gate-finale-run2`, notte del 29/07) e' stato
lanciato proprio per non farti ratificare su un campione singolo, visto che la ricerca locale
e' stocastica. Numeri verificati da me sui raw, run1 contro run2:

| metrica | run1 | run2 |
|---|---|---|
| hard (tutto H0, zero conflitti e zero violazioni di stanza) | -5 | -4 |
| room-null non pinnate di docente reale | 254 (251 lab / 3 non-lab) | 270 (262 lab / 8 non-lab) |
| full-day | 80,9% | 80,8% |
| day-start | 99,2% | 99,3% |
| ricorrenza unpinned | 74,2% | 74,6% |
| buchi interni | 0 | 0 |
| M37 (cambi aula tollerati) | 124 | 102 |
| H28 (ritorni del docente) | 0 | 0 |
| celle anomale day-start | 1 (G11 venerdi) | 1 (G11 venerdi) |

Lettura: i numeri REGGONO, la variazione e' rumore di traiettoria (hard 4-5, room-null 254-270,
KPI entro tre decimi di punto). Non era una traiettoria fortunata. Anche il difetto residuo e'
riproducibile: G11 del venerdi resta l'unica cella anomala in entrambi i run.

## Colonna 1: VERDE, pronto per l'adozione

**M37 (era H16b), demozione da hard a medium 100.** La trappola che teneva 291 ore senza
aula e' sciolta: nel gate finale restano 3 ore non-lab senza stanza (erano 291). Verificato
da me sull'export: 254 room-null totali non pinnate di docente reale, di cui 251 lab e 3
non-lab. Costo visibile della demozione: M37 scatta 124 volte (12.400 medium), cioe' 124
blocchi in cui la classe cambia aula tra due ore consecutive della stessa materia (102 nel
run2). Il golden non specifica le stanze, quindi non c'e' un riferimento esterno: per darti
il contesto ho caratterizzato quei cambi sui raw. Sono sparsi, non concentrati (il gruppo
peggiore ne ha 15-21 sull'anno, la coppia gruppo-materia peggiore 6-9) e riguardano materie
d'aula comuni, tipicamente Italiano, Matematica e Inglese. Il quadro d'insieme: su 1.922
giornate-gruppo la classe cambia aula in media 1,2 volte al giorno, e 628 giornate si
svolgono in una stanza sola, 798 in due. Quindi la demozione non ha prodotto un girovagare
sistematico, ma un pugno di trasferimenti in piu' a meta' blocco. Se li ritieni comunque
inaccettabili, la leva e' alzare il peso di M37 (oggi 100) restando sotto M36 (1000).

**H28, no-return del docente (TASK-448).** Il vincolo che avevi chiesto di modellare e'
implementato a tier hard e ha 0 violazioni nel gate finale, come nel golden (15 blocchi su
15 sono A->B, zero ritorni). E' un guard a costo zero: cristallizza l'invariante senza
cambiare nulla del comportamento attuale.

**Gate finale, numeri verificati.** Hard -5, e i 5 sono SOLO lezioni non piazzate (H0):
zero conflitti, zero violazioni di stanza. Full-day 80,9% (soglia 78, golden 85,4), day-start
99,2%, ricorrenza unpinned 74,2% (soglia 72), buchi interni 0. Il pacchetto 435 + 446 + M37 +
H28 tiene tutte le soglie e resta molto piu' veloce del ciclo ingessato di prima.

## Colonna 2: DA BUTTARE o gia' buttato

**Vincolo hard "capacita' di tag per timeslot": ritirato prima di scriverlo.** Il golden lo
viola 242 volte, quindi golden-first lo vieta. Costo della scoperta: zero run.

**H27 hard "ogni lezione assegnata deve avere una stanza": bocciato ieri sera.** A parita' di
peso con H0 svuotava l'orario (9.404 lezioni de-assegnate). Sopravvive come M36 medium 1000,
che funziona.

**R17 a capacita' zero: falsa pista mia.** Le 40 ore di saldatura sono gia' in quella
stanza; la capacita' zero pesa solo come penalita' medium. Non blocca nulla.

**Scambio cross-gruppo per l'ancora del venerdi': rinviato.** Il laptop ha analizzato e non ha
scritto codice, correttamente. La diagnosi allegata era pero' imprecisa e l'ho corretta nel
TASK-450: NON e' vero che i docenti di G11 sono tutti occupati alle 8 del venerdi (T05,
T04, T03 e T24 sono liberi e non insegnano mai di venerdi a quel gruppo). Il
blocco vero e' che l'import non trova un donatore legale, cioe' una lezione che possa
lasciare la sua giornata senza aprirvi un buco. Direzione da valutare: import da coda di
giornata, oppure ancora settimanale riservata nel template di costruzione.

## Colonna 3: ASPETTA UNA TUA DECISIONE

1. **Adozione del pacchetto 435 + 446 + M37 + H28** sui numeri qui sopra: flag
   constrained-teacher di default, righe M36/M37/H28 in CLAUDE.md, chiusura di TASK-446 e
   ratifica di TASK-435.
2. **Patch ai tag delle stanze (file protetto `DataGenerator.getRoomTags`).** L'esperimento
   in-harness misura: togliere l'officina a Progettazione Termoidraulica e Taglio Metalli
   chiude 83 delle 256 ore lab senza stanza; aggiungendo Servizio stilistico si arriva a 176.
   Ne restano circa 80 genuinamente vincolate dalla capacita'. La proposta e' scritta e NON
   applicata. La domanda vera e' per la scuola: quali di queste materie si fanno davvero in
   officina e quali in aula.
3. **TASK-450, ancora della prima ora.** 17 giornate in ritardo contro le 4 del golden, con
   l'unica cella anomala che resta G11 del venerdi. Decidi se vale un giro dedicato.
4. **TASK-447, entrambi hard con pesi diversi.** Il censimento dei punti che leggono lo score
   hard e' fatto; resta da decidere se la rottura della comparabilita' storica vale il
   guadagno.

## Dove guardare

- Gate finale: `claudedocs/spalmone-2026-27/task446-results/gate-finale/` (VERDICT, KPI,
  `day-start-anatomy.html`, `confronto-gate-finale.html`, export vero).
- Fasi notturne: `task446-results/fase1-m37`, `fase2-h28`, `fase3-tag`, `fase4-anchor`.
- Anatomia degli inizi in ritardo: `scripts/analyze/day-start-anatomy.py`, ora nella batteria
  di `capture-run.sh`.
