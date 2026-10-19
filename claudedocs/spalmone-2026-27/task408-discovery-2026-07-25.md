# TASK-408 discovery — perché full-day ~78-79% contro l'85,4% del golden

Analisi OFFLINE (nessun solve, nessuna modifica a codice/vincoli), Fedora 2026-07-25.
Metodologia: `daily-fill-kpi.py` (definizione ufficiale di "full-day", basata su
`calendario_class_days.csv`) più script ad-hoc in `/tmp` che riusano la stessa fonte
dati per fare il breakdown per-giorno che lo script aggregato non espone. Comandi
riproducibili in fondo a ogni sezione.

Dati usati: golden `claudedocs/spalmone-2026-27/data/lessons.csv`; run
`claudedocs/spalmone-2026-27/task413-results/val413-run{1,2}/lessons.txt` (M32 attivo)
e `claudedocs/spalmone-2026-27/task414-results/val414-run{1,2}/lessons.txt` (baseline
senza M32, stesso stack altrimenti).

## 0. Setup: golden convertito nello stesso formato dump

`daily-fill-kpi.py` legge un dump `group|date|hour|subject|teacher|pinned`. Il golden
(`lessons.csv`, colonne `date,day,slot,closed,group,teacher,subject`) è stato convertito
1:1 (escludendo `closed=True`, stessa convenzione di `shared.py#iter_open_lessons_rows`,
`hour = int(slot[:2])`) e passato allo stesso script:

```
FULL-DAY RATE golden: 2005/2395 = 83.7%
sotto: 320 (deficit 1297h), sopra: 70
```

Nota: l'83,7% (non l'85,4% citato nel docstring dello script) è la cifra corretta su
QUESTA scala uniforme (tutti e 14 i gruppi, stessa sorgente `calendario_class_days.csv`)
— coincide byte-per-byte con la misura già riportata nel corpo del task
("CONFRONTO GOLDEN PER-GRUPPO ESEGUITO", 2026-07-24). L'85,4% del docstring viene da una
misura precedente (TASK-385) probabilmente su uno scope leggermente diverso; qui uso
sempre 83,7% come riferimento perché è calcolato con lo stesso identico metodo/gruppi
usato per i run.

## 1. Delta per gruppo (golden vs run)

`G04`, `G08`, `G13` e `G14` hanno caveat noti (TASK-385 "Measured"): per questi 4
gruppi `calendario_class_days.csv` NON è la fonte di verità (FC/G13 usano
`calendario_fc.csv`; G14 ha un mismatch 9x sheet-vs-domanda reale) — la colonna
"delta" per loro è fuorviante e va ignorata per la diagnosi, riportata solo per
completezza.

### Gruppi puliti (10, nessun caveat noto)

| gruppo | golden | val413-run1 | val414-run1 | Δ (golden−val413) | Δ (golden−val414) |
|---|---:|---:|---:|---:|---:|
| G01 | 100% | 98% | 98% | +2 | +2 |
| G02 | 100% | 97% | 98% | +3 | +2 |
| G03 | 88% | 91% | 89% | **−3** | −1 |
| G05 | 100% | 99% | 97% | +1 | +3 |
| G06 | 100% | 88% | 89% | **+12** | +11 |
| G07 | 100% | 97% | 96% | +3 | +4 |
| G09 | 95% | 76% | 59% | **+19** | **+36** |
| G10 | 94% | 80% | 80% | **+14** | +14 |
| G11 | 99% | 98% | 95% | +1 | +4 |
| G12 | 85% | 63% | 64% | **+22** | +21 |

Il gap è concentrato in 4 gruppi: **G12 (+22pt), G09 (+19pt val413 / +36pt
val414), G10 (+14pt), G06 (+12pt)**. Tutti e 4 sono gruppi con finestra stage attiva
(G07/G11/G10/G09/G12 sono i 5 gruppi "class-days" con stage, TASK-381) —
tranne G06, che non è nella lista stage e resta come residuo non spiegato dal
meccanismo principale di sezione 4. G03 è addirittura MEGLIO del golden (−3, il run
compatta dove il golden spalma — nota già presente nel task per G04).

### Gruppi con caveat (per completezza, NON usare per diagnosi)

