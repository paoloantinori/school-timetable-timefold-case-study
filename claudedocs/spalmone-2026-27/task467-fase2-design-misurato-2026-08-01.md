# TASK-467 FASE 2: enumerazione delle rotazioni regolari - DESIGN MISURATO (zai, 2026-08-01 notte)

Read-only, nessun codice, nessun solve. DESIGN pronto (questo doc); **implementazione resta GATED**
all'apertura esplicita di maintainer (fase 1 input-driven consegnata prima). Mandato: doc
`modello-rotazione-acconciatura-2026-07-31.md` (4 parti) + estrazione `task467-results/`.
HEAD `feature/cartesian-block-entity`. Script di misura: `/tmp/measure_task467_candidatespace.py`
(riproducibile, legge solo i file di input; provenienza onesta in HEADLINE: 2 dei 4 docenti hanno
regole availability golden-derived Q22).

## HEADLINE (verdetto misurato)

Lo spazio delle rotazioni regolari di acconciatura, misurato dagli input, e' **piccolo e
enumerabile** a granularita' **periodo** (non settimana): il branching per-teacher per-periodo e'
al piu' **120** (T26), gli altri 4/4/36, tutti `< 10^4`. La timeline cresce solo moltiplicando
i periodi, ed e' li' che servono le **potature** (tutte input-driven, elencate sotto). L'unica
granularita' che esplode e' quella **per-settimana** (`B^36 >> 10^20`), ed e' **irrilevante** perche'
la regolarita' (modello di maintainer, strutturale) **vieterebbe** cambiare gruppo ogni settimana: la
depth di una rotazione e' il **numero di periodi (~7)**, non il numero di settimane (~36). Insomma:
l'enumerazione esaustiva e' **fattibile** riattivando uno skeleton di backtracking **gia' in casa**
(`DayAssignmentCsp`), non serve reinventarlo. Resta il SE-E-SOLO-SE strategico di maintainer: e' seconda
fase, il default resta consumare l'input della scuola.

**Provenienza onesta degli input (verificata, non tutto e' "no-golden").** Tre delle quattro sorgenti
sono input puro: `incarichi.csv`, `calendario_class_days.csv` (giorni-L), schema slot. La quarta,
la disponibilita' docente, e' **mista**: T26 e T18 non hanno regole (input puro,
Mon-Fri); **T01 (Thu-only) e T19 (Mon/Tue) dipendono da regole availability marcate
"Q22 golden-derived"** (`availability_rules.csv`), oggi input dichiarato ma di provenienza golden.
Senza quelle regole il branching di T01 sarebbe 96 (non 4) e T19 24 (non 4); il **max
resta 120 (T26, nessuna regola)**, quindi il verdetto di fattibilita' non dipende dal golden,
ma la *provenienza* di due dei quattro conteggi e' golden-derived e va dichiarata. Dettaglio sez. 1.

## Sorgenti di verita'

- Modello (organizzatore + struttura-vs-arbitrio): `modello-rotazione-acconciatura-2026-07-31.md`
- Istanza golden 2026/27 (riferimento, NON regola): `task467-results/rotazione-estrazione-grezza.txt` + `estrazione-rotazioni-2026-07-31.md`
- Input misurati: `seed/incarichi.csv`, `seed/calendario_class_days.csv` (modality L = giorno-lab),
  `seed/availability_rules.csv` (VACATION full-day esclude un weekday), schema slot (8 slot, mattina 0-5 / pomeriggio 6-7)
- Codice riusabile: `service/DayAssignmentCsp.java`, `service/GreedyRotationAssigner.java`,
  `service/PreAssignmentContext.java`, `service/PreAssignmentValidator.java`, `service/SlotCandidate.java`,
  `domain/LabRotation.java`

---

# 1. MISURA DELLO SPAZIO CANDIDATI (dai soli input)

Una **cella** = `(teacher, weekday, start-slot)` tenuta da **un gruppo per un periodo** (eredita'
dello slot, modello di maintainer). Una rotazione = timeline di periodi, ciascuno con un gruppo per cella.
Misuriamo quanto e' grande il menu da cui il backtracking sceglie.

## 1.1 Input che vincolano l'ammissibilita' di una cella `(teacher, group, weekday, slot)`

| Vincolo | Sorgente input | Effetto |
|---|---|---|
| teacher insegna quel group | `incarichi.csv` | definisce le 4 coppie docente-materia-gruppo |
| teacher disponibile quel weekday | `availability_rules.csv` (VACATION 0-7) | T01 solo Thu; T19 Mon/Tue; T26/T18 liberi Mon-Fri |
| group ha giorno-L quel weekday | `calendario_class_days.csv` (modality L) | scheletro lab: es. G02 L su Mon/Tue/Thu/Fri |
| slot valido e blocco mattutino | schema slot (8 slot, mattina 0-5) | blocco titolare 5-6h parte slot 0 (08:00); pomeriggio 6-7 = surplus |

Lo slot titolare e' **1** opzione (blocco mattina slot 0): un 5-6h non sta in altro start mattutino.
Il surplus pomeridiano (slot 6-7, blocchi 1-2h) e' **fill**, non struttura di rotazione, e si conta
a parte come piccola coda (2 opzioni per docente surplus).

## 1.2 Celle ammissibili `(group, weekday)` per docente - ESATTO (deterministico)

Intersezione dei tre vincoli sopra. Calcolato dallo script e verificato a mano sui raw:

| docente | weekday disponibili | celle `(group,weekday)` ammissibili | #celle |
|---|---|---|---:|
| **T01** (Thu) | Thu | Thu: G02,G06,G10,G12 | **4** |
| **T19** (Mon,Tue) | Mon,Tue | Mon:{G02,G12} Tue:{G02,G06} | **4** |
| **T18** (Mon-Fri) | Mon-Fri | Mon:{G02} Tue:{G02,G06,G10} Wed:{G06,G10} Thu:{G02,G06,G10} Fri:{G02,G06} | **11** |
| **T26** (Mon-Fri) | Mon-Fri | Mon:{G02,G12} Tue:{G02,G06,G10} Wed:{G06,G10} Thu:{G02,G06,G10,G12,G14} Fri:{G02,G06} | **14** |

Totale menu = 33 celle `(group,weekday)` (x1 slot titolare). E' il menu **annuo** (unione sull'anno)
da cui parte ogni scelta di rotazione. **Attenzione: l'unione annua sovrastima il menu per-trimestre**
(potatura input-driven aggiuntiva, sez. 1.2bis): i giorni-L non sono anno-pieno.

