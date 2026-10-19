# TASK-468 SWEEP PESO M-TR - VERDETTO comparativo (zai, 2026-08-01)

Domanda dello sweep: a che peso M-TR sterza il solver verso la pratica golden (residuo run
vicino al golden)? Risposta: **in nessun peso provato.** Il residuo e' strutturale
(sorgente: COSTRUZIONE / template builder, NON una gara di pesi; vedi DIAGNOSI CORRETTA sotto),
non accordabile col peso. Dettaglio sotto.

Setup ogni run (protocollo pulito identico, attribuzione = solo il peso): profilo production
fresco (drop-and-create + import-calendario + pre-assign, **3090 pinned verificati** su entrambi
i run), flags 463+464+468 ON, knob `planner.constraints.theory-adjacent.weight` variato. Pre-solve
w1000 verificato: M-TR -48000 / 24 match = esattamente 1000x il baseline peso-1 (-48/24) =>
knob arrivato, setup fedele. Baseline golden = **432 raw** (riprodotto dal source,
`theory_adjacent_residual.py`) / **440 runtime** (432 + 8 dal rename RA->Recuperi su import,
re-measure meccanico sessione precedente).

## HEADLINE: peso 1000 e' INERTE quanto peso 1

| metric | golden | peso 1 (ab-on-run1) | peso 1000 (ab-w1000-run1) | delta 1000 vs 1 |
|---|---|---|---|---|
| **M-TR residuo** (gruppi-2+) | 432 raw / 440 runtime | **1694** | **1679** | **-15 (~1%, weight-inert)** |
| M-TR score | - | -1790 (1790 unita') | -1780000 (1780 unita') | 1000x come atteso, stesse unita' |
| hard (boxed+room-null) | 0 | -2 (1 H0) | **0** | +1 (pulito) |
| full-day (ex-G14) | ~91,9% | 89,8% | 90,2% | +0,4pp (meglio) |
| ricorrenza unpinned | - | 76,7% | 76,3% | -0,4pp (varianza) |
| day-start anchored | ~99,3% | 99,6% | 99,1% | -0,5pp (G13 6->17 tardivi) |
| M15 same-subject consec | - | 25 / -36000 | 21 / -34800 | ~pari (leggermente meglio) |
| M20 daily quota fill | - | 76 / -91000 | 83 / -96500 | +7 (peggio, atteso: teoria sparsa) |
| M21 (2h/subject/day) | - | 90 / -91 | 100 / -102 | +10 (peggio, atteso) |
| M22 calendar compactness | - | -559000 | -560000 | ~pari |
| S17 block pair (reward) | - | 3522 / 17610 | 3504 / 17520 | ~pari |

**Lettura:** a peso 1000 il residuo M-TR scende da 1694 a 1679 (-15 gruppi su 1000x peso, ~1%).
Il solver non sterza verso il golden 440: resta ~4x sopra. Il costo KPI e' neutro (hard
addirittura 0, full-day leggermente meglio; M20/M21 peggiorano di poco perche' la poca teoria
sparsa in piu' rompe qualche blocco e lascia qualche giorno piu' scarso). Cioe' peso 1000 e'
**innocuo ma inutile** per lo scopo: non muove il residuo.

## DIAGNOSI CORRETTA (Fedora verification 02:40, SUPERSEDE la sezione M20-dominance sotto)

L'inerzia TOTALE a qualunque peso (1% su 1000x) non e' una gara di pesi persa da M-TR: a peso 1000
(1000/ripetizione) contro il marginale M20 (~1000/ora) ci sarebbe stata way piu' varianza. Inerzia
totale = la leva e' scollegata. Diagnosi read-only (causal-chain, senza altri solve):

**(1) Residuo a FINE COSTRUZIONE = 1672** (target/construction-lessons.txt via
WeeklyTemplateConstructionRealSeedTest, stessa classificazione, misurato con scripts/analyze/theory_adjacent_residual_dump.py
validato a 1679 sul run dump = identico al vincolo). vs finale 1679 (peso1000) / 1694 (peso1).
**I blocchi 2h di teoria NASCONO in COSTRUZIONE** (template builder); la LS li eredita e li smonta
appena (1672 -> 1679, +7). **La LS non li crea, li eredita.** Sorgente = construction, confermato.

