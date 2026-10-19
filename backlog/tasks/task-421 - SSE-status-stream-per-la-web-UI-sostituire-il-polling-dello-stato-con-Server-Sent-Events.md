---
id: TASK-421
title: >-
  SSE status stream per la web UI (sostituire il polling dello stato con
  Server-Sent Events)
status: To Do
assignee: []
created_date: '2026-07-26 14:24'
labels:
  - claude-planned
  - frontend
  - nice-to-have
dependencies: []
priority: low
ordinal: 244000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Valutazione 2026-07-26 (discussione maintainer/assistente, decisione: NON ora, tracciata qui).

CONTESTO: la UI polla GET /api/schedule/status (payload leggero TASK-380, staged-aware dal fix 2026-07-26). Un canale push sarebbe piu' elegante ma il polling NON e' la causa dei problemi visti il 26/07 (erano: campo stagedStage non letto dal client + poll gate spento durante i cicli staged - entrambi fixati). Costo del polling trascurabile su tool locale mono-utente; gli script di automazione (api.sh wait, capture-run, zai) restano comunque a polling, quindi un canale push aggiungerebbe un secondo modello di consumo dello stato da mantenere.

SE/QUANDO si fa, la forma giusta e' SSE, NON websocket: flusso puramente unidirezionale server->client; in Quarkus e' un endpoint @Produces(SERVER_SENT_EVENTS) su un Multi; riconnessione gratis con EventSource browser-side; zero infrastruttura ws. Sorgenti-evento gia' esistenti: PhaseTransitionMarker (transizioni fase), cambi di stagedStage (ciclo a stadi), onNewBestSolution. CAVEAT: gli eventi best-solution in piena LS arrivano a raffica -> throttling server-side obbligatorio (es. max 1 evento/2s), che di fatto reintroduce un intervallo lato server.

SCOPE se attivato: endpoint SSE /api/schedule/status/stream; client EventSource in useScheduleData/ScheduleTimeline con fallback automatico al polling attuale se la connessione muore; il polling NON si rimuove (resta per gli script e come fallback). Fuori scope: canali bidirezionali, ws.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Endpoint SSE con throttling + client EventSource con fallback al polling; il polling resta per script/fallback
- [ ] #2 ui-typecheck/ui-build/test.sh verdi; nessuna regressione del banner staged
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
