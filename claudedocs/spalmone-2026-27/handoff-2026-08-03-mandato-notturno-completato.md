# HANDOFF 2026-08-03 (zai). Mandato notturno COMPLETATO (3/3).

> Riprendi da qui dopo /clear. HEAD `feature/cartesian-block-entity` (tutto pushato). I tre mandati
> del programme notturno sono chiusi e verificati avversariamente dove portanti. Regole standing
> invariate (verdetti dai raw, commit+push, tempi assoluti, handoff+/clear a ~450k, mai fermo,
> NESSUNA adozione: source resta golden, GRASP opt-in OFF, multi-start resta PROCEDURA operativa).

## QUADRO (stato generale)

- **MANDATO 1 (frequenza bad-seed) - DONE.** Batch n=8 GRASP + 1 baseline, source=input, profilo
  `multistart`. **p(0-hard) STRICT = 0.750 (k=6/n=8), Wilson95 [0.409, 0.929]**; lettura
  STRUCTURALE (no-H17) = 0.875 [0.529, 0.978]. N raccomandato P(>=1 0-hard)>=0.95: **N=3 point
  (~85 min sequenziali), N=6 conservativo (~170 min)**, wall medio 28 min/run. **Due bacini
  cattivi DISTINTI**: seed-1 (-10, H17 strutturale G11 mercoledi, il bacino saturo noto) +
  seed-6 (-1, H29 room-tier: 1 lezione meccanica senza aula per saturazione del tag, 1 stanza
  R15 occupata da G13, 0 stanze libere = territorio M-TC, NON residuo rifinitura).
  Verdetto: `task480-results/badseed-frequency/VERDICT-badseed-frequency-2026-08-03.md`.
- **MANDATO 2 (Kempe TASK-482) - DONE, NO verificato.** 0/6 sustainable. Una Kempe-chain chiude il
  buco @10 ma ogni chain introduce 1 nuovo H3 (peso 1000): le lezioni VRP-bloccate sono ostacoli
  immobili che spezzano la conflict-closure. TASK-482 chiuso NO con evidenza meccanica. Verdetto:
  `task482-results/design-kempe-chain-2026-08-03.md`.
