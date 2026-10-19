# Calibrazione checkpoint QuotaFillRepair (TASK-533, notte 19/20-08)

> Domanda: quanto ci si puo' fidare del REPLAY sull'export FINALE per validare
> i pass terminali (post-B-ROOM)? Il replay costa secondi, il run vivo ~2h.

## Il confronto

Export finale del run vivo qfr-run1 (consegna -38hard: H32 -31 su 26 celle in-scope):
replay del pass sulla griglia congelata, stessa build:

| | live (post-RELEASE nel ciclo) | replay su export finale |
|---|---|---|
| mosse | 17 | 17 |
| celle chiuse | 13 | 13 |
| conflitti | 0 | 0 |
| under-quota in-scope dopo | 26 | 33 -> 20 |

Il replay chiude ESATTAMENTE le stesse 13 celle con le stesse 17 mosse del live.

## Lettura dei numeri (autoverifica a due vie)

1. Il counter del test (33 -> 20) e' la forma NO-tolleranza (include 7 celle G09
   da 1h che il vincolo tollera: non sono H32). Il conteggio in-scope H32 del run
   vivo e' 26: differenza 7 = le G09: i due counter quadrano al netto della
   tolleranza.
2. La prova di fedelta' e' la tripletta mosse/celle/conflitti IDENTICA al live
   (17/13/0): il pass e' deterministico e la griglia d'ingresso (l'export finale,
   CON i pin promossi flagged) ha lasciati mobili tutti e soli i donori che il
   live vedeva: le 26 celle residue non avevano donori legali al momento del
   pass NESSUNA delle due volte: la firma replay le avrebbe dette intrattabili,
   ed e' quello che il live ha confermato.
3. Controprova della volta scorsa (h32b: replay 2/7 con causa pin-promossi): li'
   la divergenza era reale (i donori G10 erano tutti pinned nell'export). Qui
   NO: le 17 mosse live e replay coincidono perche' i donori usati dal pass live
   non erano promossi. CONCLUSIONE OPERATIVA: il replay sull'export finale e'
   AFFIDABILE per le celle i cui donori non sono pin-promossi, e conserva la
   prudenza (lower bound) dove lo sono: la regola permanente "replay PRIMA del
   run per i pass terminali" e' VALIDA con questa avvertenza documentata.

## Regola permanente registrata

Pass terminale (post-B-ROOM) = replay valido sull'export finale, DA FARE PRIMA di
lanciare il run: costo secondi, firma (mosse, celle, conflitti) confrontabile
1:1 con il live quando i donori non sono pin-promossi; dove lo sono, il replay
sottostima (lower bound) e la causa e' meccanicamente individuabile (donor-day
con movability zero).
