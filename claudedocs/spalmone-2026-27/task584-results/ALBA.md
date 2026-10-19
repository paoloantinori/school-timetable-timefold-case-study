# ALBA 6/09: batch TASK-584 completato (8/8), la notizia e' grossa

La notte ha risposto alla domanda della re-validazione: **p̂(0-hard) = 0/8 sul
vincolame corrente** (H31+H32+H33+34 ON, SH OFF, tentativo singolo GRASP):
il claim m-13 "multi-start consegna 0-hard garantito" (32/32 il 9-10/08) NON
sopravvive ai quattro vincoli hard introdotti dopo. Range delle consegne -65/-167.
Wilson95 [0,000; 0,324]: anche al limite superiore P(>=1|N=5)=0,859, lontano
dalla garanzia. Decisioni a Fedora/maintainer (budget, vincolame, cure search-side:
le celle non-curabili sono deficit input TASK-586, fermo per design finche' il
batch girava: ora sbloccabile).

Il metro TASK-526 ha avuto la sua prima validazione live continua: 8/8 consegne
con "delivery concordance OK (H29 room-null memory=N db=N)", zero divergenze,
zero check-failed dopo il fix e95a4100 delle 19:29.

Serie operativa della nottata nel VERDICT (dopo un pomeriggio movimentato: burn
dei flag alle 17:15, stop prematuro e ripartenza alle 19:29): 102 consegnato
21:00, poi uno ogni ~75-90 minuti fino al 109 alle 08:10. Zero incidenti infra,
avr=0 su tutto, auto-guarigione mai innescata, 0 invalidi.

Consegne collegate di ieri: TASK-526 seconda meta' (cdc6844c), il suo fix serale
(e95a4100, ContextNotActive), la v12 di SurplusHarvest (60f25abd, NON ancora
misurata: il ciclo v12 e' la coda n.1 di oggi, profilo v12-5sep gia' seminato e
VERDICT pre-registrato in attesa). Seed 101 del batch: da recuperare a scelta.
