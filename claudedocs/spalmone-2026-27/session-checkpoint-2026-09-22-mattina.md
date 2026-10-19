# Quadro del risveglio 22/09 mattina (notte del drill, tutto pushato: HEAD 416e8516)

## COLONNA 1 - FATTO stanotte (con prove)

1. **TASK-636 RATIFICATO DEFAULT ON** (maintainer "ratifico", 015c818b): A/B 5/5
   criteri, ON [0/0]hard al tentativo 1 vs OFF -2, inerzia post-flip verificata
   (-D=false byte-identical). Serie live: **4/8 consegne zero-hard**.
2. **Timefold 2.3.0 -> 2.6.0** (suite 1364/0 EXIT 0, XSD valida, rischi
   certificati dal diff dei tag da agente ricerca). In dote: undo-score restore
   (PR 2596), fix bavet, Neighborhoods iterativo. Pillar custom-key NON
   approdato (2.7.0-rc1 a sampler).
3. **TASK-639: causa MT-LS trovata e curata** (due iterazioni):
   - AC#1: ripro controllata + thread-dump: 8 runner SPLITTATI fra prima e
     seconda await = desync dei trip; causa: awaitChannelsDrained aspettava
     "canali vuoti" ma un runner fra i due await non consuma (race latente
     dalla 543, emersa col cadence 2.6.0). PR 2596 SCAGIONATA.
   - CURA: decider parte N+1 della barrier, trip espliciti (setup + step/1 +
     step/2), attesa UNICA 60s (le fette spezzano la barrier: imparato al
     secondo giro). Unit 3/3, deadlock 4/4, byteman 2/2 VERDI.
   - **UNA FASE MT-LS COMPLETA E' PASSATA** col decider nella barrier (il
     step-chasing e' curato); la validazione del ciclo INTERO resta da fare
     (vedi colonna 3).
4. **TASK-638 banchi 638a/638b**: LS corta deterministica CHIUDE il -2 (-37 ->
   -35, restano solo i fantasmi H4 della base CH-only): predizione di maintainer
   confermata dai numeri. h7swap no-op su stanze greedy (limite noto 632):
   banco trio-refined pronto nel repo (offrep.mode=trio-refined).
5. **Sito aggiornato** (6 vincoli nuovi H31-H34/M38/M-BF + testi cinque-fasi) e
   verificato dal laptop con screenshot. Hook pre-commit rotto scoperto e curato
   (gate spostato su commit-msg). Radar decomposizione corretto (quote fantasma
   FCQuota). Cell-feed banchi verificato end-to-end. Notifica referente pronta
   (referente-notifica-g06-giugno-4h: LA SPEDISCE maintainer).

## COLONNA 2 - IN STATO DI ATTESA (bloccato da cosa)

- **A/B 637 SOSPESO** dal secondo blocco 2.6.0 (TASK-640): footprint cresciuto,
  OOM a 2g E 4g nel ciclo completo dove 2.3.0 girava a 2g da mesi. laptop ferma
  correttamente (0,2GB liberi: terzo tentativo = rischio freeze). Nessun dato
  valido perso (le consegne lette erano cicli morti).
- **Validazione lunga cura 639**: tre tentativi uccisi dal guard memoria della
  Fedora condivisa (JVM 2,7G di un altro progetto attivo + desktop). Non terzo
  strike: tre strike fatti, mi sono fermato.

## COLONNA 3 - DECISIONI PER maintainer (solo tue)

1. **Finestra dedicata con macchina scarica** (Fedora o laptop): serve PER ENTRAMBI
   i blocker: (a) validazione lunga 639 (il ciclo intero col decider in
   barrier, ~40-90 min a 3g), (b) diagnosi footprint 640 (jcmd GC.histogram al
   picco 2.3.0 vs 2.6.0 stesso checkpoint: se e' PR 2596/RecordedUndoMove
   trattenuta, issue upstream coi numeri). Una finestra risolve entrambi.
2. **Ratifica 637** dopo che l'A/B potra' correre (i criteri sono
   pre-registrati nel GATE-CRITERI.md del laptop).
3. **Gates rimasti sulla cura 639** prima dell'adozione: /simplify + code-review
   high (protocollo critico, dichiarato nel commit) + validazione lunga.
4. **Invio notifica referente** (G06 giugno 4h): tua.

## SPIKE della notte (3 righe ciascuno, come da mandato)

- **LS finale sul consegnato** (idea maintainer): ipotesi "chiude in tempo zero" ->
  CONFERMATO (-37->-35 in 303s, deterministico seed 0); consiglio: tenere, e'
  l'input che valida il design 638.
- **h7swap su stanze greedy**: ipotesi "limite ROOM-CAND noto" -> confermato
  (no-op); consiglio: iterare su base refined (banco pronto) prima di
  giudicare il braccio.
- **Neighborhoods 2.6 per il 638 forma B**: promettente on-paper; consiglio:
  valutare nel design dopo che 639/640 riaprono la pista engine.

## Regole della notte rispettate
Nessun flip, nessun invio esterno, nessun secondo ciclo sul laptop, decisioni
tecniche prese (fallback MT-LS off, tre-strike memoria) e documentate nei task.

## AGGIORNAMENTO MATTINA (08:30, post-verdict laptop + byteman)

1. VALIDAZIONE laptop 8g: FALLITA a 562s con la STESSA firma pre-cura (offerLoud
   5s, canale pieno). La cura barrier NON basta: esiste un SECONDO punto di
   stallo, quasi certamente sul percorso di VALUTAZIONE (il primo stack laptop
   aveva un runner dentro executeTemporaryMove: sospetti PR 2596 undo /
   shadow-variable 2.5). In volo: ripro Fedora con cura v2 per catturare gli
   stack allo stallo residuo (la stampa 543 esce nel log del test).
2. REGRESSION BYTEMAN 2/2 VERDI (commit 2e872a55): la finestra del protocollo
   step e' PROVATAMENTE chiusa (ritardo 8s -> dispatch gated; morte mid-trip ->
   loud bounded). Quindi: il protocollo e' sano, il residuo e' altrove.
3. FOOTPRINT 640 CONFERMATO in-JVM: il 3g OOM anche sul laptop. 8g regge
   almeno 10 min (fino allo stallo, non alla fine).
4. DECISIONE AGGIUNTA PER maintainer (alla colonna 3): se il secondo stall si
   conferma nel percorso executeTemporaryMove/undo di 2.6.0, le opzioni sono
   (a) diagnosarne la causa con gli stack della ripro in volo e curare,
   (b) pattinare MT-LS off di default su 2.6.0 (single-thread: -25% wall,
   misurato) finche' upstream non risponde, (c) valutare il rollback
   temporaneo del pom a 2.3.0 (tutto il resto della 2.6.0 che abbiamo
   adottato oggi non dipende dal engine LS). La (b) e' la piu' prudente
   se la giornata deve riprendere a produrre A/B.
