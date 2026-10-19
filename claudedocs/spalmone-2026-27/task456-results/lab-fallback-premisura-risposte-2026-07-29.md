# TASK-456 Tappa B: pre-misura risposte attese con lab-fallback ON (2026-07-29)

Pre-misura delle DUE risposte ancora attese dalla scuola, nel quadro NUOVO del lab-fallback
ATTIVO (w=1000), perche' la dinamica e' cambiata. Domanda (Fedora/maintainer): se G14 non occupa
il laboratorio e/o G12 fa Servizio stilistico/tecnico altrove, **quante delle 182 ore di
laboratorio senza stanza si chiudono, e quante restano davvero infattibili**.

Tutto in-harness (`RoomPhaseSnapshotTest` export-mode sul cert `task447-results/cert/export.json.gz`,
all-groups, phase-5 LS, lab-fallback ON w=1000). Snapshot = trend su UNA traiettoria, non adozione.
Riproducibile: identico a 90s e 180s (non e' rumore LS).

## I corsi da-taggati (dal cert, lab-tagged hours)

- **G14** (60h lab-tagged): Confezione (moda, 30h) + Servizio stilistico (acconciatura, 30h).
- **G12** (76h delle risposte): Servizio stilistico (acconciatura, 30h) + Servizio tecnico e
  stilistico (acconciatura, 46h). Restano lab genuini: Barberia (45h) + Informatica (40h).

L'hook `PLANNER_TEST_ROOM_TAG_UNTAG_GROUP="GROUP=>subj1,subj2;..."` de-taggia (gruppo, materia):
svuota `requiredRoomTags` E azzera `course.isLab` (cosi' l'ora smette di contare come lab e il
residuo LAB room-null resta pulito sui 182 genuini), poi nulla le aule-lab ormai incompatibili.

## MISURA (lab-fallback ON w=1000; residuo = ore LAB senza stanza GENUINE rimaste)

| risposta | LAB rn before | LAB rn after (RESIDUO) | riduzione vs 182 | di cui "capacity-freed" |
|---|---|---|---|---|
| baseline (no de-tag) | 182 | 182 | 0 | 0 |
| (a) G14 60h | 154 | 154 | -28 | **0** |
| (b) G12 76h | 173 | 144 | -38 | **29** |
| (c) entrambi | 145 | **120** | **-62** | 25 |

- "riduzione vs 182" = quante delle 182 ore lab senza stanza non sono piu' lab-room-null (totale:
  ore ricollocate altrove + capacita' liberata per altri lab).
- "capacity-freed" = quante ore lab GENUINE (altre classi, es. Lavaggio) trovano stanza perche'
  si e' liberata capacita' nel lab contendente. E' la metrica che dice se la risposta AIUTA I
  LABORATORI DEGLI ALTRI o solo se stessa.

## LETTURA

1. **Il residuo e' MONOTONO**: 182 -> 154 -> 144 -> 120. Piu' risposte arrivano, meno lab restano
   senza stanza. La metrica "capacity-freed" (25 vs 29 nel caso c) NON e' monotona, ma e' un
   artefatto della LS (in c ha 97 non-lab da piazzare vs 37 in b e spende budget li'): il residuo
   e' la metrica onesta ed e' pulito. Stabile a 90s e 180s.
2. **G12 libera capacita' REALE** (29 lab genuini chiusi): le sue 76h di acconciatura
   contendevano R11 con Lavaggio/Servizio stilistico/Barberia delle altre classi;
   de-taggate, 29 ore di quei lab genuini trovano stanza.
3. **G14 NON libera capacita' per gli altri (0 capacity-freed)**: le sue 30h di acconciatura
   (Servizio stilistico) liberate non coincidono, timeslot per timeslot, con i buchi dei lab
   genuini (Lavaggio). La riduzione -28 e' SOLO ricollocazione delle ore G14 stesse in aula (con
   lab-fallback ON le piazza: 13 finiscono in lab come shelter, il resto in aula). La risposta
   G14 aiuta G14, non gli altri laboratori.
4. **Residuo infattibili dopo tutte le risposte (c) = 120**. Dominato da Lavaggio (~75, acconciatura
   GENUINA: pratica su cliente, non de-taggabile) + Laboratorio Termoidraulico + Barberia +
   Lavorazioni mecaniche. E' lo stesso residuo strutturale del doc sensibilita' (le "82 residue"
   capacity-bound): **nessuna risposta di tagging lo chiude, serve un'altra stanza acconciatura o
   una riclassificazione**.
5. Limite: 120 e' un LIMITE SUPERIORE degli infattibili (la phase-5 LS undercount: in c ha trovato
   solo 25 capacity-freed perche' occupata sui 97 non-lab). Un solve intero chiuderebbe di piu';
   il numero definitivo e' gated all'adozione (lab-fallback ON in produzione + full solve).

## DOMANDA PER LA SCUOLA (quando referente risponde)

- **G12** (Servizio stilistico/tecnico -> aula): VAL 76h, libera 29 ore di lab genuini per altre
  classi. Risposta ad alto impatto sui laboratori altrui.
- **G14** (non occupa il lab): VAL 60h, ma libera 0 capacita' per gli altri (aiuta solo se stessa).
  L'impatto sui lab altrui e' nullo nel phase-5; il valore reale (se la scuola vuole che G14
  condivida il lab titolare via M-LA) e' un modello diverso, non misurato qui.
- **Nessuna delle due chiude il residuo Lavaggio (~75)**: e' capacity-bound strutturale. La domanda
  DUE al colloquio resta: (a) le materie de-tag/retag, (b) un'altra stanza acconciatura per Lavaggio.

## RICONCILIAZIONE con l'inferenza golden (Fedora, commit 0ad379ea, domande-brevi.md)

In parallelo Fedora ha inferito dal golden la STRUTTURA delle 44 sovrapposizioni residue, e
ristringe le due risposte. La mia misura e quella inferenza SI ALLINEANO, ma a granularita'
diverse (importante per non sovrainterpretare i 29 di G12):

- **G14**: il golden dice 30h in acconciatura, **in TUTTE E 30 c'e' un'altra classe in
  contemporanea (100%) -> mai esclusiva**. Non toglie il lab a nessuno. Questo CONFERMA il mio
  "0 capacity-freed": de-taggare G14 non libera capacita' per i lab altrui (G14 e' sempre gia'
  ospite). Fedora considera G14 risolta dai dati.
