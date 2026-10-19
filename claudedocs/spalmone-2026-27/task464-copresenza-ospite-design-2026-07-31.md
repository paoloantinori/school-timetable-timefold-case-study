# TASK-464: co-presenza ospite nel lab - design + prototipo FLAG OFF (zai, 2026-07-31)

Gate TASK-464 SUPERATO dallo spike (`spike-room-null-copresenza-2026-07-31/VERDICT.md`): 100% dei
92 room-null cade su celle golden co-presenza. DECISIONE maintainer (anti-attesa, questa sessione): la
modellazione NON e' gated a referente. A livello di ORARIO la co-presenza e' la pratica da replicare
(golden autoritativo, non registra le aule); la domanda fisica "5 in un lab o alcune in aula
normale?" va nel doc di notifica referente (TASK-466c) senza bloccare. Adozione resta di maintainer.

## Design breve (forma scelta)
**Esenzione mirata di H2 (pairwise) + nuovo cap-N per-cell count-based (H-COPRESENCE, hard),
entrambi dietro flag default OFF, coppie/cap SOLO da evidenza golden.**

Perche' questa forma e non altre:
- H2 e' pairwise `(room, date, slot)` cap-1: per legalizzare N gruppi co-presenti serve (a) togliere
  la penalita' H2 alle coppie ospiti golden-praticate E (b) mettere un soffitto altrimenti la
  pairwise-exemption autorizzerebbe anche 6+ in una stanza. Il count-cap (b) e' indispensabile.
- Il cap e' count-based per `(tag, startDateTime)` (stile M-TC), con soffitto = depth golden di
  quella cella. La depth golden e' la pratica da replicare, non un numero a mano.
- Tier HARD per il cap (non medium come M-TC): M-TC tollera le 44 overload del golden perche' il
  golden stesso le accetta; qui il cap e' il soffitto della pratica golden, superarlo e' l'abuso
  (6+ in una stanza) e va vietato. Il golden rispetta il proprio soffitto -> 0 hard sul fedele.

## Meccanismo (2 vincoli + 1 loader, tutti flag-gated)
1. **H2 exemption (filtro pairwise, BaseConstraintProvider.roomConflict)**: dopo i filtri esistenti
   aggiungo `.filter((a,b) -> !(enabled && isCoPresenceGuestPair(a,b)))`. `isCoPresenceGuestPair`
   ritorna true se entrambe le lezioni sono dello stesso tag di co-presenza E la cella
   `(tag, weekday, slot)` e' golden-praticata (depth >= 2). Con `enabled=false` il `!(false && ...)`
   = `!false` = true -> il filter passa tutte le coppie -> **H2 byte-identico a oggi**.
2. **H-COPRESENCE cap (count-based hard, SchoolScheduleConstraintProvider)**: clone del pattern
   M-TC. `forEachIncludingUnassigned.filter(enabled && tag co-presenza && real teacher).groupBy(
   tag, startDateTime, count()).filter(count > goldenDepth(tag,weekday,slot)).penalizeHard(matchCount)`.
   Con `enabled=false` short-circuit a 0 match -> inerte.