- **MANDATO 3 (sensibilita' stage) - DONE, MEDIO verificato.** Capacita' aggregata sufficiente in
  ogni scenario (anche stage->50h); M28 diventa collo di bottiglia se le ore tolte allo stage
  diventano nuove lezioni; domanda chiave a referente (nuovi insegnamenti o monte cala?). 8 gruppi
  stage / 2309h reali (non 7/2092: G04 ha 224h non contati). Doc:
  `sensibilita-riduzione-stage-2026-08-03.md`.

- **Decisioni invariate (a nome di maintainer, da segnalare):** source resta golden, GRASP opt-in OFF,
  multi-start resta PROCEDURA (NON default flip), ExactFinisher DEFAULT ON. Il bacino room-tier di
  seed-6 mappa su TASK-455 (M-TC prototipo default-OFF): nessun nuovo task, ma l'evidenza
  (1 stanza meccanica, 2 lezioni -> -1hard) sostiene il caso M-TC se la scuola conferma scarsita'
  strutturale del tag. NESSUNA cura del room-null implementata (non funzionerebbe: 0 stanze libere).
- **App DOWN** (batch terminato col kill-app dell'ultimo run). Profilo `multistart` con gli 8 run
  catturati (pre-assign source=input + stage pins persistono). Per ripristinare production:
  `./planner.sh stop && ./planner.sh start production`.

## COSA E' STATO FATTO (mandato 1 in dettaglio, gli altri nei rispettivi doc)

1. **Tooling prima del batch.** `scripts/analyze/badseed-frequency-stats.py` (Wilson CI + N
   raccomandato, validato contro i raw TASK-480: riproduce p_hat=0.667 CI [0.208,0.939]
   byte-exact; bug centro-Wilson-vs-MLE catturato dallo smoke test e corretto).
   `multistart-ab.sh` parameterizzato via `RESULTS_DIR` (backwards-compatible) per routare
   l'output in `badseed-frequency/` senza distruggere i raw TASK-480.
2. **Batch 9 run.** Setup fresh validato (`deletedPriorPins:0`, 10.686 pinned, anti-bug md5
   distinti + banner `grasp=%b seed=%d`). Baseline 0hard/-1043809medium (combacia TASK-480).
   SUMMARY auto-generato corretto (bug conteggio TASK-480 assente: baseline contato separatamente).
3. **Composizione dei bacini cattivi (dato portante).** Estratta da analysis.json + export.json.gz
   PRIMA della statistica: seed-1 = H17-9 (tutti G11) + H29-1; seed-6 = H29-1 SOLO.
4. **Drill causale seed-6.** Stanza ammissibile (H-M2 tag meccanica + assignable): solo
   R15. A 2026-11-04T13:00: 2 lezioni meccaniche (G04 room-null + G13 che ha la
   stanza). Outcome (a) saturazione, 0 stanze libere, territorio M-TC. Ipotesti (b) confutata.
5. **Statistica due letture.** STRICT (operativa) + STRUCTURALE (borne superiore), con N e costo
   in minuti. Batch non esteso (CI_lo strict 0.409 < 0.5 ma la raccomandazione regge e +2-4 run
   non porterebbero CI_lo>0.5).

## DEBITO RESIDUO (nessun bloccante)

- **Co-presenza G04+G13 (seed-6):** non verificato se e' coppia praticata dal golden (depth
  >= 2). Se lo fosse, un'esenzione H2 potrebbe co-locarli e recuperare il room-null di seed-6:
  cambierebbe la lettura STRUCTURALE da "room-saturation non recuperabile" a "recuperabile via
  co-presenza". Indagine read-only differita, non blocca il verdetto.
- **Speedup N-container (multi-start parallelo):** non costruito. N=3 parallelo ~30 min vs ~85
  sequenziale. Documentato come "NEXT" nello script. Da costruire SOLO se la scuola adotta la
  procedura multi-start in produzione (oggi e' opt-in).
- **p piu' stretto:** n=8 CI larga; per CI_lo>0.5 servirebbe n~16. Non urgente: la raccomandazione
  (N=3-6) regge su tutto l'intervallo.
- **M-TC (TASK-455):** l'evidenza seed-6 sostiene il caso per l'adozione se la scuola conferma la
  scarsita' strutturale del tag meccanica. Prototipo default-OFF esiste gia'.
- **Mini-bug tooling:** `badseed-frequency-stats.py` non risolve i path relativi contro ROOT (a
  differenza di capture-run.sh); usare path assoluti o fixare (una riga: `OUT = ROOT/arg if relative`).
- **Bug classificazione Kempe test:** la condizione `h17d < 0` nel test tratta "H17 peggiorato"
  come il caso sustainable (segno rovesciato); MOOT perche' ogni chain fallisce su ttier!=0 (H3),
  ma vale la pena correggerlo se si riapre TASK-482 (chiuso NO).

## PUNTATORI

- Mandato 1 verdetto: `task480-results/badseed-frequency/VERDICT-badseed-frequency-2026-08-03.md`
  + raw `task480-results/badseed-frequency/{baseline-grasp-off,grasp-seed-1..8}/` + SUMMARY.txt.
- Mandato 2: `task482-results/design-kempe-chain-2026-08-03.md` + test
  `src/test/java/com/school/planner/bootstrap/KempeChainH17DiscriminatorTest.java`.
- Mandato 3: `sensibilita-riduzione-stage-2026-08-03.md`.
- Stats helper: `scripts/analyze/badseed-frequency-stats.py`.
- Handoff precedenti: alba9 (TASK-479 completato), alba8 (TASK-480 done), alba7 (A/B in corso).
- Commit: 7191710c (RESULTS_DIR), b132476b (stats helper), 8dba6a93 (stage), 5354ed85 (Kempe),
  + questo handoff e il verdetto badseed.
