# TASK-562 A5: fit della hand-rule di screening bacini (27/08, quinta tratta)

Domanda: le feature POST-LS (H17, H34, tier alla fine della prima LS timeslot)
separano il vincitore dai condannati? Campione: i 5 tentativi del ciclo
multi-start dci-h34ms (1 vincitore deterministico + 4 GRASP condannati),
misurate dai checkpoint POST_B_TIMESLOT-1 e POST_EXACT_FINISHER.

## I dati

| att | H17@bt1 | H34@bt1 | H17@EF | H34@EF | tier@bt1 | esito |
|---|---|---|---|---|---|---|
| 1 | **5** | 22 | 4 | 12 | **-42** | VINCITORE |
| 2 | 36 | 21 | 34 | 10 | -76 | condannato |
| 3 | 47 | 20 | 48 | 10 | -99 | condannato |
| 4 | 21 | 24 | 18 | 14 | -107 | condannato |
| 5 | 42 | 17 | 42 | 11 | -112 | condannato |

## La hand-rule che emerge

**CONDANNA se H17@bt1 > 15 oppure tier@bt1 < -60.**

- H17@bt1 > 15: becca 2, 3, 5 (36/47/42); risparmia il vincitore (5); il
  falso negativo e' il tentativo 4 (21... no aspetta: 21 > 15: becca anche 4).
  Sul campione: 4/4 condannati catturati, 0 falsi positivi, 0 falsi negativi.
- tier@bt1 < -60: becca 2, 3, 4, 5 (-76/-99/-107/-112); vincitore -42 salvo.
  4/4, 0/0.

Congiunta (OR): stessa copertura (le due regine catturano lo stesso quadro:
H17 e tier sono quasi la stessa informazione al bt1).

**H34 NON separa**: 17-24 ovunque, il vincitore ha 22 (in mezzo al pack):
coerente con la bacino-invarianza gia' dimostrata. Non e' una feature di
screening: e' un dato del foglio.

## Falsi positivi/negativi sul campione

n=5 (1 positivo, 4 negativi): la regola e' perfetta SUL CAMPIONE, ma n=5 non
dimensiona nulla (Wilson su 4/4: [0.51, 0.99]). Il valore del fit e' la
DIREZIONE (le feature post-LS separano nettamente: il gap H17 vincitore-
condannati e' 5 vs 21+, 4x), non le soglie definitive.

## Come si userebbe (protocollo, 1 pagina)

1. N costruzioni (il costo di una costruzione e' ~11s + prima-LS breve).
2. Dopo una prima-LS CAPPOSATA (budget fisso corto, es. 3-5 min, uguale per
   tutte): misura H17@bt1 e tier@bt1 dal checkpoint.
3. SCARTA i tentativi con H17@bt1 > 15 o tier < -60 (il 15 e il -60 sono le
   soglie del campione: da ricalibrare a n>=20).
4. DEGLI sopravvissuti, riprendi (resume dal POST_B_TIMESLOT-1) il migliore
   per tier e portalo a ciclo completo.
5. Risparmio atteso sul ciclo dci-h34ms: 4 condannati x ~60-80 min di coda
   ciascuno = ~4-5h di wall, contro N x (11s + 5min) + 1 full (~1.5h).

NOTA di coerenza: e' la stessa direzione del predittore early-doom gia' in
produzione (TASK-516/iter-16: profilo post-costruzione > 150 scarta), ma con
la firma SPOSTATA dove il ledger dice che discrimina (post-LS, non
post-costruzione: il registro del 26/08 ha mostrato che la firma di
costruzione non separa: tutti partono tier ~-2950). Il predittore attuale
guarda nel posto sbagliato per questa famiglia di seed.

## Dichiarazione

La separazione ESISTE sul campione (netta, 4x sul gap H17). Le soglie sono
indicative (n=5). Il protocollo sopra e' la proposta; l'adozione richiede il
ricalibro a n>=20 (il ledger A5 li collezionera'). Nessun codice scritto:
questa e' la pagina di protocollo richiesta.

## ESTENSIONE OUT-OF-SAMPLE (27/08 01:2x, sesta tratta): cosa e' recuperabile e cosa no

### I punti puri aggiunti (ledger n=5 -> n=7)

Il ciclo h34on (single-attempt deterministico, consegna -14hard = bacino buono)
e' l'unico storico con checkpoint tier puri sopravvissuti (in /tmp):

| punto | confine | tier | etichetta | regola scatta? |
|---|---|---|---|---|
| h34on winner | POST_B_ROOM | **-14** | buono (consegna -14) | NO (corretto) |
| h34on winner | POST_RELEASE | **-35** | buono | NO (corretto) |

0 falsi positivi sull'estensione: la regola tier < -60 non condanna i due
punti buoni aggiuntivi. Il campione puro totale: 7 punti (2 buoni extra + i
5 del ciclo h34ms: 1 buono, 4 condannati): la regola resta 0 FP / 0 FN su
tutti e 7.

### Perche' gli altri ~300 capture storici NON sono usabili (dimostrato)

Il phases.json registra l'endScore PIENO della prima fase LS, che include il
room-tier (H29 roomless) pre-fase-stanze, di magnitudine VARIABILE per era:
- qfrv2-on (26/08): prima-LS pieno **-4045** contro tier puro ~-27 al confine
  equivalente: contaminazione ~-4.000;
- gate 446-era (luglio): prima-LS pieno **-31/-37** (contaminazione ~0);
- le consegne buone spaziano da prima-LS -31 a -4.628: la regola
  "tier<-60" applicata al numero pieno condannerebbe vincitori certi
  (verify-m3435: prima-LS -1789 -> finale 0hard: FALSO POSITIVO immediato).

La feature piena e la feature pura sono su scale INCOMPARABILI fra ere: il
test out-of-sample sulla feature piena NON e' un test della regola (misura
la contaminazione, non la separazione). L'H17@bt1 non e' ricostruibile da
nessuno storico (serve il checkpoint del confine, salvato solo dal ciclo
d'oggi).

### VERDETTO onesto dell'estensione

- La regola REGGE su tutti i punti puri recuperabili (7/7 corretti), ma il
  lato condannati resta n=4 da UNA famiglia di seed di UNA notte: non e'
  validazione out-of-sample vera, e' ampiezza di campione +2 sul lato buono.
- La via per la validazione vera e' PROSPECTIVA: ogni futuro multi-start
  salva i checkpoint per tentativo (gia' vero): il ledger cresce da solo.
  Soglia di decisione per l'uso operativo del protocollo: n>=20 tentativi.
- REGOLA DI REGISTRAZIONE per il ledger futuro: registrare SEMPRE
  H17@bt1 + tier@bt1 puri (i checkpoint li hanno: due righe di estrazione
  da aggiungere al wrapper multi-start, candidato micro-task).
