# Campaign status, 2026-08-13 (revisione indipendente batch laptop 08-08..12 + quadro decisioni da accendere)

Doc di orientamento. Sostituisce `campaign-status-2026-08-05.md` per due fatti nuovi:
1. Il laptop ha prodotto un batch autonomo di lavoro (08-08..08-12, commit `965d1349`..`56d8c916`)
   che Fedora ha revisionato indipendentemente il 2026-08-13. Esito: **tutte le conclusioni
   reggono sui raw, nessuna da reconsiderare.** m-13 APPROVATO ratificato.
2. Il batch ha risolto o escluso quasi tutti i prototipi flag-gated default-OFF rimasti aperti.
   Resta una domanda aperta per maintainer: **quali accendere.**

## Revisione indipendente del batch laptop (2026-08-13)

Ogni conclusione e' stata verificata meccanicamente (codice compilato/test girato, score letti
dai raw uno per uno, identity-gate workbook eseguiti), non da memoria.

### Codice di produzione (3 commit, tutti sani)

| commit | verdetto | prova |
|--------|----------|-------|
| `56d8c916` TASK-480 Fase 2 multi-threaded LS decider (449 righe) | SANO, NON cablato in produzione (grep: zero riferimenti fuori dal pacchetto). Scaffold corretto per eventuale moveThreadCount futuro. | `MultiThreadedLocalSearchSpikeTest` 2/2 verde |
| `7551b7dc` volatile thread-safety (Lesson cache + 3 statiche) | CORRETTO. 5 volatile, zero cambio logica. Inerto su single-thread. | diff ispezionato |
| `54c6584f` M34/M35 loud auto-disable banner | CORRETTO, strettamente additivo. | diff ispezionato |

### Conclusioni strategiche (tutte confermate sui raw)

| verdetto | esito verifica |
|----------|----------------|
| **TASK-449 RULED OUT** (entrambi i regimi) | Confermato. OFF 0hard vs ON -5hard (re-run pulito, 3 tentativi, nessuno a 0hard) su golden e su input. 0-hard binario: ON non raggiunge l'obiettivo. Pre-booking mono-room-tag over-constrain il bacino. |
| **TASK-470 NEUTRAL** | Confermato. OFF 0hard/-1284k vs ON 0hard/-1230k. Entrambi 0hard, medium -54k suggestivo ma entro ~2x stability spread 28k. "Resta default-OFF, non ruled out" prudente e corretto. |
| **Parallel multi-start NEGATIVE** | Confermato e importante. Speedup ~1,2x (CPU-bound su 12 core condivisi, Amdahl). Seriale con early-stop superiore nel caso comune. Esclude un vicolo con evidenza. |
| **M-TR (468) NEUTRAL/weight-inert** | Confermato. Diagnosi onesta: M20-dominance SUPERSEDED da "residuo nasce in COSTRUZIONE" (1672 a fine construction vs 1679 finale; blocchi unpinned 98,6%). Peso inerte perche' la sorgente e' a monte. |
| **m-13 APPROVED** | Confermato sui raw. Batch laptop N=32 (seed 1-16 + 17-32) = 32/32 a 0hard, letti uno per uno. I 2 run fallenti sono nel vecchio batch Fedora 08-03 (pre-M-TC per-retry), NON nel pool N=32. Caveat onesto esplicito. |
| **TASK-471 workbook (sheet 22 + 21)** | Confermato. Identity-gate passano (EXIT 0): theory-block 196 coppie round-trip fedele; cadence 245 coppie >= 10h filtro match Java. |

### Punto di attenzione metodologico (non un errore)

Il batch Fedora 08-03 con 2 fallimenti convive in `task480-results/` col pool N=32 citato da
m-13. Il doc m-13 e' pero' preciso nel path (cita `-laptop-2026-08-09/`), quindi nessuna imprecisione:
solo una prossimita' di directory che richiede cura al lettore.

## Stato attuale: cosa e' ADOPTED DEFAULT ON (invariato dal 08-05)

Catena di ratifiche DEFAULT ON (tutte opt-out, lette LIVE per-build, banner in
`refreshSolverStatics`). Source DEFAULT = golden. Dettaglio in `CLAUDE.md` ratifiche; qui il
riferimento per orientarsi. La tabella KPI e' invariata dall'08-05 (nessun nuovo solve che muova
i KPI di guardia).

