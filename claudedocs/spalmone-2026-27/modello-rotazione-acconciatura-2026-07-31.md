# Modello autoritativo: la rotazione acconciatura (maintainer, 2026-07-31 sera)

Fonte: maintainer, a voce, 31/07 sera, ricordando cio' che la scuola racconto' a inizio progetto
(le tracce in git sono nel commit `0ceefaf3` del 16/02: LabRotation + AcconciaturaPreAssigner
con rotazioni trimestrali placeholder "need confirmation from the school", mai confermate).

## Il principio organizzatore (parole di maintainer, parafrasate fedeli)

Tutto gira intorno alla **rotazione alternata dei 3 insegnanti di acconciatura**, pensata per
dare **periodi di regolarita'** a studenti e insegnanti:

1. Gli studenti hanno la materia **fissa in uno specifico slot della settimana**, ma solo per
   **una parte dell'anno** (un periodo).
2. Finito il periodo, **quello slot e quella materia passano a un altro gruppo** (eredita' dello
   slot: lo slot resta stabile, il gruppo ruota).
3. I docenti coinvolti sono i 3 di acconciatura. **Uno e' vincolato al solo giovedi'**
   (oggi T01; nel modello di febbraio era T28) e quindi ha **priorita' di piazzamento**;
   gli altri due **seguono il ritmo** e **integrano su altri giorni/slot, mantenendoli sempre
   regolari**.

## Conseguenze sul modello

- La co-programmazione di piu' gruppi su materie acconciatura nello stesso slot (il "depth 5
  del giovedi'" osservato dallo spike co-presenza) va letta dentro questa architettura: NON
  "5 gruppi nel laboratorio", ma il telaio della rotazione (piu' docenti in parallelo, ciascuno
  col suo gruppo di periodo, piu' gli ospiti documentati G14/G12 di Q19/Q20). Il golden non
  registra le aule, quindi la ripartizione fisica resta da confermare con referente, ma il modello
  di partenza e' la rotazione, non la co-presenza generalizzata.
- L'eccezione ospite (TASK-464) e' probabilmente legittima nel perimetro STRETTO (G14, G12);
  la generalizzazione ai tripletti FA e' da pesare contro la variante "rotazione + parte teorica
  in aula normale" (linea TASK-465 + esperimento v1 del 30/07 che de-taggava Servizio stilistico
  per G14+G12, room-null 169->119).
- **Il golden contiene le rotazioni reali del 2026/27**: per ogni (docente, weekday, slot) la
  sequenza dei gruppi nell'anno e' estraibile meccanicamente. E' la finestra-golden da usare ORA:
  chiude il placeholder di febbraio e diventa input dichiarato (workbook), con conferma di referente
  a posteriori.

## I giorni-lab sono GIA' input (maintainer, 2026-07-31 sera, parte 3)

Le tracce del modello stanno nei fogli dell'xlsx condiviso: il foglio **FA** dice quante ore
di lab, per ogni gruppo, in quali giorni. E' diventato dato di input ed e' GIA' ingerito
(`calendario_class_days.csv` -> DailyActivityQuota, modalita' L). Verifica sul seed (31/07):
G02 L su Tue 66/Thu 54/Mon 42/Fri 75; G06 Wed 128/Thu 60/Tue 53/Fri 68; G10 Wed 140/
Thu 65/Tue 15; G12 Mon 45/Thu 46. Il giovedi' compare per TUTTI (l'ancora del docente
mono-giorno). L'input NON specifica materia e insegnante dentro quei giorni: quella scelta
l'ha fatta la scuola, allineata a corsi/docenti, ed e' la parte arbitraria/manuale.

Conseguenza di modellazione: lo scheletro dei giorni-lab va preso dall'INPUT (gia' in casa),
non derivato dal golden; il golden serve solo per la mappa mancante (giorno -> materia ->
docente -> gruppo/periodo) come istanza di riferimento. Nota lever futuro (decisione maintainer,
non ora): oggi il vincolo "le ore lab stanno nei giorni L" e' enforced solo per i 3 gruppi FC
(FCQuota=true, H23/H24/M30/M31); per gli FA il piano L e' solo target di riempimento.
Legare le ore acconciatura degli FA ai loro giorni L dell'input carverebbe lo scheletro
della rotazione dagli input, senza golden.

