# Estrazione rotazioni acconciatura dal golden 2026/27 (zai, 2026-07-31 notte)

Task 8 / SYNC backlog TASK-467 (gemello). Mandato (maintainer, doc `modello-rotazione-
acconciatura-2026-07-31.md`): scheletro giorni-lab dall'INPUT (`calendario_class_days`,
gia' in casa), dal golden solo la mappa (giorno -> materia -> docente -> gruppo/periodo);
deliverable che separa STRUTTURALE (verificato) da ARBITRARIO (istanza, non regola);
coerenza golden-vs-input sui giorni-L; G14 ospite strutturale, G12 titolare al 90%
con 12h parallele T26.

Script: `scripts/analyze/acconciatura_rotation_extract.py`.
Timeline grezza completa: `task467-results/rotazione-estrazione-grezza.txt`.
Base numerica G12/G14: `rotazioni-acconciatura-crosscheck-2026-07-31.md` (gia' validato).

## 1. I 4 docenti acconciatura e le loro materie

| docente | materia | ore | weekday |
|---|---|---:|---|
| T01 | Servizio tecnico e stilistico | 231 | **solo Gio** (mono-giorno) |
| T26 | Servizio stilistico | 200 | Lun, Mer, Gio (fulcro) |
| T18 | Lavaggio | 160 | Mar, Mer, Ven |
| T19 | Barberia | 128 | Lun, Mar |

## 2. La rotazione e' reale e pulita (esempio canonico: T01, Gio slot 08-12)

Il blocco mattutino del giovedi' (5-6h) ruota di gruppo in periodo, un gruppo alla volta,
con eredita' dello slot (finito un periodo la cella passa al gruppo successivo):

`G06 (24/09-22/10) -> G02 (29/10-26/11) -> G10 (03/12-21/01) -> G06 (28/01-25/02)
 -> G02 (04/03-01/04) -> G12 (08/04-13/05) -> G10 (20/05-03/06)`

Stessa struttura a blocchi si ripete per T26 (Mer slot 08-13: G10 poi G06),
T18 (Ven: G06 poi G02; Mer pomeriggio: G10 poi G06), T19 (Mar: G06 ->
G02 -> G06). **Il modello di maintainer e' verificato nell'istanza**: regolarita' per periodo
+ eredita' dello slot + priorita' del docente mono-giorno (T01 sempre e solo giovedi').

## 3. STRUTTURALE (verificato sul golden) - input autoritativo, replicabile

- **Docente mono-giorno = T01, solo giovedi'** (231/231h su Gio). Ha priorita' di
  piazzamento: il suo blocco mattutino ruota sui gruppi FA + G12.
- **Eredita' dello slot**: ogni cella (docente, weekday, blocco-orario) e' tenuta da UN
  gruppo per periodo, poi passa ad altro gruppo. Nessuna cella strutturale ha piu' titolari
  contemporanei (le co-presenze sono gli OSPITI, vedi #5).
- **Blocco mattutino multiplo**: l'acconciatura si fa in blocchi 5-6h al mattino (08-13),
  allineati ai giorni-L dell'input.
- **Coerenza golden-vs-input sui giorni-L** (l'ore acconciatura cadono sui giorni-L del
  foglio input):

  | gruppo | tot acc | su giorno-L | off-L | %su-L |
  |---|---:|---:|---:|---:|
  | G06 | 212 | 212 | 0 | 100% |
  | G02 | 186 | 180 | 6 | 97% |
  | G10 | 170 | 170 | 0 | 100% |
  | G14 | 30 | 30 | 0 | 100% |
  | G12 | 121 | 91 | 30 | 75% |

  Lo scheletro giorni-L e' rispettato al 95-100%. Le 30h off-L di G12 sono la quota
  teoria (vedi #6), non una violazione dello scheletro.

## 4. ARBITRARIO (istanza del golden, NON regola da replicare - anti-overfitting)

- **Quale gruppo in quale periodo**: la sequenza specifica (perche' G06 per primo, perche'
  G12 ad aprile) e' una scelta dello human planner, non un vincolo. Copiarla cella per
  cella e' overfitting su una scelta casuale.
- **I confini di periodo esatti** (le date di inizio/fine di ogni rotazione): tentativo
  dello human planner, non una regola.
- **Gli slot del surplus** (pomeriggi 14:30-15:30, le poche ore sparse): sono il riempimento
  del surplus dei docenti non vincolati al mono-giorno, piazzato dove c'e' spazio.

## 5. G14 = ospite STRUTTURALE (conferma cross-check)

T26, Gio slot 08-12: **G12 (17/09-07/01) + G14 (28/01-04/03) in co-presenza**.
G14 (30h, tutte Servizio stilistico con T26) non tiene MAI una cella da titolare:
e' sempre ospite nella cella della titolare FA di turno. E' l'unico ospite-nel-lab
generalizzato della pratica golden (conferma `rotazioni-acconciatura-crosscheck`).

## 6. G12 = titolare al 90% + quota teoria off-L (conferma cross-check)

G12 e' titolare esclusivo al 90% (109/121h). Le 12h parallele di T26 del giovedi'
(Servizio stilistico, slot 08-12) sono in co-presenza con le titolari FA: sessione
parallela, **probabile aula normale**. Nel seed `Servizio stilistico` per G12/G14 e'
gia' classificato THEORY (islab=false): la quota teoria e' il canale "aula normale". Le 30h
off-L di G12 sono questa quota teoria, NON una violazione dello scheletro giorni-L. La
domanda fisica (aula normale? conferma) va a referente (Q20, doc TASK-466c).

## 7. Conseguenze di modellazione (per TASK-467 phase-2 e varianti)

- Lo scheletro giorni-L e' INPUT (gia' ingerito, modalita' L). La rotazione (chi-dove-quando)
  e' l'istanza golden: utile come riferimento e verifica, NON come regola. Il canale
  d'ingresso naturale delle rotazioni e' l'input workbook (foglio rotazioni), col
  backtracking esaustivo (commit `99197850`, famiglia TASK-120) come ripiego se l'input non
  arriva (TASK-467 phase-2, gated).
- Variante (b) (TASK-465/9): il perimetro "ospite nel lab" si restringe a G14; le 12h 4
  FD + i tripletti FA vanno a trattamento aula-normale (la quota teoria, gia' islab=false
  nel seed, canalizzata da M-LF). La rotazione dei titolari resta sullo scheletro giorni-L.
- Nessuna adozione, nessun nuovo input: questa e' estrazione read-only. Le decisioni
  (modellare la rotazione? chiudere la quota teoria?) sono di maintainer sui numeri delle varianti.
