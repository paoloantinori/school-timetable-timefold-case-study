# Research Report: vale la pena esplorare il multi-start per uscire dal bacino H17?

**Date**: 2026-08-02
**Depth**: deep
**Confidence**: HIGH sulle due conclusioni portanti, MEDIUM sulla stima quantitativa

## Executive Summary
GO, ma nella forma economica e con una misura di indipendenza obbligatoria. La letteratura
conferma il punto centrale (il multi-start funziona SOLO se la diversificazione sta nella
COSTRUZIONE, non a valle) e spiega meccanicamente perche' il nostro portfolio DRAFT-005 e'
fallito. Attenzione pero': la stessa letteratura avverte che il restart completo e' la scelta
debole quando il costo per run e' alto, e per il timetabling indica un'alternativa canonica che
zai aveva classificato "incerta" (mosse Kempe-chain / connettivita' del vicinato). La
raccomandazione e' quindi multi-start come PROCEDURA operativa a basso costo implementativo,
con la connettivita' come cura strutturale se il pattern si ripete.

## Findings

### 1. Il frame e' corretto: diversificazione nella costruzione (GRASP) - HIGH
Marti, Resende & Ribeiro, survey sui metodi multi-start [1], verbatim: *"Modern multi-start
methods usually incorporate a powerful form of diversification in the generation of solutions to
help overcome local optimality. Without this diversification, such methods can become confined to
a small region of the solution space, making it difficult, if not impossible, to find a global
optimum."* Questo e' esattamente il referto del nostro n=3: candidati generati dalla STESSA
costruzione + PROMOTION restano confinati nella stessa regione e convergono a hard identici. La
forma canonica della diversificazione in costruzione e' GRASP [2]: greedy randomizzato con
Restricted Candidate List, ripetuto, best-of-N. Il nostro caso mappa 1:1 (greedy deterministico
su template -> RCL sui tie-break gia' censiti dal recon TASK-480).

### 2. Il contro-argomento serio: il restart e' debole quando il run costa caro - HIGH
Lourenco, Martin & Stutzle (Iterated Local Search) [3]: su istanze grandi il random restart
degrada perche' la distribuzione dei costi degli ottimi locali si concentra (si ricampiona il
"tipico", non il buono) e perche' costa: *"iterated local search can perform up to 260 times more
local searches in the same CPU time compared to random restart"*. Tradotto da noi: un ciclo
staged costa ~40 minuti, quindi N=3 significa ~2 ore per un orario.
**MA la logica del contro-argomento non si applica al nostro obiettivo**: non cerchiamo il
minimo costo (dove la concentrazione uccide il restart), cerchiamo un evento binario ad alta
probabilita' per campione (bacino non saturo). Sui nostri run osservati la frequenza di seed
"buoni" e' ~0,6-0,65 (run3, run6, probe-run1, ab-final run1/run2 buoni; run4, run5, probe-run2
cattivi): con costruzioni davvero indipendenti, 3 start portano P(almeno un 0-hard) a ~0,95.
E' il regime in cui il multi-start e' efficiente, non quello patologico dell'ILS-vs-restart.

### 3. L'alternativa che la letteratura del timetabling indica come canonica - HIGH
Lewis & Thompson, *Analysing the Effects of Solution Space Connectivity* [4]: il risultato
centrale e' che aumentare la CONNETTIVITA' dello spazio con operatori di vicinato specializzati
migliora sistematicamente la qualita' raggiungibile, e nel timetabling l'operatore canonico e' la
Kempe chain (scambio coordinato di composizione tra due slot). E' esattamente la classe di mossa
che il nostro fallimento richiede: la cascata depth-3 non chiude il buco perche' e' una mossa
locale in uno slot saturo, mentre una Kempe chain riorganizza wholesale la composizione dello
slot. Questa e' la cura STRUTTURALE (un run, niente Nx), ma costa: nuovo move selector custom nella
LS, piu' rischio di regressione su un percorso adottato.

### 4. Quando il multi-start NON aiuta: i due segnali da misurare - HIGH
(a) **Costruzioni correlate**: se la randomizzazione non cambia il profilo di saturazione della
cella critica, gli N run ricadono nello stesso bacino e il guadagno e' nullo (il nostro
precedente esatto). (b) **Bacino cattivo molto probabile**: se p(buono) fosse bassa, N
crescerebbe fino a costi assurdi. Entrambi si misurano PRIMA di industrializzare: basta
generare N costruzioni e confrontare l'occupazione della cella critica; se non si diversifica,
si abortisce senza aver speso un solve.

## Confidence Assessment
Sicuri: il frame GRASP e' quello giusto e spiega il fallimento del portfolio; la connettivita'
(Kempe) e' l'alternativa canonica del dominio; i due segnali di non-aiuto sono misurabili a
costo ~zero. Incerta: la stima p(buono)~0,6 viene da 8 run non tutti a protocollo identico,
quindi e' indicativa, non statistica; va ricalcolata sul campione del multi-start stesso.

## DECISIONE (Fedora)
**GO all'esplorazione, in questa forma e in quest'ordine:**
1. **Gate di indipendenza (costo: minuti, nessun solve)**: randomizzare i tie-break della
   costruzione (RCL sui punti gia' censiti nel recon) e generare N=5 costruzioni; misurare se il
   profilo di saturazione dello slot critico cambia. Se NON cambia -> STOP, multi-start
   archiviato con evidenza, si passa alla connettivita'.
2. Solo se il gate passa: multi-start come **script di orchestrazione N-processi + pick-best**
   (nessun refactor in-process: il recon ha dimostrato che gli static volatile vietano il
   parallelismo in-JVM), quindi PROCEDURA operativa, non cambio di default.
3. **Kempe-chain resta il piano B strutturale** e va aperto come task, perche' e' la risposta del
   dominio al nostro problema e non paga il fattore N sul wall-clock.

## Sources
1. Marti, Resende & Ribeiro, "Multi-start methods for combinatorial optimization",
   https://www.uv.es/rmarti/paper/docs/multi4.pdf
2. Feo & Resende / Resende, "GRASP: greedy randomized adaptive search procedures",
   https://mauricio.resende.info/doc/sgrasp2009.pdf
3. Lourenco, Martin & Stutzle, "Iterated Local Search: Framework and Applications",
   https://www.academia.edu/93954896/Iterated_Local_Search_Framework_and_Applications
4. Lewis & Thompson, "Analysing the Effects of Solution Space Connectivity with an Effective
   Metaheuristic for the Course Timetabling Problem", https://rhydlewis.eu/papers/EJOR-TT-RLJT.pdf
5. Hasle, "Discrete Optimization Heuristics" (GRASP / RCL, diversificazione),
   https://www.sintef.no/globalassets/project/evitameeting/hasle-discrete-optimization-heuristics.pdf
