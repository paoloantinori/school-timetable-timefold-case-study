# STADIO 1 della catena TASK-614/612: SUITE ROSSA, catena FERMATA

Mandato Fedora 14/09 sera (HEAD 42682724, fix TASK-614 (a) sopra (b)). Regola del
mandato: fermarsi allo stadio che fallisce. Stadi 2 (step14 census coda) e 3
(A/B appaiabile ripetuto da /tmp/mandato-ab614.txt) NON eseguiti.

## Misura

```
./scripts/core/test.sh -mva "-Dsurefire.heap=12g"  (laptop, finestra dedicata)
Tests run: 1326, Failures: 9, Errors: 0, Skipped: 65  =>  EXIT 1
```

Log completo: suite-614a-laptop-2026-09-14.log.txt (accanto a questo doc).

## I 9 failure: TUTTI FinalClosingCampaign, tutti "expected 1, was 0"

- FinalClosingCampaignReviewTest 6/9 (modaTolerance, h32ExcludedGroups,
  floorDerived, stagePseudo, emptyingDonorDay, donorLastSlot)
- FinalClosingCampaignTest 1/2 (sameDayPullUpClosesHoleWithoutConflicts)
- FinalClosingCampaignV2Test 2/2 (exhaustedBudgetRejects, crossDayTrade)

Tutti asseriscono closed=1 e ottengono closed=0: dopo la cura (a) FCC non
chiude PIU' NULLA nel mondo fixture.

## Causa (verificata read-only, NON curata)

La cura (a) mette `isSlotLegalFresh(targetTs)` sul pull-up e nel loop candidati
di findDonor. `isSlotLegalFresh` inizia con:

```
List<Timeslot> allTimeslots = SchoolSchedule.getAllTimeslots();
if (allTimeslots == null || allTimeslots.isEmpty() || !allTimeslots.contains(ts)) return false;
```

Le tre classi di test FCC NON pubblicano MAI `setAllTimeslotsStatic` (grep:
count 0 in FinalClosingCampaignReviewTest; pubblicano solo dayFloors e
calendarRules): la statica resta la lista vuota di default, quindi nel mondo
fixture OGNI slot e' VRP-illegale, ogni pull-up/donore viene rifiutato e FCC
chiude 0. La verifica locale di Fedora ("Golden pins verdi 2x") copriva i pin
golden, non queste fixture minimali.

Il path di PRODUZIONE non e' toccato (le statiche sono pubblicate pre-solve e
la cura li' e' corretta: e' la cura indicata dall'audit H5 e dalla verifica
cura-vs-Grid). Le due strade (decisione a Fedora):
1. test-side: pubblicare la griglia statica nei setup delle tre classi FCC
   (SchoolSchedule.setAllTimeslotsStatic(schedule.getTimeslots()) + le altre
   statiche che i filtri consultano: availabilities, quotas, expectedHours,
   pinned map);
2. production-side: far degenerare isSlotLegalFresh in "nessuna restrizione"
   quando la griglia statica e' assente (forma null-safe gia' usata altrove,
   es. isInValueRange VRP null/empty = nessuna restrizione, SurplusHarvest
   v8) - attenzione: cambia la semantica del gate anche in produzione se le
   statiche non sono ancora pubblicate a qualche confine.

## Stato catena

- STADIO 1: ROSSO (EXIT 1, 1326/9/0/65). Fermata come da mandato.
- STADIO 2 (step14 -Dhrf.debug con HRF-TAIL-*): NON ESEGUITO.
- STADIO 3 (A/B appaiabile da /tmp/mandato-ab614.txt): NON ESEGUITO; il
  mandato resta in /tmp sul laptop, pronto al giro dopo la cura dei fixture.
