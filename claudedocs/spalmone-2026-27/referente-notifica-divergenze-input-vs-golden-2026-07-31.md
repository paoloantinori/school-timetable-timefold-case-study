# Notifica per referente: divergenze input-vs-golden (bozza, TASK-466, 2026-07-31)

Bozza del documento da inserire nel pacchetto pronto-da-inviare a referente. Direttiva di riferimento
("golden autoritativo sugli input", CLAUDE.md): dove un dato di input diverge dal golden adottiamo
il valore golden e segnaliamo la divergenza per validazione a posteriori (puo' essere un vincolo
che ci manca oppure un errore umano genuino; in entrambi i casi non blocca).

## Sintesi
Verifica meccanica incarichi-vs-golden (orario in righe) per (docente, materia, gruppo):
**allineato, 0 divergenze reali.** Le divergenze gia' segnalate in passato (T28, G09, G04)
risultano risolte sui dati correnti. Una sola posta numerica residua (1h, pool Recuperi ed
Approfondimenti di G08) e' da confermare come scelta di modellazione, non e' un errore.

## Normalizzazione seed (parte b) = no-op sugli incarichi
Conseguenza diretta del diff a 0 divergenze reali: `incarichi.csv` e' gia' normalizzato al golden
(TASK-371), quindi la "normalizzazione dei dati di seed ai valori golden dove divergono" e'
un **no-op sul lato incarichi** (non c'e' nulla da ricalibrare ne' una riga di DataGenerator da
toccare per le triples docente/materia/gruppo). Le AC#2 (seed normalizzato) e #3 (registro
DataGenerator) di TASK-466 sono vacue sul lato incarichi; l'unica posta numerica aperta (pool RA
40h vs 41h) e' la notifica a referente qui sopra, non una modifica al seed. La tabella discrepanze in
`.claude/rules/datagenerator-protection.md` e' stata allineata (T28/G09 = RESOLVED).

## Righe da notificare (una per divergenza)

| # | voce | valore input | valore golden (adottato) | ipotesi | azione richiesta a referente |
|---|---|---|---|---|---|
| 1 | G08 pool "Recuperi ed Approfondimenti" | 40h (incarichi, riga unica pseudo-docente "RA") | 41h (golden, distribuiti su 9 docenti come materia "RA") | errore umano / arrotondamento di foglio (1h) | confermare se il pool è 40h o 41h e come va ripartito per docente |

## Voci gia' risolte (trasparenza, nessuna azione)
Queste erano state segnalate come divergenti in passato e ora coincidono sui dati correnti. Le
riportiamo per trasparenza e per chiudere le pratiche aperte.

| voce | vecchio valore segnalato | valore corrente | risoluzione |
|---|---|---|---|
| T01 (T28) Servizio tecnico e stilistico | 244h vs 192h | 231h = 231h (G02 60, G06 60, G10 65, G12 46) | incarichi allineato al golden |
| G09 monte-ore | 666h vs 640h | 666h = 666h (23 triple) | incarichi allineato al golden (640h era un foglio precedente) |
| G04 monte-ore | 835h vs 750h | 750h = 750h | chiarita: 835h è la capacità di griglia (990h di foglio meno 155h di aula sostituita dallo stage), 750h è il monte-ore da erogare. Nessuna divergenza di contenuto |

## Voci escluse per protocollo (non sono divergenze)
- 112h di sabato 19/09/2026 con docente e materia "Fasullo": segnaposto dei pianificatori, non
  esiste negli incarichi. Escluso.
- 311h di Stage off-grid (sabati/chiusure): fuori griglia per natura. Escluse.
- 5h di Orientamento in sabato: fuori griglia. Escluse.

## Out of scope di questa notifica (tracciati altrove)
- Cadenza MODA (Confezione e materie T16): è una questione di distribuzione settimanale
  nell'orario, non una tripla di incarico. Tracciata nella milestone m-12 (cadence KPI).
- Eventuali divergenze fra il grid grezzo originale (spalmone.csv) e il golden: non sono state
  verificate in questo giro perch'quelle già note risultano risolte. Se serve una risottomissione
  sistematica del grid grezzo, va aperto come attività separata.

