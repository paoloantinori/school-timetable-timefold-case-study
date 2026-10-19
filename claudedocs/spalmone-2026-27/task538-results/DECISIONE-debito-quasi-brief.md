# Brief per la decisione: quanto debito quasi accettare (TASK-538, per maintainer)

Scritto la notte 29/08, AGGIORNATO con n=2 alle 05:50 del 30/08 (run1 e run2
entrambi consegnati, verificati sui raw). Non e' una proposta di adozione: e' la
domanda strutturata con le opzioni. La decisione e' di maintainer.

## AGGIORNAMENTO n=2 (30/08 mattina, verifica Fedora)

- **run2: `[-1/-19]`** = pure-0 NON replicato per UNA sola H28
  (criterio di confutazione pre-registrato scattato). Banda: puro negativo.
  NOTA meccanica (30/08, da pushback zai verificato nel codice): sotto multi-start
  il wrapper possiede i flag GRASP e il tentativo 1 e' SEMPRE deterministico
  (TASK-483): il seed dell'operatore e' inerte. La varianza run1/run2 viene dal
  nondeterminismo MT-LS e dalla lotteria dei retry predictor, NON dal seme: i run
  sono campioni della STESSA distribuzione di lancio (n=3 in corso con run3).
  Tracciato in TASK-573 (onorare o documentare).
- **Debito quasi VARIABILE (19-30), composizione STABILE** (H32 dominante + filone H34 6-7).
- **Guardie INVERSE al debito**: run2 (debito minore) migliore ovunque: full-day
  90,2 (vs 88,6 di run1), buchi 0, sanity 2 rossi (vs 5).
- **Firme strutturali (n=2, confronto cella-per-cella)**: solo 4 celle replicate
  in entrambi i bacini, tutte giorni pieni dichiarati vuoti in coda d'anno (G06
  09-10/06, G11 21/05, G12 16/04, ~17h). Le 10 celle -1h "sistematiche" di
  G07 erano scelte del bacino run1: run2 non le ha. La matematica del pool
  (zai): G07 copertura ESATTA con slack 0, G13 990h coperto (foglio-fantasma
  da notifica). Quindi il debito STRUTTURALE e' solo la coda d'anno; il resto e'
  variabilita'.
- **Attribuzione medium emendata (zai)**: baseline ricontatata -782.832 (non
  -1.243M: divergenza TASK-509): il 72% del delta medium e' M32 template.

## AGGIORNAMENTO n=3 (30/08 13:30, run3 consegnato a2f74402, verificato)

- run3: `[0/-32]` (H32 -25 + H34 -7) = **pure-0 REPLICATO, 2/3 run**; guardie migliori dei tre (buchi 0, sanity 1 rosso, full-day 88,1); medium -1.389M; predizione pre-registrata 06:15 centrata su tutti i punti.
- n=3 finale: puro 0/-1/0; debito 19-32 (variabilita' tutta H32, il filone H34 e' quasi-costante 6-7); medium -1.32/-1.38/-1.45M.
- Firme strutturali: nucleo su TUTTI e TRE i run = 3 celle (G06 09-10/06, G11 21/05) piu' famiglia coda-d'anno; le -1h di G07 ricompaiono parzialmente in run3 (3 su 10): attrattore debole, non rumore puro.
- La domanda di arbitramento si e' fatta quantitativa (zai): 13-25 giorni sotto quota sono consegnabili per la scuola? Con puro=0 garantito.

Effetto sulle opzioni: la B perde la premessa "firmi sistematiche da curare a
input" (non sono strutturali); la domanda a referente si riduce ai 4 giorni di coda
d'anno + la conferma foglio-vs-pratica G13. La A (flag ON) resta possibile ma
il pure-0 non e' ancora stabile (1 cella su 2 run). La raccomandazione di zai:
meccanismo valido, flag OFF in produzione finche' maintainer non arbitra.

## La domanda

Con `planner.constraints.quasi-tier.enabled=true` il solver consegna la prima
soluzione **pure-0** dell'era H34: `[0/-30]hard`, 12510/12510 lezioni collocate,
zero conflitti/buchi/boxed, stanze raffinate. Il costo e' un debito visibile su
hard livello 1 (le famiglie dichiarative H32/H34) che prima era un muro.

