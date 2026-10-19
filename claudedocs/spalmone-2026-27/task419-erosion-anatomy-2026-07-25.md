# TASK-419: Anatomia dell'erosione del template in fase 2 (ls-compactness)

Analisi pura Python (nessuna build/test Java) sui dump per confine di fase di
val408 (run1/run2), `claudedocs/spalmone-2026-27/task408-results/val408-run{1,2}/phase-transitions/`.
Nessun tocco al backlog.

## Input effettivamente usati (nomi reali dei file, diversi dal pattern del prompt)

Le directory non contengono file letteralmente chiamati `phase-2-*.txt`/`phase-3-*.txt`:
i nomi reali sono `phase-<N>-<N>-<nome-fase>.txt`. Confermato per contenuto (le
percentuali di ricorrenza unpinned nei file `-resemblance.txt` companion
coincidono esattamente con quelle citate nel prompt) che il mapping corretto è:

| Voce del prompt | File reale | Ricorrenza unpinned (da `*-resemblance.txt`) |
|---|---|---|
| ingresso fase 2 (fine costruzione) | `phase-2-2-ls-compactness.txt` | run1/run2 identici: 74.3% (7083/9539) |
| ingresso fase 3 (fine fase 2) | `phase-3-3-ls-advanced-ruinrecreate.txt` | run1: 52.8% (5075/9607), run2: 53.7% (5162/9608) |

Formato riga: `group|date|hour|subject|teacher|pinned` (prima riga `SET`, header
letterale, scartata). `hour` e' 8-15, `slot = hour-8`. `pinned` in {`true`,`false`},
popolazione pinned identica in numero fra ingresso-ph2 e ingresso-ph3 in entrambi
i run (2651/2651 run1, 2651/2651 run2 secondo i file resemblance): conferma che
nessuna lezione cambia stato pinned/unpinned durante la fase 2, quindi il
confronto "solo unpinned" e' ben posto.

## Metodo dichiarato

**Cella** = `(group, subject, teacher, weekday, slot)` con `weekday` derivato da
`date` (0=lunedi') e `slot = hour-8`. Solo lezioni **unpinned** in ciascun
istante (il dump non porta un ID di lezione stabile, quindi non c'e' modo di
tracciare l'identita' della singola istanza fra i due istanti: e' il limite
principale di questa analisi, dichiarato esplicitamente in ogni sezione dove
conta).

### Q1/Q2: erosione a livello di CELLA (multiset aggregato per corso)

Per ogni corso `(group, subject, teacher)`:
- `c2` = Counter delle celle `(weekday, slot)` usate in ingresso-ph2
- `c3` = Counter delle stesse celle in ingresso-ph3
- `lost_cells = somma di max(0, c2[cell] - c3[cell])` su tutte le celle
- `kept_cells = somma di min(c2[cell], c3[cell])`
- `conservation% = kept_cells / totale_c2 * 100`

Questa e' una misura **NETTA per cella**: se una lezione lascia la cella X e
un'altra (per lo stesso corso) arriva sulla stessa cella X, il netto e' zero,
la metrica misura quindi l'erosione **strutturale** (il corso perde davvero
un tipo di collocazione), non il turnover grezzo delle singole istanze. Non
richiede nessuna ipotesi sull'identita' della lezione: e' per costruzione un
limite superiore alla stabilita' reale, robusto e non manipolabile dal metodo
di matching.

### Q3: dove vanno le ore perse (matching a livello di ISTANZA, per data reale)

