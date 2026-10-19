# TASK-466(a): diff meccanico incarichi-vs-golden (zai, 2026-07-31)

Read-only. Zero code touching, zero DataGenerator. Diff meccanico per (docente, materia, gruppo)
fra `src/main/resources/seed/incarichi.csv` e il golden `src/main/resources/seed/lessons.csv`,
con trappole note escluse e dichiarate. Verificato due volte in modo indipendente
(skill adversarial-verification).

## VERDETTO (3 righe, dai raw)
- **Diff triplo (docente, materia, gruppo) = 0 divergenze reali.** Lo script esistente
  `scripts/analyze/derive-golden-incarichi-corrections.py` riporta 0 triple mismatched, e una
  ricalcolazione indipendente (full outer join su chiavi normalizzate, keeping RA, senza riusare
  lo script) conferma: i 17 raw-mismatch sono tutti le due esclusioni documentate (8 triple
  `Stage/Stage/gruppo` = 2009h stage on-grid; 9 triple `docente/RA/G08` = 41h pool RA). Zero
  spelling drift.
- **Totale di gruppo = 0 delta per 13/14 gruppi; G08 e' match esatto a 626h** una volta escluso
  il pool RA da entrambi i lati. L'unico delta numerico in tutto il dataset e' la size del pool RA
  di G08: 40h (incarichi, riga pseudo-docente `RA`) vs 41h (golden, distribuita come materia `RA`
  su 9 docenti reali). 1h, dentro un artefatto contabile noto (design doc stage-esame-ra-modeling).
- **Tutte le divergenze precedentemente documentate sono STALE/RISOLTE.** T01/T28 244-vs-192
  -> oggi incarichi 231 = golden 231; G09 666-vs-640 -> oggi incarichi 666 = golden 666 (640 era
  spalmone.csv); G04 835-vs-750 -> risolta da TASK-434/457 (835 = capacita' di griglia 990-155,
  750 = monte-ore da erogare, incarichi = golden = 750). incarichi.csv e' gia' normalizzato al
  golden (lo riconcilio' TASK-371).

## Per TASK-466(b): non c'e' nulla da normalizzare sul lato incarichi
La parte (b) "normalizzare il seed ai valori golden dove divergono" e' di fatto un no-op per
`incarichi.csv`: il file e' gia' allineato al golden a livello di tripla e di totale di gruppo.
L'unica posta numerica residua (pool RA 40 vs 41, 1h su G08) e' una scelta di modellazione del
pool (micro-corsi), non una divergenza di contenuto. La tabella delle discrepanze in
`.claude/rules/datagenerator-protection.md` (T28 244/192, G09 666/640) e' stale: andrebbe
ripulita perche' i numeri correnti coincidono. Rimangono fuori scope del diff triplo (e tracciati
altrove) la cadenza Q9 MODA (Confezione) e gli arrotondamenti di foglio: sono questioni di
scheduling/cadenza, non triple (docente, gruppo, materia).

## Tabella divergenze (autorevole, riproducibile)

### Diff triplo (docente, materia, gruppo) - REALE
```
$ python3 scripts/analyze/derive-golden-incarichi-corrections.py
... 0 mismatched triples (real teachers only, pseudo rows excluded)
```
Ricalcolo indipendente (full outer join, chiavi normalizzate, keeping RA): 0 divergenze reali.
Spelling drift docente/materia: 0.

### Esclusioni dichiarate (tutte documentate, tutte meccaniche)
| categoria | cosa | volume | dove esclusa |
|---|---|---|---|
| Righe `closed=True` (trappole) | Fasullo/Fasullo sabato 19/09/2026 | 112 | `iter_open_lessons_rows` |
| | Stage/Stage off-grid | 311 | `iter_open_lessons_rows` |
| | T14/Orientamento sabato | 5 | `iter_open_lessons_rows` |
| Stage on-grid (pseudo-docente) | Stage/Stage per 8 gruppi | 2009 | `PSEUDO_TEACHERS` |
| Pool RA (materia `RA`, G08) | 9 docenti reali | 41 | `RAW_POOL_SUBJECTS` |

Totale righe chiuse nel golden: 428 (311 Stage + 112 Fasullo + 5 Orientamento). Conferma diretta
delle "trappole del golden" di CLAUDE.md: sono tutte presenti nel seed ma marcate `closed=True`,
quindi escluse dal diff. Nota: `parse_golden_csv` non filtra il weekday, ma in questo golden ogni
riga di sabato e' gia' `closed`, quindi l'esclusione e' efficace (il caveat CLAUDE.md vale per un
sabato che fosse marcato open, che qui non si verifica).

