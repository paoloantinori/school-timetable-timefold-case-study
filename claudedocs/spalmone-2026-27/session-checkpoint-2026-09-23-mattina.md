# Quadro del risveglio 23/09 mattina (notte del drill autonomo, tutto pushato)

## COLONNA 1 - FATTO ieri (22/09, con prove)

1. **TASK-636 cadence-pace RATIFICATO DEFAULT ON** (A/B 5/5: ON [0/0] al
   tentativo 1, banco +1,4pp ricorrenza, Servizio tecnico 16h-in-coda ->
   38h distribuite).
2. **TASK-637 theory-spread RATIFICATO DEFAULT ON** (A/B 5/5 su rc-1: ON [-1/0]
   vs OFF [-3/0], M-TR celle 2+ same-day -12,7%, ricorrenza intatta 70,0).
3. **Timefold 2.3.0 -> 2.7.0-rc-1 COMPLETO**: suite 1366/0, upgrade certificato,
   cura barrier MT-LS (N+1 party, byteman 2/2), heap-floor 3g sufficiente,
   deploy sito verificato (68 vincoli con i 6 nuovi).
4. **TASK-638 ViolationScopedSteepestFinisher FUNZIONANTE**: 0,05ms/eval
   (141k eval in 7,4s), catalog sweep 3/6 a 0-hard. Codice 9e4675cb.
5. **Catalog sweep completato**: predizioni 2 MANCATE con cause meccaniche
   (H28 composita: collisione G08 reale; 637-off: cieco proponeva mossa
   fuori VRP). Due lacune scoperte e curate dallo sweep stesso.
6. Serie: 5/9 consegne live zero-hard (con 636+637 ratificate il pavimento sale).

## COLONNA 2 - IN STATO DI ATTESA

- laptop: sta committando i fix del catalog sweep (same-date swap + VRP gate).
- TASK-641 aperto: consolidazione delle 7 copie del resolver metamodel.
- Setaccio upstream: rc-1 STABILE (zero breaking, solo PR 2674 da guardare
  prima del 2.7.0 finale del 29/09).

## COLONNA 3 - DECISIONI PER maintainer

