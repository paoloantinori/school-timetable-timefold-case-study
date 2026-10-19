# VERDICT INTEGRATO-5 (19/09, ciclo produzione n=5)

Config: profilo fresco integrato-5, max-attempts=5, M-BF ON W=20 (default ratificato),
tutti i flag ratificati DEFAULT ON. Lanciato ~05:30, consegnato 07:40 (~2h10m).

## Consegna

| Metrica | Valore | Banda serie |
|---------|--------|-------------|
| Hard | **[-1/0]** | serie [-4,-1,0,0]: IN BANDA (pari a integrato-2) |
| Medium | -1.189.144 | ~70-90k peggio di integrato-1 (-1.116.772): bacino condannato raffinato in consegna |
| Ricorrenza unpinned | 69,8% | banda era-post-599 ~69,7-70,3%: IN BANDA |
| Full-day | 94,9% (1856/1956) | sopra golden 91,9% |
| Buchi interni | 0 | pulito |
| Day-start (>=3 vs golden) | nessuno | pulito |

L'-1 hard e' UNA violazione H7 (una sola ora-docente oltre il cap giornaliero).
Nessun H0/H17/H29/H31/H32/H33/H34 residuo.

## SANITY: NON CONFORME (2 gruppi, un rosso ciascuno)

- **G09 22/02/2027**: 1h ordinaria (Confezione, T16, slot 8) sopra 6h di Stage.
  Quota dichiarata del giorno: aula+lab 0, stage 8 = giorno di STAGE.
- **G10 05/03/2027**: 1h ordinaria (Educazione Civica, T23, slot 8) sopra 6h di Stage.
  Quota dichiarata: aula+lab 0, stage 8 = giorno di STAGE.

Classificazione: giorni di stage con UN'ORA ordinaria isolata a inizio giornata. Il tier
hard non li vede (H33 esclude i giorni di stage, H32/H34 fuori scope a quota aula+lab=0);
il golden su giorni di stage non pratica ore ordinarie isolate (min golden 2h/4h =
pratica a blocchi o zero). E' la forma sub-golden che la sanity gate esiste per beccare.
Famiglia: stessa classe dell'ora isolata S8, da curare dove si cura la non-nascita
(costruzione/steering), non con vincoli.

## Come e' nata la consegna (attribuzione onesta)

1. Attempt 1 (deterministico) ha completato B-TIMESLOT ma era DOOMED al confine
   post-RELEASE: timeslot-tier hard **-13**.
2. **32 semi GRASP scartati dal profile predictor** (h17_holes costruzione > 150, tutti):
   NESSUN retry e' mai partito, il multi-start e' degenerato a N=1. Conferma LIVE
   del verdetto TASK-624 (la soglia 150 scarta ogni seed GRASP; il metro non predice
   la qualita' individuale).
3. La catena deterministica post-RELEASE ha chiuso -13 -> **-1** (stessa catena del
   banco replay: CSF/EDF relay/HoleRelay + H7cap). Skip-doomed-room (TASK-498) ha dato
   CH-only all'attempt, la corner-b refinement ha rifinito le stanze in consegna
   (~10 min, 07:30-07:40).
4. Medium peggio della serie buona (~-1,19M vs ~-1,12M): coerente col bacino (attempt
   condannato raffinato solo a valle; M-BF 54 match vs 17-18 dei bracci sweep).

## Falso allarme mid-refinement (lezione di processo)

Alle 07:32 una lettura score dava [-9651/0]hard con 1293 H0: era lo stato LIVE
dell'ultimo tentativo GRASP scartato mentre la corner-b lavorava SU UNA COPIA.
Il driver di attesa era scattato sul falso quiescence (solverStatus NOT_SOLVING +
stagedStage null) invece di aspettare `refiningForDelivery=false`: e' ESATTAMENTE
la trappola documentata (ratifica quiescenza 17/08). Il wait e' stato riarmato sul
marcatore corretto e la consegna letta a 07:40. Nessun danno: solo una lettura
scartata. Nota per i driver futuri: `refiningForDelivery` nel payload status e'
l'unico segnale di consegna durante la refinement.

## Verdetto

IN BANDA con la serie ([-4,-1,0,0] -> [-4,-1,0,0,-1]), sanity NON CONFORME per due
celle isolate (ore ordinarie su giorni di stage, classe non-nascita). La consegna
proviene interamente dal tentativo deterministico: il multi-start non ha contribuito
(32/32 scarti), quindi INTEGRATO-5 non aggiunge informazione sulla diversita' dei
retry, conferma invece il valore della catena deterministica (-13 -> -1 live).

Artefatti: score.txt, analysis.json, sanity-report.txt, lessons.txt, kpi-*, confronto
HTML (APERTO E LETTO, screenshot confronto-top.png): la lettura per-gruppo della pagina
conferma forma compatta e golden-like su 11/14 gruppi (giorni pieni N>=G su G04 64 vs
47 e G10 buchi interni N 0 vs G 1); gli scostamenti sono puntuali e nelle famiglie
note: G04 7 celle vuote-in-span (pool-cap), G08 1, single-day G07/G11/G13.
Le due ore isolate dei rossi sanity non appaiono nei contatori full-day (giorni di stage:
full-day non e' il loro metro), restano visibili solo nel dump per-cell.
