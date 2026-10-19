# Campaign status, 2026-08-05 (indagine metaheuristic+repair ESAURITA, pivot al MODELLO)

Doc di orientamento. Sostituisce `campaign-status-2026-08-04.md`, ormai stale su due punti chiave:
elencava **TASK-296 (ACL tune)** e la **caratterizzazione HoleRepair** come direzioni produttive
aperte, ma entrambe sono chiuse (verdict 2026-08-05: ACL non-robust, HoleRepair al ceiling).
La conseguenza strategica e' che l'indagine metaheuristic+repair e' **ESAURITA** e la direzione
produttiva e' ora il **MODELLO** (vincoli, fatti, UX), non piu' il tuning del solver. Stato
ADOZIONI/KPI da `CLAUDE.md` (autorita' delle ratifiche); conclusioni dell'indagine dal synthesis
`metaheuristic-campaign-synthesis-2026-08-05.md` (commit `15c108d8`) + addendum (commit `4ca98324`)
+ `VERDICT-holerepair-characterization-2026-08-05.md` (commit `78605ace`), verificati e non da memoria.

## Stato attuale: cosa e' ADOPTED DEFAULT ON

Il ciclo staged (`POST /api/schedule/solve-staged`) e' la modalita' di campagna. Catena di ratifiche
DEFAULT ON (tutte opt-out, lette LIVE per-build, banner in `ScheduleService.refreshSolverStatics`):

- **2026-07-27**: staged cycle + M20/M33 pin-netting (TASK-424) + A4 ranking + TASK-422 SurplusRebalance
  + TASK-423 M33 anti-surplus + TASK-425 (M34/M35 cadence cap).
- **TASK-433** AnchorRepair (deterministico, post-HoleRepair/pre-SurplusRebalance).
- **TASK-435** constrained-teacher pre-assignment pin (2026-07-30).
- **TASK-451** first-hour-slot coordinato slot0+1 (2026-07-31).
- **TASK-463** lab-day-consolidation + **TASK-464** co-presenza ospite + **H-COPRESENCE** cap (2026-08-01).
- **TASK-469** M-CPS curriculum post-stage target 6h asimmetrico (2026-08-01).
- **TASK-478** ExactFinisher (completamento esatto del residuo boxed, 2026-08-02).
- **TASK-483** multi-start retry incapsulato (2026-08-03): pick-best su (hard, medium), stop al primo 0-hard.
- **TASK-486** M-TC per-retry (2026-08-04): tentativo 1 off (no overhead), tentativi >=2 on (cura il
  room-tier saturo del bad basin). Subordinato a multi-start.
- **M-LF** lab-fallback (TASK-456, 2026-07-29). **H29** promoted hard (TASK-447).

**Source DEFAULT = golden.** `source=input` e' opt-in (`-Dplanner.pre-assign.constrained-teacher.source=input`,
decisione ready-when-needed). **GRASP** (costruzione randomizzata) e' opt-in OFF
(`-Dplanner.construction.grasp.enabled`+`.seed`); il multi-start lo usa internamente per i tentativi >=2.

## Dove siamo vs il golden (KPI da evidenza ratificata)

| metrica | run corrente (ratificato) | golden | soglia |
|---------|---------------------------|--------|--------|
| hard | **0** su good seed (golden path); bad-seed -6 H17 (source=input path), curato dal multi-start (pick-best) | 0 | 0 (binario) |
| full-day | 87,5% (TASK-451, 88,6% col conteggio KPI corretto) | 85,4% | 78 |
| day-start | 99,9% | ~99,9% | - |
| ricorrenza unpinned | 75,6-75,9% (TASK-463 combined run3 / TASK-469 A/B) | ~golden cadence | 72 |
| buchi interni | 0 (good seed); 18 H17 accettati come costo TASK-451, strutturali sul bad seed | 0 | 0 |
| medium | -963k/-1047k (good seed, banda) | - | - |

Fonti: `CLAUDE.md` ratifiche TASK-451/463/469/478; `task463-results/combined-run3` (source=input, hard -6,
ricorrenza 75,9%); `task469-results/ab-cps-on-run1` (source=golden, hard 0, ricorrenza 75,6%). Numeri su
run ratificati, non medie; la LS e' NON_REPRODUCIBLE quindi varianza run-to-run. Nessun nuovo solve dal
08-04: la tabella e' invariata.

## La conclusione strategica: indagine metaheuristic+repair ESAURITA

La campagna 2026-08-04/05 ha misurato sistematicamente OGNI leva candidata al di fuori del modello.
Esito: tutte sono flat / inerti / non-robuste / al ceiling. La sintesi decisionale completa e' in
`metaheuristic-campaign-synthesis-2026-08-05.md` (con addendum); qui il riassunto che orienta.

