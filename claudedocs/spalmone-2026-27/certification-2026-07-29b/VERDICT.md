# Certificazione TASK-456 (lab-fallback ON + esclusione stanza cap-0) - staged completo

Data: 2026-07-30 (run notturno 23:42 -> 00:27, ~44,4 min). Profilo **dev fresco**,
flag dump `-Dplanner.phase-transition-dump.enabled=true` sulla riga `java -jar`
diretta (PLANNER_JAVA_OPTS, NON MAVEN_OPTS: verificato sul processo). import-
calendario 2859 pin (866 MODA + 1993 stage), pre-assign COMPLETE (no-op: nel
2026/27 LabRotation e' vuota di proposito, DataGenerator.java:1068-1087),
solve-staged portato a **terminazione naturale**.

Questa run **ritira** il VERDICT `certification-2026-07-29/` ("stallo" in
B-TIMESLOT-1 al minuto 26): falso, il ciclo dura ~44-49 min e hard converge solo
nell'ultima fase (vedi CORREZIONE li' e per-fase qui sotto).

**Esito: TASK-456 VALIDATO.** La decomposizione hard e' quella che certifica, non
il totale: con lab-fallback attivo le ordinarie senza aula scendono a ZERO (il
ripiego funziona); i 167 room-null sono tutti lab capacity-starved preesistenti;
i 35 boxed sono il collo di bottiglia T01 (assenza constrained-teacher), non
il pacchetto. Dettaglio sotto.

## Avvio (tutti i cancelli superati)

- Dump mechanism VERIFICATO: file sotto `target/phase-transitions/` ai boundary
  di fase (primo dump construction-end a ~min 1). Non MAVEN_OPTS.
- import-calendario: HTTP 200, **0 modaFailures**, 866 lab MODA + 1993 stage =
  2859 pin, 327h stage off-grid dichiarate (warning "stage-pattern-without-
  calendario" = cross-check report-only, non failure).
- pre-assignment: COMPLETE con "no rotations configured" (atteso, stesso seed
  del reference: nessuna discrepanza).

## DECOMPOSIZIONE HARD (quella che certifica o boccia)

Con la contabilita' TASK-447 (**hard = boxed x2 + room-null x1**):

| componente | reference (task447/cert) | **questa run** | delta hard |
|---|---|---|---|
| boxed (H0, non collocate) | 3 (x2 = 6) | **35** (x2 = 70) | **+64** |
| room-null NON-LAB (attese 0 col ripiego) | 7 | **0** | -7 |
| room-null LAB (capacity-starved) | 182 | **167** | -15 |
| **hard totale** | **-195** | **-237** | **+42** |

Riconciliazione: -237 = 35x2 + (0+167)x1 = 70 + 167. Delta +42 = boxed +64 -
room-null -22. **Tutti gli altri hard = 0** (H1/H3/H5/H7/H11/H17/H20/H23/H24/
H25/H26/H28/H-M2): posizionamento timeslot pulito a parte i 35 boxed.

**Cosa dice questo:**
1. **room-null NON-LAB = 0.** Con lab-fallback attivo (M-LF) NESSUNA lezione
   ordinaria resta senza aula: il ripiego (ordinaria in stanza tagged come
   ultima risorsa) FUNZIONA. Il reference (pre-lab-fallback) ne aveva 7. Questo
   e' il fatto che certifica il pacchetto.
2. **room-null LAB = 167**, tutti acconciatura (Lavaggio 58, Servizio tecnico e
   stilistico 45, Servizio stilistico 36, Barberia 4) + Laboratorio Termoidraulico
   24. Lab capacity-starved NON de-taggabili (pratica su cliente), preesistenti e
   identici per composizione al reference (182, stessi soggetti in testa).
3. **boxed = 35, catena dimostrata sul DB (NON dal pacchetto aule: H0 e'
   timeslot-tier, le fasi orari girano cieche alle stanze prima di B-ROOM):**
   - I 35 boxed sono TUTTI "G06 / Servizio tecnico e stilistico / T01".
   - T01 e' giovedi-only (le 25 collocate di G06 sono tutte in
     dow=giovedi); insegna 231h a 4 classi (G02 60, G06 60, G10 65, 4
     FD 46) tutte di giovedi, capacita' 288h (36 Thu x 8h). G02 / G10 / 4
     FD collocate per intero (0 boxed); solo G06 perde 35h su 60.
   - Perche' proprio G06: il suo giovedi e' SATURO. Finestra G06 Thu = 6
     slot x 36 = 216 slot, di cui 211 gia' occupati (186 altre materie + 25
     T01) -> ~5 slot liberi. T01 deve collocare 60h, ne ha messe 25,
     i restanti 35 non trovano cella legale (slot Thu liberi di G06 ~0-5 E in
     quegli slot T01 e' occupato dalle altre 3 classi: conflitto docente H1
     incrociato con saturazione gruppo).
   - Collo di bottiglia DOPPIO timeslot-tier: T01 giovedi-only (conflitto
     H1 tra 4 classi) INTERSECATO con la saturazione del giovedi di G06. Il
     reference ne aveva 3 (usava constrained-teacher, pre-assegnazione Phase-0
     che questa run non ha: certificava TASK-456 room-tier).
   - **Previsione confermata**: V4b di Tappa 5 marcava T01 80,2% come
     collo di bottiglia sistemico. Il numero esatto 35 e' traiettoria di questo
     solve (n=1): un SECONDO solve dira' se e' stabile (strutturale) o varianza.

