---
id: TASK-638
title: >-
  TASK-638 - ViolationScopedSteepestFinisher: il move-selector
  violation-directed che Timefold community non ha (mira della terna + ampiezza
  LS, deterministico)
status: Done
assignee: []
created_date: '2026-09-21 16:10'
updated_date: '2026-09-21 16:11'
labels:
  - claude-planned
  - delivery-stage
  - ls
  - design
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla domanda di maintainer 21/09 sera ("non esiste un mode' 'parti dalle celle che violano': siamo in grado di disegnarlo noi?"). SI': e' la fusione di due meccanismi che abbiamo gia' separati: la mira della terna (enumerazione target dai constraint match via resolveLessons, violation-directed per costruzione) e l'ampiezza di ricerca della LS. DESIGN: custom phase/pre-save pass: (1) score con match ENABLED sul vivo; (2) target = lezioni giustificate dai match hard negativi; (3) mosse generate SOLO intorno ai target (Change verso le celle VRP del target + Swap con intorno limitato stesso-docente/gruppo/giorno ±k); (4) steepest hard-migliorante (tie medium) con director incrementale, itera fino a non-miglioramento o tetto; (5) zero RNG: determinismo per costruzione + proprieta' oracolo (solo mosse miglioranti). Perche' Timefold community non lo ha: i loro selettori enumerano liste statiche per performance (la violation-direction costa una score per proposta); noi possiamo permettercelo a fine corsa su residui piccoli. Copre la classe COMPOSITE_MISSING (le due celle H7 del tentativo-1 OFF: 3.373 swap legali full-family, la steepest prende il migliore). INPUT: i tre banchi accodati sul capture off/ (h7swap armato = forma deterministica a grammatica fissa; LS corto = soffitto statistico; LS scoped+seeded = forma maintainer). TASK-636 note contengono il contesto completo.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 I tre banchi (h7swap armato / LS corto / LS scoped+seeded) girati sul capture off/ con esiti registrati: dicono se basta l'ampiezza, se serve la mira, il costo d'iterazione
- [ ] #2 Il finisher implementato prima in forma banco (replay sullo stato off/ senza ciclo): loop score+match -> target -> mosse mirate -> steepest hard-migliorante -> itera; chiude il -2 H7 del tentativo-1
- [ ] #3 Determinismo: nessun RNG (steepest + ordinamenti deterministici) o seed fisso dichiarato; proprieta' oracolo improve-or-worst-never (solo mosse miglioranti applicate)
- [ ] #4 Censimento: iterazioni, mosse valutate, wall, target size; scala quando i target sono 0 (no-op a costo della sola score)
- [ ] #5 Gates DoD + A/B di adozione a ciclo (posizione pre-save accanto alla terna) prima di qualunque default
<!-- AC:END -->

## DUE FORME D'IMPLEMENTAZIONE (dalla domanda di maintainer: "custom phase che sfrutta la LS? si'")