1. **Ciechi sui non-chiusi del catalog sweep** (ordine tuo gia' registrato):
   - H28 G13 02-12 (stati 637-on + integrato-1): composita, la collisione
     G08 rende la single-move insufficiente. Cieco con finestra allargata
     alla settimana di T15.
   - 637-off H0+H32 (G02 09-17): la boxed e' Servizio tecnico, serve un
     cieco con VRP visibile.
2. **Classi-mossa mancanti** (ordine tuo): subpillar/mass moves (PR 2610 in
   rc-1) e list-variable families mai toccate. Da valutare se il nostro
   dominio basic-variable le puo' usare.
3. **Notifica referente** (tua): pronta da giorni.
4. **638 produzione**: il banco e' validato ma il wiring nel delivery stage
   (pre-save del wrapper) e l'A/B di adozione restano da fare.

## SPIKE del catalog sweep (le due lacune curate)

- **Swap same-date**: la grammatica originale escludeva le swap intra-giorno;
  aggiunta per lo stato 2 (H28 G13).
- **Gate VRP**: senza, il finisher proponeva cure out-of-model (valori fuori
  dal value range della lezione, mascherati dal preserve-current hack).
  Entrambe scoperte dallo sweep stesso: il meccanismo trova i propri buchi.

## AGGIORNAMENTO NOTTE (cieco H28 + classi-mossa, REPORT blind-and-moves/)

**H28 E' CURABILE**: swap M-Lav@9 <-> M-TS@12 (stesso docente T15, stesso gruppo, stesso giorno). Dopo la swap: il pattern A..B..A sparisce perche' il "between" diventa TS (materia diversa da Lavorazioni). ARBITRATO JAVA sul capture reale: -1 -> 0-hard. WITNESS: il giorno G13 19/02 dello stesso capture ha la STESSA forma ed e' PULITO (la scuola pratica questa soluzione).

**LA CLASSE MANCANTE E' DI TARGET, NON DI MOSSE**: la mossa vincente e' una banale SwapMove gia' nella grammatica. Ma il 638 muove solo i TARGET (i fatti della violazione: D@8 e D@11) e la lezione "c" di mezzo (M-Lav@9) non e' mai target. Cura: estendere collectTargets per includere le lezioni CONTESTO dei pattern-match, oltre ai fatti. Non serve una mossa nuova.

**CLASSI-MOSSA CENSITE (jar rc-1)**: MassChange/MassAssign sono factory one-shot GIÀ USABILI dal 638 (Sample.of). SubPillar richiede la Neighborhood API config (non one-shot). 2opt/tail-swap sono list-variable-only (escluse dal nostro dominio basic).

## ESTENSIONE COLLECTTARGETS (notte fonda, 6fb0bc14)

La cura indicata dal cieco e' IMPLEMENTATA e VERIFICATA: le lezioni CONTESTO (la "c" di mezzo) ora sono target. Il catalog sweep passa a 4/6 a 0-hard (637-on chiude: la swap M-Lav@9<->M-TS@12 viene generata e applicata in 551ms). ZERO regressioni sugli altri 5 stati. Suite 1370/0.

## DIAGNOSI NOTTURNA 2 (857313d7)

**H28 integrato-1 = LA STESSA cella del 637-on** (G13 12/02, T10@8/@11 + T15@9), NON strutturale (golden: zero A..B..A su tutto il CSV, TASK-448 verificata), **COMPOSITA SUL TIER STANZE**: la cura timeslot esiste (swap M-Lav@9<->M-Cad@12) ma nel pipe+CH-greedy di integrato-1 le stanze collidono (H2 -1000). Nel 637-on export room-faithful la stessa swap era pulita. Classe mancante: swap con riassegno stanza.

**WIRING PRODUZIONE 638 (design, senza implemento)**: innesto post-HRF pre-save TASK-526, una volta per consegna. Director: nessun vivo al punto → convenzione passWithOracle (director fresco). Prerequisiti: iniezione factory, wrapper, flag opt-in + banner. Decisione tier stanze: opzione a (oracolo-rifiuta) raccomandata.

## 638 PRODUCTION WIRING COMPLETATO (27a515c2, notte fonda)

Il ViolationScopedSteepestFinisher e' nel ciclo di produzione: post-HRF pre-save TASK-526, una volta per consegna, OPT-IN DEFAULT OFF (flag planner.construction.violation-steepest.enabled). Oracolo full-hard con tier stanze = opzione A (H2/H29 nell'oracolo, mosse room-creanti rifiutate). Suite 1370/0, banco -2->0, no-op flag-off. RESTA OPT-IN fino a ratifica.

## 638 RATIFICATO (b90929d2, prima dell'alba)

Il ViolationScopedSteepestFinisher e' DEFAULT ON. A/B 5/5: ON [0/0] vs OFF [-2/0], la swap
applicata in produzione e' ESATTAMENTE la cura del blind puzzle (Lavorazioni <-> TecnicheSaldatura,
-1->0 in 414ms). La catena cieco -> arbitraggio -> meccanizzazione -> adozione chiusa END-TO-END
sullo stesso esempio. Serie aggiornata: 6/10 consegne zero-hard con 636+637+638 ratificati.

## Regole della notte rispettate
Nessun flip non ratificato, nessun invio esterno, catena 8/8 completata
con gates, ratifiche delegate SOLO a 5/5 evidenza completa.

## AGGIORNAMENTO 23/09 ~06:50 (pre-riavvio maintainer, tutto committato e pushato)

1. **Ricerca exhaustive COMPLETATA** (d3a5906d): claudedocs/research_violation-directed-repair_2026-09-23.md.
   Verdetto: approccio con precedenti canonici (GLS/QuikFix/fix-and-optimize),
   collocazione finisher NON workaround; forma "dentro il loop" = mini-GLS nel
   decider, tracciata come opzione.