## 1.2bis Rifinitura per-trimestre (i giorni-L sono trimestrali, non anno-pieno)

La tabella 1.2 e' l'**unione annua** delle celle ammissibili. Ma i giorni-L di `calendario_class_days`
sono **trimestre-specifici**: una cella `(group, weekday)` e' ammissibile in un trimestre solo se
quel group ha ore-L su quel weekday **in quel trimestre**. Conseguenze misurate (verifica per-quarto
sulle date del calendario):

- **T01 (Thu)**: per-quarto ha al massimo **3** gruppi eleggibili (non 4). I 4 gruppi ruotano
  proprio perche' il giovedi'-L non li copre tutti ogni trimestre.
- **G12 e' FORZATO in Q3** (Apr-Mag): ha ore-L su Thu **solo in Q3** (6 date). Fuori Q3 la cella
  `(G12, Thu)` non e' ammissibile. Questo e' un vincolo **strutturale forte** che il modello a
  unione-annua nasconde: G12 ha zero freedom di rotazione, entra e esce col suo trimestre-L.

Quindi il branching per-trimestre e' **minore o uguale** a quello annuo (1.3): il verdetto `< 10^4`
regge a maggior ragione. L'unione annua resta utile come **upper bound** e come menu su cui l'enumeratore
costruisce la timeline (scegliendo, per ogni periodo, il sottoinsieme trimestralmente attivo).

## 1.3 Branching PER-PERIODO PER-TEACHER (l'unita' di enumerazione)

In un periodo, ogni weekday-cell del docente ospita un gruppo. Il branching = prodotto dei gruppi
eleggibili per le sue celle:

| docente | branching per-periodo | = |
|---|---:|---|
| T01 | **4** | 4 |
| T19 | **4** | 2 x 2 |
| T18 | **36** | 1 x 3 x 2 x 3 x 2 |
| T26 | **120** | 2 x 3 x 2 x 5 x 2 |

**MAX per-teacher = 120 < 10^4.** L'enumerazione per-periodo e' fattibile **senza potatura**.
Il prodotto congiunto disaccoppiato (4 teacher) e' ~69120 (~7x10^4), upper bound lasco che
**sovra**stima: assume tutte le celle attive ogni periodo (falso, vedi 1.4) e ignora che
l'accoppiamento gruppi/lab **riduce** lo spazio, non lo aggiunge (lo risolve `PreAssignmentContext`).

