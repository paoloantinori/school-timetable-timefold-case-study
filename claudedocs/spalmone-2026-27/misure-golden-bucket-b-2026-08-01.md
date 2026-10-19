# Misure golden BUCKET B (zai, 2026-08-01) - vincoli-tabs-matching, voci PARZIALE/DA VERIFICARE

Stesso schema di `misure-golden-bucket-c-2026-07-31.md`: per ogni voce del bucket B di
`vincoli-tabs-matching-2026-07-31.md`, 3 righe (regola scritta / misura golden meccanica / esito).
Golden = `src/main/resources/seed/lessons.csv` (10.542 lezioni post-trappole). Trappole escluse:
sabati, docente Fasullo/Fasutto/Stage, subject Stage, closed. Metodo: python ad-hoc (NON committato,
`/tmp/bucketb_measure.py`), riproducibile dalla descrizione. Read-only, nessun solve, nessun codice.

Criterio golden-first: golden rispetta -> gia' coperta / quasi-gratis; golden viola -> tensione da
notificare a referente (NON si modella un vincolo che vieta la pratica del golden).

## Sintesi (esito per voce)
| voce | esito | azione |
|---|---|---|
| (i) T03 libero-con-termo | GIÀ COPERTA | M-LA+H1, 0 conflitto |
| (ii) T16 3h R16 NON 6 | TENSIONE (golden ha 6h) | T9 referente; NON cap 3h |
| (iii) T14/T20 doppio | TENSIONE (attribuzione non matcha) | T10 referente |
| (iv) T06 info+gest | GIÀ COPERTA / rispettata | alias M-TR inerte |
| (v) Idraulici T22 6a ora lun | PRATICA CONFERMATA, non modellata | emergente da pin/quote |
| (vi) G11 2x5h giugno | PATTERN PRESENTE, "esame" no | conferma referente |
| (vii) T20 Figurino "quando vuoi" | GIÀ COPERTA + feed 471 | Figurino = teoria (1h/day) |

---

## (i) T03 "libero le mattine con lab termo, senza eccezioni"
- **Regola scritta:** T03 libero le mattine in cui c'e' lab termoidraulico, senza eccezioni.
- **Misura golden:** T03 e' teacher-of-record di 135 Laboratorio Termoidraulico (insegna lui
  il lab) + Sicurezza 96, Taglio Metalli 60, Tecnologia 42, Orientamento 40. Le celle (data,ora)
  con un lab termo sono 313; T03 insegna in 135 di esse = ESATTAMENTE i suoi lab (0 double-booking,
  H1 rispettato). In le altre 178 celle-termo (lab di T05 246 / T22 178) T03 NON insegna
  nulla (e' libero o assistente non-di-record).
- **Esito: GIÀ COPERTA.** T03 non e' mai in conflitto con un lab termo (insegna lui il termo,
  oppure e' libero). M-LA (conflitto assistente) + H1 (conflitto docente) lo modellano gia'. Nessuna
  tensione. (Il ruolo di assistente nei lab di T05/T22 non e' confermabile dal CSV, ma il
  conflitto non esiste comunque.) Cross-rif T6 (termo prime 4 ore).

## (ii) T16 "3 ore consecutive in R16 NON 6"
- **Regola scritta:** sessioni R16 (Confezione) di 3h consecutive, non 6.
- **Misura golden:** T16 fa solo Confezione (470) + Recuperi (25). Block-size per (gruppo,data):
  {3h: 121, 2h: 29, 1h: 5, 6h: 4, 5h: 7}. I 4 blocchi da 6h sono **G05 lunedi' 26/04, 03/05,
  10/05, 17/05 (tutti 8-13)**; i 7 da 5h sono **G14 lunedi' 03-04/2027 (8-12, 6 casi) + G05
  09/06**. Verificato: ore veramente consecutive (non 3+3 con pausa).
- **Esito: TENSIONE -> T9 referente.** La regola "3 NON 6" e' VIOLATA dal golden (4x6h + 6x5h
  concentrati in periodi modulo di G05 apr/mag e G14 mar/apr). Golden-first: NON si modella un
  cap "max 3h R16" (vieterebbe la pratica del golden). Sono quasi certamente giorni intensivi
  di modulo intenzionali; referente conferma (T9). Il run deve poterli produrre.