**(2) I blocchi sono UNPINNED al 98,6%** (1655/1679 fully-unpinned, 24 fully-pinned = i pin
autoritativi, 0 misti). Quindi NON e' la PROMOTION a pinnarli (sotto-ipotesi promotion-pinning
CONFUTATA): la LS *potrebbe* tocarli ma non lo fa. Perche' la LS non smonta blocchi unpinned nemmeno
a peso 1000: la costruzione impacca la teoria in 2h (riempimento efficiente) e la struttura risultante
e' un ottimo locale dove smontare un blocco costa piu' (buco M20 + deviazione M32 + creare un blocco
altrove) di quanto risparmi (M-TR). Ipotesi M32+M20+M15-preserve PLAUSIBILE non verificata direttamente
(ma la cura non dipende da questo: la sorgente e' confermata a monte).

**Cura (proposta, NON prototipo ora):** agire alla SORGENTE, nel WeeklyTemplateBuilder. Aggiungere
un dedup per-(gruppo, giorno, materia-teorica) nel placement greedy: quando si piazza un corso
teorico (isTheoryForRule, specchio del vincolo), saltare i weekday dove quella materia e' gia'
piazzata per quel gruppo. Cosi' il template spalma la teoria a 1h/giorno (pratica golden) invece di
impacchettarla in 2h. NON un capping del block-length: M-TR fired su same-day same-subject a prescindere
dalla contiguita', quindi vietare il blocco 2h contiguo non basta (il builder piazzerebbe due celle 1h
separate della stessa materia). Serve il dedup per-giorno.
- **Perche' NON piccolo (resta proposta):** (a) il dedup e' una modifica alla DECISIONE di placement
(non un peso/length), tocca il loop greedy principale; (b) fill-risk: se un giorno richiede piu' ore
di teoria di quante materie distinte esistano, il dedup forza slot scoperti (M20) o spostamenti a
cascata; (c) interagisce con M15/S17 che vogliono il blocco 2h quando la materia si ripete.
- **Validazione:** WeeklyTemplateConstructionRealSeedTest misura residuo construction (1672 -> verso
golden 440) + fill/ricorrenza (non devono regredire). MA attenzione: il residuo construction potrebbe
NON tenere nella LS (la LS potrebbe re-impacchettare la teoria in 2h per fill/M15/S17). Serve full-solve
staged di conferma, non solo il test construction.
- **Se prototipato:** flag `planner.construction.theory-spread.enabled` DEFAULT OFF, letto LIVE come gli
altri flag construction; A/B gate come 463/464/468. Tracciare come TASK nuovo (la cura e' una fase a se',
posta-diagnosi).

**Netto per il verdetto sweep:** il peso M-TR e' inerte non perche' perda una gara di pesi con M20, ma
perche' la sorgente dei blocchi e' a monte (costruzione) e la LS, anche libera (unpinned) e pesante
(1000), non smonta un ottimo locale di fill. Agire sul peso e' il lever sbagliato; agire sul template
builder e' il lever giusto (ma non banale, vedi sopra).

---

## ⚠️ SUPERSEDED: "PERCHE' e' inerte: M20 (fill) domina" era incompleta
La diagnosi M20-dominance (gara di pesi) spiegava l'1% come "M20 vince", ma a peso 1000 la gara sarebbe
stata pari e ci sarebbe stata varianza. Il vero motivo dell'inerzia totale e' la sorgente a monte
(costruzione), vedi DIAGNOSI CORRETTA sopra. Il corpo originale resta come record:

## [SUPERSEDED] PERCHE' e' inerte: M20 (fill) domina strutturalmente

