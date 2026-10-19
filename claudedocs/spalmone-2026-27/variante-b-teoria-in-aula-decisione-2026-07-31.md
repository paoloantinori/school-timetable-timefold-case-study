# Variante (b) "rotazione + teoria in aula" - ARCHIVIATA come solve separato (zai, 2026-07-31 notte4-seguito)

Task 9 del programma notturno: "misura variante (b) rotazione + teoria in aula (full-solve),
perimetro ospiti = solo G14". **Esito: non si lancia il solve, e la variante (b) come A/B
separato si ARCHIVIA.** Decisione di maintainer (notte4-seguito): accolto l'archivio. Qui sotto il
framing corretto del perche', con l'evidenza verificata e lo stato di cio' che resta aperto.

## CORREZIONE DI FRAMING (perche' non si dice "TASK-465 smentisce (b)")

La versione precedente di questo doc motivava l'archivio dicendo che TASK-465
"smentisce" la variante (b) perche' il suo audit-inverse non trova materie esiliate fuori
dalle celle di co-presenza golden, e quindi "la scuola fa queste ore nel laboratorio, non in
aula". **Questo ragionamento e' sbagliato e va ritirato.**

L'artefatto lo dimostra: l'header del golden (`src/main/resources/seed/lessons.csv`,
verificato) e'
```
date,day,slot,closed,group,teacher,subject
```
**Il golden non ha colonna aula.** L'audit-inverse di TASK-465 misura la co-presenza delle
materie nelle CELLE `(tag, weekday, slot)`, e da li' deriva quale materia e' "lab-tagged e
co-locata". Ma una cella di co-presenza non dice in quale STANZA quelle ore cadano fisicamente:
non distingue "5 classi nello stesso laboratorio" da "alcune di quelle classi in aula normale".
Lo dice anche il VERDICT dello spike room-null: *"golden senza room -> non distingue '5 classi
in 1 lab' da 'alcune in aula normale' -> modellazione gated a referente"*.

Quindi TASK-465 **non smentisce e non conferma** la variante (b): e' semplicemente SILENTE
sull'esilio fisico in aula, perche' il golden non registra l'aula. Dire "il golden colloca
queste ore nel laboratorio, contra l'evidenza golden-first" era un'affermazione che il golden
non puo' sostenere. Va rimossa.

## Il motivo VERO dell'archivio (equivalenza oraria, differenza solo semantica)

La variante (b) e la variante (a) non si distinguono a livello di ORARIO:

- **(a) 464-ON generalized** (il traguardo combined 463+464): hard 0, room-null 0, 18 buchi H17
  chiusi dal coordinatore 463. Gia' misurato (A/B TASK-464, n=2).