2. **Proposte upstream trackate** (14cc82f7): TASK-643 PR exception-safe
   executeTemporaryMove, TASK-644 PR accessor tipizzato metamodel, TASK-645
   FLAGSHIP ViolationScopedMoveProvider, TASK-646 MT-LS extension point,
   TASK-647 stabilita' preview API filtri, TASK-648 constraint match on-demand,
   TASK-649 pubblicazione difensiva PATAT (al posto del brevetto: verdetto
   ricerca, prior art densa), TASK-650 unslop testi pubblicati README+webapp.
   Ordine consigliato: 643+644 come apri-dialogo, poi 645.
3. **TASK-641 aperto e IN CORSO**: room-aware grammar del 638 (swap/change +
   riassegno stanza via Moves.compose). Diagnosi notturna: la H28 di integrato-1
   e' composita sul tier stanze. Design completo nel task (secondo passaggio
   pigro, stanze libere allo slot dest, oracolo pieno). Task 642 = la
   consolidazione metamodel citata ai checkpoint NON esisteva: creata ora.
4. **laptop IN VOLO**: mandato integrato-7 (ciclo fresco stack pieno 636+637+638,
   capture + catalog TASK-634 + serie 6/10 -> x/11, SNAPSHOT-FIRST:serie+catalogo,
   launch guard lsof+banner). Spedito ~06:45 al pane wK:p1, delivery verificata
   (status working). I suoi artifact arrivano via git pull. Verdetto atteso
   in ~1-2h (ciclo con multi-start).
5. Javadoc stand-in byteman clarificato (1ec04297, comment-only).

## AGGIORNAMENTO 23/09 ~08:05 (handoff: fedora-box in chiusura, laptop autonomo)

**Pushati** (b9113f2b): TASK-650 unslop testi (~90 stringhe + 5 cure semantiche
a mano: H34 invertita en+it, manual en stantio M28, H17 modale, H28 registro,
mcps it; TASK-651 ortografia) e TASK-641 WIP room-aware grammar (flag
strict-literal DEFAULT OFF, banco 3/3: 637-off -3->0, integrato-1 -52->-41
con la swap blind-puzzle +ROOM R07, 636-off identico; /simplify 4-reviewer
applicato; TASK-652 = consolidamento 8 copie predicato stanza).

**CODE-REVIEW HIGH DELLA 641: MORTO con la chiusura del fedora-box (fork in
background). DA RIESEGUIRE su Fedora al ritorno, PRIMA della ratifica.**
Suite idem (delegata al laptop, coda PASSO 2).

**laptop (coda autonoma spedita ~08:05, pane wK:p1)**: PASSO 0 completare
integrato-7 (verdetto+capture+catalogo+serie, in volo); PASSO 1 TASK-650
screenshot Docs prima (app corrente pre-unslop) / dopo (pull b9113f2b +
rebuild) + deploy sito; PASSO 2 suite 641 (8g, EXIT 0); PASSO 3 banco
regressione 641 tre stati flag ON; PASSO 4 A/B adozione 641 (profili freschi
task641-off/on, criteri pre-registrati, NESSUN flip); PASSO 5 prep upstream
TASK-643 (repro minimale executeTemporaryMove su branch usa-e-getta, bozza PR
in un doc, NESSUN invio esterno); PASSO 6 watch 2.7.0 finale/PR 2674 (29/09).
Regole: un solve alla volta, launch guard lsof+banner, memoria pulita fra i
run, laptop measures only, decisioni riposte nei VERDICT.

**Al ritorno (Fedora)**: leggere i VERDICT del laptop via git pull; rieseguire
code-review high 641; ratifica 641 SOLO a gates+evidenza completi; TASK-642
consolidamento metamodel; TASK-652 idem predicato stanza; 653+ preparazione
upstream dopo ok maintainer.
