# SPIKE queue - DEFINIZIONI + misurazioni raw (per verifica Fedora)

Addendum maintainer 29/07: dopo le fasi A-E, non fermarsi; spike esplorativi su ipotesi
non ancora approvate, checkpoint-based, flag default-OFF, niente file protetti.
Consegna a 3 righe: ipotesi / risultato / tenere-buttare. Adozioni = maintainer.

Tutte le misure in-harness (H2, isolati dal prod), su export/snapshot gate-finale
(claudedocs/spalmone-2026-27/task446-results/gate-finale/), 90s phase-5 LS, full-pop.

---

## SPIKE 1 - Sweep peso M37  [MEASURATO]

DEFINIZIONE (addendum): "Sweep del peso di M37 oltre i due valori chiesti (misura
anche 200 e 1500) e curva 'cambi d'aula contro KPI': serve a maintainer per scegliere con
una curva invece che con due punti."

METRICA: countRoomChanges = coppie adiacenti stessa-materia/gruppo/data, stesso
docente, stanza diversa, non-lab (specchio del vincolo M37). Validazione: before=124
= M37 di campagna run1.

RAW (export gate-finale, 90s, stesso start):
| M37 weight | before | after | medium totale |
|-----------|--------|-------|---------------|
| 100       | 124    | 106   | -4.556.772    |
| 200       | 124    | 106   | -4.567.372    |
| 400       | 124    | 107   | -4.588.972    |
| 800       | 124    | 106   | -4.630.972    |
| 1500      | 124    | 106   | -4.705.172    |

VERDETTO: PIATTO (106/107/106). Il medium scende solo perche' lo stesso ~106 residuo
costa di piu' (delta 100->1500 = 148400 = 106x1400). I cambi sono forzati dalla
disponibilita' di stanza (H2/H-M2/capienza). TENERE 100. Curva: vedi anche faseB-m37/VERDICT.md.

---

## SPIKE 4 - De-tag sweep oltre scope autorizzato  [MEASURATO]

DEFINIZIONE (addendum): "Sweep dei de-tag oltre lo scope autorizzato, SOLO in-harness
e senza toccare i file: 'taglio metalli' spostato sul tag saldatura con R17 a
capacita' reale (ipotesi: l'officina di taglio esiste ma e' fittizia a capacita'
zero), e 'servizio stilistico' de-taggato: misura il guadagno di ciascuno cosi' la
domanda alla scuola arriva con i numeri."

METRICA: lab room-null residuo dopo phase-5 LS (baseline = 251, vedi FASE B), via
PLANNER_TEST_ROOM_TAG_UNTAG (hook de-tag in-memory).

RAW (ciascuno da solo, export gate-finale, full-pop, 90s):
| de-tag (solo) | lab room-null before | after | chiude |
|---|---|---|---|
| (baseline, nessuno) | 251 | 251 | 0 |
| servizio stilistico | 400* | 166 | ~85 (251->166) |
| taglio metalli | 305* | 233 | ~18 (251->233) |
| (FASE C autorizzata: progettazione termoidraulica + tecnologia tessile) | 540* | 179 | ~72 |
(* "before" e' post-de-tag+nulling, gonfiato dal cache di Lesson.isLab nell'harness;
il dato pulito e' l'"after" e il delta vs baseline 251.)

VERDETTO: servizio stilistico e' la leva grossa (~85) ma maintainer lo flagga come
pratica-su-cliente nell'acconciatura -> domanda alla scuola col numero 85. taglio
metalli ~18 (basso, genuinamente lab). La variante re-tag "taglio metalli -> saldatura
+ R17 real cap" NON misurata (l'harness ha solo hook de-tag, non re-tag); basso
valore atteso visto il ~18 del de-tag + R17-zero-cap era falsa pista (risveglio).
TENERE come numeri per la scuola (non adottare, file protetto).

---

## SPIKE 5 / lead VRP da FASE D  [MEASURATO, root-cause]