3. **Loader golden (`loadGoldenCoPresenceDepth` in ConstrainedTeacherAllocator, mirror
   `loadGoldenSlot0Anchors`)**: single-pass classpath `seed/lessons.csv`, salta closed, conta
   gruppi distinti per `(subject-tag, weekday, slot)`, pubblica depth per cella + l'insieme dei tag
   con depth >= 2 (l'acconciatura, l'unico). Pubblicato PRE-solve in `ScheduleService.
   refreshSolverStatics` (stessa disciplina di M32/loadGoldenSlot0Anchors, mai a meta' solve).
4. **Flag**: `SchoolSchedule.isCoPresenceEnabled()` = `Boolean.parseBoolean(
   System.getProperty("planner.co-presence.enabled"))` -> default FALSE, LIVE read (template
   `isTagCapacityEnabled`). Banner in `ScheduleService.refreshSolverStatics`.
5. **ROOM_TIER_HARD_FAMILIES**: aggiungo `"H-COPRESENCE"` (e tengo H2 gia' presente) cosi' il
   ciclo staged continua a leggere `timeslotTierHardScore == boxed` pre-B-ROOM. H2 rimane
   room-tier; il count-cap e' room-tier (fire in B-ROOM come H2/H29).

   **CORREZIONE CODE-REVIEW (sera 31/07, verificatori Opus):** questa sezione era SBAGLIATA.
   H-COPRESENCE non referencia mai `l.room` (groupa su `(tag, startDateTime)`, l'unico token
   "room" e' `requiredRoomTags` che e' campo di Course), quindi e' un hard TIMESLOT-tier, non
   room-tier. Inserendolo in ROOM_TIER_HARD_FAMILIES lo si ESCLUDE da `timeslotTierHardScore`,
   accecando il gate del ciclo staged e l'`assertImproveOrNoop` dei repair: una cella
   over-depth a fine B-TIMESLOT leggerebbe "clean" (hard escluso), poi persisterebbe in B-ROOM
   dove muovere stanze non puo' risolvere una co-locazione di timeslot. **Fix applicato: H-COPRESENCE NON e' in ROOM_TIER_HARD_FAMILIES**, quindi `timeslotTierHardScore` lo conta e il
   ciclo lo risolve in B-TIMESLOT (desiderabile: ferma il CH dal creare stack room-impossibili).
   Il che corregge anche il "fire in B-ROOM": il cap firea sulle fasi timeslot (corretto, il
   count e' su startDateTime). Nelle fasi OFF-path e' inerte (0 match), nessun impatto produzione.

## Byte-identico a OFF (AC#2)
- `enabled=false`: H2 filter `!(false && ...)` passa tutto (H2 invariato); H-COPRESENCE short-circuit
  0 match. Nessun nuovo score, nessuna nuova match. Lo asserisco in un unit test
  (`CoPresenceFlagOffTest`): build della solution con flag OFF -> score e match-count identici al
  baseline senza il codice. ConstraintVerifier su H2 con flag OFF penalizza una coppia same-room
  same-slot exactly come prima.

## Coppie SOLO da evidenza golden (AC#4, mai generalizzare)
- L'esenzione e' gated dalla depth golden per cella: fire solo dove il golden pratica co-presenza
  (depth >= 2). Sul seed 2026/27 l'unico tag con depth >= 2 e' acconciatura, sulle celle
  Mon 08-12 (depth 2: G02 + G12) e Thu 08-15 (depth 2-5). Nessun altro tag/group abilitato.
- Nessuna whitelist manuale di nomi gruppo: la sorgente e' il golden meccanicamente letto.

## Cap per-cell dalla pratica golden (clearance attesa dallo spike)
Spike VERDICT: clearance 92 = cap2 48 + cap3 2 + cap4 23 + cap5 19. Il cap per-cell dal golden:
Mon 08-12 -> 2, Thu 08-12 -> 5, Thu 13 -> 4, Thu 14-15 -> 2. Il prototipo adotta depth golden per
cella, quindi il clearance mirato (cap variabile) e' quello che il golden stesso pratica.

## Piano di misura (AC#3)
1. **Checkpoint stanze**: solve staged fermo al checkpoint pre-B-ROOM-full, conta room-null H29 su
   celle co-presenza (target: i 92 scendono verso 0 per i guest abilitati). Se il checkpoint non
   regge (es. hard peggiora su altri tier, o il cap satura stanze)，STOP, non full-solve.
2. **Full-solve A/B** (solo se checkpoint regge): baseline (flag OFF) vs flag ON, batteria KPI
   standard (`capture-run.sh`): score, hard (boxed*2 + room-null), room-null, full-day, ricorrenza,
   cadence. Conteggio room-null convertiti in ospite-legale. n>=2 per la varianza di traiettoria
   (lezione E3: n=1 inganna sui convergence-failure).

## Alternativi scartati
- **Cap-N per-room dentro H2 (count-based replacement of H2)**: riscrive H2 da pairwise a count,
  risco di rompere l'invariante byte-identico e il accounting room-tier. Scartato: l'esenzione
  pairwise + count-cap separato e' additivo e piu' sicuro.
- **Assegnazione condivisa dichiarata (multi-room entity)**: cambierebbe il modello della stanza
  (una stanza -> un set di lezioni). Troppo invasivo per un prototipo. Scartato.
- **Tier medium per il cap (come M-TC)**: il cap qui e' soffitto di pratica, superarlo e' abuso ->
  hard. Medium lascerebbe 6+ in stanza con penalita' lieve. Scartato.

## Limiti / caveat
- Golden senza room: la depth e' gruppi co-presenti nello stesso (tag, slot), inferita. Non
  distingue "5 in 1 lab" da "alcuni in aula normale" (domanda referente, nel doc TASK-466c). A livello
  orario e' la pratica da replicare.
- Il cap fire in B-ROOM; se il solver co-loca oltre depth, hard. Da misurare che non degeneri
  (spreading indesiderato).
- n dipende dalla convergenza (lezione metodo E3).
