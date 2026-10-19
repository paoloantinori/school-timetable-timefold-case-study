---
id: TASK-660
title: >-
  UX: una sola sorgente di verita' sul verdetto 'orario valido' (score vs chip
  hard vs feasibility) e in italiano
status: Done
assignee: []
created_date: '2026-09-24 08:45'
updated_date: '2026-09-26 05:28'
labels:
  - claude-planned
  - ux
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Finding blocker della UX review 24/09, MISURATO sul sito pubblico: sullo stesso schermo il planner vede 4 segnali contraddittori: banner verde 'Orario valido' (hard==0 dal latestScore), chip header rosso '8 HARD' (useLessonViolations da /api/schedule/analysis/lessons, che dichiara 8 lezioni con violazioni HARD mentre /api/schedule/analysis riporta hard=[]), warning 'Compaction requirements not met' (l'API feasibility dice feasible:FALSE mentre il banner dice valido), e lo score raw '[0 / 0]hard / [-672.7k / -3,229]soft'. Inoltre: il badge verde non e' gated su stagedStage (UnifiedSolverControls.tsx:895) e latestScore=ZERO al via del ciclo (ScheduleService.java:1587) quindi puo' apparire verde DURANTE il solve. Messaggi feasibility hardcoded inglesi (ScheduleResource.java:292-322). Nessuna riconciliazione a schermo fra le 5 sorgenti (score, analysis, analysis/lessons, feasibility, validation badge).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Un'unica definizione visualizzata di verdetto; le altre sorgenti concordano o la loro divergenza e' spiegata a schermo
- [x] #2 Il messaggio di feasibility e' in italiano e spiega cosa fare (non 'Compaction requirements not met')
- [x] #3 Il badge verde e' suppresso durante il ciclo staged (gate su stagedStage/refiningForDelivery), non solo su !isSolving
- [x] #4 Diagnosi scritta della divergenza score-vs-analysis/lessons sul deploy pubblico (score 0hard vs chip 8 HARD misurati 24/09) e dello score string a 2 tier mappato su 3 card (deploy stantio o score persistito stantio: accertare quale)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-09-25 12:00 Fedora: cura landed. SEMANTICA: feasible = entrambi i livelli hard puliti (ScoreCompat.hardLevels == ZERO_HARD_LEVELS: con quasi-tier armato il danno H34/H32 e' danno hard, cura del finding F1 della review; hardOf puro ignorava il livello 1). CODICI STABILI (feasible/hard_violated/not_scored/no_solution-defensivo) localizzati dal frontend con plurale _one/_other (F2) e passthrough defaultValue (F5). RIMOZIONI: soglia -50000 stantia + costante + breakdown analyzeScore (regressione efficienza auto-inflitta curata: minuti di recompute a ogni mount per un campo senza consumatori) + campo DTO morto + chiavi i18n morte. Chip allineato a !isBusyCycle (perimetro 661). Doc rest-api.md coerente (formato bracket TASK-538, no_solution dichiarato difensivo). Test contratto not_scored con nullValue (F4). Gates complete: /simplify 4-angle (tutti i finding applicati incl. la regressione), /code-review high 8-angle (7 finding: tutti curati o annotati). AC#3 era gia' coperta dal lavoro lane (badge gated), AC#4 chiusa dalla diagnosi. AC#1 e AC#2 ora chiuse dalla semantica unica. Verifica UI viva: differita al deploy coordinato (la lane serve il codice nuovo al prossimo rebuild).

2026-09-26 07:10 DEPLOY PUBBLICO (36f02554): verifica in vivo sul sito. PASS: chip di fattibilità localizzato ('✓ Orario valido: nessun vincolo rigido violato'), nessun falso 'Compaction', punteggio ripristinato post-restart in 10s dall'attivazione (658 AC#3 chiusa SUL PUBBLICO), UI italiana di default. RESIDUO con NUOVA EVIDENZA: il chip '8 HARD' sopravvive a un caricamento della pagina da zero (playwright sul laptop, /tmp/deploy-2609-public.png): quindi la spiegazione 'client congelato a metà ciclo' della diagnosi lane era INCOMPLETA: analysis/lessons dichiara 8 lezioni a livello hard anche a stato fresco mentre punteggio ripristinato + fattibilità (entrambi i livelli) dicono pulito. Ipotesi da verificare: analysis/lessons colloca le quasi (H34/H32, livello hard 1) nel bucket HARD mentre il ricalcolo del punteggio in produzione ha il quasi-tier disattivato (parametro predefinito) e quindi livello-1=0. La divergenza visiva resta: riaperta la parte AC#1 (divergenza spiegata a schermo) finché il chip non concorda o non viene spiegato.

2026-09-26 07:40 DIAGNOSI COMPLETA del chip 8-HARD (endpoints pubblici + codice): NON e' analysis/lessons (0 hard a stato fresco: concorda con score/feasibility) e NON e' client congelato. La sorgente e' il WATCHDOG di legalita': GET /api/schedule/validation/latest serve lo snapshot PERSISTITO piu' recente (restored da DB a ogni restart da ValidationService.onStartup), che sul pubblico e' del 24/09 07:36 (trigger solve-completed, hard -8 H17: un intermediario PRE-consegna). La consegna 0-hard delle 09:36 NON ha scritto snapshot perche' l'unico hook 'solve-completed' vive in saveFinalSolution (via SolverManager, solve ordinario) mentre la consegna staged passa dal wrapper multi-start che non spara l'evento: finestra cieca della stessa famiglia di 670. CURA landed nel working tree: evento INTERNAL DataMutationEvent a (1) ogni esito terminale del ciclo staged (withStagedOutcome finally: 'staged-cycle-<outcome>') e (2) al completamento del restore dello score post-restart ('score-restored-after-restart'), null-guarded per istanzazione non-CDI. Catena: evento -> validazione debounced (2s) -> snapshot fresco -> badge hidden a hard>=0. Cura immediata dello stato stantio del pubblico: POST /api/schedule/validation/run dopo il redeploy. Gates in corso.

2026-09-26 08:30 CURA LANDDED E VERIFICATA SUL PUBBLICO (175f0cde, redeploy con git-prima-del-kill): il chip '8 hard' e' SPARITO dalla pagina pubblica (playwright: chip-8-hard presente=False), lo snapshot watchdog e' fresco (hard=0, ts 26/09, trigger manual-run per lo stato corrente + INTERNAL retry per il futuro), il verdetto 'Orario valido' e' l'unico segnale di qualita' a schermo insieme allo score. Root cause FINALE (correzione della mia prima diagnosi da parte della code-review): i fire 'solve-completed' della via staged VENIVANO sparati ma validateNow li scartava col busy-skip perche' atterravano a meta' ciclo; cura strutturale = trigger INTERNAL con retry-until-quiescent (6s/600max) in ValidationService + fire a ogni esito terminale del ciclo e al completamento del restore. AC#1 CHIUSA (tutte le sorgenti concordano: score 0hard, feasibility feasible, analysis/lessons 0, watchdog 0), AC#2 e AC#3 gia' chiuse. TASK CHIUSO: 4/4.
<!-- SECTION:NOTES:END -->
