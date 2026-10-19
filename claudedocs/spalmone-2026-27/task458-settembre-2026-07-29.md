# TASK-458 - le 100 ore mancanti a settembre: artefatto, chiuso senza codice (2026-07-29)

## Il sospetto
Nella misura dei tier era emerso che a settembre il golden ha 992 ore ordinarie e il nostro
run certificato 892, cioe' **-100**, distribuite uniformemente su tutti i 13 gruppi
principali (-7/-10 ciascuno) e con la stessa data di prima lezione, quindi ne' un problema di
calendario di un gruppo ne' di start date (H14).

## La causa: il golden ha un SABATO, la nostra griglia no
Il golden contiene **117 ore di sabato**, di cui **112 il sabato 19 settembre 2026 su tutti e
14 i gruppi** (l'altro sabato e' il 16/01/2027, 5 ore su un gruppo solo). La nostra griglia ha
timeslot solo dal lunedi al venerdi (1.368 slot = 171 giorni x 8), quindi quelle ore non sono
collocabili per costruzione: nessun solver le potrebbe piazzare. Coerentemente, il foglio
autoritativo `calendario_class_days.csv` **non ha nessuna riga** per il 19/09, che infatti
compare come "piano ASSENTE" quando si confronta data per data.

## Il numero vero
| | golden | golden senza sabati | run | scarto reale |
|---|---|---|---|---|
| settembre | 992 | 880 | 892 | **+12** |
| anno intero | 10.659 | 10.542 | 10.498 | **-44** |

A settembre non siamo indietro di cento ore: siamo **avanti di dodici**. Sull'anno lo scarto
vero e' -44 ore su oltre diecimila, cioe' lo 0,4%.

## Verifica incrociata del mio stesso calcolo
Il primo confronto contro le ore attese dava anomalie enormi su G04, G08 e G13 (atteso
circa doppio dell'erogato). Era un **mio errore**: quei tre gruppi compaiono in ENTRAMBI i
fogli (`calendario_class_days.csv` e `calendario_fc.csv`) con le stesse ore, quindi le stavo
sommando due volte. Sui 10 gruppi presenti in un foglio solo il conto tornava esatto: noi
eroghiamo **esattamente** le ore attese, il golden ne eroga otto in piu' ciascuno, e quelle
otto sono il sabato.

## Conseguenza per la batteria KPI, misurata e circoscritta
Il parser condiviso (`scripts/analyze/shared.py`, `parse_golden_csv`) NON filtra il giorno
della settimana, quindi i sabati entrano in ogni lettura del golden. Ho misurato quanto pesa
invece di limitarmi a segnalarlo: rigenerando il golden senza sabati (304 righe in meno) e
rigirando la batteria, `hole-topology-kpi.py` da' risultati **identici** (BUCO-INTERNO 1
giorno in entrambi i casi). Il bias quindi **non tocca i KPI a base giornaliera** gia'
pubblicati, e non c'e' motivo di muovere le baseline storiche.

Resta invece pieno sui **confronti sul monte-ore**, che e' esattamente dove ha ingannato me:
117 ore, l'1,1% del totale. Chi confronta ore contro ore deve escludere i sabati. Stessa
famiglia dell'artefatto stage chiuso il 28/07 (divario apparente 327h, reale 16h): le ore del
golden fuori dalla nostra griglia vanno tolte dal confronto, non inseguite.

## Che cosa c'e' davvero in quel sabato (maintainer, 2026-07-29 sera)
Le 112 righe hanno **docente "Fasullo" E materia "Fasullo"**, cioe' il nome dice da solo che
sono finte: 8 ore esatte per ognuno dei 14 gruppi, e "Fasullo" non compare da nessuna altra
parte, ne' negli incarichi ne' in un'altra data. E' un blocco-segnaposto lasciato dai
pianificatori della scuola, probabilmente mentre tentavano di automatizzare qualcosa. Gli
altri sabati sono cosa diversa e legittima: 187 ore di Stage (fuori griglia per natura) e 5
ore di Orientamento con T14 il 16/01/2027.

maintainer dice che le ore sottostanti sono reali. In tal caso non cambia il verdetto sul solver,
ma cambia il nome del problema: non "il golden ha un giorno in piu' che non ci interessa" bensi'
**la nostra griglia non modella il sabato 19/09**, che e' un buco di INPUT (calendario), da
colmare quando la scuola confermera' il giorno. Cio' che resta vero in ogni lettura e' che
quelle righe non hanno un docente ne' una materia veri, quindi vanno escluse da ogni misura
per docente o per materia.

DANNO GIA' FATTO E RIPARATO: quelle 112 ore finivano fra gli specializzati nel KPI dei tier
(Fasullo non e' negli incarichi, quindi cadeva nel ramo "non generico") e spostavano la quota
di settembre del golden da 51,1% a 45,4%, cioe' quasi sei punti, invertendo il segno del
confronto di quel mese. `teacher-tier-kpi.py` ora scarta i docenti non classificabili e li
DICHIARA a schermo invece di assorbirli in silenzio.

## Verdetto
Non e' un difetto del solver ne' della costruzione. Resta aperta una sola domanda, di input:
se il 19/09 e' un giorno di scuola vero, la griglia deve prevederlo.
