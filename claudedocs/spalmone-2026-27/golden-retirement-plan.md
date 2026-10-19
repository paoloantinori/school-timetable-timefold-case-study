# Piano di pensionamento del golden (night 18/19-08, spike 5)

> Premessa: M13-FINAL ha provato 0hard senza golden (deterministico, tentativo 1,
> KPI indistinguibili). Il golden e' TECNICAMENTE pensionabile sul path
> production. Questo documento censisce ogni dipendenza residua da
> seed/lessons.csv nel repo e cosa serve per ritirarlo del tutto.

## (a) RUNTIME production path

Censimento F0 di stanotte confermato COMPLETO dal re-grep su tutto src/main
(ogni altro match e' javadoc/commento: SubjectNormalization,
SchoolScheduleConstraintProvider, DailyActivityQuota, SchoolSchedule,
ScheduleResource, CalendarioImportService, DataGenerator citano il golden solo
in documentazione; CalendarioImportService legge SOLO i calendario_*.csv).

| lettore | stato | azione per il ritiro | priorita' |
|---|---|---|---|
| loadGoldenCoPresenceDepth | CONGELATO (fallback FROZEN, 136 celle, identity gate) | nessuna: gia' input statico | fatta |
| loadGoldenSlot0Anchors | CONGELATO (fallback FROZEN, 67 righe, identity gate) | nessuna: gia' input statico | fatta |
| loadAllGoldenProfiles | INERTE (source=input e' il DEFAULT dal flip TASK-452) | nessuna (il ramo golden resta codice morto dichiarato) | bassa |
| buildMonoRoomCadence (TASK-449) | INERTE (flag OFF, prototype) | nessuna: se un giorno si arma, andra' congelato alla stessa maniera | differita |
| BudgetReservation / LabRotationDeriver | INERTI (chain flag OFF, capacita' workbook-futura) | nessuna: catena chiusa per decisione maintainer 18/08 | differita |

## (b) STATICHE precotte (NON leggono il golden a runtime: gia' pensionate di fatto)

| risorsa | consumatori | forma |
|---|---|---|
| claudodocs/.../data/golden-cadence-reference.json | M34/M35 (pesi letti dal JSON, mai dal CSV) | derivata una volta, fissa |
| seed/golden-copresence-depth.csv | H-COPRESENCE fallback | congelata 18/08 con identity gate |
| seed/golden-slot0-anchors.csv | first-hour block fallback | congelata 19/08 con identity gate |
| golden-fingerprint-sharpness.json, golden-hole-topology.json | KPI di riferimento | dataset storico |

## (c) TEST golden-dependent

| test | cosa perde senza il golden | rimpiazzo sensato |
|---|---|---|
| GoldenSolutionRegressionTest | i pin di regressione contro la soluzione della scuola | snapshot dei NOSTRI run certificati 0hard |
| WeeklyTemplateConstructionRealSeedTest | ricorrenza fine-costruzione vs golden (~76%) | soglia fissa sul valore storico + ricorrenza run-vs-run |
| CoPresenceDepthLoaderTest / RawLessonsCsv users | leggono il CSV da classpath di test | puntano alle risorse CONGELATE |
| GoldenScheduleImport*Test, GoldenImportResourceTest | l'endpoint stesso perde senso | @Disabled con nota, o rimozione con l'endpoint |
| GroupCalendarSeedTest / DataGeneratorSeedTest | divergenze seed-vs-golden | congelare le divergenze note come fixtures (task466) |

## (d) SCRIPT di analisi (misura, non produzione)

parse_golden_csv in scripts/analyze/shared.py + battery KPI + derive-golden-*.py:
RESTANO VALIDI finche' lessons.csv esiste come DATASET STORICO
(claudedocs/.../data/lessons.csv, copia archiviata). Nessuna azione obbligatoria;
opzionale un flag --golden per puntare la copia storica quando il file lascera'
seed/ (capture-run.sh documenta gia' il path alternativo).

## (e) IMPORT endpoint

POST /api/schedule/import-golden: senza il file, SeedCsv.load lancia
RuntimeException con messaggio esplicito -> HTTP 500 leggibile. NON serve il
pattern FROZEN: l'endpoint e' un'operazione esplicita che senza golden e'
priva di scopo. Al ritiro: declassare il bottone UI con nota o rimuovere
endpoint+UI insieme al file.

## Cosa resta da fare PRIMA di cancellare seed/lessons.csv (lista corta)

1. Congelamenti: FATTI entrambi (co-presenza, slot0-anchors) con identity gate.
2. Copiare il CSV in claudedocs/spalmone-2026-27/data/lessons.csv come dataset
   storico (verificare che la copia sia all'ultima versione).
3. Test: loader-test sulle risorse congelate; declassare i test import-golden;
   GoldenSolutionRegressionTest su snapshot run certificati (o storicizzato).
4. UI/endpoint import-golden: decisione di rimozione o nota.
5. Un run di certificazione golden-free standard quando si decide il ritiro
   effettivo (il M13 l'ha gia' fatto in profilo di test).

Niente altro: il path production e' gia' immune (verificato end-to-end).

## Appendice (coda notturna): census script analyze, golden-dependent vs run-only

Su 59 script in scripts/analyze: 20+ leggono il golden (parse_golden_csv /
lessons.csv). Tre famiglie:
1. KPI golden-vs-run (fill, daystart, cadence, sharpness, holes, anatomy,
   viz, parity-check): richiedono il golden COME ORACOLO di confronto; con il
   file pensionato puntano la copia storica claudodocs/.../data/lessons.csv
   (gia' default di alcuni, es. capture-run.sh lo documenta).
2. derive-golden-*.py (incarichi-corrections, availability-checks,
   stage-on-closure, template-recurrence): estrazioni ONE-SHOT gia' consumate
   (seed normalizzato, regole derivate): valore archivistico puro.
3. mining/violation/classification (golden-mining*, bucket_c, tag-evidence,
   overflow, block-size, rotation-extract): minerarie del periodo golden:
   dataset storico, nessuna azione.
I restanti ~39 sono run-only (parse di lessons.txt/export dei run): già
immuni dal pensionamento.
