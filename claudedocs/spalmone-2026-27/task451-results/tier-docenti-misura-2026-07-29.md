# Tier docenti (dedicati vs generici): la misura, 2026-07-29

Ipotesi di maintainer: raggruppare i docenti per tier di specializzazione e bilanciarne
l'assegnamento concorrente, per sbloccare la ricombinazione orizzontale (uno slot resta
vuoto perche' tutti i docenti utili sono gia' impegnati altrove).

## I tier esistono, e sono netti
Da `data/incarichi.csv`, 26 docenti reali su 14 gruppi. Dieci docenti servono 8+ gruppi
(T06 14, T21 13, T23 13, T02 11, T03/T11/T25/T08
10, T13 9) con 40-60h per gruppo. All'altro estremo i dedicati versano molte ore in
pochi gruppi (T15 175h/gruppo su 3, T17 131 su 3, T16 124 su 4). I generici
coprono il 35-62% delle ore di ogni gruppo.

## Ma la scarsita' che il bilanciamento curerebbe NON esiste
Sul golden (`data/lessons.csv`, 12.979 righe):
- concorrenza reale per ora: 11,2 docenti impegnati in media, picco 13, su 27 disponibili.
  Il picco usa meno della meta' del corpo docente.
- **liberta' di ricombinazione**: per ogni ora effettivamente insegnata, contando quanti
  docenti DI QUEL GRUPPO sono liberi in quell'istante, la media e' **6,6 alternative**.
  Le ore senza alcuna alternativa sono lo **0,1%**. Unico caso stretto: G14, 2,3
  alternative e 8% di ore senza alternativa, coerente con la sua eccezionalita'.
- dipendenza dai generici per gruppo: G11 e' al 53%, quarto, dietro G07 62%, G13 61%,
  G03 59%, che NON soffrono di inizi in ritardo. Quindi la dipendenza dai generici non
  discrimina il gruppo che perde la prima ora.

Limite dichiarato: la metrica conta i docenti del gruppo liberi in quell'istante, cioe' la
mossa "riempi lo slot con un'ALTRA materia di un docente libero del gruppo", che e' esattamente
la ricombinazione orizzontale. Non verifica che al docente restino ore in incarico a quel
punto dell'anno, quindi e' un limite superiore alla liberta'.

## Verdetto
Il bilanciamento per tier curerebbe una contesa che nei dati non morde: i docenti utili
liberi ci sono quasi sempre. Il contrasto con la misura di zai sullo stesso fenomeno nel
NOSTRO run (venerdi' G11 slot 0: 417 candidati nel dominio, ZERO conflict-free, tutti su
H2 stanza o H1 docente) dice che la saturazione locale non e' strutturale ma **prodotta dal
nostro stesso piazzamento**. Non si interviene quindi con un bilanciamento dei tier, ma a
monte, dove quello stato saturo viene creato.

