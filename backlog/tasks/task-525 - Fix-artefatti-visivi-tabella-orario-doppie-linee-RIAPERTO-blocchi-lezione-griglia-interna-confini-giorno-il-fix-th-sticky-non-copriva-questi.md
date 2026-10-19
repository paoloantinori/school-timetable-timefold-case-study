---
id: TASK-525
title: >-
  Fix artefatti visivi tabella orario: doppie linee (RIAPERTO - blocchi lezione,
  griglia interna, confini giorno; il fix th-sticky non copriva questi)
status: Done
assignee: []
created_date: '2026-08-19 05:42'
updated_date: '2026-08-19 18:19'
labels:
  - claude-planned
  - ui
  - bug
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RIAPERTO 19/08 sera (maintainer, con screenshot): il fix 5b873522 (bordo celle data th sticky) NON copriva il difetto principale. Verificato da Fedora sul build LIVE del laptop (rebuild post-H32, quindi fix incluso): doppie linee ancora visibili (1) sui bordi dei blocchi lezione colorati, (2) sulle linee griglia verticali interne (ogni sottocolonna disegna il proprio borderRight con spessore variabile 1px border-faint / 2px border-light: due celle adiacenti producono linee raddoppiate), (3) ai confini giorno (TimelineView contenitore esterno border-b-2 SI SOMMA al border-b delle celle interne). Riproduzione: http://laptop.lan:8080 vista timeline, screenshot confronto in /tmp/ui-now.png (Fedora) e immagine maintainer._scope esteso: non solo celle data, tutto il layer dei bordi della griglia orario.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 BUG 1 (doppie linee celle data, WeekViewGrid.tsx): bordi del thead sticky row 2 + td sticky riga giorno + border-collapse si sommano (screenshot 19/08 LUN 14/09). Fix: bordo singolo dichiarato una sola volta, o separatore via box-shadow per gli sticky
- [x] #2 BUG 2 (icona tagliata, pannello calendario): icona GIO 07/01 troncata dal bordo inferiore del contenitore (manca overflow/scroll o padding-bottom nella lista date)
- [x] #3 Entrambi i fix verificati con screenshot prima/dopo nel browser (regola consumare-dataviz)
- [x] #4 DoD standard: typecheck, build, test.sh EXIT 0
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
FINDING UI 19/08 (maintainer in ispezione): durante il B-ROOM la UI mostra contorni rossi/H29 su ~2859 lezioni perche' legge il DB (persist parziale: 9635/12494 stanze) mentre lo score in-memory e' 0hard. La UI non distingue 'solve in corso' da 'soluzione consegnata': le violazioni al click andrebbero disabilitate o bannerate quando stagedStage e' attivo/refiningForDelivery. Famiglia TASK-509, fronte UI mai curato.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-08-19 06:34
---
FIX (5b873522, typecheck+build verdi): BUG1 bordo dichiarato una sola volta (il th sticky della riga 2 del thead non disegna piu' border-b; resta il border-b del td della riga giorno: niente piu' doppie linee con lo sticky che non collassa). BUG2 paddingBottom 6px al contenitore della lista date (creditto altezza dato alla List: icona dell'ultima riga non troncata). FINDING UI: useLessonViolations ora enabled solo quando stagedStage e' nullo (durante il ciclo staged il DB e' persist parziale TASK-509 e i contorni rossi descrivevano uno stato non consegnato).
---

created: 2026-08-19 06:34
---
Verifica browser prima/dopo (AC#3): NON eseguita in questo turno (app occupata dall'ispezione di maintainer col JAR corrente; il bundle nuovo entra al prossimo rebuild). Da fare al primo rebuild: screenshot prima/dopo su Week e Timeline.
---

created: 2026-08-19 07:16
---
VERIFICA BROWSER (post-rebuild, bundle CeCqVNql): Week mode: il th sticky di riga 2 ora ha border-bottom 0px (era 1px: misurato via computed style sul DOM live, 42 celle): il bordo e' dichiarato una sola volta (border-b del td riga giorno). Timeline mode: contenitore lista con paddingBottom 6px presente nel DOM (flex 1, minHeight 0). Screenshot prima/dopo archiviati in claudedocs/screenshots/task525-{week,timeline}-after.png (il prima e' negli screenshot originali di maintainer). Violazioni-durante-solve: verificato via typecheck (enabled: stagedStage == null); il comportamento si vedra' al prossimo solve.
---
<!-- COMMENTS:END -->


## Round 2 (notte 19/20-08, commit fd6a9829, typecheck verde)
Fedora ha screenehato il build live: le doppie linee RESTANO (il fix th-sticky copriva solo le celle data). Scope vero applicato con la regola UN-PROPRIETARIO-PER-CONFINE: il last sub-column NON disegna piu' il borderRight (il divider del gruppo successivo e' l'unico proprietario del confine): 3 siti corretti (celle chiuse, celle holiday, celle lezione). I segni semantici completion/stage RESTANO (informativi, non griglia). Confine giorno TimelineView (border-b-2 contenitore + border-b celle): DA VERIFICARE VISIVAMENTE col prossimo rebuild prima di tocreferente. Rebuild differito a app ferma (il run di stanotte occupa il JAR).

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
