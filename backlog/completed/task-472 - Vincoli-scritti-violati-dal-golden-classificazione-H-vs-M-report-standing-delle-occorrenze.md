---
id: TASK-472
title: >-
  Vincoli scritti violati dal golden - classificazione H-vs-M + report standing
  delle occorrenze
status: Done
assignee: []
created_date: '2026-08-01 05:30'
updated_date: '2026-08-14 10:25'
labels:
  - claude-planned
dependencies: []
ordinal: 295000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Chiave di lettura di maintainer (01/08 mattina, direttiva registrata anche in memoria): per ogni vincolo SCRITTO (docx TASK-368/369/370, tab Vincoli del golden xlsx) che risulta VIOLATO dal golden stesso:

1. Intuire se si tratta di un constraint H (vincolo vero) o M (sarebbe meglio se).
2. Se e' quasi sempre rispettato ma non sempre, puo' essere un H che lo human planner non e' riuscito a risolvere, oppure un M ad alto valore: distinguere con l'analisi delle occorrenze (tasso, clustering temporale/di-gruppo, correlazione con scarsita').
3. Dobbiamo SEMPRE poter produrre un report che evidenzia tutte le occorrenze che non rispettano la regola e, dove possibile, una spiegazione del perche' non si riescono a risolvere (es. capacita' esaurita in quella finestra, conflitto con altro vincolo, periodo modulo intensivo).

Obiettivo ultimo invariato: 0-hard. Nota anti-overfitting di maintainer: le euristiche di costruzione inferite dal golden restano legittime come EURISTICHE CANDIDATE per ridurre lo spazio di ricerca, non overfitting.

Perimetro iniziale (dalle misure bucket B/C gia' fatte, docs misure-golden-bucket-{b,c}-2026-08-01.md e tensioni T1-T10 del doc referente): T16 3h-non-6 (4 lunedi' 6h + 6x5h), termo prime-4-ore (75%), Sicurezza 1e in 2 giorni, T21 mid-day, M29 discrepanza mer+gio vs gio+ven, M24/M25 residui calibrati, no-2h-teoria (gia' TASK-468: 432/440 vs 1694, riusare come caso pilota della classificazione).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Per ogni regola del perimetro: proposta di tier (H vero / M alto valore / M debole) motivata da tasso e clustering delle violazioni golden
- [x] #2 Script ripetibile in scripts/analyze/ che produce il report occorrenze per regola (input: golden CSV o lessons.txt di un run), non doc one-off
- [ ] #3 Per ogni occorrenza violata: spiegazione tentata o 'inspiegata' esplicito
- [ ] #4 Aggiornamento doc referente: le tensioni riformulate secondo la classificazione
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
VERDICT-472 (2026-08-03, doc-only classification): perimetro classificato. Regole codificate (M-TR, M24, M25, M26, M29) tutte MEDIUM, confermate dal tasso violazione golden (golden-first: chi viola il golden e' medium, nessuna promozione H). M26 zero violazioni golden (candidata promo H stile H28, decisone maintainer). Osservazioni non-codificate (T16 3h-non-6, termo prime-4-ore, T21 mid-day) NON sono vincoli H/M: pattern carico (entro H7) o preferenze docente -> INPUT/PREFERENCE, non layer constraint. AC#2 script (golden-constraint-violations.py occurrence-report) e' il follow-up (sintesi ora dai dati autoritativi esistenti). AC#3/#4 parziali (spiegazioni e doc referente da fare con lo script). Doc: task472-results/VERDICT-472-2026-08-03.md.

--- AC#2 chiuso (2026-08-04) ---

Script scripts/analyze/golden-constraint-classification.py: DONE + DoD completa. Standalone (golden CSV, no DB). Classifica meccanicamente il perimetro: M26 Sicurezza pre-stage misurato fresco (6 gruppi golden-practice, 0 violazioni -> HARD-candidate stile H28, decisione maintainer); osservazioni T16/termo/T21 caratterizzate come workload/pattern (route INPUT/PREFERENCE); regole codificate M-TR/M24/M25 citano i numeri solver-computed golden (440/16/27 -> MEDIUM); M29 e' rationale di demozione (tier esplicita, non conteggio). Verdetto: nessuna regola codificata promuove a Hard (golden-first), consistent con VERDICT-472.

DoD: /simplify (4 agent, applicati typo T21 + import a modulo; skippati swap shared.parse_lessons_csv e drop n_with_stage con motivo); /code-review high (3 finder correctness + 1 verificatore Opus + verifica indipendente orchestratore) -> 3 honesty fix confermati e applicati (M26 scope proxy vs coded StagePattern dichiarato; 'Consistent with' non 'Confirms'; M29 niente 0 ingannevole -> n/a + tier esplicita); test.sh N/A (script Python, suite Java non lo copre) ma EXIT 0 sullo script verificato.

Conferma meccanica vs VERDICT-472: match su tutte le 8 regole del perimetro. M26 javadoc (BaseConstraintProvider ~L868) documenta verbatim che il coded M26 esercita solo G07 (1 gruppo), lo script misura la pratica golden piu' ampia (6 gruppi) - scope mismatch reale, ora dichiarato nello script.

AC#1 coperto (tier proposti per ogni regola, motivati dal tasso violazione golden - il clustering e' territorio AC#3). AC#3 (spiegazione per-occorrenza) e AC#4 (doc referente) restano PARZIALI/DEFERRED: richiedono script esteso con clustering + spiegazione, da fare con referente.

Robustness notes differite (non bloccanti su questo golden, annotati): .strip() sui campi CSV, guardia righe malformite in load_golden, gestione esplicita date malformate in check_T21_weekday.

Doc: claudedocs/spalmone-2026-27/task472-results/VERDICT-ac2-2026-08-04.md.

Scope input AC#2 (onesta'): lo script classifica il GOLDEN (golden CSV seed/lessons.csv o path arg). L'opzione 'lessons.txt di un run' (formato pipe) non e' implementata - classificare le violazioni di un run e' domanda run-side diversa, si accoppia ad AC#3. Differita, documentata in VERDICT-ac2.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
COMPLETO (AC#3 chiuso 2026-08-14, AC#4 alimentato): script ripetibile scripts/analyze/golden-violation-occurrences.py (--full, golden CSV o path) + report claudedocs/spalmone-2026-27/task472-results/report-occorrenze-2026-08-14.md + occorrenze-golden-full-2026-08-14.txt. Le 440 M-TR scompose: 268 mista (61%, domanda referente foglio 22), 76 inspiegate (17%, lista per-coppia nel txt), 60 deliberate (14%, policy 2h), 36 compattazione docente (8%). M24 (15 settimane) e M25 (27 settimane) tutte inspiegate: nessuna forzante di carico, preferenze non-forzate -> tier MEDIUM confermato ovunque, nessuna promozione H. Limiti proxy dichiarati nel doc (auto-consistenza col golden, soglia 5 giorni lordi, metriche settimane-vs-unita). AC#4: i numeri sono pronti per il pacchetto referente (l'invio resta di maintainer). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
