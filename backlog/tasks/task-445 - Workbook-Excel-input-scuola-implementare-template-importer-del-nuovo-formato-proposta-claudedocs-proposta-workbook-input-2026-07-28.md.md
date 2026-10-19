---
id: TASK-445
title: >-
  Workbook Excel input scuola: implementare template + importer del nuovo
  formato (proposta claudedocs/proposta-workbook-input-2026-07-28.md)
status: Done
assignee: []
created_date: '2026-07-28 08:43'
updated_date: '2026-07-30 16:35'
labels:
  - claude-planned
milestone: m-13
dependencies: []
ordinal: 268000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La proposta di design e' COMPLETA (direttiva maintainer 2026-07-28): workbook unico a 21 fogli normalizzati + foglio 99_Validazione con 23 controlli (i conflitti dati storici - G14 120vs115, stage G04 assente, saturazione T01 231h/288 80,2%, typo date - diventano errori all'inserimento); disponibilita' docenti a 2 livelli (regole concise con token italiani 'solo giovedi'/'solo pomeriggio' sopra colonne strutturate -> RecurringAvailabilityRule, + eccezioni datate opzionali; mai richiesto il calendario pieno); piano-giorni come pattern settimanale + eccezioni con motivo (da ~3.300 righe a ~270, principio pattern-exhaustion nel FORMATO). QUESTO task = l'implementazione: generare il template .xlsx reale, scrivere l'importer (o il convertitore verso i CSV seed), e i 23 check come validazione d'import. DIPENDE dai cluster m-13 (C1 anagrafica materie e C6 config surface in primis) e dal model-freeze per i fogli a rischio (02_Campanella/C7). Discrepanza da risolvere strada facendo: Disegno Edile ed Impiantistico (codice: prof non-lab; CLAUDE.md: lab tag informatica).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Architettura (decisioni Fedora per maintainer, NON riaprire)

1. **Generatore + lettori in PYTHON sotto `scripts/workbook/`** (venv git-ignored con openpyxl 3.1.5). Nessuna dipendenza nuova nel pom (niente Apache POI). E' un'operazione offline una volta l'anno.
2. **Catena di import: workbook .xlsx -> CSV canonici -> importatori CSV gia' esistenti e testati.** Nessun secondo percorso di ingestione parallelo. Foglio senza CSV canonico = GAP da annotare, non da improvvisare.

## Scoperta chiave pre-Tappa-1 (cambia la sorgente del gate)

