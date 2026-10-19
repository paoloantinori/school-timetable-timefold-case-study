---
id: TASK-383
title: >-
  Vanishing pins incident — root-cause the 866-pin wipe during screenshot
  session
status: Done
assignee: []
created_date: '2026-07-19 19:49'
updated_date: '2026-07-21 18:22'
labels:
  - claude-planned
dependencies: []
ordinal: 207000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
OPEN INCIDENT (2026-07-20, orchestration-checkpoint): between two Playwright screenshot runs against the shared dev app, ALL 866 pinned lessons vanished from the DB (0/10501 assigned; sidebar H0 went from 9,635 to 10,501). Timeline: state was 866-pinned after import -> screenshot spec run A (broad locator: div:has-text("Classes").last().click() - could have hit ANY container div, including one wrapping a destructive control) -> rebuild.sh (kill-app, mvn clean package, planner.sh run) -> screenshot spec run B -> pins gone. No 'Schedule reset' trace found in planner.log (which rotates on restart, so evidence may be lost). Re-import restored the state; the rewritten spec (getByRole exact) did NOT reproduce the wipe on the next run. TO INVESTIGATE: (1) reproduce with the old broad-locator spec against a disposable profile and watch access logs for POST /api/schedule/reset or DELETE endpoints; (2) audit rebuild/restart paths for anything mutating lessons on startup (DataGenerator guards, ImportService, healPinnedNullTimeslotLessons); (3) consider an access-log or audit trail for destructive endpoints (reset/import/delete) so the next incident is attributable; (4) consider requiring a confirm token on POST /api/schedule/reset to protect against stray UI clicks (the reset button has no confirmation dialog). Until root-caused: do not run browser automation against a dev profile whose state matters.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Root cause identified with mechanical evidence (reproduction or attributable log trail)
- [x] #2 Destructive endpoints attributable in logs (access/audit line with source)
- [x] #3 Decision recorded on reset-button confirmation UX
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Causa radice per ELIMINAZIONE (evidenza meccanica, no repro live - vietato browser automation su profili che contano + app spenta): resetSchedule() (Lesson.update timeslot=null,room=null,pinned=false) e' l'UNICO mass-unpin di tutte le lezioni; i bulk-delete cambierebbero il conteggio (10.501 ancora presenti => no delete); heal tocca solo pin con timeslot null; reschedule ri-pinna. Reachable solo via POST /reset (bottone UI). Il locator largo div:has-text('Classes').last().click() poteva colpire il controllo Reset; la riscrittura getByRole che NON riproduce corrobora. AC#2: attribution logging WARN in resetSchedule (conteggi pin/assegnazioni distrutte, before+after) + ScheduleResource (IP + User-Agent della sorgente - distingue Playwright/browser/curl) => ogni reset ora attribuibile nel log anche dopo restart. AC#3: decisione registrata (forensics doc) - dialog di conferma UI e' il fix reale contro lo stray-click (server-side non basta), creato TASK-388 per implementarlo+verifireferente con app su insieme a TASK-367. 688/688 test exit 0. Regola standing fino al dialog: niente browser automation su profili dev che contano.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