- **G12**: il golden dice 121h in acconciatura, di cui **solo 12 in contemporanea (10%)**,
  tutte di giovedi' (T26 mentre T01 ha un'altra classe). La reale contesa G12 e'
  ~12h, NON 76h. Il mio de-tag di 76h (Servizio stilistico + tecnico) e' lo scenario **"G12
  lascia l'acconciatura del tutto" = LIMITE SUPERIORE** (29 lab genuini liberati). La risoluzione
  realistica che la scuola dara' (spostare solo le 12 ore contese del giovedi', non tutte le 76)
  libererebbe MOLTO MENO. I 29 sono quindi un upper bound, non la stima puntuale.
- **In entrambi i casi il residuo strutturale domina**: Lavaggio (~75, acconciatura genuina)
  resta fuori portata di qualunque risposta di tagging. La domanda DUE al colloquio resta: (a)
  le materie de-tag/retag (dove ora sappiamo che il vero bottleneck G12 sono 12 ore, non 76),
  (b) un'altra stanza acconciatura per Lavaggio.

FOLLOW-UP OPZIONALE (se maintainer lo vuole): misura "12h-only" de-taggando solo le lezioni
G12 di giovedi con T26 (hook per gruppo+materia+timeslot/docente), per la stima
puntuale invece dell'upper bound a 76h.

## COME RIPRODURRE

```
COMMON="PLANNER_TEST_ROOM_PHASE=1 PLANNER_TEST_ROOM_PHASE_EXPORT=claudedocs/spalmone-2026-27/task447-results/cert/export.json.gz PLANNER_TEST_ROOM_PHASE_GROUPS= PLANNER_TEST_ROOM_PHASE_BUDGET=90 PLANNER_TEST_ROOM_LAB_FALLBACK=1"
# (a) G14
env $COMMON PLANNER_TEST_ROOM_TAG_UNTAG_GROUP="G14=>Confezione,Servizio stilistico" ./scripts/core/test.sh -c RoomPhaseSnapshotTest
# (b) G12
env $COMMON PLANNER_TEST_ROOM_TAG_UNTAG_GROUP="G12=>Servizio stilistico,Servizio tecnico e stilistico" ./scripts/core/test.sh -c RoomPhaseSnapshotTest
# (c) entrambi
env $COMMON PLANNER_TEST_ROOM_TAG_UNTAG_GROUP="G14=>Confezione,Servizio stilistico;G12=>Servizio stilistico,Servizio tecnico e stilistico" ./scripts/core/test.sh -c RoomPhaseSnapshotTest
```
