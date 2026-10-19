---
id: TASK-404
title: >-
  Upgrade Quarkus 3.31.4 → 3.33.x LTS — post-campagna 401, con benchmark di
  riconferma
status: Done
assignee: []
created_date: '2026-07-23 18:00'
updated_date: '2026-07-25 12:30'
labels:
  - claude-planned
  - platform
  - spalmone-2027
dependencies:
  - TASK-401
priority: low
ordinal: 600
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Ricognizione versioni 2026-07-23 (fonti meccaniche: GitHub releases API + blog Quarkus):

- **Timefold: NESSUN upgrade necessario** — 2.3.0 (2026-07-10) è l'ultima; già adottata.
  Cadenza mensile, 2.4.0 attesa ~metà agosto (ricontrollare allora). Nota: la 2.3.0 include
  il fix "R&R allows for value to be kept unassigned" — la coppia ruin-recreate +
  allowsUnassigned su cui poggia il design box TASK-398/401 è supportata upstream.
- **Quarkus: noi 3.31.4 (stream regular, fuori finestra di supporto)**. Ultima stabile
  3.37.4 (2026-07-23); **LTS corrente 3.33** (2026-03-25, supporto fino a 2027-03-25,
  micro-release attive ~ogni 2 mesi), la stessa linea a cui Timefold allinea le proprie LTS.

**Target scelto: 3.33.x LTS** (l'ultima micro disponibile al momento dell'esecuzione), NON
3.37: il salto è piccolo (2 stream), rientra in supporto per un anno, ed evita Hibernate
ORM 7.4 (3.37) — la superficie di rischio maggiore per noi (Panache + schema-management
`update` in produzione); nulla di 3.34-3.37 ci serve funzionalmente.

**QUANDO: solo a campagna 401/403 chiusa.** Un upgrade di piattaforma a metà campagna
cambia le performance di base e invalida la comparabilità con i baseline TASK-393/398/401.

**Esecuzione:**
1. Bump `quarkus.platform.version` in pom.xml → 3.33.x; verificare compatibilità Quinoa
   (quarkus-quinoa 2.7.2 — controllare se serve bump).
2. `rebuild-checked.sh` + `test.sh` (golden incluso) + `ui-typecheck`/`ui-build`.
3. Fumo funzionale locale (/full-test breve) — attenzione a schema-management `update`
   su un profilo esistente (backup del volume prima).
4. **Benchmark di riconferma sul laptop** (laptop-RUNBOOK, 1-2 solve vs l'ultimo baseline
   adottato): le perf del solver non devono regredire — se regrediscono, indagare prima
   di adottare (direttiva primaria: la performance si attacca lato ricerca, ma un
   regresso da piattaforma va capito, non assorbito).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh, ui-typecheck/ui-build, rebuild-checked) + benchmark di riconferma laptop non regredito
<!-- DOD:END -->

2026-10-03 chiuso come SUPERATO: l'upgrade Quarkus e' andato oltre la meta (3.33 LTS) fino a 3.40.1 LTS (commit bdbfc0ff, 01/10), con suite 1402/0/0 e deploy pubblico verificato L1+L2. Il CVE-2026-16308 e' coperto dallo stream 3.40.
