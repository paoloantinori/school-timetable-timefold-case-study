# Registro longitudinale per la predizione del destino dei tentativi (TASK-581)

Scopo (maintainer, 4/09): accumulare, per OGNI tentativo di OGNI ciclo, i segnali
precocie il destino finale, finche' i dati bastano a cercare pattern (correlazione
quando n e' sufficiente). NESSUNA decisione automatizzata finche' la misura non
dice che un cutoff precoce e' sicuro.

## Cosa registrare per tentativo (una riga CSV, append a attempt-traces.csv)

- cycle_id, attempt, grasp_seed, start_ts
- construction_end_ts, construction_hard (score subito dopo la costruzione)
- h17_holes_after_construction, unassigned_after_construction
- bt1_end_ts, best_hard_bt1 (il migliore hard a fine B-TIMESLOT-1)
- flat_span_bt1_seconds (quanto il best e' rimasto immobile in BT1)
- bt2_best_hard, bt3_best_hard (se esistono)
- release_boundary_hard (il verdetto attuale di condanna: stagedHardAcceptThreshold, default -40 dal TASK-635 04/10, era la costante -2)
- doomed (booleano: il verdetto attuale), room_skipped (skip-doomed-room scattato)
- final_hard, final_medium, wall_seconds
- win (booleano: e' il tentativo vincitore del ciclo)

Fonti: /tmp/planner-prod.log righe gia' presenti (score per fase nel phase log,
boundary hard ai confini, "attempt N done") + phases.json della capture.
 Nessun codice nuovo per ora: prima si vedra' se il log basta o servono righe aggiunte.

## Regola di raccolta

- append ad ogni capture di ciclo (anche i cicli NOTTURNI: e' il registro che accumula valore).
- La ricerca dei pattern parte quando ci sono >= 50 tentativi tracciati.
