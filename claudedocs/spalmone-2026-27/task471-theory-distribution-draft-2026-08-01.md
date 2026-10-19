# TASK-471 draft - Distribuzione teoria dal golden: tabella per-(gruppo, materia) (zai, 2026-08-01)

> **UPDATE 2026-08-07 (deliverable #2 fatto):** la policy di questo draft e' ora
> cablata nel foglio workbook `22_TeoriaBlocco` (grain (classe, materia), 196 righe),
> derivata LIVE dal golden da `scripts/workbook/theory_block_policy.py` (`is_theory`
> importato dal modulo analyze autorevole). Il gate `test_theory_block_identity.py`
> prova che la derivazione live == questo CSV draft (set-based) + sanity 196/6800h/
> split 157-31-8. Le tabelle qui sotto restano come EVIDENZA della classificazione;
> la sorgente vivente e' il foglio generato + il modulo. RESTA la conferma scuola
> (referente): le 31 mista + le 8 blocco. Nessuna adozione (M-TR flag OFF).

Deliverable centrale di TASK-471. Per ogni (gruppo, materia teorica), con `isTheoryForRule`
come **fonte unica** (usata via il mirror python autorevole `scripts/analyze/theory_adjacent_residual_dump.is_theory`,
identico al vincolo M-TR e al prototipo TASK-470): ore totali, giorni attivi, giorni-2+,
% giorni-2+, avg ore/giorno attivo, e la **classificazione proposta**. Trappole escluse come
sempre (sabati, docente Fasullo/Fasutto/Stage, subject Stage, closed). Golden =
`src/main/resources/seed/lessons.csv` (10.542 lezioni post-trappole). Read-only sui dati, nessun
codice solver, nessun solve. CSV completo della colonna workbook: `task471-theory-policy-draft.csv`
(grain (gruppo, materia), `origine=sistema` come da convenzione generate_workbook.py).

## Soglie di classificazione (DICHIARATE, arbitrabili)
Segnale = **avg_hours_per_active_day** = theory_hours / active_days (ore medie per giorno in cui la
materia compare per quel gruppo). Proxy pulito: avg~1.0 = 1h/giorno; avg~2.0 = blocchi 2h;
avg~3.0 = blocchi 3h.
- **avg <= 1.15 -> `1h/giorno (stretta)`**: il golden colloca la materia quasi sempre a 1h/giorno.
  Proposta: il run la spreada a 1h/giorno (replica la pratica golden).
- **avg >= 1.85 -> `2h ammessa (blocco)`**: il golden usa blocchi 2h+ con regolarita'. Proposta:
  blocco 2h permesso (NON forzare 1h/giorno su questa materia).
- **1.15 < avg < 1.85 -> `mista/da-chiedere`**: zona grigia (per lo piu' 1h/giorno ma con giorni
  2h significativi, tipicamente Scienze/Matematica/Lingua). Proposta: domanda referente (1h preferito
  con eccezioni, o blocco libero?).

Le soglie sono dichiarate e modificabili. La classificazione e' un **DEFAULT proposto dal golden**,
non adottato: resta la conferma della scuola (canale referente). I borderline teoria (Figurino,
Tecnologia e creativita', Personalizzazione) sono inclusi come teoria per default AC#2; il golden
decide cosa la scuola fa davvero (es. Tecnologia e creativita' = blocco 3h per MODA, Figurino =
92% a 1h/giorno). Le ore-lab (Confezione, Laboratorio Termoidraulico, Lavorazioni meccaniche, ...)
sono escluse da isTheory per costruzione.

## Sintesi
- **196 coppie (gruppo, materia teorica), 6800 ore teoria** (su 10542 lezioni post-trappole = 65%).
- **157 (80%) `1h/giorno (stretta)`**, **31 (16%) `mista/da-chiedere`**, **8 (4%) `2h ammessa`**.
- La grandi maggioranza della teoria e' praticata a 1h/giorno (157/196), coerente col golden 432
  giorni-2+ misurato in TASK-468 AC#1 e col verdetto TASK-470 (il gap verso golden e' input-level).
- Le 8 `2h ammessa`: Tecnologia e creativita' MODA (3h blocks, x3 gruppi), G04 Recuperi,
  G06 Approfondimenti tecnica operativa, G14 Servizio stilistico (teoria nel perimetro variant
  b), G12/G13 Preparazione all'alternanza (1 giorno ciascuno, rumore di bordo).
- Le 31 `mista`: soprattutto Scienze/Matematica/Lingua/Educazione Civica con 15-48% giorni 2+.

## Per referente (domande che nascono dalla tabella)
- Le 31 `mista`: sono materie dove il golden fa 1h/giorno di default MA con ~15-48% di giorni 2h
  (probabile esigenza di fill). Confermi 1h preferito con eccezioni di fill, o blocco libero?
- Le 8 `2h ammessa`: confermi che sono blocchi intenzionali (non fill-forzato)? In particolare
  Tecnologia e creativita' (MODA 3h) e G04 Recuperi (avg 3.85).
- Cross-rif T8 (Figurino/Personalizzazione borderline): il golden tratta Figurino a 1h/giorno nel
  92% dei casi -> confermato come teoria; Personalizzazione e' 1h/giorno stretta per 1/2/G11.

### '2h ammessa (blocco)' - 8 coppie (golden usa blocchi 2h+ con regolarita')
| group | subject | theory_hours | active_days | days_2plus | pct_2plus | avg_h/day | class |
|---|---|---:|---:|---:|---:|---:|---|
| G01 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G04 | Recuperi ed Approfondimenti | 50 | 13 | 13 | 100.0 | 3.85 | 2h ammessa (blocco) |
| G05 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G06 | Approfondimenti tecnica operativa | 30 | 15 | 15 | 100.0 | 2.00 | 2h ammessa (blocco) |
| G09 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G12 | Preparazione all'alternanza | 5 | 1 | 1 | 100.0 | 5.00 | 2h ammessa (blocco) |
| G13 | Preparazione all'alternanza | 5 | 1 | 1 | 100.0 | 5.00 | 2h ammessa (blocco) |
| G14 | Servizio stilistico | 30 | 6 | 6 | 100.0 | 5.00 | 2h ammessa (blocco) |

### 'mista/da-chiedere' - 31 coppie (zona grigia per referente)
| group | subject | theory_hours | active_days | days_2plus | pct_2plus | avg_h/day | class |
|---|---|---:|---:|---:|---:|---:|---|
| G01 | Matematica | 80 | 59 | 21 | 35.6 | 1.36 | mista/da-chiedere |
| G01 | Recuperi ed Approfondimenti | 50 | 33 | 8 | 24.2 | 1.52 | mista/da-chiedere |
| G01 | Lingua Italiana | 90 | 73 | 16 | 21.9 | 1.23 | mista/da-chiedere |
| G02 | Educazione Civica | 40 | 27 | 13 | 48.1 | 1.48 | mista/da-chiedere |
| G02 | Scienze | 62 | 46 | 16 | 34.8 | 1.35 | mista/da-chiedere |
| G02 | Approfondimenti tecnica operativa | 36 | 26 | 4 | 15.4 | 1.38 | mista/da-chiedere |
| G03 | Normativa | 45 | 28 | 12 | 42.9 | 1.61 | mista/da-chiedere |
| G03 | Scienze | 50 | 40 | 10 | 25.0 | 1.25 | mista/da-chiedere |
| G04 | Scienze | 60 | 42 | 15 | 35.7 | 1.43 | mista/da-chiedere |
| G04 | Matematica | 70 | 59 | 11 | 18.6 | 1.19 | mista/da-chiedere |
| G05 | Arredi Tessili | 13 | 10 | 3 | 30.0 | 1.30 | mista/da-chiedere |
| G05 | Recuperi ed Approfondimenti | 50 | 34 | 7 | 20.6 | 1.47 | mista/da-chiedere |
| G05 | Figurino | 50 | 42 | 8 | 19.0 | 1.19 | mista/da-chiedere |
| G06 | Scienze | 60 | 46 | 14 | 30.4 | 1.30 | mista/da-chiedere |
| G06 | Igiene e sicurezza | 30 | 25 | 5 | 20.0 | 1.20 | mista/da-chiedere |
| G07 | Preparazione all'alternanza | 5 | 3 | 2 | 66.7 | 1.67 | mista/da-chiedere |
| G08 | RA | 41 | 27 | 8 | 29.6 | 1.52 | mista/da-chiedere |
| G08 | Lingua Inglese | 40 | 32 | 8 | 25.0 | 1.25 | mista/da-chiedere |
| G09 | Matematica | 50 | 40 | 9 | 22.5 | 1.25 | mista/da-chiedere |
| G09 | Merceologia | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G09 | Tecnologia tessile | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G09 | Recuperi ed Approfondimenti | 39 | 33 | 5 | 15.2 | 1.18 | mista/da-chiedere |
| G10 | Scienze | 40 | 33 | 6 | 18.2 | 1.21 | mista/da-chiedere |
| G10 | Comportamento sociale | 20 | 17 | 3 | 17.6 | 1.18 | mista/da-chiedere |
| G10 | Cosmetologia | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G12 | Scienze | 60 | 52 | 8 | 15.4 | 1.15 | mista/da-chiedere |
| G13 | Competenze Digitali | 55 | 34 | 21 | 61.8 | 1.62 | mista/da-chiedere |
| G13 | Economia e Diritto del lavoro | 60 | 46 | 14 | 30.4 | 1.30 | mista/da-chiedere |
| G13 | Scienze | 45 | 37 | 8 | 21.6 | 1.22 | mista/da-chiedere |
| G14 | Comportamento Sociale | 20 | 16 | 4 | 25.0 | 1.25 | mista/da-chiedere |
| G14 | Competenze Digitali | 20 | 17 | 3 | 17.6 | 1.18 | mista/da-chiedere |

### Tabella completa - 196 coppie (gruppo, materia teorica)
| group | subject | theory_hours | active_days | days_2plus | pct_2plus | avg_h/day | class |
|---|---|---:|---:|---:|---:|---:|---|
| G01 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G01 | Matematica | 80 | 59 | 21 | 35.6 | 1.36 | mista/da-chiedere |
| G01 | Recuperi ed Approfondimenti | 50 | 33 | 8 | 24.2 | 1.52 | mista/da-chiedere |
| G01 | Lingua Italiana | 90 | 73 | 16 | 21.9 | 1.23 | mista/da-chiedere |
| G01 | Figurino | 45 | 40 | 5 | 12.5 | 1.12 | 1h/giorno (stretta) |
| G01 | Tecnologia tessile | 31 | 30 | 1 | 3.3 | 1.03 | 1h/giorno (stretta) |
| G01 | Lingua Inglese | 70 | 70 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Scienze | 50 | 50 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Merceologia | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Economia e Diritto del lavoro | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Storia della moda | 25 | 25 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Storia | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Comportamento Sociale | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Sviluppo Sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Arredi Tessili | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G01 | Sicurezza | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Educazione Civica | 40 | 27 | 13 | 48.1 | 1.48 | mista/da-chiedere |
| G02 | Scienze | 62 | 46 | 16 | 34.8 | 1.35 | mista/da-chiedere |
| G02 | Approfondimenti tecnica operativa | 36 | 26 | 4 | 15.4 | 1.38 | mista/da-chiedere |
| G02 | Consulenza d'immagine | 35 | 32 | 3 | 9.4 | 1.09 | 1h/giorno (stretta) |
| G02 | Lingua Inglese | 70 | 65 | 5 | 7.7 | 1.08 | 1h/giorno (stretta) |
| G02 | Matematica | 70 | 68 | 2 | 2.9 | 1.03 | 1h/giorno (stretta) |
| G02 | Cosmetologia | 50 | 49 | 1 | 2.0 | 1.02 | 1h/giorno (stretta) |
| G02 | Storia | 50 | 49 | 1 | 2.0 | 1.02 | 1h/giorno (stretta) |
| G02 | Lingua italiana | 80 | 79 | 1 | 1.3 | 1.01 | 1h/giorno (stretta) |
| G02 | Relazione con il cliente | 55 | 55 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Analisi del capello e del cuoio capelluto | 50 | 50 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Gestione aziendale | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Igiene e sicurezza | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Comportamento sociale | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Sviluppo sostenibile | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G02 | Sicurezza | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Normativa | 45 | 28 | 12 | 42.9 | 1.61 | mista/da-chiedere |
| G03 | Scienze | 50 | 40 | 10 | 25.0 | 1.25 | mista/da-chiedere |
| G03 | Lingua italiana | 80 | 71 | 9 | 12.7 | 1.13 | 1h/giorno (stretta) |
| G03 | Matematica | 80 | 71 | 9 | 12.7 | 1.13 | 1h/giorno (stretta) |
| G03 | Lingua Inglese | 60 | 59 | 1 | 1.7 | 1.02 | 1h/giorno (stretta) |
| G03 | Personalizzazione | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Storia | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Gestione aziendale | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Economia e Diritto del lavoro | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Sicurezza | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G03 | Sviluppo sostenibile | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G04 | Recuperi ed Approfondimenti | 50 | 13 | 13 | 100.0 | 3.85 | 2h ammessa (blocco) |
| G04 | Scienze | 60 | 42 | 15 | 35.7 | 1.43 | mista/da-chiedere |
| G04 | Matematica | 70 | 59 | 11 | 18.6 | 1.19 | mista/da-chiedere |
| G04 | Storia | 20 | 18 | 2 | 11.1 | 1.11 | 1h/giorno (stretta) |
| G04 | Lingua Italiana | 90 | 82 | 8 | 9.8 | 1.10 | 1h/giorno (stretta) |
| G04 | Economia e Diritto del lavoro | 20 | 19 | 1 | 5.3 | 1.05 | 1h/giorno (stretta) |
| G04 | Lingua Inglese | 60 | 59 | 1 | 1.7 | 1.02 | 1h/giorno (stretta) |
| G04 | Educazione civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G04 | Competenze di cittadinanza | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G04 | Sviluppo sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G04 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G04 | Sicurezza | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G05 | Arredi Tessili | 13 | 10 | 3 | 30.0 | 1.30 | mista/da-chiedere |
| G05 | Recuperi ed Approfondimenti | 50 | 34 | 7 | 20.6 | 1.47 | mista/da-chiedere |
| G05 | Figurino | 50 | 42 | 8 | 19.0 | 1.19 | mista/da-chiedere |
| G05 | Storia della moda | 25 | 24 | 1 | 4.2 | 1.04 | 1h/giorno (stretta) |
| G05 | Tecnologia tessile | 30 | 29 | 1 | 3.4 | 1.03 | 1h/giorno (stretta) |
| G05 | Scienze | 50 | 49 | 1 | 2.0 | 1.02 | 1h/giorno (stretta) |
| G05 | Lingua Inglese | 60 | 59 | 1 | 1.7 | 1.02 | 1h/giorno (stretta) |
| G05 | Lingua Italiana | 80 | 80 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Matematica | 80 | 80 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Comportamento Sociale | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Economia e Diritto del lavoro | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Merceologia | 22 | 22 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Storia | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G05 | Sviluppo Sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Approfondimenti tecnica operativa | 30 | 15 | 15 | 100.0 | 2.00 | 2h ammessa (blocco) |
| G06 | Scienze | 60 | 46 | 14 | 30.4 | 1.30 | mista/da-chiedere |
| G06 | Igiene e sicurezza | 30 | 25 | 5 | 20.0 | 1.20 | mista/da-chiedere |
| G06 | Cosmetologia | 50 | 47 | 3 | 6.4 | 1.06 | 1h/giorno (stretta) |
| G06 | Consulenza d'immagine | 30 | 29 | 1 | 3.4 | 1.03 | 1h/giorno (stretta) |
| G06 | Storia | 50 | 49 | 1 | 2.0 | 1.02 | 1h/giorno (stretta) |
| G06 | Lingua Inglese | 60 | 59 | 1 | 1.7 | 1.02 | 1h/giorno (stretta) |
| G06 | Lingua italiana | 80 | 79 | 1 | 1.3 | 1.01 | 1h/giorno (stretta) |
| G06 | Matematica | 70 | 70 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Relazione con il cliente | 55 | 55 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Analisi del capello e del cuoio capelluto | 50 | 50 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Educazione Civica | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Gestione aziendale | 35 | 35 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Comportamento sociale | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G06 | Sviluppo sostenibile | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Preparazione all'alternanza | 5 | 3 | 2 | 66.7 | 1.67 | mista/da-chiedere |
| G07 | Matematica | 80 | 75 | 5 | 6.7 | 1.07 | 1h/giorno (stretta) |
| G07 | Scienze | 50 | 47 | 3 | 6.4 | 1.06 | 1h/giorno (stretta) |
| G07 | Educazione Civica | 30 | 28 | 1 | 3.6 | 1.07 | 1h/giorno (stretta) |
| G07 | Lingua italiana | 80 | 80 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Lingua Inglese | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Personalizzazione | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Storia | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Economia e Diritto del lavoro | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Normativa | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Gestione aziendale | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Sicurezza | 12 | 12 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G07 | Sviluppo sostenibile | 8 | 8 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | RA | 41 | 27 | 8 | 29.6 | 1.52 | mista/da-chiedere |
| G08 | Lingua Inglese | 40 | 32 | 8 | 25.0 | 1.25 | mista/da-chiedere |
| G08 | Lingua Italiana | 60 | 53 | 7 | 13.2 | 1.13 | 1h/giorno (stretta) |
| G08 | Scienze | 30 | 28 | 2 | 7.1 | 1.07 | 1h/giorno (stretta) |
| G08 | Matematica | 50 | 50 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Educazione civica | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Storia | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Sicurezza | 12 | 12 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Economia e Diritto del lavoro | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Sviluppo sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G08 | Competenze di cittadinanza | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Tecnologia e creatività | 24 | 8 | 8 | 100.0 | 3.00 | 2h ammessa (blocco) |
| G09 | Matematica | 50 | 40 | 9 | 22.5 | 1.25 | mista/da-chiedere |
| G09 | Merceologia | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G09 | Tecnologia tessile | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G09 | Recuperi ed Approfondimenti | 39 | 33 | 5 | 15.2 | 1.18 | mista/da-chiedere |
| G09 | Arredi Tessili | 10 | 9 | 1 | 11.1 | 1.11 | 1h/giorno (stretta) |
| G09 | Storia della moda | 20 | 19 | 1 | 5.3 | 1.05 | 1h/giorno (stretta) |
| G09 | Figurino | 35 | 34 | 1 | 2.9 | 1.03 | 1h/giorno (stretta) |
| G09 | Lingua Italiana | 56 | 56 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Lingua Inglese | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Scienze | 35 | 35 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Comportamento Sociale | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Economia e Diritto del lavoro | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Sicurezza | 12 | 12 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Sviluppo Sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G09 | Storia | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Scienze | 40 | 33 | 6 | 18.2 | 1.21 | mista/da-chiedere |
| G10 | Comportamento sociale | 20 | 17 | 3 | 17.6 | 1.18 | mista/da-chiedere |
| G10 | Cosmetologia | 15 | 13 | 2 | 15.4 | 1.15 | mista/da-chiedere |
| G10 | Lingua italiana | 50 | 46 | 4 | 8.7 | 1.09 | 1h/giorno (stretta) |
| G10 | Relazione con il cliente | 40 | 38 | 2 | 5.3 | 1.05 | 1h/giorno (stretta) |
| G10 | Storia | 30 | 29 | 1 | 3.4 | 1.03 | 1h/giorno (stretta) |
| G10 | Gestione aziendale | 35 | 34 | 1 | 2.9 | 1.03 | 1h/giorno (stretta) |
| G10 | Analisi del capello e del cuoio capelluto | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Lingua Inglese | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Matematica | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Consulenza d'immagine | 23 | 23 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Sicurezza | 12 | 12 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Orientamento | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Igiene e sicurezza | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G10 | Sviluppo sostenibile | 5 | 5 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Economia e Diritto del lavoro | 20 | 18 | 2 | 11.1 | 1.11 | 1h/giorno (stretta) |
| G11 | Scienze | 40 | 37 | 3 | 8.1 | 1.08 | 1h/giorno (stretta) |
| G11 | Matematica | 60 | 58 | 2 | 3.4 | 1.03 | 1h/giorno (stretta) |
| G11 | Lingua italiana | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Personalizzazione | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Lingua Inglese | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Educazione Civica | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Gestione aziendale | 26 | 26 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Storia | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Normativa | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Sviluppo sostenibile | 10 | 10 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G11 | Preparazione all'alternanza | 5 | 5 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Preparazione all'alternanza | 5 | 1 | 1 | 100.0 | 5.00 | 2h ammessa (blocco) |
| G12 | Scienze | 60 | 52 | 8 | 15.4 | 1.15 | mista/da-chiedere |
| G12 | Matematica | 30 | 27 | 3 | 11.1 | 1.11 | 1h/giorno (stretta) |
| G12 | Servizio stilistico | 30 | 28 | 2 | 7.1 | 1.07 | 1h/giorno (stretta) |
| G12 | Lingua italiana | 55 | 52 | 3 | 5.8 | 1.06 | 1h/giorno (stretta) |
| G12 | Lingua Inglese | 40 | 38 | 2 | 5.3 | 1.05 | 1h/giorno (stretta) |
| G12 | Gestione aziendale | 54 | 54 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Relazione con il cliente | 45 | 45 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Comportamento Sociale | 45 | 45 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Analisi del capello e del cuoio capelluto | 40 | 40 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Storia | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Economia e Diritto del lavoro | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Consulenza d'immagine | 30 | 30 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Igiene e sicurezza | 25 | 25 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G12 | Orientamento | 25 | 25 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G13 | Preparazione all'alternanza | 5 | 1 | 1 | 100.0 | 5.00 | 2h ammessa (blocco) |
| G13 | Competenze Digitali | 55 | 34 | 21 | 61.8 | 1.62 | mista/da-chiedere |
| G13 | Economia e Diritto del lavoro | 60 | 46 | 14 | 30.4 | 1.30 | mista/da-chiedere |
| G13 | Scienze | 45 | 37 | 8 | 21.6 | 1.22 | mista/da-chiedere |
| G13 | Comunicazione | 70 | 66 | 4 | 6.1 | 1.06 | 1h/giorno (stretta) |
| G13 | Matematica | 30 | 29 | 1 | 3.4 | 1.03 | 1h/giorno (stretta) |
| G13 | Personalizzazione | 60 | 60 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G13 | Tecnologia | 42 | 42 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G13 | Orientamento | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G13 | Lingua Inglese | 20 | 20 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G13 | Sicurezza | 16 | 16 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
| G14 | Servizio stilistico | 30 | 6 | 6 | 100.0 | 5.00 | 2h ammessa (blocco) |
| G14 | Comportamento Sociale | 20 | 16 | 4 | 25.0 | 1.25 | mista/da-chiedere |
| G14 | Competenze Digitali | 20 | 17 | 3 | 17.6 | 1.18 | mista/da-chiedere |
| G14 | Orientamento | 15 | 15 | 0 | 0.0 | 1.00 | 1h/giorno (stretta) |
