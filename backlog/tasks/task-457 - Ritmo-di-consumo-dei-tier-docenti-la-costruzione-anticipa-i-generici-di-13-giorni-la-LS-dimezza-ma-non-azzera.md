---
id: TASK-457
title: >-
  Ritmo di consumo dei tier docenti - la costruzione anticipa i generici di 13
  giorni, la LS dimezza ma non azzera
status: Done
assignee: []
created_date: '2026-07-29 18:30'
updated_date: '2026-08-04 02:58'
labels: []
dependencies: []
ordinal: 280000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
MISURA GIA' FATTA (Fedora 2026-07-29, doc claudedocs/spalmone-2026-27/task451-results/tier-docenti-misura-2026-07-29.md, script scripts/analyze/teacher-tier-kpi.py, report claudedocs/spalmone-2026-27/teacher-tier-pacing.html). Un docente e' GENERICO se serve >=8 dei 14 gruppi (9 docenti), SPECIALIZZATO altrimenti (17). Il golden tiene la quota di ore erogate da generici piatta fra 41% e 47% da settembre a giugno; noi partiamo sopra (+3,2 a settembre) e finiamo sotto (-6,4 a giugno), cioe' spendiamo presto la risorsa flessibile e chiudiamo l'anno con un residuo di specializzati poco sostituibili. Baricentro temporale: generici -6,6 giorni nel run finale, specializzati +0,1. CHECKPOINT: a fine costruzione la deriva e' DOPPIA (generici -13,4 giorni, giugno -12,4 punti), quindi nasce nel piazzamento iniziale e la LS la dimezza soltanto. Stessa fase indiziata per gli inizi in ritardo di G11 (TASK-450). NON ancora dimostrato che la deriva CAUSI un difetto noto: e' una firma sistematica, non una catena causale.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Stabilito se la deriva causa un difetto misurabile (buchi, deficit di fine anno, ricorrenza) o se e' solo una firma
- [ ] #2 Se causa: prototipo dietro flag default OFF di una regola di ordinamento in costruzione (spendere prima lo specializzato, tenere il generico come jolly) misurata con teacher-tier-kpi.py su dump di costruzione
- [ ] #3 Se non causa: chiuso senza codice con la misura agli atti
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DOWNGRADE 2026-07-30 (verifica richiesta da maintainer, non ripensamento): il lead tier-drift va DECLASSATO da 'lead supportato' a FIRMA con supporto causale DEBOLE, NESSUNA LEVA da costruire. Statistica: la deriva generici e il deep-deficit co-muovono a fine anno (sopra golden ott-feb, sotto -6,5/-7,2 mag/giu; deep 68-71% mar-giu). MA non e' causa: il gruppo dominante (~3/4 dei deep), G04, NON segue il disegno - ad aprile sta +2,5 SOPRA il golden sui generici (nessun esaurimento) e ha 10 deep (aprile e' il picco); 12/22 dei suoi deep cadono di venerdi' (G04 ven = ricerca non capacita', SPIKE 4). Lettura: a fine anno tutto si stringe, deriva e deep peggiorano per la stessa ragione di fondo, non per causa-effetto. CANCELLATO il 'prossimo passo = run con drift variato'. Il bersaglio operativo e' G04 venerdi' (ricerca), non la deriva tier. Dettaglio: task457-falsificazione-2026-07-30.md (VERDETTO + controesempio G04 aprile).
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-30 00:19
---
PER-FASE DOV'E' NATO IL DRIFT TIER (zai, 30/07 notte, dump per-fase 29b, certification-2026-07-29b/phase-transitions/, script teacher-tier-kpi.py). Baricentro temporale generici/specializzati (scarto run-golden, giorni; golden=0, negativo=generici front-loaded) per fase:

| fase | generici | specializzati |
|---|---|---|
| P2 LS-Compactness | -7,2 | +0,3 |
| P3 Compaction(repair) | -7,2 | +0,3 |
| P4 CH-Room | -7,5 | +0,2 |
| P5 LS-Room (final) | -7,5 | +0,2 |

FINDING: per il run 29b la deriva generici e' pienamente formata gia' a fine P2 (-7,2) e NESSUNA fase successiva la muove in modo materiale (P2->P5 = -7,2 -> -7,5, delta -0,3). P3 repair, P4 room, P5 room-LS sono inerti sul tier-pacing. Coerente con TASK-450 (anche lo slide 8->9 di G11 nasce in P2): stesso piazzamento iniziale indiziato, e l'evidenza per-fase che mancava a TASK-459 ora c'e'.

