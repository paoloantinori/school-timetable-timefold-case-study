# PREDIZIONE LEVA A (compact-swap intra-classe) - Fedora, pre-registrata prima delle corse

LEVA: nel finisher H17, per un buco il cui docente di coda e' conteso, provare la
permutazione compatta DENTRO la stessa classe attorno al buco (la forma cieca: la
lezione sotto il buco sale nel buco, la coda scende nella cella liberata; span e
conteggio del giorno preservati), PRIMA di pagare la catena docente cross-classe.
Prototipo in banco con i gate simbolici esistenti + i gate TASK-616 (roomFor null
= reject, VRP per-lezione) + verifyComposite come cintura.

PREDIZIONE (Fedora): la catena del difetto 2 si chiude (H17 6 -> 5) con mosse
pulite; NESSUN altro difetto toccato.

Forma attesa della mossa (dal round cieco, moves-live2.txt): G11 2026-10-30
Scienze(T25) 11->12 + Gestione aziendale(T06) 13->11.

Esito misurato: VEDI SOTTO (scritto dopo la corsa).

## ESITO MISURATO (step26, export arm-b, heap 12g): PREDIZIONE CONFERMATA ESATTA

- LEVA A da sola: targets=6 swaps=1 moves=2, hard **-9 -> -8** = H17 6 -> 5,
  H32 -2 e H34 -1 INTATTI (nessun altro difetto toccato, nessuna famiglia nuova).
- La mossa applicata e' IDENTICA alla cieca: G11 2026-10-30 Scienze(T25)
  11->12 + Gestione aziendale(T06) 13->11 (log in slot-index: 3->4 e 5->3).
- Cintura verifyDate pulita (zero revert); census {CS-H1-BELOW=10, CS-H1-TOP=3}:
  gli altri 5 buchi restano bloccati dall'H1, coerente con le prove di
  inchiudibilita' del cieco.

### VERDICT LEVA A (3 righe)
1. IPOTESI: la famiglia compact-swap intra-classe (fuori alfabeto v0.6) chiude
   il difetto 2 senza toccare nulla altro.
2. MISURATO: confermato al numero (H17 6->5 con la STESSA coppia della cieca,
   2 mosse, cintura pulita, -9 -> -8).
3. TENERE: la famiglia e' reale, piccola e pulita: candidata al porting in src/
   come passo intra-classe nel finisher H17 (PRIMA della DFS cross-classe),
   con i gate usati qui (VRP per-lezione, H1 final-state con ignore del partner,
   roomFor TASK-616, cintura). Decisione Fedora.
