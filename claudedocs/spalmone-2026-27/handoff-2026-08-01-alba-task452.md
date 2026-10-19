# HANDOFF alba 01/08 (zai) - TASK-452: census deducibilita' + PATCH #1 (ESITO 1) pronta

HEAD `feature/cartesian-block-entity`, pushato con questo commit. Sopra al census commit
`8726283e` (verificato da Fedora). Tutto flag DEFAULT OFF / default golden, nessuna adozione.
DoD completa (/simplify + /code-review high + full test.sh EXIT 0).

## HEADLINE
Confermato il census (Fedora: l'ESITO 1 su `loadAllGoldenProfiles` e' il punto d'oro, unica lettura
golden DEFAULT ON in produzione), ne ho convertito l'esito in **patch pronta opt-in**, default
byte-identical. La patch costruisce il profilo del docente vincolato dai SOLI input (incarichi + H5 +
calendario) dietro knob `source=input`, con un test di EQUIVALENZA sul seed reale che cristallizza
la verifica-finestra (eseguibile solo finche' il golden esiste). Nessun flip: pronta per m-13.

## FATTO (verificato dai raw)
- **Knob `planner.pre-assign.constrained-teacher.source` (golden|input, DEFAULT golden).**
  `HybridPhase1aService.isConstrainedTeacherSourceInput()` letto LIVE come gli altri knob. La branch
  in `runConstrainedTeacherPhase`: `source==input ? buildAllInputDerivedProfiles(...) :
  loadAllGoldenProfiles()`. **DEFAULT golden = byte-identical** (skeptic CONFIRMED: la branch e'
  presa solo se input, `buildAllInputDerivedProfiles` MAI chiamato sul default, la riorganizzazione
  degli hoist e' side-effect-free read-only).
- **`ConstrainedTeacherAllocator` + due metodi puri** (mirror di `plan()`/`loadAllGoldenProfiles`):
  `availableWeekdays(fullDayVacationWeekdays)` = Mon-Fri meno VACATION full-day; `buildInputDerivedProfile(budgetByGroup, availableWeekdays, schoolDates, h7Cap)` = greedy one-group-per-date H7-capped. Nessuna lettura golden, nessun accoppiamento entity.
- **`RecurringAvailabilityRule.isFullDay()`** = single source of truth per la convenzione `0..7`
  (prima hardcodata nei call site).
- **GATE = test di equivalenza** `ConstrainedTeacherInputSourceRealSeedTest` (@QuarkusTest, real
  seed): per T01 confronta profilo input-derived vs golden. Verdi: stessi gruppi (1/2/G10 + 4
  FD), weekday Thu (H5 indipendente), per-group totals, `plan()` pin equivalente (drop-in valido),
  date-delta documentato (input front-loads 8h/giovedi', golden sparge 6h, overlap ~23%).
- **DoD completa.** /simplify: 8 fix (getEffectiveMaxDailyHours al posto del magic 8, isFullDay
  predicate, EnumSet.range, clamp hang-proof `Math.max(1, min(h7Cap,remaining))`, fixture reuse
  RealSeedFixture, drop null-guards, javadoc trim, test extraction helpers). /code-review high: 3
  skeptic (byte-identical CONFIRMED, test REFUTED-su-doc onesto, wiring CONFIRMED + limite H5).
  **full test.sh EXIT 0: 927 test, 0 falliti, 16 skipped.**

## ONESTA' IMPORTANTE (da code-review, skeptic)
Il test di equivalenza e' un **regression-guard del builder + cristallizzazione strutturale**, NON
una prova indipendente di deducibilita'. Due delle tre dimensioni input NON sono indipendenti dal
golden: (a) il BUDGET e' circolare perche' TASK-371 normalizzo' `incarichi.csv` al golden (referente:
golden autoritativo), quindi i totali coincidono per costruzione e il test non puo' catchare un
moderate mis-clamp dell'H7; (b) il WEEKDAY H5 di T01 (Thu) ha provenienza golden (regola Q22
"golden-derived", confirmata da maintainer 2026-07-30). L'unica dimensione genuinamente indipendente dal
golden e' il CALENDARIO (Timeslot), che decide quali giovedi' sono validi. Il verdetto ESITO 1
(sostituibile) regge perche' in produzione il path `source=input` legge input, non golden; ma la
prova che input != golden non e' possibile (sono stati allineati). Documentato onestamente nel test
javadoc e nel census #1.

## COSA DECIDE maintainer (m-13)
- **Flip del default golden->input?** NO ora (patch opt-in, default invariato, nessuna adozione).
  Il flip e' lavoro m-13: richiede un run di verifica che il `source=input` regga sui KPI del solve
  (slack +5h, 3 boxed) mentre il golden e' ancora disponibile, perche' il test prova solo il profilo,
  non l'esito del solve. Il limite H5 latente (il path ignora SICK + finestre effectiveFrom/To; nessuno
  sul seed; `canPinHardConstraints` backstop) va rifinito prima del flip.

## STATO / COME RIPRENDERE
- HEAD pushato. Census + patch + test tutti committed. Default invariato.
- Punto di ripresa: **QUADRO ALBA 01/08** (in cima) + `task452-deducibility-census-2026-08-01.md` +
  `ConstrainedTeacherInputSourceRealSeedTest`.
- Le 4 letture ESITO 2 (#2/#3/#6/#7) restano debito censito (flag-OFF/self-gate, non in produzione):
  i default workbook andrebbero depositati ORA dal golden (finestra aperta). Lavoro tracciato qui +
  TASK-452 note.

## REGOLE STANDING RISPETTATE
- Default invariato, nessuna adozione, verdetti dai raw, tempi assoluti, DoD completa, commit+push.
- Niente codice solver oltre il builder, niente solve. read-only sul resto.
