# Design spike 615-pratiche: le tre pratiche del censimento input-side (15/09 notte)

Dal censimento (censimento-input-7-strutturali.md): i 7 difetti 'strutturali' sono
pattern-placement; il golden li tiene pieni con TRE pratiche che la costruzione non ha.
Questo doc le specifica come spike misurabili, con la storia delle falsificazioni come
vincolo di design.

## Vincolo di design (dalle tre falsificazioni 615)

contiguita'-first (80->274), scan-forward raw (80->318), scan-forward contiguita'-gate
(INERTE: il guadagno fill ERA gli atterraggi che il gate vieta): le spike che rimodellano
gli ATTERRAGGI muoiono. Le pratiche qui sotto agiscono sulle PREFERENZE di scelta
(quale weekday/docente/ora per primo), mai sulla forma dell'atterraggio.

## Pratica 1: affinita' weekday (docente-cerniera, gruppo)

GOLDEN: il giovedi' di G10 e' la giornata monografica T01 8-13; G12 lo stesso
giovedi' senza di lui. E' concentrazione PER-GRUPPO del docente condiviso.
MECCANISMO: preferenza di weekday per la coppia (docente, gruppo): quando il builder
sceglie il giorno di un chunk ricorrente di un docente-cerniera (docente con ore su 2+
gruppi), preferisce il weekday dove la coppia e' gia' piazzata (auto-rafforzamento) e
dove gli ALTRI gruppi del docente NON sono (evita la contesa alla nascita).
FONTE: golden-praticed finche' c'è (validazione), workbook dichiarato per la sopravvivenza
(foglio affinita'-weekday: la domanda a referente e' 'il giovedi' pieno di [docente] su [gruppo]
e' pratica o contingenza?' - TASK-445). Forma: opt-in flag, banco prima.
PREDIZIONE (banco, construction-born-census): i buchi H17 nati D1-like (docente-cerniera
conteso) scendono da 3 a <=1; nessun effetto sui G12 ven (pratica 3); H32/H34 stabili.

## Pratica 2: filler di materie a basse ore

GOLDEN: il buco delle 9 di G11 e' tappato da T03 Orientamento (materia a pool
piccolo) dove il titolare (T05) e' conteso.
MECCANISMO: nel cell-assignment, quando il chunk preferito del giorno e' bloccato da
conflitto docente, il builder considera PRIMA le materie a pool residuo piccolo (<=8h)
dello stesso gruppo come candidati filler per quella cella (consumarle dove altrimenti
nascerebbe un buco, non solo first-fit). Spezza l'ottimo locale fill-only-del-corso.
PREDIZIONE: H17 nati D3-like giu'; attento al collaterale: le materie piccole consumate
in filler non devono sparire altrove (controllare cadence/ricorrenza per le coppie toccate).

## Pratica 3: scaglionamento orario dei docenti condivisi

GOLDEN: i venerdi' tengono G12 + G02 + G04 in parallelo con T23/T02/
T13 su ORE DIVERSE per gruppo (il docente condiviso lavora in sequenza, non in
conflitto).
MECCANISMO: quando placeChunkOnce valuta un candidato slot e il docente e' condiviso con
un gruppo G2 che quel giorno e' gia' piazzato (o viceversa), preferisce la finestra
COMPLEMENTARE alle ore gia' impegnate del docente quel giorno (stesso giorno, ore diverse:
la forma esatta del golden), invece di abbandonare il giorno (il difetto strutturale
'bloccanti H1 permanenti 8-11' nasce proprio dallo scontro alla stessa ora).
PREDIZIONE: i buchi H17 D4/D5/D6-like (venerdi' G12 @11) scendono da 3 a <=1; e' la
pratica col bersaglio piu' largo (3 dei 7 difetti).

## Protocollo spike

Una pratica per commit, flag opt-in DEFAULT OFF nel WeeklyTemplateBuilder (forma spike),
banco: WeeklyTemplateConstructionRealSeedTest + construction-born-census.py + la stessa
guardia della FASE C 615 (braccio OFF diff-zero, H17 <= ~1.25x, H32/H34 non peggiorati,
full-day/ricorrenza in banda) + golden-placement-census.py sulle celle target come
verifica di direzione. Ordine consigliato: pratica 3 (bersaglio piu' ampio), poi 1, poi 2.
Ogni pratica nega/confirmà da sola: niente cicli completi senza gate di adozione.
