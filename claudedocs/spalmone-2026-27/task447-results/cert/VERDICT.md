# FASE E (TASK-447) - Certificazione finale staged (post FASE A/B/C)

Data: 2026-07-29. Staged solve completo su profilo production fresco, flag
constrained-teacher ON via PLANNER_JAVA_OPTS (default codice ancora OFF =
adozione maintainer), pre-assign 3090 pinned (1993 stage + 866 MODA + 231 Phase-0),
JAR con FASE A (H29/H0) + FASE B (knob M37) + FASE C (de-tag 2 teoria).
Capture canonica qui. Durata solve ~48min (B-TIMESLOT-1..3 -> RELEASE -> repairs
-> B-ROOM). Nota operativa: il cert script ha abortito su un blip transiente del
`api.sh wait` a B-ROOM, ma il solve era completato (stagedStage cleared); capture
eseguita a posteriori sulla solve finale.

## Risultato vs gate-finale / gate-finale-run2

| metrica | gate-finale | run2 | **cert (FASE A/B/C)** |
|---|---|---|---|
| hard | -5 (tutti H0) | -4 | **-195** = H0(boxed 3 x2=6) + H29(room-null 189 x1) |
| room-null reali non-pinnate | 254 (251L / 3 nl) | 270 (262L / 8 nl) | **189** (182L / ~7 nl) |
| M37 cambi d'aula | 124 | 102 | **176** |
| full-day | 80,9% | 80,8% | **80,8%** |
| day-start | 99,2% | 99,3% | **99,3%** |
| ricorrenza unpinned | 74,2% | 74,6% | **74,4%** |
| buchi interni | 0 | 0 | **0** |

## Lettura

1. **FASE A VALIDATA (anti-arbitraggio + nuova semantica hard).** hard = -195 =
   H0(boxed 3 x peso 2 = 6) + H29(room-null 189 x peso 1 = 189). Esattamente come
   progettato: de-assegnare (-2) resta strettamente peggiore di room-null (-1), e
   la semantica hard e' ora "boxed*2 + room-null*1" (non piu' "boxed-count").
   Boxed = 3 (pari ai 4-5 del gate-finale, rumore). NESSUNA regressione: il solver
   non ha svuotato l'orario (l'arbitraggio e' chiuso).

2. **FASE C VALIDATA (de-tag 2 teorie).** room-null 254 -> 189 (chiude **65 lab**,
   in linea con la predizione in-harness ~72; il diff e' varianza di traiettoria +
   il confound isLab-cached dell'in-harness). "progettazione termoidraulica" e
   "tecnologia tessile" NON compaiono piu' nei room-null (de-tag funzionato, ora aula).

3. **KPI in PARITA' col gate-finale.** full-day 80,8 (vs 80,9), day-start 99,3 (vs
   99,2), ricorrenza 74,4 (vs 74,2), buchi 0. Il pacchetto 435+446+M37+H28 + FASE
   A(H29/H0)+FASE C(de-tag) NON peggiora nessuna soglia di campagna.

4. **G11 venerdi PERSISTE (15 ritardi, golden 0).** Confermato da FASE D: non
   riparabile (teacher-busy 396 + lab-quota VRP 111), serve la direzione (c) ancora
   nel template di costruzione (SPIKE 3, prototipo a focus fresco).

5. **M37 = 176 (watch-item, non regressione).** Piu' alto di gate-finale 124 / run2
   102. Ipotesi: FASE A (H29 hard forza l'assegnazione di QUALUNQUE stanza) + FASE C
   (piu' lezioni aula -> piu' flessibilita' di stanza) lasciano alla LS room piu'
   liberta' di spostare a meta' blocco. Costo minore: 176 x 100 = 17.600 medium
   (~1,8% del medium totale -952.724), H29 hard domina. Da tenere d'occhio se M37
   venisse alzato (FASE B: ma alzare M37 non riduce il conteggio, e' forzato dalla
   disponibilita' di stanza).

## Collo di bottiglia room-null (189) per materia -> per la scuola

| materia | room-null | tag |
|---|---|---|
| Lavaggio | 75 | acconciatura |
| Servizio stilistico | 53 | acconciatura |
| Laboratorio Termoidraulico | 24 | termoidraulica |
| Barberia | 11 | acconciatura |
| Taglio Metalli | 10 | meccanica |
| Lavorazioni meccaniche | 9 | meccanica |
| (aula: Matematica/Inglese/Educ.Civica/Scienze/Sicurezza/Approond.) | ~1 ciascuno | aula |

I lab **acconciatura** dominano (Lavaggio + Servizio stilistico + Barberia = 139/189).
Domanda concreta per la scuola: Servizio stilistico = 53 ore senza stanza (de-tag
ne chiuderebbe ~85 per SPIKE 4) -> e' pratica-su-cliente (serve il lab
acconciatura, capacity-starved) o aula? Lavaggio 75 e' genuinamente lab
acconciatura (capacity-starved, non de-taggabile).

## Note per l'adozione (maintainer)

- **Flag constrained-teacher default ancora OFF** nel codice; nel cert e' stato ON
  via -D. Il flip del default e' adozione tua (Fedora doveva metterlo, non arrivato).
- **getFeasibility** ora riporta room-null come hard-infeasible (etichetta, non
  verdetto: era gia' infeasibile via medium prima). Vedi FASE A VERDICT.
- M37=176 e' un watch-item, non bloccante.
