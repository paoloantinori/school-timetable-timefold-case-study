# Predizione PRE-REGISTRATA per il ciclo live m-12 (Fedora, 2026-07-27 ~11:05 CEST)

Scritta PRIMA di vedere m12-run1/run2 (lanciati dal laptop 10:37+, HEAD 824fa2a8 =
staged/treno-2 + TASK-424 pin-netting + TASK-425 M34/M35). Base di confronto:
val419-train2-run1/2 (hard 0/0, full-day 77,0/76,6, sopra 111/118, ricorrenza
73,5/73,7, burst 45,1/45,5, day-start 95,9/96,0, gap-weeks 1813/1776).

## Attese quantitative (con la derivazione)

1. **hard = 0** in entrambi i run (M34/M35/424 sono medium-only; la meccanica staged
   e' invariata). Buchi interni 0, H16b 0, H17 0. Se hard != 0 e' un bug NUOVO, non
   una taratura.
2. **giorni-sopra: 75-95** (da 111-118). Derivazione: il checkpoint pinned A/B di 424
   ha misurato 135->81 su smoke2; il live parte gia' meglio e M33 ora vede i giorni
   misti. Golden 70 = floor pratico. Sotto 75 sarebbe sospetto (piu' rigidi del golden).
3. **full-day: 77,5-80** (da 76,6-77,0). Il pinned A/B dava +4,2pp ma su smoke non-staged;
   nel ciclo staged il template protetto limita la liberta' della LS, quindi sconto
   l'effetto a +1-3pp. Sotto 76,5 = 424 non si trasferisce al live (indagare M22/M32
   come blocco); sopra 80 = meglio del previsto.
4. **burst>2: 41,5-45** (da 45,1/45,5, golden 41,6). M34 e' start-low (100) e
   self-gated per pair: mi aspetto un calo REALE ma parziale (meta' del gap, non tutto).
   burst < 41 sarebbe PIU' rigido del golden -> campanello, non vittoria.
5. **gap-weeks: -5-15%** (da ~1780-1810 verso ~1550-1700). E' la leva M35 (peso 50,
   la piu' debole): se non si muove affatto, il knob e' da alzare via benchmark, non
   un fallimento di design.
6. **ricorrenza unpinned: 71,5-74 TENUTA** (da 73,5/73,7). RISCHIO PRINCIPALE del blocco
   M34/M35: spingere ore su settimane nuove puo' staccare lezioni dalle celle template
   (M32 e' clamp 20..500, M35 e' 50: gerarchia ok sulla carta, ma il traffico conta).
   Sotto 70 = trigger di ricalibrazione, il VERDICT deve dirlo in cima.
7. **M32 residuo: 780-850 match** (stabile). **M15: < 40 match**. **day-start: 95,5-96,5
   INVARIATO** - per il finding 426 il residuo e' H1 hard in costruzione: NESSUNA leva
   m-12 puo' muoverlo; se si muove comunque e' rumore, non merito.
8. **medium totale: NON confrontabile** con train2 (424 ha eliminato ~950k di falso
   deficit: il numero cala per ri-attribuzione anche a parita' di grid). Confrontare
   solo le componenti per-vincolo e i KPI fisici.

## Cosa guarderei per primo nel VERDICT (ordine di lettura)

(a) hard/buchi (safety) -> (b) ricorrenza (il rischio M34/M35) -> (c) sopra + full-day
(il payoff 424) -> (d) burst/gap-weeks (il payoff 425, atteso parziale) -> (e) coda
surplus +4h per il trigger di TASK-427 (post-424 il checkpoint dava ancora 6-7 giorni
vs golden 1: se il live conferma, 427 SI ATTIVA).

## Quadro di ratifica atteso per maintainer (bozza, da confermare coi numeri veri)

- Adozione 424: quasi scontata se (a)-(c) tengono (la leva rimuove un incentivo
  perverso, non aggiunge rigidita').
- Adozione M34/M35: dipende da (b) e (d): tenuta ricorrenza + calo reale burst =
  adozione ai pesi attuali; ricorrenza giu' o burst fermo = knob da benchmark prima.
- Day-start: accettare come residuo noto construction-level (426), lever in TASK-430.
- TASK-429 (donatori at-plan-con-pin): priorita' bassa confermata se il live non
  mostra anomalie nei log del rebalance.

## ESITO (confronto onesto, 2026-07-27 ~13:05, m12-run1 verificato sui raw)

| Attesa | Predetto | Reale | Verdetto |
|---|---|---|---|
| hard | 0 | 0 (buchi 0/0, pin 2.651) | CENTRATO |
| giorni-sopra | 75-95 | 87 | CENTRATO |
| full-day | 77,5-80 | 78,2 | CENTRATO |
| burst>2 | 41,5-45 | **40,2** | FUORI BANDA (sotto), il caso pre-segnalato "sotto 41 = campanello": M34 self-gated per-pair non supera mai il max golden per-pair, quindi l'aggregato sotto il golden e' differenza di distribuzione, non rigidita' per-pair - nota di ratifica, non blocco |
| gap-weeks | -5-15% | -5,7% (1.813->1.710) | CENTRATO (fascia bassa: M35 a 50 e' deliberatamente timido) |
| ricorrenza unpinned | 71,5-74 TENUTA | 73,4 | CENTRATO - il rischio principale M34/M35 NON si e' materializzato |
| M32 / M15 | 780-850 / <40 | 791 / 22 | CENTRATO |
| day-start | invariato | 96,3 | CENTRATO (come da anatomia 426) |
| medium totale | non confrontabile | -1,343M (-960k di ri-attribuzione 424) | trappola disinnescata |

7,5/8 in banda; l'unico fuori banda era pre-segnalato col suo esatto criterio di lettura.
Run2 = artefatto del path reset (hard -54 H0), root-caused da zai (profilo fresco
obbligatorio), non conta come misura. Nota di verifica: 427 riaperto (il trigger raw della
coda +4h E' scattato: 12 vs golden 1; serve anatomia pin/unpinned prima della decisione).