A peso 1000, ripetere una materia teorica costa -1000 medium (una unita' ore-1). Lasciare
un'ora scoperta su un giorno profondo costa M20 fino a **-4500/ora** (fill deficit). Quindi il
solver riempie quell'ora RIPETENDO la teoria (-1000 M-TR) perche' l'alternativa (-4500 M20) e'
peggio. Lo stesso vale a peso 1 (-1 M-TR vs -4500 M20): il fill vince sempre. Il residuo e'
**determinato dal bisogno di riempire i giorni**, non dal peso del vincolo. Alzare il peso a
1000 non cambia la classifica delle alternative, per questo il residuo non si muove.

Corollario: il **golden fa 440 ripetizioni, il run ne fa ~1680** a parita' di fill da colmare.
La differenza NON e' un problema di peso M-TR: il golden colma i giorni con un mix di materie
distinte (o una struttura) che il run non riproduce sotto l'attuale set di vincoli. Il lever
per chiudere il gap, se esiste, e' ALTROVINDI: la forma del target M20 / il mix di materie
disponibili per giorno / o e' una pratica golden che va dichiarata in input (workbook,
direttiva solo-input). Agire sul peso M-TR e' il lever sbagliato.

## Bracketing: DECISO (2 punti), peso 100 saltato

Il programma autorizzava un run intermedio a peso 100 "se il residuo e' ancora lontano da 440".
Lo e', ma il run e' **inutile** e lo si salta, per due motivi:
1. **Meccanicamente determinato.** Il residuo cresce al calare del peso (meno pressione a non
   ripetere). Quindi peso 100 >= peso 1000 = 1679 (atteso ~1685, in mezzo ai due estremi). Non
   puo' scendere sotto 1679. Predizione NON verificata empiricamente (LS stocastico), ma il
   verso e' garantito dal meccanismo M20-dominante.
2. **Robusto rispetto all'esito.** Anche se peso 100 sorprendesse (es. 800, non-monotonico), la
   raccomandazione non cambierebbe: 800 e' ancora ~2x il golden 440, ancora lontano, e la
   tesi forte (il gap e' strutturale/fill, non accordabile col peso) regge. Il punto peso 100
   non puo' ribaltare il verdetto.

Se maintainer vuole il punto empirico a peso 100 (o un probe a peso 5000, dove M-TR repeat finally
batte M20 max sul giorno piu' profondo, a costo di buchi/hard), e' un comando solo (protocollo
identico, knob diverso): vedere handoff.

## Raccomandazione (adozione resta a maintainer)

- **Non adottare M-TR per sterzare il residuo: nessun peso in [1,1000] funziona.** Il vincolo a
  qualunque peso in questo range e' o cosmetico (peso 1, residuo 1694) o innocuo-ma-inutile
  (peso 1000, residuo 1679). Flag 468 resta DEFAULT OFF.
- **Se l'intento e' "niente 2h teoria same-day" weak-best-effort**, peso 1 va bene e va
  documentato che non muove il needle (run resta ~1694): e' una dichiarazione di preferenza,
  non un vincolo effettivo.
- **Se l'intento e' riflettere la pratica golden (440)**, il lever e' a monte nel WeeklyTemplateBuilder,
  NON un peso (vedi DIAGNOSI CORRETTA sopra): dedup per-(gruppo, giorno, materia-teorica) nel placement
  greedy per stampare teoria a 1h/giorno. Tracciato come **TASK-470** (PROPOSTA gated maintainer, non
  prototipo: non banale + rischia di non tenere nella LS).
- Il vincolo M-TR come scrittto ha comunque un valore: documenta la PREFERENZA della scuola
  (teoria a 1h/giorno) e la conta come residuo calibrato, coerente col pattern M24/M25. Il fatto
  che non sterzi e' un'informazione onesta, non un difetto del codice.

## Verifica (adversarial-verification, prima di scrivere i numeri)
- **Weight-inert (1679≈1694)**: CONFIRMED. Attribuzione pulita (peso1000 pinned=3090 = peso1
  3090; pre-solve M-TR -48000/24 = 1000x peso1 -48/24, stessi 24 match pinned). Entrambi i run
  convergi normally (hard 0 / B-ROOM completato, cronologia fasi in linea col reference
  ab-on-run1). Scoped [1,1000]: pesi piu' alti NON testati.
- **Baseline 432**: CONFIRMED (riprodotto dal source, `theory_adjacent_residual.py` TEORIA
  STRETTA threshold-1 = 432, match esatto AC1). **440 runtime** = 432 + 8 (rename RA->Recuperi
  su import), re-measure meccanico documentato sessione precedente, non ri-verificato qui.
- **Skip peso 100**: PLAUSIBLE (meccanicamente determinato + robusto), non verificato
  empiricamente. Predizione ~1685 label come non verificata.
