# Audit: cosa e' stato fatto negli ultimi 2 mesi (2026-07-04 .. 2026-09-04)

Data analisi: 2026-09-04. Metodo: estrazione delegata (2 agenti Sonnet) su backlog, commit e documenti di campagna, con verifica a campione dei numeri portanti eseguita di persona. Ogni numero citato ha fonte; dove due fonti divergono sono riportate entrambe.

Questo documento risponde a: **cosa e' stato fatto**, **dove siamo rispetto a 0hard**, **cosa resta aperto**. Le raccomandazioni sono nel documento gemello `roadmap-0hard-breve-media-2026-09-04.md`.

---

## 1. Quadro d'insieme in un paragrafo

In due mesi il progetto ha completato la trasformazione da "singolo solve + metaeuristica" a **pipeline orchestrata multi-start con repair deterministici**: staged cycle a 5 fasi, multi-start GRASP con pick-best, e una batteria di finisher deterministici (ExactFinisher, RoomTailFinisher, EmptyDayFinisher, QuotaFillRepair v2, SurplusHarvest) che chiudono la coda che la ricerca locale non chiude. Il risultato di punta e' uno **0hard end-to-end raggiunto il 25/08** (ciclo RoomTailFinisher). Pero' da fine agosto il **metro si e' alzato**: quattro nuovi vincoli hard (H31, H32, H33, H34) sono entrati in produzione, e le consegne recenti sono tornate nella banda **-13/-16 hard**. Il percorso a 0hard oggi non e' piu' un problema di vincoli ne' di metaeuristica (entrambe le famiglie sono state dichiarate esaurite in due documenti indipendenti) ma di **tre buchi meccanici localizzati** piu' un **bug di fiducia della persistenza** che va chiuso per primo.

---

## 2. Traiettoria dello score hard (i numeri che contano)

Formula corrente: `hard == boxed*2 + room-null*1` (dalla ratifica H29). "Consegna" = lo score del risultato finale del ciclo.

| Data | Run / evento | hard | Note | Fonte |
|---|---|---|---|---|
| 27/07 | gate419 (staged adottato) | **0** | ricorrenza unpinned ~73,5-74 | CLAUDE.md "Modalita' di solve" |
| 31/07 | combined 463+464 | 0 (n=2) | lab-day-consolidation + co-presenza | commit 4b11b332 |
| 01/08 | combined run3 | -6 | residuo | CLAUDE.md "RATIFICA 463+464" |
| 02/08 | ExactFinisher A/B | 0/0 | coda boxed deterministica | CLAUDE.md "RATIFICA TASK-478" |
| 05/08 | source=input certification | 0 | path input | commit c1139403 |
| 16/08 | rehearsal production input | 0 (primo tentativo, ~23 min) | path input | commit e103d8f3 |
| 19/08 | m13-final (senza golden) | **0** | medium -1.028.908, ricorrenza 74,2 | `notte-0hard-2026-08-19.md` |
| 24/08 | TASK-550 pre-cura | **-721** (altro doc -713) | muro boxed: domanda fantasma Stage | commit ec7ec7bf / c3b0fd0c (DIVERGENZA riportata) |
| 25/08 | cure congiunte | -152 -> -83 | diagnosi-dai-dati + golden-first | commit c3b0fd0c |
| **25/08** | **RoomTailFinisher ciclo** | **0 hard end-to-end** | **H29 83->0; ULTIMO 0hard documentato** | `task550-roomtail/VERDICT-2026-08-25.md` |
| 26/08 | H34 STRICT-FIRST | -14 | H34 entra in produzione | commit 80715843 |
| 29/08 | pre-booking v2.1 gate | -81 -> -12 | H17 74->0 | commit 68fa7ace |
| 02/09 | SurplusHarvest ON | -13 | | `task577-results/ciclo-sh-on/score.txt` |
| 03/09 | ciclo v10 | -16 | primo ciclo stabile end-to-end col pass | `task577-results/ciclo-v10/score.txt` |
| 03-04/09 | ciclo v11 | -13 | H0 0, H29 -1, H34 -5 | `task577-results/ciclo-v11/score.txt` |
| 04/09 | ciclo v11-n2 | -13 | H0 -4, H29 0, H34 -6 | `task577-results/v11-n2/score.txt` |

