---
id: TASK-412
title: >-
  Concentrazione giorni-docente — golden: settimane docente compatte (es.
  T01 solo giovedì); noi: sparsi su 5 giorni
status: Done
assignee: []
created_date: '2026-07-24 12:10'
updated_date: '2026-08-03 21:59'
labels:
  - claude-planned
  - research
  - constraints
  - spalmone-2027
milestone: m-12
dependencies: []
priority: medium
ordinal: 500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Rivelata dalla vista "Docenti (fingerprint)" di golden-vs-run-viz (2026-07-24): il golden
CONCENTRA la settimana di molti docenti in pochi giorni pieni, noi li spargiamo su tutti e 5.
Esempio lampante: **T01 golden = SOLO giovedì (97%)**, nostro run = tutti i giorni al
6-73%. Nessun vincolo di disponibilità lo impone (altrimenti il VRP bloccherebbe anche noi):
è una SCELTA del planner umano. T02/T03 mostrano lo stesso pattern attenuato
(struttura giorni forte nel golden, diffusione da noi).

Oggi NULLA chiede la consolidazione: S1 minimizza i buchi DENTRO il giorno, M24/M25 sono
speciali per-docente (T11/T05), nessun vincolo limita i giorni lavorati/settimana in
generale. È plausibilmente una qualità importante per i docenti (meno viaggi/presenze).

## SCANSIONE SISTEMATICA ESEGUITA (Fedora 2026-07-24) — ipotesi di maintainer confermata e ALLARGATA

Scan di TUTTI i docenti (ore golden per weekday vs regole seedate): **6 candidati** con ≥90%
delle ore in ≤2 weekday e nessun vincolo hard corrispondente: **T01 231h → 100% giovedì**
(unico con maxDailyHours=8, l'indizio "data-driven" che avevamo già recepito senza capirne la
causa), T07 72h → 100% mercoledì, T19 100% lun+mar, T22 98% lun+gio, T24 100%
lun+mar (documentato solo come PREFERENCE equipe), T12 97% lun+mar. Più due "negativi":
T17 mai-lunedì/quasi-mai-venerdì (0/2 su 393h), T16 quasi-mai-martedì (1h su 495).

**La risposta all'alternativa di maintainer ("non nei documenti" vs "non recepito"): NON È NEI
DOCUMENTI.** availability-not-yet-modeled.md §"Rows with nothing to transcribe" conferma per
ispezione diretta che le celle vincoli del foglio sono VUOTE per T01/T19/T07/
T17; T22 non ha nemmeno la riga. Sono requisiti (semi-)hard non scritti che il
planner umano conosce a memoria. → **Q22 in domande-brevi.md** per la conferma di referente.

**Modellazione (dopo la conferma, o come derivazione golden se la risposta tarda):** per i
100%-fissi la forma naturale è RecurringAvailabilityRule VACATION sui giorni non lavorati
(stessa meccanica di T28) — MODIFICA AI SEED: richiede il permesso esplicito di maintainer
(datagenerator-protection). Interim più prudente senza toccare i seed: PREFERENCE forte
(soft-avoid) derivata dal golden. Il guard golden misura 0 violazioni per costruzione sui
100%-fissi → anche la forma hard sarebbe golden-compatibile per T01/T07/T19.

## CODIFICATO (2026-07-24, autorizzazione maintainer "assumiamo che siano veri")

**19 righe golden-derived aggiunte ad availability_rules.csv** (23→42), nota "Q22
golden-derived" su ognuna: bloccati SOLO i giorni a zero ore golden (T01 lun/mar/mer/
ven; T07 lun/mar/gio/ven; T19 mer/gio/ven; T24 mer/gio/ven; T22 mer/ven;
T12 mer/gio; T17 lun) → il guard golden resta pulito per costruzione. Copia
claudedocs/data sincronizzata (guard byte-identico), guard seed aggiornato (42 righe/12
docenti + conteggio Q22 pinnato). Suite 702/702 EXIT=0. Alla risposta di referente su Q22:
correggere/estendere le righe (eventuali giorni ammessi diversi, i semi-vincoli di
T22-mar/T12-ven/T17-ven da decidere).

