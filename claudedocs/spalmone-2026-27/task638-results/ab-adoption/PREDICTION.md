# PREDICTION pre-registrata TASK-638 A/B adozione (23/09 notte, PRIMA del lancio)

Braccio A task638-off (flag assente) vs braccio B task638-on
(`-Dplanner.construction.violation-steepest.enabled=true`). Entrambi: profilo
fresco, attempt 1 deterministico + multi-start race, MT-LS ON, heap 8g, engine
rc-1, checkpoint capture ON (opt-in passato: la lezione 637), capture-run
completa. Criteri: il mandato (5, pre-registrati).

Predizione:

1. Hard (criterio 1): ON mai peggiore di OFF PER COSTRUZIONE (oracolo
   improve-or-noop full-hard + shell catch/restore: il worst case del braccio
   ON e' la consegna invariata). Entrambi i bracci in [0 .. -3] (race 628).
   Il caso piu' probabile: entrambi 0-hard al tentativo 1 e il finisher
   applied=0 (no-op su residuo vuoto: risultato VALIDO, criterio 4 lo dice).
2. Ricorrenza (criterio 2): >= 68 entrambi (banda 68-71 dei bracci freschi
   recenti; il finisher muove pochissime lezioni e solo se migliora hard:
   impatto ricorrenza ~0).
3. Full-day (criterio 3): >= 93 entrambi (banda 94-95).
4. Banner (criterio 4): ON "enabled=true" al boot + riga [delivery] con
   applied e hard prima/dopo (anche applied=0); OFF "enabled=false" e NESSUNA
   riga [delivery].
5. Sanity (criterio 5): nessun rosso NUOVO in ON (confronto per-gruppo; i
   rossi noti delle famiglie giorni-corti/buchi possono esserci in entrambi).

Rischio dichiarato: nessuno strutturale (il pass e' no-op-arbitrato); l'unica
incertezza e' la race hard tra bracci, che il criterio 1 assorve per
costruzione. Nessun flip: la ratifica e' di maintainer.