**Nessun residuo e' imputabile a TASK-456.** Il peggioramento hard (+42) viene
dai boxed (T01, constrained-teacher), parzialmente compensato da room-null
migliorato (-22, lab-fallback + traiettoria).

## R18 (cap 0): l'esclusione NON peggiora hard

L'esclusione `Room.isAssignable` (capacity > 0) toglie dal dominio AULA
POLIVALENTE (cap 0), che nel reference ospitava ~204 lezioni (aula). Verifica:

- **R18 = 0 assegnazioni** in questa run (esclusione attiva).
- room-null TOTALE = 167, inferiore ai 189 del reference E inferiore ai 204
  spostati. I 204 spostati erano lezioni **aula**: assorbite dalle altre aule,
  non finite in room-null. I 167 room-null sono **lab** (acconciatura/
  termoidraulica), slegati da R18.

Quindi l'ipotesi "il peggioramento hard viene dall'esclusione di AULA
POLIVALENTE" e' **REFUTATA**: room-null e' migliorato (189 -> 167), non
peggiorato. Il +42 hard e' tutto boxed (T01). L'esclusione cap-0 e' in
costo ZERO su hard: costo previsto e accettato, anzi qui nemmeno si paga.

## MEDIUM (-1089090 vs -952724, delta -136366): distinto per TIER, non a occhio

M-LF pesa 1000/lezione, 51 match = -51000: da solo NON spiega 136k. Diff
vincolo per vincolo (mio analysis.json vs reference):

| vincolo | tier | reference | questa run | delta |
|---|---|---|---|---|
| **M-LF** (lab-fallback) | ROOM | 0 | -51000 | **-51000** (il pacchetto, atteso) |
| **M37** (cambio stanza a meta' blocco) | ROOM | -17600 | -31600 | **-14000** |
| M20 (riempimento giornaliero) | TIMESLOT | -199500 | -250000 | -50500 |
| M15 (same-day same-subject consecutivo) | TIMESLOT | -12000 | -27600 | -15600 |
| M22 (compattezza calendario) | TIMESLOT | -562000 | -568000 | -6000 |
| M32 (deviazione template) | TIMESLOT | -114419 | -109601 | +4818 (migliorato) |
| M35 (spread cadenza) | TIMESLOT | -19950 | -18100 | +1850 (migliorato) |
| M3 (capienza stanza) | ROOM | -760 | -531 | +229 (migliorato) |
| altri | | | | ~ -7k |
| **TOTALE medium** | | **-952724** | **-1089090** | **-136366** |

**Distinzione per tier (la parte che certifica):**
- **Tier-ROOM (dove vive TASK-456, B-ROOM): M-LF -51k + M37 -14k, netto ~-65k.**
  M-LF e' il pacchetto stesso (lab-fallback penalizza 51 ordinarie nei lab, by
  design: "lab = ultima risorsa"). M37 e' il riassetto stanze forzato
  dall'esclusione cap-0 (le 204 aula spostate aumentano i cambi a meta' blocco):
  effetto reale del pacchetto, piccolo, watch-item (gia' notato nel reference).
- **Tier-TIMESLOT (M20 -50,5k + M15 -15,6k + M22 -6k = ~-72k): NON e' TASK-456.**
  TASK-456 e' room-tier (B-ROOM, corre DOPO B-TIMESLOT): non puo' toccare il
  riempimento giornaliero o la consecutivita' same-day, decisi in B-TIMESLOT.
  Questo scarto traccia alla **assenza di constrained-teacher** (il reference lo
  aveva ON: pre-assegna Phase-0, migliora la struttura timeslot) + varianza di
  traiettoria fra run. Per isolare TASK-456 totalmente servirebbe un A/B a
  constrained-teacher costante (un altro solve da 44 min): ma l'attribuzione per
  tier e' sufficiente a dire che il pacchetto non regressa il timeslot.

## SOFT (3076 vs 25758, delta -22682): timeslot-tier, non TASK-456

Lo soft e' quasi tutto timeslot-tier (S1 gap -123720, premi S7 +102290 / S17
+17575 / S13 +14442). Mine ha premi piu' bassi = struttura timeslot meno ricca.
Room-tier (TASK-456) non lo tocca: traccia a constrained-teacher assente +
varianza, come il medium timeslot.