## Domanda per referente (sostituisce la formulazione "5 in un lab?")

"Quando piu' gruppi hanno materie di acconciatura nello stesso orario: chi tiene il laboratorio
(esiste la rotazione per periodi? com'e' assegnata quest'anno?) e quali spazi usano gli altri
docenti che insegnano in parallelo? G14 e G12 confermate come ospiti (Q19/Q20)?"

## Struttura vs arbitrio (maintainer, 2026-07-31 sera, seconda parte - ANTI-OVERFITTING)

Le rotazioni sono regolari e rispettano i vincoli ESPLICITATI dai docenti (giorni di
disponibilita'/preferenze), ma hanno una componente **random/arbitraria**: DOVE iniziare la
rotazione, e QUALI altri giorni/slot scegliere per il surplus dei docenti non vincolati al
singolo giorno, sono solo "tentativi" di soluzione dello human planner. Percio':

- **STRUTTURALE (input autoritativo)**: la regolarita' per periodo, l'eredita' dello slot tra
  gruppi, i vincoli/preferenze dei docenti, la priorita' del docente mono-giorno.
- **ARBITRARIO (una istanza valida, NON da replicare come regola)**: l'istanza specifica del
  golden (quale gruppo in quale periodo, quali slot per il surplus). Copiarla cella per cella
  sarebbe overfitting su una scelta casuale dello human planner.

Evidenza in git che il progetto la trattava gia' cosi': commit `99197850` (22/02, backtracking
esaustivo su TUTTE le rotazioni per l'acconciatura, nessuna esclusa a priori tra le legali) e
`0a842142` (giorno/slot trattati come PREFERENZE, non vincoli: tutti i candidati (day x slot)
legali generati e ordinati per priorita'); famiglia TASK-120 (RotationCandidateGenerator,
RotationTestRunner early-abort, HybridPhase1aService). Quella linea fu accantonata quando
l'accordo con referente divento' "quella parte del problema ce la pre-alloca lui a mano": quindi
il canale d'ingresso naturale delle rotazioni e' l'INPUT della scuola (foglio del workbook),
col solver/backtracking come ripiego se l'input non arriva.

## Cornice strategica (maintainer, 2026-07-31 sera, parte 4)

Il foglio giorni-lab e' l'euristica piu' RECENTE dello human planner: fa confermare le date dei
lab ai docenti coinvolti, e poi risolve in un sottospazio che gli semplifica la vita. **Default
nostro: fare lo stesso** (consumare l'input, risolvere nel sottospazio). SE E SOLO SE riusciamo
a fare di meglio - non chiedere quei dati e valutare TUTTE le combinazioni di assegnamenti
regolari - possiamo offrire un servizio superiore: l'approccio umano e' ottimista (spera che la
selezione iniziale conduca a una soluzione valida, senza certezza matematica), noi possiamo
provare PIU' di una soluzione parziale, non una sola scelta arbitraria. E' materiale di SECONDA
FASE, tracciato in **TASK-467** (backlog indipendente, gated all'apertura esplicita di maintainer).

## Lavoro derivato

- Estrazione read-only delle rotazioni dal golden (per docente/weekday/slot -> timeline gruppi,
  con i confini di periodo): la tabella e' l'ISTANZA 2026/27 (utile per verifica del modello e
  come riferimento), NON la regola; il deliverable separa esplicitamente cio' che e' strutturale
  (verificato: regolarita', vincoli docente rispettati) da cio' che e' scelta arbitraria.
- Confronto delle semantiche per l'adozione: (a) co-presenza 464 com'e'; (b) rotazione + de-tag
  teoria per i tripletti FA con ospiti stretti; (c) misto. Decisione di maintainer coi numeri di
  entrambe.