**Gerarchia delle leve (cosa muove cosa, e di quanto):**

| Leva | Muove | Entita' | Stato |
|------|-------|---------|-------|
| Multi-start (selezione bacino, TASK-483) | HARD strutturale (residuo bad-seed) | la differenza hard<0 vs hard=0 sui bad seed | RATIFICATA, in opera |
| HoleRepair | HARD (parte chiudibile) | ~10 hard bad basin, ~6 good | default ON; al ceiling |
| M-TC per-retry (TASK-486) | HARD room-tier saturo (bad basin, tentativi >=2) | cura deterministica dove il multi-start e' solo probabilistico | RATIFICATA, in opera |
| ExactFinisher (TASK-478) | HARD (coda boxed H0) | converte la chiusura boxed da probabilistica a deterministica (good seed) | RATIFICATO, in opera |
| AnchorRepair | DAY-START (medium) | 1-3pp day-start | default ON (TASK-433) |
| acceptedCountLimit / accettore LS | MEDIUM | ~5% medium (acl=100 vs 1000; VND-FI vs Tabu) | default 1000; **TASK-296 RULED OUT** (non-robust) |
| Pesi medium (m32/m34/m35/m-tc/m37) | nulla (sul timetable) | FLAT | dead end |
| Accettore LS per HARD sul staged | nulla | inerte (VND-FI staged hard=0 = Tabu) | dead end per l'hard |

**I tre verdetti che chiudono l'indagine (tutti 2026-08-05, read-only sui dati gia' catturati):**

1. **Pesi medium FLAT** (knob sweep, 0.2x..3x su m32/m34/m35/m-tc/m37): muovono il NUMERO dello score
   (scaling dell'auto-penalita'), non il TIMETABLE. Flat via residuo forzato (M32/M34/M35: cadenza/template
   locked da costruzione + famiglia di fill) o soddisfazione (M-TC: non-binding sul good seed). Il solver
   NON e' accordabile via pesi medium.
2. **Accettore LS / ACL NON-ROBUST** (TASK-296 finer sweep): nessun sweet spot. La curva medium-vs-ACL e'
   state-dependent e non-monotona a n=1 (stato 3090-pin: ACL basso meglio; stato 2907-pin: ACL alto
   meglio). Il range ~5% medium che ACL spazia e' al FLOOR della varianza run-to-run del pre-assign (~6%
   pin variabili), quindi l'effetto non e' robusto. ACL NON muove l'hard (solo 10000 rompe, -7498 H29).
   ACL NON e' candidata adozione. Il default 1000 resta.
3. **HoleRepair al CEILING** (caratterizzazione bad basin): i ~10-hard del bad basin sono il suo soffitto.
   Chiude i 9 buchi chiudibili G08 mezzogiorno (mercoledi' 2026-11-10..2027-02-16) + 1 wobble LS G07.
   E' INERTE sui 12 buchi strutturali G11 @10/@11 (10 mercoledi' consecutivi 2026-09-16..11-18): e'
   lo shell-game di TASK-481, uno slot saturo mid-run che nessuna riparazione LOCALE puo' chiudere.
   "Raffinare HoleRepair" e' RULED OUT.

**Conclusione:** il lato HARD e' pienamente caratterizzato e risolto. Parte strutturale (12) -> multi-start
(TASK-483, RATIFICATA). Parte chiudibile (10) -> HoleRepair (DONE, al ceiling). La via principale per H17
e' la **selezione del bacino** (multi-start), non la chiusura in-locus. Ogni ulteriore tuning del solver
(medium weights, accettore, repair) e' un vicolo cieco misurato. **L'indagine metaheuristic+repair e'
dichiarata COMPLETA.**

## Il blocco corrente: m-13, source=input H17 bad-seed

