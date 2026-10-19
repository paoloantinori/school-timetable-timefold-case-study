# SPIKE 2 (TASK-613): census della popolazione di enumerazione dei target (anomalia D2-b)

Mandato notturno 15/09. Anomalia: il census v0.1 contava "5 candidati valutati
per 3 target" ({HRF-H1=3, HRF-VRP=2}); ipotesi a alternativa: la popolazione
same-group e' piccola (dayKeysOf funziona ma c'e' poco da scansionare) OPPURE
bug del contatore.

Misura (step19HoleTargetPopulationCensus, env PLANNER_TEST_POP_CENSUS=1,
stato [-3] post-catena su m39-off, heap 12g, EXIT 0):

```
[pop] G11 2027-04-30 gaps=1 | same-group days=169 lessons=958 unpinned=958
[pop] G13 2026-11-04  gaps=1 | same-group days=171 lessons=978 unpinned=978
[pop] G13 2027-03-02  gaps=1 | same-group days=171 lessons=978 unpinned=978
```

## Tre righe

IPOTESI: la popolazione same-group visibile a dayKeysOf/tailMap e' piccola e
spiega i "5 valutati". RISULTATO: CONFUTATA: l'universo e' di 169-171 giorni e
~958-978 lezioni PER CIASCUN target: qualunque scansione onesta valuta centinaia
di candidati per buco, non 5 totali. TENERE-BUTTARE: BUTTARE l'ipotesi
popolazione; il "5" del census v0.1 era un ARTEFATTO DEL CONTATORE (il contatore
non misurava cio' che credevamo: i dati census v0.1 non vanno usati per inferenze
di popolazione; i census v0.2d+ con chiavi per-gate sono la sorgente attendibile).
Nota: il codice v0.1 (import scan) non esiste piu' al HEAD ced6903e (revert a
v0.2d): la diagnosi e' chiusa per esclusione, senza resurrezione di codice morto.
