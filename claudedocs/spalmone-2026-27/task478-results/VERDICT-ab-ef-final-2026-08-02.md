# VERDICT A/B conferma ExactFinisher @ knob 1M (pacchetto adozione). 2026-08-02 alba4.

> 2 run freschi protocollo input + EF ON (`-Dplanner.exact-finisher.enabled=true
> -Dplanner.exact-finisher.max-candidates=1000000`) + source=input. Capture
> `ab-ef-final-run{1,2}/`. Verdetti dai raw. NESSUN flip effettuato: il flip default EF ON + knob
> 1M e' gated al si' di maintainer.

## Esito A/B: 2/2 hard-0, stabili, 0 H17

| run | score | full-day | H17 (buchi interni) |
|-----|-------|----------|---------------------|
| run1 | **0hard**/-1074257medium/8115soft | 88.3% | 0 |
| run2 | **0hard**/-1057977medium/8652soft | 88.7% | 0 |

Target ("hard 0 o residuo H17-only quantificato e stabile") **centrato: hard 0 su entrambi**,
nessun residuo. Medium spread -1057k/-1074k (~1,6%), full-day 88.3-88.7%.

## EF e' inerte su questi run (good seed)

Entrambi i run sono good seed (boxed = 0 all'entry del finisher): il log mostra solo il banner
`refreshSolverStatics` ("ExactFinisher residual-completion: enabled=true, maxCandidates=1000000"),
**nessun "placed X of Y boxed"**. EF non ha fatto nulla. Di conseguenza medium e full-day sono
**puro LS-baseline, non attribuibili a EF**. La banda good-seed (EF inerte) e' coerente:

| run good-seed | medium | full-day |
|---------------|--------|----------|
| probe-1m-run1 (riferimento) | -1047939 | 87.6% |
| final-run1 | -1074257 | 88.3% |
| final-run2 | -1057977 | 88.7% |

run1/run2 sono **dentro la banda** (al limite alto del full-day). Nessuna regressione EF (EF inerte
non puo' regredire medium). L'A/B conferma: **EF ON non regredisce il good seed**, hard-0 stabile.

## L'evidenza completa per la decisione di maintainer

L'A/B (good seed, 2/2) non esercita il path bad-seed (dove EF e' attivo). Quello e' coperto da:
- **probe-1m-run2** (bad seed, n=1): EF @ 1M placed **18/18 boxed** depth 0..1, H0 -> 0, finale
  -6hard = 6 H17 strutturale (ortogonale a EF, vedi diagnosi).
- **discriminatore offline** (`ExactFinisherRun4DiscriminatorTest`, deterministico, budget UNCAPPED
  100M): depth-0/1 chiude il worst case run4 (5/5 residui, 0 conflitti, Q22-fedele). depth-2 non
  serve.

Insieme: EF @ 1M chiude la worst-case boxed tail (bad seed) ed e' inerte/non-regredente sul good
seed. **EF @ 1M e' pronto per l'adozione come chiusura DETERMINISTICA della coda H0.**

## I 6 H17 residui (bad seed): ORTOGONALI a EF (causale), ma BLOCCANTI end-to-end

Diagnosi in `diagnosi-h17-g11-mercoledi-2026-08-02.md`: i 6 H17 di probe-run2/run4 sono G11
mercoledi ore 10 (NON il pattern G13 ipotizzato). La costruzione e' compatta; la LS sostituisce
la composizione con una strutturalmente non compattabile; hill-climbing non attraversa la valle a
2 mosse. **EF e' prosciolto meccanicamente** (non crea/non corregge il buco): l'attribuzione causale
è ORTOGONALE a EF, e questo regge.

**MA non e' trade-off accettabile.** Direttiva maintainer 2026-08-02: 0-hard e' BINARIO e BLOCCANTE
end-to-end, qualunque hard>0 (anche 6 buchi / 12.494 lezioni = 0,048%) e' un difetto aperto
bloccante per il path input. EF ratificato chiude la coda boxed (H0), ma l'hard TOTALE del path
input resta seed-dipendente (good seed 0, bad seed -6 H17): **il path input NON e' hard-0
end-to-end** finché TASK-481 (DRAFT-006 consolidato) non cura questi H17. Cura search-side
(metaeuristica LS o hole-repair composition-aware), NON tocca il modello (golden 0/34).

## Ratifica (maintainer, 2026-08-02: EF DEFAULT ON + knob 1M)

1. **Flip default EF ON + knob 1M RATIFICATI.** `-Dplanner.exact-finisher.enabled` opt-out (null ->
   true, solo `=false` disattiva), `max-candidates` default 1000000. Converte la chiusura della coda
   boxed (H0) da probabilistica a DETERMINISTICA. **m-13 (source=input default):** EF e' condizione
   NECESSARIA (chiude H0) ma NON sufficiente: resta bloccato end-to-end dai 6 H17 bad-seed (TASK-481).
   maintainer NON ha flippato il default source (resta golden).
2. **Verdetto EF-vs-multistart (TASK-480 AC#3):** EF chiude la coda boxed da solo sul worst case
   noto -> il multi-start e' cintura+bretelle per l'H0. L'H17 (-6hard, bad seed) e' ortogonale a EF,
   cura bloccante separata (TASK-481).
3. **DoD sul codice EF (ratifica):** test.sh EXIT 0 963/0/16 (stessi conteggi di alba3, il flip
   default-ON non ha rotto nulla); `/simplify` + `/code-review high` sul diff di ratifica in corso.

**Stato: EF RATIFICATO DEFAULT ON + knob 1M (maintainer ha detto si').** Path input end-to-end: H0
deterministico (EF), ma -6 H17 bad-seed resta bloccante (TASK-481 aperto). Nessun cambio al default
source (resta golden).

## Puntatori
- Capture: `task478-results/ab-ef-final-run{1,2}/` (score, analysis, export, lessons.txt, KPI, viz)
- Diagnosi H17: `task478-results/diagnosi-h17-g11-mercoledi-2026-08-02.md`
- Discriminatore: `task478-results/discriminatore-uncapped-run4-2026-08-02.md`
- Cronaca EF completa: `task478-results/VERDICT-ab-exactfinisher-2026-08-02.md`
