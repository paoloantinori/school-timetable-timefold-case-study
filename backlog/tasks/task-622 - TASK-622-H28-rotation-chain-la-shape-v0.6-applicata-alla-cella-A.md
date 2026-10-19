# TASK-622 - H28-rotation-chain: la shape v0.6 applicata alla cella (T15 esce via swap)

Status: To Do
Priority: medium
Labels: claude-planned, finisher, composite-moves
References: claudedocs/spalmone-2026-27/integrato-1/blind-r1/VERDICT.md, src/main/java/com/school/planner/solver/H28RotationFinisher.java
Created: 2026-09-17

## Il problema

L'audit H28RotationFinisher (c2ee51e5, 17/09 notte) ha chiuso il quadro dell'H28 -1
dell'integrato-1: il trio e' G13 | Lavorazioni meccaniche | 2027-02-12 (T10@8,
T15@9, T10@11, tutte unpinned), il golden ha ZERO ritorni A..B..A su tutto
l'anno (vero vincolo), e il pass dedicato con flag ON TROVA il trio ma la permutazione
interna alla cella non basta (locked=1): per rompere l'A..B..A T15 deve stare a
un estremo (slot 8 o 11) e lì H1/VRP/co-presenza rifiutano.

## La soluzione da costruire

H28-rotation-chain: la shape rotation-with-permission v0.6 applicata alla CELLA invece
che al giorno: T15 (il B del triangolo) esce dalla cella via scambio con una
lezione di altro giorno (same-group cross-date swap, la forma x-swap), lasciando la
cella ai blocchi contigui di T10. Gate H28 pair-safe (h28ReturnsIn before/after),
gates di casa su ogni mossa, belt verifyComposite. Banco target: il trio G13 02-12
dell'integrato-1 (-1 -> 0).

## Note

- L'armamento del pass (flag DEFAULT ON, 4ad0f0dd) e' gia' ratificato sotto la
  direttiva bounded-no-op: chiude le celle dove la permutazione interna basta.
- Questo task copre SOLO la forma locked (la catena di uscita del B).