Le tre categorie richieste dal prompt ("stesso giorno slot diverso / altro
weekday stesso slot / altro giorno del tutto") richiedono la **data reale**,
che le celle (weekday,slot) non hanno (una cella si ripete su ~20-30
settimane/anno). Per questo Q3 usa un secondo matching, piu' fine e piu'
soggetto a euristica, dichiarato per intero:

Per ogni corso, sui multiset di istanze `(date,hour)`:
1. **match esatto** `(date,hour)` ph2 intersecato ph3 (intersezione di
   multiset) -> `retained`, rimosso da entrambi i pool.
2. Sulle **partenze** (ph2 residue, ordinate per data) e **arrivi** (ph3
   residui) rimasti, matching goloso deterministico con priorita':
   - **A** stessa data, ora diversa -> `same_day_diff_slot`
   - **B** data diversa, stesso weekday e stesso slot -> `same_cell_other_week`
     (stessa cella template, solo un'altra occorrenza settimanale: **non e'
     una deviazione**, cade fuori dal computo "ore perse")
   - **C** data diversa, stessa ora, weekday diverso -> `other_weekday_same_slot`
   - **D** tutto il resto (data e ora diverse) -> `other_day_entirely`
   In caso di piu' candidati alla stessa priorita' si sceglie il piu'
   vicino (minor differenza assoluta di ore per A, minor differenza assoluta
   di giorni per B/C/D).
3. Partenze senza arrivo residuo -> `removed_no_destination` (probabile
   lezione finita unassigned). Arrivi senza partenza residua ->
   `new_from_unassigned` (probabile lezione prima unassigned, ora piazzata).

Per ogni spostamento nelle categorie A/C/D (quelle che cambiano davvero
cella) si verifica se la cella di arrivo appartiene comunque a
`template_cells(corso)` = insieme delle celle distinte usate dal corso in
ingresso-ph2 (**proxy** del vero template pre-solve di `WeeklyTemplatePlanner`,
che questi dump non espongono; il proxy puo' sovrastimare leggermente il
template se la coda round-robin della costruzione ha aggiunto celle fuori
template): se si' -> `moved_onto_other_template_cell` (M32 **non** penalizza,
mossa legittima); se no -> `moved_off_template` (M32 penalizza).

**Limite esplicito di Q3**: essendo un matching euristico senza vera identita'
di lezione, la classificazione descrive il **carattere del turnover di
popolazione** per corso, non la traiettoria certificata della singola
lezione. E' una lettura qualitativa complementare a Q1/Q2 (che restano la
misura quantitativa robusta), non un secondo modo di misurare la stessa cosa.

### Q4: run1 vs run2

Confronto diretto dei `lost_cells` per corso e della `conservation%` per
gruppo fra run1 e run2 (stesso codice, seed diverso, vedi correlazione sotto).

### Script

```python
# /tmp/task419/erosion_anatomy.py -- eseguito con: python3 erosion_anatomy.py
# (path assoluti REPO-relativi verso claudedocs/spalmone-2026-27/...)
```
Lo script completo (circa 220 righe) implementa esattamente l'algoritmo sopra
descritto: `load_dump()` parsa i 4 file, `cell_level_analysis()` fa Q1/Q2,
`instance_level_matching()` fa Q3, `group_aggregate()` aggrega per gruppo/
docente. Output raw in `/tmp/task419/results.json` (260 corsi per 2 run).
Struttura riproducibile (pseudocodice fedele all'implementazione reale):

```python
import csv, datetime, json
from collections import Counter, defaultdict

def load_dump(path):
    records = []
    for line in open(path).readlines()[1:]:   # salta header "SET"
        line = line.rstrip("\n")
        if not line: continue
        g, d, h, subj, t, pin = line.split("|")
        wd = datetime.date.fromisoformat(d).weekday()
        records.append(dict(group=g, date=d, hour=int(h), subject=subj,
                             teacher=t, pinned=(pin == "true"),
                             weekday=wd, slot=int(h) - 8))
    return records

def by_course(records):
    d = defaultdict(list)
    for r in records:
        if r["pinned"]: continue
        d[(r["group"], r["subject"], r["teacher"])].append(r)
    return d

# Q1/Q2: multiset di celle (weekday,slot) per corso, ph2 vs ph3
c2 = Counter((r["weekday"], r["slot"]) for r in ph2_course)
c3 = Counter((r["weekday"], r["slot"]) for r in ph3_course)
lost = sum(max(0, c2[c] - c3.get(c, 0)) for c in set(c2) | set(c3))
kept = sum(min(c2[c], c3.get(c, 0)) for c in set(c2) | set(c3))

# Q3: matching a priorita' su (date,hour), vedi testo per la logica A-D
```

Comandi di verifica rapida usati per validare l'input (nessun mvn/test.sh):
```bash
wc -l claudedocs/spalmone-2026-27/task408-results/val408-run1/phase-transitions/phase-2-2-ls-compactness.txt
cat claudedocs/spalmone-2026-27/task408-results/val408-run1/phase-transitions/phase-2-2-ls-compactness-resemblance.txt
```

---

## Sintesi numerica di controllo

| | run1 | run2 |
|---|---|---|
| Corsi unpinned analizzati | 260 | 260 |
| Celle ph2 totali (unpinned) | 9539 | 9539 |
| Celle perse (netto, Q1) | 2053 | 2083 |
| Conservazione netta complessiva | **78.5%** | **78.2%** |

Nota: questo 78.5/78.2% (conservazione NETTA per cella) e' un numero diverso
dal 74.3% -> 52.8/53.7% di `ricorrenza UNPINNED` nei file `*-resemblance.txt`:
quella metrica misura tutt'altro (quante lezioni ripetono la cella della
settimana precedente, il segnale S13), non "quante celle del corso sono
ancora occupate rispetto a ingresso-ph2". Le due metriche non sono
comparabili linea per linea; qui si riporta solo la propria.

