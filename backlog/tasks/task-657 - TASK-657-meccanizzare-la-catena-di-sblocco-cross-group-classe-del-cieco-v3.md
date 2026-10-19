---
id: TASK-657
title: 'TASK-657: meccanizzare la catena di sblocco cross-group (classe del cieco v3)'
status: Done
assignee: []
created_date: '2026-09-23 13:28'
updated_date: '2026-09-23 17:28'
labels:
  - claude-planned
  - zero-hard-loop
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dal blind puzzle v3 della TASK-654 (REPORT.md, 23/09): la classe mancante e' la CATENA DI SBLOCCO CROSS-GROUP. Struttura: il tail-slide del giorno ferito (G04 Informatica ven@13->@11) e' bloccato dall'impegno @11 del suo docente in un ALTRO gruppo (G03 Gestione, T06); una swap CROSS-GIORNO dentro il gruppo terzo (G03 Gestione ven@11 <-> Laboratorio Tecnologico gio@13, T22) libera il docente senza danni (contatori invariati, contiguita' preservata, sbloccante libero entrambi i lati, stanza tag-bound libera). MOSSA PRE-REGISTRATA per il banco (dal REPORT): le 3 mosse sopra con LabTe che atterra in LAB. TERMOID ven@11 (libera tutto il venerdi'; il cieco l'aveva messa in R04 perche' il puzzle non elencava il suo tag). BANCO-FIRST: replay sul capture integrato-7 (export presente), predizione -1 -> 0 con quelle 3 mosse; poi design del braccio (dove vive: candidato naturale = nuova famiglia nel ViolationScopedSteepestFinisher come swap cross-group sui BLOCKERS dei target, o braccio dedicato in HRF; la discussione e' parte del task); gates completi prima di qualunque adozione. Nota: il 638 swap e' same-group e i target sono le lezioni delle violazioni: la coppia G03 non e' mai candidata. Anche il room-aware 641 non copre (la sua swap resta same-group).
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ESITO 23/09 sera: implementato il braccio unblock (terzo passaggio pigro nel 638: swap cella-per-cella cross-date nel gruppo del blocker + tail slide nel composito, riassegno stanza deterministico per le collisioni, flag strict-literal DEFAULT OFF). BANCO integrato-7: 576 candidati generati, TUTTI correttamente rifiutati. CAUSA (probe forced657, prova meccanica): la catena del cieco v3 e' illegale per la VACATION full-day di T22 sul ven 20/11 - terza Vacation in tre iterazioni (v1 T24, v2 membership, v3 T22). CELLA STRUTTURALE con prova: partner ven-disponibili tutti occupati @11, liberi @11 tutti ven-indisponibili. NESSUN flip: il braccio resta prototype senza positivo dimostrato. IN POSITIVO da questa tornata: (1) fix resolver H17 (justification gruppo+data -> target del giorno ferito: le ferite H17 erano INVISIBILI al 638, zero target, walk morto - classe F1 PASSO-7), valore immediato su ogni stato con residuo H17; (2) il Long-boxing fix nel filtro; (3) la modalita' forced657 come probe standard per verificare le catene dei ciechi. Da ratificare il resolver fix col prossimo stato H17 che chiude.

VALIDAZIONE SINTETICA CENTRATA 23/09 sera (commit 061266e4): con le tre viste disponibilita' coerenti (DB + VRP static + H5 facts, flag banco offrep.synth.dropT22Fri), il braccio applica la catena cieco-v3 esatta: applied UNBLOCK [Gestione<->LabTe G03 + Informatica->ven@11] delta=1hard, -1->0 in 21s. Predizione pre-registrata centrata. Il banco-forzato di controllo conferma che la sonda stanza (review F1) e' indispensabile (raw chain = -1000 solo H2). LA RATIFICA HA ORA IL POSITIVO DIMOSTRATO su input controllato. Gates completi: /simplify 4-reviewer (cure applicate), code-review high 8-finding (tutte curate), suite 1372/0 EXIT 0 sul main finale, banco regressione 3x identico + sintetico positivo. Task CHIUSO.
<!-- SECTION:NOTES:END -->
