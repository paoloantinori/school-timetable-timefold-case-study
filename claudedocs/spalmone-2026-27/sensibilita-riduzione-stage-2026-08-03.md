# Analisi di sensibilita: riduzione ore di stage (2026-08-03)

**Contesto:** referente ha segnalato che alcuni corsi potrebbero passare da ~300h a molte meno ore di stage (150h o 50h). Questa analisi quantifica l'impatto sulla fattibilita della griglia, leggendo SOLO dai fatti di input (DAQ, GCR, Course, Timeslot). Il DB e' mid-solve sulla profil `multistart` quindi nessuna query tocca la tabella `lesson`.

---

## (a) Delta per gruppo

**Fonte:** `dailyactivityquota` (somma `stagehours` per `studentgroup_id`), `course` (`totalhours`).

| Gruppo | Stage attuali (DAQ) | In-grid attuali (DAQ aula+lab) | Delta se stage->150 | Delta se stage->50 |
|--------|--------------------:|-------------------------------:|-------------------:|------------------:|
| G04  |                  224 |                            835 |                 +74 |               +174 |
| G07 |                  300 |                            690 |                +150 |               +250 |
| G08  |                  300 |                            666 |                +150 |               +250 |
| G09 |                  300 |                            670 |                +150 |               +250 |
| G10  |                  300 |                            666 |                +150 |               +250 |
| G11 |                  295 |                            671 |                +145 |               +245 |
| G12 |                  295 |                            675 |                +145 |               +245 |
| G13   |                  295 |                            683 |                +145 |               +245 |
| **TOTALE** | **2 309** | **5 556** | **+1 109** | **+1 909** |