---

## 1. Top-20 corsi per celle perse (i piu' smontati dalla LS)

### run1

| # | Gruppo | Materia | Docente | Celle perse | Celle ph2 | Conserv.% | Ore corso |
|---|---|---|---|---:|---:|---:|---:|
| 1 | G04 | Lingua Italiana | T14 | 40 | 90 | 55.6% | 90 |
| 2 | G03 | Lingua Italiana | T04 | 32 | 80 | 60.0% | 80 |
| 3 | G04 | Matematica | T05 | 31 | 70 | 55.7% | 70 |
| 4 | G03 | Lingua Inglese | T21 | 30 | 60 | 50.0% | 60 |
| 4 | G07 | Disegno Edile ed Impiantistico | T12 | 30 | 60 | 50.0% | 60 |
| 6 | G10 | Lingua Italiana | T04 | 28 | 50 | 44.0% | 50 |
| 6 | G11 | Personalizzazione | T11 | 28 | 60 | 53.3% | 60 |
| 8 | G04 | Scienze | T25 | 27 | 60 | 55.0% | 60 |
| 8 | G13 | Lavorazioni meccaniche | T10 | 27 | 110 | 75.5% | 110 |
| 10 | G08 | Disegno | T10 | 25 | 50 | 50.0% | 50 |
| 10 | G07 | Personalizzazione | T11 | 25 | 60 | 58.3% | 60 |
| 12 | G05 | Matematica | T08 | 24 | 80 | 70.0% | 80 |
| 13 | G05 | Figurino | T20 | 23 | 50 | 54.0% | 50 |
| 13 | G02 | Cosmetologia | T09 | 23 | 50 | 54.0% | 50 |
| 13 | G06 | Relazione con il cliente | T13 | 23 | 55 | 58.2% | 55 |
| 13 | G07 | Lingua Italiana | T04 | 23 | 80 | 71.2% | 80 |
| 17 | G05 | Scienze | T09 | 22 | 50 | 56.0% | 50 |
| 17 | G13 | Comunicazione | T02 | 22 | 70 | 68.6% | 70 |
| 19 | G11 | Matematica | T08 | 21 | 60 | 65.0% | 60 |
| 19 | G02 | Lingua Inglese | T21 | 21 | 70 | 70.0% | 70 |

### run2 (per confronto, vedi sezione 4)

