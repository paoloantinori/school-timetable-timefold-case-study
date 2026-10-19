# TASK-445 Tappa 6 (hardening template) - HANDOFF per-requisito (2026-07-30)

Scritto per context-alto (direttiva maintainer: "se il contesto si alza scrivi l'handoff con
lo stato per-requisito"). Stato durevole per riprendere pulito.

## Contesto: 30h chiuso prima di questo blocco

Il blocco 30h (TASK-455 POINT 3, M-TC sopra v1) e' COMPLETO e pushato (commit ef2eb58d,
VERDICT in `certification-2026-07-30h/VERDICT.md`). **M-TC e' una leva DUD**: chiude 12/119
room-null (solo coda acconciatura TUE/WED; picco THU 50 T01-pinned immobile) a costo
-121k medium (ratio ~10k medium/room-null). Guardie tengono (full-day 88,4%, ricorrenza
74,9%, holes 0). M-TC resta DEFAULT OFF, decide maintainer. La room-null acconciatura e' un
problema di costruzione (TASK-449), non di ricerca.

## TASK-445 Tappa 6: i 6 requisiti (commit 4326eaeb, in repo)

Ognuno nato da un errore reale del 30/07. Criterio: il workbook della scuola di oggi
ricaricato nel nostro template deve FAIL rumoroso sui difetti che ci hanno morso.

### Stato per-requisito

| # | Requisito | Stato | Note |
|---|---|---|---|
| 3 | Au/La/St nativo per data (tutti i gruppi) | **FATTO + PUSHATO + DoD** (0c992b62) | loader sources.py aggrega split-modality (mirror Java); DoD coperta dal review consolidato |
| 6 | Ore fuori griglia dichiarate | **FATTO + PUSHATO + DoD** (e79a3e47) | gate piano-giorni rilassato (any modality, dichiarato); metric req-6-allineata |
| - | Baseline drift G14 (cascata TASK-462 su validazione) | **FATTO + PUSHATO + DoD** (e79a3e47) | V2/V7/V8/V9/V16 rigenerati, prima/dopo DICHIARATO |
| 1 | Un posto autoritario per fatto (no fogli fantasma/dupi) | **FATTO + PUSHATO + DoD** (55195650 + fix 5a658e63) | V24: classi referenziate validate contro il 10_Classi del WORKBOOK (non seed). + cambio API |
| 2 | Totali = formule + cross-check calendario/incarichi | **FATTO + PUSHATO + DoD** (f3ff3539) | V25: totali 12 + CALCOLO TOTALE 99 sono formule; 99 cross-check per-classe con ALARM [0.8,1.2] |
| 4 | Note separate, colonne dati numeriche | **FATTO + PUSHATO + DoD** (73b56dcb) | V26 colonne numeriche + DataValidation prevenzione; note separate (ultima colonna) |
| 5 | Correzioni golden-first = input (stage G04 in 16_Stage) | **FATTO + PUSHATO + DoD** (61664f6a) | STAGE_PATTERNS +G04 (224h misurati); V1 G04 +240h fantasma -> +16h reale; V3 'nessuna riga' rimosso |

**STATO SUITE (2026-07-30 notte): 7/7 gate workbook VERDI + 9 casi negative EXIT 0.**
Tappa 6 COMPLETA (6/6 requisiti). DoD piena per ognuno (/simplify + /code-review high
consolidati, reviewer Opus: M1 V24 canonical-from-workbook e M2 V25 no-silent-pass
trovati e fixati). Bozza 2026/27 rigenerata, identita' verificata (byte-identity +
roundtrip). Pronta per referente.

## STATO FINALE (notte 30->31/07, zai) - Tappa 6 CHIUSA, 3 spike fatti

- **Coda approvata COMPLETA**: bridge M-TC committato (3f4086bf) + req1+API (55195650) +
  req2 (f3ff3539) + req4 (73b56dcb) + req5 (61664f6a) + fix DoD (5a658e63) + regen/identita.
  HEAD `cb10d3c5`, tutto pushato.
- **3 spike esplorativi** (dietro flag/read-only, nessuna adozione): S1 isG14 (NO-OP al
  checkpoint, LS-only, TENERE con commento da aggiornare), S2 builder room-aware (premessa
  CONFERMATA: 69 lezioni-eccedenti / 33 acconciatura misurati; prototipo = gate mattino),
  S3 M20 step 1500 (NO-OP al checkpoint, NEUTRO). Doc: `spike-s{1,2,3}-*-2026-07-30.md`.
