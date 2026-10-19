# Censimento di deducibilita' del debito golden-runtime TASK-452 (01/08/2026, zai)

LAVORO DI FINESTRA CHE SI CHIUDE. Direttiva (CLAUDE.md "la soluzione gira sul SOLO
INPUT, il golden e' temporaneo"): in esercizio la scuola ci passera' i dati di INPUT,
non una soluzione di esempio. Ogni meccanismo che a runtime legge il golden
(`seed/lessons.csv` o i JSON derivati in `data/`) ha una data di scadenza. Per ognuno
valgono due esiti onesti: (1) cio' che estrae e' **deducibile dagli input**, allora si
cambia sorgente e si verifica la coincidenza ORA (finche' il golden esiste); (2) non e'
deducibile, allora e' una **pratica della scuola da dichiarare** nel workbook
(TASK-445/471) con riga, grain e default.

Questo doc e' il complemento quantitativo di `censimento-dipendenze-golden-2026-07-30.md`
(che fermo' i verdetti in forma qualitativa). Qui, per OGNI lettura runtime, si
**ricostruisce il valore estratto dai soli input** e lo si **confronta col golden**
(match %, mismatch). Read-only: niente codice solver, niente solve. Script di misura:
`/tmp/measure_task452_deducibility.py` (monouso, specchio 1:1 della logica Java di
estrazione; non nel repo).

## Perimetro (letture runtime del golden nel main)

| # | Sito (file:symbol) | Task | Stato | Sorgente letta |
|---|---|---|---|---|
| 1 | `ConstrainedTeacherAllocator.loadAllGoldenProfiles` (chiamato da `HybridPhase1aService:660`) | TASK-435 | **DEFAULT ON, produzione** | `seed/lessons.csv` |
| 2 | `HybridPhase1aService.buildMonoRoomCadence` (+ `MonoRoomTagAllocator`) | TASK-449 / SPIKE 2 | flag OFF | `seed/lessons.csv` |
| 3 | `ScheduleService.publishCadenceReferenceStatics` (M34/M35) | TASK-425 | self-gate (inerte se assente) | `data/golden-cadence-reference.json` |
| 6 | `ConstrainedTeacherAllocator.loadGoldenSlot0Anchors` (chiamato da `WeeklyTemplateBuilder:557`) | TASK-451 | flag OFF | `seed/lessons.csv` |
| 7 | `ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth` (chiamato da `ScheduleService:1841`) | TASK-464 / H-COPRESENCE | flag OFF | `seed/lessons.csv` |

Escluso: `GoldenScheduleImportService.importGolden` (TASK-346, `POST /import-golden`) e'
lo **strumento di import**, non una derivazione: in esercizio input-only non viene mai
chiamato. Non e' debito. Confirma il censimento 2026-07-30 (#4). Nessun'altra lettura
runtime del golden nel main oltre a queste 5 (grep `lessons.csv`/`SeedCsv`/`loadGolden`/
`golden` su `src/main/java`).

Legenda verdetti: **ESITO 1** = sostituibile a sorgente input (la verifica di coincidenza
si fa ora). **ESITO 2** = non deducibile, diventa riga workbook con default = valore golden.

---

## #1 loadAllGoldenProfiles (TASK-435, DEFAULT ON, produzione) - ESITO 1 (sostituibile senza chiedere)

**Cosa estrae.** `teacher -> group -> date -> ore` dal golden. La fase di
pre-assegnazione ne consuma il profilo di T01 per pinnare i suoi giovedi'
(`plan()` produce `PinTarget(group, date, hours)`, validati H5/H7/budget/disgiunzione).

**Misura ( ricostruzione dai soli input vs golden).**
- WHO e' input: T01 e' in `incarichi.csv`.
- WEEKDAY e' input: solo-giovedi' dalla H5 VACATION in `availability_rules.csv`
  (confermato da maintainer 2026-07-30, non piu' "in attesa").
- TOTALI per gruppo: **golden == incarichi al 100%**. NOTA di onesta'
  (verificata in code-review del patch 01/08): questa coincidenza NON e' prova indipendente di
  deducibilita', perche' TASK-371 normalizzo' `incarichi.csv` AL golden (referente: golden
  autoritativo, incarichi stale). Quindi i totali coincidono per costruzione. Il totale e' input
  PER DEFINIZIONE (il monte-ore di contratto che la scuola dichiara nel workbook); cio' che e'
  genuinamente indipendente dal golden, e quindi prova la sostituibilita', e' il WEEKDAY (H5,
  regola confirmata da maintainer) e il CALENDARIO (Timeslot), che decide quali giovedi' sono validi.

| gruppo | golden (h) | incarichi (h) |  |
|---|---|---|---|
| G02 | 60 | 60.0 | MATCH |
| G06 | 60 | 60.0 | MATCH |
| G10 | 65 | 65.0 | MATCH |
| G12 | 46 | 46.0 | MATCH |
| **TOTALE** | **231** | **231.0** |  |

- La distribuzione **per-data** (quali giovedi' -> quale gruppo -> quante ore) NON e'
  ricostruibile: 35 giovedi' golden, distribuzione ore/giovedi' `{5:2, 6:21, 7:1, 8:11}`
  (per lo piu' 6h, a volte 8h monografici). Una ricostruzione greedy (un gruppo per
  giovedi', H7=8, sui 36 giovedi' scolastici in sessione) sovrappone date col golden
  solo **8/35 = 23%**: le date specifiche sono una scelta del golden.

**Verdetto.** La parte **load-bearing** (chi, giorno, totale-per-gruppo) e' **deducibile
al 100%** dagli input. La data specifica e' una scelta **non load-bearing**: il pin
funziona con qualunque giovedi' valido (la `plan()` non controlla la data, solo
H5/H7/budget; il golden le fissa anche per farle coincidere col calendario del gruppo).
ESITO 1: la lettura e' sostituibile a sorgente input **senza chiedere nulla alla scuola**.

**Sostituzione proposta (implementazione in m-13, NON ora).** Sostituire
`loadAllGoldenProfiles` con un piano derivato da: H5 weekday + totali `incarichi` +
giovedi' scolastici in sessione filtrati per `isNoSessionDate`/stage del gruppo. L'unica
cosa che il golden portava implicitamente e che manca agli input e' il **filtro
calendario del gruppo** (il greedy cieco potrebbe scegliere un giovedi' no-session/stage
del gruppo): va aggiunto esplicitamente. Verifica di coincidenza fattibile SOLO finche'
il golden esiste: confrontare il piano ricostruito col golden sui KPI del solve
(slack +5h, 3 boxed) invece che sulle date (il 23% e' atteso e irrilevante).

### Verifica run source=input (zai, 2026-08-01 alba) - ESITO 1 NON CONFERMATO su n=1

Run di verifica del flip (protocollo combined-run2 + `-Dplanner.pre-assign.constrained-teacher.source=input`
su riga `java -jar`, banner `TASK-452 source=input` confermato nel log, import 2859 pin identici).
Risultato vs combined-run2 (source=golden): **hard -28 (7 boxed H0 + 14 H20) vs 0; full-day 87,1%
vs 90,4%; medium -22k**. Ricorrenza (75,7% vs 75,8%) e day-start (99,5% vs 99,5%) **flat**.
Dettaglio e diagnosi: `task452-results/verify-source-input-run1/VERDICT-2026-08-01.md`.

**La riga sopra ("il 23% e' atteso e irrilevante") e' REFUTATA end-to-end (causal-chain, dopo
correzione Fedora):** le 21 hard sono SISTEMATICHE, non varianza. (Correzione Fedora: la banda
0 .. -169 dei run golden era boxed/room-null H29, NON H20; H20 > 0 su un staged che chiude H20=0 e'
causa sistematica.) Tutte e 21 le hard sono T01: 14 H20 = pinned T01/G06 a 14:30+15:30
(slot 6-7) su 7 giovedi'; 7 H0 = T01/G02. **Meccanismo tracciato:** la mappa statica
GroupCalendarRule e' vuota a pre-assign (`setGroupCalendarRulesStatic` solo in refreshSolverStatics,
solve-time), quindi `getGroupCalendarWindow` (:813) torna default fully-open, `openSlotsForGroupDay`
(:1209) non filtra gli slot chiusi; il profilo input-derived assegna 8h/data (h7Cap=8) che ricade
sui slot 6-7 chiusi di G06 giovedi' (GCR maxslot 4). Il golden evade perche' i suoi blocchi sono
<=6h (mattina). Dettaglio: `task452-results/verify-source-input-run1/VERDICT-2026-08-01.md` (DIAGNOSI).

**Verdetto (aggiornato, systematic -> CURA DENTRO):** (1) source=input aveva un **bug sistematico**
(pin su celle calendario-chiuse) -> flip m-13 era NON pronto; (2) NON e' varianza (H20 e'
qualitativo, tutte pinned sul docente vincolato); (3) la CURA e' in `buildInputDerivedProfile`:
cappare le h/data al window calendario-gruppo REALE (la mappa statica a pre-assign e' vuota), stesso
knob `source=input`, **default golden INVARIATO** (byte-identical: source=golden non usa quel builder),
+ test di equivalenza aggiornato con asserzione "nessun pin input-derived su cella chiusa" (DEVE
catturare il caso). **CURA DENTRO + DoD CLEAN (commit `4f5cd9d0`):** il profilo cappa le h/data agli
open-slot reali del GroupCalendarRule; test RED->GREEN (G02 8h>6 open pre-cura); `/simplify` +
`/code-review high` (skeptic Opus CONFIRMED nessun pin su cella chiusa + golden result-identical) +
`./scripts/core/test.sh` 928/0/0. **Downgrade ESITO 1 definitivo:** il PROFILO e' sostituibile, la
SOLUZIONE no; il limite latente (riga 82 "filtro calendario... va aggiunto esplicitamente") e' ora
RISOLTO end-to-end. **PROVA FATTA (run3 + paired-golden, post-cura+473, 2026-08-01):** source=input chiude
**hard -6** (3 unassigned = residuo TASK-435) vs paired-golden **0**; full-day 85,2% vs golden
90,2%; ricorrenza unpinned 75,9% (migliore dei 3, > golden 75,2%); 0 H20/H29/holes. Numeri
verificati dai raw (`verify-source-input-run3/`: score.txt -6hard, analysis.json H0 matches=3,
kpi-fill 1643/1928=85,2%, kpi-resemblance 7173/9446=75,9%, kpi-holes 0).
**DECISIONE m-13 (maintainer -> Fedora, 2026-08-01): FLIP GATED A PARITA', non ora.** Obiettivo
ultimo = 0-hard; run3 e' valida e vince sulla ricorrenza MA non chiude hard 0 e perde 5pp
full-day, quindi flippare oggi regredirebbe il riferimento. Default resta golden, source=input
resta opt-in. GATE: flip quando un run protocollo-run3 chiude hard 0 E full-day nel range del
golden appaiato. Percorso verso il gate = raffinamento profilo input SENZA leggere il golden
(diagnosi 3 unassigned + deficit full-day concentrato G04 54% / G10 30%). La dichiarazione
"flip PRONTO" del VERDICT run3 era point-in-time (blocker AnchorRepair risolto, run valida);
l'adozione e' GATED, percorso di raffinamento aperto. Dettaglio decisione: commento TASK-452
#5 (2026-08-01 09:00).

### run4 + DECISIONE m-13 AGGIORNATA (Fedora -> zai, 2026-08-01 sera) - FLIP = READY-WHEN-NEEDED

**run4 (post-cura stage-aware interleaved, commit `04900742`) chiude HARD 0** (n=1; score
`0hard/-1018024medium/8359soft`, 0 unassigned nel DB, 0 constraint con negativeHard). Il
bloccante hard-0 del gate e' RIMOSSO: G10 e' pinnato su 9 Thursday non-stage (la cura
replica la fattibilita' golden dal solo input). Dettaglio: `task452-results/verify-source-input-run4/VERDICT-2026-08-01.md`.

**run5 n=2 (2026-08-01 sera, protocollo identico; reset+re-import = fresh-reseed PG17, pre-solve
byte-identico a run4: 2882 authoritative pin) NON conferma hard-0: chiude -38hard** (16 unassigned:
G03/T08/Matematica 11 + G06/T19/Barberia 5; + 6 H17). Varianza della LS, NON della cura
(G10 collocato, il problema stage-blind e' chiuso). Il path input e' feasible (run4 hard-0) ma
**NON robustamente hard-0**: la LS puo' strandare ~16 lezioni su capacita' stretta. Dettaglio:
`task452-results/verify-source-input-run5/VERDICT-2026-08-01.md`.

**DECISIONE m-13 AGGIORNATA (Fedora, 2026-08-01 sera; SUPERSEDES il "GATED A PARITA'" qui
sopra): FLIP = READY-WHEN-NEEDED, non piu' gated su hard-0.** Il flip non si fa oggi non per
un gate ma per scelta: finche' il golden esiste, il default golden-derived e' **strettamente
migliore**, e run5 lo **rinforza** non solo su full-day (90,2% vs run5 89,5%/run4 87,9%) e medium
(-983k vs -1012/-1018k) ma anche su **robustezza hard** (golden hard-0 affidabile su
paired-golden + ab-cps-on-run1; path input con varianza 0/-38 in n=2). Il valore del lavoro input
e' la **PRONTEZZA**: la strada e' feasible quando servira' (a regime post-golden), ma la ricerca
deve rendere hard-0 affidabile (staged repair / BoxRepair piu' risolutivo sulle tight-capacity
Math/Barberia) prima che sia un drop-in. Resta il gap full-day con leve tracciate (TASK-476:
FC-venerdi' school-gated con contributo 0 al gap, gap reale su G10+G12 aggressibile): e' lavoro di
RICERCA, non di modello (la direttiva "correttezza logica vs costo" regola il modello, il costo si
attacca lato ricerca). Default solver: golden; source=input opt-in, **adozione ready-when-needed**
(non gated su hard-0).

---

## #2 buildMonoRoomCadence (TASK-449 / SPIKE 2, flag OFF) - ESITO 2

**Cosa estrae.** Per ogni (gruppo, tag mono-stanza) le celle `(weekday, startSlot,
hours)` dominanti (shape modale sulle date del golden), usate per pre-pinnare i blocchi
lab su celle disgiunte cosicche' l'unica stanza del tag non sia mai doppia.

**Misura.** La DOMANDA (ore lab per (gruppo, tag)) e' deducibile da `incarichi.csv`:

| tag | domande per gruppo (incarichi, h) |
|---|---|
| acconciatura | G02 186, G06 212, G10 170, G12 121, G14 30 |
| termoidraulica | G03 235, G07 24, G11 54 |
| meccanica | G04 152, G08 110, G13 140 |

Le CELLE `(weekday, startSlot, hours)` **non sono in nessun input popolato**:
`LabRotation` (il veicolo input inteso) e' **VUOTO INTENZIONALMENTE** per il 2026/27
(`DataGenerator.generateLabRotations`: "seeded with NOTHING ... empty LabRotation makes
HybridPhase1aService fallback to golden"). Golden: 14 celle modali acconciatura su 5
gruppi, 7 termoidraulica su 3, 10 meccanica su 3. Esistono solo nel golden.

**Verdetto.** ESITO 2. Le celle sono una pratica della scuola non deducibile.

**Sostituzione proposta (workbook).** Popolare `LabRotation` (il veicolo esiste gia').
Grain `(group, roomTag, weekday, startSlot, blockHours)`; default = cella modale golden
per (gruppo, tag). Domanda referente: per ogni gruppo e lab monostanza
(acconciatura/termoidraulica/meccanica), quale giorno settimanale e quale fascia
(inizio + durata)? Fatto questo, `buildMonoRoomCadence` cambia sorgente (TASK-452).

---

## #3 publishCadenceReferenceStatics / M34/M35 (TASK-425, self-gate) - ESITO 2 (condizionale)

**Cosa estrae.** Dal JSON `golden-cadence-reference.json` (golden-derived): per ogni
`(gruppo, materia)` il `max` (burst cap settimanale, M34) e le `active_weeks`
(settimane attive, M35). Self-gate: JSON assente => mappe vuote => M34/M35 inerte.

**Misura.** Gli input danno solo `total_hours` per materia. Caducamente deducibile
solo se la scuola spalmasse in piano: `max == round(total/active_weeks)` coincide in
**72/245 = 29%** delle coppie (>=10h); entro 1h di scarto **190/245 = 78%**. Nel 22%
restante il `max` golden e' **materialmente sopra** la media derivabile, cioe' sono i
burst **deliberati** della scuola. Esempi (gruppo, materia, tot_h, avg_h, max_h, sett_attive):
G01 Lingua Italiana 90 / avg 2.6 / **max 6** / 34; G01 Modello (Moulage) 128 /
avg 3.6 / **max 6** / 36; G01 Figurino 45 / avg 1.6 / **max 4** / 28.

Una derivazione flat-avg sarebbe quindi **piu' stringente del golden** sulle coppie che
la scuola concentra di proposito (cappellerebbe i burst legittimi). Non coincide.

**Verdetto.** ESITO 2 (condizionale). Il burst cap e' una scelta di pacing non
deducibile. Ma M34/M35 self-gate inerte senza il JSON: nessun danno se non dichiarato.

**Sostituzione proposta (workbook, condizionale).** Grain `(group, subject)` con colonne
`maxHoursPerWeek`, `activeWeeks`; default = golden. Dichiarare **solo se la scuola vuole
i capped settimanali per materia**. Domanda referente: volete i capped settimanali per
materia? Se si', ore/settimana max e settimane attive per (gruppo, materia).

---

## #6 loadGoldenSlot0Anchors (TASK-451, flag OFF) - ESITO 2

**Cosa estrae.** `(gruppo, weekday) -> docente modale allo slot 08:00` (chi apre il
giorno). Usato dal `WeeklyTemplateBuilder.reserveFirstHourBlock` come preferenza di
matching cross-gruppo per il blocco prima-ora.

**Misura.** 67 celle (gruppo, weekday) con una lezione slot-0; **48/67 = 72%** hanno un
docente modale a maggioranza rigorosa (la pratica e' reale, non rumore). Esempi:
G11 venerdi' -> T08 (26/26, perfetto); G01 giovedi' -> T13 (33/36); 1
FA mercoledi' -> T18 (30/35). Le regole di disponibilita' codificano i weekday
**disponibili** del docente, non "questo docente apre il giorno di questo gruppo": nessun
input lo esprime. E' una scelta di posizionamento emergente.

**Verdetto.** ESITO 2. "Chi apre il giorno" non e' deducibile dagli input.

**Sostituzione proposta (workbook).** Grain `(group, weekday) -> openingTeacher`; default
= docente modale golden (72% stabile). Domanda referente: per ogni (gruppo, giorno), quale
docente apre la prima ora? (Veicolo alternativo: `RecurringAvailabilityRule`, ma la
semantica e' disponibilita', non assegnazione, quindi la riga workbook e' piu' onesta.)

---

## #7 loadGoldenCoPresenceDepth (TASK-464 / H-COPRESENCE, flag OFF) - ESITO 2

**Cosa estrae.** `(tag, weekday, slotStart) -> n. gruppi distinti co-presenti` sul golden.
La depth (>=2) di una cella e' la pratica che l'esenzione a coppie di H2 sblocca
(ospite condivide la stanza dell'host) e il soffitto che il cap H-COPRESENCE impone.

**Misura.** 136 celle lab con almeno un gruppo; **91 celle con depth >= 2**. Tag
distinti con depth >= 2: **acconciatura, informatica, meccanica, moda, termoidraulica**
(5 tag, non solo acconciatura). Depth massime: informatica ven 09:00 **depth 6** (G02,
G03, G04, G08, G09, G10); acconciatura gio 08:00-12:00 **depth 5** (1/2/3
FA + G12 + G14).

La depth **non e' deducibile dal numero di stanze**: stanze per tag
(`DataGenerator.ROOM_TAG_MAP`) = acconciatura 1, termoidraulica 1, meccanica 1, moda 1,
informatica 2. In **tutti** i tag la depth golden (2-6) supera le stanze (1-2). Cio' che
si legge e' quindi co-presenza ospite deliberata (piu' gruppi della capienza fisica
condividono la stanza dell'host tramite docenti ospiti), non derivabile da stanze,
incarichi, calendari o quote.

**SIDE-FINDING (doc-hygiene, non verdetto).** Il javadoc di `loadGoldenCoPresenceDepth`
dice "the only tag with depth >= 2 is acconciatura, on the Mon AM and Thu cells". E'
**stale**: la lettura estrae depth per **tutti** i lab single-tag (filtra solo
`subjectToTag`, nessun filtro acconciatura) e la pratica spans 5 tag e 91 celle. Lo
spannuseum e' verificato dal path di pubblicazione (`ScheduleService:1838-1845`, nessun
filtro) e dalla logica di estrazione (specchio 1:1 nello script). Il javadoc riflette
l'indagine ristretta dello spike, non l'estrazione effettiva. Da correggere quando si
tocca la classe (m-13); non blocca il censimento.

**Verdetto.** ESITO 2. La co-presenza per cella non e' in nessun input.

**Sostituzione proposta (workbook).** Grain `(tag, weekday, slotStart) -> depth` (o
lista gruppi co-presenti); default = depth golden.

**Domanda referente: NON nuova.** La co-presenza e' gia' coperta: Q14 (due classi nello
stesso lab), Q19/Q20 (G14 e G12 ospiti in acconciatura) e il thread "44
sovrapposizioni" in `domande-brevi.md`. Anzi, la risposta di referente del 29/07 ("una
classe in laboratorio, le altre no. I laboratori sono uno per specialita'") e' in
**tensione** con la depth-5 del golden su acconciatura-giovedi: e' proprio il caso
ospite che l'esenzione H2 e il cap H-COPRESENCE governano. Nessuna nuova domanda; la
pratica va solo dichiarata nel workbook col default golden.

---

## Domande referente: nuovezza (verifica fatta)

Per ognuna delle 4 letture ESITO 2, la riga workbook ha un **default golden gia'
calcolato** (e' il valore della finestra: lo estraiamo ORA dal golden, non serve referente
per produrlo). La domanda referente serve solo a confermare/selettare la pratica. Nuovezza:

| # | Pratica da dichiarare | Domanda referente | Nuova? |
|---|---|---|---|
| 2 | celle lab mono-stanza (LabRotation) | giorno/fascia per (gruppo, lab) | no (workbook col. c 2026-07-30) |
| 3 | cadenza settimanale (M34/M35) | volete i capped? | no (condizionale, 2026-07-30) |
| 6 | chi apre il giorno (slot-0) | docente per (gruppo, weekday) | **SI (nuova)** |
| 7 | co-presenza ospite (depth) | gruppi co-presenti per cella | no (Q14/Q19/Q20 + 44-sovrappos.) |

L'unica domanda **nuova** e' #6 (chi apre il giorno). E' legata a TASK-451 (flag-OFF,
parcheggiato: la cura prima-ora coordinata e' in colonna 2/3 del QUADRO, non adottata).
**Non la inietto nel pacchetto referente in partenza** (menu c: rotazione/tensioni + bucket-C
+ Q20): il workbook e' il veicolo giusto per una dichiarazione di pratica, e il default
golden (mappa slot-0 modale, 72% stabile) e' gia' calcolato e pronto da depositare. La
registro qui come item differito: se TASK-451 si riattiva, la riga workbook e' `(group,
weekday) -> openingTeacher` con quel default.

## Sintesi

| # | Lettura | Stato | Verdetto | Serve riga workbook? |
|---|---|---|---|---|
| 1 | loadAllGoldenProfiles (T01) | **DEFAULT ON, produzione** | **ESITO 1** (sostituibile) | NO (H5 + incarichi + filtro calendario) |
| 2 | buildMonoRoomCadence (mono-room) | flag OFF | ESITO 2 | SI: popolare `LabRotation` |
| 3 | golden-cadence-reference (M34/M35) | self-gate | ESITO 2 condizionale | SI solo se la scuola vuole i capped |
| 6 | loadGoldenSlot0Anchors (prima ora) | flag OFF | ESITO 2 | SI: `(group, weekday) -> opening teacher` |
| 7 | loadGoldenCoPresenceDepth (ospite) | flag OFF | ESITO 2 | SI: `(tag, weekday, slot) -> depth` |

**Headline.** L'unica lettura sul percorso di produzione (#1, DEFAULT ON) e' la sola
**sostituibile senza chiedere nulla alla scuola**: chi/giorno/totale sono input al 100%,
la data e' scelta non load-bearing. Le altre 4 sono flag-OFF/self-gate (non in produzione
oggi) e pratiche da dichiarare: nessuna urgenza, ma il debito e' censito, non nascosto.
Tutte le verifiche "input-vs-golden" possibili sono fatte nella finestra; il census qualitativo
del 2026-07-30 e' ora quantificato. La sostituzione effettiva (code) e' lavoro m-13:
questo doc le formula, non le implementa.
