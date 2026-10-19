# Notifica per referente: i giorni da 4h di giugno per G06 (famiglia 550, a posteriori)

Contesto: stiamo chiudendo gli ultimi residui dell'orario 2026/27. Dove il
nostro solver lascia un giorno dichiarato VUOTO, la prassi di casa e' chiedere
al golden cosa la scuola ha fatto davvero su quella cella, prima di toccare
input o vincoli (golden-first). Questa e' una di quelle celle, con l'analisi
allegata e una domanda sola.

## La cella

**G06, mercoledi' 9 giugno 2027**: il foglio calendario dichiara 4 ore
ordinarie; il nostro orario la lascia completamente vuota (l'unico residuo
della consegna di riferimento, -1). Tutti gli altri giorni del gruppo sono a
quota o in surplus di 1 ora (il 7/6 ha 5 ore su 4 dichiarate).

## Cosa pratica il golden

Il golden riempie quel mercoledi' con 4 ore di Ra lab aperto (8-11) e tiene in
riserva le ore Ra di G06 fino a giugno: le esaurisce in primavera avanzata
(16 e 23 marzo, 6 aprile, 5 ore a maggio), mentre il nostro piano le consuma
entro aprile. A fine anno il golden ha ancora ore da spendere dove il foglio
chiede giornate piene; il nostro no: e' l'unica ragione del -1 (verificato con
puzzle completo: nessuna sequenza di mosse legali chiude la cella senza
aprire tre giorni sotto quota altrove).

## La domanda (una sola)

**I 4 giorni di giugno da 4 ore di G06 (3, 4, 9, 10 giugno) sono voluti o
sono residuo di finestra?** Se sono voluti, la risposta naturale e' dichiarare
nel workbook anche la cadenza di fine anno delle ore Ra/ordinarie di G06
(conservazione delle ore per la coda, il golden la pratica); se sono subiti,
correggiamo il foglio e la cella muore alla radice.

## Non blocca

In entrambi i casi il nostro comportamento attuale e' coerente (assumiamo il
golden finche' l'input tace, famiglia TASK-550). La domanda serve a scegliere
la forma dell'input dichiarato (foglio quota vs foglio cadenza), non a sbloccare
il lavoro.

---
Allegati di riscontro (nel repo): `integrato-6/nearmiss/ARBITRAGGIO.md` (la
catena completa con il puzzle e la prova del surplus=1), `integrato-6/
day-anatomy-G06-2027-06-09.html` (il giorno a confronto con il golden).
