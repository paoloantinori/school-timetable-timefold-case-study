---
id: TASK-374
title: 'Golden-derived defaults for the open availability questions (Q11-Q14, Q16)'
status: Done
assignee: []
created_date: '2026-07-17 12:31'
updated_date: '2026-07-17 13:46'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-8
dependencies: []
priority: high
ordinal: 198000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY (Fase 0 della strategia autonoma, 2026-07-17): referente è in ferie; l'orario golden è VALIDO, quindi le risposte de-facto alle domande aperte si leggono da lì. Verificato meccanicamente dal main session: T10 insegna il giovedì slot 4 (12-13) 20 volte -> giovedì bloccato SOLO slots 0-3; T12 mai negli slot 0-1 di lunedì e martedì (equipe dalle 10:00 su entrambi; martedì è il suo giorno principale, 24 settimane) -> bloccare lun+mar slots 0-1; T16: martedì quasi vuoto (1 lezione) e mercoledì mattina scarico (slots 0-2 usati solo ~11/33 settimane) -> le '2 mattine dalle 11' = mar+mer, bloccare slots 0-2 mar+mer (le eccezioni golden vanno misurate e documentate); G14 solo gio+ven (già così nel seed - confermare soltanto); T13 'Comportamento sociale lab due' = nota non-availability, nessuna regola.

WHAT:
1. Update availability_rules.csv (both copies + byte-sync): T10 Thursday 0-3 (replaces full-day block — this UNBLOCKS his Thursday 12-13 slot the golden uses), T12 recurring Mon+Tue slots 0-1 UNAVAILABLE, T16 Tue+Wed slots 0-2 UNAVAILABLE. Every new/changed row's note column marks 'derived-from-golden 2026-07-17, da confermare al rientro di referente'.
2. Measure each rule against the golden (count golden lessons that would violate it) and document the counts in availability-not-yet-modeled.md — if a derived rule fires on >2% of that teacher's golden lessons, weaken it and note why (the golden is valid by definition).
3. Move Q11/Q12/Q13/Q14/Q16 answers to the archive section of domande-per-la-scuola.md marked 'DERIVED (golden), pending referente confirmation'; empty domande-brevi.md accordingly (keep the file with a note that nothing is pending).
4. Update the NOT-YET-MODELED registry entries accordingly.
Ref: claudedocs/spalmone-2026-27/analysis.md §5, availability-not-yet-modeled.md, the mechanical checks in the session (T10/T12/T16 slot usage).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Rules updated with provenance notes; byte-sync green; golden-violation counts per rule documented and <2% each
- [x] #2 Question sheets updated (archive marked DERIVED); NOT-YET-MODELED registry aligned
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Golden-derived availability defaults adopted (T10 Thu 0-3 only, T12 Mon+Tue 0-1, T16 Tue 0-2; Wednesday REJECTED by the >2% rule at 6.87% — Q12-bis stays open for referente; T13 note non-availability; G14 confirmed). All percentages re-runnable via derive-golden-availability-checks.py (documented PASS/FAIL output). Value-level seed assertions added. code-review high combined with 371 (E1-E9). DoD complete. Tests 514/514.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
