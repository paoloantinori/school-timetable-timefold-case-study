# PATCH PREPARATA (NON APPLICATA): cura input G07 2027-06-09 (5,S) -> (5,A)

Famiglia TASK-550 (golden-first input cure). Il foglio
`calendario_class_days.csv` dichiara G07 09/06 come (5,S) ma il golden
quel giorno fa 5h ORDINARIE: Educazione Civica T23 x3 (slot 0,2,3) +
Scienze T25 x2 (slot 1,4). Con la riga a modality S la quota aula+lab
del giorno e' 0 e quelle 5 ore non hanno bersaglio dichiarato.

LA CURA (da applicare su ordine di maintainer, ENTRAMBE le copie byte-identical,
CRLF preservati, come il precedente G09 del 27/08):

```
src/main/resources/seed/calendario_class_days.csv
claudedocs/spalmone-2026-27/data/calendario_class_days.csv

- G07,2027-06-09,5,S
+ G07,2027-06-09,5,A
```

VERIFICA da correre dopo l'applicazione (la prova che la cura lavora):
1. `./scripts/core/test.sh -c WeeklyTemplateConstructionRealSeedTest` e grep
   G07|2027-06-09 nel dump: atteso il giorno PIENO di ordinarie a fine
   costruzione (oggi e' vuoto: replay 17/09 notte).
2. `DataGeneratorExpectedDailyHoursSeedTest`: la riga cambia ore-colonna ma
   non totali (5 ore restano 5, cambia solo la modality): pin attesi
   invariati nel numero, verifica.
3. `GoldenSolutionRegressionTest`: il golden su quel giorno fa 5h ordinarie:
   con la quota ora aula=5, H32 chiede 5 e il golden li ha: atteso pin
   invariato o migliorato (zero nuovi match).
4. Notifica referente a posteriori (famiglia TASK-550, non bloccante).
