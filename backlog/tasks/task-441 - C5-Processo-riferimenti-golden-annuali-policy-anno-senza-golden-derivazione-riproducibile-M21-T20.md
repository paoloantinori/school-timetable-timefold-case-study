---
id: TASK-441
title: >-
  C5 Processo riferimenti golden annuali (policy anno-senza-golden + derivazione
  riproducibile M21/T20)
status: To Do
assignee: []
created_date: '2026-07-28 08:23'
updated_date: '2026-08-08 21:25'
labels:
  - claude-planned
milestone: m-13
dependencies: []
priority: high
ordinal: 264000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Audit C5+G: path reference per-anno; POLICY esplicita per l'anno senza golden (oggi M34/M35 si auto-spengono in silenzio); regeneration documentata (cadence-kpi -j); script di derivazione per soglia M21 e finestre T20 (oggi non riproducibili); pattern aspirazionale = M32 (reference dai dati vivi, niente golden).
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
COLLEGAMENTO 2026-07-30: questo task e' la meta' 'politica' del vincolo che maintainer ha ribadito stamattina (ci passeranno gli INPUT, non una soluzione di esempio). L'altra meta', tecnica, e' TASK-452. La direttiva architetturale sta in CLAUDE.md. Aggiunta di scope: la policy non basta che dica COME derivare i riferimenti senza golden, deve anche dire QUALI pratiche non sono derivabili e vanno chieste alla scuola come input dichiarato nel workbook TASK-445 (candidati noti: concentrazione per docente in stile T01-giovedi, ancore settimanali per gruppo, cadenze attese per (gruppo, materia) oggi lette da golden-cadence-reference.json per M34/M35). Finestra: la validazione 'derivazione senza golden == derivazione col golden' si puo' fare solo finche' il golden 2026/27 esiste.

AGGIORNAMENTO CENSIMENTO 2026-07-30 (zai, LAVORO 2): la colonna (c) del censimento (claudedocs/spalmone-2026-27/censimento-dipendenze-golden-2026-07-30.md) e' la lista concreta di domande-workbook che questa policy deve raccogliere mentre il workbook e' ancora in costruzione. La pratica 'T01-giovedi' gia' candidata qui e' stata VERIFICATA non deducibile: misura solo-input (ore dovute / weekday disponibili) mette T01 #1 solo perche' la disponibilita' solo-giovedi e' essa stessa una regola Q22 golden-derived in availability_rules.csv (7 docenti nella famiglia Q22, tutti 'in attesa conferma scuola'). Conclusione per la policy: l'anno senza golden DEVE prevedere di farsi dichiarare i giorni fissi per docente (non derivabili da incarichi/calendari/quote). Verifiche ancora aperte nella finestra che si chiude: cadenza mono-room (#2) e burst-cap M34/M35 (#3), confronto derivazione input-only vs golden non ancora fatto in questo giro (read-only).
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: zai
created: 2026-08-08 11:31
---
POLICY + M21 VERIFICATION (2026-08-08, zai, turno Fedora offline): deliverable = policy doc `task441-no-golden-policy-2026-08-08.md` + script `scripts/analyze/derive-m21-exempt-no-golden.py`.

1) POLICY: consolidamento censimento #1-#8 (cosa scade col golden: #1 ConstrainedTeacherAllocator DEFAULT-ON parziale, #2 MonoRoomTagAllocator non-derivabile/LabRotation vuoto, #3 M34/M35 cadenza non-derivabile + self-gate SILENT, #5 Q22 6 docenti scelta). Cosa sopravvive: #6 M32, #7 repair, #8 seed, costruzione, vincoli classici.

2) M21/T20 (verifica script): NON sono letture golden-runtime, sono COSTANTI Java (SSCP:90,1274-1277) esposte come colonne workbook (08_Materie capOreGiorno/settimanaInizio). M21_EXEMPT NON derivabile da subjects.csv (best rule 'no roomTag' precision 7,5%, nessuna al 100/100): scelta della scuola. La 'riproducibilita' no-golden e' la COLONNA DICHIARATA (scuola conferma), non una derivazione dati.

3) POLICY operativa: auto-disable deve essere LOUD non silent (M34/M35 oggi si spengono invisibili: banner + check 99_Validazione, gated). Ogni riferimento in scadenza: due esiti onesti (deducibile->cambia sorgente+verifica ORA; non deducibile->colonna workbook). Verifiche input-vs-golden fatte: #1, M21, #3. RESTA: confronto end-to-end source=input vs source=golden sui KPI (non fatto questo giro).

RESTA in TASK-441 (per Fedora): (a) implementare l'auto-disable loud (banner+check), gated; (b) il confronto KPI end-to-end input-vs-golden; (c) raccogliere le dichiarazioni colonna-c nel workbook. Status resta To Do (policy e' analisi, non adozione).
---

author: zai
created: 2026-08-08 21:25
---
RESIDUO 'CONFRONTO END-TO-END' CHIUSO (2026-08-08, zai, Fedora offline): doc `task441-input-vs-golden-2026-08-08.md`. Solve source=input vs source=golden su questa laptop (capture canonica entrambi i lati).

RISULTATO: i due path sono COMPARABILI. hard=0 entrambi (via multi-start pick-best; source=input ha il bad-seed H17 curato dal retry, 2 tentativi). KPI strutturali identici (0 buchi, day-start clean entrambi). source=input leggermente indietro sui medium (ricorrenza 74,6% vs 75,9%, full-day 84,4% vs 86,8%, ~-1/-2,4pp entro varianza NON_REPRODUCIBLE).

VERDETTO: il path no-golden (source=input) e' VIABLE: produce orario hard=0 strutturalmente identico al golden path. La direttiva solo-input e' rispettata. Il debito golden-runtime #1 (ConstrainedTeacherAllocator DEFAULT ON) e' confermato non-bloccante per la qualita' (lavora su source=input, produce 0hard).

Le verifiche 'input-vs-golden' della finestra sono ora TUTTE fatte: #1 docente vincolato (censimento), M21 (script), cadenza (censimento), confronto solve end-to-end (questo). Resta in TASK-441: (a) auto-disable loud (gated), (b) raccogliere dichiarazioni colonna-c (cadenza DONE, Q22/LabRotation restano).
---
<!-- COMMENTS:END -->
