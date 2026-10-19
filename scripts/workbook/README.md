# Workbook di input scuola (TASK-445)

Generatore Python del workbook Excel di input per un nuovo anno scolastico, secondo
la proposta `claudedocs/proposta-workbook-input-2026-07-28.md` (21 fogli + 00_Istruzioni
+ 99_Validazione). Strumento OFFLINE, una volta l'anno: nessuna dipendenza nel pom
(niente Apache POI).

## Architettura (decisioni Fedora per maintainer, NON riaprire)

1. Generatore e lettori in PYTHON con openpyxl, sotto `scripts/workbook/`.
2. Catena di import: workbook `.xlsx` -> CSV canonici -> importatori CSV gia' esistenti.
   Nessun secondo percorso di ingestione parallelo. Foglio senza CSV canonico = gap
   annotato, non improvvisato.

## Setup

openpyxl vive in un venv git-ignored (gia' in `.gitignore`):

```bash
python3 -m venv scripts/workbook/.venv
scripts/workbook/.venv/bin/python -m pip install openpyxl
```

## Uso

```bash
# genera la bozza 2026/27 (default: scripts/workbook/output/AnnoScolastico_2026-27.xlsx)
scripts/workbook/.venv/bin/python scripts/workbook/generate_workbook.py

# gate di byte-identita' 08_Materie (exit code: 0 ok, 1 divergenza)
scripts/workbook/.venv/bin/python scripts/workbook/test_materie_byte_identity.py
```

## File

| File | Scopo |
|------|-------|
| `sources.py` | Sorgenti dati: letture live dei seed CSV + snapshot delle costanti Java (con provenance `# SRC:`). |
| `generate_workbook.py` | Genera `AnnoScolastico_<label>.xlsx`. Un foglio = una funzione. |
| `availability_compress.py` | Tappa 3: comprime le chiusure docenti in regole concise 06 + eccezioni datate 07. |
| `piano_giorni_compress.py` | Tappa 4: comprime il piano-giorni per-data in pattern settimanali 13 + eccezioni 14; espansione 15. |
| `xlsx_to_csv.py` | Tappa 2: converte il workbook nei CSV canonici. |
| `test_materie_byte_identity.py` | Gate: tassonomia 08_Materie byte-identica a subjects.csv (live); colonne timing = self-consistency vs snapshot sources.py. |
| `test_roundtrip_identity.py` | Gate Tappa 2: seed -> workbook -> CSV byte-identico (08/17/10). |
| `test_availability_roundtrip.py` | Gate Tappa 3: comprimi=espandi delle chiusure docenti (+ onesta' per-regola, fail-loud sui gap). |
| `test_piano_giorni_roundtrip.py` | Gate Tappa 4: 15 espanso == quota in-grid byte-identico + onesta' per-pattern + fail-loud sui residui. |
| `test_piano_giorni_negative.py` | Gate Tappa 4 (negative): PROVA che il gate fallisce rumoroso su drop-eccezione / periodo-allargato / drop-pattern / off-grid-fantasma. |
| `theory_block_policy.py` | TASK-471: deriva la policy blocco teoria (1h/giorno vs 2h) per (classe, materia) dal golden (parse + trappole + `is_theory` autoritativo importato), per il foglio 22_TeoriaBlocco. |
| `test_theory_block_identity.py` | Gate TASK-471: la policy live == draft Fedora-verificato (set-based, non line-based) + sanity 196/6800h/split 157-31-8. |
| `cadence_reference.py` | TASK-445/441: legge `golden-cadence-reference.json` (riferimento M34/M35) e emette le coppie (classe, materia, max, settimane attive) per il foglio 21_CadenzaAttesa (no re-derivation, solo rename dei campi JSON). |
| `test_cadence_identity.py` | Gate cadenza: reshape fedele al JSON + round-trip del foglio 21 (258 coppie). |
| `fixed_weekday_policy.py` | Mining 15/08: giorno fisso per (classe, materia) dal golden con filtro deducibilita'-da-cadenza, per il foglio 23_GiornoFisso (40 coppie genuinamente nuove). |
| `test_fixed_weekday_identity.py` | Gate 23_GiornoFisso: sanity 40 coppie + pin + filtro + round-trip. |
| `concentration_policy.py` | Mining r2 16/08 (baseline CORRETTA, guard modality S): concentrazioni mensili forti (>=4x atteso, >=25% dell'anno) per il foglio 24_ConcentrazioneMensile (6 righe). |
| `test_concentration_identity.py` | Gate 24: sanity 6 righe + pavimenti + pin + artefatti-baseline-buggata assenti + round-trip. |
| `weekday_fill_policy.py` | Mining r2 16/08 sez. D corretta: celle (classe, weekday) che il golden riempie < 80% del proprio calendario (semantica sources: skip FC, guard S, osservato solo su giorni attesi), per il foglio 25_WeekdayFill (4 celle, tutte G04 lun-gio). |
| `test_weekday_fill_identity.py` | Gate 25: sanity 4 celle + pin + artefatti prima-versione assenti + round-trip. |

## Stato (Tappa 4, 2026-07-29)

Fogli popolati da sorgente verificata: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12,
13, 14, 15, 16, 17, 20, 21, 22, 23 (mining 15/08), 24 e 25 (mining r2 16/08, origine=sistema
in attesa delle risposte referente di domande-brevi.md). Il 99_Validazione e' popolato (23 controlli + GAP-1/GAP-2/PAT-1)
ma e' documentazione, non dato seed. Fogli con header pronti + marcatore GAP
(sorgente = tappa successiva): 18, 19. Nessun dato inventato: la cella origine e'
GIALLA quando vale sistema (proposta), le righe in GRIGIO `[GAP ...]` documentano una
sorgente pendente.

TASK-471 (2026-08-07): il foglio `22_TeoriaBlocco` (196 righe per (classe, materia))
porta la distribuzione teoria (1h/giorno vs 2h) come INPUT DICHIARATO, derivato dal
golden come proposta `sistema` (referente conferma). Sorgente: `theory_block_policy.py`
(golden-runtime read, `is_theory` importato dal modulo analyze autorevole); gate
`test_theory_block_identity.py` (policy live == draft Fedora-verificato, set-based).
Non e' adozione: M-TR (TASK-468) resta flag OFF. Reader xlsx->CSV = tappa successiva
(come 18/19/21).

Tappe successive nel piano del task TASK-445 (Tappa 2 convertitori xlsx->CSV, Tappa 3
compressione disponibilita', Tappa 4 piano-giorni come pattern, Tappa 5 validazione
lato sistema).

## Tappa 2: mappa CSV canonici (2026-07-29)

`xlsx_to_csv.py` converte il workbook nei CSV canonici; `test_roundtrip_identity.py`
e' il cancello (seed -> workbook -> CSV deve tornare identico cella per cella,
exit != 0 alla prima divergenza). Un foglio senza CSV canonico e' un GAP
annotato, non improvvisato.

| Foglio | CSV canonico | Round-trip | Note |
|--------|--------------|------------|------|
| 08_Materie | subjects.csv | PULITO (gate) | 52 righe, identity cella per cella |
| 17_Aule | rooms.csv | PULITO (gate) | 18 righe |
| 10_Classi | groups.csv | PULITO (gate) | 14 righe |
| 12_Incarichi | incarichi.csv | PARZIALE | 266/295 righe: le 29 pseudo (Stage/Esame/RA/Totale) escluse per design; ore Esame/RA = GAP-1 in 99 (decisione maintainer) |
| 05_Docenti | teachers.csv | PARZIALE | 4/6 colonne: maxEarlyStartDaysPerWeek e requiredFreeDaysPerWeek migrati in 06 (Tappa 3) |
| 06_DocentiDisponibilita | availability_rules.csv | POPOLATO (Tappa 3) | 16 regole concise (SOLO_GIORNI/MAI_GIORNI/SOLO_FASCIA/FINESTRA) + 2 conteggio settimanale; gate comprimi=espandi |
| 07_DocentiEccezioni | availability_rules.csv | POPOLATO (Tappa 3) | 3 eccezioni datate (visite T12) |
| 13/14/15 | calendario_class_days.csv + calendario_fc.csv | POPOLATO (Tappa 4) | 172 pattern + 113 eccezioni spiegano 2352 date in-grid (0 residui); 43 righe off-grid (stage su sabati/chiusure) = GAP-2 oreFuoriGriglia |
| 01,02,03,04,09,11,16,18,19,20,21 | (nessuno) | GAP | costanti Java o nessun CSV; derivation documentata in sources.py |

Il converter copre solo i 3 PULITO: una mappatura lossy (12, 05) o assente
sarebbe il "secondo percorso di ingestione" che l'architettura vieta.

## Tappa 4: piano-giorni come pattern (2026-07-29)

`piano_giorni_compress.py` estrae dal per-data (calendario_class_days.csv 11 gruppi
+ calendario_fc.csv 3 FC, riproducendo i due loader di DataGenerator) i pattern
settimanali per periodo (13) + le eccezioni datate con motivo (14); `expand`
ricostruisce il per-data (15). `sources.daily_activity_quota()` e' la verita' di
riferimento (un solo punto che riproduce entrambi i loader, non due copie a mano).

CANCELLO (`test_piano_giorni_roundtrip.py`): 15 espanso == quota IN-GRID
byte-identico su (classe, data, aula, lab, stage) + onesta' PER-PATTERN (ogni
giorno scolastico nel periodo e' piano==quota o eccezione esplicita) + FAIL-LOUD
sui residui (date non spiegate: contate nell'output, exit != 0 se > 0) +
segmentazione mai allargata (un pattern copre >= 2 date; la data singola e'
sempre eccezione). `test_piano_giorni_negative.py` PROVA il fail-loud corrompendo
un compress() reale (drop-eccezione / periodo-allargato / drop-pattern /
off-grid-fantasma: ognuno rilevato).

SPLIT FORZATO DAL MODELLO: il sabato non e' giorno-pattern (giorniSettimana =
LUN-VEN), quindi le 43 righe off-grid (26 sabati + 19 chiusure, tutte stage, no
Timeslot) NON sono in 13/14/15: dichiarate come oreFuoriGriglia in 99 (GAP-2), da
riconciliare con 16_Stage.oreFuoriGriglia. Mai nascoste in un pattern.

DATA DRIFT: il javadoc di DataGenerator (513 righe, G04 stage=0) e' STALE; il CSV
calendario_fc.csv rigenerato 2026-07-28 ora produce 515 righe fc + 1880 class_days
= 2395 (2352 in-grid + 43 off-grid). La verita' del gate e' il CSV corrente.