## I fatti (tutti verificati dai raw)

- **Lo scambio e' esplicito, non un fallimento di ricerca**: a flag OFF il
  baseline pagava ~-6 PURO (3 lezioni non collocate + famiglia) per tenere
  H32=1; a flag ON paga 23 celle + 7 giorni QUASI per azzerare il puro. Una
  unita' pura vale piu' di qualsiasi quantita' di quasi nell'ordine del
  punteggio: [0/-30] > [-6/-6]-equivalente.
- **Il debito e' piccolo e mappato** (MAPPA-DEBITO-QUASI.md, riconciliazione
  esatta col -30): 23 celle da -1h (G07 10 volte ott-mar sistematiche, G13
  12 volte, G06 1) + 7 giorni dichiarati vuoti in coda d'anno. Totale ~54h
  su 10.542 = **0,5%** dell'orario.
- **Le guardie lo mostrano onestamente**: sanity 5 rossi (i giorni sotto minimo
  del golden, quasi tutti i vuoti sopracitati), full-day 88,6% vs 91,6 (fuori
  banda -3,3pp), medium +10,7%. Coerenti fra loro: sono il prezzo, non incidenti.
- **Il golden stesso pratica tolleranze analoghe**: 133 celle G13 e 15 giorni
  dichiarati vuoti nel golden (fantasmi del foglio condivisi); il NOSTRO debito
  e' solo la parte OLTRE la pratica della scuola (23+7).

## Le opzioni

A. **Accettare il regime quasi come standard di consegna** (flag ON in
   produzione): priorita' alla fattibilita' fisica (nessuna lezione fuori),
   debito dichiarativo visibile e prezzato; da presentare alla scuola con la
   mappa. Richiede: ratifica + il run2 che confermi la replicabilita' del
   pure-0 + la stabilizzazione del debito (banda attesa ~20-40).

B. **Tenere il flag OFF e usare il regime quasi solo in ricerca** (stato
   attuale: il flag e' opt-in DEFAULT OFF, nessun rischio in produzione): il
   pure-0 resta il risultato che dimostra la direzione; si investe sulle cure
   a input delle firme sistematiche (le 10 celle G07 e le 12 G13 sono
   candidate domanda-referente: se il pool incarichi non copre la somma delle
   quote, quei -1h sono matematicamente forzati e vanno curati nel foglio,
   non nel solver).

C. **Ibrido con tetto**: flag ON ma con un peso/cap sul debito (es. penalita'
   crescente oltre N celle) per fermare l'attraversamento dove il golden
   stesso non tollera. Nuovo sviluppo, da valutare solo se la scuola dice
   "zero giorni vuoti oltre il golden".

## Raccomandazione (da confermare con run2)

B nell'immediato (zero rischio: il default e' gia' OFF) con la domanda a referente
sulle firme sistematiche preparata; A come obiettivo se la scuola accetta la
cornice "0 lezioni fuori orario, 0,5% di giorni sotto quota dichiarata".
La risposta di referente puo' trasformare B in A senza ulteriore sviluppo.

## DECISIONE (maintainer, 30/08 pomeriggio): A RESPINTA, filone = flag OFF + chiusura one-off fisici

Verifica golden-first della proposta A: il costo dominante del regime quasi NON e' pratica della scuola (12-19 giorni ordinari a -1h, quasi tutti G07, dove il golden fa sempre 5/5; il golden avalla solo i vuoti di coda d'anno, 15 suoi). Regola di maintainer: le violazioni fisiche non sono accettabili (né non-piazzate, né senza stanza, né overload). Conclusione: NESSUNO dei due regimi e' consegnabile così com'e'; la linea diventa chiudere a flag OFF i 3 one-off fisici del baseline deterministico (H7 1 overload-day, H29 1 roomless, H33 1 under-floor) per consegnare [0 puro / -7 dichiarative]: zero fisico con debito dichiarativo ENTE la pratica del golden. Nota: le lezioni non piazzate non esistevano nel baseline (H0 = 0: la mia "3 non collocate" del mattino era memoria stantia, corretta). Nuovo target tracciato in TASK-574.
