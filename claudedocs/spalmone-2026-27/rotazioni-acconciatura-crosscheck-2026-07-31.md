# Rotazioni acconciatura - cross-check numerico G12 / G14 (zai, 2026-07-31 notte)

Cross-check meccanico richiesto da maintainer (31/07 sera parte 4: "rifai il conto, e' un cross-check")
sul CSV golden `src/main/resources/seed/lessons.csv` (12979 lezioni, formato
`date,day,slot,closed,group,teacher,subject`). Script: `/tmp/acconciatura_crosscheck.py`.
Subject acconciatura considerati: Barberia, Servizio stilistico, Servizio tecnico e stilistico,
Lavaggio. "Overlap" = stesso (date, slot) con una lezione acconciatura di un ALTRO gruppo.

## VERDETTO: i numeri di maintainer sono confermati ESATTAMENTE

| gruppo | teacher | subject (day) | tot h | esclusive | overlap | vs chi |
|---|---|---|---:|---:|---:|---|
| G12 | T01 | Servizio tecnico e stilistico (Giovedi') | 46 | **46** | 0 | - |
| G12 | T19 | Barberia (Lunedi') | 45 | **45** | 0 | - |
| G12 | T26 | Servizio stilistico (17 Lun + 13 Gio) | 30 | **18** (17 lun + 1 gio) | **12** (tutte gio) | G06 5h, G02 5h, G10 2h |
| **G12 totale** | | | **121** | **109 (90%)** | **12 (10%)** | |
| G14 | T26 | Servizio stilistico (Giovedi') | 30 | **0** | **30 (100%)** | G06 25h, G02 5h |

- **G12 = titolare esclusivo al 90%** (109/121h esclusive). L'etichetta "ospite" veniva solo
  dall'attribuzione dell'eccesso tag-capacity (M-TC), non dalla pratica golden. Le uniche 12h in
  overlap sono tutte giovedi', T26/Servizio stilistico, mentre T01 tiene la titolare FA
  di turno (5h vs G02, 5h vs G06, 2h vs G10): sessione parallela, probabile aula normale.
- **G14 = ospite STRUTTURALE** (30/30h in overlap, sempre con le titolari FA di Servizio
  stilistico). E' l'unico ospite-nel-lab generalizzato della pratica golden.

## Conseguenza (gia' nel mandato parte 4)
- Estrazione rotazioni (task 8): classifica G14 = ospite strutturale, G12 = titolare con 12h
  sessione parallela T26 (aula normale).
- Variante (b) (task 9): perimetro ospite-nel-lab si restringe a G14 solo; le 12h di G12 vanno
  a trattamento aula-normale come le co-programmazioni FA.
- Doc referente (task 10): Q20 riformulata "le 12 ore parallele di T26 con G12 il giovedi' si
  tengono in aula normale, giusto?".

## Nota per l'estrazione completa (task 8, prosieguo)
T26 e' il docente che tiene Servizio stilistico per G12 (30h), G14 (30h ospite) e le
titolari FA: e' il fulcro del "depth del giovedi'" che il doc modello-rotazione legge come telaio
di rotazione (piu' docenti in parallelo, ciascuno col suo gruppo di periodo), NON co-presenza
generalizzata. L'estrazione completa (timeline gruppi per (docente, weekday, slot) + confini di
periodo + verifica struttura-vs-arbitrio) resta da fare; questa tabella ne e' la base numerica
validata.
