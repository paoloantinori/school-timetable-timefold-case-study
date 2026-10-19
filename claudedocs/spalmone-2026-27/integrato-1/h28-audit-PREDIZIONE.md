# PREDIZIONE audit H28 (Fedora, pre-registrata; laptop la riporta PRIMA del replay)

> il pass e' o (a) non-innescato al confine (wiring/gap di posizione: il trio
> nasce a valle del suo punto di corsa), o (b) innescato ma il suo gate rifiuta
> T15 come C (conteso/VRP): in entrambi i casi la cura e' piccola
> (riposizionare il pass post-HRF nel wrapper, o un gate). Se il banco chiude
> il trio col pass esistente: -1 -> 0 sul replay e il quadro e' completo.

Esito: VEDI SOTTO.

## ESITO AUDIT (step47, replay export integrato-1, heap 16g): predizione (a)+(b) ENTRAMBE verificate, in cascata

### (1) WIRING: il pass NON GIRA alla consegna. Cura = UN FLAG.

Il banner nel log dell'integrato-1 (45+ occorrenze):
`H28 rotation finisher (TASK-595, prototype): enabled=false`
Il pass e' prototipo TASK-595 **opt-in DEFAULT OFF**
(`planner.construction.h28-finisher.enabled`, solo "true" arma): al confine di
consegna non e' MAI stato invocato. Predizione (a) CONFERMATA alla prima riga
del log: e' un gap di ARMAMENTO, non di posizione.

### (2) Replay col flag ON: il pass TROVA il trio ma il suo gate LO BLOCCA

`Result[cellsViolating=1, cellsRepaired=0, pinBlocked=0, locked=1]`,
hard **-4 -> -4**: la cella violante e' ESATTAMENTE il trio nominato
(G13/Lavorazioni meccaniche/02-12), tutte mobili (pinBlocked=0), ma
`applyFirstLegalOrdering` non trova NESSUNA permutazione legale dei blocchi
Docente sui 3 slot: **locked=1**. Predizione (b) CONFERMATA: innescato, il
gate rifiuta. I gate in gioco per la permutazione (grid.cellOk = H1 live +
vacation + H3 cell + room + VRP; piu' il gate co-presenza in-pass): per una
cella (gruppo, materia, data) G13 di 3 ore con blocchi T10(2)+T15(1),
le permutazioni non-A..B..A richiedono che T15 stia a un ESTREMO (slot 8
o 11): il blocco e' il gate che rifiuta (T15 agli estremi scontra H1/VRP
con le sue altre lezioni di quel venerdi', o la co-presenza cap).

### La cura e' PICCOLA in entrambe (come previsto), ma DOPPIA

1. **Armamento**: il flag va portato a default-ON nella catena di consegna
   (la direttiva bounded-no-op lo copre: il pass e' oracle-bound, sweep-cap,
   improve-or-noop nel wrapper) - decisione Fedora/maintainer, forma ratifica.
2. **Il locked=1 sul trio**: anche armato, QUESTO stato non si chiude col pass
   com'e': la permutazione interna alla cella non basta (serve la forma
   rotation-with-permission: T15 esce/via scambio con lezione di altro
   giorno). Il pass valuta SOLO riordinamenti interni alla cella: la specifica
   mancata e' la stessa family-shape del v0.6 (catena docente), applicata alla
   cella H28. Non e' un gate da allargare: e' una forma mancante.

## VERDETTO (tre righe)

1. IPOTESI: (a) non-innescato o (b) gate rifiuta T15-C.
2. MISURATO: ENTRAMBE in cascata: (a) il banner dice enabled=false in tutto il
   ciclo (prototipo opt-in mai armato: gap di armamento, non di posizione);
   (b) armato in replay, il pass trova il trio (cellsViolating=1) ma
   applyFirstLegalOrdering non trova permutazioni legali: locked=1, -4 -> -4:
   la permutazione interna non basta, serve la catena (T15 fuori via
   scambio).
3. RACCOMANDAZIONE: (i) ratifica dell'armamento (bounded-no-op: il pass com'e'
   e' sicuro, chiudera' le celle dove la permutazione basta); (ii) la specifica
   per il residuolocked e' H28-rotation-chain (la v0.6 shape applicata alla
   cella): task nuova, non allargare i gate del pass.