## Riproducibilità
La verifica è meccanica e ripetibile:
```
python3 scripts/analyze/derive-golden-incarichi-corrections.py   # diff (docente, materia, gruppo) -> 0 divergenze
```
Dettaglio tecnico e tabelle complete: `claudedocs/spalmone-2026-27/task466-incarichi-vs-golden-diff-2026-07-31.md`.

---

## Aggiornamento 2026-09-01: censimento sistematico input-vs-pratica (audit completo)

Richiesta maintainer: "verifica se ce ne sono altre" (dopo il caso dei giorni pieni di
coda d'anno mai praticati). Lo strumento e' ora permanente:
`python3 scripts/analyze/input-vs-golden-audit.py` (filtri di CONSUMO del generatore:
le righe mai lette non contano come difetti vivi). Esito post-cura: **zero difetti
vivi** su tutti i calendari.

### 1. Notifica: 4 righe del calendario G08 corrette (typo complementari, golden-first)
Il foglio DEF FC aveva quattro righe sbagliate che si compensavano nei totali
(per questo il totale annuo 990 sembrava sano):
- **2027-03-01 e 2027-03-02** dichiaravano aula+lab (1+4 e 5+0): sono le PRIME DUE
  DATE del periodo stage (il golden colloca 8h di stage, e dal 03/03 il foglio
  dichiara correttamente `0,0,8`). Corrette a `0,0,8`.
- **2027-04-23** dichiarava stage 8h: il golden quella giornata fa LEZIONE NORMALE
  in aula (Storia, Lingua Italiana, Scienze, Tecnologia Meccanica: 5h). Corretta a
  `5,0,0`.
- **2027-04-26** dichiarava stage 8h: il golden fa 5h di LABORATORIO (Controllo
  numerico, Lavorazioni meccaniche). Corretta a `0,5,0`.
Dopo la correzione il foglio converge con la pratica su ogni singola data e nei
totali: stage esattamente 300h (= il riferimento del foglio stesso e il budget
incarichi), aula/lab rispettivamente 569/107 in orizzonte. Conferma a posteriori
richiesta, non bloccante.

### 2. Nota di igiene documentale: fogli per-classe FC contraddittori
I fogli per-classe "G04" e "G08" del calendario dichiarano 6h aula+lab nei 31
giorni di stage di aprile-giugno dove il foglio DEF FC dichiara correttamente lo
stage (8h). Il nostro generatore NON consuma mai i fogli per-classe dei gruppi FC
(la fonte viva è il DEF FC, più fine), quindi nessun effetto sul solver: resta la
suggerenza di ritirare/ignorare quei fogli per-classe per evitare letture future
contraddittorie.

### 3. Trasparenza: righe oltre il 10/06 nei fogli per-classe
Le righe del 14-15/06 (G09, G10, G11, G13: giorni dichiarati pieni che il
golden non pratica mai; fine attività reale) e del 21-24/06 (G08) sono oltre
l'orizzonte di griglia e vengono scartate dal generatore (TASK-550: capacità solo
dove il golden la pratica). Nessuna azione necessaria; segnalato per completezza.

### 4. Nota: pool incarichi dichiarati vs ore praticate
Il foglio incarichi dichiara 990h per gruppo (la finestra piena); il golden pratica
839-978h a gruppo. La differenza è già gestita per progetto (il seeding golden-open
genera lezioni solo per le ore praticate e registra il resto in expectedUnscheduled),
quindi non è un difetto: è la stessa informazione delle voci "giorni corti" già nel
pacchetto domande.

### 5. G13 ore/giorno: VERIFICATO COERENTE (voce ritirata)
La pratica G13 nel golden è 5h tipiche (115 giorni) con picchi a 8h (37 giorni);
le dichiarazioni di entrambi i fogli non superano mai 8h/giorno. La voce "G13
dichiarato 10-11h" emersa nella notte è risultata non rintracciabile in alcun foglio:
ritirata.