DEFINIZIONE (addendum #5 + mio): "Qualunque altra pista tu veda nei dati e che valga
una misura." Nello specifico, il lead emerso in FASE D: i 111 donatori coda+liberi
VRP-esclusi dallo slot-0 del venerdi per G11 sono un'esclusione spuria (bug VRP)?

RAW (db-query, read-only): G11 venerdi 2026-09-18 ha labHours=0 (giornata NON-lab),
calendar slot-0 open (GroupCalendarRule dayofweek=4 minslot=0 maxslot=7).
I 111 donatori (passati conflict-free + departure-safe) sono lezioni LAB escluse dal
VRP della lab-quota (H23: lab lesson su giorno a labHours=0). Breakdown pool G11
(666): teacher-busy=396, departure-unsafe=159, VRP=111.

VERDETTO: NON e' un bug, e' la lab-quota VRP corretta. BUTTARE l'ipotesi "VRP spurio".
G11 venerdi va risolto a livello di COSTRUZIONE (SPIKE 3). Dettaglio raw:
faseD-450/spike-applicability-raw.txt (output completo SpikeApplicabilityAnatomyTest)
+ faseD-450/VERDICT.md.

---

## Non ancora misurati (prototipi sostanziali, a focus fresco)

SPIKE 2 - DEFINIZIONE (addendum): "Prototipo dell'allocatore lab a tag mono-stanza
(TASK-449) dietro flag default-OFF, con verdetto all'harness di fine costruzione come
fu per il 435: quante delle ~170 ore lab senza stanza residue si chiudono." STATUS:
non iniziato (prototipo allocatore).

SPIKE 3 - DEFINIZIONE (addendum): "Ancora settimanale riservata nel template di
costruzione (TASK-450 direzione c) come ALTERNATIVA al fix del repair della Fase D:
misura le due direzioni sullo stesso checkpoint e dì quale vince." STATUS: non
iniziato (WeeklyTemplateBuilder e' 693 righe, greedy fill intricato; prototipo dietro
flag). E' la direzione RACCOMANDATA da FASE D/SPIKE 5 per i 16 ritardi di G11 ven.
FASE D (direzione d repair) gia' misurata: NON regge (vedi sopra + faseD-450/).

---

## SPIKE 6 - Anatomia G04  [MEASURATO, read-only, da Fedora]

DEFINIZIONE: "G04 finisce il 28/05 nel run mentre nel golden arriva al 15/06, ed e' la
classe col full-day peggiore (54% contro 32%, unico caso in cui siamo MEGLIO del golden).
Misura: perche' finisce prima? Ore totali collocate contro attese, distribuzione mensile
contro golden, e se il residuo dipende dalla quota lab FC (foglio DEF FC TUTTI) o da altro."

FONTE: run = export cert FASE A/B/C (task447-results/cert/lessons.txt), golden = seed
lessons.csv, input = incarichi.csv (totale corso) + calendario_fc.csv (expected-hours fact).

Scomposizione run vs golden G04:
| dimensione | run | golden | input |
|---|---|---|---|
| non-stage collocate | 750 | 758 | 750 (incarichi 990 - stage 240) |
| stage collocate | 208 | 240 | 240 |
| TOTALE collocate | 958 | 998 | 990 |
| ultima lezione REGOLARE (non-stage) | 2027-05-28 | 2027-06-15 | expected aula+lab: mag=20 giu=0 |
| ultima lezione stage | 2027-06-10 | 2027-06-14 | 30 stage days |

Mensile (expected aula+lab del foglio | run | golden):
| mese | expect | run | golden |
|---|---|---|---|
| 2026-09 | 74 | 62 | 72 |
| 2026-10 | 127 | 123 | 110 |
| 2026-11 | 122 | 118 | 108 |
| 2026-12 | 81 | 78 | 73 |
| 2027-01 | 93 | 88 | 83 |
| 2027-02 | 104 | 98 | 94 |
| 2027-03 | 111 | 93 | 100 |
| 2027-04 | 103 | 109 | 125 |
| 2027-05 | 20 | 141 | 156 |
| 2027-06 | 0 | 48 | 77 |

Sotto-piazzamento STAGE run vs golden (per gruppo) - NON specifico di G04:
| gruppo | run | golden | diff |
|---|---|---|---|
| G12 | 112 | 295 | -183 |
| G10 | 228 | 300 | -72 |
| G04 | 208 | 240 | -32 |
| G08 | 276 | 300 | -24 |
| G11 | 287 | 295 | -8 |
| G09 | 292 | 300 | -8 |
| G07 | 295 | 295 | 0 |
| G13 | 295 | 295 | 0 |
TOTALE stage: run 1993 vs golden 2320 (-327 globale).

VERDETTO: BUTTARE l'ipotesi "difetto G04". (1) Ordinarie: run=750=totale corso esatto
(fedele all'input); il golden over-piazza 758. (2) Il "28/05" e' l'ultima regolare del run;
il foglio DEF FC TUTTI stesso diluisce G04 a 0 dopo aprile -> il golden 15/06 e' l'outlier.
(3) Gap reale = 32h stage = fetta di un deficit GLOBALE stage (capofila G12 -183), non G04.
Azionabile: sotto-piazzamento STAGE globale, non G04.

---

## SPIKE 3 - Ancora settimanale riservata nel template (TASK-450 dir c)  [MEASURATO, checkpoint]

DEFINIZIONE: "WeeklyTemplateBuilder deve riservare per ogni (gruppo, weekday) la cella dello
slot 0, cosi' M32 la difende. Verdetto a checkpoint di fine costruzione + day-start-anatomy:
bersaglio G11 ven 15-16 -> 0 e nessuna nuova cella con scarto >= 3, senza perdere
ricorrenza o full-day." MECCANISMO GIA' ESISTENTE = TASK-430 `reserveSlot0Anchors`
(flag planner.construction.slot0-balancing.enabled, default OFF; bocciato gate2 a solve
completo). Misurato qui al checkpoint di COSTRUZIONE.

METODO: WeeklyTemplateConstructionRealSeedTest (test H2, ~50-70s), flag OFF (baseline) vs
flag ON via JDK_JAVA_OPTIONS (canale system-property al JVM forkato di surefire; verificato
che prende: il dump cambia, G11 ven 09-18 slot-0 cambia corso). Dump target/construction-
lessons.txt + python3 scripts/analyze/day-start-anatomy.py.

FINDING DECISIVO - G11 venerdi e' ancorato a slot 0 a COSTRUZIONE in entrambi gli stati:
- costruzione baseline 2026-09-18: G11|2026-09-18|8|Matematica|T08 (slot 0 pieno)
- costruzione flag-ON  2026-09-18: G11|2026-09-18|8|Disegno Edile ed Impiantistico|T12
- solve finale (cert) 2026-09-18: hour 8 VUOTO, prima lezione alle 9 (Educazione Civica/T23)
=> l'ancora c'e' a costruzione, il solver la ERODE. 5 venerdi consecutivi verificati (09-18,
09-25, 10-02, 10-09, 10-16): tutti h8 a costruzione. G11 ven NON compare nella matrice
day-start di costruzione (baseline ne' flag-ON): 0 ritardi a costruzione in entrambi.

Metriche costruzione baseline vs flag-ON:
| metrica | baseline | flag-ON |
|---|---|---|
| ricorrenza (L2) | 76,3% (7935/10404) | 76,8% (7975/10389) |
| full-day scoped | 85,5% (1671/1954) | 84,9% (1659/1954) |
| full-day raw | 69,8% (1672/2395) | 69,3% (1660/2395) |
| late-days costruzione (day-start-anatomy) | 25 | 20 |

Celle con scarto>=3 (<<< SCARTO nella matrice) - flag-ON le SPOSTA non le elimina:
- baseline: G07 lunedi (6), G11 mercoledi (3)
- flag-ON: G04 venerdi (4), G12 venerdi (4), G14 giovedi (4)
Nessuna delle due ha "G11 venerdi" (il bersaglio): a costruzione e' gia' 0 in entrambi.

VERDETTO: BUTTARE la direzione (c). Il problema NON e' di costruzione (l'ancora slot-0 di G11
ven c'e' gia' a costruzione in entrambi gli stati), e' di DIFESA IN SOLVE: M32 non tiene
l'ancora sotto pressione H1 (FASE D: 396/666 teacher-busy). CORREGGE la raccomandazione
FASE D/SPIKE 5 che ipotizzava la costruzione come leva. La leva e' solve-time (peso M32/M37
sulla cella, o pinning esplicito di G11 ven slot 0), non reserveSlot0Anchors.

---

## SPIKE 7 - Anatomia erosione ricorrenza (74% run vs 77% template)  [MEASURATO, read-only]

DEFINIZIONE (mandato "non fermarti", gap noto "ricorrenza 74 vs template 77"): dove la LS erode
il template di costruzione? Misura resemblance-kpi.py UNPINNED per-gruppo a fine costruzione
(dump WeeklyTemplateConstructionRealSeedTest baseline, /tmp/spike3/baseline-construction.txt)
vs a solve finale (cert/lessons.txt).

METRICA: ricorrenza UNPINNED per gruppo, costruzione -> finale (delta pp).
A costruzione TUTTE le lezioni sono unpinned (PINNED 0/0); a finale la pre-assegnazione pinna
~3090 (stage+MODA+constrained-teacher), quindi la popolazione unpinned cambia composizione
tra i due cut: parte del delta e' COMPOSIZIONE, non pura erosione LS. Caveat onesto.

| gruppo | costr.% | finale% | delta |
|---|---|---|---|
| G14 | 82,7 | 62,6 | -20,1 (caso limite, 98->115 lezioni) |
| G11 | 72,4 | 67,4 | -5,0 |
| G01 | 81,1 | 76,1 | -5,0 |
| G05 | 78,8 | 73,9 | -4,9 |
| G03 | 81,9 | 77,1 | -4,8 |
| G09 | 72,1 | 68,2 | -3,9 |
| G10 | 71,5 | 67,8 | -3,7 |
| G06 | 81,5 | 78,2 | -3,3 |
| G02 | 78,5 | 79,1 | +0,6 |
| G04 | 81,9 | 81,3 | -0,6 |
| G13 | 77,7 | 77,7 | 0,0 |
| G08 | 71,7 | 73,2 | +1,5 |
| G07 | 75,3 | 78,0 | +2,7 |
| G12 | 55,9 | 62,8 | +6,9 (LS aiuta, costruiva basso) |
TOTALE UNPINNED: 76,3% -> 74,4% (-1,9pp globali).

VERDETTO: TENERE come segnale. L'erosione e' DISUNIFORME: concentra su G14 (-20, caso limite)
e sui gruppi di 3° anno/stage (G09/FA/FB, -4/-5) + MODA; la LS MIGLIORA G12 (+7) e
G07/FC. Ipotesi: l'erosione e' Pressure della fase room + stage sui gruppi 3° anno (stesso
contorno del gap stage globale di SPIKE 6). Leva candidata: move selection / difesa M32 sui
gruppi 3° anno, non costruzione (coerente col verdetto SPIKE 3). Per una misura pulita serve
tracking same-lesson costruzione->finale (follow-up).

---

## SPIKE 8 - Anatomia sotto-piazzamento STAGE (capofila G12)  [MEASURATO, read-only]

DEFINIZIONE (da SPIKE 6 "gap azionabile = stage globale", run 1993 vs golden 2320): perche' il
run piazza meno stage del golden? Caso peggiore G12 (run 112 vs golden 295, -183).

METRICA: stage placement run (cert) vs golden (lessons.csv), mensile + date-range + date-distinct.

G12 stage mensile (run | golden):
| mese | run | gold |
|---|---|---|
| 2026-11 | 14 | 28 |
| 2026-12 | 21 | 73 |
| 2027-01 | 21 | 56 |
| 2027-02 | 28 | 56 |
| 2027-03 | 21 | 63 |
| 2027-04 | 7 | 19 |
Finestra identica: entrambi iniziano 2026-11-20. Run finisce 2027-04-02, golden 2027-04-10.

FINDING: deficit di COPERTURA DATE, non di capacita' giornaliera.
- run: 112h su 16 date distinte = ~7,0 h/data
- golden: 295h su 43 date distinte = ~6,9 h/data
Il run CONCENTRA lo stage di G12 su molte meno date (16 vs 43) con riempimento per-data
identico al golden. Mancano ~27 date stage del golden (soprattutto dic 73->21, mar 63->21).
Le ore non piazzate (~183) NON sono boxed (H0 globali = 3): sono expected-unscheduled
(off-grid) o stub non generati/piazzati.

VERDETTO: TENERE come segnale. Il sotto-piazzamento stage e' un comportamento di
pinning/placement a costruzione/importer (lo stage e' pinnato "on the grid" da
CalendarioImportService.planStagePins / StagePattern), NON di search del solver. Il run usa
meno date stage del golden a parita' di ore/date. Root-cause del "perche' meno date" richiede
il meccanismo di pinning stage (follow-up: planStagePins + StagePattern di G12). Deficit
sistemico (run 1993 vs golden 2320 stage; peggiore G12 -183, poi G10 -72). Non e' il
contenzione-docente di TASK-450: e' copertura date del pinning stage.

---

## SPIKE 2 - Allocatore lab a tag mono-stanza (TASK-449)  [MEASURATO + PROTOTIPO, flag default-OFF]

DEFINIZIONE (mandato): prototipo allocatore pre-assegnazione che pinni i blocchi lab dei tag
mono-stanza (acconciatura, termoidraulica, meccanica) CON stanza, su celle disgiunte, cadenza
golden, validando 0hard. Verdetto: quante delle ~189 ore lab senza aula si chiudono.

IMPLEMENTAZIONE (commit 4d33abf2, flag planner.pre-assign.mono-room-tag.enabled default OFF):
MonoRoomTagAllocator (puro, 206 righe, mirror di ConstrainedTeacherAllocator) + runMonoRoomTagPhase
(@ActivateRequestContext @Transactional, +317 righe in HybridPhase1aService, Phase 0b, indipendente
dal short-circuit LabRotation come Phase 0) + MonoRoomTagAllocatorPhaseTest (bootstrap, real-seed).

VERDETTO per-tag (re-run indipendente, test PASS 10.7s, smoke invariants OK: pinned lessons con
room!=null && timeslot!=null, nessuna doppia prenotazione stanza su (data,slot)):
| tag | pinned-with-room | skipped | residual | total demand |
|---|---|---|---|---|
| acconciatura | 365 | 33 | 354 | 719 |
| termoidraulica | 242 | 34 | 71 | 313 |
| meccanica | 418 | 21 | 44 | 462 |
| TOTALE | 1025 | 88 | 469 | 1494 |

NO-OP flag-OFF verificato esatto (re-run WeeklyTemplateConstructionRealSeedTest: ricorrenza 76,3%,
full-day scoped 85,5%, stamp stats identici placed 8308 conflict 0; nessuna riga mono-room-tag nel
log = fase non eseguita). Via flag-off byte-identical al baseline.

RAFFREDDAMENTO DEL VERDETTO (onesto, IMPORTANTE): "total demand" = pool lab completo richiedente il
tag (1494h), NON le 189 (room-null a solve FINALE senza allocatore). L'allocatore pinnerebbe 1025h
con stanza, eliminandone il room-null, MA provare una RIDUZIONE NETTA delle 189 richiede un A/B
solve live (vietato senza via esplicito - checkpoint only). Quindi il verdetto NON e' "chiude X
delle 189": e' "l'allocatore pinnerebbe disgiuntamente col stanza il 69% del pool lab mono-tag
seguendo la cadenza golden".

FINDING STRUTTURALE - SOFFITTO DI CAPACITA': l'acconciatura NON serve tutti i gruppi. La singola
stanza R11 non regge 5 gruppi (1/2/G10 + G12 + G14) su celle disgiunte: G12
e G14 prendono 0 (le loro celle golden collidono con 1/2/G10 dominanti: G02 Mon slot0, G06
Tue slot0, G10 Thu slot0). Residuo acconciatura 354h concentrato qui. Quindi le 189 (139
acconciatura) NON sono chiudibili solo via allocatore: c'e' un soffitto HARD di capacita'. La leva
sull'overflow e' piu' stanze o riclassificazione (de-tag SPIKE 4: servizio-stilistico ~85).
meccanica invece copre al 90% (3 gruppi 1/G08 + G13 ruotano pulito).

RACCOMANDAZIONE: TENERE il prototipo (flag-OFF, pattern 435 corretto e verificato) come strumento
di pre-assegnazione valido, MA il verdetto mostra che l'allocatore da solo non chiude le 189: il
collo di bottiglia acconciatura e' capacita' (1 stanza, 5 gruppi), non search. Per chiudere le 189
servono o piu' stanze/tag (scuola) o de-tag (SPIKE 4), non solo questo allocatore. Adozione =
maintainer (flip del default solo dopo A/B staged completo che misuri la reale riduzione del room-null).

---

## SPIKE 8b - STAGE root-cause PERCHE' (maintainer 29/07, HIGH PRIORITY)  [MEASURATO, read-only]

DEFINIZIONE: SPIKE 8 stabili' CHE e' deficit di copertura date (G12 16 vs golden 43, h/data
identico), comportamento pinning/import non search. Ora il PERCHE': dove si perdono le 27 date
(foglio assente / riga stageHours=0 / stub esauriti / off-grid sabati+chiusure -> expectedUnscheduled).

DATI (foglio calendario_class_days.csv modality=S, golden seed/lessons.csv, closures.csv, run cert):
- foglio G12 S-rows: 43 (ripartizione Tue1 Wed2 Thu3 Fri18 Sat19) = IDENTICA al golden (43, stessa
  ripartizione). Quindi il dato c'e' TUTTO: 43 date attese, golden piazza su tutte 43, run ne pinna 16.
- run G12 stage: 16 date / 112h. Le 16 pinzate sono SUBSET STRETTO del golden (0 date fuori golden).
- GAP (27 date / 183h) scomposto:
  | categoria | date | ore | on/off grid |
  |---|---|---|---|
  | pinned (run) | 16 | 112 | on-grid |
  | SABATO (off-grid) | 19 | 131 | off-grid -> expectedUnscheduled |
  | CHIUSURA weekday (Natale 12-23/24/29/30/31, Carnevale 03-25/26) | 7 | 45 | off-grid -> expectedUnscheduled |
  | mystery Friday 2027-01-22 (on-grid, non-chiusura, non pinnato) | 1 | ~7 | on-grid (residuo) |
  | TOTALE | 43 | 295 | |

VERDETTO: NON e' un difetto - ne' di codice ne' di dati. Lo stage di sabato e' un DATO DI INPUT
REALE dettato dal calendario (confermato da maintainer: il golden ha davvero stage di sabato, non va
messo in discussione). G12 ha il 60% dello stage OFF-GRID (19 sabati + 7 giorni di chiusura =
176h delle 183h di "gap"). Il run si comporta CORRETTAMENTE: pinna le 16 date on-grid non-chiusura
(112h) e contabilizza le 176h off-grid come Course.expectedUnscheduledHours (comportamento
progettato per stage di sabato/chiusure: CalendarioImportService planStagePins "off-grid hours draw
budget too" + StagePlan.stageOffGridHours). Le ore sono CONSERVATE: 295h totali = 112 on-grid pinned
+ 176 off-grid expectedUnscheduled + ~7 residuo. Il "deficit stage" (run 1993 vs golden 2320, -327)
e' un ARTEFATTO DI METRICA: confronta lo stage on-grid-pinnato (run) contro lo stage TOTALE incluso
off-grid (il golden LISTA anche i sabati). I ~272h off-grid (sabati+chiusure, solo class_days) sono
correttamente in expectedUnscheduled, NON mancano. NIENTE da fissare (codice o dati).
RESIDUO MINORE: 1 data (Friday 2027-01-22, ~7h, on-grid non-chiusura non pinnata) - probabile
stageSkippedNoBudget (esaurimento stub) o skip specifico; ~7h sulle 183h, follow-up opzionale.
CONCLUSIONE: il gap stage e' chiuso come indagine - e' contabilita' off-grid corretta, non un difetto.
La metrica "stage run vs golden" va letta netto-off-grid (on-grid confrontabile), non lordo.

CORREZIONE Fedora (29/07, numeri esatti, TEMA STAGE CHIUSO): il golden ha 2320h stage = 2009
ON-GRID (lun-ven) + 311 OFF-GRID (sabati+chiusure). Il run ne piazza 1993 ON-GRID. Divario REALE
= 16h, tutte su G04. Su G12 il confronto corretto e' 112 contro 112 (identico): le 183
"mancanti" erano TUTTE sabati. La lettura precedente (327h di gap, G12 capofila) era un
artefatto di metrica (on-grid-pinnato vs totale-incluso-sabati). Il tema stage NON e' un gap.


---

## TASK-451 - MISURA fattibilita' catena-2 (ricombinazione orizzontale, maintainer 29/07)  [MEASURATO, read-only]

DEFINIZIONE (TASK-451 riformulato): prima del codice, misurare quanti buchi sarebbero risolvibili
da una catena di profondita' 2 = buchi con almeno un docente del gruppo occupato da un altro gruppo
che a sua volta ha un docente alternativo libero in quello slot. Se alto -> la mossa vale; se basso
-> buchi infattibili, si chiude il tema.

METODO (INPUT-derived, niente golden: dump di costruzione/export = output run + incarichi.csv per
subject->docenti). "GAP" = slot vuoto DENTRO lo span della giornata (buco interno + late-start,
esclude la coda = fine anticipata legale). "HARD" = tutti i docenti del gruppo quel giorno sono
occupati in quello slot (il caso della contesa). depth-2 resolvabile = un docente Ti del gruppo e'
occupato in quello slot da un altro gruppo Gi, e Gi ha un docente alternativo (stessa materia, != Ti)
LIBERO in quello slot. Antefatto: 54 materie su 59 sono multi-docente (alternative quasi sempre esistono).

RISULTATI:
| dump | GAP totali | trivially fillabili (docente gia' libero) | HARD | depth-2 resolvabili | share HARD |
|---|---|---|---|---|---|
| costruzione | 183 | 110 | 73 | 46 | 63% |
| finale (cert) | 28 | 13 | 15 | 7 | 47% |

- FINALE (i buchi su cui il solver si e' BLOCCATO, quello che conta): 15 HARD, **7 (47%) risolvibili
  a catena-2**. Per gruppo: **G11 16 totali / 8 resolvabili** (il caso headline), poi G13, G09,
  G04 ecc. (1 ciascuno).
- CHECK PIN: tutti i 7 risolvabili a finale (e 46 a costruzione) hanno la lezione Gi NON-pinnata
  (mobile) -> il vincolo "PINNED immobili" NON blocca queste cateni. La stima non scende per i pin.

VERDETTO: SEGNALE MODERATO-ALTO, la mossa VALE il prototipo. ~47% dei buchi HARD finali (7, inclusi
8/16 di G11 ven) sarebbero chiudibili da una catena-2 con lezione mobile e docente alternativo
libero. E' un LIMITE SUPERORE: la misura controlla solo disponibilita' docente + alternativa-libera +
non-pinnato, NON valida H11/M15/stanza/quota (la ri-assegnazione non deve violarli) ne' la
rappresentazione della mossa. OPEN: il modello Course fissa docente-per-corso, quindi "docente
alternativo" implica una mossa custom di cambio Course (stessa materia, altro docente) - e' la via
(a) MoveIteratorFactory di maintainer, da definire nel prototipo. Coerente con la direttiva input-only
(niente golden assorbito): la catena agisce sullo stato live del solver, non legge il golden.

---

## TASK-451 - VALIDAZIONE catena-2 (catena CORRETTA, departure-safe)  [MEASURATO, read-only]

CORREZIONE 1 (maintainer/Fedora): la catena NON cambia Course. Sono DUE cambi di timeslot composti:
G1 ha buco a slot s; un suo docente T e' occupato a slot s con G2; sposto la lezione di G2 (by T)
in una destinazione legale, e metto la lezione di G1 (by T) a slot s. Quindi la misura precedente
(47%, criterio "docente alternativo libero") era MAL POSTA: andava rifatta con la catena corretta.

RISULTATO (catena corretta + validazione departure-safe su dump):
| dump | HARD gaps | SURVIVE | fail: departure-unsafe | no-dest | G1-H11 |
|---|---|---|---|---|---|
| finale (cert) | 15 | 0 (0%) | 15 | 0 | 0 |
| costruzione | 73 | 8 (11%) | 65 | 0 | 0 |

VERDETTO: il 47% CROLLA A ZERO sui buchi HARD finali (0/15, tutti G11 ven slot-0). Il vincolo
bindante e' il DEPARTURE-SAFE: la lezione di G2 nello slot del buco e' l'INIZIO della giornata di
G2 (non la coda), quindi spostarla apre un buco (es. 09-18 slot0: T23->G08 che ha slot 0-4,
T21->G06 0-3, T06->G09 0-4, T12->G07 0-5; slot0 NON e' mai coda).
E' la STESSA trappola di FASE D (159/666 departure-unsafe). A costruzione 8/73 (11%) sopravvivono
ma il solver li processa gia'.
PER IL GATE DI maintainer ("crolla a zero -> il tema si chiude senza codice"): la catena di PROFONDITA'
2 SI CHIUDE. Nessun buco HARD finale e' chiudibile a depth-2 senza aprire un buco altrove.
UNICA VARIANTE RESIDUA: profondita' 3+ (spostare un inizio-di-giornata + riempire il buco che
apre), NON misurata. La catena non e' una mossa di cambio Course (CORREZIONE 1 recepita).
Caveat residui: room conflict + H11 caps esatti non validati offline, ma non rilevanti (il
kill e' tutto departure-safe, gia' validato).

---

## TASK-451 depth-3 - MISURA fattibilita' catena-3 (maintainer 29/07, gate AC#4)  [MEASURATO, read-only]

DEFINIZIONE (maintainer): depth-2 muore sempre per departure-unsafe (la lezione di G2 nello slot
bersaglio e' l'inizio della giornata dell'altro gruppo). Domanda: esiste almeno un caso in cui
una catena piu' lunga ESCE dalla trappola invece di riprodurla? Cioe' sposto la lezione di G2
che apre il buco, e quel buco e' riempibile da una terza mossa legale (docente libero, slot
aperto, nessun conflitto, giornata che resta contigua). Misura, non prototipo.

METODO (script `scripts/analyze/chain-feasibility-kpi.py`, read-only, INPUT-derived niente
golden). CALIBRAZIONE: lo script ricostruisce depth-2 (catena corretta + departure-safe) e
riproduce ESATTAMENTE i numeri della sessione precedente - 0/15 finale, 8/73 costruzione - quindi
la primitiva departure-safe (mossa gap-neutro = rimuovere una lezione solo dalla CODA della
giornata; slot0 e' sempre un inizio-giornata, mai una coda) e' corretta. Definizione HARD =
slot GAP (late-start + buco interno, coda esclusa) dove TUTTI i docenti del gruppo quel giorno
sono occupati in quello slot. Stage/Esame riempiono lo slot (non sono gap) ma non sono docenti
reali spostabili (questo corregge i 4 HARD spuri su G08 2027-04-01, mattina di Stage).

DEPTH-3 (SWAP gap-neutro): per chiudere il gap di G1 a slot s senza aprire un buco netto servono
3 mosse, tutte con sorgente in CODA (rimozione gap-neutra):
  1. G2(T2): coda-G2 -> slot s      [T2 libero a s; rifila il buco aperto in G2]
  2. G2(T1): slot s -> coda-G2      [T1 libero alla coda-G2; lo swap chiude la forma-giorno di G2]
  3. G1(T1): coda-G1 -> slot s      [T1 ora libero a s dopo il passo 2; riempie il gap di G1]
Netto: gap di G1 CHIUSO (coda accorciata), forma-giornata di G2 IMMUTATA (swap), T1 finisce a
coda-G2 + G1@s (slot diversi, no teacher conflict). EXTEND = variante lasca in cui T1 atterra
oltre la coda-G2 (giornata estesa di 1, H11 non validato).

RISULTATI:
| dump | HARD gaps | depth-2 survive | depth-3 SWAP | depth-3 EXTEND |
|---|---|---|---|---|
| finale (cert) | 15 | 0 (0%) | **0 (0%)** | **0 (0%)** |
| costruzione | 73 | 8 (11%) | 12 (16%) | 14 (19%) |

- FINALE (i buchi su cui il solver si e' BLOCCATO, quello che conta): depth-3 = **0/15**, sia
  SWAP che EXTEND. Tutti 15 i buchi sono G11 venerdi slot-0.
- SANITY: a costruzione depth-3 = 12/73 (SWAP) / 14/73 (EXTEND contiguo), NON-zero => la logica
  NON e' rotta; trova le catene quando esistono. Lo 0/15 finale e' una proprieta' GENUINA del dump.

MECCANISMO del kill (tracciato sui 15, tutti verificati):
- **5 gap** (T06->G09): il donatore G2=G09 ha la CODA = lab PINNATA (slot4
  Confezione/T16, pinned=true). Nessun donatore di coda mobile per rifare slot0.
- **10 gap** (T12->G07, T25->G04, T08->G02): il docente di coda di G2 e'
  unpinned ma OCCUPATO a slot0 con un terzo gruppo (es. T09 coda di G02 insegna a G10 a
  slot0; T21 coda di G07 insegna a G06 a slot0). La trappola si RIPRODUCE: liberare
  T2 servirebbe un quarto swap, e il MINIMO per questi casi e' depth-5 (due swap annidati),
  oltre il gate depth 2-3 di TASK-451.

VERIFICA ADVERSARIALE (skeptic, CONFIRMED): (a) calibrazione riprodotta indipendentemente;
(b) i 15 gap ricalcolati con parser proprio = tutti G11 ven slot-0; (c) 3 spot-check dei
meccanismi letti sul dump grezzo (pinned T16; T09/T21 busy a slot0); (d)
BRUTE-FORCE su tutte le lezioni G1 come sorgente del gap-filler = 0 catene trovate (il vincolo
"sorgente in coda" e' STRUTTURALE: ogni slot non-coda k < max_occupied resta dentro lo span =>
rimuoverlo apre un buco; solo la coda e' gap-neutrale); (e) catene a 3 gruppi distinti sono
strutturalmente impossibili (la lezione e' vincolata al gruppo); (f) i vincoli non-validati
(room/H11/M15/quota) possono dare solo falsi-positivi, mai falsi-negativi => upper bound 0 =>
vero 0.

VERDETTO: **LA TEMA SI CHIUDE.** Per il gate di maintainer ("crolla a zero -> chiudi senza codice"),
la ricombinazione orizzontale a profondita' 2-3 non ha prototipo: NESSUNO dei 15 buchi HARD
finali e' chiudibile senza aprire un buco altrove, a depth-2 (0/15) ne' a depth-3 (0/15). I buchi
G11 ven slot-0 sono genuinamente infattibili via catena timeslot same-day. La leva resta
solve-time fuori dalla catena (peso M32/M37 sulla cella, o pinning esplicito di G11 ven slot0
- gia' nel contorno di SPIKE 3), non una mossa composta. I 5 gap a coda-pinnata sono infissibili
a QUALUNQUE profondita' (pin immobili per mandato).

---

## FULL-DAY deficit shape (maintainer 29/07, read-only, script potenziato)  [MEASURATO attendibile]

METODO: daily-fill-kpi.py potenziato (dettaglio per (gruppo,data) + caratterizzazione dei modi del
deficit), expected dal suo class_days.csv (seme DailyActivityQuota, NO ricostruzione CSV che
doppia-conta gli FC). Run = cert dump.

FORMA DEL DEFICIT run (senza G14), per modo:
| modo | #giorni | top gruppi | top giorni | natura |
|---|---|---|---|---|
| -1h | 108 | G08 39, G04 32, G06 10 | mar/gio/mer | FC over-capacity pianificata (835h aula+lab vs 750h corso) |
| -2h | 14 | G04 10 | ven 7 | FC over-capacity concentrata piu a fondo del golden |
| -3h | 13 | G04 11 | ven 5 | FC over-capacity concentrata |
| -5h | 7 | G08 3, G12 3 | gio 3 | mix |
| -7h | 24 | G12 24 | SAB 18 | ARTEFATTO: stage off-grid del sabato (expected conta, placed=0) |
| -8h | 13 | G10 9 | SAB 7 | ARTEFATTO: stage off-grid del sabato |

VERDETTO: il deficit full-day e' dominato da ARTEFATTI. (1) I modi deep (-7h/-8h, ~37 giorni) sono
SABATI di stage off-grid (expected conta il sabato, placed=0 perche' off-grid -> expectedUnscheduled),
stesso artefatto di SPIKE 8b. (2) Il modo -1h (108 giorni) e' l'over-capacity FC PIANIFICATA dal
foglio (835h aula+lab vs 750h di corso): shortfall strutturale, non difetto di search. Il golden
rasa -1h su molti giorni (67% dei suoi deficit a -1h, 0 profondi su G04); il RUN concentra parte
di quello stesso shortfall FC in giorni piu profondi (-2h/-3h, ~21 giorni su 1/G08): questo e'
il segnale REALE (forma del deficit peggiore del golden a parita' di shortfall totale).
ANOMALIA: G11 2027-01-22 (ven) exp 8 got 0 - venerdi completamente vuoto (il mystery Friday di
SPIKE 8b), da root-causare a parte (no-session? H26?).
NOTA strumentale: il golden (data/lessons.csv) e' formato CSV non pipe, quindi daily-fill-kpi.py non
lo parse; il "golden 85.4%" nello script e' hardcoded (riferimento, non calcolato). Confronto
golden-richiede un dump golden in formato pipe (db-query del docstring).

---

## EROSIONE ricorrenza 3 anno, hotspot per (gruppo,materia) (maintainer 29/07, read-only)  [MEASURATO]

METODO: resemblance-kpi.py potenziato con --by-subject (breakdown ricorrenza unpinned per
(gruppo,materia), N>=8). Tracking costruzione->finale = rec per-(gruppo,materia) a costruzione
(tutte unpinned) vs finale (unpinned), diff. Gruppi 3 anno (G11/FA/MODA). Escluse materie con
finN<8 (a finale tutte PINNATE -> fuori popolazione unpinned, NON erose: la loro ricorrenza e' alta
da pin). Caveat: popolazione unpinned shifts leggermente (pre-assign pinna tra i due cut), ma per
finN~=conN il segnale e' reale.

REAL erosion hotspots 3 anno (drop costr->fin, finN>=8):
| drop | gruppo | materia | con% | fin% |
|---|---|---|---|---|
| -65 | G10 | Consulenza d'immagine | 87 | 22 |
| -47 | G09 | Tecnologia tessile | 80 | 33 |
| -47 | G09 | Merceologia | 80 | 33 |
| -43 | G11 | Informatica | 73 | 30 |
| -40 | G10 | Informatica | 80 | 40 |
| -30 | G09 | Storia della moda | 85 | 55 |
| -22 | G10 | Lingua Italiana | 88 | 66 |
| -20 | G11 | Lingua Inglese | 75 | 55 |
| -20 | G11 | Economia e Diritto del lavoro | 75 | 55 |

VERDETTO: l'erosione della ricorrenza sul 3 anno concentra sulle MATERIE TEORICHE/AULA
(Consulenza d'immagine, Tecnologia tessile, Merceologia, Informatica, Storia della moda, Lingue).
La LS le sparge (le sposta per riempire buchi / bilanciare), erodendo la cadenza settimanale del
template. Le materie strutturate (lab MODA, Servizio tecnico/T01) sono PINNATE a finale ->
ricorrenza alta, nessuna erosione.
IPOTESI di meccanismo (da verificare): M32 e' scarcity-graduated (peso = clamp(100k/|celle VRP|,
20, 500)); le materie teoriche hanno MOLTE celle settimanali -> peso BASSO (verso 20) -> la LS le
muove liberamente scambiando poca penalita' M32 per il riempimento. Le lab (poche celle, risorsa
scarsa) hanno peso alto (verso 500) e restano difese. Coerente col quadro: la LS ridefinisce le
aula per riempire i buchi (G11 ven, full-day), pagando ricorrenza sulle teoriche.

ADDENDUM anomalia G11 2027-01-22 (root-cause, read-only): il venerdi vuoto (exp 8 got 0) e' un
giorno di STAGE (class_days modality=S). Il golden piazza 8h di stage li'; il run 0. E' esattamente
il -8h stage di G11 (SPIKE 8b) concentrato su quell'unico venerdi ON-GRID: il budget di stub
stage si esaurisce (sabati off-grid + altre date on-grid) prima di arrivare al 2027-01-22 ->
stageSkippedNoBudget. Non e' un buco d'aula, e' contabilita' stage budget (stesso tema SPIKE 8b:
stage off-grid + budget). Venerdi adiacenti (01-15, 01-29) piazzati 8h regolarmente.

CORREZIONE ipotesi M32 (verify hours-proxy, read-only): l'ipotesi "teoriche erose perche' M32
scarcity-graduated dà peso basso a materie con molte celle" NON e' supportata. Le materie erose
spaziano 15-56h (Consulenza 23, Merceologia/Tecnologia tessile 15, Informatica 20-30, Lingue 40-56):
nessuna correlazione pulita ore-erosione. Le non-erose ad alto monte (Confezione 105, Modello 80,
Servizio tecnico 65, Disegno 65) sono LAB. (Nota: le ore totali sono proxy debole della scarsita'
di celle VRP - i lab sono vincolati a stanza/giorno pur avendo alto monte - quindi M32 non e'
confutato del tutto, ma nemmeno supportato.)
DISCRIMINATORE PULITO = STATO DI PIN: le erose sono materie AULA unpinned (la LS le sposta per
riempire buchi / bilanciare H11, scambiando la loro ricorrenza difesa da M32 contro il guadagno
hard/riempimento); le non-erose sono LAB pinned (immobili, ricorrenza alta da pin). Quindi
l'erosione e' il costo che la LS paga sulle aula mobili per riempire i buchi (G11 ven, full-day)
- coerente con TASK-451 (la LS ridefinisce orizzontalmente) e col deficit-full-day. Per confutare
davvero M32 serve il riferimento celle VRP pre-solve (WeeklyTemplatePlanner.buildReference), non
proxyabile da ore.

---

## WORK 2 - TOOLING erosione ricorrenza per-identita' (maintainer 29/07, read-only tool)  [COSTRUITO, verdetto GATED]

PROBLEMA: SPIKE 7 confronta la ricorrenza UNPINNED a fine costruzione vs finale, ma tra i due cut la
pre-assegnazione/promotion pinna ~3090 lezioni, quindi il set "unpinned" NON e' lo stesso insieme
nelle due misure. Parte del delta -1,9pp e' COMPOSIZIONE (il set cambia), non pura erosione LS. Per
separarle serve tracciare la STESSA lezione (per identita') tra i cut, non aggregare per posizione.

VINCOLO TECNICO (verificato): l'identita' di lezione NON sopravvive tra solve separate. Il `Lesson.id`
(DB Panache) e' riassegnato ad ogni generazione; `sessionIndex` e' deterministico solo per le lezioni
a blocco (lab), NON per le regular/aula (sessionIndex=-1) - che sono proprio le materie erose
(Consulenza, Informatica, Lingue). Quindi il tracking per-identita' richiede dump ID-taggati dalla
STESSA esecuzione (un solve flagged). Offline su due dump separati non e' affidabile (mandato).

COSA E' STATO COSTRUITO (commit a seguire, flag default-OFF, niente file protetti):
1. `PhaseTransitionMarker.writeTransitionDumpWithIds` (sotto la flag esistente
   `planner.phase-transition-dump.enabled`, default OFF): dumpa per-fase `phase-N-slug-id.txt` in
   formato `id|group|date|hour|subject|teacher|pinned|promotedPin`, stesso filtro/sort del dump
   6-campi (pairs row-for-row). Rifattorizzato: i 3 dump sibling (placed/boxed/id) delegano ora a un
   unico `writeLessons(suffix, population, sort, rowFn)`. Measurement-only: never-breaks-the-solve.
2. `scripts/analyze/lesson-identity-erosion.py`: confronta due dump ID-tagged per `id`. Modalita'
   `--from/--to` (due cut) e `--dir` (walk phase-*-id.txt, attribuisce il cambio-cell alla fase).
   Report: composition shift (quante escono dal set mobile, splittate authoritative vs
   template-promoted via promotedPin) + cell-retention sul set MOBILE (unpinned a entrambi i cut) +
   breakdown per (gruppo, materia). La cella = (weekday, slotIndex); cambiare DATA a parita' di cella
   NON e' erosione (cadenza preservata, come M32/S13).
3. `scripts/analyze/chain-feasibility-kpi.py` (WORK 1): misura catene depth-2/3 sui buchi HARD.

VERDETTO: NON ANCORA PRODOTTO. Serve UN solve staged con `-Dplanner.phase-transition-dump.enabled=true`
(via `java -jar` con la flag sulla riga di comando, NON MAVEN_OPTS - vedi CLAUDE.md) che produca i dump
ID per-fase (costruzione -> finale, stessa popolazione); poi `lesson-identity-erosion.py --dir
target/phase-transitions` dà il verdetto "quali gruppi/materie perdono la cella settimanale, e in
quale fase", composition-controlled. E' un RUN LIVE -> gate maintainer (regola: nessun run live senza via
esplicito). Alternativa checkpoint: estendere il construction test a solve staged completo su real
seed in H2 (incremento piu' pesante, non fatto).

SMOKE verificato (dump sintetici 8-campi): mobile=5, 1 left (authoritative), 4/5 retention, 1 moved -
tool corretto. NoDoD: /simplify (3 dump sibling collassati in writeLessons + fix onesta' promotedPin
usato davvero) + /code-review high + test PhaseTransitionMarkerBoxDumpTest verde.
