# PREDIZIONE gate A/B TASK-613 v0.6 (15/09 mattina, PRIMA dei risultati)

Protocollo = ab-gate2 TASK-612. Braccio A3 = default (relay/h32 del 612 sono
gia' DEFAULT ON in produzione); braccio B3 = + -Dplanner.construction.hole-relay.enabled=true.

1. CONTROLLO CRASH (e): NESSUNA braccia muore con IllegalStateException VRP
   alla delivery refinement (la cura 614 e' nel tree dal gate 2, ha tenuto su
   2+2 braccia; v0.6 e' dopo di essa).
2. Riga [delivery] TASK-613 in B3 al punto previsto (post EDF-ext, pre-save).
   L'efficacia dichiarata dal banco e' -3 -> 0 SUL RESIDUO DI REPLAY: sul live
   dipende da cosa sopravvive alla consegna:
   - se il bacino consegna 0-hard (multi-start buono al primo tentativo):
     residuo vuoto, [delivery] 613 con mosse=0, counter=0 NON blocca;
   - se consegna con buchi H17 residui (bacino condannato attempt-1 come i
     gate precedenti): mi aspetto [delivery] 613 con mosse>0 e almeno un buco
     chiuso: hard B3 < hard A3 a parita' di bacino.
3. (a) hard B3 <= hard A3 a meta'-bacino comparabile; (c) sanity non
   peggiorata; (d) KPI in banda (full-day ~93-94%, ricorrenza unpinned ~71%).
4. Concordanza mem/db hard attesa su entrambe (medium divergente = TASK-509).

Falsificazioni: braccio morta; hard B3 > hard A3 con [delivery] 613 moves>0
(oracolo rotto); banner assente in B3 (flag perso); sanity peggiorata.
