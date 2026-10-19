---
id: TASK-579
title: >-
  Harness anti-errore per le operazioni laptop: censimento errori settimana +
  script di prevenzione
status: Done
assignee: []
created_date: '2026-09-02 15:51'
updated_date: '2026-09-02 16:35'
labels:
  - process-hardening
  - laptop-ops
  - harness
  - reliability
milestone: m-13
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Ordine di maintainer (2/09 sera): nell'ultima settimana ho fatto parecchi errori operando il laptop (codice vecchio girato, flag inesistenti lanciati, e ogni altro genere di cazzata). LAVORO: (1) studiare le sessioni recenti (checkpoints di memoria, registri task, transcript jsonl) e censire gli errori REALI commessi sul laptop; (2) distillarne una tassonomia per causa-meccanica (non per sintomo); (3) costruire script e harness che rendano quegli errori IMPOSSIBILI o comunque LOUD (verifiche meccaniche pre-lancio, banner-check automatico, flag-existence check, guard sulle capture); (4) testare gli harness sullo stato reale del laptop; (5) documentarli nel runbook e in memoria. Fonti note da cui partire: confound jar-stantio della serie E (TASK-576), ciclo delle 05:50 senza SurplusHarvest.class (TASK-577), MAVEN_OPTS che non raggiunge il fork, capture corrotte per api.sh export giant-body (TASK-567), export 1,2GB non pushabile, PATH ssh non-interactive mancante (podman/tmux/mvn), turni zai morti dopo mandati multi-voce, artifact su worktree non visibili dal clone principale, ssh irraggiungibile sotto JVM 920%.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CENSIMENTO COMPLETO (agente dedicato su checkpoint/registri/runbook): 27 INCIDENTI in 6 FAMIGLIE: A stale-build (serie E confound, ciclo 05:50 senza SurplusHarvest.class: 2 ricorrenze in 24h), B flag-mai-consumati (MAVEN_OPTS, GRASP owned-by-wrapper 3 lanci mistidi 29-30/8, 'command line come prova' regola permanente), C cicli dichiarati partiti senza banner-gate (il run-measure esisteva ma era BYPASSATO dai lanci diretti), D capture/artifact (TASK-567 1-byte x6, export 1.2GB/106MB non pushabile, worktree odb-flap, commit-senza-push, typo claudodocs), E ssh/tmux (PATH non-interactive, 920% JVM, pane sbagliato, turni morti multi-voce 4x, paste mancanti), F verdetti-da-proxy (0hard da stream misto, screening ridotto che 'confermo' un ranking, replay-di-stato che non riproduce i contatori). TOP-5 uncovered: lanci fuori harness, fragilita' capture/export, mandati/turni, flag owned/nonexistent, verdetti da proxy.

HARNESS COSTRUITO E TESTATO SUL CAMPO (6 commit): (1) launch-preflight.sh (jar INTERNO app/+lib/main/ vs HEAD, classi nel jar, flag-existence planner.*+quarkus.* anche in resources, banner-wait post-lancio, battery Darwin): 7 verdetti verificati sul laptop inclusi i 4 casi d'incidente (stale beccato sul jar del wt, typo col hint, classe inesistente, banner mancante); (2) capture-doctor.sh (export>=1KB+gunzip, pushability 99MiB, hard score-vs-analysis, sanity presence, lint typo): 3 casi sul laptop (sana=warn 105MiB GIUSTO, corrotta=FAIL, typo-dir=warn); chiamato da capture-run.sh e dal finalize di run-measure; (3) laptop-send-mandate.sh (gate pane-zai-idle, paste con prova marker O [Pasted text #N], C-u PRIMA del paste, Enter separato, rifiuto multi-voce, --check): testato --check sul vivo (idle senza marker = FAIL corretto; il send END-TO-END resta da esercitare al primo mandato reale); (4) run-measure instradato (via il doppio check sullo stub, manifest col jar interno, hint post-lancio); (5) registrati in laptop-RUNBOOK (sezione 'I tre gate obbligatori'), scripts/README, claudedocs/scripts.md, memoria laptop-ops-three-gates.

REVISIONI ASSORBITE (4-reviewer /simplify + code-review high): F0 chmod mancante (il wiring doctor era MORTO), C1 parser --check, C2 C-u dopo il paste CANCELLAVA il payload, C3 paste multilinea collassano (prova via [Pasted text #N]), I1 APPJAR_GLOB unbound, I2 date -r BSD, I3 stat fallback concatenato disinnescava la guardia su GNU, I4 quarkus.* false-fail, I5 doppio-zero REDS, stat guard numerico, ls -t newest-wins, ControlMaster rimosso (rompeva auth), tmux risolto lato laptop, WORKING=prompt-assente (glifi spinner del turno finito = falsi working), NBSP prompt. LIMITI DICHIARATI: banner-check prova ARRIVO non PERSISTENZA (caso F7-A); il send mode non ancora esercitato su un mandato reale.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
TASK-579 CHIUSO. CENSIMENTO: 27 incidenti 26/8-2/9 in 6 famiglie (stale-build, flag-mai-consumati, cicli-senza-banner-gate, capture/artifact fragili, ssh/tmux transport, verdetti-da-proxy), top-5 uncovered identificati. HARNESS: tre gate meccanici nel repo, ognuno nato da un incidente reale e TESTATO SUL CAMPO: launch-preflight.sh (jar interno vs HEAD, classi nel jar, flag-existence, banner-wait, battery: 7 verdetti verificati sui casi d'incidente), capture-doctor.sh (integrita' export, pushability, hard cross-check, sanity, lint: 3 casi), laptop-send-mandate.sh (procedura tmux 5-passi in un comando con prove di consegna: --check testato sul vivo). run-measure instradato sui gate; documentati in laptop-RUNBOOK/README/scripts.md + memoria laptop-ops-three-gates. REVISIONI: /simplify 4-reviewer + code-review high, 15+ finding applicati (inclusi 3 critici del mandate che il testing sul vivo ha confermato: C-u-che-cancellava-il-payload, tmux-path, falsi-working). LIMITI DICHIARATI: banner prova arrivo non persistenza (F7-A); send-mode esercitato al primo mandato reale. DoD: /simplify + code-review + verifica sul campo (script bash: nessuna suite JUnit applicabile).
<!-- SECTION:FINAL_SUMMARY:END -->