Il path **source=input** (quello dell'esercizio reale, senza golden) e' seed-dipendente: good seed 0-hard,
bad seed -6 H17 (G11 mercoledi' ore 10). 0-hard e' BINARIO e BLOCCANTE end-to-end (direttiva maintainer
2026-08-02), quindi il gate **m-13 (genericeta'/riuso multi-anno) resta bloccato** finche' la varianza H17
non e' assorbita (`CLAUDE.md`).

L'assorbimento e' ORA PROCEDURALE, non strutturale: il multi-start (TASK-483 RATIFICATA, DEFAULT ON)
ritenta con costruzione GRASP randomizzata e fa pick-best su (hard, medium), stop al primo 0-hard.
p(0-hard) per singola costruzione = 0,750 (n=8, Wilson95 [0,41,0,93]); N=3 -> P(>=1 0-hard) > 0,95; wall
medio 28 min/run. Costo zero sui good basin (il loop si ferma al primo 0-hard). La cura LOCALE del buco
(TASK-481 CompositionHoleCloser) e' uno shell-game ed e' esclusa: resta committed DEFAULT-OFF come
building block corretto (gate `isDepartureSafe`).

**Implicazione per m-13:** il gate e' bloccato sul *significato* della chiusura procedurale, non sulla
sua *esistenza*. Domanda per maintainer: accettare la procedura multi-start come chiusura del path input
(cioe' 0-hard come esito statisticamente garantito, non deterministico per costruzione), oppure
inseguire una cura strutturale. Il synthesis dichiara la cura strutturale dell'H17 fuori portata
delle riparazioni locali, quindi l'alternativa reale e' accettare la procedura o spostare l'attenzione
al modello.

## Dove si lavora ora: il MODELLO (non il solver)

L'indagine metaheuristic+repair e' esausta. I guadagni di qualita' dell'orario residui non vengono da
ulteriore tuning del solver: vengono dal **modello**, su tre assi coerenti con la direttiva solo-input
(`CLAUDE.md`, "la soluzione gira sul SOLO INPUT"):

1. **Vincoli** (golden-first, framework H-vs-M): nessuna nostra regola puo' essere piu' rigida della
   pratica che il golden incarna; il tier hard-vs-medium si decide sulla correttezza logica, non sul
   costo computazionale (direttiva 2026-07-21).
2. **Fatti** (target input-derived, solo-input): cio' che il golden dice e va dedotto dagli input si
   cambia sorgente; cio' che e' pratica della scuola non deducibile va CHIESTA e diventa input dichiarato.
   Il debito golden-runtime (TASK-452) ha una data di scadenza.
3. **UX** (workbook TASK-445): il veicolo per dichiarare gli input che la statistica non puo' inventare.

## Cosa resta aperto / prossimo intervento (gated maintainer)

La lista e' ORDINATA per asse di modello. Tutti gated: maintainer decide cosa avviare. Nessuna adozione
implicita.

**Fatti / solo-input (la finestra del golden si chiude):**
1. **TASK-441** C5 - processo riferimenti golden annuali: policy per l'anno senza golden, derivazione
   riproducibile di M21/T20. Mette in sicurezza la partenza quando il golden sparira'.
2. **TASK-449** allocatore pre-assegnazione lab mono-stanza: estende il pattern del TASK-435 (gia'
   DEFAULT ON) alla risorsa stanza scarsa (acconciatura/termoidraulica/meccanica). E' il consumatore
   "costruzione" della scarsita' di tag che M-TC misura a valle; via separata dal VRP.

**Vincoli / framework H-vs-M:**
3. **TASK-470** cura M-TR construction-origin: la diagnosi (Fedora verification) colloca l'origine dei
   blocchi 2h teoria nella COSTRUZIONE (WeeklyTemplateBuilder), non nella LS. Il peso e' il lever
   sbagliato (sweep inerte 1..1000). La cura e' la dedup per-(gruppo, giorno, materia-teorica) nel
   placement greedy. PROPOSTA non banale (tocca placement + fill-risk + interazione M15/S17), gated maintainer.
4. **TASK-460** ricombinazione orizzontale: il blocker erosione (TASK-453) e' rimosso (cell-retention
   misurata 16,6%, concentrata in LS-Compactness, template preservato dai PIN). Brainstorming
   dell'approccio ora sbloccato; l'evidenza erosione e' disponibile per semearlo.

**UX / workbook:**
5. **TASK-471** workbook distribuzione teoria come input dichiarato: 1h/giorno vs 2h dal golden, da
   conferma scuola. Veicolo solo-input per la pratica "non ci devono essere 2 ore vicine della stessa
   materia teorica" (complementare alla cura costruttiva di TASK-470).

**Igiene backlog (bookkeeping, riflette verdict gia' ratificati):**
- **TASK-296** va chiuso: l'addendum del synthesis lo rule-out (ACL non-robust). E' ancora "To Do".
- **TASK-481** gia' Done con esito onesto (cura locale esclusa, H17 deferito al multi-start).

## Dove guardare

- Synthesis decisionale: `claudedocs/spalmone-2026-27/metaheuristic-campaign-synthesis-2026-08-05.md`
  (+ addendum in calce).
- Verdetti di chiusura: `task296-results/VERDICT-task296-2026-08-05.md` (ACL non-robust),
  `task284-results/VERDICT-holerepair-characterization-2026-08-05.md` (HoleRepair ceiling),
  `task284-results/VERDICT-vnd-staged-*` + `VERDICT-tabuvnd-benchmark-*` (accettore inerte sul staged).
- Orientamento nottata precedente: `handoff-notte-2026-08-04.md`.
- Ratifiche e KPI: `CLAUDE.md` (sezioni "RATIFICA TASK-*" + tabelle vincoli).
- Pipeline solver: `claudedocs/solver-phases.md`; config knob: `claudedocs/solver-configurations.md`.
