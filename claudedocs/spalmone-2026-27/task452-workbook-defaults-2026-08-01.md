# TASK-452: default workbook delle 4 letture ESITO 2 (deposito finestra-golden, 01/08)

Direttiva (CLAUDE.md "la soluzione gira sul SOLO INPUT, il golden e' temporaneo"): le 4 letture
golden-runtime ESITO 2 del census (`task452-deducibility-census-2026-08-01.md` #2/#3/#6/#7) sono
pratiche della scuola NON deducibili dagli input, quindi vanno **dichiarate nel workbook**. Finche'
il golden esiste possiamo estrarre il **default** (cio' che la scuola ha fatto nel 2026/27); quando
sparira', non sara' piu' possibile. Questo doc deposita quei default ORA, come CSV draft
(`origine=sistema`, valori = estrazione golden), pronti perché la scuola li confermi o corregga.

Tutte e 4 sono flag-OFF/self-gate (NON in produzione oggi): il deposito e' lavoro di finestra, non
di adozione. Nessuna lettura golden resta a runtime per queste quando il workbook le avra' assorbite
(la #1 e' gia' patchata opt-in, vedi `handoff-2026-08-01-alba-task452.md`).

## I 4 depositi

| # | Lettura (TASK) | File draft | Grain (una riga =) | Righe | Cosa chiedere alla scuola |
|---|---|---|---|---|---|
| 2 | mono-room cells (TASK-449) | `task449-monoroom-cells-draft.csv` | (gruppo, tag, weekday, startSlot, blockHours) | 31 | per ogni gruppo e lab monostanza: giorno + fascia (inizio + durata) del blocco settimanale. Confermare o correggere la cella modale. |
| 6 | slot0 anchor (TASK-451) | `task451-slot0-anchors-draft.csv` | (gruppo, weekday) -> docente | 67 | per ogni (gruppo, giorno): quale docente apre la prima ora? (Pratica nuova: vedi sotto.) |
| 7 | co-presenza depth (TASK-464) | `task464-copresence-depth-draft.csv` | (tag, weekday, slotStart) -> depth + gruppi | 91 | quali gruppi fanno lab insieme (co-presenza ospite) e a quale profondita'? (Gia' toccata da Q14/Q19/Q20.) |
| 3 | cadenza M34/M35 (TASK-425) | **NON duplicato**: `data/golden-cadence-reference.json` e' gia' il deposito (258 pair, `max` + `active_weeks`) | (gruppo, materia) -> max settimanale + settimane attive | 258 | condizionale: volete i capped settimanali per materia? Se sì, dichiarate max + settimane attive. |

## Come rigenerare i CSV (logica = census, read-only)
Script: `/tmp/extract_task452_workbook_drafts.py` (monouso, non nel repo). Reuse la STESSA logica
del census (`/tmp/measure_task452_deducibility.py`, verificata combaciare col runtime):
- **#2 mono-room**: modal `(startSlot, hours)` per (gruppo, tag, weekday) dai subject del tag in
  `seed/lessons.csv` (specchio di `HybridPhase1aService.buildMonoRoomCadence`). `stability` =
  weekCount/totalWeeksThisWeekday (quanto la shape modale e' stabile).
- **#6 slot0 anchor**: docente modale alle 08:00 per (gruppo, weekday), open rows only (specchio di
  `ConstrainedTeacherAllocator.loadGoldenSlot0Anchors`). `stability` = modalN/totalN.
- **#7 co-presenza**: gruppi distinti per (tag, weekday, slotStart), depth>=2 only (specchio di
  `loadGoldenCoPresenceDepth`). Subject->tag da `subjects.csv` (single-tag).

## Come leggere i default
- `origine=sistema` su ogni riga: il valore viene dall'estrazione golden, NON e' stato confermato
  dalla scuola. E' il default da proporre.
- `stability` alta (>=0,8) = pratica consolidata nel 2026/27 (alta priorita' di conferma); bassa
  (<0,5) = la scuola e' meno regolare (la riga e' un suggerimento debole, da non forzare).
- I CSV sono **draft**: la scuola puo' confermare (`origine=scuola`), correggere o cassare righe.
  Veicolo finale = workbook TASK-445 (il grain di ogni CSV mappa su una sua sezione).

## Nuovezza delle domande (verificato)
- #2 (mono-room cells): NON nuova (colonna workbook c 2026-07-30; veicolo = popolare LabRotation).
- #7 (co-presenza): NON nuova (Q14/Q19/Q20 + thread "44 sovrapposizioni"; la risposta di referente
  "una classe in laboratorio" e' in tensione con depth>=2, che l'esenzione H2 + cap H-COPRESENCE governano).
- #3 (cadenza): NON nuova (condizionale, self-gate; 2026-07-30).
- #6 (chi apre il giorno): **NUOVA**. Legata a TASK-451 (parcheggiato). Il default (mappa slot-0
  modale, 48/67 celle stability>=0,5) e' gia' depositato qui; la domanda si fa se TASK-451 si riattiva.

## Collegamenti
- Census quantitativo: `task452-deducibility-census-2026-08-01.md`. Patch #1 (ESITO 1, opt-in):
  `handoff-2026-08-01-alba-task452.md`. Census qualitativo: `censimento-dipendenze-golden-2026-07-30.md`.
- Lavoro successivo (m-13): (i) popolare LabRotation dal CSV #2, (ii) depositare gli anchor #6 nel
  workbook, (iii) decidere co-presenza #7 vs risposta referente, (iv) M34/M35 condizionale, (v) flip del
  default della patch #1 dopo run di verifica.