**Query riproducibile:**
```sql
SELECT sg.name, SUM(daq.stagehours) AS stage_h, SUM(daq.aulahours+daq.labhours) AS in_grid_h
FROM dailyactivityquota daq JOIN studentgroup sg ON sg.id = daq.studentgroup_id
WHERE daq.studentgroup_id IN (SELECT DISTINCT studentgroup_id FROM dailyactivityquota WHERE stagehours > 0)
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

**Nota:** G04 ha solo 224h di stage (28 giorni x 8h), non 300. Il corso Stage vale 240h con `expectedUnscheduledHours=32` (208h effettive), ma la DAQ conta 224h dal calendario. Per l'analisi di sensibilita si usa il valore DAQ (il piano della scuola).

---

## (b) Capacita residua della griglia

**Metodologia:** la capacita della griglia = somma (`GCR.maxslot - GCR.minslot + 1`) x giorni-scolastici-per-weekday, usando solo le regole GCR sempre attive (`effectivefrom IS NULL`). 171 giorni scolastici (2026-09-14..2027-06-10): Lun=33, Mar=33, Mer=35, Gio=36, Ven=34.

**Scenario A: gli ore spostate diventano ore in-grid (carico totale aumenta)**

| Gruppo | Capacita griglia | In-grid attuali | Slack attuale | In-grid @150 | Slack @150 | Verdetto | In-grid @50 | Slack @50 | Verdetto |
|--------|----------------:|----------------:|-------------:|------------:|----------:|---------:|-----------:|---------:|---------:|
| G04  | 1 368 | 835 | 533 | 909 | 459 | FEASIBLE | 1 009 | 359 | FEASIBLE |
| G07 | 1 302 | 690 | 612 | 840 | 462 | FEASIBLE | 940 | 362 | FEASIBLE |
| G08  | 1 368 | 666 | 702 | 816 | 552 | FEASIBLE | 916 | 452 | FEASIBLE |
| G09 | 1 368 | 670 | 698 | 820 | 548 | FEASIBLE | 920 | 448 | FEASIBLE |
| G10  | 1 269 | 666 | 603 | 816 | 453 | FEASIBLE | 916 | 353 | FEASIBLE |
| G11 | 1 302 | 671 | 631 | 816 | 486 | FEASIBLE | 916 | 386 | FEASIBLE |
| G12 | 1 031 | 675 | 356 | 820 | 211 | FEASIBLE | 920 | 111 | FEASIBLE |
| G13   | 1 302 | 683 | 619 | 828 | 474 | FEASIBLE | 928 | 374 | FEASIBLE |
| **TOTALE** | **10 310** | **5 556** | **4 754** | **6 665** | **3 645** | **ALL OK** | **7 465** | **2 845** | **ALL OK** |

Tutti i gruppi sono **FEASIBLE** anche nello scenario peggiore (stage->50, tutti gli ore spostati diventano in-grid). Il gruppo piu stretto e' **G12** con solo 111 slot di slack @50 (10.8% di margine), ma ancora positivo.

**Scenario B: gli ore spostati diventano ore in-grid, CON giorni di stage liberati**

Quando lo stage scende, i giorni precedentemente dedicati allo stage diventano disponibili per lezioni in-grid. Ogni giorno liberato apre `open_slots` posizioni aggiuntive. Questo aumenta la capacita effettiva:

| Gruppo | Stage giorni attuali | Giorni liberati @50 | Capacita liberata | In-grid @50 | Capacita effettiva | Slack effettivo |
|--------|--------------------:|--------------------:|------------------:|-----------:|-------------------:|----------------:|
| G04  | 28 | 21 | 168 | 1 009 | 1 536 | 527 |
| G07 | 38 | 31 | 236 | 940 | 1 538 | 598 |
| G08  | 38 | 31 | 248 | 916 | 1 616 | 700 |
| G09 | 38 | 31 | 248 | 920 | 1 616 | 696 |
| G10  | 38 | 31 | 230 | 916 | 1 499 | 583 |
| G11 | 37 | 30 | 228 | 916 | 1 530 | 614 |
| G12 | 43 | 34 | 205 | 920 | 1 236 | 316 |
| G13   | 37 | 30 | 228 | 928 | 1 530 | 602 |

La capacita effettiva (griglia + giorni liberati) supera ampiamente il fabbisogno in ogni scenario. **Lo Scenario B (il realistico, con giorni di stage liberati) e' ancora piu comodo.**

**Scenario C: gli ore di stage semplicemente SPARISCONO (totale ore scende)**

In questo caso in-grid resta 5 556h. Slack attuale = 4 754 slot. Nessun impatto. Questo e' il scenario piu semplice.

---

## (c) Pressione sulle celle sature

### Cellula nota: mercoledi' 10:00 per G11

Il difetto H17 del bad-seed nota e' su G11, mercoledi', ore 10:00 (slot index 2, 11:00-12:00). La LS inserisce un buco di compattezza a quel punto perche' non riesce a collocare tutte le lezioni in modo compatto.

**Dati DAQ per G11 il mercoledi':**
- 34 mercoledi' scolastici
- 24 mercoledi' in-grid (aula+lab > 0), 10 mercoledi' stage (full 8h, blocco continuo 13/01-17/03/2027)
- Media in-grid per mercoledi': 120h / 24 giorni = **5.0h/giorno** (solo giorni in-grid)
- Capacita mercoledi': 8 slot x 35 giorni = 280 slot totali (ma 10 dedicati stage -> 270 effettivi in-grid)

**Se stage->50h per G11:**
- 245h spostate in-grid, distribuite su ~170 giorni scolastici
- Incremento mercoledi': ~245 x 35/170 = **+50h** su mercoledi'
- I 10 mercoledi' di stage si liberano, fornendo 10 x 8 = **80 slot aggiuntivi**
- Nuovo mercoledi': 120 + 50 = 170h in 35 x 8 = 280 slot (60.7% utilizzo)

**Verdetto sulla pressione:** la cellula mercoledi'@10 per G11 **NON subisce pressione aggiunta**. Anzi, i giorni di stage liberati aumentano la capacita disponibile. Il problema H17 del bad-seed e' legato alla configurazione di compattazione (slot saturi per composizione), non alla capacita aggregata del mercoledi'.

### Altre celle sature

**G10**: 21 giorni con in-grid = 8h (tutti mercoledi' durante il periodo Ott-Dic, lab-only). Nessun margine su quei giorni. Se stage sposta 150h in-grid, la distribuzione uniforme aggiunge ~150 x 35/168 = +31h su 35 mercoledi' = +0.9h/giorno. Da 5.1 a 6.0h. Ancora sotto il cap di 8h.

**G12**: 5 giorni con in-grid = 8h (tutti giovedi' Apr-Mag, periodo G12 pre-stage). Il gruppo ha solo 5h di capacita lun-mar-ven, 8h gio, 7h sab. Se stage sposta 245h in-grid, distribuzione uniforme su 177 giorni = +1.4h/giorno. Da 4.7 a 6.1h in media. Gia' oggi G12 ha giorni a 8h.

**Cosa NON posso misurare senza un solve:** la pressione per-cell esatta (quanti giorni supererebbero il cap di 8h, quante lezioni finirebbero in slot non desiderati) richiede il solver. Dai fatti di input, la distribuzione uniforme mostra che l'impatto medio-giornaliero e' contenuto (< 2h/giorno in ogni scenario), ma la varianza reale puo' produrre giorni localmente saturi. Il solver (M20/M33/H7) gestisce questo.

---

## (d) Tabella di adattamento dei vincoli

| Vincolo | Classificazione | Perche' |
|---------|----------------|---------|
| **H25** (Stage fuori giorni stage) | **AUTO-ADAPTA** | Legge `DailyActivityQuota.isStageDate` (stageHours > 0). Se stage scende, il numero di "stage days" nella DAQ cambia -> il filtro VRP e il vincolo H25 si restringono automaticamente. Nessun intervento. |
| **H26** (Lezione su giorno no-session) | **AUTO-ADAPTA** | Legge `SchoolSchedule.isNoSessionDate`. Un gruppo con 0 stageHours ma in-session resta in-session. Se i giorni di stage diventano no-session, la DAQ e' aggiornata -> auto. |
| **H23/H24** (Lab/Aula su giorno a quota zero) | **AUTO-ADAPTA** | Leggono `DailyActivityQuota.aulahours/labhours` per giorno. Se stage scade e le quote aula/lab cambiano nella DAQ -> auto. |
| **Filtro VRP stage** (`Lesson.isStageConfined`) | **AUTO-ADAPTA** | Filtra i timeslot disponibili per lezioni Stage unpinned. Meno giorni stage = meno slot disponibili per Stage. Automatico. |
| **M-CPS** (Curriculum post-stage target 6h) | **AUTO-ADAPTA** | `stage-end` derivato da `DailyActivityQuota.stageHours` (INPUT-derived). Se stageHours scende, lo stage-end anticipa -> target 6h post-stage si applica a un periodo piu lungo -> il vincolo si allenta. Automatico. |
| **M28** (Pomeriggi riservati allo stage) | **NEEDS RECALIBRATION** | Penalizza lezioni non-stage nei pomeriggi (slot >= 6). Peso 100/lezione. Se stage scende drasticamente, i pomeriggi sono "riservati" a uno stage che non c'e' piu -> spinge inutilmente le lezioni nei slot mattutini, riducendo la capacita effettiva. Il peso va ridotto o il vincolo disattivato se stage < soglia. **Azione:** disattivare M28 se stage < 100h per un gruppo, o ridurre il peso proporzionalmente. |
| **M20/M33** (Target di riempimento giornaliero) | **NEEDS RECALIBRATION** | `effectiveQuota` = DAQ `aulahours + labhours` - ore pinnate. Se stage scade e le ore spostate vanno in-grid, la DAQ aula+lab per quei giorni deve essere aggiornata. Se invece le ore spariscono, le quote scendono -> auto. **Azione:** aggiornare il file `calendario_*.csv` con le nuove quote aula/lab se referente conferma lo spostamento. Il vincolo stesso non cambia (legge la DAQ). |
| **M30/M31** (Lab day quota/contiguous) | **AUTO-ADAPTA** | Leggono DAQ `labhours` per giorno. Se le quote lab cambiano -> auto. Nessun intervento. |
| **M12** (Overflow oltre target end date) | **AUTO-ADAPTA** | I target end date derivano da `DailyActivityQuota` (cumulata expected-hours). Se le ore totali cambiano, il target end date si ricalcola -> auto (se la DAQ viene aggiornata). |
| **StagePattern** (date stage) | **NEEDS UPDATE** | Le date di inizio/fine stage vanno aggiornate nel seed se il periodo stage cambia. Impatta `isStageDate`, `isStageConfined`, M-CPS stage-end. **Azione:** referente fornisce le nuove date; aggiornare `DataGenerator` e il `calendario_*.csv`. |
| **Course.Stage.totalHours** | **NEEDS UPDATE** | Se lo stage scade, il corso "Stage" per ogni gruppo cambia `totalHours`. **Azione:** aggiornare `incarichi.csv` e `DataGenerator`. |

---

## Verdetto sintetico (3 righe)

1. **Rischio: MEDIO** (non ALTO perche' la capacita aggregata della griglia e' sufficiente in ogni scenario; non BASSO perche' M28 diventa un collo di bottiglia artificiale e la domanda a referente e' aperta).
2. **Il singolo driver piu grande e' la risposta alla domanda di referente:** le ore rimosse dallo stage diventano NUOVE lezioni (nuovi corsi/docenti necessari) oppure il totale ore del gruppo SCENDE semplicemente? Nel primo caso servono nuovi docenti e M28 va disinnescato; nel secondo caso e' solo un orario piu piccolo.
3. **La domanda da fare a referente:** "I corsi che perdono ore di stage: quelle ore diventano nuovi insegnamenti in aula (con nuovi docenti o piu' ore per docenti esistenti), oppure il monte-ore totale del gruppo scende e basta?" Questa domanda cambia tutto: ore nuove = problema di capacita docente + vincoli; ore che spariscono = nessun problema.

---

## Numero piu sorprendente

I gruppi **G02, G03, G01, G05** (quelli senza stage) hanno **solo 2 slot di slack** su 992 (990h di corso in 992 slot). Sono gia' al limite assoluto della capacita della griglia. Qualsiasi aggiunta di ore a questi gruppi (anche 1h) richiederebbe un cambiamento al calendario (piu' giorni o piu' ore/giorno). Questo non e' legato allo stage ma e' un vincolo strutturale preesistente.

```sql
SELECT sg.name, gc.total_grid_slots AS capacity, COALESCE(crs.total_h,0) AS course_h,
  gc.total_grid_slots - COALESCE(crs.total_h,0) AS slack