**VERIFICA ARCHITETTURA "input data, non statici" (domanda di maintainer): POSITIVA end-to-end.**
`RecurringAvailabilityRuleResource` = CRUD REST completo e ogni mutazione ri-espande subito
le TeacherAvailability concrete (`expandForTeacher`); il guard degli statics
(`forSolver || NOT_SOLVING`) ricarica dal DB a ogni avvio solve → nessun riavvio richiesto.
Il VRP (filtro H5.1 in getAvailableTimeslots) si restringe automaticamente: per T01
~-80% di timeslot candidati, e il difficulty comparator lo piazza per primo (pattern T28).
LACUNE MINORI trovate: (a) CsvImportResource non supporta l'import bulk delle availability
(solo teachers/groups/courses/holidays); (b) verificare la copertura della UI admin per le
RecurringAvailabilityRule (TeacherAvailAdmin gestisce le concrete). Da valutare come
follow-up di questa task.

**Misura collaterale preziosa (dal checkpoint harness):** ricostruendo il run-2 del laptop
sotto le NUOVE regole, hard = **−516**: è quanto le nostre soluzioni violavano i vincoli
impliciti (516 ore dei 7 docenti su giorni ora vietati). I harness artifact-based
(BoxRepairCheckpointTest, discriminatore) ora seedano il mondo pre-Q22 via flag di
RealSeedFixture (l'artifact va giudicato nel mondo in cui fu prodotto).

La parte QUALITÀ GENERALE (limitare i giorni per TUTTI i docenti — la metrica che resta
valida anche dopo aver codificato i casi fissi) resta come sotto:

EXPLORATIVE, golden-first:
1. MISURARE sul golden la distribuzione giorni-lavorati/settimana per docente (script o
   estensione viz: distinct-days G vs N per settimana, per docente) → quali docenti il
   golden consolida e quanto; pinnare la misura.
2. Candidate leve: (a) soft/medium "minimize distinct working days per (teacher, week)"
   generalizzato (attenzione a interazione con M24 early-start e H7 cap giornaliero: il cap
   6h/giorno LIMITA la consolidazione massima: 6h×N giorni); (b) template builder
   teacher-day-clustering (già ordina per docente più vincolato: aggiungere preferenza a
   riusare i giorni già usati dal docente nella settimana template).
3. A/B con metrica dedicata; watch: la consolidazione comprime la capacità mattutina e può
   confliggere con day-start/full-day — misurare il trade.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore + A/B laptop con metrica giorni-docente
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
CODA 2026-07-25: bloccata dalla risposta di referente su Q22 (generalizzazione giorni-fissi ad altri docenti).

VERDICT-412 (2026-08-03): RESOLVED. VACATION rule gia' in seed per 5/6 (T01/T07/T19/T22/T24), run matcha golden (H5-enforced). Solo T12 diverge (Tue vs Fri, flessibile). Q22 per referente in gran parte moot per i 5 (recepiti). T12 DEFER.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Caratterizzato + LARGELY RESOLVED (2026-08-03, read-only). La premessa 2026-07-24 ("run sparso, serve VACATION rule") e' OUTDATED: le RecurringAvailabilityRule VACATION sono GIÀ nel seed per 5/6 candidati (T01 Thu, T07 Wed, T19 Mon+Tue, T22 Mon+Thu, T24 Mon+Tue) e il run le rispetta (concentrazione matching golden, H5-enforced, top-day 100% match). La data-driven derivation proposta e' gia' stata fatta. Residuo: solo T12 diverge (golden Tue, run Fri; disponibilita' flessibile, scelta solver; stesso monte-ore 125h). RACCOMANDAZIONE: CHIUDI 412 (gap risolto per 5/6); T12 DEFER (gated referente: se deve essere Tue, aggiungere VACATION Fri + permesso seed maintainer). Doc: task412-results/VERDICT-412-2026-08-03.md.
<!-- SECTION:FINAL_SUMMARY:END -->
