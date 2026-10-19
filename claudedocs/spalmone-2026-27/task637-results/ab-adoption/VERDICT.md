# VERDICT A/B adozione TASK-637 theory-spread (PASSO 4 catena autonomia, 22/09)

Mandato: A/B su Timefold 2.7.0-rc-1, due profili freschi consecutivi, heap 8g, MT-LS ON,
criteri pre-registrati (GATE-CRITERI.md, 21/09), predizione scritta prima del lancio
(PREDICTION.md). Nessun flip di produzione: la decisione di adozione e' di maintainer.

## Setup (meccanico, dai manifest)

- Braccio A OFF: profilo `task637b-off`, flag assente. Braccio B ON: profilo `task637b-on`,
  `-Dplanner.construction.theory-spread.enabled=true` sulla riga `java -jar` (verificata
  nel processo: `pgrep -f quarkus-run.jar` mostra il flag).
- Entrambi: git f2d745d0 (src/pom identici ad a6e2bebd, verificato con git diff vuoto),
  jar rebuildato sul posto dopo aver trovato il jar laptop stantio pre-rc-1 (21/09 22:14),
  `PLANNER_HEAP=8g`, harness run-measure.sh (preflight launch-preflight VERDE,
  manifest con predizione obbligatoria, capture-run + capture-doctor a valle).
- Banner al volo verificato PRIMA del solve in entrambi i bracci
  (`Construction/repair flags ... theory-spread=false/true ... grasp=false seed=0`):
  il flag e' arrivato alla JVM in entrambe le direzioni.
- MT-LS ON in entrambi (`Multi-threaded LS: enabled=true, threads=8, dispatch=single-writer`).

## I 5 criteri (tutti VERDI)

| # | Criterio | OFF | ON | Verdetto |
|---|----------|-----|----|----------|
| 1 | hard consegna ON mai peggiore di OFF | [-3/0]hard | [-1/0]hard | VERDE (ON migliore di 2) |
| 2 | ricorrenza unpinned >= 68 entrambi | 70,3% (8631/12276) | 70,0% (8589/12277) | VERDE |
| 3 | celle teoriche 2+ same-day (M-TR) ON < OFF | 1606 celle (residuo 1662) | 1402 celle (residuo 1455) | VERDE (-204 celle, -12,7%; residuo -207) |
| 4 | full-day >= 93 entrambi | 94,7% | 94,6% | VERDE |
| 5 | banner ON armed=true theoryHoldFills>0 | armed=false, fills 0 (tutte le righe) | armed=true, fills 139 alla prima costruzione, 111-155 nel burst GRASP | VERDE |

Fonti grezze: score.txt per braccio; kpi-resemblance.txt; kpi-mtr.txt (theory_adjacent_report.py
mode=stretta sui lessons.txt consegnati, classificazione single-source col vincolo M-TR);
kpi-fill.txt; app.log (righe census `Theory-spread tail`). La predizione e' stata centrata
su tutti e cinque i punti, incluso il rischio ricorrenza che NON e' morso (-0,3pp).

## Attribuzione onesta

- n=1 per braccio. La differenza hard (-3 vs -1) sta DENTRO la variabilita' race delle
  consegne fresh-profile (precedente 636: "differenza hard dentro la varianza race").
  Il criterio 1 e' verde ma il delta non va letto come "il lever cura 2 hard".
- Protocollo simmetrico: ENTRAMBI i bracci hanno consegnato al tentativo 2 del multi-start
  (attempt 1 deterministico ~63-69 min senza 0-hard; attempt 2 grasp=true con discard-loop
  `maxDiscards=32`, consegna finale dal seed 32 in entrambi). Le costruzioni GRASP del
  braccio ON applicavano il lever a ogni seed (fills 111-155), quindi il confronto e'
  grasp-attempt-2 ON vs grasp-attempt-2 OFF, appaiato per struttura.