## 1.4 Il PRUNER reale (input-driven): il BUDGET vincola la timeline, non il per-periodo

Il **budget** (ore di `incarichi` su ~36 settimane / ~7 periodi) dice quante celle un docente puo'
**sostenere in totale nell'anno**, non quante attiva in un singolo periodo:

| docente | budget | h/settimana | h/periodo (x7) |
|---|---:|---:|---:|
| T01 | 231h | 6.4 | ~33 (forzato Thu) |
| T26 | 200h | 5.6 | ~29 |
| T18 | 160h | 4.4 | ~22 |
| T19 | 128h | 3.6 | ~18 |

**Correzione onesta (da verifica avversariale):** il budget **non** riduce il branching per-periodo
di T26 come sembrava di primo acchito. Con ~29h/periodo e blocchi da ~6h, T26 puo' tenere
**~5 celle attive in un periodo** (5 x 6 = 30h ~ budget/periodo), quindi il suo branching per-periodo
di **120 puo' essere realistico, non un bound lasco**. Resta `120 < 10^4`, quindi il verdetto di
fattibilita' per-periodo regge. Dove il budget pota davvero e' la **timeline**: vincola *quali*
sequenze di periodi coprono il budget di ogni (teacher,group), ed e' li' che lo spazio si restringe
dai `120^P` naif ai candidati 0hard-legali (stime oneste in 1.5/1.6, non misura esatta).

## 1.5 Caso canonico: T01 (Thu forzato, 1 cella, 4 gruppi) - conteggio esaustivo

