# TASK-468 AC#1 - Residuo golden "no 2 ore vicine stessa materia teorica" (zai, 2026-07-31 notte)

Mandato: TASK-468 AC#1 (read-only) - misurare sul golden quante volte la pratica della
scuola viola il vincolo del tab "Vincoli DA ANALIZZARE": *"Non ci devono essere 2 ore
vicine della stessa materia teorica; i laboratori (compreso disegno o informatica) possono
avere piu' ore vicine"*. La misura calibra peso e forma del vincolo M (decisione maintainer:
medium a residuo calibrato golden, pattern M24/M25).

Script riproducibile: `scripts/analyze/theory_adjacent_residual.py`.
Output raw: `task468-results/residuo-golden.txt`.

## Metodo
- Golden: `src/main/resources/seed/lessons.csv` (12.979 lezioni).
- Classificazione lab/theory **autorevole dal DB** (`course.islab`), con l'eccezione che
  `Servizio stilistico` e' lab per 1/2/G10 ma THEORY per G12/G14 (gia' cosi' nel
  seed: e' il perimetro variante b).
- Filtri trappole-golden (CLAUDE.md): esclusi sabati (off-grid), docente `Fasullo`/`Fasutto`
  (segnaposto 19/09) e subject `Stage` (off-grid). Residuo: **10.542 lezioni** (combacia col
  "10.542" di CLAUDE.md, cross-check della pipeline).
- Due definizioni di "teoria":
  - **LARGA** = non-lab (`islab=false`).
  - **STRETTA** = non-lab e non pratico-tecnico. Il tab esente esplicitamente "disegno o
    informatica"; nel seed Disegno/Disegno Edile/Progettazione/Elettrotecnica/RA sono
    `islab=false` ma sono pratici-tecnici -> esclusi come i lab. (Confine esatto = decisione
    di design AC#2.)
- Due forme candidate (description TASK-468):
  - **threshold-1** (M21-style soglia 1): giorni con 2+ ore stessa materia teorica.
  - **coppia adiacente**: coppie same-subject same-day a slot adiacenti (scarto 1h).

## Risultati (verificati indipendentemente: match esatto)

| definizione | giorni-2+ (threshold-1) | coppie adiacenti | coppie/giorni |
|---|---:|---:|---:|
| LARGA (non-lab) | 719 | 814 | 1.13 |
| **STRETTA (non-lab, no pratico-tecnici)** | **432** | **441** | **1.02** |

Top driver (STRETTA, aggregato gruppi): Scienze 86g/66c, Matematica 63/53, Recuperi ed
Approfondimenti 33/75, Tecnologia e creativita' 24/48, Competenze Digitali 24/24, Lingua
Italiana 31/15, Approfondimenti tecnica operativa 19/25, Lingua Inglese 19/18.

## Lettura per il design (AC#2)

1. **Conferma la decisione maintainer (MEDIUM calibrato, NON hard).** Anche alla definizione
   piu' stretta (il bersaglio reale della regola: lettere/scienze/cultura) il golden viola
   432 volte. Un hard vieterebbe il golden -> regola golden-first violata, deve essere
   medium a residuo calibrato (come M24 -16 / M25 -27).
2. **Le violazioni sono quasi tutte blocchi puliti di 2h adiacenti** (coppie/giorni = 1.02
   nella stretta: 441 coppie su 432 giorni). Cioe' il golden non fa "3h di fila di Scienze",
   fa blocchi di 2h. Quei blocchi **sono voluti**: sono la pressione S17 (+5/coppia adiacente,
   teoria inclusa) e M15 (consecutivita' same-subject). Il vincolo M candidato entra in
   tensione DIRETTA con S17/M15/M21: la sua forma effetto-reale e' "non ripetere la materia
   teorica nello stesso giorno" (soglia-1), non "separa le 2 ore".
3. **Forma candidata pulita = soglia-1 teorica** (M21-style ma a soglia 1 sulle materie
   teoriche), non penalita' sulla coppia adiacente: a parita' di residuo (~432), la soglia-1
   e' piu' leggibile e si compone meglio con M15/S17 (che restano a spingere il blocco quando
   la materia E' ripetuta). Peso calibrato sul residuo (~432 unita' su 10.542 lezioni = ~4%):
   stesso ordine di M24/M25 (ONE_MEDIUM x unita'), non ONE_HARD.
4. **Whitelist riutilizzabile**: l'esenzione "compreso disegno o informatica" mappa sui nostri
   `LAB_SUBJECTS` + il set pratico-tecnico (`Disegno`, `Disegno Edile ed Impiantistico`,
   `Progettazione Termoidraulica`, `Elettrotecnica`, `Metrologia`, `Tecnologia Meccanica`,
   `Taglio Metalli`, `RA`). Confine esatto di questo set e' una domanda AC#2 (Figurino,
   Personalizzazione, Tecnologia e creativita' sono borderline: nel golden fanno blocchi di 2h).
5. **Discrepanza da chiarire con referente**: la nota di T06 nello stesso tab ("non 1 ora
   info e 1 ora gest ma stessa materia") chiede l'OPPOSTO per alcuni casi (info+gest = stessa
   materia). Va nel doc referente (TASK-466c) come domanda, non blocca il prototipo.

## Nota tecnica
Durante la misura: correzione di un mio typo di trascrizione (`Laboratorio Termoidraulica` ->
`Laboratorio Termoidraulico`: il DB e il golden usano entrambi il maschile "...ico"). Non e'
un drift golden-vs-DB, era solo il set hardcoded nello script. I 12 subject lab autorevoli
(estratti dal DB): Barberia, Cad, Confezione, Controllo numerico, Informatica, Laboratorio
Termoidraulico, Lavaggio, Lavorazioni meccaniche, Modello (Moulage), Servizio stilistico,
Servizio tecnico e stilistico, Tecniche di saldatura.
