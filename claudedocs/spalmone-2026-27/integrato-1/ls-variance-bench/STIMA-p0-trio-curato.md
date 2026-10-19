# STIMA p(0 | race + delivery curata): priors per integrato-6 (Fedora, 20/09)

ZERO-COMPUTE: nessun run nuovo. Fonti: VERDICT banco 628 (serie 8 run dalla
stessa costruzione di integrato-5, solo race LS diversa), banco 632 (terna su
m39), conferme 629 v3/v4. E' un PRIOR dichiarato, non una misura: integrato-6
misura.

## La serie 628 e la copertura della catena odierna

| run | hard | famiglie | copertura con CSF+EDF[delivery]+HRF v4 |
|-----|------|----------|----------------------------------------|
| 4 | -1 | H32x1 | EDF: chiude quasi certo |
| 1 | -4 | H32x2, H34x1, H7x1 | H32/H34 via EDF; H7 via h7cap v4 SE donatore esiste (T08/T21: esisteva); altrimenti h7swap (NON PROVATO) |
| 2 | -4 | H17x2, H34x1, H7x1 | H34 via EDF; H7 idem; H17 dipende dalla classe (teacher-locked curabile da rotazioni/x-swap, VRP-bloccata no: la lezione G13 630 e' VRP-bloccata) |
| 5 | -4 | H17x3 | tutto sulla famiglia H17: curabile se non VRP-bloccate |
| 7 | -5 | H17x5 | idem, piu' profonde |
| 3/6/8 | -10..-12 | H0+H17+H32 | H0 via CSF; H32 via EDF; la coda H17 (fino a x6) e' il collo |

FAMIGLIA NON COPERTA dalla terna: H25 (stage fuori giorni stage, m39-off
residua H25x4). CHIUSA IN DIAGNOSI (20/09, verifica al numero esatto): le 4
celle sono tutte G07 2027-06-09 slot 8-11, UNPINNED, ed e' ARTEFATTO DI
REPLAY: l'export m39 e' del 14/09 (pre-cura input), la data 06-09 e' stata
curata il 18/09 ((5,S) -> (5,A) golden-first: il golden mette 5 ore ORDINARIE
quel giorno), quindi lo stage li' piazzato dal vecchio run ora sfora. Su cicli
freschi col seed attuale il VRP (quota A + stage-confinement) impedisce quella
collocazione in costruzione: la famiglia NON ESISTE live e nessun braccio la
deve curare (le Stage sono pseudo-docente, movable() le esclude). Il residuo
REALE di m39-off dopo la terna e' quindi H32x1, non -7.

## Prior per integrato-6 (catena v4 + H30 leg, h7swap OFF)

- p(0 | singolo tentativo): 0.3-0.5 (la serie live col wrapper pre-v4 era
  -4,-1,0,0 = 2/4 a zero; v4 aggiunge copertura H7-donor e le due cure
  net-zero H31/H28 che prima SPEDIVANO violazioni a pareggio).
- Con multi-start N=5 (stop al primo 0): P(>=1 zero) > 0.83 anche al prior
  basso (complementare di 0.7^5); al prior alto ~0.97.
- La coda dura resta H17 profonda (run 3/6/8) e H25 se emerge.

## Cosa trasformerebbe il prior

1. h7swap PROVATO (banco T13 su base room-faithful in preparazione):
   copre gli H7 donor-less, non gli H17.
2. TASK-631 (composizione giorni availability-aware): cura a monte la classe
   H17 VRP-bloccata (G13/T10): agisce in COSTRUZIONE, riduce la coda alla
   partenza, non al delivery.
3. Il follow-up 628 (terna invocata inline su race fresche, ~2h/run): misura
   direttamente p(0 | race+delivery) senza ciclo completo. E' la corsa lunga
   candidata come chiusura giornata, insieme o alternativa a integrato-6.

## Registro

- Scritto PRIMA di qualunque run nuovo (nessuno dei numeri sopra e' misura).
- La predizione operativa: se integrato-6 (quando lanciato) consegna sotto il
  prior basso, la prima domanda e' quale famiglia residua domina (census
  [delivery] + sanity), non "perche' non zero".