**Lettura della traiettoria.** Il punto di svolta e' il 24-25/08: il muro a -721 (domanda fantasma Stage) e' stato risolto in un giorno con cure di input golden-first, portando a **0hard end-to-end il 25/08**. Da li' la qualita' e' risalita, ma il 26/08 H34 e' entrato in produzione e ha alzato il metro: le consegne sono scese in banda -13/-16 e non sono piu' tornate a 0. Non e' una regressione del solver: e' lo stesso orario misurato con un metro piu' rigido.

---

## 3. Il nodo centrale: il claim "0hard garantito" e' invecchiato col vincolame

Questo e' il singolo rilievo piu' importante dell'audit, verificato su due fonti indipendenti.

- La milestone **m-13** ("Genericita' e riuso multi-anno") e' dichiarata **SBLOCCATA / APPROVATA** l'11/08, con il claim: *"p(0-hard) = 32/32 = 1,0, Wilson95 [0,89, 1,0]; il multi-start consegna 0-hard statisticamente garantito"* (`backlog/milestones/m-13 - ...md`, sezione "Dato che regge la decisione"; raw in `task480-results/badseed-frequency-laptop-2026-08-09/`).
- **MA** quella misura N=32 e' del **9-10/08**, cioe' **precedente** all'introduzione di quattro vincoli hard che oggi sono DEFAULT ON:
  - **H32** quota-fill HARD (TASK-532, 19/08)
  - **H31** start-of-day HARD (TASK-529, 20/08)
  - **H33** per-group hard day floor (TASK-536, 20/08)
  - **H34** empty declared day HARD (TASK-557, 26/08)
- Conferma indipendente: lo 0hard storico del 25/08 girava su jar **pre-H34**; ricalcolato col metro attuale farebbe **-21 H34** (censimento in backlog TASK-577, 1/09). Ovvero: neppure il miglior run storico sarebbe 0hard col metro di oggi.

**Conseguenza onesta da dichiarare:** la proprieta' "0hard statisticamente garantito" e' vera per il vincolame dell'11/08, **non e' stata re-validata** sul vincolame corrente. La milestone m-13 va trattata come *proceduralmente chiusa ma da re-validare*, non come fatto acquisito. Questo non invalida il lavoro fatto (il multi-start resta la via giusta), ma il claim non si puo' piu' citare come garanzia corrente.

---

## 4. Cosa e' stato adottato (ratifiche DEFAULT ON nella finestra)

Tutte con la forma opt-out standard (flag `null`/absent -> ON, solo `"false"` disarma). Le ratifiche sono il canale reale di adozione (commit `feat(TASK-...)` con DoD nel body).

| Data | Meccanismo | Effetto misurato |
|---|---|---|
| 31/07 | TASK-451 blocco prima-ora slot0-1 | hard -173 -> -108/-109; day-start 16->2; costo 18 H17 |
| 01/08 | TASK-463 lab-day-consolidation + TASK-464 co-presenza | hard -112 -> -17/-18 |
| 01/08 | TASK-469 M-CPS curriculum post-stage | converge verso target 6h |
| 02/08 | TASK-478 ExactFinisher | coda boxed H0 deterministica |
| 02/08 | TASK-480 multi-start (procedura) | A/B n=3, 2/3 a 0hard |
| 03/08 | TASK-483 multi-start in prodotto | pick-best, stop al primo 0-hard |
| 04/08 | TASK-486 M-TC per-retry | cura room-tier solo sui retry |
| 14/08 | TASK-480 multi-threaded LS | **-25% wall**, 0hard |
| 15/08 | TASK-497 M20 pool-cap; TASK-498 skip-doomed-room; H30 Sicurezza | |
| 16/08 | TASK-500 adaptive weights forma B | wall tail-capping |
| 17/08 | refiningForDelivery (quiescenza) | fix falsi segnali |
| 18/08 | TASK-517 golden-first esteso ai piani | regola di gate |
| 24/08 | TASK-543 single-writer MT-LS | divergenza dimezzata 12542->6041/12821 |
| 25/08 | TASK-550 cure input + RoomTailFinisher | **0hard end-to-end** |
| 27/08 | TASK-556 QFR v2; TASK-558 M22 quota-aware; TASK-560 EmptyDayFinisher | |
| 29/08 | TASK-564 pre-booking v2.1 (con REVISIT-FLAG) | tier -81 -> -12, H17 74->0 |
| 02/09 | M38 pair-deviation weight=1 (TASK-576) | qualita'; builder pair resta opt-in |