FORMA A (custom phase pura, come la terna): riusa del motore LS solo la VALUTAZIONE (ScoreDirector incrementale: doMove/delta/undo), il loop proposta-scelta-accettazione e' nostro (steepest). FORMA B (PREFERIBILE, la forma 'sfrutta la LS'): una fase <localSearch> vera il cui selettore e' un MoveListFactory violation-scoped: genera per step SOLO le mosse intorno ai target (Change verso celle VRP + Swap intorno limitato); il motore fa proposta incrementale/undo/terminazione/stepping (e tabu/LA se serve sull'intorno ristretto). Il ponte tecnico: il MoveListFactory non vede il punteggio, quindi la mira arriva da una custom phase precedente che pubblica gli id delle lezioni in violazione su una static (pattern di casa, precedenti pair-cell M38). Riproducibilita': REPRODUCIBLE + seed fisso + factory a ordinamento deterministico. CRITERIO: B salvo che il bridge si riveli insufficiente nel banco (es. serve re-scoping per-iterazione che il factory non regge: allora A). Il banco sul capture off/ risponde anche questo.

## CONFERMA maintainer 21/09 (regressione e pin): le due clausole invalutabili

(1) SCOPE = SOLO PROPOSTA, MAI VALUTAZIONE: ogni mossa e' valutata contro il provider COMPLETO (67 famiglie, delta incrementale): nessun insieme ridotto, l'accettazione hard-migliorante respinge qualsiasi regressione familiare. (2) PIN SU DUE LIVELLI: mai mossi (factory salta target pinnati E partner pinnati, regola di casa della terna) ma CONTATI (H7 somma le ore pinnate, i target M20-family sono netti dei pin autoritativi: la valutazione legge lo stato pinnato). BELT: verifica composita fine-pass zero-new-hard (stile verifyComposite), fallimento rumoroso mai consegna silenziosa. Da verificare nel banco AC#2.

## BANCHI 638a/638b ESEGUITI (21/09 notte, sul capture off/ in repo, Timefold 2.6.0)

BASE: pipe lessons.txt off/ + stanze CH-only (rumore dichiarato: H4 fantasma -35, medium base noise). 638a (terna+h7swap ARMATO): NO-OP (-37 -> -37): le ROOM-CAND di h7swap muoiono su stanze greedy, il limite NOTO della 632 (chiudeva solo su base refined): prossima iterazione = forma bench4 (room-only refinement prima della terna). 638b (LS corta change+swap, NO_ASSERT seed 0, unimproved 60s / wall 300s): **CHIUDE ENTRAMBE LE CELLE H7** (-37 -> -35, resta solo l'H4 fantasma), medium -7.040k -> -6.588k, 303s. LA PREDIZIONE DI maintainer E' CONFERMATA: il soffitto statistico esiste ed e' economico. Correzione di misura del banco (lezione): solver.solve() RITORNA il best, l'istanza di lavoro resta all'ultimo stato: il primo take misurava l'istanza, non il best. INPUT DI DESIGN: (1) la mira serve (la terna a grammatica fissa non basta su stanze greedy); (2) l'ampiezza sola funziona ma costa 5 minuti e non ha oracolo; (3) il design 638 (scoped+steepest+oracolo) resta il bersaglio: la stessa chiusura in secondi con determinismo totale. Cell-feed v2 VERIFICATO anche qui (le due celle H7 stampate con giustificazione completa pre-LS).

## NEAR-MISS A/B 637 (22/09 sera, entrambi i bracci, ciechi in volo)

OFF [-3/0]: H0+H32 su G02 2026-09-17 (lezione boxed = T21/Lingua Inglese, il giorno a 5/6). CIECO: SOLUZIONE in 1 mossa (piazzare a @13: T21 libera, 5h del giorno -> 6 = cap). ARBITRATO: CONFERMATO (T21 ha zero lezioni a @13 quel giorno, cap 6 = aggiungere la sesta e' legale). Classe COMPOSITE_MISSING: ExactFinisher non ha piazzato la boxed con lo slot libero davanti (perche'? da diagnosticare). ON [-1/0]: H28 su G13 2027-02-12 (T10@8..T15@9..T10@11 in Lavorazioni meccaniche; golden = blocco monografico mono-docente). H28RotationFinisher ha provato: 'locked 1' (lezione pinnata). Cieco in volo.

## ARBITRAGGIO ON H28 (22/09 sera): CURABILE in 1 swap intra-giorno

Il cieco deduce che il lock e' sull'interloper M@9 (ogni permutazione del trio lo trascina) e trova la cura allargando al giorno intero: SWAP T10/LM @8 <-> T15/TS @10. Il giorno risultante (@8 M-TS, @9 M-LM locked, @10 D-LM, @11 D-LM, @12 M-TS) rompe la bracket D..M..D senza toccare il locked: T10 passa a @10-@11 adiacenti. H17/H32/H7/H1 intatti per costruzione (contiguita' preservata, conteggi per-docente invariati). ROBUSTEZZA: il cieco enumera tutti i 10 swap intra-giorno: 5 curano H28, e per QUALUNSI singola lezione locked almeno uno sopravvive. Incurabile solo con 2+ locked. CONFERMA COMPOSITE_MISSING: l'H28RotationFinisher ha un vocabolario di trio (ogni permutazione trascina l'interloper) che il giorno intero sventa.

2026-10-03 chiuso come FATTO (marcatura stantia): ViolationScopedSteepestFinisher RATIFICATO DEFAULT ON il 22/09 (delega 'ratifica quello che devi', A/B 5/5: ON [0/0] vs OFF [-2/0], la swap chiude la consegna live -1 -> 0 in 414ms). Il pass vive nel ciclo staged post-HRF pre-save TASK-526. Docs: task638-results/{ab-adoption,catalog-sweep,blind-and-moves}/. Questo task era rimasto To Do per omissione di registro, non per lavoro mancante.