| # | Gruppo | Materia | Docente | Celle perse | Celle ph2 | Conserv.% |
|---|---|---|---|---:|---:|---:|
| 1 | G04 | Lingua Italiana | T14 | 44 | 90 | 51.1% |
| 2 | G03 | Lingua Italiana | T04 | 36 | 80 | 55.0% |
| 3 | G07 | Disegno Edile ed Impiantistico | T12 | 28 | 60 | 53.3% |
| 3 | G07 | Personalizzazione | T11 | 28 | 60 | 53.3% |
| 3 | G04 | Matematica | T05 | 28 | 70 | 60.0% |
| 6 | G03 | Lingua Inglese | T21 | 27 | 60 | 55.0% |
| 7 | G10 | Lingua Italiana | T04 | 26 | 50 | 48.0% |
| 7 | G04 | Scienze | T25 | 26 | 60 | 56.7% |
| 7 | G11 | Personalizzazione | T11 | 26 | 60 | 56.7% |
| 10 | G02 | Cosmetologia | T09 | 24 | 50 | 52.0% |
| 10 | G08 | Disegno | T10 | 24 | 50 | 52.0% |
| 10 | G06 | Scienze | T25 | 24 | 60 | 60.0% |
| 10 | G05 | Matematica | T08 | 24 | 80 | 70.0% |
| 14 | G13 | Comunicazione | T02 | 23 | 70 | 67.1% |
| 15 | G03 | Personalizzazione | T11 | 22 | 60 | 63.3% |
| 16 | G07 | Lingua Italiana | T04 | 22 | 80 | 72.5% |
| 17 | G06 | Relazione con il cliente | T13 | 21 | 55 | 61.8% |
| 17 | G11 | Matematica | T08 | 21 | 60 | 65.0% |
| 17 | G02 | Lingua Inglese | T21 | 21 | 60 | 65.0% |
| 20 | G05 | Figurino | T20 | 20 | 50 | 60.0% |

**Lettura**: i corsi piu' smontati sono materie "aula" ordinarie ad alto
volume (Lingua Italiana, Matematica, Scienze, Lingua Inglese, Disegno) di
docenti NON nella lista Q22 dei 7 a giorni fissi (T14, T04,
T05, T21, T25, T10, T11, T08, ecc.), con l'eccezione
di T12 (Q22), sempre in top-5 in entrambi i run.

---

## 2. Aggregato per gruppo e per docente: verifica empirica della gradazione M32

### Per gruppo (run1, ordinato per conservazione crescente = piu' eroso prima)

| Gruppo | Celle perse | Celle ph2 | Conserv.% |
|---|---:|---:|---:|
| G09 | 142 | 459 | 69.1% |
| G04 | 205 | 750 | 72.7% |
| G10 | 179 | 670 | 73.3% |
| G11 | 169 | 671 | 74.8% |
| G05 | 155 | 631 | 75.4% |
| G07 | 168 | 695 | 75.8% |
| G13 | 163 | 683 | 76.1% |
| G08 | 134 | 626 | 78.6% |
| G06 | 204 | 957 | 78.7% |
| G12 | 115 | 650 | 82.3% |
| G03 | 173 | 987 | 82.5% |
| G14 | 19 | 115 | 83.5% |
| G02 | 154 | 988 | 84.4% |
| G01 | 73 | 657 | 88.9% |

(run2 conferma l'ordine di grandezza, vedi sezione 4: correlazione Pearson
r=0.948 sulle conservazioni per gruppo fra i due run; nessun gruppo cambia
drammaticamente posizione.)

### Per docente: cross-check coi 7 docenti Q22 a giorni fissi