- **Per maintainer (dal QUADRO):** (1) aggiornare commento isG14 (1 riga, stale 990h); (2)
  decidere priorita' del prototipo builder room-aware (S2) al gate del mattino. Niente
  bloccante dalla Tappa 6.
- **Lezione di metodo (S1+S3):** parametri/vincoli LS non si vedono al checkpoint di
  costruzione (builder pre-score); solo cure che agiscono in costruzione (S2) sono
  misurabili li.

### Req 3 - Au/La/St nativo: FIX LOADER FATTO (working tree, sources.py)

**CORREZIONE IMPORTANTE:** l'Explore subagent aveva detto "req 3 gia' fatto, nessun
cambiamento" (le colonne oreAula/oreLab/oreStage ci sono in 13/14/15). **ERA FALSO**: il
subagent non aveva fatto girare i gate. Il **loader** `sources.py:daily_activity_quota()`
che alimenta quelle colonne era STALE pre-TASK-462: faceva raise su duplicato (group, date)
con "cannot represent split-modality days". Il baseline era ROSSO (5 gate su 6 EXIT 1).

**Fix applicato** (sources.py:271-340, mirror byte-per-byte di
`DataGenerator.generateExpectedDailyHours` DataGenerator.java:940-991 post-TASK-462):
- check su duplicato **(group, date, MODALITY)** non (group, date);
- righe di stessa data con modalita' diversa (G14 A+L, 5 date: 2027-03-11/03-18/04-01/
  04-08/04-15) si **aggregano** in un'unica entita' [aula, lab, stage];
- ore<=0 skip e dup-check precedono il skip out-of-horizon; gruppi sconosciuti droppati
  a emit (findGroupByName). Docstring aggiornato.

**Risultato gate dopo il fix:** materie/roundtrip/availability **RED->GREEN** (EXIT 0).
piano-giorni e validazione restano rossi per altra causa (req 6 + baseline drift, NON req 3).

**DoD req 3:** /simplify + /code-review NON ancora fatti (context alto, differdo a suite
verde). Il fix e' piccolo (~30 righe) e mirror esatto del Java (verificato leggendo
DataGenerator.java:940-991).

### Req 6 + baseline drift - CASCATA TASK-462 (bloccanti, da fare dopo req 3)

Il cambio G14 (TASK-462, 120h invece di 990h) ha effetti a cascata che req 3 ha esposto:

1. **test_piano_giorni_roundtrip/negative ROSSI:** "riga off-grid non di stage (inatteso):
   G14 2027-01-16 aula=5 lab=0 stage=0". Il nuovo G14 ha **ore aula di sabato** (2027-01-16
   e' sabato, off-grid, no Timeslot). Il gate assume off-grid = solo stage (modello GAP-2:
   43 righe off-grid tutte stage). G14 rompe l'assunzione con aula off-grid. **Questo e'
   proprio req 6:** le ore fuori griglia (G14 Sat 5h, Q28) devono stare in colonna
   esplicita 'fuori griglia' con motivo, mai come righe ordinarie. Il gate va aggiornato per
   accettare off-grid non-stage DICHIARATI, e il template (12_Incarichi/16_Stage
   oreFuoriGriglia + 99 GAP-2) deve ospitarli.

2. **test_validazione DRIFT BASELINE:** EXPECTED_* in test_validazione.py stali per G14:
   - **V9 MANCA** ora: era "G14: 136 giorni con ore oltre maxOreGiorno 5" (il vecchio G14
     990h superava 5h/giorno). Il nuovo G14 120h non supera piu' 5 -> V9 non fuoca. Rimuovere
     da EXPECTED.
   - **V16 NUOVO:** "G14: 41 giorni scolastici aperti senza riga in 15 (no-session)". Il nuovo
     G14 part-time non copre tutti i giorni -> 41 no-session. Aggiungere a EXPECTED (o
     ratificare, v. esenzione isG14 SSCP:1839).

### Req 1 - Un posto autoritativo per fatto (NOT STARTED)

