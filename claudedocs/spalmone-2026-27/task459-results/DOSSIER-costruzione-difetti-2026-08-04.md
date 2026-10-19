# DOSSIER TASK-459. Difetti nati in costruzione: tre filoni, causa distinta, tutti risolti sullo stack attuale. 2026-08-04

> META-TASK di raccolta (Fedora 2026-07-29). Tre indagini indipendenti puntavano alla stessa fase (il
> piazzamento iniziale). Questo dossier le consolida con l'evidenza per-fase ora disponibile (dump
> TASK-453, run source=input 0-hard del 2026-08-03, stack ratificato attuale) e chiude AC#1 + AC#2.
> NESSUNA adozione, NESSUN codice (read-only consolidation).

## AC#2 (la domanda chiave): causa comune o distinte?

**Cause DISTINTE, costruzione come NEXUS comune.** I tre difetti nascono tutti nella fase di costruzione
ma per meccanismi diversi, e ognuno e' ora risolto sullo stack ratificato attuale da una ratifica
specifica (o dalla LS):

| Difetto | Meccanismo in costruzione | Risoluzione (stack attuale) | Verifica |
|---------|---------------------------|------------------------------|----------|
| **G11 friday late-start** (TASK-450) | La costruzione lascia un presupposto (buco slot-1); la P2 LS chiude l'H17 slidando a 9 | **TASK-451** first-hour-slot coordinato (ratificato 2026-07-31) riserva slot0+1 con l'ancora golden | task453: 34/34 venerdi' G11 start@8, **0 late** (qui sotto) |
| **G06 / T01 deficit** (35 boxed nel cert 29b) | CH assegna greedy i giovedi' di T01 ai suoi 4 corsi, G06 resta corta | **TASK-435** constrained-teacher (DEFAULT ON 2026-07-30) pre-assegna i giovedi' | slack -30h -> +5h, 60 lezioni G06 pinnate (comment task) |
| **tier drift generici** (TASK-451/457) | La costruzione front-loada i generici (baricentro -7,1 a fine costruzione) | **la LS lo rimuove** al finale (+0,2; giu +0,5 bilanciato) | VERDICT-457 (run task453) |

Non c'e' una singola causa comune: e' greedy-assignment (435), presupposto-di-buco (451), front-load-
pacing (LS). Ma il piazzamento iniziale e' il NEXUS: tutti e tre toccano la costruzione, e la direzione
"fix a monte" (459 direzione c, 450 SPIKE) era corretta per i primi due; il terzo non e' un difetto
persistente (la LS lo riassorbe).

## AC#1: il momento esatto in cui G11 scivola dalle 8 alle 9

**Sullo stack attuale NON scivola: e' curato.** Verifica sul run task453 (post-TASK-451):

```
CONSTRUCTION phase-2: G11 venerdi'=34  start@8=34  late(start@9,no 8)=0  other=0
FINAL phase-5:        G11 venerdi'=34  start@8=34  late(start@9,no 8)=0  other=0
```

Tutti i 34 venerdi' di G11 iniziano alle 8 (slot 0), zero late, sia a costruzione (phase-2) che al
finale (phase-5). Il difetto che era "1 cella anomala" nel gate 2026-07-29 (G11 venerdi', pre-TASK-451)
**non e' piu' presente** sul run post-TASK-451.

**Sullo stack vecchio (29b, pre-ratifiche) il verso era: nasce in P2.** Il commento #1 del task (30/07,
dump 29b) fermo': tutti i 18 mercoledi late gia' a >=9 a fine P2; P3 inerte; P4 ri-ancora incidentalmente
4 a 8 (18->14); P5 stabile. Lo slide nasceva in P2 (chiusura del buco HARD H17 lasciato dalla costruzione
allo slot 1), NON era drift soft. L'anello aperto allora era "P1-vs-P2" (la costruzione crea il
presupposto e P2 agisce, oppure P1 e' gia' cosi'?). **TASK-451 risolve a monte:** riservando il blocco
slot0+1 con l'ancora slot-0 modale golden, il presupposto non si forma piu', quindi P2 non ha buco H17 da
chiudere slidando. AC#1 chiusa: il difetto e' PREVENUTO alla radice da TASK-451, non piu' osservabile.

## Sintesi: il dossier CHIUDE

I tre filoni di "difetto nato in costruzione" sono tutti RISOLTI sullo stack ratificato attuale:
- **G11 late-start**: curato da TASK-451 (verifica task453: 34/34 venerdi' start@8).
- **G06 T01**: curato da TASK-435 (constrained-teacher, slack +5h).
- **tier drift**: non persistente (la LS lo rimuove al finale, VERDICT-457).

**AC#2: cause distinte, costruzione come nexus. Non c'e' una singola leva "sistema la costruzione" da
costruire: ogni difetto aveva la sua ratifica (451, 435) o era gia' riassorbito (LS tier). Il dossier
chiude senza codice.** La direzione "fix a monte nella costruzione" era corretta ed e' stata attuata nei
due casi in cui serviva (451 first-hour-slot, 435 constrained-teacher); il terzo (tier) non richiede
intervento.

## Filone residuo (non bloccante, tracciato altrove)

L'anello orizzontale (saturazione venerdi'-8 prodotta dal piazzamento, candidati conflict-free 0/417 nel
run vecchio) e' il terzo filone citato nella descrizione. Sullo stack attuale, con G11 venerdi'
anch'ato (start@8) e TASK-460 (brainstorming ricombinazione orizzontale, blocker TASK-453 rimosso)
sbloccato, l'analisi per-fase di quel filone e' riproducibile coi dump TASK-453 se servira', ma non e'
un difetto aperto: e' il dominio di TASK-460 (intervento gated maintainer).

## Limiti (onesti)

- La verifica G11 e' sul run task453 (n=1, source=input 0-hard, stack attuale). LS NON_REPRODUCIBLE,
  ma la cura (TASK-451 first-hour-slot riservato) e' DETERMINISTICA (pre-assegnazione del blocco slot0+1),
  non LS: quindi l'effetto "venerdi' start@8" e' strutturale (il blocco e' riservato pre-solve), non
  traiettoria-fortunata. Riproducibile per costruzione.
- Il verso "P1-vs-P2" del 29b (stack vecchio) resta aperto STORICAMENTE (manca il dump P1 del 29b), ma e'
  MOOT sullo stack attuale: TASK-451 previene il presupposto, quindi la domanda "P1 crea il presupposto
  o P2 agisce?" non si pone piu'.
- Tier drift: n=1 (VERDICT-457), la LS lo rimuove ma andrebbe confermato n>=2.

## Riferimenti

- VERDICT-457 (tier drift construction-vs-final): `task457-results/VERDICT-457-construction-vs-final-2026-08-04.md`.
- G11 late-start per-fase (stack vecchio 29b): commento #1 di TASK-459 + TASK-450.
- G06 T01 deficit: commento task (implementation notes) + `task459-slack-triangolo-g06-t01-2026-07-30.md`.
- Ratifiche: TASK-451 (first-hour-slot), TASK-435 (constrained-teacher) in `CLAUDE.md`.
- Dump per-fase: `task453-results/dumps/phase-{2,5}-*-id.txt`.