- Medium: ON -1.172.413 vs OFF -1.237.587 (ON migliore di ~65k; M-TR e' OFF in entrambi
  i bracci, quindi non e' punteggio del vincolo: e' qualita' generale della consegna).
- Wall: ~83 min (OFF) e ~88 min (ON) a heap 8g, entrambi 2 tentativi.

## Sanity (dichiarata, NON conforme entrambi, non e' criterio)

OFF: 2 gruppi rossi (G09 1 giorno sotto min 2h il 13/04; G10 1 giorno sotto min 4h
il 03/03). ON: 1 gruppo rosso (G08 1 giorno con 2 buchi interni il 01/04). Rossi
diversi per braccio, famiglie note (giorni corti H33/pool-cap, buco interno), nessuna
famiglia nuova. ZERO inizio tardivo (violazione) in entrambi.

## Lettura visiva (dichiarata)

Regola "consumare l'output visivo": screenshot full-page archiviati (`viz-off-fullpage.png`,
`viz-on-fullpage.png`). NOTA DI METODO ONESTA: il toolchain di questa sessione non ha
potuto ingerire i pixel PNG (Read li carica su CDN senza mostrarli), quindi la lettura e'
fatta sulla STRUTTURA DOM RENDERIZZATA dei comparatori (le 4 viste: barre riassuntive per
gruppo, heatmap occupazione celle, fingerprint docenti settimanale, anatomia inizio giorno),
che contiene gli stessi valori che l'occhio leggerebbe; gli screenshot restano per
l'occhio di maintainer. Cosa emerge: forme della stessa famiglia nei due bracci (full-day
per-gruppo con wobble +/-2pp in entrambe le direzioni, G04 oltre-ideal 48 identico,
buchi interni quasi ovunque 0); nessun pattern strutturale NUOVO nel braccio ON; i totali
docente G=N esatti su tutti e 26 i docenti in ENTRAMBI i bracci; zero inizio tardivo
violazione ovunque. La firma attesa del lever (meno colonne doppie materia-teoria) non e'
visibile a questa altitudine del comparatore: vive nel numero M-TR (criterio 3).

## Gap dichiarati

1. CHECKPOINT NON CATTURATI: `planner.checkpoint.capture.enabled` e' OPT-IN (default OFF
   nel codice, forma letterale "true"), i driver non lo hanno passato: le directory
   `{off,on}/checkpoints/` sono VUOTE e nessuna riga checkpoint esiste nei log (verificato).
   Gli Stati consegnati SONO preservati dagli export (`export.json.gz`, 112-113MiB);
   perduti gli snapshot di confine intermedi di questi due bacini. Process lesson: la
   frase CLAUDE.md "attivo di default in ogni ciclo di misura" descrive la direttiva
   operativa (passarlo sempre), non il default del codice.
2. EXPORT OVER CAP: export.json.gz > 100MiB (warning del capture-doctor): restano sul
   laptop, NON committati; puntatore qui. OFF: `ab-adoption/off/export.json.gz` (113M),
   ON: `ab-adoption/on/export.json.gz` (112M).
3. Sanity NON CONFORME in entrambi i bracci (sopra): dichiarata, in linea con la serie
   storica dei bracci A/B, non bocciatura specifica del lever.

## Conclusione

5/5 criteri VERDI, predizione centrata, costo zero sugli assi di guardia (ricorrenza,
full-day), firma M-TR visibile (-204 celle). NESSUN FLIP: il default resta OFF
(opt-in `planner.construction.theory-spread.enabled`); l'adozione e' decisione di maintainer
su questo VERDICT. Materiale per la decisione: il lever e' una preferenza-mai-forza che
non ha pagato nulla sugli assi di guardia e ha ridotto il residuo M-TR del 12,7%; il
rischio ricorrenza della famiglia 599/636 non si e' materializzato (-0,3pp).

## Indice artifact

- `PREDICTION.md` (pre-registrata), `GATE-CRITERI.md` (pre-registrati 21/09), `driver.sh`,
  `master.sh` (i driver eseguiti, con la lezione del banner-check dentro).
- `{off,on}/run-manifest.json`, `driver.log`, `app.log` (force-add: il .gitignore
  `*.log` li escluderebbe, ma sono la provenza delle righe banner e della timeline
  tentativi; precedente i log oom 21/09), `score.txt`, `analysis.json`,
  `phases.json`, `lessons.txt`, `kpi-*.txt` (incluso `kpi-mtr.txt`, generato adesso),
  `sanity-report.txt`, `confronto-*.html`, `day-start-anatomy.html`,
  `phase-markers-log.txt`.
- SOLO laptop (gitignore `*.png`): `viz-off-fullpage.png`, `viz-on-fullpage.png`
  (raster dell'HTML gia' committato). SOLO laptop (over cap): gli `export.json.gz`.
- La prima (fallita) tirata del master ha lasciato nel driver.log off le righe del lancio
  abortito per banner-check: trattenute intenzionalmente come provenance.
