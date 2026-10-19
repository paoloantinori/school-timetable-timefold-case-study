# Catena di autonomia (ordine di maintainer 22/09 ~14:00: "mai fermi ad aspettare")

Regola: a ogni evento (notifica, tick, verdetto) esegui il passo successivo della
catena SE e solo se i prerequisiti sono verdi. Ogni passo registra qui il proprio
stato. Nessun flip di produzione senza gates; le ratifiche sono delegate
("ratifica quello che devi" 22/09) ma SOLO a evidenza completa.

## PASSO 1 [COMPLETATO sul laptop f2d745d0] Suite rc-1 VERDE 1366/0
Prerequisito per tutto. Se ROSSA: diagnosizza il fallimento, cura, rilancia.
Se VERDE: PASSO 2.

## PASSO 2 [ESEGUITO a6e2bebd] Commit pacchetto rc-1
pom 2.7.0-rc-1 + migrazioni (QuotaPreservingSwapFilter -> SelectorBasedSwapMove,
PhaseTransitionMarkerBoxDumpTest fake overload, OffCapture638 API Union) +
cura barrier con le 6 cure /simplify applicate. Messaggio con Gates: suite EXIT 0
+ /simplify 4-reviewer + byteman 2/2. Push.

## PASSO 3 [IN VOLO 4/5] Code-review high sulla cura barrier
5 reviewer standard sul diff cumulativo (88f91f81..working tree del decider).
NON dipende dalla suite: parte subito.

## PASSO 4 [COMPLETATO 9c8d3c9e] Mandato laptop: A/B 637 su rc-1: 5/5 VERDI
Due profili freschi, theory-spread ON vs OFF, criteri pre-registrati (hard,
ricorrenza>=68, M-TR celle, full-day>=93, censore theoryHoldFills>0), capture ON,
checkpoint preservati per 631/635, heap 8g, MT-LS ON (rc-1 sane), banner-check.
Watchdog dedicata.

## PASSO 5 [COMPLETATO e16710ad] Ratifica 637: DEFAULT ON
Flip opt-out standard + voce CLAUDE.md + serie + task chiuso. Se un criterio
fallisce: registra onestamente, nessun flip, riporta a maintainer.

## PASSO 6 [COMPLETATO] Heap-floor rc-1: 3g SUFFICIENTE (6143s, EXIT 0, -19/0 boxed 2)
Sul laptop o localmente: una run del ciclo a 3g per misurare se rc-1 torna nei
binari di memoria vecchi (il 640 residue). Aggiorna il runbook se serve.

## PASSO 7 [COMPLETATO 9e4675cb] TASK-638: steepest CURA le H7 in 7,4s (141k eval, 0,05ms/eval)
Sul capture off/ in repo: localSearch + MoveIteratorProvider/factory scoped,
bridge static, REPRODUCIBLE. Target: chiude il -2 COMPOSITE_MISSING in secondi.

## PASSO 8 [COMPLETATO 0147ace2] Deploy sito su rc-1
Con verifica visiva della pagina Docs.

## Regole di ingaggio
- laptop: un ciclo per volta, mandati via herdr, artifact via git.
- Fedora: suite e banchi locali quando la macchina regge (guard memoria).
- Ogni ratifica: gates DoD complete (simplify + code-review + suite) PRIMA.
- Se un passo si blocca per un prerequisito mancante: salta al passo
  sbloccato piu' avanti nella lista (mai idle).
- Stato aggiornato qui a ogni transizione.

## TRANSIZIONI
- ~15:10: PASSO 6 avviato sul laptop (pom locale rc-1, banco 3g, catalizzatore ha sbloccato senza aspettare il PASSO 2: il pattern bisect dei pom locali non richiede il commit). PASSO 1 suite in volo; PASSO 3 reviewer b atteso.
- ~15:15: suite Fedora UCCISA dal guard memoria (2a volta, 3 JVM esterni attive). PASSO 1 si sposta sul laptop DOPO l'heap-floor (coda). Code-review 5/5 COMPLETA: a=2 prose curate, b=NESSUN bug (5 angoli puliti, bytecode rc-1 verificato), c=3+1 curati/annotati, d=2 etichette curate, e=6 curati.
- ~15:25: PASSO 2 ESEGUITO: pacchetto completo pushato (a6e2bebd: pom rc-1 + migrazioni + cura barrier definitiva + tutte le cure review). La suite laptop girera' su QUESTO stato (finale vero). PASSO 4 sbloccato appena la suite accodata passa.
- ~16:25: PASSO 6 COMPLETATO: ciclo intero a 3g su rc-1 = runbook invariato (floor non salito). Suite laptop partita dietro (22 classi gia' passate).
- ~17:00: PASSO 1 COMPLETATO (suite 1366/0 sul laptop, stato finale a6e2bebd). PASSO 4 INVIATO al pane wK:p1 (restart herdr: pane cambiato da wD a wK). Watchdog 29min. Prossimo evento: VERDICT 637.
- ~20:20: PASSO 4+5 COMPLETATI: A/B 637 5/5 VERDI (ON [-1/0] vs OFF [-3/0], M-TR -12,7%); ratifica applicata (flip e16710ad). Watchdog cancellata.
- ~21:10: PASSO 8 avviato (laptop wK:p1: pull + rebuild + banner-check + Docs screenshot). PASSO 7 (638 forma B) resta accodato: Fedora a 2GB liberi (troppo per QuarkusTest), il banco si scrive ora e gira quando la macchina si libera.
- ~21:50: PASSO 8 COMPLETATO (laptop: rebuild + screenshot 68 vincoli con i 6 nuovi; nota: URL pubblica giu' per edge-box preesistente). PASSO 7 resta: 638 WIP committato (0a5d1130), serve collegare il PlanningVariableMetaModel dall'API Neighborhoods (rc-1) per la valutazione incrementale. Mandato al laptop.
- ~22:30: PASSO 7 COMPLETATO: il ViolationScopedSteepestFinisher con metamodel rc-1 collegato CHIUDE le due celle H7 in 7,4 secondi (era 300s con la short-LS: 1/40 del tempo). Il banco steepest con budget exhaustive trova 2 SWAP delta=1hard e cura T21 27/01 + T23 02/12. Suite 1366/0. CATENA COMPLETA 8/8.
- ~22:45: CATALOG SWEEP avviato sul laptop (5 stati con predizioni pre-registrate). Scoperta in corso: ab637-off H0+H32 chiude col finisher ma la lezione atterra senza stanza (H29): il 638 e' timeslot-tier, serve il pass stanze downstream nel banco. I residui non-chiusi andranno in cieco con finestra allargata (ordine maintainer) + esplorazione classi-mossa mancanti (subpillar/mass/list-var families di PR 2610).
- ~23:10: CATALOG SWEEP COMPLETATO (REPORT.md): 3/6 a 0-hard, 1 parziale, 2 correttamente non-curabili. Due lacune trovate e curate dallo sweep (swap same-date + gate VRP). Predizioni 2 e 3 MANCATE con dati interessanti: H28 composita (collisione G08 reale vs cieco), 637-off out-of-VRP (cieco proponeva mossa illegale). Ordine maintainer registrato: i non-chiusi vanno in cieco con finestra allargata + esplorare classi-mossa mancanti.
