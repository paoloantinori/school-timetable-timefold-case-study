# TASK-472 perimetro residuo - classificazione golden-first di 5 regole (2026-08-01)

Zai, 2026-08-01. Estensione della chiave di lettura TASK-472 (pilota
`no-2h-teoria`) a 5 regole del perimetro non ancora classificate. Per ciascuna:
predicato preciso sul golden, conteggio violazioni golden, verdetto di tier
(golden-first: violazioni > 0 vieta l'HARD a copertura totale, perche' renderebbe
il golden infeasible), nota di composizione vs vincoli H/M esistenti.

Criterio governante (CLAUDE.md "Golden-first recalibration"): la scuola considera
VALIDO l'orario golden, quindi nessuna nostra regola puo' essere piu' rigorosa
della pratica che il golden incarna. Una regola violata N>0 volte dal golden non
puo' essere HARD.

## Strumento (ripetibile)

`scripts/analyze/perimetro_residuo_report.py` - report read-only, sibling di
`theory_adjacent_report.py` (il pilota TASK-472). Legge il golden
`src/main/resources/seed/lessons.csv`, filtra le trappole (sabati off-grid,
docente Fasullo/Fasutto segnaposto, Stage off-grid), e per ognuna delle 5 regole
emette: predicato, conteggio violazioni, clustering, verdetto di tier.
Output golden: `task472-results/perimetro-residuo-golden-report.txt`.

Eseguibile con: `python3 scripts/analyze/perimetro_residuo_report.py`.
I numeri nel documento sono l'output di quello script (10.542 lezioni golden
post-filtri).

## Sintesi (i 5 triples)

| # | regola | tier proposto | violazioni golden |
|---|--------|---------------|-------------------|
| 1 | Termoidraulica "prime 4 ore" | MEDIUM (nuovo candidato) | 78 (25% di 313h) |
| 2 | Sicurezza "1a / 2 giorni" | NON-MODELLARE / al piu' SOFT | 4/4 gruppi (100%) |
| 3 | T21 mid-day | NON-MODELLARE / al piu' SOFT | 31 (slot0 su G08+G12) |
| 4 | M24/M25 residual | MEDIUM HOLDS (nessun cambio) | 43 (M24=16, M25=27) |
| 5 | M29 discrepanza | MEDIUM HOLDS (confermato) | 27/38 date (71%) |

Sanity check golden-first: **nessuna delle 5 regole e' proposta HARD**, perche'
tutte e 5 hanno violazioni golden > 0. L'invariante regge: nessun HARD proposta
viola il golden.

---

## R1. Termoidraulica "prime 4 ore"

**Predicato.** Una lezione di `Laboratorio Termoidraulico` a slot >= 4 (12:00+)
violerebbe la lettura "prime 4 ore = slot 0-3 (08-12) tassativo" del workbook
(bucket B, tracciato). Lettura alternativa "blocco mattutino 8-13" = slot 0-4:
in quel caso solo slot 5 (13-14) viola.

**Golden.** 313h totali; 235 (75%) a slot 0-3 (08-12), 78 (25%) a slot 4-5
(12-14), 0 al pomeriggio. Distribuzione: slot0=43, slot1=63, slot2=65, slot3=64,
slot4=58, slot5=20. Block-start: 43 blocchi iniziano a slot0, 20 a slot1, 2 a
slot2, 1 a slot3, 2 a slot4. **Violazioni (slot >= 4): 78 (25%)**. Sotto la
lettura alternativa "8-13" (solo slot 5): 20 violazioni (6%).

**Tier: MEDIUM (nuovo candidato).** Golden-first: il golden viola la lettura
"08-12 tassativo" 78 volte, quindi l'HARD e' vietato. Ma il 75% del lab e' al
mattino e la distribuzione e' coerente con blocchi lab di 5h (8-13), non con una
regola rigida sugli slot 0-3. MEDIUM e' il registro giusto: incoraggia il
mattino senza vietare il 12-14 che la scuola pratica. Da chiarire con referente se
"prime 4 ore" = slot 0-3 (tassativo, 78 viol) o "blocco 8-13 mattutino" (solo 20
viol). Non bloccante: la forma emerge gia' dai pin/quote (M30/M31 lab-day).