FROM studentgroup sg
JOIN (
  SELECT gs.studentgroup_id, SUM(gs.open_slots * wd.days) AS total_grid_slots
  FROM (SELECT gcr.studentgroup_id, gcr.dayofweek, gcr.maxslot-gcr.minslot+1 AS open_slots
        FROM groupcalendarrule gcr WHERE gcr.effectivefrom IS NULL) gs
  LEFT JOIN (SELECT EXTRACT(ISODOW FROM startdatetime)::int AS iso_dow, COUNT(DISTINCT DATE(startdatetime)) AS days
             FROM timeslot GROUP BY iso_dow) wd
  ON wd.iso_dow = CASE gs.dayofweek WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 4 WHEN 4 THEN 5 END
  GROUP BY gs.studentgroup_id
) gc ON gc.studentgroup_id = sg.id
LEFT JOIN (SELECT studentgroup_id, SUM(totalhours) AS total_h FROM course GROUP BY studentgroup_id) crs
ON crs.studentgroup_id = sg.id
ORDER BY slack;
```

---

## Cosa NON ho potuto misurare

1. **Pressione per-cell esatta senza solve:** l'analisi usa la distribuzione uniforme delle ore spostate sui giorni. Il solver potrebbe concentrare lezioni su certi giorni (per vincoli di docente, cadenza, compattezza), creando picchi localizzati non visibili dall'input. Solo un solve reale nelle nuove condizioni mostrerebbe se si generano nuovi buchi H17 o giorni oltre il cap H7.

2. **Capacita docente per ore nuove:** se le ore rimosse dallo stage diventano nuove lezioni, servono docenti reali. Ho i carichi attuali per docente e gruppo, ma senza sapere quali materie/orari verrebbero assegnati, non posso stabilire se i docenti esistenti hanno margine. La domanda a referente e' bloccante.

3. **Impatto su docenti condivisi tra gruppi:** T21 insegna in 13 gruppi, T08 in 10. Se un gruppo perde stage ma un altro no, la redistribuzione delle ore puo' creare conflitti H1. Impossibile da valutare senza la specifica di quali ore si spostano.

---

## Appendice: tutte le query usate

### Q1: Ore stage e in-grid per gruppo
```sql
SELECT sg.name, SUM(daq.stagehours) AS stage_h, SUM(daq.aulahours+daq.labhours) AS in_grid_h,
  COUNT(*) AS daq_days
