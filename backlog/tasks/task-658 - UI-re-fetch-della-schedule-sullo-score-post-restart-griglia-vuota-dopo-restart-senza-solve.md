---
id: TASK-658
title: >-
  UI: re-fetch della schedule sullo score post-restart (griglia vuota dopo
  restart senza solve)
status: Done
assignee: []
created_date: '2026-09-24 05:17'
updated_date: '2026-09-24 08:45'
labels:
  - claude-planned
  - tooling
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dall'handoff referente 23/09 sera: dopo un restart dell'app, il backend ora ripristina lo score al primo read (f73bdc97, one-shot solutionManager.update) MA la UI non re-fetcha la schedule quando lo score passa da null a valorizzato: la griglia resta vuota finche' non arriva un evento del solver (click su Solve) o un import. Il fix backend e' anche BLOCCANTE sul primo fetch (il recompute da ~10-20s rallenta la prima GET /api/schedule: valutare la forma asincrona). CURE: (1) frontend: useScheduleData re-fetch della schedule quando lo status score transita null->valore (o un evento 'schedule-updated'); (2) backend: restaurazione asincrona (background al primo read) per non allungare il primo fetch. Verifica: restart dell'app + open della UI senza solve -> griglia popolata. Contesto: screenshot deploy-final (shell vuota) + report laptop 23/09 sera.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dopo restart dell'app (senza solve), l'apertura della UI mostra la griglia popolata e lo score valorizzato entro il tempo del recompute asincrono, senza click manuali
- [x] #2 La cura frontend e' reale: il re-fetch al transito null->score NON puo' appoggiarsi al solo poll shouldPoll (ScheduleTimeline.tsx:349-351) perche' da fermo nessun poll gira; serve un poll leggero attivo finche' score==null o un evento equivalente
- [ ] #3 Verifica live sul sito pubblico: restart + open, screenshot griglia popolata (memoria deploy-verifica-pagina-renderizzata)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-24 UX review: verificato in codice che la cura (2) backend asincrona E' landed (a8886823, guard AtomicBoolean) mentre la cura (1) frontend NON esiste: la 'change-detection gia' esistente' citata in a8886823 vive dentro il poll che gira solo se shouldPoll (solving/pre-assign/staged) e da fermo e' false. Da fermo l'unica cosa che polla e' ValidationBadge (15s, altro endpoint).

2026-09-24 lane UX laptop (commit 5f938b30, feature/ux-planner-lane): cura (1) landed e verificata live sulla lane (worktree + DB/porta paralleli). Forma reale: non useScheduleData ma il poll di ScheduleTimeline come sede del re-fetch (la' vive la change-detection): poll leggero attivo finche' score null E esistono lezioni collocate (awaitingScoreRestore) + schedule?.score nelle deps dell'effetto polling e dell'effetto feasibility (il banner 'Solution not yet scored' si aggiorna). Prova meccanica AC#1: load pagina 13:13:12 score null, restore server 13:13:21, griglia popolata senza click. REPORT: claudedocs/spalmone-2026-27/ux-review-2409/REPORT.md, img baseline+after. AC#3 (sito pubblico) resta aperta: richiede il restart dell'istanza di referente, differita al deploy coordinato con Fedora.
<!-- SECTION:NOTES:END -->

2026-10-03 chiuso con AC#3 soddisfatta dall'evidenza dei deploy pubblici: due restart dell'istanza pubblica (28/09 e 01/10, quest'ultimo sul ramo upgrade) con L2 verde su https://planner.example.org: griglia popolata (12551/12551 assegnate), score ripristinato dal DB entro ~25s dal boot, badge valido, zero errori console. Screenshot: .playwright-mcp/deploy3-public-upgraded.png (01/10). Cura frontend: commit 5f938b30, verificata live in lane il 24/09 (REPORT ux-review-2409).
