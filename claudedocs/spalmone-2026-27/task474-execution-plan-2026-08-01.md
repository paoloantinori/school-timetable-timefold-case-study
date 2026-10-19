# TASK-474 explainability vincoli - piano di esecuzione (2026-08-01)

> Valutazione + prep. L'esecuzione (i ~150 edit) e' demandata a una SESSIONE FRESCA: volume
> alto, il blocco Origine deve essere UNIFORME (disegnato una volta), e la mappatura
> fonte-per-vincolo e' judgment non puro mechanical. Questo doc fa il lavoro di design +mapping
> upfront affinche' la sessione fresca sia fill-in, non research.

## Verdetto: sessione fresca

- **Volume**: 50 vincoli registrati (.asConstraint), di cui alcuni split (M-LA.1-4, H1/H2/H3 .1-.3,
  M30.1/.2, H4/H5 split) ~55-60 blocchi logici. Ognuno x 3-4 artifact (javadoc + constraints.ts +
  locale en + locale it) = **~150-200 edit**.
- **DoD**: /simplify + /code-review high su un diff da ~200 edit + test.sh + ui-typecheck +
  ui-build. Pesante, merita contesto fresco.
- **Buona notizia**: le quote verbatim sono GIÀ estratte in
  `vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` (110 righe, tab Vincoli Classi/Docenti/DA
  ANALIZZARE). Quindi la sessione fresca e' "mappa + scrivi", non "research + scrivi".

## Formato del blocco Origine (UNIFORME)

Javadoc (SchoolScheduleConstraintProvider/BaseConstraintProvider, sul metodo del constraint):
```
 * <p><b>Origine.</b> <quote verbatim o dichiarazione>. <fonte precisa>.
```
Tre casi, onestamente distinti dal colore della fonte:
1. **Documento scuola (verbatim)**: `Origine: "<quote>" - Spalmone...xlsx, tab <Tab>, riga <n>`
   (quote tra virgolette, esente no-em-dash perche' verbatim).
2. **Pratica del golden (no doc)**: `Origine: pratica del golden, nessun documento scuola;
   <measure doc>.json/md (misure <X>)`.
3. **Strutturale (no doc)**: `Origine: vincolo strutturale, nessun documento scuola; introdotto
   TASK-<nnn> (<ragione fisica/logica>).`

Sito (constraints.ts + locales en/it): stessa stringa Origine; la cornice localizzata, il quote
verbatim invariato. Campo nuovo in constraints.ts per ogni voce (es. `origin: "..."`), renderato
nel DocsPage sotto la descrizione.

## Mappa vincolo -> categoria fonte (prep fatto, da riempire in sessione)

### A. Documento scuola verbatim (xlsx - quote pronte nell'extract)
| vincolo | quote verbatim | tab, riga extract |
|---|---|---|
| H7 | "Non piu' di 6 ore di lezione al giorno per ogni docente" | DA ANALIZZARE, 108 |
| M24 (T05 early) | "MER GIOV LUN 8,00-14,00 No lezione... AL MASSIMO UN'ORA" | Vincoli Docenti, 45 |
| M25 (T05 free) | "VENERDI' Libero a casa" | Vincoli Docenti, 46 |
| M26 (Sicurezza) | "SICUREZZA Ore con le 3° (+ 2° OIT) prima di Stage" | Vincoli Docenti/DA, 42/92 |
| M27 (T20 subject period) | "Subito merceologia in 1° e 2°" / "3° fa tecnologia e merceologia ultime" | DA ANALIZZARE, 98/101 |
| TASK-469 (T11 curr) | "CON II FC E III OIT E III FA 6 ore dopo stage per curriculum (ANCHE 2 CONSECUTIVVE)" | Vincoli Docenti, 50 |
| TASK-472 (T16 3h) | "3 ore consecutive in R16 NON 6" | Vincoli Docenti/DA, 66/81 |
| TASK-475 (doppio) | T14/T20 "Libera/o per doppio in 2°(100) e 3° moda (100/85)" | DA ANALIZZARE, 96/97 |
| M-TR (theory adjacent) | "Non ci devono essere 2 ore vicine della stessa materia teorica" | DA ANALIZZARE, 104-105 |
| H5/availability (T10) | "Libero il Giovedi mattina per LES e G14 9,00-17,00" | Vincoli Docenti, 72 |
| Bentivoglia/T12/T24/T05 availability | (quote respective) | Vincoli Docenti, 40/51/53 |

### B. Calendario xlsx (per-group tab -> seed calendario CSV)
M12 (overflow target end), M20 (daily quota), M28 (afternoon stage), M30/M31 (lab day), e tutti i
DailyActivityQuota-derived: origine = `Copy of Calendario scolastico 2026_2027.xlsx, tab <gruppo>`
(righe Totali Ore/STAGE/Lab.) -> seed/calendario_*.csv.

### C. Pratica del golden (no doc, misura)
M32 (template deviation), M34/M35 (cadence: golden-cadence-reference.json), M15 (consecutive),
S17 (block pair), M21 (2h cap), S13 (weekly stability), H28 (no return). Origine = pratica golden
+ measure doc.

### D. Strutturale (no doc, onesta dichiarazione)
H0, H1, H2, H3, H5, H6, H11, H17, H29, H-M2, M3, S1, S7, S8, S10, S11, S16. Origine = vincolo
strutturale + TASK introduttivo.

## Workflow sessione fresca
1. Definire il campo `origin` in constraints.ts + il render DocsPage (1 spike frontend).
2. Per ogni vincolo (A,B,C,D): aggiungere blocco Origine javadoc + riga constraints.ts + locale
   en/it. Procedere per categoria (A prima, quote pronte; D ultima, dichiarazione standard).
3. Spot-check 5 vincoli (AC#4) contro la fonte citata.
4. DoD: /simplify + /code-review high + test.sh + ui-typecheck + ui-build verdi.