## La domanda aperta: quali prototipi accendere

Il batch laptop ha lasciato un gruppo di prototipi flag-gated DEFAULT-OFF, ciascuto con un verdetto
misurato. Nessuno e' "da accendere per dovere": sono tutti opzionali. La decisione e' di maintainer.
Quadro sintetico (evidenza dai VERDICT, non da memoria):

| prototipo (flag) | verdetto live A/B | cosa cambiarebbe | rischio | consigliato |
|------------------|-------------------|------------------|---------|-------------|
| **TASK-449** mono-room-tag pre-booking | RULED OUT: -5hard vs 0hard, entrambi i regimi | pre-booking delle celle scarse | DEGRADA hard (binario) | NON accendere (escluso con evidenza) |
| **TASK-470** theory-spread construction dedup | NEUTRAL: 0hard entrambi, medium -54k suggestivo ma non conclusivo | teoria a 1h/giorno via template builder | basso (non degrada) | mantenere OFF finche' non serve il medium; la pratica teoria e' gia' coperta dal workbook TASK-471 (dichiarata come input) |
| **M-TR** theory-adjacent (468) | WEIGHT-INERT: peso non muove il residuo (sorgente in construction) | penalizza 2h teoria same-day | inutile al peso | OFF; il lever reale e' construction (470), non il peso |
| **M-TC** flat tag-capacity (455) | prototype, non ratificato flat (2x wall); **M-TC per-retry (486) e' gia' DEFAULT ON** | capacity del tag come medium | 2x wall se flat | OFF flat (la forma per-retry e' gia' in opera) |
| **CompositionHoleCloser** (481) | building block corretto, INERTE sul bad-seed noto (shell-game depth-3) | chiude buchi H17 localmente | inerte sul caso noto | OFF (lo scaffold e' corretto ma non converge sul bacino che abbiamo) |
| **multi-start parallelo** | NEGATIVE (~1,2x, CPU-bound) | N app+DB concorrenti | piu' lento del seriale nel caso comune | NON costruire (il seriale DEFAULT ON e' superiore) |
| **GRASP** costruzione randomizzata | opt-in, usato internamente dal multi-start per tentativi >=2 | costruzione randomizzata | nessuno (gia' in opera dentro multi-start) | gia' attivo dove serve; non c'e' un default da flippare |
| **source=input** (vs golden) | APPROVATO proceduralmente (m-13 sbloccato), ma medium peggiore del golden | path senza golden | medium -1300k vs -1281k | resta GOLDEN di default; flip ready-when-needed (quando il golden sparira') |

### Lettura strategica

L'indagine metaheuristic+repair era gia' ESAURITA al 08-05. Il batch laptop 08-08..12 ha **chiuso
i conti aperti**: ha escluso 449 (degrada), misurato 470 (neutro), escluso il parallelo (CPU-bound),
confermato M-TR inerte, ratificato m-13 (path input viable). Non ci sono prototipi "promettenti
da accendere": ogni flag DEFAULT-OFF e' o escluso con evidenza (449, parallelo) o neutro/inerte
(470, M-TR, M-TC flat, CompositionHoleCloser). La direzione produttiva resta il **MODELLO**
(vincoli, fatti, UX, workbook), come da pivot 08-05.

L'unica decisione con effetto reale e' **470**: se accenderlo per il medium -54k (teoria a
1h/giorno in construction). Ma non e' conclusivo (single A/B, entro ~2x varianza) e la pratica
teoria e' gia' dichiarata come input nel workbook TASK-471. Da valutare se il medio -54k merita
la complessita' di un flag construction in piu'.

## Riferimenti

- Revisione task-by-task: questo doc, sezioni sopra.
- Batch raw: `task449-results/`, `task470-results/`, `task480-results/badseed-frequency-laptop-2026-08-09/`,
  `parallel-multistart-2026-08-11/`.
- Ratifiche autoritative: `CLAUDE.md` (sezioni RATIFICA TASK-433..486).
- m-13: `m13-procedural-closure-APPROVED-2026-08-11.md` + milestone `backlog/milestones/m-13`.
