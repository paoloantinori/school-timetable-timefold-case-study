# HANDOFF notte4-coda 31/07 (zai) - decisioni maintainer + coda A/B/C/D

Sessione post-/clear che ha eseguito le 2 decisioni di maintainer sul gate + la coda notturna
residua (A/B/C/D). HEAD `feature/cartesian-block-entity` pushato a `3cc98091`. Tutto read-only
+ design draft + rigenerazione artefatto: **NESSUNA implementazione, NESSUN flag flippato,
NESSUNA adozione** (la DoD /simplify+/code-review/test.sh non si applica: nessun codice).
App production ferma a NOT_SOLVING (run2 del traguardo combined hard-0).

## Commit (in ordine, tutti pushati)
1. `06bb8f54` docs(variante-b): archivio accolto - framing corretto (golden cieco all'aula)
2. `1a55f836` docs(bucket-c): misure golden 5 regole non modellate + referente T6/T7
3. `a88c23eb` docs(task468): design AC#2 - soglia-1 teoria, M15/S17/M21 risolti, set teoria + T8
4. `c090881f` docs(orario): rigenerato HTML per-gruppo sul traguardo combined-run2 hard-0
5. `3cc98091` docs(serie-storica): run del 31/07 al metro DailyActivityQuota + traguardo hard-0

## DECISIONE (1) - variante (b) ARCHIVIATA, framing corretto
- maintainer: accolto archivio variante (b) come A/B separato. **Correzione di framing**: NON e'
  "TASK-465 smentisce (b)". Il golden NON ha colonna aula (header `date,day,slot,closed,group,
  teacher,subject` verificato), quindi l'audit-inverse di 465 misura la co-presenza nelle CELLE
  tag, non in quale STANZA cadano le ore: e' cieco all'esilio fisico.
- Motivo vero: (a) 464-ON e (b) de-tag esteso sono EQUIVALENTI a livello orario (entrambe hard
  0), differiscono SOLO nella semantica-stanze che arbitra referente (Q20 + domanda rotazione).
- Resta: combined 463+464 candidato adozione MECCANISMO; scelta semantica aperta maintainer+referente;
  override G14/G12 nel seed invariato. Corretto in 3 doc: decisione, QUADRO notte4-seguito,
  handoff notte4-seguito.

## CODA (A) - misure golden bucket C (5 regole, verificate adversarial)
Script repo: `scripts/analyze/golden_bucket_c_measures.py`. Doc: `misure-golden-bucket-c-*.md`.
- (i) Sicurezza 1e 2gg cumulativi (C1/T5): VIOLATA (8h/8sett, non 2gg) -> notifica referente.
- (ii) T21 prime ore (C2/T3): VIOLATA (26/42h slot0 per G08) -> notifica referente.
- (iii) T11 6h post-stage (C3/T4): RISPETTATA su G10 (6h/2-consec post-stage, combacia
  "anche 2 consecutive"); G08 5h, G11 scope diverso -> mini-domanda referente.
- (iv) Termo lab prime 4h (B/T6): PARZIALE (75% slot0-3) -> chiarire referente (8-12 vs 8-13).
- (v) Stage G10 no-lunedi (C7/T7): RISPETTATA (0 lun, deliberato: lunedi attivo fuori-stage)
  -> quasi-gratis ma basso impatto (H25 gia' confina le date stage).
- referente doc arricchito con dato golden su T3/T4/T5 + nuovi T6/T7/T8. Matching-doc gap-table
  aggiornata. Verifica adversarial: confutato "soggetto sbagliato" per (i) (Igiene e sicurezza
  check) e "artefatto calendario" per (v) (lunedi fuori-stage attivo).

## CODA (C) + DECISIONE (2) - TASK-468 AC#2 design draft (design-only)
Doc: `task468-results/AC2-design-2026-07-31.md`. Task TASK-468 AC#2 spuntata.
- Forma: **soglia-1 teorica** (M21-style ONE_MEDIUM x (ore-1)), non penalita' coppia adiacente.
  Peso ordine M24/M25, knob default 1, debole per proposito.
- Interazioni M15/S17/M21 **risolte in composizione pulita**: M15 (medium 20/min) forza il repeat
  a blocco adiacente, S17 (+5 soft teoria-inclusa) resta invariata (tier diversi, no conflitto),
  M21 (soglia 2) convive. Netto fedele al golden (432 blocchi puliti 2h).
- Set teoria: LAB_SUBJECTS + pratico-tecnici esenti; **borderline Figurino/Personalizzazione/
  Tecnologia-e-creativita' = TEORIA di default** (predicato `isTheoryForRule`, flip una riga).
- T06 info+gest = hook alias (default inerte).
- **Mini-domanda referente T8** (borderline + alias) aggiunta al doc referente.
- NESSUNA implementazione: prototipo resta AC#3 dietro flag
  `planner.constraints.theory-adjacent.enabled` DEFAULT OFF, gate A/B.

## CODA (B) - orario HTML rigenerato sul traguardo hard-0
`claudedocs/spalmone-2026-27/orario/index.html` ora sul **combined-run2 (0hard/-994722medium,
hard 0 verificato)**, non piu' 30e. 14 pagine per-gruppo + index, **0 "SENZA AULA"** (vs 167
del 30d), Stage off-site grigio. Primo orario feasible, deliverable visivo per maintainer.
Sorgente: `task463-results/combined-run2/export.json.gz`. Linkato dal QUADRO (colonna 3
notte4-seguito + nota 30e/30d storica).

## CODA (D) - serie storica KPI aggiornata (metro DailyActivityQuota)
`serie-storica-kpi-2026-07-30.md`. combined-run1/2 (HARD 0 n=2) aggiunti alla linea di base
come nuovo ceiling (superano 447/30b/30d). Blocco "Run del 31/07" nel record completo (464
off/on1/on2, 451 run1/2, T19 baseline/test, btimeslot1 A/H/B, portfolio-n3), tutti misurati
con `daily-fill-kpi.py` + `analysis.json`. Contabilita' hard verificata.

## Cosa resta aperto (per la prossima sessione)
- **Risposte referente** sono il collo di bottiglia: doc pronto
  `domande-rotazione-e-tensioni-referente-2026-07-31.md` (rotazione, Q19, Q20, T1-T8) da
  impacchettare con `domande-brevi.md` + `referente-notifica-divergenze-*-2026-07-31.md` e inviare.
- **Adozione combined 463+464** = maintainer (in attesa Q20 referente sulla semantita' stanza). Flag
  463/464 restano DEFAULT OFF.
- **TASK-468 prototipo** (AC#3) dietro flag OFF + A/B (AC#4): il design AC#2 e' pronto, quando
  si attacca seguire lo specifica del design doc.
- **Backlog**: collisione ID TASK-38/230 blocca i comandi ID-based del backlog MCP (preesistente,
  non mio; `backlog doctor` per il repair).

## Come riprendere
- QUADRO consolidato: `QUADRO-2026-07-30.md` (sezione NOTTE4-seguito in cima + colonna 3).
- Handoff notte4 base: `handoff-2026-07-31-notte4-combined-hard0.md`; seguito:
  `handoff-2026-07-31-notte4-seguito.md`; questa coda: questo doc.
- App production ferma. Per run nuovi: stop+delete production, restart con `PLANNER_JAVA_OPTS`
  (GOTCHA drop-and-create: ogni restart raw `java -jar` wipea il DB).
