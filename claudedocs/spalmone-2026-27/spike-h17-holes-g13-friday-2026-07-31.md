# SPIKE: diagnosi dei 18 buchi H17 del run ON (TASK-451 A/B) - 2026-07-31

Read-only sui dump catturati (`task451-results/ab-firsthour-on-run{1,2}/`, `certification-2026-07-30d/`).
Affila la decisione di maintainer sull'adozione di (A): i 18 buchi H17 sono un costo del trade-off, vanno capiti.

## Consegna (3 righe)
- **I 18 buchi H17 sono UN SOLO pattern, non un H17 sparso**: gruppo **G13**, ogni **venerdì**, sempre
  allo **slot 11 (11:00)**, 18 occorrenze (tutti i venerdi' G13 nell'orizzonte). occupied=[8,9,10,12,13], hole=[11].
- **G13 venerdì = 5h lab meccanica** (quota labhours=5 ogni ven; aulahours=0). LM (Lavorazioni meccaniche)
  e' lab -> M21-esente, quindi 5h LM compatte in un blocco sono VALIDE (non violate M21).
- **Il golden e 30d (OFF) fanno il blocco compatto mono-docente (T10 LM 8-12, 0 gap); il run ON lo
  ROMPE splittando T10 (8,9,12) + T15 (10 Cad, 13 LM) con gap a 11.** E' un artefatto di ricerca,
  non un vincolo hard (la compattazione e' feasible: golden e 30d la fanno).

## Evidenza sui raw (G13 venerdì 2026-09-18, stesso giorno nei 3 bracci)
| braccio | slot 8 | 9 | 10 | 11 | 12 | 13 | gap? |
|---|---|---|---|---|---|---|---|
| golden | LM/T10 | LM/T10 | LM/T10 | LM/T10 | LM/T10 | - | no (compatto, mono-docente) |
| 30d (OFF) | LM/T10 | LM/T10 | LM/T10 | LM/T10 | LM/T10 | - | no (riproduce il golden) |
| run1 (ON) | LM/T10 | LM/T10 | Cad/T15 | **GAP** | LM/T10 | LM/T15 | SI (slot 11) |

Quota G13 2026-09-18: aulahours=0, labhours=5, stagehours=0 (verificato su 3 venerdi': sempre 5 lab).

## Perche' (A) lo causa (meccanismo)
(A) riserva il blocco slot0-1 (8-9) per l'anchor golden di G13 ven (T10 LM) - corretto, e run1 lo
fa (8,9 = T10 LM). MA poi la LS colloca le 3 ore lab residue NON continuando il blocco T10 LM,
bensie' inserendo Cad/T15 a slot 10 e LM a 12 (T10) + 13 (T15), lasciando 11 vuoto.
La LS converge a un ottimo locale che spezza il lab-day G13-ven su 2 docenti. (A) sbilancia la
traiettoria di ricerca; il flag OFF (30d) trova il blocco compatto mono-docente.

## Perche' HoleRepair / StudentDayCompaction non lo chiudono
Chiudere il gap a 11 richiede RI-COORDINARE 2 docenti (consolidare a T10 per tutto il blocco, o
riempire 11 con un docente libero li'). HoleRepair fa mosse locali (shift lezioni) e non riassegna il
docente di un lab-day intero; StudentDayCompaction e' un left-anchor (non colma gap mid-day da split
multi-docente). Nessun repair esistente targettizza "compatta lab-day a docente unico". => il gap resta.

## Cura a valle (NUOVA, non esiste ancora; NON stanotte)
1. **Lab-day single-teacher contiguity**: un repair (o vincolo medium) che, per un (gruppo, giorno) con
   >=N ore lab dello stesso tag, le consolidi su un unico docente in blocco contiguo (come fa il golden).
   Famiglia dei lab (TASK-449 mono-room-tag, M-TC tag-capacity) ma ortogonale (docente, non stanza).
2. Oppure un pass H17-piu-forte / LS mirato sui lab-day. Da valutare al checkpoint, non adottare ora.
3. NOTA: e' la stessa famiglia del room-null acconciatura (scarsita' congiunta lab): il lab-day G13-ven
   e' una risorsa coordinata (docente T10 + stanza meccanica), e la ricerca non la coordina.

## Conseguenza sulla decisione (A) di maintainer
I 18 buchi H17 NON sono un difetto strutturale di (A): sono **una singola regression di ricerca su un
pattern specifico (G13-venerdì lab meccanica, 5h)**, stabilmente causata da (A), ma **feasible da
chiudere** (golden/30d lo fanno) e **curabile a valle** (lab-day single-teacher contiguity). Quindi:
- Il trade-off (A) "16 late-start MEDIUM <-> 18 mid-day gap HARD" e' reale ma il lato gap e' **mitigabile**.
- (A) resta netto-positivo (hard -65, boxed 3->0, room-null 167->90, ricorrenza +1.3pp, difetto cronico
  G11-ven azzerato); il costo gap e' un work-item di ricerca curabile, non un veto all'adozione.

## Riferimenti
- (A) A/B run1/run2: `task451-first-hour-slot-2026-07-30.md` sezioni "A/B laptop run1/run2".
- Famiglia lab/scarsita': QUADRO "LA TRIPLETTA", spec scarcity-aware-assignment, TASK-449/M-TC.
