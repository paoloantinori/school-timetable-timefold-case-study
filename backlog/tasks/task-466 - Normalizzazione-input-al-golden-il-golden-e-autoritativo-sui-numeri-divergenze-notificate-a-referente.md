---
id: TASK-466
title: >-
  Normalizzazione input al golden: il golden e' autoritativo sui numeri,
  divergenze notificate a referente
status: Done
assignee: []
created_date: '2026-07-31 10:53'
updated_date: '2026-07-31 13:33'
labels:
  - claude-planned
  - seed
  - golden-first
milestone: m-12
dependencies: []
priority: high
ordinal: 289000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Direttiva maintainer 2026-07-31: 'prendiamo come validi i numeri estratti dal golden, e segnaliamo ogni divergenza con i presunti dati di input a referente per notifica/validazione; ci serve replicare soluzioni valide come quelle del golden; in futuro il golden non ci sara', ma per ora guida la modellazione e la normalizzazione delle discrepanze'. Contesto: direttiva maintainer 2026-07-31 pomeriggio sulle piste di re-interpretazione dei ~92 room-null lab; CLAUDE.md sezioni 'Golden-first recalibration' e 'Il golden come oracolo sui dati'.

Lavoro previsto:
(a) Censimento sistematico delle divergenze input-vs-golden gia' note: T28 244 vs 192 (col caso impossibile G10 11 blocchi/9 slot), G09 666 vs 640, Q9 MODA turni vs orario (es. Confezione 180 vs 169), G04 85h di slack (835 vs 750), Fasullo 112h di sabato segnaposto, sabato G14 16/01 5h; piu' eventuali nuove divergenze scoperte con un diff meccanico incarichi-vs-golden per (docente, gruppo, materia).
(b) Normalizzare i dati di seed ai valori golden dove divergono. ATTENZIONE: DataGenerator.java e' file protetto (.claude/rules/datagenerator-protection.md); la direttiva di maintainer autorizza la DIREZIONE, ma ogni modifica concreta va elencata e documentata nel registro datagenerator-authorized-changes.
(c) Produrre il doc di notifica per referente 'divergenze-input-vs-golden' con una riga per divergenza (valore input, valore golden adottato, ipotesi: vincolo mancante oppure errore umano), da aggiungere al pacchetto pronto-da-inviare.

Trappole del golden da escludere nel diff (CLAUDE.md): 117 ore di sabato (112 Fasullo segnaposto del 19/09), 187 ore Stage off-grid, 5 di Orientamento 16/01; docenti presenti nel golden ma assenti dagli incarichi si scartano e si DICHIARANO, mai assorbiti in silenzio.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Diff meccanico completo incarichi-vs-golden per (docente, gruppo, materia) committato, con le trappole note del golden (sabati, Fasullo, Stage off-grid) escluse e dichiarate
- [ ] #2 Seed normalizzato ai valori golden dove divergono, con test verdi (test.sh exit code 0, incluso il guard di regressione golden)
- [ ] #3 Registro datagenerator-authorized-changes aggiornato con ogni modifica concreta a DataGenerator.java
- [x] #4 Doc di notifica per referente 'divergenze-input-vs-golden': una riga per divergenza con valore input, valore golden adottato, ipotesi (vincolo mancante o errore umano)
- [ ] #5 DoD piena (simplify + code-review high + test.sh con exit code)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-31 13:02
---
VERDETTO parte (a) (zai, 2026-07-31, verificato adversarial-verification): diff triplo incarichi-vs-golden = 0 divergenze reali, confermato due volte in modo indipendente (script esistente + full-outer-join ricalcolato). Totale di gruppo = 0 delta per 13/14 gruppi; G08 match esatto a 626h escluso il pool RA. Unico delta numerico in tutto il dataset: size pool RA G08, 40h (incarichi) vs 41h (golden), 1h, dentro artefatto contabile noto (micro-corsi). TUTTE le divergenze documentate sono STALE/RISOLTE: T01/T28 244vs192 -> 231=231; G09 666vs640 -> 666=666; G04 835vs750 -> risolta TASK-434/457 (835 capacita', 750 erogazione).
---

created: 2026-07-31 13:02
---
IMPLICAZIONE per (b): incarichi.csv e' gia' normalizzato al golden (TASK-371), quindi (b) e' di fatto un no-op sul lato incarichi. La tabella discrepanze in .claude/rules/datagenerator-protection.md e' STALE (riportata nel doc risultati). (c) bozza doc referente prodotta. Out-of-scope diff triplo: cadenza Q9 MODA (Confezione), tracciata in m-12.
---

created: 2026-07-31 13:02
---
Artifact: claudedocs/spalmone-2026-27/task466-incarichi-vs-golden-diff-2026-07-31.md (tabella divergenze + riproducibilita') + referente-notifica-divergenze-input-vs-golden-2026-07-31.md (bozza notifica, 1 riga da notificare: pool RA 40vs41). DoD: parte (a) e' read-only analysis (zero code, zero DataGenerator), nessun diff codice da /simplify o /code-review; le AC#2 (seed normalizzato) e #3 (registro DataGenerator) sono parte (b) gated a maintainer.
---

created: 2026-07-31 13:33
---
PARTE (b) = NO-OP sugli incarichi (formalizzato, 2026-07-31 sera): il diff a 0 divergenze reali (parte a, verificato adversarial x2) implica che incarichi.csv e' gia' normalizzato al golden (TASK-371), quindi la normalizzazione del seed e' vacua sul lato incarichi: nessuna triples docente/materia/gruppo da ricalibrare, nessuna riga di DataGenerator da toccare. AC#2 (seed normalizzato) e AC#3 (registro DataGenerator) vacue sul lato incarichi; restano gated a maintainer solo se si decidesse di toccare il seed per altre ragioni. Unica posta numerica aperta = pool RA G08 40h vs 41h (1h, artefatto contabile micro-corsi), che e' notifica referente (gia' in referente-notifica doc riga #1), NON modifica al seed. Tabella discrepancy in .claude/rules/datagenerator-protection.md allineata: T28/G09 = RESOLVED con puntatore al diff doc. Doc referente aggiornato con sezione 'Normalizzazione seed (parte b) = no-op'. DoD parte (b): read-only/docs, nessun diff codice.
---
<!-- COMMENTS:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