Idea tenuta viva, non buttata: il tier resta una possibile leva di ORDINAMENTO in costruzione
(consumare prima il docente dedicato e tenere il generico come jolly, principio del
piu'-vincolato-per-primo), che e' cosa diversa dal bilanciare una scarsita' inesistente.

## Secondo giro (richiesta maintainer): rapporto per timeslot, per famiglia, nel tempo

Misura ripetibile: `scripts/analyze/teacher-tier-kpi.py <lessons-dump.txt>`. Confronto
golden contro run certificato `task447-results/cert/lessons.txt`. **Date di stage escluse**
(obiezione di maintainer, controllo eseguito): l'esclusione tocca 29 ore del golden e 10 del run,
perche' le lezioni ordinarie gia' non cadono nei turni di stage. Il quadro NON cambia.

### 1. Per famiglia: identico, ed e' atteso
G13 60,8 contro 61,6; FB 57,3 contro 58,0; FD 46,1 contro 46,7; FA 43,0 contro 43,6;
FC 38,1 contro 37,9; MODA 35,2 contro 35,5. Scarti sotto il punto. E' un INVARIANTE DI
INPUT: il monte-ore per docente e classe e' fissato dagli incarichi, quindi il mix non puo'
divergere. Serve da controllo di sanita', non da diagnosi. Unica eccezione G14 +3,5 su 115
ore, popolazione troppo piccola per pesare.

### 2. Per slot della giornata: divergenza vera alla sesta ora
Slot 0-3 allineati entro 4 punti. **Slot 5 (13:00): golden 45,0% generici, noi 27,3%**, cioe'
la nostra ultima ora del mattino e' fatta di specializzati (676 ore contro le 467 del golden).
Pomeriggio: il golden non ci mette MAI un generico (0% su 57 ore), noi il 20,9% e il 15,6%,
su volumi piccoli (43 e 32 ore).

### 3. Per mese: il segnale, ed e' una deriva
CORREZIONE (maintainer, 2026-07-29 sera): il primo giro contava fra le ore del golden le 112 righe
del sabato 19/09 che hanno docente E materia "Fasullo", un segnaposto lasciato dai pianificatori
della scuola. Non essendo negli incarichi finivano silenziosamente fra gli specializzati e
spostavano settembre di quasi 6 punti. Ora lo script le esclude e lo dichiara a schermo.

Numeri corretti: il golden sta fra 41% e 51%, noi da +3,2 a dicembre e +2,9 a febbraio fino a
-5,3 a maggio e **-6,4 a giugno**, con settembre a -3,0. Il senso della deriva regge da
ottobre in poi (partiamo sopra, finiamo sotto), ma **non e' monotona**: settembre e' sotto,
non sopra. Consumiamo il docente flessibile presto nella parte centrale dell'anno e chiudiamo
con un residuo di specializzati.

Baricentro temporale (giorno mediano delle ore di ciascun docente): i **generici anticipano
di 6,6 giorni** in media (T13 -34, T06 -22, T23 -15), gli specializzati
sono fermi (+0,1). La deriva e' quindi tutta su una sola delle due classi.

### 4. Nota a margine trovata strada facendo
Settembre: golden 992 ore, noi 892, **-100 ore**, distribuite su TUTTI i gruppi (-7/-10
ciascuno) e non su uno solo, con la stessa data di prima lezione. E' un deficit di avvio
uniforme, non un problema di calendario di un gruppo. Da spiegare separatamente.

## Riformulazione dell'idea di maintainer
Il tier non va usato per bilanciare la CONCORRENZA (non morde, vedi sopra) ma per difendere
un RITMO: consumare le due classi allo stesso passo con cui lo fa la scuola. E' un invariante
misurabile, oggi violato in modo monotono, e si presta sia a una regola di ordinamento in
costruzione (spendere prima lo specializzato, tenere il generico come jolly) sia a una
penalita' di ritmo. Non ancora testato: prima serve capire se la deriva CAUSA un difetto
noto o se e' solo una firma.

## Test di checkpoint: la deriva NASCE IN COSTRUZIONE
`./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest` (8 test, 0 failure,
BUILD SUCCESS, exit 0), poi `teacher-tier-kpi.py target/construction-lessons.txt`.

| | fine costruzione | run finale certificato |
|---|---|---|
| baricentro generici | **-13,4 giorni** | -6,6 giorni |
| baricentro specializzati | -5,7 giorni | +0,1 giorni |
| quota generici maggio | -9,3 punti | -5,3 |
| quota generici giugno | **-12,4 punti** | -6,4 |

La costruzione anticipa i generici del doppio rispetto a quanto si osservi alla fine: la
ricerca locale **dimezza** il danno ma non lo cancella. Il difetto quindi non e' della LS ne'
dei repair, e' del piazzamento iniziale, che consuma la risorsa flessibile nell'ordine
sbagliato. E' la stessa fase indiziata per gli inizi in ritardo di G11.

Nota di onesta': i singoli specializzati oscillano molto in entrambe le direzioni (da -40 a
+44 giorni) e si compensano; i generici invece si spostano tutti dalla stessa parte. E' la
sistematicita', non l'ampiezza, a rendere il segnale credibile.

Report visivo: `claudedocs/spalmone-2026-27/teacher-tier-pacing.html`, servito anche da
`/reports` (`teacher-tier-pacing.html`).
