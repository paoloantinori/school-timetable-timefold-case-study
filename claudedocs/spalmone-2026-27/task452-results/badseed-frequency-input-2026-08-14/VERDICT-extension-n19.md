# VERDICT EXTENSION n=19 - Census single-construction flip #1 (2026-08-14 sera)

> Filone B del mandato notturno 14/08. Estensione del census (seed 13-19, 7 run) sullo
> STESSO protocollo della notte 13/08: single-construction source=input, multi-start OFF,
> MT-LS OFF (comparabilita' con i primi 12: `-Dplanner.multithreaded-ls.enabled=false`,
> i primi 12 girarono pre-ratifica su LS mono-thread di default). Driver
> `multistart-ab.sh` con `MULTI_START_OFF=1 MT_LS_OFF=1 SEED_OFFSET=12 N=7 --no-setup`
> sul profilo bsinput14 gia' setupato. Banner verificato per run: grasp seed=N,
> Multi-start enabled=false, Multi-threaded LS enabled=false.

## TL;DR

1. **GRASP single-construction p(0-hard) = 13/19 = 0.684, Wilson95 [0.460, 0.846]** (era
   8/12 = 0.667 [0.391, 0.862]): il point estimate e' STABILE (0.667 -> 0.684) e la CI si
   e' stretta ma resta larga. La coda bad e' confermata strutturale: ~1 costruzione su 3.
2. **P(>=1 0-hard | N multi-start)**: N=3 (default ratificato) = 0.969 point ma
   **CI_lo 0.843 < 0.95**; **N=5 = CI_lo 0.954**; N=6 = 0.975. Il gap CI del mandato
   ("CI_lo chiede N=7" su n=12) si e' RIDOTTO: ora N=5 basta per la copertura robusta
   su tutto l'intervallo. N raccomandato P>=0.95: point=3, CI_lo=5.
3. **DUE NUOVE FIRME di bad-basin** oltre le tre note (H17-G11, H29 room-null, H0-boxed):
   - **seed-17 = H17 -7 TUTTI su G13 mercoledi' slot 12** (pattern settimanale ricorrente,
     7 date, occupazione 8-11+13, buco a 12): quarta firma, gruppo e meccanica diversi
     dal bacino G11 noto;
   - **seed-18 = H28 -32** (no-teacher-return, TUTTE le lezioni collocate, zero H0):
     quinta firma, constraint mai visto nei census precedenti.
   La coda bad non e' un unico bacino: e' una popolazione di modi di fallimento distinti.
   Ogni cura mirata (TASK-481/482 per H17) copre UNA firma; il multi-start resta l'unica
   copertura trasversale.
4. Baseline deterministica (GRASP-off) resta 0hard: la costruzione DEFAULT di source=input
   e' robusta single-shot (conferma n=1, non campionato di nuovo questa notte).
