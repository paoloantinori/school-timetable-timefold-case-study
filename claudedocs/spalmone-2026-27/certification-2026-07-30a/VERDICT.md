# CERTIFICAZIONE 2026-07-30a (run 30a) - TASK-456 aule, secondo run DEFAULT

Cattura del 2026-07-30 (notte). Secondo run della tripletta TASK-456, lanciato coi
**default** (solo `-Dplanner.phase-transition-dump.enabled=true`), identico in configurazione
al 29b. Serve come **n=2** del run default: conferma che i 35 boxed sono saturazione
strutturale, non varianza del solver.

## VERDETto: run SENZA constrained-teacher, NON confrontabile col 447 sugli orari

**Questo run gira SENZA la pre-assegnazione constrained-teacher** (`-Dplanner.pre-assign.constrained-teacher.enabled`
e' default OFF, TASK-435 mai ratificato). La pipeline di pre-assegnazione standard ha girato
ed e' COMPLETE, ma le ore del corso critico NON sono state pinnate in anticipo.

Di conseguenza **NON e' confrontabile col riferimento 447 sul fronte orari**. Il 447 aveva
quel flag ACCESO: 231 ore di T01 PINNATE prima del solve (tutte collocate, slack +5h).
Il 30a, coi default, ne pinnata zero e chiude con **35 lezioni non collocate (H0)**, slack
-30h. I 35 boxed e parte del calo di full-day (84,4% vs 87,0% ex-G14 del 447) vengono
dalla pre-assegnazione ASSENTE, non dal pacchetto aule.

Rinvio completo: `task459-runs-non-confrontabili-2026-07-30.md` (+ `task459-slack-triangolo-g06-t01-2026-07-30.md`).

**Il verdetto AULE del 29b regge** perche' la sua popolazione (room-tier: M-LF, room-null,
R18 cap-0) e' indipendente dal flag constrained-teacher. Il 30a non lo tocca:
conferma solo il fronte boxed/full-day del default.

## Cosa certifica questo run (n=2 del default)

**I 35 boxed sono saturazione strutturale, non varianza.** Verificato meccanicamente sul DB:
**tutti e 35** i boxed sono un UNICO triple `G06 / Servizio tecnico e stilistico / T01`
(0/35 fuori dal triple). E' il triangolo G06 / T01 documentato: capacita 30h vs
domanda 60h = slack -30h senza pre-assegnazione (overdemand strutturale), +5h con. Leva =
allocazione anticipata (TASK-435), NON tuning.

## Score e breakdown

```
score: -234hard/-1081927medium/3239soft
```

| Constraint | matches | hard |
|---|---|---|
| H29 - Assigned lesson must have a room | 164 | -164 |
| H0 - Unassigned lesson (boxed) | 35 | -70 |
| **totale hard** | | **-234** |

Contabilita TASK-447: `hard == boxed*2 + room-null*1` = 35*2 + 164 = 70 + 164 = 234. (Tutti
boxed room-tier esclusi da `timeslotTierHardScore`, per costruzione.)

## Confronto 29b (default, certificato) vs 30a (default, questo run)

| Metrica | 29b | 30a | Δ | letto |
|---|---|---|---|---|
| hard | -237 | -234 | +3 | rumore fase stanze (3 room-null in meno) |
| H0 boxed | 35 | 35 | 0 | **identici**, stesso triple |
| H29 room-null | 167 | 164 | -3 | solver noise |
| medium | -1.089.090 | -1.081.927 | +7.163 | trascurabile |
| full-day ex-G14 | 84,3% | 84,4% | +0,1 | stabile |
| resemblance unpinned | 75,2% | 75,3% | +0,1 | stabile |
| full-day (con G14) | 78,3% | 78,4% | +0,1 | stabile |

**Tutto entro 0,1% o 3 unita hard.** Il secondo run default riproduce il primo sui KPI
portanti. La catena dei 35 boxed e' la stessa. Questo e' esattamente l'n=2 che serviva:
nessun segnale di varianza, la saturazione e' strutturale.

## KPI notabili (interi file nella cartella)

- **Ricorrenza TOTALE 76,4%** (unpinned 75,3%, pinned 80,3%). Sopra la baseline pre-campagna
  (52,8%) e coerente con la ricorrenza a fine costruzione (~76%). Stabile vs 29b.
- **Buchi interni: 0** giorni / 0 slot bucati. (G14 esente nel motore.)
- **M-LF attivo**: 61 match (-61k medium), co-firma col S10 (61). M37: 298 match (-29,8k).
- Day-start scarto (>=3 vs golden): `G11 mercoledi run 14 | golden 0` (singolo, non
  strutturale; oggetto del task 450 coi dump per-fase).

## Note

- `preAssignmentStatus: COMPLETE`, `lastSolverError: null`, `stagedStage: null` (ciclo
  staged completato). Letto il campo corretto: `stagedStage`, non `solverStatus` (NOT_SOLVING
  per tutta la durata del ciclo staged, per costruzione).
- Phase-transition dump raccolti: 8 file (+4 boxed, TASK-419). Il 30a aveva il dump flag ON,
  quindi ha dump per-fase propri nella sottocartella `phase-transitions/`.
- Export incluso: `export.json.gz` (ora committabile, fix gitignore `6ce94ebd`).
