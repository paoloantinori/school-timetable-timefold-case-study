# VERDETTO SPIKE: room-null vs co-presenza golden acconciatura (zai, 2026-07-31)

Read-only, zero solve. Cross-tab baseline-mwd1 room-null vs golden co-presenza. Analisi meccanica
delegata a subagent + **verifica indipendente** del claim piu' sorprendente (golden Thu depth-5)
direttamente dal CSV. Gate per TASK-464 (modellazione co-presenza ospite).

## VERDETTO (3 righe, dai raw)
- **Tutti i 92 room-null (100%) sono acconciatura e cadono su celle dove il golden co-loca 2+ gruppi**
  (Mon AM + Thu). Il solver capped il R11 a 1 gruppo/slot (H2), il golden ci mette
  **2-5 gruppi** contemporanei (Thu 08-12 depth 5 = 1/2/G10 + G12 + G14, VERIFICATO dal CSV).
  Zero room-null su celle non-co-presenza (25% dell'envelope): non e' tautologico, e' causale.
- **La moda del golden**: 95% delle ore acconciatura (683/719) avviene con 2+ gruppi co-presenti.
  Per azzerare i 92 room-null servirebbe cap 2 (48 lezioni) + cap 3 (2) + cap 4 (23) + **cap 5 (19)**.
  Un model "guest cap 2" pulisce solo ~meta'; il clearance pieno richiede cap 5 (una stanza, 5 classi).
- **CAVEAT (decisione di referente)**: il golden NON ha colonna room, quindi la "co-presenza" e' INFERITA
  da sovrapposizione (weekday, slot) di materie acconciatura. Non possiamo distinguere "5 classi
  davvero nello stesso laboratorio" da "alcune in aula normale". L'audit inverso (§5) non trova
  materie esiliate fuori dalle celle di co-presenza: il tagging lab-obbligatorio delle 4 materie
  acconciatura è CORRETTO, il difetto è il cap H2 (1 lezione/slot), non il tagging. Nessun de-tagging.

## GATE TASK-464: SUPERATO (con caveat)
La condizione di maintainer ("TASK-464 solo se lo spike conferma che una fetta significativa dei room-null
cade sulle celle di co-presenza golden") e' **MET**: non una fetta, il 100%. Quindi TASK-464 (design +
prototipo flag OFF per co-presenza ospite legale) e' giustificato. MA:
1. Un guest-cap=2 pulisce solo 48/92; il clearance pieno richiede cap 5 (modello piu' invasivo:
   una stanza, fino a 5 classi contemporanee - inusuale per un "lab").
2. La modellazione e' **gated alla conferma di referente**: la scuola condivide davvero il laboratorio
   acconciatura fra 5 classi al giovedi', oppure alcune di quelle ore sono in aula normale? (Il golden
   non lo dice). In quest'ultimo caso la cura non e' co-presenza ma rilassare il tagging lab-obbligatorio
   per le ore che la scuola fa in aula normale - che pero' l'audit non ha trovato.
3. **Coppie ospite candidate** (da dove il solver oggi sceglie male): oggi il baseline lascia G12 +
   G14 possedere il lab e fa morire di fame G02 (70h room-null), G10 (19h), G06 (3h). Il golden
   invece ospita G12 con G02 (Mon 08-12) e G14+G12 col blocco 1/2/G10 (Thu 08-13).

## Tabella raw (verificata: 92 riprodotto, depth Thu verificato dal CSV)

H29 room-null = 92 (placed, no room, not pinned, not Stage). Tutti tag acconciatura.

| group | subject | n |
|---|---|---|
| G02 | Lavaggio | 45 |
| G02 | Servizio stilistico | 24 |
| G10 | Servizio stilistico | 18 |
| G06 | Barberia | 1 |
| G06 | Lavaggio | 1 |
| G06 | Servizio stilistico | 1 |
| G02 | Barberia | 1 |
| G10 | Lavaggio | 1 |
| **TOT** | | **92** |

Room-null per weekday: Mon 45 / Thu 42 / Tue 2 / Wed 2 / Fri 1. (Mon AM 08-09 = G02 Lavaggio;
Thu 11+13 = i picchi.)

Golden co-presenza (escluse trappole sabati/Fasullo/Stage): 30/40 celle envelope sono co-presenza;
**683/719 ore acc (95%) con 2+ gruppi**. Profondita' Thu (VERIFICATO dal CSV):
- Thu 08-12: depth 5 (1/2/G10 + G12 + G14)
- Thu 13: depth 4 (1/2/G10 + G12)
- Thu 14-15: depth 2 (G10 + G12)
- Mon 08-12: depth 2 (G02 + G12)

Cross (room-null su celle co-presenza): **92/92 = 100%**. Genuine scarcity: **0**.

Depth richiesta per clearance: cap2=48 lezioni, cap3=2, cap4=23, cap5=19 (somma 92 ✓).

## Meccanismo (causale, non tautologico)
A ogni datetime room-null, il lab tiene gia' 1 gruppo (H2 cap 1 lezione/slot); la lezione room-null
non puo' aggiungersi. Il baseline lab depth = {1: 567 datetimes}: il solver NON condivide mai. Il golden
condivide nel 95% delle ore. Quindi i room-null sono **artefatto del cap H2**, non qualita' di
piazzamento ne' scarsita' genuina.

## Limiti
- n=1 baseline (baseline-mwd1). L'anatomia room-null e' stabile across run (E3 relaunch: room-null 93,
  stessa concentrazione acconciatura), quindi la finding regge; le magnitudini esatte no.
- Golden senza room: la co-presenza e' inferita. La domanda "5 classi in 1 lab vs alcune in aula
  normale" resta aperte per referente.
- Solo acconciatura analizzata (dove si concentrano i room-null). La moda-WED (16 celle excess) sono
  lab pinned (non H29), fuori scope.

## Artifact
- Analisi piena (subagent + mia verifica): `spike-room-null-copresenza-2026-07-31/ANALYSIS.md`
- Helper script: `/tmp/analyze_copresence.py`, `/tmp/refine_copresence.py`
- Baseline: `t19-2026-07-31/baseline-mwd1/export.json.gz`