## 5. Cosa e' stato bocciato (con motivo, nella finestra)

Selezione delle bocciature che delimitano il perimetro delle vie ancora aperte:

- **Metaeuristiche: famiglia CHIUSA DEFINITIVAMENTE** (TASK-569, 29/08): nessuna configurazione offre P(0hard)/wall migliori del baseline; le leve wall dimostrate sono gia' ratificate. Re-benchmark 27/08 conferma il ranking di agosto.
- **TASK-538 quasi-tier (BendableScore): CHIUSO NEGATIVO** (2/09): il landscape H32/H34 e' monotono per-ora, il collo e' il **campionamento** (mossa concatenata ~15Mx il budget LS), non la gerarchia dei tier. La cura e' la mossa deterministica lungo-raggio, non un tier intermedio. Chiude in cascata la milestone-2 di TASK-564.
- **TASK-481 composition hole-closer**: shell-game depth-3 (SUSTAINABLE 0/6); fork a multi-start.
- **TASK-482 Kempe-chain**: 0/9 sustainable, NO.
- **TASK-284 VND**: non adottato; ranking agosto confermato.
- **F6-a / flip congiunto builder pair (TASK-576)**: bocciati dal guard real-seed (+18 celle sotto-quota).

**Conclusione di perimetro:** sia la via "metaheuristica" sia la via "cambia il metro (quasi-tier)" sono state chiuse con evidenza. La via rimasta e' la **meccanica deterministica sulle celle residue**.

---

## 6. Stato per famiglia hard (doc piu' recente)

| Famiglia | Stato | Fonte |
|---|---|---|
| **H0 / boxed** | Chiusa deterministicamente da ExactFinisher. Residuo strutturale piccolo: G14/T16 Confezione + T01/Servizio tecnico | CLAUDE.md TASK-478; backlog TASK-577 censimento |
| **H29 / room-null** | **CURATA** da RoomTailFinisher (83->0). v11-n2: 0 lezioni senza stanza | `task550-roomtail/VERDICT`; `v11-n2/VERDICT-raw.md` |
| **H17** | Coperta dal multi-start nei bacini buoni (0-3), esplode nei bacini GRASP cattivi (45 su s25) | backlog TASK-577; `v11-n2/analysis.json` |
| **H32** | Marginale post-cure (0-4 celle, spesso G13 1h) | backlog TASK-577 censimento |
| **H33** | Residuo 1-3 celle; TASK-574 OneOffFinisher (To Do) lo cura ma round8 ha shell-game | analysis.json cicli v10/v11 |
| **H34** | **FAMIGLIA DOMINANTE residua**: 5-6 celle strutturali, invarianti al seme, 83-100% in mag-giu | backlog TASK-577; `v11-n2/VERDICT-raw.md` |

---

## 7. Stato della linea calda (TASK-574 / 576 / 577 / 578 / SurplusHarvest)

- **TASK-577 (SurplusHarvest): CHIUSO Done (4/09).** Censimento completo: H34 mag-giu e' **strutturale** (5-9 celle invarianti al seme), nessun lock duro, e' l'assenza di un **mover lungo-raggio** (il surplus parcheggiato a set-nov e' l'ora che manca a giugno). Cura costruita e iterata in 10 versioni in una notte (v0.1 -> v11), ogni round un gate isolato. Ciclo v10 = primo ciclo stabile end-to-end col pass armato.
- **SurplusHarvest: costruito e sano, ma non ancora convertitore di consegne.** Nei cicli v11/v11-n2 tutte le passate al confine danno `moves=0`: **le celle H34 che la consegna mostra non esistono ancora quando il pass gira** (si formano dopo, lungo i tentativi successivi). La finestra vera e' **sul winner nel wrapper multi-start** (dopo restore-best, prima della consegna).
- **TASK-576 (pair-cells): CHIUSO Done.** M38 ratificato DEFAULT ON a peso 1; builder pair resta opt-in (bocciato dal guard real-seed). Scoperto e corretto il confound jar-stantio.
- **TASK-578 (VRP cache-stale): migrazione eseguita** (`isSlotLegalFresh` O(1), commit 6ad3767e). Deep-fix epoch dell'hot-path tracciato, in attesa di decisione.
- **TASK-574 (OneOffFinisher): To Do, alta priorita'.** Target [0 puro / -7 dichiarative]. Round8: chiude H7+H29 ma apre H17/H32/H34 (-11 vs -7 atteso) = shell-game senza guardie contiguita'/quota sul donatore. I 3 one-off sono identificati a cella esatta.

