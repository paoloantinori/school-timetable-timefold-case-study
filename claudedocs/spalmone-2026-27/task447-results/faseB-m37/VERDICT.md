# FASE B (TASK-447) + SPIKE 1 - Sweep peso M37: curva cambi d'aula

Data: 2026-07-29. Harness `RoomPhaseSnapshotTest` export mode, export
gate-finale (`claudedocs/spalmone-2026-27/task446-results/gate-finale/export.json.gz`),
tutti i gruppi (`PLANNER_TEST_ROOM_PHASE_GROUPS=`), budget phase-5 LS 90s, stesso
start (le stanze dell'export) per ogni peso (confronto equo: cambia solo il peso M37).

## Ipotesi (maintainer 29/07 mattino)

M37 a peso 100 lascia troppi cambi d'aula; alzandolo (400, poi 800) la LS room
dovrebbe ridurne il numero, restando sotto H29 (ora hard) che impedisce la
room-null trap.

## Misura (curva completa 100..1500)

Metrica = `countRoomChanges` (coppie adiacenti stessa materia/gruppo/data, stesso
docente, stanza diversa, non-lab). **Validazione**: il "before" (le stanze dell'export
gate-finale) = 124, uguale al M37=124 del run1 di campagna verificato da Fedora; e
lab/non-lab room-null = 251/3 = 254 (= 254=251+3 di Fedora). Il counter replica
esattamente il vincolo M37 di Timefold.

| M37 weight | before | after (90s LS) | medium totale |
|-----------|--------|----------------|---------------|
| 100       | 124    | **106**        | -4.556.772    |
| 200       | 124    | **106**        | -4.567.372    |
| 400       | 124    | **107**        | -4.588.972    |
| 800       | 124    | **106**        | -4.630.972    |
| 1500      | 124    | **106**        | -4.705.172    |

## Risultato: PIATTO. M37 non e' la leva.

Da 100 a 1500 (15x) il conteggio dei cambi d'aula **non scende** (106/107/106,
rumore di traiettoria). Il medium scende monotono ma e' SOLO lo stesso ~106 residuo
che costa di piu: il delta medium 100 -> 1500 = 148.400 = 106 x 1400 (esatto), cioe'
zero residui in meno, solo costo unitario maggiore. I ~106 cambi sono
**strutturalmente forzati** dalla disponibilita' di stanza (H2 conflitto, H-M2 tag,
capienza): la LS room non puo' consolidare oltre perche' bloccata dai vincoli hard.

Guard (full-day, day-start, ricorrenza): l'harness e' room-only (griglia timeslot
fissa dall'export), quindi non puo' misurarli direttamente. MA siccome il conteggio
cambi e' invariante al peso, la distribuzione delle stanze e' di fatto identica tra
i pesi -> non c'e' meccanismo per cui alzare M37 distorca il piazzamento (la LS fa le
stesse scelte di stanza a prescindere dal peso, perche' bloccata dai vincoli hard).
Il rischio "M37 troppo alto distorce il piazzamento pur di non cambiare aula" non si
manifesta qui (e non ci sarebbe nemmeno guadagno).

## Raccomandazione: MANTENERE 100 (non adotto, decide maintainer)

- Nessun beneficio ad alzare M37 (conteggio piatto da 100 a 1500).
- La leva vera per ridurre i cambi d'aula e' **piu' stanze / tag migliori**, non il
  peso: FASE C (de-tag materie teoria) libera stanze lab; i ~251 lab room-null e i
  ~106 cambi forzati sono entrambi sintomo della stessa scarsita' di stanze con il
  tag giusto.
- Default di produzione lasciato a 100 (il knob `planner.m37.weight` e' stato
  aggiunto, default 100, behavior-preserving; serve solo per sweep futuri senza
  rebuild).

## Riproducibilita'

```
for W in 100 200 400 800 1500; do
  PLANNER_TEST_ROOM_PHASE=1 \
  PLANNER_TEST_ROOM_PHASE_EXPORT=claudedocs/spalmone-2026-27/task446-results/gate-finale/export.json.gz \
  PLANNER_TEST_ROOM_PHASE_GROUPS= PLANNER_TEST_ROOM_PHASE_BUDGET=90 \
  PLANNER_TEST_M37_WEIGHT=$W ./scripts/core/test.sh -c RoomPhaseSnapshotTest 2>&1 | grep 'M37 room-changes'
done
```

## Consegna a 3 righe (addendum)

- Ipotesi: alzare M37 (100->400->800) riduce i cambi d'aula.
- Risultato misurato: piatto 106/107/106 su tutta la curva 100..1500; i residui sono
  forzati dalla disponibilita' di stanza (H2/H-M2), non dal peso.
- Raccomandazione: TENERE 100; la leva e' piu' stanze/tag (FASE C), non il peso di M37.
