# Rehearsal golden-free della chain (TASK-523, laptop 18/08 sera)

> Mandato sera 18/08. Domanda: la chain (rotazioni congelate + enumerator +
> budget-reservation) sopravvive senza seed/lessons.csv? Risposta: SI, con costo
> concentrato e previsto. Sezione GOLDEN-FREE nel VERDICT ab-chain per i numeri.

## Come e' stata ricostruita la condizione production

1. Le 15 rotazioni derivate del run8 estratte dal profilo e inserite come input
   statico nel profilo fresco gf-rehearsal (labrotation via SQL).
2. seed/lessons.csv rimosso da src/main/resources, JAR ricostruito senza golden
   (backup in /tmp durante il test, file ripristinato da HEAD a fine run e JAR
   ricostruito con golden: CorsPublishedOriginRegressionTest 2/2 sul JAR finale).
3. Flags: deriver OFF (righe gia input), enumerator ON, budget-reservation ON.

## Scoperte (commit 88604ee2)

- SENZA golden l'app non partiva: loadGoldenCoPresenceDepth throwava e
  /api/schedule 500. Curato: i tre loader golden degradano con warn forte.
- BudgetReservation: il fallback dichiarato non scattava davvero (throw alla
  prima riga): curato, ora il banner dichiara il path fallback e il clamp.

## Esiti (dettagli nel VERDICT, sezione GOLDEN-FREE)

- L1 piano: 4.663 pin, H7 pulito, parity 21 flag senza T15/G04. VERDE.
- L2 distanza dal piano golden-live: coppie non-T15 IDENTICHE; T15
  +70h totali = delta reservation 1h vs 2h per giorno-L.
- L3 solve: -27hard contro -3 del run8 (H29 -15 ospite-inerte, H7 -5, 3 RA
  boxed), KPI in banda (full-day 76,9, ricorrenza 68,9, buchi 1).

## Proposta workbook per maintainer (NON applicata)

Tre congelamenti chiudono il gap: rotazioni (15 righe), ritmo per coppia
(carico golden nei giorni della materia), profondita' co-presenza per cella.
I primi due sono quantificati qui; il terzo e' il censito TASK-452 bloccante.
