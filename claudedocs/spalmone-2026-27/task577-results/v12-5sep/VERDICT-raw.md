# VERDICT-raw: ciclo v12 (profilo v12-5sep, 5/09 pomeriggio)

## CHIUSURA (6/09 11:45): ciclo interrotto per decisione del proprietario

Ciclo interrotto alle 11:45 per decisione del proprietario (configurazione superata
da 53f73b3c); dati meccanici preservati: primo sparo 09:42:02 moves=198 cells=38
reverted=7, REJECTS border=2874 slideH1=9712 teacherBusy=1229 noDonors=799
noSlots=1032 (log in /tmp/planner-prod.log, righe nel registro doom). Nessuna
capture completa del run morto; il confronto formale spetta al ciclo v12.3 completo
(profilo v123-6sep). Le cinque domande pre-registrate qui sotto restano come
erano al momento dell'interruzione: la (a) aveva gia' la risposta meccanica
(moves=198 > 0 alla prima occasione) e la (c) pure (slideH1=9712).

## ANOMALIA INFRASTRUTTURA (registrata, non e' un segnale di bacino)

Il primo lancio (13:40) e' MORTO alle ~15:09 per il riavvio dello stack podman del
laptop (planner-pg scomparso, JDBCConnectionException x10, il wrapper ha bruciato i
tentativi rimasti a vuoto: "attempt 5 done: score=null" alle 15:10:09, app viva ma
orfana di DB). RECUPERO (15:14-15:15): container morto rimosso (Exited 0), volume
planner-data-v12-5sep SOPRAVVISSUTO (zero re-seed al riavvio: boot 2.1s), app
rilanciata con la stessa sequenza del gate. RILANCIO alle 15:15:54, consegna attesa
~23:15. IL RUN INTERROTTO NON VA CONTA NELLE STATISTICHE (nessuna riga
doom-registry dal run morto: il suo log e' stato troncato dal rilancio; l'evidenza
dell'anomalia e' il mandato MANDATE-151347 + i riscontri in questa sezione). Se la
finestra slitta oltre le 23: il batch 584 parte comunque, N ridotto accettato.

## Setup

Ciclo lanciato 13:40 dal gate automatico, RILANCIATO 15:15:54 dopo l'anomalia infra
(commit v12 60f25abd, slide-companion H1 + donor-exclusion retry; jar 13:40:23,
PREFLIGHT-OK classes=[SurplusHarvest] flags-verified, banner surplus-harvest=true
verificato nel log). Config IDENTICA a notte-4sep: default tutto, solo
surplus-harvest armato, costruzione deterministica (nessun seed GRASP), profilo
fresco. Le risposte sotto sono compilate ALLA CONSEGNA: le domande erano
pre-registrate PRIMA di leggere qualunque risultato (questa pagina esisteva gia'
alle 13:45 col solo scheletro).

## DOMANDE PRE-REGISTRATE (con attese scritte prima dei risultati)

a. **moves>0 in almeno un'occasione?** (`grep "SurplusHarvest (TASK"` sul log).
   Baseline notte-4sep: moves=0 su 10/10 occasioni. ATTESA: SI' se i due difetti
   curati (slide-H1 in ricerca, esclusione donatore fallito) erano davvero il blocco.

b. **oracleRevert collassa?** ATTESA: da 208-736 a ~0-50 (la guardia slideH1 sposta
   in ricerca cio' che l'oracolo vedeva solo dopo l'apply).

c. **Quanto vale il nuovo contatore slideH1?** Misura diretta di quanto il collo
   fosse la slide-companion H1. ATTESA: dello stesso ordine dei vecchi reverted se
   la diagnosi era giusta.

d. **Consegna**: hard vs -8 baseline (in banda o meglio), ZERO assertValueRange e
   ZERO IllegalStateException, sanity gate, riga "delivery concordance OK" (prima
   validazione live del metro TASK-526 su consegna v12).

e. **CELLE chiuse nel consegnato**: attese CHIUSE le pass-curable G06 09+10/06 e
   G12 07/05 (conservazione 0: il golden le riempie a quota esatta); NON attese
   G09 03/06 e G11 04/06 (deficit input, TASK-586: non curabili da pass).

## RISPOSTE (compilate alla consegna)

<!-- compilate alla consegna, sotto questa riga -->

## Score di consegna

<!-- alla consegna -->

## Righe SurplusHarvest

<!-- alla consegna: grep "SurplusHarvest (TASK" /tmp/planner-prod.log -->

## Sanity e concordanza

<!-- alla consegna -->
