# G11 late-start: convergenza aule/inizi-in-ritardo, REFUTATA (2026-07-29, read-only)

TASK-450 vs TASK-456. Ipotesi iniziale (maintainer/Fedora): gli inizi in ritardo di G11 e la
penuria di aule sono LO STESSO PROBLEMA (10 aule, 11-13 gruppi a slot0; G11 resta senza
stanza, quindi comincia alle 9), e il lab-fallback (che aggiunge i lab a slot0) dovrebbe far
calare gli inizi in ritardo.

## CORREZIONE (Fedora, post-analisi): la convergenza NON regge, motivo strutturale

Nel ciclo a stadi la fase che decide gli ORARI gira quando le stanze NON sono ancora
assegnate (e' proprio il disaccoppiamento introdotto da TASK-446), e la fase delle stanze
non sposta nessuna lezione nel tempo. Quindi nel momento in cui il blocco di G11 viene
spinto alle 9, le aule NON esistono ancora nel modello e non possono essere la causa.
Conferma indiretta: a fine costruzione quella giornata inizia alle 8, nel run finale inizia
alle 9, quindi lo slittamento nasce nella fase cieca alle stanze.

La contesa di aule a slot0 e' reale e il lab-fallback la risolve (vedi numeri sotto), ma
NON e' cio' che genera il ritardo. Quindi il lab-fallback NON chiude il late-start.

## Causa reale (tornata al centro, da stamattina)

La costruzione lascia un BUCO alla SECONDA ora (33 giornate su G11, 17 venerdi), il buco
e' una violazione hard di H17, e la riparazione piu' economica trasla il blocco a destra
(9-13 invece di 8-12). Il pezzo di prova mancante e' il dump PER FASE, che dira' in quale
momento esatto la giornata scivola dalle 8 alle 9: attaccato alla prossima certificazione,
era gia' deciso come TASK-453. Non serve altra analisi ora.

## Numeri di capienza (valide come DATO, ma NON spiegano il ritardo)

Restano utili come misura della contesa a slot0 (il problema che il lab-fallback risolve,
separatamente dal late-start):

- Baseline flag-OFF (`day-start-anatomy.py` sul dump del cert): 15 giornate in ritardo,
  TUTTE G11 / venerdi (run inizia alle 9, golden alle 8, golden 0 su G11-venerdi).
- Capacita' a slot0 (08:00) nei 16 venerdi late-start di G11: gruppi@8 = 11-13,
  aule ordinarie occupate = 8-10/11 (sature), laboratori LIBERI = 4-6 (media 5.2)/7.

Questi dicono che le aule a slot0 sono sature e i lab sono liberi: il lab-fallback aggiunge
capacita' di aula (risolve la penuria), ma cio' e' ORTOGONALE al late-start, che nasce prima
dell'assegnazione delle stanze.

## Stato

- Late-start G11: causa = buco H17 in costruzione riparato per traslazione. Prova
  per-fase mancante (TASK-453, prossima certificazione). Non e' un problema di prezzo (M32)
  ne' di capacita' di aula (lab-fallback): e' un difetto di costruzione/riparazione.
- Lab-fallback (TASK-456): resta valido per la penuria di aule (chiude quella), NON per il
  late-start. Ratifica di maintainer (flag ON + exclude POLIVALENTE) invariata.
