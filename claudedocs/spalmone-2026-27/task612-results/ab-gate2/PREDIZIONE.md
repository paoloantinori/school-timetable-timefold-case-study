# PREDIZIONE gate A/B ripetuto TASK-612 su codice curato (14/09 sera, PRIMA dei risultati)

Mandato /tmp/mandato-ab614.txt: stessa coppia di braccia del gate del mattino,
ora su HEAD 707c8853 (cura TASK-614: isSlotLegalFresh su FCC + catch con
PlacementSnapshot sulla delivery refinement).

## Predizioni

1. CONTROLLO CRASH (il piu' importante): NESSUNA delle due braccia muore con
   IllegalStateException VRP alla delivery refinement. Meccanismo atteso: la
   cura (a) impedisce a FCC di CREARE placement H5-illegali; il catch (b) e'
   la rete che consegna il winner pre-rifinitura anche se un assert dovesse
   arrivare da altro verso. Se MUOIE: la cura non regge, fermata e documento.
2. Braccio A2 (controllo, flag default): consegna analogamente al -15 del
   mattino SE capita nello stesso tipo di bacino (tentativo 1 condannato +
   predictor che scarta i reseed); oppure 0-hard se il primo tentativo e'
   buono. La varianza bacino-e' il fatto dominante, non il flag.
3. Braccio B2: riga [delivery] presente al punto pre-save; comportamento
   no-op o chiusure a seconda del residuo consegnato (counter=0 NON blocca);
   hard B <= hard A a parita' di meta' bacino (entrambe 0-hard, o entrambe
   residuo con B non peggiore); sanity e KPI in banda; nessuna regressione di
   famiglia (improve-or-noop).
4. Concordanza hard mem/db attesa su entrambe (medium divergente = TASK-509
   noto).

Falsificazioni: braccia morta al delivery refinement (cura non regge); hard B
peggiore di A con [delivery] moves>0 (oracolo rotto); riga [delivery] assente
in B (flag perso: guardia banner fallisce).
