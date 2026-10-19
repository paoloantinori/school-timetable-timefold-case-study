# Censimento input-side dei 7 difetti dichiarati strutturali (round cieco B3, 15/09 sera)

Domanda: per ognuno dei 7 difetti che il cieco ha dichiarato inchiudibili con enumerazione
meccanica, cosa fa il GOLDEN sulle stesse celle? Se il golden riempie -> la pratica esiste e
il blocco e' nostro (pattern di piazzamento non modellato o search); se nemmeno il golden
riempie -> tolleranza della scuola (domanda referente). Metodo: grep diretti sul golden
(src/main/resources/seed/lessons.csv), celle esatte dal VERDICT blind-live-r1.

## Risultato secco: il golden riempie 7/7. Nessun difetto e' "giorno accettato corto".

| Difetto | Cella | Golden quel giorno | Come lo risolve il golden |
|---|---|---|---|
| D1 H17 | G10 gio 2026-12-10 @11 | 5h compatte 8-13 | GIORNATA MONOGRAFICA T01 (Servizio tecnico e stilistico x5): proprio il docente che da noi blocca (stanza famiglia occupata da T01 su G12). Nello stesso giovedi' il golden mette G12 su ALTRI 5 docenti (T26/T13/T20/T09/T04), senza T01. |
| D3 H17 | G11 gio 2026-12-10 @9 | 4h compatte 8-12 | FILLER DI MATERIA A BASSE ORE: slot 9 = T03 Orientamento. T05 (il nostro bloccante) nel golden fa G03 8-9, G11 10-11, G08 11-12, G13 12-13: giornata SCAGLIONATA su 4 gruppi. |
| D4 H17 | G12 ven 2026-09-18 @11 | 4h 8-12 (slot 11 = T18) | venerdi' pieno con docenti ruotanti; stesso venerdi' il golden tiene ANCHE G02 e G04 a giorno pieno in parallelo: i tre gruppi coesistono. |
| D5 H17 | G12 ven 2026-10-02 @11 | 4h 8-12 (slot 11 = T25) | idem: coesistenza G12 + G02 (8-13) + G04 (8-13) nello stesso venerdi'; i docenti CONDIVISI (T23, T02, T13) sono SCAGLIONATI su ore diverse fra i gruppi. |
| D6 H17 | G12 ven 2026-10-09 @11 | 4h 8-12 (slot 11 = T13) | idem D5. |
| D8 H32 | G12 mar 2026-11-10 (4/5) | 5h piene 8-13 (T18/T13/T08/T06/T02) | giorno pieno, cinque docenti distinti. |
| D9 H32 | G12 ven 2027-04-16 (4/5) | 5h piene 8-13 (T21/T02/T23/T25/T13) | idem. |

## Lettura: i 7 non sono input-impossibili, sono PATTERN DI PIAZZAMENTO non modellati

Il cieco aveva ragione sui GATE (le sue enumerazioni: celle congelate, H1 permanenti, surplus
R10-confinali sono vere SUL NOSTRO STATO), ma la causa radice e' a monte: la costruzione non
conosce le pratiche di weekday che il golden usa per tenere queste celle piene. Tre pratiche
identificate:

1. **Giornata monografica del docente-cerniera** (T01: giovedi' = G10 intera; G12
   senza di lui). E' la famiglia storica TASK-432 bocciata come VINCOLO ma che come BIAS di
   costruzione (weekday-preference del docente sul gruppo) e' esattamente il terreno di
   TASK-615/teacher-weekday-spread (TASK-599 gia' ratificato nella direzione giusta).
2. **Filler di materia a basse ore nelle celle difficili** (Orientamento@9): il golden usa le
   ore piccole come tappi dove il docente titolare e' conteso. Nessun nostro meccanismo lo fa
   (la costruzione piazza per continguita'/quota, non per tappare buchi da ore piccole).
3. **Scaglionamento dei docenti condivisi fra gruppi paralleli** (i venerdi' G12 + G02 +
   G04 insieme, con T23/T02/T13 su ore diverse per gruppo): e'
   teacher-weekday-spread applicato ALL'ORA, non solo al giorno.

## Conseguenze operative

- I 7 restano chiudibili IN TEORIA: la frontiera e' COSTRUZIONE (bias di pattern, famiglia
  TASK-615/599) e non input mancante: nessuna domanda a referente e' necessaria per questi 7
  (il golden li pratica con docenti e incarichi che gia' abbiamo).
- Il censimento INVALIDA l'etichetta 'strutturale' come categoria permanente: sono difetti
  di pattern-placement. La direttiva bounded-no-op resta valida per i pass, ma per questi 7
  la leva giusta e' insegnare alla costruzione le tre pratiche sopra (candidate spike 615:
  (a) weekday-preference docente-cerniera per-gruppo, (b) low-hour-filler placement,
  (c) hour-level staggering dei docenti condivisi).
- Il round cieco A3 ha nel frattempo trovato la famiglia di repair viva (import cross-data,
  TASK-619): le due vie (repair 619 + costruzione 615-pratiche) non si escludono; 619 prende
  i punti oggi, le pratiche di costruzione prevengono che i buchi nascano.

Verifica: grep diretti su seed/lessons.csv (righe riportate per esteso nel file; nessuno
script intermedio, il classifier era giu' e l'analisi e' fatta a mano con i greps nel
transcript della sessione 15/09 sera).