TASK-437 (completato ieri) ha GIA' migrato i classificatori a prefisso in `seed/subjects.csv` (52 materie) via `SubjectRegistry`. Quindi la sorgente dei flag `laboratorio`/`professionalizzante`/`tagAula` di `08_Materie` e' `subjects.csv` (gia' verificata byte-identica ai vecchi prefissi dal guard test `SubjectRegistryTest`). Le colonne timing (`primaDelloStage`=Sicurezza, finestre T20, `capOreGiorno`=0 da M21_EXEMPT) sono ancora costanti lato solver. Numeri riconciliati: **incarichi reali = 266** (pseudo-docente {Stage,Esame,RA,Totale ore corso} esclusi, 29 righe), subjects.csv = 52, materie reali non classificate = solo {Esame, Stage} (correttamente assenti, vanno in 16/tipo).

## Decomposizione in tappe (una tappa = incremento chiudibile con DoD piena)

**Tappa 1: Bozza workbook 2026/27 + gate 08_Materie byte-identita' (IN CORSO)**
- venv openpyxl (git-ignored) + `scripts/workbook/generate_workbook.py` -> `AnnoScolastico_2026-27.xlsx`.
- Tutti i 21 fogli + 00_Istruzioni + 99_Validazione, con intestazioni ESATTE per la proposta, legenda su 00, colonna `origine` (`sistema` dove derivato).
- Fogli a sorgente diretta popolati: 01_Anno, 02_Campanella, 03_Chiusure, 04_GiorniSpeciali, 05_Docenti, 08_Materie, 09_MaterieAlias, 10_Classi, 11_ClassiCalendario, 12_Incarichi, 16_Stage, 17_Aule, 20_GiorniProfessionalizzanti.
- Fogli che richiedono RIORGANIZZAZIONE = stub con header esatti + riga marcatore `origine=sistema (gap: ...)` che documenta la sorgente pendente: 06/07 (compressione disponibilita'), 13/14/15 (piano-giorni come pattern), 18 (rotazioni, vuoto by design), 19 (LabAssistant morto, gap G6), 21 (cadenza, gap G1).
- 99_Validazione = contratto dei 23 controlli come tabella di documentazione (V1..V23, regola, livello), marcato "implementazione lato sistema = Tappa 5".
- **GATE (cancello di correttezza, proposta 5.2 + audit C1)**: `scripts/workbook/test_materie_byte_identity.py` con EXIT CODE. TASSONOMIA (laboratorio/professionalizzante/tagAula) byte-identica a subjects.csv (LIVE, link forte: subjects.csv e' la verita' estratta dal codice, garantita da SubjectRegistryTest lato Java). COLONNE TIMING (capOreGiorno/settimanaInizioMax-Min/primaDelloStage) = self-consistency vs lo snapshot sources.py (NON un link Java live: niente in questo percorso legge Java; uno stale snapshot passerebbe il gate in silenzio). Rendere quel link meccanico (test Java come SubjectRegistryTest, o codegen) e' lavoro futuro. Fallimento VISIBILE, non silenzioso.
- DoD: /simplify + /code-review high sul diff + gate exit-code 0 + commit + push.

**Tappa 2: Convertitori xlsx->CSV canonici (round-trip) + mappa gap**
- Per ogni foglio con CSV canonico (05->teachers, 17->rooms, 12->incarichi, 10->groups, 03->closures, 09->SubjectNormalization, 08->subjects), convertitore workbook->CSV + test round-trip identity. Mappa esplicita dei fogli senza CSV canonico (gap).

**Tappa 3: Compressione disponibilita' (06/07) + verifica espansione**
- 06 generato comprimendo availability_rules.csv in regole concise; 07 eccezioni datate. Verifica: l'espansione delle regole concise riproduce le VACATION equivalenti.

**Tappa 4: Piano-giorni come pattern (13/14/15) + espansione**
- Estrazione pattern per periodo dal per-data, eccezioni con motivo, espansione 15. Gate: 15 espanso == calendario CSV attuale byte-identico.

**Tappa 5: 99_Validazione lato sistema (23 controlli)**
- Implementazione Python dei 23 controlli (la proposta li vuole anche lato sistema perche' le formule Excel possono arrivare rotte). Casi: G14 990/115, G04 stage senza monte-ore, T01 80.2%.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
REQUISITI ANTI-AMBIGUITA' dalla giornata del 30/07 (maintainer: 'un xlsx usabile da referente e al tempo stesso chiaro per noi'). Ogni voce nasce da un errore reale di oggi, non da un principio astratto:
1. UN SOLO POSTO AUTORITATIVO PER OGNI FATTO. Il workbook della scuola aveva DUE fogli per G14 ('G14' = default non compilato con il calendario di G01, 'G14 2' = quello vero) piu' 'Copia di', 'def.', 'NON USARE', 'duale piano B': l'estrattore ha letto quello sbagliato e ci e' costata mezza giornata. Il nostro template NON deve permettere fogli fantasma: un foglio per gruppo, col nome canonico del gruppo, e il validatore FALLISCE se trova fogli fuori lista o duplicati di nome simile.
2. TOTALI CALCOLATI, MAI DIGITATI. I blocchi 'Totale ORE' devono essere formule sulle righe per-data, e il foglio 99_Validazione deve incrociare calendario contro incarichi: capienza >= obbligo per ogni gruppo, e allarme se il rapporto supera una soglia (il calendario di G14 copiato da G01 dava 990 contro 115 di incarichi, un 9x che NESSUNO ha visto per settimane: col controllo sarebbe emerso al primo import).
3. SPLIT AULA/LAB NATIVO PER DATA. I fogli per-classe attuali hanno una sola modalita' per giorno; 'G14 2' ha date con aula E lab insieme e abbiamo dovuto estendere il formato a valle. Il template deve avere colonne Au/La/St per data per TUTTI i gruppi, non solo per gli FC.
4. NOTE SEPARATE DAI DATI. In 'G14 2' la colonna 'St' conteneva testo libero ('rosy', 'colloqui t14') e un testo era finito anche nella colonna La: il parser regge, ma il template deve avere una colonna Note dedicata e le colonne dati devono validare come numeriche.
5. LE CORREZIONI GOLDEN-FIRST DEVONO DIVENTARE INPUT. Oggi i CSV committati NON sono output puri dell'estrattore: contengono curatele (stage G04 del TASK-434) che una rigenerazione sovrascrive, ripristinate a mano due volte in un giorno. Nel workbook lo stage di G04 (240h, 30 date) deve essere una RIGA DICHIARATA dalla scuola nel foglio 16_Stage, cosi' la catena estrai->importa torna idempotente e la distinzione estratto-vs-curato sparisce.
6. CELLE FUORI GRIGLIA DICHIARATE. Il sabato 16/01 di G14 (5h, Q28) e il sabato 19/09 del golden (Fasullo): il template deve permettere ore fuori dalla griglia lun-ven SOLO in una colonna esplicita 'fuori griglia' con motivo, mai come righe ordinarie che poi diventano bersagli irraggiungibili.
Queste sei voci sono la 'Tappa 6: hardening del template' e vengono PRIMA di far compilare qualsiasi cosa a referente.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: zai
created: 2026-07-29 18:10
---
DECISIONE maintainer (2026-07-29, tramite utente) sulle ore Esame/RA in 12_Incarichi:
- Lo SCHEMA del foglio prevede colonna tipo = ordinario | esame (quelle ore esistono davvero, un foglio che le tace mente).
- Il GENERATORE V1 emette SOLO le 266 righe ordinarie, cosi' il cancello di identita' con i classificatori attuali resta verde (nessun cambio di comportamento mascherato da migrazione).
- Le ~164h delle classi diventano una voce ESPLICITA e nominata in 99_Validazione (buco dichiarato con il numero accanto, non riconciliazione finta).
- La scuola le compilera' come righe tipo=esame quando compilera' il workbook.
Verifica meccanica (non fiducia nell'agente): Esame 124h su 6 classi (G08 24, G11 24, G10 20, G12 20, G09 24, G13 12) + RA 40h su G08 = 164h. GAP-1 in 99_Validazione riporta lo scomposizione reale.

DOMANDA EMERGENTE per maintainer (vado avanti, non blocco): RA e' un pool (40h, G08) NON coperto dal tipo {ordinario, esame} scelto. Come lo modelliamo? (a) aggiungere tipo=pool allo schema, (b) assorbire RA in esame, (c) lasciarlo come gap separato (come ora). Attualmente e' dichiarato in GAP-1 come pool non coperto.
---

created: 2026-07-29 19:16
---
#2 - zai - 2026-07-29 - DECISIONE maintainer (tramite utente) su RA (40h, G08):
- Il vocabolario della colonna tipo diventa TRE valori: ordinario | esame | pool.
- Le 40h di RA su G08 sono ore che la scuola deve alla classe SENZA legarle in partenza a un docente: fatto di natura diversa, non variante di ordinario o esame. Sotto ordinario fabbricherebbe un'assegnazione di docente inesistente; fuori farebbe mentire il foglio su 40 ore.
- Regola = stessa dell'esame: lo schema (tipo=pool) le prevede, il generatore V1 NON le emette (cancello di identita' resta verde), e il foglio 99 le nomina come voce esplicita con il numero (GAP-1, ora Esame 124h tipo=esame + RA 40h tipo=pool = 164h). La scuola le compilera' come righe tipo=esame | pool.
- APPLICATO in 00_Istruzioni (tipo=ordinario|esame|pool) + GAP-1 in 99_Validazione. DOMANDA RA CHIUSA.
---

created: 2026-07-29 19:38
---
#3 - zai - 2026-07-29 - TAPPA 4 avviata. Ground truth verificata meccanicamente sui raw (reproduce dei due loader DataGenerator.generateDailyActivityQuotas + generateExpectedDailyHours):
- DailyActivityQuota seeded = fc.csv (3 gruppi 1/G08+G13, split aula/lab/stage, FCQuota=true) + class_days.csv (altri 11, modality A->aula/L->lab/S->stage, FCQuota=false). Loader skip ore<=0 e fuori-orizzonte, NON skip chiusure/weekend.
- Totali CORRENTI: fc 515 righe in-horizon + class_days 1880 = 2395 righe in-grid+off-grid.
- DATA DRIFT: il javadoc di DataGenerator (righe 852-866) e' STALE (dice 513 righe, G04 stage=0): il CSV calendario_fc.csv e' stato rigenerato 2026-07-28 04:27 e ora G04 ha stage=240 (periodo stage in-grid da 2027-04-26, +16h off-grid = TASK-434). La verita' del gate = CSV corrente, non il javadoc.
- SPLIT FORZATO DAL MODELLO: il gate byte-identico e' sul piano IN-GRID (Mon-Fri non-closure) perche' il sabato non puo' essere giorno-pattern (giorniSettimana=LUN-VEN). Le 43 righe off-grid (26 sab + 19 chiusura, TUTTE stage, overlap Sat&chiusura=2) = ore stage fuori-griglia, DICHIARATE in 99 (GAP-2) come oreFuoriGriglia, NON nascoste in 13/14. In-grid = 2352 righe.
- No-session (giorno scolastico Mon-Fri non-closure senza riga quota): 0 per la maggior parte dei gruppi, fino a 21 (G12, su Thu/Fri durante lo stage). Diventano eccezioni 0,0,0 esplicite con motivo (rimuove l'ambiguita' 'riga dimenticata vs non in sessione' del proposal V16/H26).
- Algoritmo di segmentazione: spezza dove il piano (aula,lab,stage) cambia; mergia attraverso i gap di chiusura e no-session; le deviazioni di data singola (es. primo giorno 2026-09-14 ramp 4h, G04 Thu 2027-01-07) diventano eccezioni; i periodi devono coprire >=2 date (ricorrenza), la data singola e' sempre eccezione.
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Tappa 1 completata (2026-07-29). Generatore Python del workbook di input 2026/27 sotto
scripts/workbook/ (venv git-ignored openpyxl 3.1.5): generate_workbook.py + sources.py
(letture live seed CSV + snapshot costanti Java con provenance # SRC:) + test_materie_byte_identity.py
(gate) + README.md. Tutti i 21 fogli + 00_Istruzioni + 99_Validazione (23 controlli come contratto).
Fogli a sorgente verificata popolati: 01,02,03,04,05,08,09,10,11,12,16,17,20. Fogli con GAP
(header + marcatore, sorgente = tappa successiva): 06,07,13,14,15,18,19,21. Nessun dato inventato.
GATE: 08_Materie TASSONOMIA byte-identica a subjects.csv (live, 52 materie), copertura OK
(266 incarichi reali, tutte le materie di insegnamento classificate), timing = self-consistency
vs snapshot sources.py (onestamente documentato come NON link Java live nel docstring del gate).
Scoperta chiave: TASK-437 aveva gia' migrato i prefissi in subjects.csv, quindi la sorgente del
gate e' subjects.csv (garantita da SubjectRegistryTest). /simplify (4 angoli) e /code-review high
(5 finder) eseguiti, tutti i finding confermati applicati (lru_cache loader, import shared.py,
tint origine value-driven, split gap/caveat, _read_seed !=, _opt_* None, 04 ramp su cella Lunedi
non-null, tag sort/lowercase, em-dash ban, onesta' del gate). Verifica exit-code: gate EXIT 0,
test.sh EXIT 0 (874 tests, 0 fail, 0 err). DOMANDA DIFFERITA per la scuola/Fedora (proposta
interna-mente incoerente): ore di Esame/RA (~164h, 6 classi) vanno rese righe 12_Incarichi con
tipo=esame/pool (esempi 3.12) o restano escluse (righe 266)? Fino alla decisione il monte-ore
V1 non riconcilia per quelle 6 classi. Annotata in 00_Istruzioni + qui. Next: Tappa 2
(convertitori xlsx->CSV canonici + round-trip).

TAPPA 2 completata (2026-07-29). Converter xlsx->CSV canonici (scripts/workbook/xlsx_to_csv.py) + gate round-trip identity (test_roundtrip_identity.py) + mappa gap nel README. Copre i 3 fogli con mapping pulito (08_Materie->subjects.csv, 17_Aule->rooms.csv, 10_Classi->groups.csv). CANCELLO: il giro seed->workbook->CSV torna byte-identico + cella per cella (write_csv esercitata: una divergenza = exit != 0, verificato con test negativo su cambio delimiter). 12_Incarichi (266/295, Esame/RA = GAP-1) e 05_Docenti (4/6 colonne, weekly-count in 06) = PARZIALE, esclusi dal gate perche' lossy by design. Resto = GAP (nessun CSV canonico o riorganizzazione = tappe 3/4). /simplify (4 angoli) + /code-review high (3 finder correttezza/conventions) eseguiti: finding chiave applicato = il gate ora esercita write_csv (l'ultima freccia della catena era non testata). Exit code: round-trip gate 0 (+ negative test), test.sh 0 (874/0/0). Next: Tappa 3 (compressione disponibilita' 06/07).

TAPPA 3 completata (2026-07-29). Compressione disponibilita' docenti: scripts/workbook/availability_compress.py comprime le 39 chiusure ricorrenti di availability_rules.csv in 16 regole concise (SOLO_GIORNI/MAI_GIORNI/SOLO_FASCIA/FINESTRA) + 2 conteggio settimanale (T11 MAX_INIZI_PRESTO=1, T05 MIN_GIORNI_LIBERI=2 da teachers.csv); 3 eccezioni datate (visite T12) -> 07. 06/07 ora POPOLATI nel workbook (non piu' GAP). CANCELLO: comprimi->espandi == stesso insieme di (teacher,weekday,slot chiusi,forza), exit != 0 su diff. Due gate-blind hole fixati: (1) asserzione per-regola che SOLO_GIORNI non coesiste con una regola parziale sullo stesso (teacher,forza) - la regola bugiarda verrebbe nascosta dall'overwrite in expand; (2) chiusura ricorrente non comprimibile = fail-loud (gap di vocabolario 06), NON spostata a 07 (schema 07 e' datato). MATTINO/POMERIGGIO derivati dalla campanella (non hardcoded, audit bug 4). /simplify (4) + /code-review high (2 finder + altitude): finding chiave = ordine giorni alfabetico->cronologico (human-facing), tipo mapping esaustivo su AvailabilityType{VACATION,SICK,PREFERENCE}. Exit code: 3 gate 0 (+ negative test su bugiardo/gap), test.sh 0 (874/0/0). DOMANDA RA (40h pool G08) sempre aperta. Next: Tappa 4 (piano-giorni come pattern 13/14/15).

TAPPA 4 completata (2026-07-29). Piano-giorni come pattern: scripts/workbook/piano_giorni_compress.py estrae il per-data (sources.daily_activity_quota riproduce ENTRAMBI i loader DataGenerator.generateDailyActivityQuotas+generateExpectedDailyHours, con group-existence skip e round-half-up fedeli al Java) in pattern settimanali per periodo (13, 172 righe) + eccezioni datate con motivo (14, 113: deviazioni + no-session 0,0,0 H26 esplicito). Espande 15 (2394 righe, una per giorno scolastico in-grid, 2352 ore>0 + 42 no-session). CANCELLO test_piano_giorni_roundtrip: 15 espanso == quota IN-GRID byte-identico (0 residui) + onesta' PER-PATTERN (boundaries TIGHT alla data => segmentazione mai allargata, verificato empiricamente che widening e' ora rilevato su tutte le 172) + fail-loud sui residui (exit != 0 se > 0) + off-grid dichiarato (43 righe stage su sabati/chiusure = oreFuoriGriglia GAP-2 in 99, non nascoste). test_piano_giorni_negative PROVA il fail-loud su 4 corruzioni (drop-deviation / widen-period / drop-pattern / phantom-offgrid). SPLIT forzato dal modello: sabato non e' giorno-pattern (giorniSettimana=LUN-VEN) quindi le ore stage off-grid sono dichiarate, non in 13/14. DATA DRIFT: javadoc DataGenerator (513/G04 stage 0) STALE; CSV rigenerato 2026-07-28 ora 515 fc + 1880 class_days = 2395 (2352 in-grid + 43 off-grid), G04 stage 224 in-grid (+16 off-grid = TASK-434). /simplify (9 finding: lru_cache compress/dates, delete dead metric, WEEKDAY_CODES alias, eager stage windows, heavy_groups predicate, rekey expand, top-level imports). /code-review high (4 finder + verify): 5 finding confermati corretti = (1) gate widening tightness [CRITICO: 70/172 erano widen-invisible, ora 0], (2) group-existence skip fedele a findGroupByName, (3) round-half-up invece di banker's rounding, (4) modality fail-loud documentato, (5) stage 240->224 mislabel. Exit code: 5 gate Python 0 (+ negative test), test.sh 0 (Java non toccato). Gruppi eccezioniosi dichiarati in 99 PAT-1: G06, G07, G09, G10, G12, G13. Sabato 19/09 Fasullo del golden NON incontrato (non nella quota). Next: Tappa 5 (99_Validazione lato sistema, 23 controlli Python).

TAPPA 5/5b (2026-07-30): 99_Validazione lato sistema = 20/25 controlli implementati e verificati (skeptic x2 con evidenza indipendente). Modulo scripts/workbook/validazione.py (framework + registry cid-keyed in sheet order) + gate test_validazione.py (baseline regression: 24 ERRORE / 8 AVVISO / 5 DEFERRED su 25 cid).

Implementati: V1 monte-ore, V2 incarichi-vs-piano, V3 stage tripla coerenza (existence+hours), V4/V4b capacita' docente+saturazione, V5 materie, V6 date, V7 finestra, V8 giorno-chiuso, V9 cap giornaliero, V10 nomi, V11 chiavi, V13 capienza aula, V16 copertura piano-giorni, V18 sigle, V19 slot, V20 alias/canonici, V21 token/struttura, V22 regole, V23 esterni. Casi di prova verificati: V1 G14 residuo 5, V4b T01 80.2%, V2 G14 -875, V3 G04/G08/G09/G10/G13 stage senza 16_Stage.

5 DEFERRED con motivo: V4c (ridondante con V4, intento pieno serve giorno-per-materia non nel seed), V12 (manca sorgente 'blocco per materia'), V14 (LabRotation vuota di proposito 2026/27), V15 ('prof solo in finestra stage' e' proprieta' dello schedule non dell'input, ambigua pre-solve), V17 (invariante sull'output Tappa 4, gia' coperta dal suo gate).

Decisioni semantiche PRIMA del codice (maintainer: dichiara l'interpretazione): V3 formula conciliazione oreTotaliAttese==in-grid+off-grid (verificata sui 3 dichiarati); G14 View A RATIFICATA (proposta 5.3.3: flagga ERRORE, finding nomina isG14 SSCP:1839). DoD piena per ogni incremento (/simplify + /code-review/skeptic + 6 gate EXIT 0).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Skill /simplify eseguita sul diff e finding applicati
- [x] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [x] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->
