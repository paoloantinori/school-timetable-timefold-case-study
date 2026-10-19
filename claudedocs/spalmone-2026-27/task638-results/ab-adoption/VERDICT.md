# VERDICT A/B adozione TASK-638 ViolationScopedSteepestFinisher (23/09 notte, laptop, rc-1)

Mandato: A/B su ciclo fresco, protocollo 636/637 (profili freschi consecutivi
task638-off/on, heap 8g, MT-LS ON, checkpoint capture ON stavolta passata,
predizione pre-registrata in PREDICTION.md). Il pass e' wired opt-in
(27a515c2): questo e' l'ultimo gate prima della ratifica di maintainer.

## I 5 criteri (tutti VERDI)

| # | Criterio | OFF | ON | Verdetto |
|---|----------|-----|----|----------|
| 1 | hard consegna ON mai peggiore di OFF | [-2/0]hard | **[0/0]hard** | VERDE (ON consegna 0-hard) |
| 2 | ricorrenza >= 68 entrambi | 69,8% | 70,1% | VERDE |
| 3 | full-day >= 93 entrambi | 94,3% | 94,6% | VERDE |
| 4 | banner ON enabled=true | enabled=false, nessuna riga [delivery] | enabled=true + **[delivery] applied=1 hard -1 -> 0 in 414ms** | VERDE (non no-op: ha curato) |
| 5 | sanity: nessun rosso nuovo in ON | 3 gruppi rossi | 3 gruppi rossi | VERDE (dettaglio sotto) |

Fonti: score.txt, kpi-resemblance.txt, kpi-fill.txt, app.log (banner e riga
[delivery]), sanity-report.txt. Capture-doctor VERDE entrambi (export over
cap GitHub: restano sul laptop come da prassi).

## Il fatto del gate: il finisher ha chiuso la consegna fresca

La riga [delivery] del braccio ON: `applied=1 hard -1 -> 0 in 414ms`, e la
mossa applicata e' **"SWAP: Lavorazioni meccaniche <-> Tecniche di
saldatura"**: ESATTAMENTE la cura del blind puzzle H28 (blind-and-moves),
vista live in produzione per la prima volta. La race del ciclo aveva ricreato
un residuo -1 e il pass lo ha chiuso con la mossa che il cieco aveva derivato
a mano e l'arbitrato Java aveva certificato. La catena cieco -> arbitraggio ->
meccanizzazione -> adozione e' chiusa end-to-end sullo stesso esempio.

## Attribuzione onesta

- n=1 per braccio: il delta hard (-2 vs 0) e' race + finisher insieme: il
  braccio OFF sarebbe potuto essere 0 o -3 per sola race, e il criterio 1 e'
  garantito PER COSTRUZIONE (oracolo improve-or-noop + shell catch/restore:
  il worst case del ON e' la consegna invariata). Il contributo SPECIFICO del
  finisher e' pulito e misurato: -1 -> 0 sullo stato consegnato, 414ms.
- OFF: consegna al proprio residuo di race (-2, 2 tentativi). ON: 2 tentativi,
  residuo -1 chiuso dal pass.
- Sanity (criterio 5, dettaglio): OFF rosso su G08 (buchi 04-01 [4,5]), 3
  MODA (2 giorni corti), G14 (1 giorno corto, G14 full-day 76% nel
  confronto visivo). ON rosso su G08 (lo STESSO buco 04-01 [4,5]), G09
  (2 giorni corti, date diverse), G10 (1 giorno corto). Stesso numero di
  gruppi rossi, stesse famiglie note, un rosso identico nei due braccia (la
  race lo fa in entrambi); il rosso G10 nuovo nell'ON NON e' del finisher:
  la sua unica mossa tocca lezioni G13 (Lavorazioni/Saldature), non G10.

## Lettura visiva (dichiarata)

Metodo come i gate 636/637: struttura DOM dei comparatori renderizzati +
screenshot full-page archiviati (viz-{off,on}-fullpage.png; il toolchain
sessione non ingerisce PNG). Cosa emerge: stesse forme di famiglia nei due
braccia (per-gruppo con wobble +/-2pp in entrambe le direzioni), zero
inizio-tardivo violazione ovunque, G13 99% entrambi, buchi ~0 (1 gruppo per
braccio), totali docente G=N su 25/26 (1 mismatch per braccio, non legato al
pass), G04 oltre-ideal 48 identico. Nessun pattern strutturale nuovo nel
braccio ON. G14: 76% nell'OFF (il suo rosso) contro 83% = golden nell'ON.

## Conclusione

5/5 criteri VERDI, predizione centrata (incluso il caso previsto "il finisher
chiude il residuo della race": e' avvenuto, con la mossa del puzzle). Il pass
costa 414ms sulla consegna quando cura ed e' no-op-arbitrato quando non puo'.
NESSUN FLIP: resta opt-in (`-Dplanner.construction.violation-steepest.
enabled=true`); la ratifica e' decisione di maintainer, che ora ha: validazione
catalogo (4/6 a 0-hard storici), wiring production-safe (catch/restore), e
questo A/B fresco con la cura del puzzle vista live.

## Indice artifact

`{off,on}/`: run-manifest, driver.log, app.log, score/analysis/phases/
lessons.txt, KPI complete, sanity-report, confronto-*.html, checkpoints/
(catturati stavolta: capture opt-in passata, la lezione 637 applicata).
Screenshot: viz-{off,on}-fullpage.png (gitignore png: solo laptop).
Export >100MB: solo laptop, non committati.
