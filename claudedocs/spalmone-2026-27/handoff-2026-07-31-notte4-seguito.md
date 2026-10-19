# HANDOFF notte4-seguito 31/07 (zai) - rotazioni + referente + TASK-468 + variante (b) spike

Seguito del programma notturno di maintainer (5 voci). HEAD `feature/cartesian-block-entity`
pushato a `532529c4`. App production ferma a NOT_SOLVING (run2 del traguardo combined).
solverConfig canonico (tabu7). Tutti i flag (451/463/464) DEFAULT OFF nei default di produzione
(451 e' default ON solo nella build adottata, vedi CLAUDE.md RATIFICA TASK-451; 463/464 OFF).

## Cosa e' fatto (tutto read-only + spike, verificato)

1. **Task 8 estrazione rotazioni (SYNC TASK-467)** - `task467-results/estrazione-rotazioni-2026-07-31.md`
   - 4 docenti acc: T01 (Serv.tecnico 231h SOLO Gio, mono-day verificato 231/231),
     T26 (Serv.stilistico 200h), T18 (Lavaggio 160h), T19 (Barberia 128h).
   - Rotazione verificata: blocco Gio mattina di T01 ruota 2->1->3->2->1->G12->G10 in
     periodi ~mensili. Coerenza giorni-L: G06 100%, G02 97%, G10 100%, G14 100%, G12 75%.
   - Separazione struttura (regolarita'/eredita'/mono-day/coerenza-L) vs arbitrio (quale gruppo
     in quale periodo, date, slot surplus). G14 ospite strutturale, G12 titolare 90%.
   - Script riproducibile: `scripts/analyze/acconciatura_rotation_extract.py`. Raw: `task467-results/rotazione-estrazione-grezza.txt`.

2. **TASK-468 AC#1 residuo golden "no 2 ore vicine teoria"** - `task468-results/AC1-residuo-golden-2026-07-31.md`
   - Teoria LARGA 719g/814coppie; **STRETTA 432g/441coppie** (coppie/giorni=1.02 = blocchi 2h voluti).
   - Conferma MEDIUM calibrato (golden violato 432x -> hard vieterebbe golden). Forma candidata
     = soglia-1 teorica, peso ordine M24/M25. Verificato indipendentemente (10542/432/441).
   - Script: `scripts/analyze/theory_adjacent_residual.py`. Backlog TASK-468 AC#1 spuntata + commento.

3. **Task 10 doc referente** - `domande-rotazione-e-tensioni-referente-2026-07-31.md`
   - Domanda rotazione affilata (prassi strutturale o scelta annuale?), Q19/Q20 riformulate con
     la rotazione (G14 ospite; G12 titolare 90% + 12h T26 gio in aula normale?),
     tensioni bucket C -> T1-T5 (M29 discrepanza, T20 serializzazione, T21, T11, Sicurezza).

4. **Task 12 matching vincoli-tabs finale** - `vincoli-tabs-matching-2026-07-31.md` (sezione VERDETTO FINALE)
   - Bucket A CONFIRMATO vs availability_rules.csv (nuance: T05 mar-pom DROPPATO, "8h T28" =
     stantio per T01, Q22 golden-derived da confermare). Bucket C risolto: C4 MISURATO,
     C6/C1/C2/C3/C5 -> domande referente T1-T5. Gap table finale.

5. **Quadro** aggiornato in cima: `QUADRO-2026-07-30.md` (sezione NOTTE4-seguito).

## Variante (b) - RISOLTA (archivio accolto, framing corretto)

**Task 9 variante (b) full-solve: ARCHIVIATA come A/B separato.** Decisione maintainer notte4-seguito
(ACCOLTO). Doc con framing corretto: `variante-b-teoria-in-aula-decisione-2026-07-31.md`.
- **Correzione di framing (importante).** La prima stesura diceva che TASK-465 "smentisce" (b)
  perche' l'audit-inverse non trova materie esiliate. Sbagliato: il golden NON ha colonna aula
  (header `date,day,slot,closed,group,teacher,subject` verificato), quindi l'audit di 465 misura
  la co-presenza nelle CELLE tag, non in quale STANZA cadono le ore. E' cieco all'esilio fisico:
  non puo' smentire ne' confermare (b).
- **Motivo vero dell'archivio.** (a) 464-ON e (b) de-tag esteso sono EQUIVALENTI a livello orario
  (entrambe hard 0, stesso export di ore per gruppo/docente); differiscono SOLO nella
  semantica-stanze che arbitra referente (Q20 + domanda rotazione). Nessun solve la risolve.
- La componente stretta G14+G12 (override a trattamento teoria) e' GIA' nel seed
  (`seed/subject_overrides.csv`, SubjectRegistry:32-33) e RESTA invariata (neutra sul punteggio).
- Il traguardo combined 463+464 resta candidato di adozione del MECCANISMO; scelta semantica
  aperta maintainer+referente. Il "quanto 464 compra" e' GIA' misurato (TASK-464 A/B: +94/+95 hard,
  94 room-null chiusi) - non serve re-misurare.

## Incidente rm-rf (registrato)
Aggiunta riga a `tasks/lessons.md`: "ispezionare SEMPRE prima di rm -rf, anche una dir che sembra
spazzatura". Le controparti giuste in `claudedocs/` sono intatte, danno accettato.

## Come riprendere
- **Variante (b) CHIUSA** (archivio accolto, framing corretto nel doc e nel QUADRO). Il
  traguardo combined 463+464 e' il candidato di adozione del meccanismo; scelta semantica-stanze
  aperta maintainer+referente via Q20 + domanda rotazione. Se maintainer volesse comunque un run "464 OFF" di
  conferma, e' zero codice (stop+delete production + `PLANNER_JAVA_OPTS` senza flag 464/463 +
  solve-staged), ma non serve: il "quanto 464 compra" e' gia' misurato (TASK-464 A/B +94/+95).
- **TASK-468 AC#2** (design forma+whitelist): la misura AC#1 indica soglia-1 teorica; il confine
  "teoria" (Figurino/Personalizzazione borderline) e la nota T06 vanno chiuse in design +
  domanda referente.
- **Doc referente** pronto da impacchettare con `domande-brevi.md` e `referente-notifica-divergenze-*-2026-07-31.md`.
- HEAD `532529c4`, tutto pushato. App production ferma. Per run nuovi: stop+delete production,
  restart con `PLANNER_JAVA_OPTS` opportuno (GOTCHA drop-and-create: ogni restart raw `java -jar`
  wipea il DB).

## Numeri chiave della notte (verificati)
- Rotazione T01: 231/231h su Gio (mono-day). Blocco mattina ruota 7 gruppi in ~7 periodi.
- Coerenza giorni-L: 100/97/100/100/75% (G02 / G06 / G10 / G14 hanno 1-6h off-L = teoria).
- TASK-468 residuo: 432 giorni / 441 coppie (teoria stretta), 719/814 (larga).
- Variante (b): ARCHIVIATA come A/B separato. (a) e (b) equivalenti a livello orario (entrambe
  hard 0), differiscono solo nella semantica-stanze che arbitra referente; il golden e' cieco
  all'aula, quindi TASK-465 non la smentisce (framing corretto nel doc decisione).
