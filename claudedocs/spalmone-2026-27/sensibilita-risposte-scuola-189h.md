# Sensibilita' alle risposte della scuola - le 189h hard senza aula (MISURATO)

Le 189 ore hard senza aula (H29 room-null del cert 2026-07-29) dipendono da risposte chieste
alla scuola e non ancora arrivate. Questo documento dice, MISURATO e non stimato, quante ore
cadono per ciascuna risposta e quante restano davvero infattibili. Tutto in-harness
(`RoomPhaseSnapshotTest` export-mode sul cert `export.json.gz`, all-groups, phase-5 LS 90s,
override PRE-LS); file protetti intoccati.

METRICA: il `hard` score (boxed invariato a 3 -> 6 hard; H29 peso 1 per room-null). Baseline
cert = **hard -195** = 189 room-null + 3 boxed*2. "Chiude X" = delta hard vs -195 (= room-null
che la phase-5 LS e' riuscita a piazzare dopo l'override). Snapshot = trend su UNA traiettoria
(quella del cert), non adozione.

## Decomposizione statica delle 189 (da export.json.gz)

Per MATERIA (tutte lab tranne 7 non-lab sparsi):
| materia | ore | tag stanza |
|---|---|---|
| Lavaggio | 75 | acconciatura |
| Servizio stilistico | 53 | acconciatura (di cui G14 28) |
| Laboratorio Termoidraulico | 24 | termoidraulica |
| Barberia | 11 | acconciatura |
| Taglio Metalli | 10 | meccanica |
| Lavorazioni meccaniche | 9 | meccanica |
| 7 non-lab sparsi (1 ciascuno: Sicurezza, Lingua Inglese, Recuperi, Approfondimenti, Ed. Civica, Scienze, Matematica) | 7 | - |

=> **139 delle 182 lab sono ACCONCIATURA** (Lavaggio+Serv.stil.+Barberia): il singolo
R11 (cap 20) non regge la domanda di 5 gruppi.

Per GRUPPO: G06 76, G14 28, G02 15, G03 14, G10 14, G04 13, G07 10, G13 9,
G12 9, G11 1.

## TABELLA DI SENSIBILITA' (misurato, hard-score delta dal baseline -195)

| Risposta della scuola | chiuso | residuo | hard dopo | nota |
|---|---|---|---|---|
| (baseline, nessuna risposta) | 0 | 189 | -195 | phase-5 LS fills 0: nessuna stanza libera |
| **Servizio stilistico in aula** (de-tag) | **89** | 100 | -106 | i 53 suoi + ~36 liberati per capacita' acconciatura |
| **Taglio Metalli in aula** (de-tag) | 4 | 185 | -191 | aule sature, quasi niente assorbe |
| **Taglio Metalli in R17** (retag saldatura + cap 24) | **18** | 171 | -177 | R17 dedicata >> aula satura |
| R17 cap reale (sola, corso saldatura) | 0 | 189 | -195 | nessuna room-null saldatura da piazzare |
| **G14 assiste nel lab della classe titolare** | **<=28** | >=161 | (bound) | G14 ha 28 room-null TUTTE serv.stil.; upper bound statico, chiusura effettiva subordinata a capacita' lab titolare (modello M-LA/condivisione, non risolvibile in questo harness). ALTERNATIVA al de-tag serv.stil (che gia' le chiude nei suoi 89): non si somma |
| Combo A: serv.stil aula + taglio aula | 93 | 96 | -102 | additivo (89+4) |
| **Combo B: serv.stil aula + taglio R17 + R17 cap** | **107** | 82 | -88 | "tutte le risposte di tagging sì": 89+18 |

## LETTURA (cosa sapere il giorno che arrivano le risposte)

1. **Leva grossa = Servizio stilistico in aula (89)**. E' da sola quasi meta' delle 189.
   Conferma il ~85 stimato nello SPIKE 4 (misura pulita sul cert a 189, non piu' il 251 vecchio).
2. **Taglio Metalli: R17 (18) battuto aula (4)**. Se la scuola ha un'officina di saldatura
   reale, il RETAG a saldatura + R17 a capacita' reale chiude 18; il de-tag in aula ne chiude
   solo 4 (le aule sono sature). La domanda alla scuola arriva col numero 18 (retag) vs 4 (de-tag).
3. **R17 cap da sola = 0**: serve combinata col retag (nessuna room-null saldatura oggi).
4. **G14 = 28, tutte Servizio stilistico**. "G14 assiste" e' un'ALTERNATIVA al de-tag serv.stil,
   non si somma: se serv.stil va in aula, le 28 di G14 sono gia' chiuse (nei 89). Se invece la
   scuola preferisce che G14 condivida il lab titolare (M-LA), chiude fino a 28 (bound statico,
   chiusura effettiva da confermare col modello condivisione, non misurato qui).
5. **Combo "tutte le risposte di tagging" (B) = 107 chiusi, residuo 82**.

## COSA RESTA INDETTABILE (le 82 residuali dopo combo B)

Il residuo 82 e' dominato da **Lavaggio (75)**: acconciatura GENUINA (pratica su cliente, non
de-taggabile in aula), nessuna risposta di tagging la tocca. Serve un'altra stanza acconciatura
(risposta di CAPACITA', non di tagging) o riclassificazione. Il resto del residuo: Barberia 11
+ Laboratorio Termoidraulico 24 + Lavorazioni meccaniche 9 + 7 non-lab, tutte lab genuini o
artefatti, capacity-bound.

CONCLUSIONE: le risposte di TAGGING della scuola (servizio stilistico, taglio metalli) chiudono
al massimo **107 delle 189** (combo B). Le **82 residue sono capacity-bound** (soprattutto
Lavaggio/acconciatura): non si chiudono con una materia che cambia stanza, ma con piu' stanze
o riclassificazione. La domanda strutturale alla scuola e' quindi DUE: (a) le materie
de-tag/retag (107 potenziali), (b) un'altra stanza acconciatura per Lavaggio (le ~75 residue).

## METODO (per riprodurre)

Harness: `RoomPhaseSnapshotTest` export-mode, env `PLANNER_TEST_ROOM_PHASE=1
PLANNER_TEST_ROOM_PHASE_EXPORT=<cert export> PLANNER_TEST_ROOM_PHASE_GROUPS=""
PLANNER_TEST_ROOM_PHASE_BUDGET=90` + override (`PLANNER_TEST_ROOM_TAG_UNTAG`,
`PLANNER_TEST_ROOM_TAG_RETAG`, `PLANNER_TEST_ROOM_R17_CAP`). Wrapper:
`scripts/benchmark/room-tag-sensitivity.sh` (casi de-tag/R17/combo A). Casi RETAG e
combo B: invocazione diretta (sopra). Output raw: questo documento + log harness.

Caveat: snapshot a UNA traiettoria (il cert), trend non adozione; la chiusura e' quella che la
phase-5 LS raggiunge in 90s sullo stato reale del cert (boxed invariato a 3). La misura e'
ripetibile e deterministica sullo stesso export.