**Mappa (corretta dall'Explore):** il workbook NON ha un foglio per gruppo; ha 21 fogli
system-named (01-21 + 00/99), e "classe" e' una COLONNA dati. Quindi req 1 = validare che
ogni valore "classe" in 10/11/12/13/14/15/16 sia nel set canonico (groups.csv) + detection
duplicati in 10_Classi + refusi (es. 'G14 2' vs 'G14').
- Generator: `sheet_10_classi()` (generate_workbook.py:287); pubblicare il set canonico.
- Validator: nuovo check (V24-ish) in validazione.py, registra in CONTROLS (lista) + SHEET_CIDS.
- Test: nuovo negative test (mirror test_piano_giorni_negative.py: corrompi + assert fail).

### Req 2 - Totali come formule + cross-check (NOT STARTED)

- Generator: `sheet_12_incarichi()` (generate_workbook.py:325) aggiungere riga "Totale ORE"
  con `=SUM(...)`; `sheet_99_validazione()` (439) aggiungere sezione "CALCOLO TOTALE" con
  formule + ALARM su rapporto calendario/incarichi > soglia (es. 1.2x). Il 9x G14 (990 vs
  115) sarebbe emerso al primo import.
- Validator: nuovo check che le celle totali siano formule (non literal).
- **ATTENZIONE API:** validazione.py oggi legge solo da sources.py (seed), NON dal workbook
  caricato. Req 2/4/6 richiedono che legga ANCHE il workbook .xlsx (openpyxl) per verificare
  formule/tipi/colonne. Cambio API necessario.

### Req 4 - Note separate, dati numerici (NOT STARTED)

- Generator: `_sheet()` helper (generate_workbook.py:60) aggiungere openpyxl DataValidation
  (whole 0-1000) sulle colonne numeriche (ore/slot/capienza/oreAula/...). La colonna "note"
  c'e' gia' in tutti i fogli (ultimo posto).
- Validator: nuovo check che le colonne numeriche contengano solo numeri (non testo come
  'rosy'/'colloqui t14' finiti in St/La di 'G14 2'). Richiede lettura workbook.

### Req 5 - Correzioni golden-first = input (NOT STARTED)

- Generator: `sheet_16_stage()` (generate_workbook.py:382) + `S.STAGE_PATTERNS` (sources.py:470):
  aggiungere riga G04 (TASK-434: 240h, 30 date, 16h off-grid). Cosi' lo stage G04 diventa
  INPUT dichiarato (non curato a mano nei CSV), e la catena estrai->importa torna idempotente.
- Validator: V3 (validazione.py:394) gia' valida lo stage; aggiornare baseline test_validazione
  (rimuovere il finding "G04: nessuna riga 16_Stage").

## Ordine di ripresa consigliato

1. **Req 6 + baseline drift** (sbloccano piano-giorni + validazione -> suite verde). Sono la
   cascata TASK-462 esposta da req 3.
2. **DoD req 3** (/simplify + /code-review sul fix sources.py) ora che si vede l'effetto.
3. Req 1, 2, 4, 5 (ognuno: generator + validator + test negative + DoD + commit).
4. Cambio API validazione.py (legge anche il workbook) per req 2/4/6.
5. Rigenerare la bozza 2026/27 + verifica identita' (come Tappa 1).
6. PRIMA di consegnare a referente.

## DoD Tappa 6 (per requisito, obbligatoria)

/simplify + /code-review high sul diff del requisito + gate EXIT 0 (materie, roundtrip,
availability, piano-giorni roundtrip+negative, validazione) + commit e push. Niente resta
in locale.

## File coinvolti

- `scripts/workbook/sources.py` (req 3 fix FATTO working tree; req 5 STAGE_PATTERNS).
- `scripts/workbook/generate_workbook.py` (req 1/2/4/5/6 generator).
- `scripts/workbook/validazione.py` (req 1/2/4/6 nuovi check + API workbook-read).
- `scripts/workbook/test_validazione.py` (baseline drift V9/V16 + nuovi EXPECTED).
- `scripts/workbook/test_*.py` (nuovi negative test per req 1/2/4/6).

## Run del baseline (venv)

```
cd scripts/workbook
for t in test_materie_byte_identity test_roundtrip_identity test_availability_roundtrip \
         test_piano_giorni_roundtrip test_piano_giorni_negative test_validazione; do
  .venv/bin/python $t.py; echo "$t EXIT $?"
done
```
Stato post-req3-fix: materie/roundtrip/availability GREEN; piano-giorni roundtrip+negative
RED (G14 Sat off-grid aula, req 6); validazione RED (drift V9/V16, baseline).

## NON toccare (extra-Tappa-6, in working tree)

- `src/test/java/.../RoomPhaseSnapshotTest.java`: bridge M-TC +18 righe UNCOMMITTED (blocco
  30h). Difetti strutturali (capacity non pubblicata + gradiente zero fase aule) documentati
  in `task455-mtc-checkpoint-30g-2026-07-30.md`. Fate: revert / completare / lasciare -
  decisione maintainer. Non influenza Tappa 6.
