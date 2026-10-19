# REVISIT: punti di dubbio dichiarati del 17/09 (riferimenti per riconsiderare)

Le decisioni prese oggi sono andate; questi sono i punti dove il quadro e' meno
solido e che meriteranno una riapertura quando arriva evidenza nuova. Un punto
per riga, con il dato che dovrebbe triggerare la riapertura.

1. **M-BF peso-form contro S13 (rischio ricorrenza).** La cura M-BF combatte
   frontalmente il vettore appena nominato: l'erosione interleaved e'
   cristallizzata da S13 (+2/lezione). Una penalita' medium M15-style (20/min)
   su una giornata-intera da' una forza che sopraffare S13 per costruzione:
   fine, ma la ricorrenza unpinned e' gia' al bordo basso della banda (70,0 vs
   76+-2, RUGA dichiarata dalla ratifica TASK-599). TRIGGER DI RIAPERTURA: se
   il banco M-BF mostra ricorrenza < 69 o full-day sotto banda, la forma
   sbagliata e' il peso (provare flat-only sul gap di giornata, non gap-minutes),
   non la direzione.

2. **La forma del peso M-BF e' stata delegata al banco di zai.** Io ho chiesto
   a zai di DECIDERE la forma misurando (flat vs 20/min) e dichiarare la scelta.
   La ratifica finale della forma spetta a maintainer col VERDICT in mano: non
   trattare la scelta del banco come decisione presa.

3. **Lo 0-hard di integrato-3 arriva dalla corner-b refinement, non dal pick.**
   "Nessun 0-hard al pick" significa che il meccanismo che chiude e' la
   rifinitura di consegna, e la selezione dei tentativi (predictor al confine,
   -2 threshold, skip-doomed) decide chi ARRIVA alla rifinitura. Un bacino che
   il predictor scarta o che chiude sotto -2 non arriva mai alla fase che lo
   salverebbe. Non e' un difetto dimostrato (il predictor scarta firme 184+
   buchi, non -1), ma e' un acritico: se un giorno un bacino a -3 boundary
   potrebbe essere rifinibile a 0 e viene shippato condannato, e' qui che
   guardare. TRIGGER: una consegna finale non-0-hard con pick [-2..-8].

4. **La coda bad-seed su questo profile e' molto piu' densa della storica.**
   24 predictor-discard + 2 tentativi condannati su un ciclo: la stima storica
   p(bad)=0,32 non descrive il pescaggio di oggi. O era un profile sfortunato,
   o la costruzione e' cambiata sotto il baseline: IPOTESI CH (maintainer, 17/09
   sera, confermata plausibile dal layering): il predictor legge lo stato a
   fine COSTRUZIONE, quindi solo i cambi di costruzione possono densificare
   la coda; da quando il baseline e' stato misurato (census n=8, 3/8) sono
   entrati DEFAULT ON tre cambi CH: pre-booking v2.1 (TASK-564, 29/8),
   pool-cap-fill (TASK-577, 9/9), teacher-weekday-spread (TASK-599, 12/9).
   I bracci notturni NON possono essere la causa (delivery, post-lettura del
   predictor). PROVA DESIGNATA (banco, minuti/seed): replay solo-costruzione
   su N=8+ seed GRASP con matrice di flag {spread, pool-cap-fill, prebooking}
   ON/OFF e distribuzione dei buchi H17 a fine costruzione per braccio.
   TRIGGER: se un flag sposta la massa sopra soglia 150, colpevole nominato;
   se nessuno, pescaggio. (Serie profili pesanti: 24 discard il 17/9 mattina,
   32/32 di cap il 17/9 sera: terzo profilo consecutivo sopra la storica.)

5. **Full-day 94,5 sopra golden 91,9 letto come positivo.** Coerente con
   STRICT-FIRST (H34 riempie oltre il golden), ma sopra-golden sul fill merita
   una lettura visiva della diff (regola dataviz): se l'occhio vede giorni
   riempiti dove il golden lascia respirare, il metro "sopra golden = bene"
   va riconsiderato per gruppo.

6. **Il 622-duale bench non usa il recorder condiviso** (chiarimento di zai nel
   VERDICT emendato 1c986f2c5): il banco porta il suo contratto
   record-prima-di-mutare. Quando il pass di produzione arrivera', DEVE usare
   il CompositeRecorder; la parita' banco-produzione oggi e' per contratto,
   non per codice. TRIGGER: al porting di qualunque famiglia 622.