| Rank (1=piu' eroso) | Docente | Celle perse | Celle ph2 | Conserv.% | Q22? |
|---:|---|---:|---:|---:|:---:|
| 1 | **T17** | 15 | 37 | **59.5%** | si |
| 2 | **T12** | 47 | 125 | **62.4%** | si |
| 3 | T11 | 134 | 425 | 68.5% | |
| 4 | T10 | 67 | 225 | 70.2% | |
| 5 | T25 | 161 | 564 | 71.5% | |
| 6 | T04 | 131 | 485 | 73.0% | |
| 7 | T23 | 135 | 520 | 74.0% | |
| 8 | T14 | 94 | 367 | 74.4% | |
| 9 | T09 | 140 | 555 | 74.8% | |
| 10 | T16 | 14 | 57 | 75.4% | |
| 11 | T20 | 128 | 524 | 75.6% | |
| 12 | T13 | 104 | 440 | 76.4% | |
| 13 | T21 | 150 | 660 | 77.3% | |
| 14 | T02 | 120 | 530 | 77.4% | |
| 15 | T08 | 143 | 640 | 77.7% | |
| 16 | T05 | 104 | 496 | 79.0% | |
| 17 | **T24** | 38 | 187 | **79.7%** | si |
| 18 | T03 | 74 | 373 | 80.2% | |
| 19 | T26 | 58 | 340 | 82.9% | |
| 20 | T18 | 50 | 385 | 87.0% | |
| 21 | T15 | 63 | 526 | 88.0% | |
| 22 | T06 | 53 | 601 | 91.2% | |
| 23 | **T22** | 15 | 178 | **91.6%** | si |
| 24 | **T01** | 12 | 195 | **93.8%** | si |
| 25 | **T19** | 3 | 104 | **97.1%** | si |

(**T07**, il settimo docente Q22, non compare: tutte le sue 72 lezioni
sono pinned in ingresso-ph2, quindi nessuna lezione unpinned da confrontare,
popolazione strutturalmente vuota per questo docente in entrambi i run.)

**Risultato (falsificabile, non uniforme): la gradazione morde ma non
uniformemente.** Dei 6 docenti Q22 con ore unpinned:
- **4/6 sono fra i meglio conservati della coorte**: T19 (rank 25/25,
  miglior conservazione assoluta), T01 (24/25), T22 (23/25), T24
  (17/25, sopra la mediana), coerente con l'ipotesi di gradazione
  ("scarso = quasi-immobile").
- **2/6 sono i PEGGIO conservati di TUTTA la coorte**: **T17 (rank
  1/25) e T12 (rank 2/25)**, peggio di ogni docente non-Q22, "libero".
  Questa e' l'eccezione diretta che la domanda chiedeva di cercare.

Media semplice (non pesata per ore): Q22 80.7% vs non-Q22 77.6% (run1);
pesata per ore (celle ph2): Q22 84.3% vs non-Q22 77.9% (run1). **In
aggregato la scarsita' aiuta** (+6.4 punti pesati), ma il gap e' quasi
interamente prodotto dai 4 docenti ben protetti, mentre T17/T12
restano un residuo non spiegato dalla teoria "docente a giorni fissi implica
peso alto".

**Causa piu' probabile per corso, non verificata contro il peso M32 reale**
(che richiederebbe rieseguire `WeeklyTemplatePlanner`, fuori scope di
un'analisi pura-Python sui soli dump): il peso M32 e' **per corso**
(`clamp(100000/celle-VRP-del-corso, 20, 500)`), non per docente. Un docente
a giorni fissi puo' comunque avere un corso con MOLTE celle VRP disponibili
se quel corso richiede poche ore rispetto alla sua disponibilita':
- **T17** eroso quasi solo su **"Recuperi ed Approfondimenti"** (RA,
  6/10 e 5/10 celle perse su G05/G09). RA e' per sua natura "ore a
  discrezione del docente, tipicamente usate per estendere le proprie altre
  materie" (nota M21 in CLAUDE.md): un corso strutturalmente flessibile,
  candidato naturale a molte celle VRP libere anche per un docente scarso.
- **T12** eroso su **"Disegno Edile ed Impiantistico"** (G07, 30/60
  perse; G11 17/65), unico corso di T12 nei dati, template_cells
  osservato 8 (G07) e 15 (G11), piu' ampio dei 2-6 osservati per
  T01/T19/T22 sui loro corsi lab (Servizio tecnico e stilistico,
  Barberia, Laboratorio Termoidraulico): indizio (non prova) che questo
  corso abbia piu' celle VRP disponibili degli altri corsi Q22, quindi un
  peso M32 piu' basso nonostante il docente sia "a giorni fissi".

Questa e' un'ipotesi plausibile a partire dai dati osservabili, **non una
verifica del peso M32 reale** (serve il calcolo lato Java/`refreshSolverStatics`
per confermarla).

---

## 3. Dove vanno le ore perse

Sulle istanze **davvero rilocate** (esclude `same_cell_other_week`, che non
e' una deviazione, ed esclude `removed_no_destination`):

| Categoria | run1 | run2 |
|---|---:|---:|
| Totale rilocate | 2161 | 2190 |
| stesso giorno, slot diverso | 99 (4.6%) | 113 (5.2%) |
| altro weekday, stesso slot | 526 (24.3%) | 573 (26.2%) |
| altro giorno del tutto (data e ora diverse) | 1536 (71.1%) | 1504 (68.7%) |
| **su un'ALTRA cella template dello stesso corso (M32 non penalizza)** | **398 (18.4%)** | **422 (19.3%)** |
| **fuori da qualunque cella template del corso (M32 penalizza)** | **1763 (81.6%)** | **1768 (80.7%)** |

Voci fuori dal totale "rilocate":

| | run1 | run2 |
|---|---:|---:|
| stessa cella, altra settimana (nessuna deviazione) | 382 | 370 |
| rimossa senza destinazione (probabile unassigned) | 16 | 18 |
| nuova da unassigned (probabile lezione prima unassigned) | 84 | 87 |

**Lettura**: la stragrande maggioranza (circa 69-71%) delle ore che
cambiano posto cambia sia giorno che ora: non e' un piccolo aggiustamento
locale (slittamento di un'ora sullo stesso giorno, solo 4.6-5.2%), e' uno
spostamento su tutt'altra collocazione settimanale. E di queste rilocazioni,
solo **circa 1 su 5 (18-19%)** atterra su una cella che e' comunque parte
del template del corso (quindi non penalizzata da M32): l'**80%** delle
rilocazioni e' una deviazione genuina che M32 dovrebbe scontare, coerente
col fatto che la ricorrenza settimanale unpinned crolla dal 74.3% al
52.8-53.7% in questa sola fase.

---

## 4. Run1 vs run2: sistematico o stocastico?

| Metrica | Valore |
|---|---|
| Corsi comuni analizzati | 260/260 |
| Pearson r (celle perse per corso, run1 vs run2) | **0.953** |
| Spearman rho (rank, stessa coppia) | **0.909** |
| Overlap Top-10 corsi piu' erosi | **8/10 (80%)** |
| Overlap Top-20 corsi piu' erosi | **17/20 (85%)** |
| Pearson r (conservazione% per gruppo, run1 vs run2) | **0.948** |

Le uniche differenze nel Top-10: run1 include `G13/Lavorazioni
meccaniche/T10` e `G08/Disegno/T10` che in run2 scendono appena
fuori dal Top-10 (rimpiazzati da `G02/Cosmetologia/T09` e `2
FB/Personalizzazione/T11`, comunque presenti nel Top-20 di entrambi i
run, vedi sezione 1).

**Conclusione: l'erosione e' sistematica, non rumore stocastico.** Con seed
diversi, la correlazione fra i due run e' molto alta sia a livello di singolo
corso (r=0.953) sia di gruppo (r=0.948), e 4/5 dei corsi piu' smontati sono
identici. Questo esclude che il fenomeno sia un artefatto del random walk
della local search: e' la struttura dei corsi/gruppi/vincoli concorrenti
(M20 riempimento giornaliero, M22 compattezza calendario, H17 compattezza
verticale) a determinare sistematicamente QUALI corsi cedono,
indipendentemente dal seed.

---

## Limiti dichiarati (riepilogo)

1. Nessun ID di lezione stabile nei dump: tutte le metriche sono su
   multiset aggregati, non su traiettorie di lezioni singole verificate.
2. `template_cells(corso)` e' un **proxy** (celle usate in ingresso-ph2), non
   il vero output di `WeeklyTemplatePlanner`: puo' differire leggermente se
   la coda round-robin della costruzione ha piazzato qualche lezione fuori
   template.
3. Il peso M32 reale per corso (`clamp(100000/celle-VRP, 20, 500)`) non e'
   ricalcolato qui: le ipotesi su T17/T12 nel paragrafo 2 sono
   indiziarie, basate sul numero di celle OSSERVATE (non sulle celle VRP
   disponibili prima del solve), e andrebbero confermate lato Java se
   rilevanti per una decisione di tuning.
4. Q3 usa un secondo matching euristico (priorita' A-D) piu' fragile di
   Q1/Q2: va letto come descrizione qualitativa del carattere del turnover,
   non come misura di traiettorie certificate.
