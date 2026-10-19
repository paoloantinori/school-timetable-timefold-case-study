---
id: TASK-459
title: >-
  Fase di costruzione - dossier unico dei difetti che nascono li' (late start 3
  FB, ritmo tier, buco slot 1)
status: Done
assignee: []
created_date: '2026-07-29 18:31'
updated_date: '2026-08-04 03:02'
labels: []
dependencies: []
ordinal: 282000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
META-TASK di raccolta, aperto da Fedora 2026-07-29. Tre indagini indipendenti hanno puntato tutte alla stessa fase, il piazzamento iniziale: (1) TASK-450, i 15 venerdi di G11 che iniziano alle 9 invece che alle 8 - refutate sia l'ipotesi prezzo (lo swap costa -3000 hard per H2 stanza e H1 docente, non e' M32) sia l'ipotesi penuria di aule (la fase orari gira PRIMA dell'assegnazione stanze, TASK-446); resta il buco alla seconda ora lasciato dalla costruzione, riparato per traslazione. (2) TASK-451 tier, la deriva dei generici e' -13,4 giorni a fine costruzione contro -6,6 nel run finale. (3) La ricombinazione orizzontale non e' un problema di offerta di docenti: nel golden ci sono in media 6,6 docenti del gruppo liberi per ogni ora e solo lo 0,1% di ore senza alternative, mentre nel nostro run a quei venerdi alle 8 i candidati conflict-free sono ZERO su 417: la saturazione e' PRODOTTA dal nostro piazzamento. La prova che manca a tutte e tre e' la stessa: il dump per fase (TASK-453), gia' deciso per la prossima certificazione.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Dump per fase acquisito e letto: identificato il momento esatto in cui la giornata G11 scivola dalle 8 alle 9
- [ ] #2 Stabilito se i tre difetti hanno una causa comune in costruzione o tre cause distinte
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
#1 - zai - 2026-07-30 - DIFETTO IN COSTRUZIONE aggiunto al dossier: deficit G06 / Servizio tecnico e stilistico / T01 (35 boxed nel cert 2026-07-29b). NASCE IN COSTRUZIONE: la CH assegna greedy i giovedi di T01 ai suoi 4 corsi, G06 resta corta.

MISURA (export, task459-slack-triangolo-g06-t01-2026-07-30.md): senza pre-assegnazione capacita 30h vs domanda 60h = SLACK -30h (overdemand strutturale, non varianza). Il run 447 (riferimento) attivava l'allocazione anticipata teacher-centric (TASK-435, flag constrained-teacher): 60 lezioni di G06 PINNATE -> tutte collocate (slack +5h). Il 29b non la attivava (0 pin -> 35 boxed).

LEVA = allocazione anticipata alla 435 su quel corso (constrained-teacher / pin anticipato teacher-centric), NON tuning. Coerenza con 432 (9 giovedi monografici G10) e 435 (pianificazione upfront dei giovedi di T01 tra i 4 gruppi): gia' progettate, il 29b semplicemente non le attiva perche' certificava TASK-456 room-tier.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-30 00:19
---
PROVA PER-FASE ORA DISPONIBILE (zai, 30/07 notte). Questo meta-task diceva 'la prova che manca a tutte e tre le indagini e' il dump per fase (TASK-453)'. Quel dump c'e' ora: certification-2026-07-29b/phase-transitions/ (fine-fase P2 LS-Compactness, P3 Compaction, P4 CH-Room, P5 LS-Room; manca solo P1 costruzione). Due indagini tracciate per-fase questa sessione, entrambe indiciano la NASCITA in P2 (prima LS) e il CONGELAMENTO dopo:

(1) TASK-450 (late-start G11): tutti i 18 mercoledi late sono gia' a >=9 a fine P2; P3 inerte; P4 ri-ancora incidentalmente 4 a 8 (18->14); P5 stabile. Lo slide nasce in P2 (chiusura del buco HARD H17 lasciato dalla costruzione allo slot 1), NON e' drift soft.
(2) TASK-457 (drift tier generici): -7,2 a fine P2, stabile -7,5 a P5 (delta -0,3); P3/P4/P5 inerti. Drift pienamente formato al primo LS.

SINTESI: costruzione + prima LS (P2) e' la fase dove nascono sia il late-start sia il drift tier, e NESSUNA fase successiva li recupera (P4 lo fa solo parzialmente e per caso sul late-start). Questo e' coerente con la direzione 'fix a monte nella costruzione' (459 direzione c, 450 SPIKE). ANELLO ANCORA APERTO: il verso P1-vs-P2 per entrambi (la costruzione crea il presupposto e P2 agisce, oppure P1 e' gia' cosi'?) richiede il dump di fine-costruzione P1 del 29b, non nel set. TERZO filone (saturazione 8-venerdi prodotta dal piazzamento, candidati conflict-free 0/417) tracciabile per-fase con gli stessi dump se serve. NESSUN codice modificato (read-only).
---

created: 2026-08-04 03:02
---
DOSSIER-459 (2026-08-04, read-only consolidation): AC#2 = cause DISTINCT, costruzione come NEXUS comune; tutti e tre i difetti RISOLTI sullo stack ratificato attuale. AC#1 = G11 friday late-start CURATO da TASK-451 (verifica task453: 34/34 venerdi start@8, 0 late, construction phase-2 e final phase-5); sullo stack vecchio 29b nasceva in P2 (chiusura H17 hole), ora PREVENUTO a monte dal blocco slot0+1 di 451. G06 T01 curato da TASK-435 (slack -30h->+5h). tier drift non persistente (LS lo rimuove al finale, VERDICT-457). Dossier chiude senza codice: ogni difetto aveva la sua ratifica (451/435) o era riassorbito (LS); non c'e' una singola leva 'sistema la costruzione'. Doc: task459-results/DOSSIER-costruzione-difetti-2026-08-04.md.
---
<!-- COMMENTS:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
