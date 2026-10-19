# TASK-601 misura pre-build: la concentrazione-coppie e' GIA' sopra il golden

Misura fatta il 12/09 mattina, DOPO la ratifica TASK-599 (spread pass-0 DEFAULT ON),
PRIMA di scrivere una riga del prototipo CH teacher-centric. Scopo: la direttiva
anti-inerzia ("questa misura cambia una decisione di oggi?") applicata al task prima
di pagarlo.

## Metodo

Stesso metro del banco TeacherPairMajorBenchTest: celle (docente, gruppo, weekday,
slot-orario) dal dump di costruzione reale (target/construction-lessons.txt del run
con il NUOVO default, suite 1304/0), coppie >=20h, concentrazione top-3 = ore nelle
3 celle piu' piene / totale coppia, mediana sulle coppie grandi. Golden: identico
metro su lessons.csv (righe open, Stage/Fasullo esclusi).

## Numeri

| braccio | mediana top-3 | costo H34 costruzione |
|---|---|---|
| golden (pratica della scuola) | **0,80** (169 coppie) | - |
| pair-major OFF (pre-spread, 9/09) | 0,83 | 41 sotto-quota |
| pair-major ON (9/09, bocciato) | 0,85 | 59 sotto-quota (+18) |
| **stack attuale (spread ratificato)** | **0,82** (165 coppie) | **39 sotto-quota (migliore)** |

Top-5 attuale: T02/G01 65h 1.00, T07/G01 1.00, T23/G01
1.00, T19/G02 1.00, T11/G02 1.00.

## Lettura

La costruzione attuale produce GIA' la forma-per-coppia SOPRA la pratica del golden
(0,82 > 0,80) SENZA il costo H34 che bocciava il pair-major (39 sotto-quota, il
migliore mai misurato). Il criterio di successo pre-registrato del prototipo era
">=0,85": i +3pp restanti non chiudono nessun residuo misurato (la consegna corrente
e' [-11/0] = H34 coda 10 tutte import teacher-locked + H7 1 artefatto di bacino:
nessuna componente di shape/concentrazione).

## Verdetto

PREMESSA ASSORBITA dalla ratifica TASK-599: il pass-0 spread (la regola del
pianificatore umano, "il corso preferisce il weekday dove il docente ha meno ore")
era il pezzo mancante della forma-coppia, non la selezione teacher-centric completa.
Il prototipo CH non ha un residuo da chiudere: costruirlo sarebbe lavoro senza una
decisione che cambia. TASK-601 chiuso con verdetto KILLED-BY-EVIDENCE (pre-build).

## Revival

Riaprire SOLO se emerge un residuo con componente shape (es. ricorrenza che crolla
sotto 70% o una famiglia H* localizzata su coppie multi-gruppo): i checkpoint
spread-11sep sul laptop restano l'input. La matrice rosso/verde dichiarata (TASK-602)
quando arriva si consuma direttamente nel pass-0 esistente (il consumatore giusto),
non serve una CH nuova per leggerla.