DISCREPANZA ONESTA DA RICONCILIARE (non coperta): la descrizione di questo task cita 'fine costruzione generici -13,4 giorni, run finale -6,6, la LS dimezza'. Per 29b misuro finale -7,5 (confermato dall'handoff 29b-vs-447) e STABILE dopo P2, cioe' NESSUN dimezzamento P2->P5. I numeri -13,4/-6,6 vengono dalla misura task451-results (snapshot/run diverso). Due letture discordano su magnitudo e sull'esistenza del dimezzamento: per chiudere il verso serve un dump di fine-costruzione (P1) del 29b (non nel set) per vedere se P1->P2 dimezza un valore piu' alto. Fino ad allora il drift va usato come FIRMA (naso in P2, congelato dopo), non come leva causale provata. NESSUN codice modificato (read-only, mandato documenta-e-passa).
---

created: 2026-07-30 01:25
---
FALSIFICAZIONE FATTA (zai, 30/07 notte, doc claudedocs/spalmone-2026-27/task457-falsificazione-2026-07-30.md). Test di maintainer: i deep-deficit (>=2h) si concentrano nei mesi con quota generici SOTTO il golden?

3 RIGHE:
- IPOTESI: deep-deficit concentra dove quota generici < golden (esaurimento tier -> buchi tardi non riempibili).
- NUMERO (n=3 29b/30a/30b, weekday, sabati esclusi): deep-deficit 75-77% in mar-giu, picco mar 11-12 + apr 10-12 + giu 22. Quei mesi sono ESATTAMENTE dove la quota generici del run e' sotto il golden (mar -0,6 ... giu -3,6); ott-feb (sopra golden) quasi zero deep.
- TENERE O BUTTARE: TENERE come lead supportato. La deriva NON e' firma inerta: traccia una concentrazione reale di deficit profondi a fine anno, n=3 coerente. MA LEVA NON PROVATA: (a) causazione non isolata dalla saturazione strutturale per-gruppo (G10 72h non collocate, G08 24h, G12 off-grid stage hanno deep per saturazione nota, non per tier); (b) i 3 run hanno drift quasi identico (-7,2/-7,2/-7,5) quindi non variano la presunta causa e non possono provare causazione. Prossimo passo decisivo = un run con drift variato.

CAUTELE VERIFICATE: (1) artefatto sabbati: 26 righe sabato in calendario_class_days.csv (G12 19 + G10 7 = stage off-grid) gonfiano il KPI e si addensano a mar-apr; ESCLUSI dall'analisi (weekday-only). BUG del KPI da sistemare a parte (daily-fill-kpi.py conta i sabati, dovrebbe escluderli come esclude G14). (2) gruppi finiti a giugno: verificato via completamento 30b - i deep di giu sono per lo' piu' REALI (G08 24h non collocate, G10 72h, G09/FB 8h: NON finiti); solo G13 e G07 (0h non collocate) sono finiti (i loro 2+1 deep di giu sono artefatto over-plan, minoranza).

AC #1 (causa difetto o firma?): RISPOSTA PARZIALE - non e' firma inerta (traccia difetto reale), ma causazione non isolata. AC #2 (prototipo flag): NON fatto, servirebbe un run con drift variato per giustifireferente. NESSUN codice di modello modificato; solo scripts/analyze/daily-fill-kpi.py esteso additivamente (bucket mensile deep, DoD piena) come strumento di misura.
---

created: 2026-07-30 01:30
---
CORREZIONE NUMERI (zai, stesso giro DoD): il commento #2 e la prima versione del doc usavano weekday deep 62-69 / mar-giu 75-77% / giugno 22 da un'analisi inline con orizzonte HI=2027-06-30 SBAGLIATO (righe fantasma post-fine-scuola). Numeri CANONICI (orizzontale reale LO=09-14/HI=06-10, sabati esclusi): weekday deep 44/44/51 (29b/30a/30b), mar-giu 68%/68%/67%, giugno 4 (non 22). Doc aggiornato ai numeri corretti (task457-falsificazione-2026-07-30.md). CONCLUSIONE INVARIATA: TENERE come lead supportato (concentrazione 67-68% mar-giu dove quota generici < golden, n=3 coerente), leva non provata.
---

created: 2026-08-04 02:58
---
VERDICT-457 (2026-08-04, read-only): construction-vs-final misurato col canonical teacher-tier-kpi.py sul run task453 (source=input 0-hard, stack ratificato attuale). Generic drift -7,1 (construction, phase-2 pre-LS) -> +0,2 (final, phase-5). La LS RIMUOVE il drift generico (non lo dimezza), contraddice entrambe le letture precedenti (task451 -13,4/-6,6 'LS dimezza'; 29b -7,2/-7,5 stabile 'LS inerte'). Causa probabile: ratifiche intervenute (435/451/463/469/478/483) cambiate il tier-pacing. Verdetto: FIRMA non leva causale (la LS rimuove il drift al finale, quindi non puo' causare il deficit tardo-annale persistente -> decoupled). AC#3: CHIUDI senza codice (AC#2 prototipo flag non warrantato - la LS riequilibra il tier-mix comunque). n=1 caveat (LS NON_REPRODUCIBLE), fresh re-run riproduce i numeri, parse sanity (family invariant) OK. Doc + raw: task457-results/VERDICT-457-construction-vs-final-2026-08-04.md. Commit 7ca82c8a.
---
<!-- COMMENTS:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