T01 e' il sottoprocesso piu' pulito: weekday fisso (Thu), 1 cella, 4 gruppi (60/60/65/46h).
Una rotazione = sequenza cronologica di periodi (segmenti continui di giovedi'); ogni gruppo appare
e la somma dei blocchi copre il suo budget. Conteggio delle sequenze suriettive `n! * S(P,n)`
**prima** del vincolo ore:

| periodi P | sequenze (pre-budget) |
|---:|---:|
| 4 (1 segmento/gruppo, semplici) | **24** = 4! |
| 5 | 240 |
| 6 | 1.560 |
| 7 (modale golden) | 8.400 |
| 8 | 40.824 |

Il vincolo budget ("somma blocchi del gruppo = suo budget") pota ulteriormente. **I conteggi
suriettivi sopra sono esatti; il sottoinsieme budget-feasible e' una stima** (non enumerato qui):
ordine di grandezza `~10^2-10^3` rotazioni 0hard-legali a P=7, da confermare all'implementazione.
Il golden ne realizza **una**: il multi-candidato offrirebbe `k ~ 10-100` alternative distinte,
ranked, invece dell'unica scelta arbitraria del planner. Questo e' il valore di servizio della
fase 2 (parole di maintainer, parte 4 del modello).

## 1.6 La granularita' e' tutto: periodo vs settimana

| granularita' | depth | spazio timeline (naive, pre-pruning) | verdetto |
|---|---:|---:|---|
| **periodo** (modello regolarita') | ~7 | T26 `120^7 ~ 10^14` naif | **fattibile con potature (1.7)** |
| settimana (cambio gruppo/settimana) | ~36 | `120^36 >> 10^70` | **esplode, vietato dal modello** |

La regolarita' (1 gruppo/cella per **periodo**) e' la **leva** che tiene il problema in scala: e'
essa stessa un vincolo strutturale input-autorevole (organizzatore di maintainer), non un'approximation.
L'enumeratore non deve mai trattare la settimana come unita' di decisione.

**Onesta' sulla stima post-pruning.** Quanti candidati 0hard-legali restino dopo le potature di 1.7
**non e' misurato** in questo doc (richiederebbe far girare l'enumeratore, che e' lavoro gated):
e' una **stima**, ordine di grandezza `10^3-10^6` per acconciatura. Cio' che e' **misurato e
verificato** (sez. 1.2/1.3, skeptic CONFIRMED) e' il **branching per-periodo per-teacher <= 120 <
10^4**, che basta a garantire la fattibilita' di una DFS per-periodo con early-abort
(`MAX_CANDIDATES=10000`). Il conteggio esatto dei candidati legali e' un risultato da produrre
all'implementazione, non da asserire ora.

## 1.7 Elenco delle POTATURE e la loro giustificazione di input

Lo spazio timeline (periodo) cresce e va potato. Ogni potatura e' **input-driven** (mai dal golden,
tranne tie-break dichiarato). Le frecce `->` sono ordini di grandezza, non misure esatte.

1. **Regolarita' -> granularita' periodo** (non settimana). Input: modello "periodi di regolarita'"
   (strutturale, maintainer). E' il pruner dominante: porta la depth da ~36 (settimane) a ~7 (periodi),
   cioe' lo spazio da `>>10^70` a `~10^14` naif (T26 `120^7`).
2. **Trimestralita' dei giorni-L** (1.2bis): cella ammissibile in un periodo solo se il group ha
   ore-L quel weekday in quel trimestre. Input: `calendario_class_days.csv` per-data. Riduce il
   menu per-periodo (T01 4 -> max 3/trimestre; G12 forzato in Q3).
3. **Budget-feasibility**: ogni segmento di un gruppo deve sommare alle sue ore di `incarichi`.
   Input: `incarichi.csv`. Pota la timeline (quali sequenze di periodi coprono i budget) a una
   stima `~10^3-10^6` candidati (da misurare all'implementazione).
4. **0hard conflict** (H1 teacher / H3 group / H5 disponibilita' / H2 room): cella ammissibile solo
   se non conflitto. Input: availability + calendario + slot. Risolto da `PreAssignmentContext` in O(1).
5. **Docente mono-giorno ha priorita'** (T01 Thu): il suo branching collassa all'ordine dei
   gruppi. Input: availability (Q22 golden-derived, dichiarato).

**Non** c'e' una potatura "budget-activity-rate che riduce il per-periodo": corretto in 1.4, il
budget vincola la timeline, non il per-periodo (T26 120 puo' essere realistico). Il verdetto
`max per-periodo = 120 < 10^4` non dipende da questa potatura, regge da solo.

---

# 2. DESIGN dell'enumeratore (riusa, non reinventare)

## 2.1 Dove si innesta: RIATTIVARE `DayAssignmentCsp`, non scrivere un terzo backtracker

In casa ci sono **due** skeleton di backtracking vivi (verificati ai file):

| componente | ruolo | vivo? | linee chiave |
|---|---|---|---|
| `DayAssignmentCsp.enumeratePairsRecursive` | enumerazione ESAUSTIVA (teacher/group -> day), branch-and-bound, **early-abort a `MAX_CANDIDATES=10000`** | **SI', production** | `DayAssignmentCsp.java:252` (ricorsiva), `:209`/`:341` (chiamate), `:270` (prune cap), `:57` (MAX_CANDIDATES) |
| `GreedyRotationAssigner.backtrackAssign` | backtracking ricorsivo che assegna slot; accetta **SUCCESSO PARZIALE** (`hasPartialSuccess()`, `blocksPlaced>0`) | SI', production (path fase-1) | `GreedyRotationAssigner.java:779` (ricorsiva), `:808`/`:840` (accetta parziale), `:70` (MAX_BACKTRACK_DEPTH=100) |
| `RotationTestRunner.testCandidate` / `RotationCandidate` | vecchia enumerazione Cartesian "candidate -> test" | **MORTO**: `testCandidate` chiamato solo nei test; in production solo il no-op `clearCapacityCache()` (`HybridPhase1aService.java:542/556/595`) | `RotationTestRunner.java:93` |

**Scelta di design:** la fase 2 riattiva la **forma di `DayAssignmentCsp`** (enumeratore esaustivo
con cap + branch-and-bound), che e' quella semanticamente piu' vicina (enumera TUTTE le
assegnazioni legali, non si ferma al primo parziale). `GreedyRotationAssigner` resta il path
fase-1: il suo "accetta parziale" e' l'ottimismo del planner umano che la fase 2 vuole
**superare** (certezza matematica: prova k soluzioni complete 0hard, non una parziale).
`RotationCandidate`/`RotationTestRunner` sono il pattern storico da **non** resuscitare: TASK-121
li ha giustificatamente rimpiazzati integrando l'assegnamento giorno dentro il backtracking.

**Precisione onesta su cosa si riusa pari-pari e cosa si aggiunge.** `DayAssignmentCsp.enumeratePairsRecursive`
enumera assegnamenti **giorno** per una singola istanza (pair -> DayOfWeek) in un periodo: e' lo
skeleton di **enumerazione esaustiva + early-abort + branch-and-bound** che si riusa. La dimensione
**timeline** (la sequenza di periodi che copre i budget, sez. 1.5) e' uno **strato nuovo** che si
costruisce sopra: l'enumeratore per-periodo viene chiamato ricorsivamente sui periodi, con
`PreAssignmentContext.track/untrack` per propagare lo stato 0hard tra periodi. Quindi: skeleton
riusato, layer timeline nuovo. Nessun terzo backtracking da zero.

## 2.2 Componenti riusati (nessuno nuovo)

- **`PreAssignmentContext`** (`trackLesson`/`untrackLesson` `:200`/`:253`, conflict check O(1)):
  traccia lo stato durante il backtracking per il prune 0hard a costo costante. Gia' fatto per
  supportare backtracking (`track`/`untrack` esistono per questo).
- **`PreAssignmentValidator.canPinHardConstraints`** (`:600`, TASK-435): validatore **hard-only**
  (H1/H2/H3/H5/H11/H-M2), esclude i medium. E' il predicato di accettazione 0hard riusato.
- **`SlotCandidate`** (`:23-29`, priority 1-4): payload "(day x slot) preference" col ranking
  a 4 livelli (exact -> same-day alt-slot -> alt-day same-slot -> alt-day alt-slot). Riusato come
  oggetto-candidato ordinato.
- **`LabRotation.periodStart/periodEnd`** (`:35`/`:36`): la struttura del periodo. Oggi vuoto
  (placeholder febbraio); la fase 2 lo **popola** con i periodi che deriva dall'enumerazione (e che,
  in produzione, arriverebbero dal workbook della scuola).
- **`LabSession` / `BlockKey`** (teacherId, studentGroupId): le chiavi dell'assegnamento.

## 2.3 Criterio di ACCETTAZIONE: 0hard stretto (come oggi, ma senza parziale)

`canPinHardConstraints` per ogni lezione del segmento candidato. **Nessuna medium** in fase di
enumerazione (M15/M31/H16 etc.): la fase 2 enumera rotazioni **legali sui hard**, il ranking (2.4)
decide tra le legali. Differenza chiave vs `GreedyRotationAssigner`: qui **non** si accetta
`hasPartialSuccess()`; una rotazione candidato e' valida solo se **tutti** i suoi blocchi sono 0hard.
Questo e' il superamento dell'ottimismo del planner (parte 4 del modello).

## 2.4 Criterio di RANKING tra candidati validi (input-driven; golden solo tie-break dichiarato)

Tra le `k ~ 10-100` rotazioni 0hard-legali, si ordina per criteri **input-driven**, e il golden
entra **solo come tie-break esplicito** (finche' esiste, poi sparisce - direttiva solo-input):

1. **Completezza del budget** (primario): copertura dei budget `incarichi` per (teacher,group).
   Input: `incarichi.csv`. Massimizza le ore collocate sulla griglia.
2. **Rispetto dello scheletro giorni-L**: % di ore acconciatura sui giorni-L del gruppo.
   Input: `calendario_class_days.csv` (modality L). Lo scheletro e' input.
3. **Eredita' dello slot + mono-docente prioritario**: stabilita' della cella, T01 Thu prima.
   Input: availability + modello (strutturale).
4. **Tie-break dichiarato (golden, finestra):** tra rotazioni pari sui criteri 1-3, preferire quella
   piu' **simile nelle dimensioni STRUTTURALI** osservate nel golden (regolarita', eredita',
   profondita' cella) - **mai** copia cella-per-cella dell'istanza (quelle sono scelte arbitrarie,
   vedi sez. 3). Il tie-break sparisce con il golden; i criteri 1-3 sopravvivono.

Il ranking **non** usa mai "il golden fa cosi'" come vincolo: lo usa solo come segnale debole di
tie-break su cio' che e' gia' strutturale. Questo e' il guardrail anti-overfitting.

## 2.5 Stima del costo runtime

- Menu celle annuo: 33; per-trimestre e' un sottoinsieme (1.2bis). Branching per-teacher per-periodo
  **misurato**: max 120 (T26, realistico non lasco - vedi 1.4), gli altri 4/4/36.
- Depth: ~7 periodi. Lo spazio timeline naif e' `~10^14` (T26 `120^7`); il numero di nodi
  **realmente esplorati** dipende dalle potature (1.7) e va **misurato all'implementazione** (non
  asserito): stima d'ordine `10^3-10^6` candidati 0hard-legali per acconciatura.
- `MAX_CANDIDATES=10000` (`DayAssignmentCsp:57`) e' un early-abort gia' dimensionato giusto; per la
  fase 2 si valuta un cap **per-teacher** piu' uno globale, con `log()` dei candidati scartati
  (nessun silent truncation: se il cap ferma la ricerca, va dichiarato, non nascosto).
- Ordine di grandezza atteso: **secondi** per acconciatura (4 docenti), non minuti (confrontabile
  col tempo di `GreedyRotationAssigner` fase-1). **Da verificare** col primo run gated: e' una
  stima, non una misura. Nessun impatto sul solve LS (pre-assignment, pin in/pin out).

## 2.6 Integrazione col ciclo staged

La fase 2 e' un **sostituto del canale input** per la pre-assegnazione acconciatura, non un nuovo
passo del solve. Si innesta dove oggi `HybridPhase1aService.runPhase1()` (`:1295`) chiama
`greedyAssigner.assignTeachers()` (`:1315`): dietro knob esplicito, al posto di leggere le
`LabRotation` (vuote), esegue l'enumeratore, ranka k candidati, ne promuove il migliore come pin.
Il solver a valle non vede differenza (pin in pin out). Gated: default OFF, opt-in esplicito.

---

# 3. COSA NON FA LA FASE 2 (scope negativo - ANTI-OVERFITTING)

L'istanza golden 2026/27 e' **una** soluzione valida, **non la regola**. La fase 2 non deve:

- **NON copia l'istanza golden cella-per-cella.** La sequenza specifica (perche' G06 per primo,
  perche' G12 ad aprile), i confini di periodo esatti, gli slot del surplus pomeriddiano sono
  **scelte arbitrarie** del planner (modello parte 2 "Struttura vs arbitrio"). Copiarle e'
  overfitting su una realizzazione casuale. Il golden e' solo tie-break strutturale (2.4.4).
- **NON cambia la semantica di accettazione della fase 1.** `GreedyRotationAssigner` (parziale) e il
  canale input-workbook restano il default. La fase 2 e' un'**alternativa** opt-in, non un replace.
- **NON sostituisce il canale d'ingresso naturale.** Il modo onesto di avere le rotazioni e'
  **chiederle alla scuola** (foglio del workbook, TASK-445). Il backtracking esaustivo e' il
  **ripiego** se l'input non arriva (commit `99197850`, famiglia TASK-120, accantonata quando
  l'accordo divenne "referente pre-alloca a mano"). Offrire entrambi e' il servizio superiore, ma
  l'input dichiarato resta preferito (auditabile, modificabile dalla scuola).
- **NON promuove a hard vincoli che il golden viola.** La regolarita'/eredita'/mono-giorno sono
  **struttura enumerata**, non nuovi vincoli del solver. La fase 2 genera candidati che rispettano
  quella struttura; non la impone a tutto lo schedule.
- **NON inizia finche' la fase 1 non e' consegnata** e maintainer non apre la fase 2 (gated esplicito).
- **NON esce dall'acconciatura.** Lo scope e' i 4 docenti acconciatura. Generalizzare ad altri lab
  (MODA, FB) e' lavoro separato, da misurare a parte (il loro menu celle va ricalcolato).

## Anti-overfitting: la prova del nove

Una rotazione enumerata dalla fase 2 e' **buona** se e' 0hard e massimizza i criteri input-driven
(2.4.1-2.4.3), **non** se assomiglia al golden. Se due rotazioni input-equivalenti divergono dal
golden, cio' non e' un difetto: e' il punto (il golden era una di tante). Lo sprawl di k candidati
ranked e' la **garanzia** contro il mimetizzarsi di una scelta casuale del planner.

---

# 4. COSA DECIDE maintainer (gated)

- **Aprire la fase 2?** Ora NO (design pronto, implementazione gated). La fase 1 (input-driven,
  TASK-452 patch opt-in) si consegna prima. La fase 2 si attiva solo se si vuole il servizio
  "non chiedere i giorni-lab, enumera tutte le regolari".
- **k del multi-candidato:** quanti candidati ranked provare (es. k=10) prima di promuovere il
  migliore. Da calibrare sull'A/B (feasibility rate vs costo), misurato col golden ancora disponibile.
- **Dove stare sul tie-break golden:** se usarlo (2.4.4) o tenerlo spento per il primo A/B pulito.

## STATO

- DESIGN pronto (questo doc), misurato sui soli input. Numeri esatti (menu celle, branching
  per-periodo) verificati a mano + script `/tmp/measure_task467_candidatespace.py`; stimati
  (timeline post-pruning) dichiarati come bound. Implementazione **gated**, nessun codice scritto.
- TASK-467 aggiornato (AC #1 design coperto; #2-#4 restano gated all'implementazione).
- Nessuna adozione, nessun solve, read-only. Commit+push del solo doc + riga QUADRO.