FROM dailyactivityquota daq JOIN studentgroup sg ON sg.id = daq.studentgroup_id
WHERE daq.studentgroup_id IN (SELECT DISTINCT studentgroup_id FROM dailyactivityquota WHERE stagehours > 0)
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

### Q2: Capacita griglia per gruppo (GCR x giorni/weekday)
```sql
WITH wd AS (
  SELECT EXTRACT(ISODOW FROM startdatetime)::int AS dow, COUNT(DISTINCT DATE(startdatetime)) AS days
  FROM timeslot GROUP BY dow
)
SELECT sg.name, SUM((g.maxslot-g.minslot+1) * w.days) AS total_grid
FROM groupcalendarrule g JOIN studentgroup sg ON sg.id = g.studentgroup_id
JOIN wd w ON w.dow = CASE g.dayofweek WHEN 0 THEN 1 WHEN 1 THEN 2 WHEN 2 THEN 3 WHEN 3 THEN 4 WHEN 4 THEN 5 END
WHERE g.effectivefrom IS NULL
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

### Q3: Giorni scolastici per weekday
```sql
SELECT EXTRACT(ISODOW FROM startdatetime)::int AS dow, COUNT(DISTINCT DATE(startdatetime)) AS days
FROM timeslot GROUP BY dow ORDER BY dow;
```

### Q4: Ore totali corso per gruppo
```sql
SELECT sg.name, SUM(c.totalhours) AS total_h
FROM course c JOIN studentgroup sg ON sg.id = c.studentgroup_id
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