## Per-fase: hard converge SOLO nell'ultima fase

| fase | durata | hard inizio -> fine |
|---|---|---|
| 1. CH Lesson Placement | 0,3 min | 0 -> -2568 |
| 2. LS Compactness (construction) | 24,4 min | -2568 -> -2354 |
| 2. LS Compactness (B-TIMESLOT-1) | 6,5 min | -2353 |
| 2. LS Compactness (B-TIMESLOT-2) | 3,0 min | -2355 -> -2354 |
| 4. CH Room Assignment | 0,0 min | -> **-9670** (rooms entrano, spike room-null) |
| 5. LS Room Refinement | 10,0 min | **-9670 -> -237** (converge qui) |

hard congelato ~-2354 per ~30 min nelle fasi timeslot, poi converge solo nei 10
min finali di B-ROOM (-9670 -> -237). Identico al reference (-9407 -> -195).
Giudicare la convergenza a meta' ciclo e' un errore di metodo.

## Batteria KPI vs golden (e vs reference)

| KPI | golden | reference (task447) | **questa run** |
|---|---|---|---|
| full-day | 85,4% | 80,8% | **78,3%** (84,3% senza G14) |
| day-start ancorato | ~99,3-100% | 99,3% | **99,2%** |
| ricorrenza unpinned | (golden 41,5% stretta) | 74,4% | **75,2%** (meglio) |
| buchi interni | 0 | 0 | **0** |
| cadenza burst>2x | ~41,6% | - | **38,5%** (burst medio 2,09) |
| sharpness docenti | - | - | 87,5% pooled / 84,9% macro |
| moda-knot (stacked H3) | 0 | - | **0 (PASS)** |
| G11 venerdi/mercoledi ritardi | 0 | 15 | **14 mer + 1 ven** (persiste) |

full-day 78,3% e' ~2,5 pt sotto il reference: coerente con l'assenza di
constrained-teacher (il reference lo aveva), non con TASK-456 (room-tier).
Senza G14 (esente nel motore, Q16) si risale a 84,3%, in linea col golden.

## Cosa consegna questa run

1. **Certificazione TASK-456**: VALIDATO. lab-fallback porta room-null non-lab a
   0; R18 cap-0 = 0 assegnazioni; costo medium del pacchetto = M-LF
   -51k (by design) + M37 -14k (riassetto cap-0, watch-item).
2. **Dump di TUTTE le fasi solver** in `phase-transitions/` (construction LS,
   compaction, CH room assignment, LS room refinement, ciascuno con boxed/id/
   resemblance): per **task 459** (costruzione), **task 450** (ritardo G11),
   **task 460** (ricombinazione).
3. **Dump finale fresco** per il ritmo dei tier del **task 457** (analysis.json
   con M-LF + tutti i medium, score per-fase in phases.json).

## Domanda aperta (non blocca)

Per isolare TASK-456 dal confound constrained-teacher sui KPI timeslot (full-day
78,3 vs 80,8, medium M20/M15), servirebbe un A/B a constrained-teacher costante
(un secondo solve da ~44 min). L'attribuzione per tier gia' dice che il pacchetto
non regressa il timeslot, ma l'A/B lo chiuderebbe formalmente.

## File in questa directory

`VERDICT.md` (questo), `score.txt`, `analysis.json`, `phases.json`, `lessons.txt`,
kpi-* (fill, daystart, daystart-anatomy, resemblance, holes, cadence, sharpness,
moda-knot), `confronto-*.html`, `day-start-anatomy.html`, `phase-transitions/`
(dump di tutte le fasi solver + resemblance). (export.json.gz e' gitignored,
rigenerabile.)
