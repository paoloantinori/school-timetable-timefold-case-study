# VERDICT TASK-482 Kempe-chain. Pre-validazione meccanica su grasp-seed-1. 2026-08-03.

> Fedora re-spike (mandato autonomo). Domanda di fattibilita' che decide se vale l'A/B completo (e il
> MoveSelector di produzione): **la Kempe chain chiude il gap H17 su grasp-seed-1 localmente?**

## Predizione (ex-ante, dichiarata PRIMA di guardare il risultato)

grasp-seed-1 ha un'unica topologia di buco H17: **G11 @ hour 11** (lezioni 8,9,10,_,12,13) su 9
mercoledi'. E' la STESSA struttura del bad-seed @10 (G11 Wednesday, slot mid-day saturo) su cui la
sessione precedente (alba10) aveva gia' provato la Kempe chain chiudendo NO (0/6 sustainable, H3 da
VRP-bloccati). Predizione: **stesso esito NO su grasp-seed-1** (0/9 sustainable), perche' la root cause
(slot saturo + ostacoli VRP-bloccati che spezzano la conflict-closure di Kempe) e' strutturale, identica.

## Esito (meccanico, oracolo `solutionManager`)

```
grasp-seed-1 (@11, 9 date): BASE H17=-9, conflicts=0 (H1/H3-fedele).
  VERDICT Kempe-chain grasp-seed-1 (SUSTAINABLE): 0/9 ; shell-game: 0/9 ; no-improve: 9/9
@10 (task478, regression check, 6 date): BASE H17=-6, conflicts=0.
  VERDICT Kempe-chain (SUSTAINABLE): 0/6 ; shell-game: 0/6 ; no-improve: 6/6
```

**Predizione CONFIRMATA. 0/9 sustainable su grasp-seed-1.** Ogni chain e' no-improve: holeClosed=true ma
noNewGap=false (shell-game: il blocker spostato apre un gap altrove) OPPURE H17d=0 (nessun guadagno netto)
OPPURE ttierHardd=-1000 (H3: una chain lesson arriva a uno slot dove una lezione VRP-bloccata condivide il
gruppo -> il teorema di Kempe non vale con ostacoli immobili). Stessa root cause del @10.

## Risposta alla domanda di fattibilita'

**NO. La Kempe chain NON chiude il gap H17 su grasp-seed-1 localmente (0/9 sustainable).** La saturazione
dello slot (@11 packato di 11-12 gruppi) vanifica il vantaggio strutturale della mossa, esattamente come
sul @10 e come il depth-3 cascade (TASK-481). Qualsiasi mossa locale che sposti un blocker dallo slot
saturo fa danno altrove.

## Decisione

**MoveSelector di produzione NON costruito; TASK-482 confirmato NO.** La pre-validazione dice che il
MoveSelector sarebbe inerte sul bad seed (le chain sono no-improve: la LS le rifiuterebbe in
hill-climbing o le accetterebbe solo peggiorando in tabu). Costruirlo (API 2.3.0 MutableSolutionView,
alta fatica) per un vincolo che la pre-validazione dice inerte non e' ragionevole: la pre-validazione
serve proprio a evitare questa spesa. **NON e' adozione** (nessun flag, nessun file di produzione toccato,
solo il test discriminator + i doc).

**Residuo non misurato (onesto):** la LS che combina una chain no-improve con mosse di follow-up. Solo un
MoveSelector nella LS reale lo testerebbe (il discriminatore offline applica/valuta/undo in isolamento).
Speculativo (le chain sono no-improve gia' in isolamento). Se maintainer vuole perseguirlo nonostante il NO,
serve il prototipo completo + A/B con guardia. Non raccomandato: la via principale resta il multi-start
(TASK-480/483, DEFAULT ON) che seleziona un bacino good-seed evitando la saturazione.

## Riproducibilita'

```
PLANNER_TEST_KEMPE=1 ./scripts/core/test.sh -c KempeChainH17DiscriminatorTest
```
ENTRambi i test (@10 regression + grasp-seed-1) EXIT 0 come spike informativo. Discriminatore gated
default-OFF (`@EnabledIfEnvironmentVariable`): il `test.sh` normale lo salta (zero impatto suite).

Design + modello + root cause: `task482-kempe-design-2026-08-03.md`. Precedente verdetto alba10:
`task482-results/design-kempe-chain-2026-08-03.md`. Raw seed-1:
`task480-results/badseed-frequency/grasp-seed-1/export.json.gz`.