**Composizione.** Nessun vincolo esistente copre questo placement; non c'e' clash
con H-M2 (lab room-tag), M30/M31 (lab-day quota/contiguity) o H7 (max ore
docente). Un eventuale nuovo M sarebbe un layer additivo sul when, non sul where.

## R2. Sicurezza "1a / 2 giorni"

**Predicato.** Workbook riga 42: "2 gg di sicurezza 4 ore con le 1e". I giorni
cumulativi di `Sicurezza` (subject, `BaseConstraintProvider.SICUREZZA_SUBJECT`)
per ciascun gruppo prima-anno (G03/G02/G01/G04) devono essere <= 2. Uno
span > 2 giorni viola. (Regola DIVERSA da M26 "Sicurezza before stage": quella e'
sul date-vs-stageStart, questa sul concentration-in-2-days.)

**Golden.** Tutti e 4 i gruppi prime-anno: 8h di Sicurezza spalmate su **8 giorni
distinti** ciascuno (cadenza 1h/settimana: G03/G01/G04 lunedi', G02
mar/ven). Mai concentrata in 2 giorni. **Violazioni: 4/4 gruppi (100%)**.

**Tier: NON-MODELLARE / al piu' SOFT.** Golden-first: il golden la viola al 100%
su tutti i gruppi. HARD vietato, e anche MEDIUM combatterebbe la cadenza 1h/settimana
che e' pratica golden consolidata. La regola legge aspirazionale/non-applicata
quest'anno. Da notificare a referente (T5): la regola e' reale (e quest'anno non
rispettata) o un appunto non applicato? Non si modella finche' non e' confermata:
modellarla oggi metterebbe una regola che contraddice 32 ore di pratica golden.

**Composizione.** Ortogonale a M26 (Sicurezza before stage): M26 guarda la data
relativa al stageStart, questa guarda il numero di giorni cumulativi. Nessun
clash. M21 (max 2h same subject) e' inerte qui (Sicurezza e' 1h/giorno).

## R3. T21 mid-day

**Predicato.** Workbook bucket C2 / T3: "T21 t30 -> ore di mezzo, non
prime ore". Una lezione di T21 a slot 0 (08:00, prime ore) viola. Scope:
i 2 gruppi nominati dalla regola (G08, G12).

**Golden.** G08: 42h, slot0=26 (62%), distribuzione slot0=26, slot1=9, slot2=5,
slot3=2. G12: 40h, slot0=5 (12%). **Violazioni slot0 totali: 31** (26 + 5).
La distribuzione G08 e' l'opposto di "ore di mezzo": pesantemente a prime ore.

**Tier: NON-MODELLARE / al piu' SOFT.** Golden-first: 31 violazioni, di cui 26
su 42 (62%) per G08. La pratica golden contraddice la regola. HARD vietato, e
anche MEDIUM sarebbe una regola che combatte il golden. Da notificare a referente
(T3): T21 e' davvero "T30"? La regola e' reale (e quest'anno non
rispettata) o e' un appunto non applicato? (Non si modella finche' non e'
confermata: today metterebbe una regola che contraddice 26 ore di pratica golden.)
Se confermata reale, al piu' SOFT, e G08 e' il carve-out da guardare (62% slot0).

**Composizione.** Nessun vincolo strutturale danno' slot0 (M24 T11 e'
per-docente). Ma un vincolo "T21 lontano da slot0" combatterebbe M18
(day-start anchoring: il solver viene premiato per iniziare i giorni a slot0),
creando tensione. Da valutare solo dopo conferma referente.

## R4. M24/M25 residual

**Predicato M24.** T11 (`maxEarlyStartDaysPerWeek=1`): piu' di 1 giorno
distinto a settimana ISO con lezione a slot0 (08:00) viola. Residual = eccesso
di giorni slot0 oltre il cap.

