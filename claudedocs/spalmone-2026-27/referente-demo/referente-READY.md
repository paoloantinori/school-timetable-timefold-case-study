# referente-READY (23/09 14:15, maintainer: "aprire il tool a referente appena possibile")

## URL

- **http://laptop.lan:8080** (LAN)
- **https://planner.example.org/** (esterna, verificata 200 con SNI)

## Stato del profilo

Profilo dedicato `referente-demo`, seed 2026/27, UN ciclo completo staged col
stack ratificato (636 cadence-pace + 637 theory-spread + 638 violation-steepest
tutti DEFAULT ON su Timefold 2.7.0-rc-1).

- Consegna: **[0/0]hard** al tentativo 1 (~85 min, tentativi usati: 1/5)
- Ricorrenza unpinned: 69,4% (banda 68-71), full-day 94,4% (banda 94-95)
- Sanity: NON CONFORME su 3 gruppi (G08 buco 01/04 [4,5]; G09 1 giorno
  corto; G10 1 giorno corto): famiglie note, giorni isolati su tutto l'anno,
  l'orario e' comunque perfetto sul tier hard
- ViolationScopedSteepestFinisher [delivery]: applied=0 hard 0->0 in 287ms
  (no-op legittimo: consegna gia' pulita al tentativo 1)

## Cosa puo' toccare referente

- **Solve button** = protocollo production COMPLETO: un click fa
  pre-assegnazione + ciclo staged + multi-start (stop al primo 0-hard).
  L'orario attuale resta caricato: Reset prima di un nuovo Solve se vuole
  ripartire da zero (altrimenti il solver riparte dallo stato corrente).
- **Filtri** per gruppo/docente/stanza nella Schedule Filter Bar.
- **Analyze button** = drill-down violazioni per vincolo (click su una
  violazione per vedere le lezioni coinvolte).
- **Docs page** = la documentazione completa dei 68 vincoli (en/it).
- **Administration** = CRUD di docenti/gruppi/stanze/corsi/turni (ATTENZIONE:
  modifica il profilo referente-demo, non dati di produzione).

## Avvertenze

- Un Solve occupa la macchina per **~1h** (tentativo 1); se la race e' sfavorevole il
  multi-start puo' arrivare a ~3h (5 tentativi max). Non chiudere il laptop (caffeinate attivo).
- Nessun benchmark/test nostro gira mentre referente sperimenta (direttiva maintainer).
- La UI reagisce anche mentre il solver gira: lo stato e' nella barra in alto
  (tentativo X di Y, fase corrente).
- Se qualcosa sembra bloccato: guardare la barra di stato, NON refreshare la
  pagina (un solve in corso continua anche a UI chiusa; per fermarlo usare il
  pulsante Stop).
