# FASE 3 - ESPERIMENTO TAG in-harness (+ PROPOSTA, no file protetti), VERDICT (2026-07-28 notte)

**ESITO: esperimento MISURATO.** I 256 lab room-null sono H2-capacity-constrained (la
phase-5 LS ne chiude 0 da sola), NON un puro errore di tag. De-taggiare le materie TEORICHE
chiude una quota crescente (83 -> 176 delle 256) perche' le manda in aula (fillable) E libera
l'officina per i lab pratici genuini (cascade). R17 capacity 0->20 ha ZERO effetto sulle
256 (le 40 ore saldatura sono gia' in R17; la capacita' e' M3 medium, non hard). laptop
MISURA, NON adotta: la patch reale a DataGenerator.getRoomTags/rooms.csv e' PROPOSTA per maintainer.

## Come (in-harness, NESSUN file protetto toccato)

RoomPhaseSnapshotTest (export mode, gate-l5-m36/export.json.gz, tutti i gruppi, phase-5 LS
120s) + 3 hook env-gated applicati DOPO loadFromExport e PRIMA di solver.solve (cache vuota):
- `PLANNER_TEST_ROOM_TAG_UNTAG="subj1,subj2"`: cancella requiredRoomTags dai Course matching
  (-> non-lab/aula) + NULLa la room delle lezioni de-taggate ancora in una stanza lab (sennò
  la room e' fuori dal nuovo value range aula -> Timefold IllegalState; fix verificato).
- `PLANNER_TEST_ROOM_R17_CAP=20`: override capacity di R17.
- print LAB REAL room-null before/after (`countLabRealRoomNull`, M36 filter + isLab).

CAVEAT (onesta'): `Lesson.isLab()` legge il boolean seed-time `course.isLab`, NON
requiredRoomTags, quindi i de-taggati restano conteggiati come "lab" nel bucket. I de-taggati
pero' vanno in value range aula e la LS li riempie (~97%, come i 291->8 non-lab), quindi il
residuo misurato e' lab genuino + cascata. "Chiuse delle 256" = 256 - residuo.

## Tabella (tutti i gruppi, phase-5 LS 120s, M37 nel provider)

| correzione | de-taggati (su 256) | residuo lab room-null | chiuse delle 256 |
|---|---|---|---|
| baseline (nessuna) | 0 | **256** (LS fills 0) | 0 |
| R17 cap 0->20 | 0 | **256** (no effect) | 0 |
| de-tag ProgTerm + TaglioMet | 53 (41+12) | **173** | 83 (53 aula + 30 cascata) |
| + Servizio stilistico | 106 (+53) | **80** | 176 (106 aula + 70 cascata) |

I 256 si spalmano cosi' (da export.json.gz): Lavaggio 72 (acconciatura), Laboratorio
Termoidraulico 55 (termoidraulica), Servizio stilistico 53 (acconciatura), Progettazione
Termoidraulica 41 (termoidraulica, TEORIA), Barberia 17 (acconciatura), Taglio Metalli 12
(meccanica, TEORIA), Lavorazioni meccaniche 6 (meccanica). (Tecnologia tessile = 0, non move.)

## Lettura

1. **Non e' (solo) un errore di tag.** Le 256 sono H2 room-conflict al picco: ogni tag ha 1-2
   stanze (acconciatura 1, termoidraulica 1, meccanica 1, moda 1, informatica 2) e al picco
   sono sature. La phase-5 LS da sola ne chiude 0.
2. **De-taggiare le TEORICHE funziona (doppio effetto).** ProgTerm/TaglioMet/Servizio
   stilistico sono materia teorica mappata sull'officina del rispettivo lab pratico (collisioni
   golden: Laboratorio vs Progettazione 50+, Confezione vs Tecnologia 24, Taglio vs
   Lavorazioni 12). De-taggarle: (a) le manda in aula -> chiuse direttamente; (b) libera
   l'officina -> i lab pratici genuini (Laboratorio Termoidraulico, Lavaggio, Barberia,
   Lavorazioni meccaniche) la occupano (cascata). Servizio stilistico e' la leva piu' grossa
   (libera R11 per Lavaggio 72 + Barberia 17).
3. **E' PARZIALE.** Anche de-taggando tutte e 3, restano ~80 lab room-null genuini
   (Laboratorio Termoidraulico, Lavaggio residui): ancora capacity-constrained. Quindi
   l'allocatore lab (TASK-449) resta rilevante, ma su scope ridotto.
4. **R17 cap non c'entra con le 256.** Le 40 ore saldatura sono gia' in R17 (cap e'
   M3 medium, non hard). Darle capacita' 20 ridurrebbe solo la penalita' M3 medium, non le 256.

## PROPOSTA (NON applicata, ratifica maintainer) - patch a DataGenerator.getRoomTags / rooms.csv

DataGenerator.java:1109-1125 `getRoomTags`: rimuovere i prefissi delle materie TEORICHE che
il golden non mette in officina (golden-first, vedi TASK-379):
- `progettazione termoidraulica` -> via (condivide tag `termoidraulica` con `laboratorio
  termoidraulico`; teoria, golden collision 50+).
- `tecnologia tessile` -> via (condivide `moda` con `confezione`; teoria, golden collision 24).
- `taglio metalli` -> via (condivide `meccanica` con `lavorazioni meccaniche`; teoria, golden
  collision 12). [valuta se teoria o pratico: da confermare scuola]
- `servizio stilistico` / `servizio tecnico e stilistico` -> AMBIGUI (acconciatura). La leva
  piu' grossa (libera R11), ma teoria-vs-pratico e' una decisione della SCUOLA.
  Lasciarli e demandare, OPPURE de-taggarli se la scuola conferma teoria.
rooms.csv: R17 capacity 0 -> 20 (per la penalita' M3 medium, NON per le 256;così la
stanza saldatura non e' fittizia cap-0). Capacita' realistica da confermare.

Se maintainer adotta i de-tag teorici, le 256 scendono a ~80 -> TASK-449 (allocatore lab) resta ma
su scope molto minore. Annotare sul task 449.

## Confine di mandato

laptop MISURA, NON adotta. DataGenerator.java e seed/*.csv NON toccati (l'esperimento e' tutto
in-memory nell'harness). La patch reale resta PROPOSTA in questo VERDICT per maintainer domattina.
DoD piena (review + suite) sul diff dell'harness.