**Predicato M25.** T05 (`requiredFreeDaysPerWeek=2`): meno di 2 giorni liberi
Mon-Ven a settimana ISO (i.e. insegna in >3 giorni) viola. Residual = shortfall
sotto il requisito (flat 5-weekday span, M25's own simplification).

**Golden M24.** 36 settimane attive, 15 settimane violate (>1 giorno slot0),
residual = **16** giorni slot0 in eccesso (esattamente "M24 -16" in CLAUDE.md).

**Golden M25.** 38 settimane attive, 27 settimane violate (free < 2), residual =
**27** shortfall (esattamente "M25 -27" in CLAUDE.md). T05 insegna 4 giorni/sett
in 27 settimane (solo 1 giorno libero, il venerdi' gia' in VACATION).

**Tier: MEDIUM HOLDS per entrambi (nessun cambio).** Gia' medium, gia'
residual-calibrated (CLAUDE.md TASK-368). Il golden li viola entrambi (M24 -16,
M25 -27), quindi HARD e' vietato per golden-first. Il residual e' la pratica
della scuola, non un fallimento del solver: il golden stesso lo produce. Nessun
tweak di peso consigliato: ONE_MEDIUM corrisponde alla preference scale di M24/M25;
alzare il peso combatterebbe la pratica golden. Tier e peso correnti confermati.

**Composizione.** Entrambi gia' registrati e attivi. M24 raggruppa per (docente,
settimana ISO) con filtro slot0; M25 con ogni lezione. Nessun clash con H7 (max
ore docente) o M18 (day-start). La flat-5-weekday-span di M25 e' una
semplificazione documentata (settimane con chiusura scolastica hanno <5 giorni
reali), accettata come semplificazione medium-tier.

## R5. M29 discrepanza

**Predicato.** M29 (`professionalizzantiAtDayEdges`): in un giorno restricto per
G12 (Mer/Gio, `M29_RESTRICTED_DAYS_BY_GROUP`), le professionalizzanti NON-LAB
di quel giorno devono formare UNA sequenza contigua di <=2 slot che tocca il
primo o l'ultimo slot calendario-aperto del gruppo quel giorno. Finestra
calendario (empirica dal golden): Mer aperto 0-4 (last=4), Gio aperto 0-7
(last=7). `isLegalProfEdgeRun` in `SchoolScheduleConstraintProvider`.

**Golden.** G12 professionalizzanti non-lab su Mer/Gio: Mer 26 date (10
legal-edge, 16 ILLEGAL 62%), Gio 12 date (1 legal-edge, 11 ILLEGAL 92%).
**Violazioni: 27/38 date (71%)**. I pattern illegali: Mer slot {0,3}/{0,4}
(non-contigui), Gio slot {0,2,3}/{0,1,2,3} (span >2 o non-contigui).

**Nota runtime.** M29 scoreggia 0 sul golden IMPORTATO a runtime, perche' il
golden e' import 100% pinned e M29 ha esenzione DAY-LEVEL pinned (un giorno e'
esente solo se TUTTE le professionalizzanti di quel giorno sono pinned). Quindi
la constraint e' inerte sul golden runtime (0/0) mentre il PLACEMENT viola la
edge-shape nel 71% dei giorni. Questo e' esattamente il motivo per cui TASK-379
demoted H22 -> M29: il golden non segue l'edge-rule del docx, quindi l'HARD era
stricter-than-practice.

**Tier: MEDIUM HOLDS (confermato).** Il golden viola al 71% -> HARD vietato
(confermato dalla demotion TASK-379). L'attuale MEDIUM + esenzione day-level
pinned e' il registro corretto: il vincolo morde solo sui placement generati dal
solver (unpinned), non sul golden. Nessun cambio di tier. La discrepanza (71%
di placement golden illegali ma score 0) e' strutturale e attesa, non un bug.

**Composizione.** M29 e' l'unico consumer della regola docx G12 edge-of-day.
Nessun clash con H17 (vertical compaction), M15 (same-subject adjacency) o H20
(group calendar). L'esenzione pinned significa che morde solo sulle mosse del
solver, il che e' il comportamento voluto (cristallizza la shape per i nuovi
placement senza invalidare il golden).

---

## Riproducibilita'

```
python3 scripts/analyze/perimetro_residuo_report.py
# -> perimetro-residuo-golden-report.txt (gia' salvato in task472-results/)
```

Lo script legge il golden, filtra le trappole (10.542 lezioni post-filtri), e
emette i 5 conteggi. I numeri M24/M25 (16, 27) coincidono byte-exact con i
"golden -16/-27" di CLAUDE.md. I numeri R1/R2/R3 coincidono con le misure
indipendenti di `golden_bucket_c_measures.py` (bucket C). Le misure R5 sono
calcolate replicando `isLegalProfEdgeRun` sul golden raw.