- **(b) 464-OFF + de-tag esteso ai tripletti FA** (la forma letterale "rotazione + de-tag
  teoria per i tripletti FA"): raggiunge anch'essa hard 0 a livello di ORE collocate per
  gruppo e per docente (lo stesso export orario). La differenza fra (a) e (b) non e' oraria:
  e' **in quale stanza fanno fisicamente le ore teoriche** dei tripletti FA.

Cio' che separa (a) e (b) e' una **semantica di stanza**, e quella e' una domanda fisica sulla
pratica della scuola che solo referente puo' arbitrare: le 12h parallele di T26 con G12 del
giovedi' (Q20) e, in generale, la rotazione acconciatura (domanda 1 del doc referente). Nessun
solve la risolve: un solve misura un orario, non dove la scuola mette fisicamente i banchi.

**Conseguenza:**
1. **La variante (b) come A/B separato e' archiviata.** Non misura nulla di nuovo rispetto al
   traguardo: a livello orario e' equivalente a (a), e a livello di stanza e' una domanda per
   referente, non per il solver.
2. **Il traguardo combined 463+464 resta il candidato di adozione del MECCANISMO**
   (co-presenza tag + coordinatore cross-gruppo). Il meccanismo e' neutro sulla scelta di
   semantica-stanze: funziona sia che G12/G14 stiano nel laboratorio come ospiti (caso
   "aula = lab", che (a) gia' copre), sia che vadano in aula normale.
3. **La scelta di semantica-stanze resta APERTA, da chiudere maintainer + referente** via Q20 e la
   domanda sulla rotazione. Non blocca l'adozione del meccanismo.
4. **La componente stretta di (b) (override G14/G12 a trattamento teoria) resta nel seed
   cosi' com'e'**, perche' e' gia' li' ed e' neutra sul punteggio (vedi sotto).

## Lo stato ATTUALE del codice/seed (verificato, invariato)

1. **Il de-tag G14 + G12 e' GIA' IN PRODUZIONE e resta.** `seed/subject_overrides.csv`:
   ```
   G14,Servizio stilistico,NONE,
   G12,Servizio stilistico,NONE,
   ```
   `SubjectRegistry` (riga 32-33) lo documenta come "TASK-455 v1 (2026-07-30)". Servizio
   stilistico per quei due gruppi e' isLab=false, nessun requiredRoomTags, M-LF la spinge in
   aula normale. Questa e' la componente "ospiti G14/G12 a trattamento teoria" della
   variante (b), ed e' **gia' attiva nel HEAD corrente**, invariata dalla decisione.
2. **Il flag 464 (H-COPRESENZA) e' DEFAULT OFF** (`planner.co-presenza.enabled`, opt-in), come
   tutti i flag di questa campagna.
3. **Il traguardo combined 463+464 (hard 0 n=2)** ha girato con 464 ON sul seed attuale (quindi
   col de-tag G14+G12 gia' attivo): hard 0, room-null 0, 18 buchi H17 chiusi dal
   coordinatore 463.

## L'unica domanda residua davvero aperta (non da solve, da referente)

Q20 (doc `domande-rotazione-e-tensioni-referente-2026-07-31.md`): le 12h parallele di T26 con
G12 del giovedi' sono in **aula normale**? La risposta decide solo la SEMANTICA di stanza
di G12, non l'adozione del meccanismo:
- Se referente conferma "aula normale", la quota teoria di G12 resta de-taggata e mandata in
  aula da M-LF (invariata, come oggi).
- Se referente dice "nel laboratorio come G14", allora G12 rientra nel perimetro ospiti e
  serve l'esenzione guest (caso coperto dal flag 464).

In entrambi i casi l'orario (le ore collocate) non cambia: cambia solo quale stanza dichiariamo
per quelle ore. Per questo la variante (b) come A/B separato e' moot.

## Esperimento residuo - GIA' MISURATO (TASK-464 A/B)

Il "quanto 464 compra" e' gia' misurato: il TASK-464 A/B (QUADRO sezione SERATA 31/07,
`task464-results/VERDICT-ab-2026-07-31.md`) ha confrontato 464 OFF vs ON sullo stesso seed:
**OFF -112hard (H29 room-null -94 + H17 -18) -> ON -18/-17hard (0 room-null), delta +94/+95**,
n=2 concordi, nessuna regressione KPI. Quindi NON serve re-misurarlo. Gli elementi per il
verdetto di adozione del traguardo combined ci sono tutti: hard 0 n=2 (traguardo) vs -112 hard
(baseline 464-OFF), nessuna regressione KPI.

## Riepilogo della decisione (maintainer, notte4-seguito)
- **ACCOLTO**: archivio della variante (b) come A/B separato. Framing corretto: NON perche'
  TASK-465 la smentisca (il golden e' cieco all'aula), ma perche' (a) e (b) sono equivalenti a
  livello orario e differiscono solo nella semantica-stanze che arbitra referente.
- **RESTA**: il traguardo combined 463+464 come candidato di adozione del MECCANISMO; la scelta
  di semantica-stanze aperta (maintainer + referente, via Q20 + domanda rotazione); l'override
  G14/G12 nel seed invariato.
