# PREDIZIONE re-certifica cleanups 8dc2efe1 (Fedora, pre-registrata; laptop la riporta PRIMA di leggere)

Cleanups sotto prova (behavior-preserving per costruzione): hoist movable(top) fuori dal
loop di tryCompactSwap; GATE-E a corpo unico donorDayYieldsAtLeast con wrapper
strict/tolerant; costante COMPACT_SWAP_FLAG; soli javadoc oltre.

PREDIZIONE: TUTTI i numeri certificati del porting INALTERATI al byte:
- transfer A sola: 6/1/0/2, hard -9 -> -8;
- B sola: 6 mosse / 1 cella, hard -9 -> -8;
- catena A->B: -7;
- inerzia flag OFF: step14 m39-off -3 -> 0 (targets=3 slides=3 rotations=14 moves=17)
  e m39-on -6 -> -4;
- suite integrale: 1341/0/0/80 EXIT 0.

NOTA census: le chiavi HRF-CS-MOVABLE ora contano AL PIU' 1 per target con top
immobile (prima N): l'hoist cambia il CONTEGGIO del census, non le mosse; se un
census HRF-CS-* cambia numero senza che cambino le mosse, e' atteso, dichiaralo e basta.

Divergenza MOSSE o ESITO da qualunque numero sopra: FERMA, cattura, riporta
(sarebbe un errore nei cleanups, non un banco ballerino).

Esito misurato: VEDI SOTTO (scritto dopo le corse).

## ESITO MISURATO (laptop, 15/09 sera, wt/blind-live-r1 @ 8dc2efe1): 6/6 CONFORMI, zero divergenze

1. transfer A sola (step29a): targets=6 slides=1 rotations=0 moves=2, hard -9 -> -8,
   mossa identica (G11 2026-10-30 Scienze 11->12 + Gestione 13->11). = certificato.
2. transfer B sola (step29b): moves=6 cellsClosed=1, hard -9 -> -8. = certificato.
3. catena A->B (step29c): hard -9 -> -7 [B: moves=7 cellsClosed=1]. = certificato.
4. inerzia m39-off (step14, -Dhrf.debug): targets=3 slides=3 rotations=14 moves=17,
   hard -3 -> 0; census {HRF-SYM-HOLE=3, HRF-VISITED=139, HRF-H1=3,
   HRF-SYM-CODEST-PARENT=44, HRF-DEPTH=116} IDENTICO al pre-cleanup. = certificato.
5. inerzia m39-on (step14): targets=4 slides=2 rotations=8 moves=10, hard -6 -> -4.
   = certificato.
6. suite integrale heap 12g: Tests run: 1341, Failures: 0, Errors: 0, Skipped: 80,
   EXIT 0, BUILD SUCCESS. = certificato.

CENSUS (nota attesa): HRF-CS-MOVABLE assente (0 occorrenze) in TUTTI i banchi,
invariato rispetto al certificato: nessun target con top immobile su questi bacini,
quindi il cambiamento di conteggio dell'hoist e' latente (non osservabile qui).
NESSUNA divergenza di mosse o esito. I cleanups sono confermati inerti.
