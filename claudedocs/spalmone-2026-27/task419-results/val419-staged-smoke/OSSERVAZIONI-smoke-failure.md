# val419-staged-smoke: OSSERVAZIONI (smoke FALLITO su qualita, meccanica pulita)

Run: 2026-07-26 09:49 -> 11:35 (~1h46m). Profilo `dev` fresco, `java -jar` con
`-Dplanner.phase-transition-dump.enabled=true` (dump dir custom in questo outdir).
HEAD: f0465bc8 (feat(TASK-419): passo 2).

## Verdetto Fedora: FALLITO su qualita (-897hard). FASE 1 (A/B staged) sospesa; config staged da correggere lato Fedora.

## Meccanica staged: PULITA (gate a-d passano tutti)
- (a) Promossi template -> pinned: **7132** (atteso ~7-9k).
- (b) Authoritative pin set **intact: 2651 -> 2651** (conservazione OK).
- (c) `SELECT count(*) FROM lesson WHERE promotedpin=true` = **0** dopo RELEASE (smarcatura completa).
- (d) Stage C attivo: **4 demozioni** in C-DEMOTION-1 (2x Lingua Italiana G04 / T14, M32w=73; 2x Lab Termoidraulico G03 / T03, M32w=101).

Stage progression (tempi assoluti, da app.log):
- A-CONSTRUCTION: 4s, 12190/12286 placed, score -1221hard.
- PROMOTION: 7132 -> pinned (2651 authoritative untouched, 2503 left free).
- B-SOLVE-1 (~66min, 09:49->10:55): -917hard/-1.879.899medium/-14.561soft, **box=9** (7132 pin tenuti).
- C-DEMOTION-1 (10:55): 4 pin smarcati (7128 ancora tenuti).
- B-SOLVE-2 (~39min, 10:55->11:34): -897hard/-1.737.849medium/+22.357soft, **box=0** (7128 tenuti).
- Accettato as-is: box=0 ma hard=-897 < soglia -2, e selectDemotions vuoto (box=0 -> nessuna smarcatura ulteriore possibile).
- RELEASE (11:34): released 7128 promoted pin; authoritative 2651 -> 2651.

## Qualita: il fallimento e H16b
Score finale: **-897hard/-1.737.849medium/+22.357soft**.
- **H16b (Consecutive same-subject same-teacher same room): -895hard / 895 match. DOMINA.**
- Altro hard: -2hard (2 match).

Causa: i pin promossi fissano il TIMESLOT (cella template) ma la ROOM e una variabile
separata assegnata in fase 5/6 (CH room + refinement). I blocchi same-subject-same-teacher
finiscono in stanze diverse -> H16b. Il pinning rigido del timeslot strozza la consistenza
di stanza dei blocchi. val408 (no staged) aveva H16b ~0. Da correggere lato config staged
(es. promuovere pin comprensivi di room, o esentare H16b, o pinnare solo blocchi room-consistent).

## La metrica ricorrenza: il guadagno c'e (ma a prezzo di H16b)
- Ricorrenza **UNPINNED: 74,6%** (target >=70 MET; val408 52,8/53,6%; **+22pp**).
- Ricorrenza TOTALE: 76,0% (golden stretta 41,5%).
- Ricorrenza PINNED: 81,2%.
- Full-day: **79,9%** (val408 79,9/80,0; golden 85,4%) -> **nessun crollo**.
- Per-gruppo unpinned: range 63,1% (G12) - 81,3% (G02); G11 63,6%, G14 69,6%.

## Note tecniche
- `api.sh wait` rc=1 **BENIGNO**: la wait fa `fetch status || exit 1` ad ogni poll; un
  hiccup di fetch al finalizzazione (RELEASE) fa uscire rc=1 DOPO che il ciclo era gia
  completato (cycle finished 11:34:50 -> released 11:34:55 -> rc=1 11:34:56).
  conservation intact, promotedpin=0, capture-run con score finale: il ciclo e terminato
  correttamente.
- Phase-transition dumps: **10 file** in `phase-transitions/` (phase-2..phase-6 + -boxed),
  flag funzionante. capture-run dice "none" perche cerca nel path default
  `target/phase-transitions`, non nel dir custom. I dump sono **OVERWRITTEN ad ogni
  iterazione B** -> riflettono l'ultima (B-SOLVE-2).
- `export.json.gz` (146B) e solo il messaggio di stato di `api.sh export` (l'export reale
  20M e andato in /tmp); `lessons.txt` ha i dati completi per i KPI.
