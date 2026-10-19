---
id: TASK-587
title: >-
  Lore self-invocation CDI stantio in 3 commenti (ArC intercetta anche le
  self-call @Transactional: verificato dal path di consegna notturno)
status: Done
assignee: []
created_date: '2026-09-05 17:15'
labels:
  - claude-planned
  - code-hygiene
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task577-results/
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal code-review high del fix 526b (5/09 sera): TRE commenti nel codebase asseriscono che la self-invocation CDI bypassa gli interceptor (@Transactional/@ActivateRequestContext): ScheduleService.java:1030-1031, HybridPhase1aService.java:197-199, ValidationSnapshotRepository.java:22-26. Il comportamento di produzione dimostra il contrario in ArC: loadSchedule(true) a ScheduleService:1178 e' una chiamata interna @Transactional sul thread demone staged-solve SENZA request context, e ogni solve notturno dipende dal fatto che l'intercettore scatta (altrimenti Timeslot.listAll() a :328 tirerebbe la stessa ContextNotActiveException che ha ucciso il check di concordanza). I pattern difensivi costruiti su quel lore (self-Instance proxy in HybridPhase1aService, wrapper esterni) sono ridondanti ma innocui; il rischio e' la prossima decisione di design presa sul lore sbagliato (es. scegliere una forma contorta per evitare un bypass che non esiste, o fidarsi di un @Transactional self-call dove invece serve requiringNew per semantica propria come nel fix 526b). Cura minima: correggere i tre commenti con la semantica verificata (ArC intercetta anche la self-invocation per questi interceptor; la forma programmatica QuarkusTransaction resta preferita quando serve una transazione DISSOCIATA dal contesto, non per bypassare interceptor inesistenti). Verifica consigliata prima di scrivere: un micro-test che chiami un metodo @Transactional self-invocato su thread nudo e osservi la transazione attiva (pinna la semantica di ArC contro la deriva futura del lore).
<!-- SECTION:DESCRIPTION:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