5. Verdetto flip #1: **DEFER CONFERMATO** (dipendenza strutturale dal multi-start; golden
   single-shot p=1.0). Novita': la configurazione CI-robusta costa N=5 tentativi
   (non piu' 7), e con MT-LS ratificato il costo per tentativo e' ~25% piu' leggero.

## Per-run (estensione seed 13-19)

| run | score | wall(s) | 0-hard? | firma hard |
|-----|-------|---------|---------|------------|
| grasp-seed-13 | 0hard/-1149240medium | 2351 | YES | - |
| grasp-seed-14 | 0hard/-1273328medium | 2623 | YES | - |
| grasp-seed-15 | 0hard/-1318333medium | 2192 | YES | - |
| grasp-seed-16 | 0hard/-1168458medium | 3412 | YES | - |
| grasp-seed-17 | -7hard/-1492888medium | 1716 | no | H17 -7, TUTTI G13 mercoledi' slot 12 (QUARTA firma) |
| grasp-seed-18 | -32hard/-1145270medium | 3546 | no | H28 -32 no-teacher-return, 0 unplaced (QUINTA firma) |
| grasp-seed-19 | 0hard/-1360518medium | 1552 | YES | - |

Consegna estensione: 5 good / 7. Pool completo n=19: 13 good / 6 bad
(seed 1, 6, 9, 12, 17, 18).

## Lettura per la decisione flip #1 (per maintainer)

- Il census n=19 conferma il quadro n=12 senza sorprese sul point estimate: la
  costruzione GRASP randomizzata di source=input ha ~32% di probabilita' di chiudere con
  hard residuo. Golden non ha questa coda (single-shot 0hard su N=32, TASK-480).
- La struttura della coda e' PIU' frammentata di quanto il n=12 mostrasse: 5 firme
  distinte su 6 bad (H17-G11 x2, H29 x1, H0-boxed+H17 x1, H17-G13 x1, H28 x1).
  Nessuna cura singola chiude la coda; il multi-start (aggira tutte) o un cambio di
  regime di costruzione sono le leve.
- Costo della copertura: maxAttempts=3 copre al point (0.969) ma non sul CI_lo (0.843).
  maxAttempts=5 porta CI_lo a 0.954: configurazione CI-robusta consigliata QUANDO il
  flip verra' fatto. Filone D (se eseguito) misura il wall di maxAttempts=7.

## Comparabilita' e limiti

- Stesso host (laptop), stesso profilo (bsinput14, --no-setup: seed+import+pre-assign della
  notte 13/08 riusati), stesso JAR (e964f667, rebuild verificato classi MT-LS nel jar).
  MT-LS esplicitamente OFF su tutti i 7 run (i primi 12 pre-ratifica, mono-thread).
- n=19 su un host solo. Le firme G13/H28 erano n=1 alla stesura: RIPRODOTTE il 15/08
  (reproduction-2026-08-15/NOTA.md, 6/6 re-run concordi, firme n>=2 deterministiche).
- Il wall dei run 16/18 (3412s/3546s) e' vicino al cap 5400s ma dentro; nessun abort.
- Nessun rebuild/restart con altro codice durante il census (JAR unico).

## Puntatori

- Raw: questa directory, `grasp-seed-13..19/` (score, analysis, export, KPI battery).
- Stats: `scripts/analyze/badseed-frequency-stats.py <dir>` (output n=19 sopra).
- Notte 1 (n=12 + protocollo): `VERDICT.md` in questa directory (invariato, resta la
  cronistoria); SUMMARY-night1.txt e' il log notte 1.


## Addendum: caratterizzazione delle due nuove firme (read-only sui raw, sera 14/08)

Analisi post-consegna sui lessons.txt committati (nessun nuovo run):

- **seed-17 / H17 -7 G13**: i 7 buchi sono TUTTI mercoledi' slot 12. Il golden sugli
  stessi mercoledi' fa il giorno-5h CONTIGUO 8-12 (T05/Metrologia a chiudere a slot 12);
  il run mette le stesse 5 ore spezzate 8-11 + 13 (T06/Competenze Digitali a 13).
  Stessa famiglia dello scarto noto "G13 venerdi' late-start" (confirm run A: 13 celle):
  G13 e' il gruppo dove la LS rompe la forma del giorno piu' facilmente. Cura ipotetica
  (non costruita): famiglia della compattezza/consolidamento, non territorio M-TC.
- **seed-18 / H28 -32**: 14 date, TUTTE G13, TUTTE "Lavorazioni meccaniche", pattern
  identico T10, T10, T15, T10 (A-B-A del docente dentro la materia
  co-docente nel giorno). Il vincolo H28 (golden-first, TASK-448) vieta il ritorno: la
  co-docenza sullo stesso giorno e' la condizione strutturale. Matches 32 su 14 giorni
  = ~2 lezioni contate per giorno.
- **Convergenza**: entrambe le firme nuove sono G13. Nel pool n=19, G13 appare nelle
  firme di seed-17 E nel late-start cronico: candidato "gruppo fragile" del path input,
  da aggiungere ai watch-item del flip #1 accanto a G11.