### Q5: Ore mercoledi per gruppo
```sql
SELECT sg.name,
  SUM(CASE WHEN EXTRACT(ISODOW FROM daq.date)=3 THEN daq.aulahours+daq.labhours ELSE 0 END) AS wed_in_grid,
  SUM(CASE WHEN EXTRACT(ISODOW FROM daq.date)=3 THEN daq.stagehours ELSE 0 END) AS wed_stage
FROM dailyactivityquota daq JOIN studentgroup sg ON sg.id = daq.studentgroup_id
WHERE daq.studentgroup_id IN (SELECT DISTINCT studentgroup_id FROM dailyactivityquota WHERE stagehours > 0)
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

### Q6: Giorni saturi (>= 8h in-grid)
```sql
SELECT sg.name,
  COUNT(*) FILTER (WHERE daq.aulahours+daq.labhours >= 8) AS full_8h_days
FROM dailyactivityquota daq JOIN studentgroup sg ON sg.id = daq.studentgroup_id
WHERE daq.studentgroup_id IN (SELECT DISTINCT studentgroup_id FROM dailyactivityquota WHERE stagehours > 0)
GROUP BY sg.name, sg.id ORDER BY sg.name;
```

### Q7: Slot pomeriggio (6-7) per gruppo
```sql
SELECT sg.name,
  SUM(CASE WHEN g.dayofweek IN (0,1,2,3,4) THEN
    GREATEST(0, (LEAST(g.maxslot,7)-GREATEST(g.minslot,6)+1))
    * CASE g.dayofweek WHEN 0 THEN 33 WHEN 1 THEN 33 WHEN 2 THEN 35 WHEN 3 THEN 36 WHEN 4 THEN 34 END
  END) AS afternoon_slots
FROM studentgroup sg
JOIN groupcalendarrule g ON g.studentgroup_id = sg.id
WHERE g.effectivefrom IS NULL
  AND sg.id IN (SELECT DISTINCT studentgroup_id FROM dailyactivityquota WHERE stagehours > 0)
GROUP BY sg.name ORDER BY sg.name;
```

### Q8: Gruppi non-stage con slack minimo
```sql
-- Vedere la sezione "Numero piu sorprendente" per la query completa.
```

---

## Verifica indipendente (main session, 2026-08-03)

I due numeri portanti sono stati ricalcolati dalla sessione principale contro il DB (profilo
`multistart`, read-only, durante il batch bad-seed in corso). Entrambi riprodotti esattamente:

- **Slack gruppi non-stage**: G02 / G03 / G01 / G05 = **2 slot** ciascuno (capacita 992,
  corso 990). Finding strutturale confermato.
- **Ore stage per gruppo**: **8 gruppi** (incluso G04 a 224h), **totale 2 309h**.

Discrepanza con il mandato (che citava "7 gruppi / 2 092h"): il dato reale e' 8 gruppi / 2 309h
perche' G04 ha 224h di stage non contati nel "7". Le conclusioni non cambiano (lo slack aggregato
resta positivo in ogni scenario). La pressione per-cell resta non verificabile senza un solve reale
nelle nuove condizioni, come dichiarato onestamente nella sezione "Cosa NON ho potuto misurare".