| gruppo | golden | val413-run1 | val414-run1 | nota |
|---|---:|---:|---:|---|
| G04 | 32% | 53% | 50% | run MEGLIO del golden — quota da `calendario_fc.csv`, non da questa sheet |
| G08 | 90% | 83% | 83% | idem |
| G13 | 89% | 85% | 82% | idem (G13 è quota-FC, TASK-385) |
| G14 | 0% | 0% | 0% | mismatch 9x sheet-vs-domanda reale, esente da M20/M22 |

Comando: `python3 scripts/analyze/daily-fill-kpi.py <dump>` sul golden convertito e su
ciascun `lessons.txt` (vedi script di conversione in appendice).

## 2. Anatomia dei giorni golden-pieni / run-sotto-pieni

Per ogni `(gruppo, data)` con `expected>0` (sheet), `gruppo` NON in {G14, G04, G08,
G13}, dove il GOLDEN è pieno (`len(golden_hours)==expected`) e il RUN è sotto-pieno
(`len(run_hours) < expected`):

| metric | val413-run1 | val414-run1 |
|---|---:|---:|
| coppie (gruppo,data) | 100 | 112 |
| deficit totale (ore) | 173 | 182 |

**Istogramma deficit** (val413-run1): `{1: 61, 2: 23, 3: 6, 4: 3, 5: 6, 6: 1}` — il 61%
delle coppie manca di **esattamente 1 ora**; solo il 16% manca di 3h o più.

**Per weekday** (val413-run1): `Monday 21, Tuesday 11, Wednesday 37, Thursday 14, Friday
17`. Mercoledì domina (37%), coerente col fatto che G12 (40/100 coppie) ha
esattamente il pattern Mon/Wed a 5 slot aperti (vedi sezione 3).

**Per mese** (val413-run1): distribuito su tutto l'anno (2026-09 a 2027-06), nessun mese
domina da solo (max 19 a maggio, min 1 a marzo) — NON è un effetto di fine-anno o di
inizio-anno concentrato.

