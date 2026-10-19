# PREDICTION pre-registrata TASK-641 room-aware A/B (23/09, PRIMA del lancio)

Braccio A task641-off (room-aware assente, 638 DEFAULT ON) vs braccio B
task641-on (-Dplanner.construction.violation-steepest.room-aware.enabled=true).
Protocollo 636/637/638. Criteri: il mandato (5).

1. Hard: ON mai peggiore di OFF PER COSTRUZIONE (l'oracolo resta improve-or-noop
   full-hard sul composito timeslot+stanza: se il criterio fosse violato sarebbe
   un BUG da indagare, non una misura).
2. Ogni apply +ROOM documentato (subject, stanze, delta): attesi SOLO se il
   residuo della consegna lo richiede (cure che collidono stanze: classe
   integrato-1/H29). Il caso piu' probabile su ciclo fresco: applied=0 o 1-2
   mosse, perche' i residuali H17/H34 tipici della race non sono target del
   room-aware.
3. KPI in banda: ricorrenza >= 68, full-day >= 93.
4. Census banner: ON "roomAware=true", OFF "roomAware=false" (entrambi con
   enabled=true del 638 ratificato).
5. Sanity per-gruppo: nessun rosso nuovo ATTRIBUIBILE al pass (le mosse
   +ROOM toccano stanze: un rosso stanze nuovo sarebbe del pass).
