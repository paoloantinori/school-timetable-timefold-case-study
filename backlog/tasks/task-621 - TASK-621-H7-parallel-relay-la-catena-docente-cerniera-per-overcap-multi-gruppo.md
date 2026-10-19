# TASK-621 - H7-parallel-relay: la catena docente-cerniera per overcap multi-gruppo (figlio TASK-620, dal ciclo integrato)

Status: To Do
Priority: high
Labels: claude-planned, finisher, composite-moves
References: claudedocs/spalmone-2026-27/integrato-1/, src/main/java/com/school/planner/solver/HoleRelayFinisher.java
Created: 2026-09-16

## Il problema

Il ciclo fresco integrato-1 (prima consegna con catena completa: bundle + 619 + 620)
consegna [-4/0]: H17 -2, H28 -1, H7 -1. Il banco diagnostico (step41, replay sullo
stato consegnato con -Dhrf.debug, DIAGNOSI-PREDIZIONE.md pre-registrata) chiude la
domanda: l'H7 residuo NON e' innesco (il pass gira) ma FAMIGLIA non coperta.

La forma misurata: T13 2027-01-20, 7 ore (cap 6), tutte unpinned, MISTE su
TRE gruppi: 8-9 (G06), 10 (G01), 11 (G05), 13 (G01), 14-15 (G10).
Census: {HRF-H7CAP-SOURCE-YIELD=8, HRF-H7CAP-NO-CANDIDATE=1}: tutti gli 8 candidati
export cadono sul SOURCE-YIELD. Perche' il paio 620 non scatta: (a) ogni giorno
sorgente e' a quota piena (il mid-day export richiede yield tollerante che non c'e');
(b) la DONOR-TOL e' same-group sul gruppo del candidato e i donatori tolleranti
tail-only sono assenti a quota piena. Firma IDENTICA a T26 7h su A3: overcap da
trascinamento parallelo multi-gruppo del docente-cerniera, non da un giorno sorgente
mal piazzato.

## La soluzione da costruire (spec del VERDICT integrato-1)

H7-parallel-relay: l'equivalente H7 del rotation-with-permission v0.6. Per un overcap
multi-gruppo del docente-cerniera, la catena sgombra ore dal giorno saturo spostando
le lezioni PARALLELE del docente (donatore cross-group sulle ore parallele, o riordino
intra-giorno delle ore parallele nei gruppi ospiti) invece di esportare dal giorno
sorgente a quota. Banco prima (target provati: T13/2027-01-20 su integrato-1
E T26/A3), gates di casa + H7 sull'aterraggio, oracle improve-or-noop full-hard.

## Note

- L'H17 -2 residuo e l'H28 -1 sono famiglie separate (H28 non ha pass: eventuale
  round cieco futuro). Il -4 complessivo e' DENTRO la banda GRASP ±5 (firma identica
  al braccio B endgame pre-cura) con i pass ATTIVI (HRF delivery: 6 target, 4 chiusi,
  9 mosse): non tradisce i banchi.
- Metodo: banco, poi porting, poi doppia gate (/code-review high + /simplify), poi
  re-cert, poi ratifica: il giro rodato di 619/620.
