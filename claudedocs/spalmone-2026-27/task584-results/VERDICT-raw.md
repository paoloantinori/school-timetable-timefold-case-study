# VERDICT-raw: batch TASK-584 (profilo b584, notte 5-6/09)

Il claim m-13 ("multi-start consegna 0-hard statisticamente garantito", misurato
32/32 il 9-10/08) viene RE-VALIDATO sul vincolame corrente (H31+H32+H33+H34 DEFAULT
ON, SurplusHarvest OFF, maxAttempts=1, GRASP seeds 102-109 via
`-Dplanner.multistart.test.force-grasp-seed=N`).

## RISULTATO PRINCIPALE

**p̂(0-hard) = 0/8 = 0,000. Wilson95 [0,000; 0,324].**

AGGIORNAMENTO 10:20 (seed 101 di rimpiazzo consegnato: **[-116/0]hard**, 0 su 9):
**p̂ = 0/9. Wilson95 [0,000; 0,299].** Curva invariata nella sostanza
(P(>=1|N=5) = 0,000; a CI_hi 0,299: 0,831).

| N | P(>=1 0-hard) a p̂=0 | a p=CI_hi 0,324 |
|---|---|---|
| 1 | 0,000 | 0,324 |
| 2 | 0,000 | 0,544 |
| 3 | 0,000 | 0,692 |
| 5 | 0,000 | 0,859 |

Il claim NON e' re-validato: sul vincolame corrente NESSUN seed dei 8 consegna
0-hard (range -65/-167, vedi sotto). Contro lo storico p=0,75 (n=8, 3/08) e 32/32
(9-10/08, PRE-H31/H32/H33/H34). La differenza di metro e' ESATTAMENTE quella che
il batch esisteva per misurare: i quattro vincoli hard introdotti dopo la misura
storica hanno cambiato il bacino. La decisione (budget N come costo, o altro) e'
di Fedora/maintainer: il laptop misura.

## Score per seed (tutti bacini cattivi)

```
s102: [-102/0]hard/[-850703/32104]soft    s106: [ -92/0]hard/[-822272/13649]soft
s103: [ -65/0]hard/[-881263/-1169]soft    s107: [-100/0]hard/[-910173/-4314]soft
s104: [ -77/0]hard/[-891436/2974]soft     s108: [-167/0]hard/[-837380/9823]soft
s105: [-156/0]hard/[-972223/16648]soft    s109: [ -65/0]hard/[-899394/-11254]soft
```

## VALIDAZIONE LIVE DEL METRO TASK-526 (design punto 6)

**8/8 consegne con `delivery concordance OK (H29 room-null memory=N db=N)`**:
s102 memory=1 db=1; s103-s106, s108 memory=0 db=0; s107 e s109 memory=1 db=1.
La prima validazione continua del metro di fiducia: ZERO divergenze, ZERO righe
check-failed dopo il fix e95a4100. Il metro e' ora di routine a ogni consegna.

## Qualita' operativa del batch

- avr=0 e IllegalStateException=0 su TUTTI i run (16 cicli-app complessivi):
  la notte-4sep ha tenuto anche qui.
- 0 invalidi-infra: l'ambiente e' stato stabile tutta la notte (dopo i due
  incidenti del pomeriggio, nessuna recidiva).
- Auto-guarigione infra: mai innescata.
- Durata media ~1h25m/seed (boot-solve-consegna).

## NOTE METODO (per il registro e le decisioni)

1. KNOB [FEDORA, confermare]: il tentativo 1 e' SEMPRE deterministico nel wrapper
   (graspEnabledForAttempt(1)=false, anche con honor-operator-grasp): l'unico modo
   di mettere un seed GRASP al tentativo 1 e' `-Dplanner.multistart.test.force-grasp-seed=N`
   (hook TASK-486 AC#4, javadoc testuale). Il batch lo ha usato: i semi sono
   costruzioni GRASP pure a tentativo unico.
2. Il seed 101 e' stato consumato dal driver-burn iniziale (flag storici): da
   recuperare a scelta (rimpiazzo statistico, la serie non cambia).
3. Bug FCC trovato e NON toccato: format-string `%d` con HardLevels
   (IllegalFormatConversionException nel log, ingoiata da JUL: cosmetico).
4. Sentinella di consegna del driver: la firma "validated (trigger=solve-completed"
   non e' affidabile (il watchdog puo' skippare per tutto il run): corretta sulla
   riga di concordanza (incondizionata a ogni consegna).
5. Storia operativa della serata nel verbale /tmp/batch584/driver.log (volatile):
   burn flag delle 17:15, stop prematuro 18:57-19:02, cascata boot-fail 18:58,
   rilancio pulito 19:29. Nessuna di queste e' una misura.

## AC del task

- #1 N=8 costruzioni GRASP indipendenti sul vincolame corrente: FATTO (102-109,
  score.txt per ciascuna in run-b584-s*/).
- #2 p(0-hard) con Wilson95: 0,000 [0,000; 0,324].
- #3 Verdetto: il claim m-13 e' SMETTITO sul vincolame corrente (0/8).
- #4 p e' calata: la raccomandazione sul budget NON puo' venire dai soli 0/8
  (CI_hi 0,324 ancora largo): servono piu' dati o la decisione e' altrove
  (vincolame/cura search-side: le celle G09/G11 sono deficit input TASK-586).
- #5 aggiornamento milestone m-13: da fare (riga con data 6/09 e esito 0/8).