### Totale di gruppo (docente reale, escl. Stage/Esame): golden vs incarichi
| gruppo | golden | incarichi | delta | nota |
|---|---|---|---|---|
| G01 | 990 | 990 | 0 | |
| G02 | 990 | 990 | 0 | |
| G03 | 990 | 990 | 0 | |
| G04 | 750 | 750 | 0 | 835 e' capacita' griglia, 750 e' erogazione |
| G05 | 990 | 990 | 0 | |
| G06 | 990 | 990 | 0 | |
| G07 | 695 | 695 | 0 | |
| G08 | 667 | 626 | -41 | artefatto pool RA (vedi sotto), match esatto a 626 escluso il pool |
| G09 | 666 | 666 | 0 | |
| G10 | 670 | 670 | 0 | |
| G11 | 671 | 671 | 0 | |
| G12 | 675 | 675 | 0 | |
| G13 | 683 | 683 | 0 | |
| G14 | 115 | 115 | 0 | |

### Scomposizione del -41 di G08 (unico delta, artefatto contabile)
- incarichi: `RA,Recuperi ed Approfondimenti,G08,40.0` (riga pseudo-docente, esclusa dal totale
  real-teacher). Totale real-teacher incarichi G08 = 626.
- golden: le stesse ore sono registrate come materia `RA` sotto 9 docenti reali
  (T23 19, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2,
  T11 1, T03 1 = 41). Totale real-teacher golden G08 = 626 (non-RA) + 41 (RA) = 667.
- Escludendo il pool RA da entrambi i lati: **626 = 626, match esatto.** Delta reale = size del
  pool, 40 vs 41, 1h. Non e' una divergenza di contenuto: e' la stessa quota modellata in due forme
  (design doc `stage-esame-ra-modeling-2026-07-16.md`, sezione C micro-corsi).

## Stato delle divergenze documentate (tutte verificate sui raw correnti)
| divergenza documentata | valore doc | valore corrente (raw) | stato |
|---|---|---|---|
| T01/T28 (datagenerator-protection) | 244 vs 192 | incarichi 231 = golden 231 | STALE: risolta |
| G09 (datagenerator-protection) | 666 vs 640 | incarichi 666 = golden 666 | STALE: risolta (640 era spalmone.csv) |
| G04 slack 85h | 835 vs 750 | incarichi 750 = golden 750 | RISOLSA (TASK-434/457): 835 capacita', 750 erogazione |
| Fasullo 112h sabato | trappola | 112 closed | esclusa (closed) |
| Orientamento 5h sabato | trappola | 5 closed | esclusa (closed) |
| Q9 MODA cadenza Confezione | 180 vs 169 | n/a | fuori scope diff triplo (cadenza) |

T01 incarichi (4 triple, 231h totali): G02 60, G06 60, G10 65 (Servizio tecnico e
stilistico), G12 46. Speculare nel golden.

## Come riprodurre
```
python3 scripts/analyze/derive-golden-incarichi-corrections.py        # diff triplo -> 0
python3 scripts/analyze/derive-golden-incarichi-corrections.py --selftest  # autocheck ADD/CORRECT/DROP
```
Il totale di gruppo e' ricalcolato dallo snippet inline documentato sopra (seed `lessons.csv` open
rows, docenti reali, escl. Stage/RA-pseudo). Le copie in `claudedocs/spalmone-2026-27/data/` sono
verificate `diff -q` IDENTICAL a `src/main/resources/seed/`.

## Limiti
- diff triplo e totale di gruppo solo. La cadenza (Q9 MODA Confezione) e gli arrotondamenti di
  foglio sono fuori scope: sono questioni di scheduling, non triple di incarico.
- spalmone.csv (`scripts/data/spalmone.csv`) e' il grid grezzo originale della scuola (formato wide,
  materie abbreviate servtecT28/progtermo/perspic, date in italiano). Diffarlo vs golden
  richiederebbe di decodificare tutti gli abbreviatori = ri-derivare il golden. Non e' un input
  narrow e non e' stato usato come sorgente di divergenze: i numeri correnti di incarichi e golden
  coincidono gia'.
- n=1 sul seed (snapshot unico); la meccanica e' deterministica perche' puro join su CSV.
