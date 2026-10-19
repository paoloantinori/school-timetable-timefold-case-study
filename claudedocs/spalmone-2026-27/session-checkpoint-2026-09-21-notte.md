# Checkpoint sessione 21/09 notte (drill notturno, maintainer a dormire)

## Stato al momento del checkpoint (~00:45)
- Ratificato TASK-636 cadence-pace DEFAULT ON (015c818b), serie 4/8 zero-hard.
- Timefold 2.3.0 -> 2.6.0 (suite 1364/0 EXIT 0, XSD ok, rischi certificati).
- BLOCKER TASK-639: MT-LS stall su 2.6.0. AC#1 CHIUSA (ripro + diagnosi):
  8 runner splittati fra prima (run:109) e seconda (run:110) await della
  barrier doppia = desync dei trip del protocollo step-chasing; PR 2596
  scagionata; direzione cura = protocollo desync-immune.
- A/B 637 in volo sul laptop col fallback MT-LS off in entrambi i bracci
  (watchdog 33 min; al VERDICT: verificare 5 criteri dai raw, registrare,
  NESSUN flip: ratifica di maintainer).
- Banco 638a da rifare su base refined (stanze greedy ammazzano le ROOM-CAND
  di h7swap). 638b gia' fatto: LS corta deterministica chiude il -2.
- Sito aggiornato e verificato dal laptop (screenshot committuto).

## Cosa NON fare stanotte
- NESSUN flip di default, NESSUN adozione/ratifica (solo maintainer).
- NESSUN invio esterno (referente incluso: la notifica e' pronta, la spedisce lui).
- NESSUN secondo ciclo sul laptop: l'A/B ha la macchina.

## Coda della notte (ordine)
1. TASK-639 cura: protocollo step-chasing desync-immune (trip-contatore con
   realign o epoch di broadcast), banco ripro (6 min allo stallo: col cure
   deve completare), suite byteman.
2. Verdetto 637: verifica + registrazione serie/task.
3. 638a refined: banco bench4-form (room-only refinement + trio h7swap).
4. Se resta finestra: 638 forma B (localSearch + MoveListFactory scoped,
   bridge static, REPRODUCIBLE).

## Risvegli con consegna
- Mattina: quadro a tre colonne (fatto / in volo / decisioni per maintainer) con
  i numeri dei raw, per ogni spike 3 righe (ipotesi, misurato, consiglio).
