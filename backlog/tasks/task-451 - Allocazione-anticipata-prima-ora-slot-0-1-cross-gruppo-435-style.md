---
id: TASK-451
title: >-
  Ricombinazione orizzontale a solve time: uscire dagli ottimi locali dove uno
  slot e' conteso e ogni docente utile e' gia' allocato altrove
status: Done
assignee: []
created_date: '2026-07-29 09:49'
updated_date: '2026-07-30 08:11'
labels:
  - claude-planned
milestone: m-12
dependencies: []
priority: high
ordinal: 274000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RIFORMULAZIONE (maintainer, 29/07, sostituisce l'inquadramento originale 'allocazione anticipata della prima ora 435-style', che maintainer ha respinto: congela a priori una decisione che il solver dovrebbe saper prendere, e cura un sintomo invece del meccanismo).

PROBLEMA, in forma generale: esistono stati in cui uno slot di una giornata resta vuoto non perche' manchi lavoro da fare, ma perche' TUTTI i docenti utili a quel gruppo sono gia' impegnati con altri gruppi in quello stesso slot. Uscirne richiede una mossa COORDINATA su piu' gruppi (io prendo il docente, l'altro gruppo si fa servire da un suo docente alternativo), che nessuna mossa singola di cambio o scambio puo' rappresentare: ogni stato intermedio e' peggiore o illegale, quindi la ricerca locale si ferma in un ottimo locale. Caso verificato: G11 del venerdi, dove a slot 1 tutti e 5 i docenti della giornata insegnano altrove (T08 a G02, T12 a G07, T23 a G01, T06 a G09, T25 a G04) e il buco sopravvive a costruzione, compattazione e repair. Ma il caso e' solo l'istanza che abbiamo misurato: il meccanismo NON riguarda solo la prima ora, vale per QUALSIASI ora (maintainer).

DIREZIONE: dare al solver la capacita' di ricombinare ORIZZONTALMENTE, cioe' una mossa composta che sposta la lezione di un altro gruppo per liberare il docente e riempire il buco, a catena, con profondita' limitata (2-3 anelli). Vincolo non negoziabile: le lezioni PINNATE (import autoritativi, stage, docente vincolato) sono immobili e la catena non le puo' rompere mai; le QUASI-pinnate (celle di template difese da M32) sono mobili ma hanno un prezzo, e la catena deve tenerne conto invece di ignorarlo.

DOVE FARLA, alternative da valutare con misura, non a priori: (a) mossa custom nelle fasi di ricerca locale (MoveIteratorFactory/MoveSelector Timefold) - la piu' generale, agisce dove la contesa si manifesta; (b) passo deterministico di riparazione, fratello di HoleRepair/AnchorRepair, che cerca le catene solo sui buchi rimasti - piu' controllabile e testabile a checkpoint, ma vede solo lo stato finale; (c) in costruzione (CH), sfruttando che i pin sono gia' noti in quel momento (maintainer): parte della contesa e' prevedibile li', ma resta da capire QUANTA - la contesa che abbiamo misurato nasce dal riempimento round-robin, non dai pin, quindi la prevedibilita' a CH e' essa stessa da misurare prima di scommetterci.

PRIMO PASSO (prima di scrivere codice): misurare quante situazioni di questo tipo esistono e quante sarebbero risolvibili da una catena di profondita' 2, cioe' quanti buchi hanno almeno un docente del gruppo occupato da un altro gruppo che a sua volta ha un docente alternativo libero in quello slot. Se il numero e' alto, la mossa vale; se e' basso, sono buchi genuinamente infattibili e si chiude il tema. Misurabile offline sul dump di costruzione e sull'export, senza toccare il solver.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 #1 [DONE, SUPERSEDATO] Misura catena-2 criterio docente-alternativo: 47% - criterio SBAGLIATO (ignorava departure-safe).
- [ ] #2 #1b [DONE] VALIDAZIONE catena corretta (due cambi timeslot + departure-safe, CORREZIONE 1): finale 0/15 (0%, tutti G11 ven slot-0), costruzione 8/73 (11%). Kill: departure-unsafe (la lezione di G2 nello slot-buco e' l'inizio della giornata dell'altro gruppo, non la coda -> spostarla apre buco). Stessa trappola FASE D.
- [ ] #3 #2 [GATE] depth-2 crolla a ZERO sui residui finali -> per il gate di maintainer 'crolla a zero -> chiudi senza codice' la catena depth-2 SI CHIUDE senza prototipo.
- [x] #4 #3 [DONE, GATE CHIUSO] depth-3 misurata = **0/15** (SWAP stretto) e 0/15 (EXTEND lasco, H11 non validato). Script `scripts/analyze/chain-feasibility-kpi.py` ricostruisce depth-2 (calibrato 0/15 finale, 8/73 costruzione) ed estende a depth-3. Su 15 buchi HARD finali (tutti G11 ven slot-0) NESSUNO e' chiudibile a depth-3: 5 gap falliscono perche' il gruppo-donatore G2 ha la coda = lab PINNATA (G09 slot4 Confezione/T16, immobile); 10 gap perche' il docente di coda di G2 e' unpinned ma OCCUPATO a slot0 con un terzo gruppo (la trappola si riproduce, servirebbe depth-5). Brute-force su tutte le lezioni G1 come sorgente = 0 catene (skeptic CONFIRMED: il vincolo tail-source e' strutturale). I vincoli non-validati (room/H11) danno solo falsi-positivi, mai falsi-negativi => upper bound 0 => vero 0. SANITY: a costruzione depth-3 = 12/73 (SWAP) / 14/73 (EXTEND contiguo), non-zero => la logica non e' rotta, lo 0 e' proprieta' del dump finale. PER IL GATE "crolla a zero -> chiudi senza codice": **LA TEMA SI CHIUDE**, nessun prototipo. Dettaglio: SPIKES-RAW.md sezione "TASK-451 depth-3".
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
ANALISI DERIVABILITA' 2026-07-30 (zai, finestra autonoma, lente direttiva SOLO-INPUT): cosa dell'allocazione coordinata della prima ora e' derivabile dagli INPUT vs scelta della scuola (colonna c del censimento).

INPUT-DERIVABILE (la parte che si puo' costruire senza golden): la FATTIBILITA'. Per ogni gruppo, l'insieme dei docenti disponibili a slot0 quel weekday = intersezione di (docenti che insegnano al gruppo, da incarichi) e (disponibilita' slot0, da availability_rules/H5). SPIKE 1 (spike1-feasibility-prima-ora) ha gia' verificato il matching bipartito 14 gruppi x docente-slot0 = 14/14 per tutti i 5 weekday, anche come blocco slot0+slot1 e con soglia >=20h: zero scoperti. Quindi la capienza c'e', ed e' puro input. La coordinazione deterministica cross-gruppo (un docente a un solo gruppo per slot0) si puo' costruire su questo insieme senza leggere il golden.

SCELTA DELLA SCUOLA (colonna c, NON derivabile): QUALE materia/docente ancorare alla prima ora per gruppo. Il golden mostra ancore specifiche (es. G11 ven = Matematica/T08 slot0-1) ma nessun input dice 'G11 inizia con Matematica'. E' una pratica. => colonna (c): 'per gruppo, c'e' una materia/docente da ancorare alla prima ora come preferenza?'.

IMPLICAZIONE PER L'IMPLEMENTAZIONE: costruire l'allocatore sull'insieme FATIBILE (input-derived) lasciando al solver la scelta dell'ancora specifica, OPPURE dichiarare l'ancora (colonna c). NON derivare l'ancora dal golden: e' lo stesso errore del docente-vincolato (#1 del censimento). Differenza chiave vs TASK-435: la coordinazione della prima ora e' REALIZZABILE input-pura (fattibilita' + scelta solver), mentre il docente-vincolato pinnava assegnazioni specifiche del golden. Quindi TASK-451 nasce senza debito golden se si segue questo shape. Verifica da fare (finestra golden): l'ancora del golden (Matematica/T08 per G11 ven) e' una tra le tante fattibili, o il solver senza anchor riproduce la stessa? Se la riproduce, l'anchor e' inutile; se no, l'anchor e' una preferenza (colonna c).

VERIFICA FIRST-HOUR (a) FATTAsul dump 30d (default, no anchor pin) -Domanda: il solver senza anchor riproduce l'ancora del golden? RISPOSTA: NO. (1) Dove sia golden che 30d hanno una lezione a slot0, solo il 23% ha la STESSA MATERIA (77% diversa: Personalizzazione->Progettazione Termoidraulica, Matematica->Scienze, ecc.): il solver sceglie liberamente, non riproduce l'ancora. (2) 30d e' senza slot0 in 44 giorni in cui il golden ce l'ha (i late-start). (3) G11 ven (la cella difettosa headline): golden ancora Matematica ogni venerdi', 30d NON la mette MAI (15 ven con slot0 vuoto, gli altri Informatica/Disegno Edile/Educazione Civica).

CONCLUSIONE (fermo prima del prototipo, come chiesto): l'ancora (QUALE materia inizia la giornata) NON e' derivabile dagli input - e' una scelta che il solver fa liberamente e che il golden fissa come pratica. Quindi e' COLONNA (c) del censimento: 'per gruppo, quale materia ancorare alla prima ora come preferenza?'. Il coordinatore NON va progettato per riprodurre l'ancora del golden. Il suo obiettivo input-derivable e' la PRESENZA di slot0 (riempire i 44 late-start coordinando la disponibilita' docente cross-gruppo, vedi fattibilita' SPIKE 1 14/14), NON la MATERIA. La scelta della materia resta al solver. Script: /tmp (confronto slot0 golden vs 30d). DA FARE (non ora): se la scuola dichiara un'ancora preferita (colonna c), il coordinatore puo' tenderci; se no, persegue solo slot0-pieno.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Comments

<!-- COMMENTS:BEGIN -->
created: 2026-07-29 11:05
---
MISURA PRIMO PASSO (zai, 29/07, read-only, input-derived niente golden): catena-2 fattibile per il 47% dei buchi HARD finali. Metodo: dump costruzione/export + incarichi.csv (subject->docenti). GAP=slot vuoto nello span giornata; HARD=tutti i docenti del gruppo occupati in quello slot; depth-2 resolvabile=un docente Ti del gruppo occupato da altro gruppo Gi che ha docente alternativo (stessa materia) libero in quello slot. RISULTATI: finale 15 HARD, 7 (47%) resolvabili (G11 16 totali / 8 resolvabili = caso headline); costruzione 73 HARD / 46 (63%). CHECK PIN: tutti i resolvabili hanno lezione Gi NON-pinnata (mobile) -> PINNED-immobili non blocca. VERDETTO: segnale moderato-alto, la mossa VALE il prototipo. CAVEAT: limite superiore (non valida H11/M15/stanza/quota); OPEN la rappresentazione della mossa (modello Course fissa docente-per-corso -> 'docente alternativo' = mossa custom di cambio Course, via (a) MoveIteratorFactory). Dettaglio: SPIKES-RAW.md sezione TASK-451. Le AC qui sotto sono STALE (vecchia proposta first-hour respinta): vanno riscritte sul prototipo catena-2 se maintainer approva.
---

created: 2026-07-29 11:27
---
VALIDAZIONE (zai, 29/07, read-only): con la catena CORRETTA (CORREZIONE 1: due cambi di timeslot, non cambio Course) + departure-safe, il 47% crolla a 0% sui buchi HARD finali (0/15, tutti G11 ven slot-0). Kill = departure-unsafe: il docente T di G11 occupato a slot0 insegna a un altro gruppo G2 il cui slot0 e' l'INIZIO della sua giornata (non coda), quindi spostarlo apre un buco in G2. Confermato concreto su 09-18 (T23->G08 0-4, T21->G06 0-3, T06->G09 0-4, T12->G07 0-5). Stessa trappola FASE D. A costruzione 8/73 (11%) sopravvivono ma il solver li processa. PER IL GATE: depth-2 chiude senza codice. Dettaglio: SPIKES-RAW.md sezione TASK-451 VALIDAZIONE.
---
created: 2026-07-29 14:15
---
DEPTH-3 MISURATA (zai, 29/07, read-only, gate maintainer AC#4): catena-3 = **0/15** sui buchi HARD finali. Script `scripts/analyze/chain-feasibility-kpi.py` ricostruisce depth-2 come calibrazione (riproduce ESATTAMENTE 0/15 finale e 8/73 costruzione, primitive departure-safe verificata) poi estende a depth-3. Definizione depth-3 (SWAP gap-neutro): G1(T1) coda->slot0 [T1 liberi a slot0 via swap in G2], G2(T1) slot0->coda-G2, G2(T2) coda-G2->slot0 [T2 libero a slot0, coda-G2 rimovibile gap-neutro]. Su 15 buchi (tutti G11 ven slot-0): 5 falliscono per coda-G2 = lab PINNATA (G09 slot4 Confezione/T16); 10 per docente-coda-G2 unpinned ma occupato a slot0 con terzo gruppo (trappola riprodotta, depth-5). Brute-force su tutte le lezioni G1 come sorgente = 0 (skeptic CONFIRMED: tail-source e' strutturale). Vincoli non-validati (room/H11) = solo falsi-positivi => upper bound 0 => vero 0. SANITY: costruzione depth-3 = 12/73 SWAP / 14/73 EXTEND-contiguo (non-zero) => logica non rotta, lo 0 e' proprieta' del dump finale. VERDETTO: per il gate "crolla a zero -> chiudi senza codice", LA TEMA SI CHIUDE. Nessun prototipo. Dettaglio: SPIKES-RAW.md sezione "TASK-451 depth-3".
---
<!-- COMMENTS:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
CHIUSO PER MISURA (29/07), nessun codice di produzione scritto: era il gate che il task stesso imponeva. La ricombinazione orizzontale non recupera i 15 inizi in ritardo residui a NESSUNA profondita' praticabile: 0/15 a catena-2 e 0/15 a catena-3, con verifica avversariale (brute force su tutte le lezioni sorgente) e controllo di sanita' (a fine costruzione la stessa logica trova 12/73 e 14/73, quindi lo zero non e' un difetto dello strumento). MECCANISMO DEL FALLIMENTO: in 5 casi la coda del gruppo donatore e' un laboratorio pinnato e immobile, negli altri 10 il docente di coda e' a sua volta occupato in quel medesimo slot con un terzo gruppo, quindi la trappola si riproduce e servirebbe profondita' 5. Lascito: scripts/analyze/chain-feasibility-kpi.py, riusabile per qualunque futura ipotesi di mossa composta. CONSEGUENZA per la decisione: quei 15 inizi in ritardo non sono recuperabili dalla ricerca, quindi restano due sole strade, accettarli come residuo dichiarato oppure decidere a monte l'allocazione della prima ora dei docenti contesi (che e' cio' che il golden fa).
<!-- SECTION:FINAL_SUMMARY:END -->
