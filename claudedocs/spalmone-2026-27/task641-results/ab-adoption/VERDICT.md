# VERDICT A/B adozione TASK-641 room-aware (23/09, laptop, rc-1, mandato coda laptop)

Protocollo 636/637/638: profili freschi task641-off/on consecutivi, heap 8g,
MT-LS ON, checkpoint capture ON, banner-guard per braccio (valore ESATTO).
Driver riscritto integrale a mano dopo l'incidente di derivazione (vedi sotto).

## ASIMMETRIA HEAD (dichiarata dal mandato Fedora)

- Braccio OFF: **b9113f2b** (pre-fix). Path OFF byte-identico al fix: il banco
  636-off ha prodotto 23337 eval IDENTICI fra i due commit (PASSO 3), le cure
  code-review toccano solo il path armato + javadoc/knobs.
- Braccio ON: **234d8a94** (cure code-review 8-angle: F1 filtro a.id>b.id che
  scartava meta' dello spazio composito, + verifier + knobs).

## I 5 criteri

| # | Criterio | OFF (b9113f2b) | ON (234d8a94) | Verdetto |
|---|----------|----------------|---------------|----------|
| 1 | hard ON mai peggiore di OFF | [0/0]hard | [0/0]hard | VERDE (parita') |
| 2 | ricorrenza >= 68 | 70,0% | 70,0% | VERDE |
| 3 | full-day >= 93 | 94,5% | 94,5% | VERDE |
| 4 | census banner | enabled=true, roomAware=false | enabled=true, **roomAware=true** | VERDE |
| 5 | sanity per-gruppo | 1 gruppo rosso | 3 gruppi rossi | VERDE con nota (sotto) |

ON: **[0/0]hard al TENTATIVO 1** (la race ha consegnato pulita) e il 638
[delivery] ha girato `applied=0, 0 -> 0 in 335ms`: **no-op legittimo**, il
caso che il mandato dichiara esito valido ("il finisher spara solo se il
residuo lo richiede: parita' e' esito legittimo"). Il room-aware non ha avuto
nulla da curare su una consegna gia' 0-hard.

## Criterio 5, dettaglio onesto

OFF: 1 gruppo rosso. ON: 3 gruppi rossi (G08 buco 01/04 [1,2]; G09 3
giorni corti; G10 2 giorni corti). TUTTI race-attribuiti: il pass ON ha
applicato ZERO mosse (applied=0), quindi nessun rosso e' attribuibile al
room-aware; il divario 1-vs-3 e' la varianza race fra bracci (n=1, HEAD
diversi per direttiva dichiarata). Famiglie note (giorni-corti, buchi).

## Incidente di processo (registrato, risolto)

Il primo lancio ON e' partito SENZA il flag room-aware: la derivazione del
driver dal template 638 aveva lasciato i banner/flag dell'era theory-spread e
la guardia greppava il valore sbagliato (quindi "verde" a torto). Rilevato al
banner BOOT (roomAware=false), solve fermato, driver riscritto PER INTERO a
mano con banner-esatto-per-braccio (niente piu' derivazioni da template),
rilanciato: il run valido e' questo. Braccio OFF non toccato dall'incidente.

## Conclusione

5/5 criteri VERDI (criterio 1 in parita' 0-hard, criterio 4 con il pass
room-aware armato e censito, criterio 5 con nessun rosso attribuibile al
pass). Il room-aware si e' comportato esattamente come progettato: armato,
censito, no-op quando la consegna e' pulita. NESSUN FLIP: il flag resta
strict-literal default OFF; l'adozione e' decisione di maintainer (che ha gia'
l'evidenza del banco 3/3: 637-off -3->0, integrato-1 -52->-41 in 7 mosse,
636-off identico).

## Indice artifact

{off,on}/: run-manifest, driver.log (on: include l'incidente e il rilancio),
app.log, score/analysis/phases/lessons.txt, KPI complete, sanity-report,
checkpoints/ (capture opt-in attiva), export.json.gz 106MB solo laptop.