---

## 8. Tre porte aperte meccaniche + un bug di fiducia (le vere priorita')

Questi sono i blocchi residui dichiarati, in ordine di priorita' che l'evidenza suggerisce:

1. **TASK-526, BUG BLOCCANTE di fiducia della persistenza (To Do, alta).** Un run puo' mostrare 0hard in-memory mentre il DB persiste 2859 lezioni senza stanza (H29); la UI legge il DB e mostra il difetto. Famiglia TASK-509 (score-vs-DB divergence) all'hard tier. **Finche' questo non e' chiuso, ogni "0hard" dichiarato va verificato sul DB, non sullo score in-memory.** Precondizione di fiducia di tutto il resto.
2. **Istanza orfana della catena post-B-ROOM** (scoperta 4/09, con antitesi): `runRoomSolveStage` ritorna una nuova istanza (`finalSolution`), ma la catena post-B-ROOM (QFR-2, EDF, SurplusHarvest) gira sull'istanza vecchia (`roomProblem`); nessun `saveSchedule` salva `roomProblem` dopo la catena. Se confermato, **le mosse dei finisher post-B-ROOM potrebbero non persistere**, il che spiegherebbe perche' il residuo H34 resta nelle consegne nonostante i pass chiudano celle nei log. Antitesi trovata: il gate EDF 26/08 verifico' le celle nel DB. Da chiudere: quale istanza conserva `AttemptOutcome`.
3. **Winner-tail nel wrapper multi-start**: la finestra corretta per SurplusHarvest (dopo restore-best, prima della consegna definitiva), dove le celle esistono davvero. Le 10 passate `moves=0` del ciclo v11 lo dimostrano.
4. **Quinta via OOF `relieveOverloads`** (2 assertValueRange non fatali in v11-n2): il relief sposta blocker senza value-range check fresco. Diagnosi localizzata, guardia da aggiungere.

---

## 9. Stato del backlog (disallineamento da sanare)

Rilievo di igiene: **molti task che i commit mostrano come ratificati risultano ancora "To Do" nel backlog** (532, 535, 536, 542, 556, 558, 560, 574, 526...). Conteggio attuale: **118 task aperti** totali; m-11: 2, m-12: 5, m-13: 17. Il backlog non e' allineato allo stato reale del codice: va fatto uno sweep di chiusura per i task gia' ratificati, altrimenti la vista "cosa resta da fare" sovrastima il lavoro e nasconde le vere priorita'.

---

## 10. Decisioni pendenti e conferme esterne

- **Pacchetto domande a referente** (3 domande, rev 27/08 + notifica divergenze input-vs-golden 31/07): giorni corti 1/G08 (TASK-497), ore G09 fantasma (cura 550), semantica stanze. Non bloccanti (golden-first con notifica a posteriori), ma da spedire.
- **TASK-580**: hook lato laptop per il gate di lancio livello-3 (bassa priorita').
- **REVISIT-FLAG TASK-564 v2.1**: l'A/B cross-basin TASK-565 e' risultato non-confermante per costruzione dei bacini; da riconsiderare se emerge evidenza cross-basin.
- **Deep-fix epoch cache VRP** (TASK-578): spike TRACKED_FULL_ASSERT in attesa di decisione maintainer.

---

## Nota metodologica

Estrazione eseguita da due agenti Sonnet (backlog+commit, e campagna 0-hard) con obbligo di citazione della fonte per ogni numero. Verifica a campione eseguita di persona sui numeri portanti: (a) contraddizione m-13 confermata confrontando le date dei commit dei vincoli hard con la data della misura N=32; (b) ultimo 0hard 25/08 confermato dal VERDICT RoomTailFinisher; (c) TASK-526 letto direttamente nel backlog. Divergenze trovate e riportate senza mediazione: hard pre-cura -721 vs -713; medium v11-n2 -1.312.519 (score.txt) vs -1.298.841 (analysis.json, divergenza nota TASK-509); pin golden H34=0 nel codice vs nota task-577 che cita ancora -1hard.
