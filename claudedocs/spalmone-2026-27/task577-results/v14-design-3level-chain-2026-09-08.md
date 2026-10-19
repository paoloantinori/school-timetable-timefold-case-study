# v14 Design: la catena a tre livelli (donatore → blocker → follower-del-blocker)

8/09 sera. Le tre fonti concordi (twin-tail 7/09: 8/8, oracolo boxdef attempt-1: 207/207,
ciclo pin di zai: 16-220/occasione) dicono tutte la stessa cosa: il muro dei near-miss
della catena v13 e' **chainBlkSlideH1**: il blocker viene trovato, la sua estrazione
e' geometricamente corretta, ma il SUO follower non puo' scivolare perche' il docente
del follower e' occupato nello slot di destinazione della slide.

## Il meccanismo in forma precisa

La catena v13 fa: donatore G → atterra nello slot libero del bersaglio ⟺ blocker H
(docente del donatore, altro gruppo) → si ricolloca nella CODA del suo giorno (tail-swap
v13.1) o cross-date. Il tail-swap richiede che i follower del blocker scivolino di uno
slot per chiudere il buco: **questa slide muore in H1** quando il docente del follower
del blocker ha un'altra lezione in quello slot adiacente.

Il terzo livello: quando la slide del follower del blocker e' H1-bloccata, il colpevole
e' un'ALTRA lezione (firma: docente occupato nello slot adiacente). La catena a tre
livelli risolve ricorsivamente: follower-del-blocker → blocker-2 (la lezione che occupa
lo slot adiacente al docente del follower) → ricollocazione di blocker-2.

## La forma corretta NON e' una ricorsione illimitata

La v13.2 insegno' che l'intra-giorno e' geometricamente chiusa a una sola famiglia (il
tail-swap). Il terzo livello deve quindi essere CROSS-DATE come la v13 plain:
blocker-2 si ricolloca in un ALTRO giorno del suo gruppo con deficit (una cella H34
dello stesso gruppo: due celle si chiudono con una mossa composta).

## I vincoli di design

1. **Oracolo compound full-hard**: ogni mossa composta (donatore + blocker + follower +
   blocker-2) viene valutata sul punteggio COMPLETO (non timeslot-tier): il costo
   computazionale e' 4 valutazioni per mossa, ma la frequenza e' bassa (207 near-miss
   per ciclo = 207 valutazioni composte totali).
2. **Guardia anti-shell-game (TASK-481)**: la mossa composta e' valida SOLO se il numero
   totale di celle vuote diminuisce: la profondita' della catena non puo' spostare il
   problema altrove.
3. **Budget di profondita'**: MAX 3 livelli (donatore + blocker + blocker-2). Se
   blocker-2 non trova una ricollocazione legale, la catena muore li': niente ricorsione.
4. **Flag**: `planner.construction.surplus-harvest.chain-depth=3` (default 2 = la v13
   attuale). Opt-in per il banco.

## Il banco (gia' strumentato)

Il gemello ce l'ha: checkpoint POST_B_ROOM del boxdef + twin-tail. La misura: le 207
occasioni near-miss quante si convertono in celle chiuse con depth=3. Attesa onesta:
se il blocker-2 ha le stesse difficolta' del blocker (slide-H1 del SUO follower), il
guadagno e' ~zero: il banco lo dice in 15 minuti.

## Priorita' (da decidere con maintainer alla consegna del ciclo)

Se il ciclo boxdef consegna H34 ≤ 3 e la domanda DEFER scatta, la catena v14 e' la
prossima mossa search-side. Se consegna H34 > 3 (peggio del baseline), la domanda
cambia: la cura ha peggiorato e va capita PRIMA di costruire sopra.