**Per posizione dell'ora mancante nel giorno del golden** (rango dell'ora mancante
nell'elenco ordinato delle ore golden di quel giorno): `LAST 96, MID 66, FIRST 15`
(su 177 ore mancanti totali — leggermente più delle 173h di deficit per via di rari casi
dove il run usa un'ora diversa da quella golden lo stesso giorno). **Il 54% delle ore
mancanti è l'ULTIMO slot del giorno**, e per-gruppo il pattern è quasi universale (G02
100% LAST, G06 13/17 LAST, G10 19/27 LAST, G12 40/40 giorni con l'ultima ora
mancante, spesso l'unica). Il buco non è casuale nella giornata: la giornata si costruisce
compatta dallo slot 0 (H17/M18) e la ripartizione di ore mancanti la sacrifica quasi
sempre in coda.

**Materie/docenti al posto della slot mancante** (dal lato GOLDEN, per G12): nessun
singolo corso domina — Lingua italiana/T04 (15), Servizio tecnico e
stilistico/T01 (10), Servizio stilistico/T26 (9), Scienze/T25 (5), il resto
1-4 ciascuno. Non è un problema di UN docente/corso saturo; è un pattern strutturale del
giorno, non del corso.

Comando (script completo in appendice `/tmp/anatomy.py`):
```
python3 /tmp/anatomy.py claudedocs/spalmone-2026-27/task413-results/val413-run1/lessons.txt val413-run1
```

## 3. val413 (M32 on) vs val414 (M32 off): stessi giorni o giorni diversi?

| metric | val413-run1 | val414-run1 |
|---|---:|---:|
| coppie golden-pieno/run-sotto | 100 | 112 |
| intersezione (stesso giorno non-pieno in entrambi) | 30 | 30 |
| solo in val413 | 70 | — |
| solo in val414 | — | 82 |

Solo **30/100-112 coppie sono le stesse identiche date**: M32 non riduce uniformemente il
deficit, **sposta QUALI giorni restano non-pieni** (redistribuisce le deviazioni dal
template), con un netto miglioramento modesto (100 vs 112 coppie, 173h vs 182h deficit,
~5-8%). Per G12 il conteggio resta quasi identico (40 vs 38) ma quasi tutte le date
specifiche cambiano (solo 12 in comune). Il gruppo dove M32 aiuta di più è **G09** (9
coppie in val413 contro 21 in val414 — M32 "chiude" 19 dei 21 giorni-difetto di G09,
ne apre 7 nuovi). Conclusione: M32 non tocca il meccanismo strutturale di sezione 4 (che
è indipendente da M32), redistribuisce solo il rumore residuo della local search.

## 4. Il meccanismo permissivo — root-caused nel codice, non ipotizzato

### 4.1 Le "date fantasma": nessuna riga sheet ⇒ nessun DailyActivityQuota ⇒ M20 tratta il giorno come APERTO invece che CHIUSO

Per ogni gruppo, isolando le date su cui il RUN piazza lezioni ma che NON hanno riga in
`calendario_class_days.csv` (quindi non entrano nel denominatore "expected" del KPI),
il **golden ha SEMPRE 0 ore su quelle stesse date** (verificato per tutti i 26/25 casi
trovati, ogni run) — cioè sono giorni che il golden considera realmente chiusi/fuori
orario per quel gruppo (quasi sempre stage full-day), ma il nostro sistema li tratta come
scolastici normali:

| gruppo | val413-run1: date-fantasma / ore-run-lì | golden lì |
|---|---:|---:|
| G12 | 12 date / **64h** | 0 |
| G10 | 3 date / 20h | 0 |
| G07 | 2 date / 15h | 0 |
| G09 | 2 date / 8h | 0 |
| G11 | 1 data / 5h | 0 |
| G01, G02, G03, G05, G06, G14 | 1 data ciascuno (2027-01-22, vedi 4.2) / 5h | 0 |
| **totale** | **26 date / 140h** | — |

Riproducibile e stabile su tutti e 4 i run disponibili: 140h (val413-run1), 154h
(val413-run2), 125h (val414-run1), 139h (val414-run2) — **sempre G12 il maggior
contributore (53-64h), G10 il secondo (20-37h)**. Non è rumore del solver: è la stessa
identica firma indipendentemente da M32 on/off.

**Per G12 questo spiega l'intero deficit**: 64h di ore-fantasma contro 64h di deficit
totale nelle coppie golden-pieno/run-sotto (match esatto, sezione 2). Per l'aggregato dei
10 gruppi puliti: **140h fantasma su 173h di deficit totale = 81%** (val413-run1); 125h
su 182h = 69% (val414-run1). Questa è la causa dominante quantificata.

**Meccanismo nel codice** (`SchoolScheduleConstraintProvider.dailyQuota()`,
`src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java:1401`):

```java
DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
if (row != null) {
    int expected = Math.min(row.aulaHours + row.labHours, openSlots);
    ...
    return expected;
}
int quota = group.maxHoursPerDay != null ? group.maxHoursPerDay : DEFAULT_DAILY_QUOTA;
return Math.min(quota, openSlots);
```

Quando `calendario_class_days.csv` non ha una riga per `(gruppo, data)`,
`DataGenerator.generateExpectedDailyHours()` (`bootstrap/DataGenerator.java:894-...`) non
crea NESSUNA riga `DailyActivityQuota` per quella data — `getExpectedHoursRow` ritorna
`null` — e `dailyQuota()` cade nel fallback `group.maxHoursPerDay` (per G12, 8h,
TASK-343) limitato solo da `openSlots` (la finestra calendario SETTIMANALE ricorrente, che
non sa esprimere "questo giovedì specifico è chiuso"). Per G12, giovedì ha 8 slot
aperti nel pattern settimanale → `dailyQuota()` = 8, e **M20 (`ofMedium(1000)` per
ora-di-deficit) penalizza attivamente il giorno finché non è riempito fino a 8h**, anche
se quel giorno specifico dovrebbe avere capacità 0. `H4` (stage blackout) blocca solo le
materie NON-professionalizzanti nella finestra stage — le professionalizzanti restano
piazzabili ogni giorno della settimana mascherata, incluse le date realmente "fuori sede"
— e infatti tutte le lezioni piazzate su queste 12 date fantasma di G12 (tranne
2026-11-19, giorno prima dell'inizio finestra, vedi sotto) sono professionalizzanti pure
(Servizio tecnico e stilistico, Relazione con il cliente, Gestione aziendale, Consulenza
d'immagine, Analisi del capello...).

**Non c'è un vero trade-off medium da "comprare"**: su un giorno fantasma, piazzare una
lezione ABBASSA il costo M20 (riduce il deficit contro un target sbagliato ma
nonzero), non lo alza — è una mossa dominante, non un compromesso contro M15/M21/M32/M22.
`M22` (calendar compactness, `:1525`) penalizza SOLO `actualDays > ceil(totalHours/quota)`
— con un volume annuo fisso, riempire un giorno fantasma anziché lasciarlo vuoto riduce
anche `actualDays` a parità di ore totali, quindi conviene pure a M22. Non serve una
misura A/B per confermarlo: è deducibile direttamente dalla definizione dei due vincoli.

**2026-11-19 (giorno prima dell'inizio finestra stage 2026-11-20 di G12)**: stesso
buco nella sheet, ma essendo FUORI dalla finestra `StagePattern`, H4 non blocca nulla —
il run piazza 6h MISTE (Orientamento, Scienze — non professionalizzanti) invece delle
sole professionalizzanti. Conferma che il meccanismo di root cause è la RIGA MANCANTE, non
la finestra stage in sé: la finestra stage aggiunge solo un filtro sul TIPO di materia,
non un segnale "il giorno è chiuso".

### 4.2 Bug concreto trovato: entry di chiusura con l'anno sbagliato

`2027-01-22` (venerdì) è chiuso al 100% nel golden: **tutte le 15 righe golden per quella
data hanno `closed=True`**, per TUTTI i gruppi (incluse le pin Stage di G11/G12).
Ma il nostro `DataGenerator.CLOSURES_2026_27` ha:

```java
closures.put(LocalDate.of(2026, 1, 22), "Out-of-range entry (predates 2026/27 school year)");
```

`2026-01-22` non è nell'orizzonte scolastico (che parte il 2026-09-14) — la entry è
letteralmente inerte, come il commento stesso ammette. Tutto lascia pensare a un errore di
anno nella trascrizione della sorgente originale (`2026` invece di `2027`): la data
INTESA coincide esattamente con la chiusura osservata nel golden. Effetto: **9 gruppi su
14 ricevono una giornata scolastica intera (5h ciascuno = 45h totali) su una data che
dovrebbe essere chiusa** — un sottoinsieme del meccanismo di 4.1 (nessuna riga sheet →
nessun fatto → M20 apre il giorno), reso concreto e riproducibile.

`DataGenerator.java` è protetto (`.claude/rules/datagenerator-protection.md`): NON
toccato in questo task (solo analisi offline); il fix (se confermato dalla scuola) è
cambiare l'anno della entry da autorizzare esplicitamente.

### 4.3 "Ore finite" (esaurimento aritmetico) — ESCLUSO per 12/14 gruppi

Confronto ore totali piazzate (run) vs golden, per gruppo:

| gruppo | golden | val413-run1 | val414-run1 | Δ |
|---|---:|---:|---:|---:|
| G01…G07, G09, G10, G13, G14 (9 gruppi) | — | — | — | **0** |
| G11 | 958 | 966 | 966 | +8 |
| G12 | 787 | 794 | 794 | +7 |
| G04 | 974 | 750 | 750 | **−224** (caveat noto) |
| G08 | 943 | 902 | 902 | **−41** (caveat noto) |

Per i 12 gruppi puliti il run piazza le STESSE ore totali del golden (o leggermente di
più). L'ipotesi "il run ha semplicemente meno ore da piazzare quindi il full-day non può
arrivare al livello del golden" è **falsificata per tutti tranne G04/G08** (caveat già
noto, quota da `calendario_fc.csv`): il problema è di ALLOCAZIONE (quali giorni), non di
QUANTITÀ (quante ore totali).

## 5. Sintesi — cause dominanti quantificate

1. **Fatto-mancante-trattato-come-aperto (M20/M22, meccanismo dominante)**: quando
   `calendario_class_days.csv` non ha una riga per `(gruppo, data)`, nessun
   `DailyActivityQuota` viene generato, e `dailyQuota()` cade nel fallback
   `maxHoursPerDay`/`DEFAULT_DAILY_QUOTA` — un giorno che il golden tratta come CHIUSO
   (quasi sempre stage full-day) diventa un giorno APERTO che M20 penalizza se
   sotto-riempito. Spiega **81% (val413) / 69% (val414)** del deficit totale sui 10
   gruppi puliti, e **il 100% (match esatto 64h=64h)** del deficit di G12, il gruppo
   col gap maggiore (+22pt vs golden). Colpisce soprattutto i 5 gruppi con finestra stage
   (G07/G11/G10/G09/G12). Leva candidata: un vincolo/fatto nuovo — un
   complemento tipo M20.2 (già esistente per il caso simmetrico "riga con expected>0 mai
   riempita") ma per il caso "nessuna riga sheet AND il gruppo è in finestra stage AND la
   data cade in un pattern di weekday che altre date della stessa finestra segnano come
   stage-only" → target 0 esplicito, oppure trattare l'ASSENZA di riga come fatto
   `expected=0` invece che `nessun fatto` quando la data cade dentro l'attivo
   `StagePattern` del gruppo. Va verificato con la scuola se l'assenza di riga è
   davvero "0 ore" prima di implementarlo (non è certo al 100%: 2027-01-22 mostra che a
   volte l'assenza è un buco sheet-wide di chiusura, non uno stage-day).
2. **Bug dati concreto e isolato**: `DataGenerator.CLOSURES_2026_27` ha una entry di
   chiusura con l'anno sbagliato (`2026-01-22` invece di `2027-01-22`, la data che il
   golden mostra chiusa al 100%). Effetto misurato: 45h su 9 gruppi in una singola data.
   È un caso particolare del meccanismo #1, ma essendo un singolo valore errato è la leva
   più economica e diretta: correggere l'anno (previa autorizzazione esplicita, file
   protetto) chiude interamente questa fetta.
3. **"Ore finite" (esaurimento aritmetico)**: NON è una causa per 12/14 gruppi (le ore
   totali piazzate combaciano col golden o le superano leggermente) — confermato solo per
   G04/G08, già un caveat noto e tracciato altrove (gap di quota-lab). Nessuna leva
   nuova qui.
4. **Residuo non spiegato** (es. G06 +12pt, non un gruppo stage; G03 già migliore
   del golden): quota minoritaria del gap, coerente con la tolleranza medium
   originariamente prevista per M20/M22 (la scelta storica H→M, CLAUDE.md "Sparse-day
   horizontal compaction") — il confronto golden diretto della sezione precedente del
   task conferma che quella scelta resta corretta (il golden stesso ha 320/2395 giorni
   sotto-pieni, l'83,7% non è "sempre pieno"). Nessuna nuova evidenza qui giustifica una
   promozione a hard; questa quota residua non è il driver dominante del gap misurato.
5. **M32 (TASK-413) non tocca il meccanismo**: A/B mostra che M32 ridistribuisce QUALI
   giorni restano sotto-pieni (solo 30/100-112 coppie coincidono) con un miglioramento
   aggregato modesto (~5-8%), ma il volume di "ore fantasma" resta quasi identico
   (140h→125h) — la causa #1 è indipendente dalla presenza di M32.

## Appendice — script di riferimento (riprodotti in `/tmp`, non nel repo)

`/tmp/build_maps.py` (loader condivisi: expected da `calendario_class_days.csv`, golden
da `lessons.csv`, run da `lessons.txt`), `/tmp/anatomy.py` (join golden-pieno/run-sotto
con weekday/mese/posizione/materie adiacenti). Snippet minimo per rigenerare la
conversione golden→dump usata in sezione 0:

```python
import csv
rows = csv.DictReader(open('claudedocs/spalmone-2026-27/data/lessons.csv'))
out = open('/tmp/golden-dump.txt', 'w'); out.write('SET\n')
for r in rows:
    if r['closed'] == 'True':
        continue
    out.write(f" {r['group']}|{r['date']}|{int(r['slot'][:2])}|{r['subject']}|{r['teacher']}|true\n")
```
poi `python3 scripts/analyze/daily-fill-kpi.py /tmp/golden-dump.txt`.
