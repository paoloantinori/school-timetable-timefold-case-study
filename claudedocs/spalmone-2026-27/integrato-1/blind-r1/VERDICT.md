# VERDICT blind-r1: round cieco sul [-4/0] dell'integrato (consegna con catena completa)

Census NOMINATIVO prima del cieco: H17 = G13 2027-06-10 doppio buco @10-@11;
H28 = pattern Confezione..Modello..Confezione su G01 giovedi' (5 raw, 1 punteggio);
H7 = T13 2027-01-20 7h/6 su tre gruppi. Il G08 04-01 emerso come falso
positivo raw (giorno riempito da ore Stage: score-clean), escluso da puzzle e
checker con R3-che-conta-Stage-occupante. Predizione FUORI dalla dir del cieco.

## Il cieco: 5 mosse, TUTTO PULITO sul checker, minimolta ARGOMENTATA

- Difetti 1-2 (G13 @10/@11): due SWAP 1-per-1 cross-date stesso-gruppo (import
  T15 dalla coda del venerdi' donatore + export T10 sull'ora appena
  vacata del donatore: 4 mosse). Argomento minimalita': il VRP confina le T10
  alle ore 12-15 del 06-10, R3+R4 impongono blocco contiguo da 8, R5 n>=4: le due
  T10 DEVONO uscire e due entranti DEVONO riempire; nessun giorno G13 ha
  surplus: ogni uscita va compensata 1-per-1.
- Difetto 3 (T13): 1 mossa, export della PRIMA ORA (le 8) di G06 verso
  coda libera 12 del venerdi' stessa settimana.

## Arbitrato Java: -4 -> -5. Le mosse curano i target ma APRONO collaterali

- H17 -2 -> 0 e H7 -1 -> 0: I TARGET SONO CURATI (0 VRP-skip).
- MA: H28 -1 -> **-3** (+2: gli swap creano nuovi ritorni A..B..A), H31 0 ->
  **-1** (l'export della prima ora di G06: il checker non copriva R4 su G06
  e l'ha lasciato passare), H32 0 -> **-1** (un giorno sotto quota).
- Netto: -4 -> -5. Le 5 mosse NON trasferiscono sul modello reale.

## Classificazione contro l'alfabeto (il valore vero del round)

1. **G13 H17: lo SWAP cross-date stesso-gruppo 1-per-1** (import nell'hole +
   espulso che atterra sulla cella vacata del donatore). E' una VARIANTE NUOVA
   rispetto al 619: l'import arm fa import+backfill-da-donatore-tollerante, qui
   il backfill e' LA LEZIONE ESPULSA STESSA (swap simmetrico). FAMIGLIA
   candidata: cross-date-swap-same-group (TASK-619-ext o braccio proprio).
2. **H7 T13: la cura del cieco e' un ARTEFATTO SCOPE del checker** (R4
   copriva solo G13): l'export della prima ora apre H31 REALE. La cura H7 resta
   la catena depth-2 cross-group della specifica 621: il cieco non l'ha trovata
   (e l'argomento minimalita' mostra PERCHE': le ore interne non possono uscire
   senza buco R3, la prima ora non puo' uscire senza H31 reale).
3. **H28 peggiora con gli swap** (-1 -> -3): la famiglia teacher-return non ha
   NE' pass NE' regola nel checker: ogni meccanizzazione che sposta ore cross-date
   deve aggiungere il gate H28 (no A..B..A) o aprira' ritorni. TASK dedicata.

## VERDETTO (tre righe)

1. IPOTESI (Fedora): 4-8 mosse; H7 via catena cross-group depth-2, H28 con
   scambio rompi-ritorno, H17 con alfabeto esistente o poco oltre.
2. MISURATO: 5 mosse checker-pulite con minimalta' argomentata; sull'arbitrato
   reale -4 -> -5 (H17 e H7 curati MA H28 +2, H31 +1, H32 +1 aperti): le mosse
   del cieco NON trasferiscono, ma le FORME sono la specifica: lo swap
   cross-date stesso-gruppo (nuovo), l'artefatto scope-R4 (la cura H7 resta
   depth-2), e H28 come gate obbligatorio per ogni swap.
3. RACCOMANDAZIONE: tre specifiche partono da qui: (a) cross-date-swap
   same-group per H17 (con gate H28 e quota-check H32 addosso), (b) la catena
   H7 depth-2 resta aperta (il cieco conferma che nessuna mossa singola la
   chiude), (c) H28-gate come vincolo trasversale per TUTTE le future famiglie
   swap. Nessuna adozione.

## CENSIMENTO H28 residuo (17/09, read-only): il trio e' NOMINATO

1. **G13 | Lavorazioni meccaniche | 2027-02-12: T10@8 (A) .. T15@9 (C)
   .. T10@11 (B)**: tutte unpinned. Semantica esatta del vincolo (tri-join
   group+subject+date): stesso docente a slot i e k con lezione di ALTRO docente
   della STESSA (gruppo, materia, data) строго in mezzo.
2. **GOLDEN: ZERO ritorni A..B..A su tutto l'anno** (conteggio globale con la
   stessa semantica su lessons.csv aperto: 0 trii). Il golden NON pratica la
   famiglia: e' un VERO vincolo della scuola, non tolleranza (il precedente
   H28-crystallization del TASK-448 era corretto).
3. **Frequenza nei run recenti**: endgame arm-b (pre-cura) -1hard/1m; reab-cura
   arm-b -1hard/1m; integrato-1 -1hard/1m; reab-cura arm-a e endgame arm-a:
   0. Pattern: circa meta' dei bacini condannati porta ESATTAMENTE 1 match,
   sempre single. Mai piu' di 1.
4. Esiste GIA' un pass dedicato: `H28RotationFinisher` (condivive h28ReturnPair/
   h28Between con il provider, il javadoc lo dichiara). Da verificare perche'
   non gira (o non chiude) sul confine di consegna: la domanda operativa non e'
   "nuovo pass vs tolleranza" ma PERCHE' il pass esistente lascia 1 match.

RACCOMANDAZIONE: (a) il vincolo resta HARD (golden 0/anno: nessuna tolleranza da
 dichiarare, nessuna domanda referente); (b) audit di H28RotationFinisher sullo
 stato consegnato dell'integrato-1 (gates? innesco? confine?): se il pass ha la
 forma giusta e non scatta, e' un gap di innesco; se scatta e non chiude, il
 census dei suoi gate dara' la specifica (la forma A..B..A su G13 con T15
 in mezzo suggerisce la rotation-with-permission gia' in alfabeto).