## (iii) T14 / T20 doppio 2a/3a MODA R16 (100+100 / 100+85)
- **Regola scritta:** T14 doppio R16 2a/3a MODA (100+100h), T20 (100+85).
- **Misura golden:** T14 insegna **Lingua Italiana** (G05 80h, G09 65h), NON
  Confezione. T20 insegna **Figurino/Tecnologia tessile/Storia della moda/Merceologia** (G05
  150h, G09 105h), NON Confezione. Nessuno dei due e' teacher-of-record di R16; i budget
  100+100/100+85 non sono verificabili (il doppio/co-docenza non e' docente di record nel golden).
- **Esito: TENSIONE -> T10 referente.** L'attribuzione "doppio R16" del tab non matcha il golden
  (T14 = Italiano, T20 = materie MODA). Chi e' il vero doppio R16? I budget orari
  del doppio non sono modellabili dal CSV (limitazione strutturale, non un gap nostro).

## (iv) T06 "non 1 ora info + 1 ora gest, ma stessa materia"
- **Regola scritta:** non 1h Informatica + 1h Gestione aziendale adiacenti (vanno trattate come
  un'unica materia).
- **Misura golden:** T06 insegna Informatica (450) + Gestione aziendale (76) + Competenze
  Digitali (75). Su 509 suoi giorni di lezione, **0 (zero) hanno entrambe** info+gest. Il pattern
  1h+1h non si presenta MAI per lui. (Esistono 81 giorni group-level con entrambe le materie, ma
  tenute da docenti diversi: varieta' di materia normale, non il caso T06.)
- **Esito: GIÀ COPERTA / rispettata.** La nota di T06 e' rispettata (0/509). L'alias hook
  M-TR (info+gest = una materia, TASK-468) resta inerte (non c'e' 1h+1h da coalescere per lui).
  Conferma in T8 (bullet T06 aggiornato col dato).

## (v) Idraulici OIT: conteggi per-docente, T22 16 lunedi' + 6a ora lunedi'
- **Regola scritta:** piani giorni-lab OIT con conteggi per idraulico; T22 16 lunedi', 6a ora
  lunedi' 13-14 (aggiunta da referente).
- **Misura golden:** termo FB = T05 (Progettazione Termoidraulica 246), T03 (Lab 135),
  T22 (Lab 178). T22: 178 lezioni tutte Lab Termo, **21 lunedi' distinti** (tab dice 16),
  e insegna la **6a ora (13:00) in 20 lunedi'** (tutti Lab Termo). Pratica confermata.
- **Esito: PRATICA CONFERMATA, non modellata esplicitamente.** La 6a-ora-lunedi' di T22 e'
  nel golden (20 volte) ma non e' un vincolo (emerge da pin/quote); il count per-docente (21 vs 16)
  non e' una regola. Se il run drifta da questa pratica, un pin la cristallizzerebbe. Nessuna
  tensione (documentato).

## (vi) G11 "2 giorni da 5 ore prep esame a giugno"
- **Regola scritta:** 2 giorni da 5h di prep esame a giugno per 3 OIT.
- **Misura golden:** G11 giugno 2027 = 18 lezioni su 4 date. Due giorni da 5h: **07/06 (5h tutte
  Laboratorio Termoidraulico)** e **08/06 (4h Lab Termo + 1h Disegno Edile)**. Il pattern "2 giorni
  concentrati da 5h a giugno" C'E', ma il contenuto e' lab termo, non una materia "esame" (concetto
  non presente nel dato).
- **Esito: PATTERN PRESENTE, "esame" non modellato.** I 2 giorni concentrati da 5h (7-8 giugno)
  ci sono e sono termo; il "prep esame" e' un'interpretazione della scuola (l'etichetta "esame" non
  e' nel seed). Nessun vincolo nuovo; conferma referente se questi sono i giorni prep-esame (T6 vicina).

## (vii) T20 "figurino quando vuoi (non necessariamente 2 ore)"
- **Regola scritta:** Figurino flessibile (1h o 2h, non forzato a 2h).
- **Misura golden:** Figurino = 130 lezioni, tutte T20. Block-size per (gruppo,data): {1h: 107,
  2h: 9}. Nessun blocco 3h+. Quindi **107/116 giorni (92%) Figurino a 1h**, solo 9 giorni a 2h.
- **Esito: GIÀ COPERTA + feed TASK-471.** "Quando vuoi" rispettato (Figurino per lo piu' 1h/day).
  Per M-TR/471: il golden tratta Figurino come TEORIA (1h/day nel 92% dei giorni), confermando il
  default AC#2 (Figurino nel set teoria, non esente). Dato diretto per il workbook TASK-471
  (politica blocco teoria: Figurino = 1h preferito).

## Metodo + riproducibilita'
Misura python ad-hoc (`/tmp/bucketb_measure.py`, NON committato: read-only, niente codice nuovo).
Riproducibile: caricare `seed/lessons.csv`, escludere trappole (closed/sabato/Fasullo/Stage),
computare per voce. Verifica indipendente dei 2 finding load-bearing (ii T16 6h veramente
consecutivi, iv T06 insegna entrambe): CONFIRMED. Dati raw a richiesta nello script.
