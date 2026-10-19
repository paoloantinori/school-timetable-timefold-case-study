# Research Report: Forma dei vincoli di quota giornaliera (H32/H34) nel timetabling

**Date**: 2026-09-01
**Depth**: exhaustive (14 ricerche + 3 scrape mirati + verifica avversariale)
**Confidence**: HIGH sulla letteratura citata, MEDIUM sul trasferimento al nostro dominio

## Executive Summary

La domanda di maintainer: H32 (quota giornaliera a tolleranza zero) e H34 (giorno dichiarato vuoto) sono espressi nella forma giusta per la ricerca? La risposta a due livelli: la semantica hard a tolleranza zero e' legittima e valida per la CONSEGNA (il golden converge esattamente alla quota), ma il benchmark di riferimento mondiale del dominio (ITC2007/CB-CTT) esprime i vincoli analoghi come SOFT a penalita' calcolata per-unita' (gradiente), e la letteratura cura il problema del campionamento lungo-raggio (che e' il nostro vero collo) con operatori destroy-and-repair di grande neighborhoods (ALNS), non riformulando i vincoli. La cura notturna SurplusHarvest e' indipendentemente un operatore di quella famiglia.

## Findings

### 1. Come il benchmark mondiale esprime i vincoli analoghi (CB-CTT / ITC2007)

Il riferimento del dominio (Curriculum-Based Course Timetabling, formulazioni UD1-UD5) classifica i soft constraint in due tipi: a COSTO COSTANTE (una penalita' per violazione: IsolatedLectures, TravelDistance, RoomSuitability, DoubleLectures) e a COSTO CALCOLATO (penalita' proporzionale all'entita' della violazione). I nostri vincoli hanno li' esattamente i loro corrispondenti:

| Nostro | Corrispondente CB-CTT | Forma nel benchmark | Peso UD1/UD2 |
|---|---|---|---|
| H34 (giorno dichiarato vuoto) | S2 MinWorkingDays | SOFT, penalita' (MWD - N) x w: giorni-mancanti, per-unita' | 5 |
| H32 (quota giorno, deficit orario) | S6 StudentMinMaxLoad | SOFT, penalita' (Min - N) x w: ore sotto il minimo, per-unita' | 2 |
| H17 (buchi interni) | S4 Windows | SOFT, penalita' lunghezza finestra x w, per-periodo | 4 |

Fonte: Banbara et al. (PATAT 2016, encoding ASP di CB-CTT con la tabella pesi UD1-UD5 e la definizione operativa delle penalita'), piu' la specifica ITC2007 (unitime.itc2007.pdf).

Nota critica onesta: il CB-CTT mette soft perche' li' il min-days e' una PREFERENZA di spreading decisa dal pianificatore, non un dato dichiarato dalla scuola. Il nostro caso e' piu' forte: la quota per-data e' INPUT autoritativo e il golden converge esattamente (tolleranza zero validata). Quindi il transfer della FORMA soft non e' automatico: la differenza semantica reale va riconosciuta.

### 2. Le guide ufficiali del nostro solver (Timefold/OptaPlanner)

- I livelli si confrontano lessicograficamente: 0 hard con 1.000.000 soft batte 1 hard con 0 soft (docs.timefold.ai, Constraints and score / Overview).
- Anti-pattern "score folding": usare un peso enorme per simulare un livello e' dichiarato broken: se il business vuole gerarchie, servono livelli veri.
- Over-constrained planning: quando nessuna soluzione feasible esiste, Timefold consiglia nullable (lasciare entita' non assegnate) piuttosto che degradare l'hard; e i pesi hard contano comunque perche' "la soluzione meno infeasibile stima quante risorse mancano".
- Nessuna indicazione ufficiale a favore di hard flat per vincoli di quantita': il pattern consigliato per vincoli di carico e' penalita' pesata per-unita' al livello adeguato.

### 3. La patologia da penalita' piatta e la traversabilita'

Il calcolo fatto nel reflect sul nostro modello (giorno da 4h vuoto: H34(1)+H32(4)=5, poi 3, 1, 0 man mano che si riempie) mostra che la NOSTRA forma e' gia' monotona: ogni ora aggiunta migliora. La letteratura sulla fitness landscape (ParadisEO-MO, Talbi) conferma che il problema classico delle penalita' piatte e' il PLATEAU (nessun gradiente che guida la ricerca), non il muro: la nostra forma per-ora evita il plateau sul riempimento. Il patologico nel nostro caso e' ALTRO: il campionamento.

### 4. La cura stabilita per il long-range non campionato: ALNS / ruin-and-recreate

- Kiefer, Hartl, Schnell (Annals of Operations Research 2017): ALNS per CB-CTT basata su "destroying and subsequently repairing relatively large parts of the solution" con operatori specifici del problema: BATTE i migliori algoritmi ITC2007 e trova 5 nuovi best-known.
- Fondamenti: Ahuja-Orlin VLNS survey (DAM 2002); Schrimpf et al. ruin and recreate (JCP 2000).
- Variante notturna nostra: SurplusHarvest (togli il blocco dal donatore lontano, ricrea sul giorno vuoto) e' un operatore destroy-and-repair specializzato: stessa famiglia, scoperta indipendente. La letteratura aggiunge il pezzo che a noi manca: la ADATTIVITA' (pesi degli operatori che si adattano ai risultati: la nostra adaptive-weights infrastruttura TASK-500 e' il gancio naturale).
- Strategic oscillation (Glover): attraversare deliberatamente il confine feasible/infeasibile con pesi dinamici; imparentata col nostro quasi-tier; per il nostro caso specifico non serve perche' il percorso di riempimento e' gia' monotono.

### 5. CP-SAT (per confronto di paradigma)

Pattern standard: half-reified constraint (booleano implica il vincolo) + penalita' lineare della booleana in objective (docs OR-Tools + discussions): anche li', i vincoli di quantita' entrano come penalty pesata nell'obiettivo, non come hard puri, quando il dato e' una preferenza; diventano hard solo da dati di disponibilita' (H4 Availability-like).

## Verifica avversariale (tentativi di confutazione)

1. "Il CB-CTT usa soft quindi anche noi dovremmo": CONFUTATA la transferibilita' automatica: la nostra quota e' input dichiarato (il golden la pratica al 100%), la loro e' preferenza. La semantica di maintainer (caratteristica della soluzione accettabile) regge.
2. "La forma hard ci blocca": confutata dal calcolo di monotonia: il gradiente c'e' (ogni ora migliora); il blocco e' nel repertorio di mosse (distanza donatore-target), non nello score.
3. "Serve il quasi-tier per traversare": indebolita: la traversata parziale e' gia' migliorante senza cambiare livello; il quasi-tier non aggiunge il campionamento mancante.

## Confidence Assessment

- HIGH: formulazioni CB-CTT/ITC2007 (tabella pesi letta dal paper), efficacia ALNS su questo dominio (abstract con risultati verificati), guide Timefold (docs ufficiali).
- MEDIUM: il transfer al nostro dominio (differenza semantica input-dichiarato vs preferenza: ragionata, non misurata).
- LOW/non verificato: il meccanismo preciso per cui la nostra LS non campiona mai la mossa lunga (ipotesi filtro mosse vs probabilita': resta la domanda aperta da accertare nel codice).

## Sources

1. Banbara et al., teaspoon/CB-CTT ASP encoding, PATAT 2016: cs.uni-potsdam.de/wv/publications/TEMP_conf/patat/BanbaraIKSSNW16.pdf (tabella pesi UD1-UD5, definizioni penalita' calcolate)
2. ITC2007 specification: unitime.org/papers/itc2007.pdf
3. Kiefer, Hartl, Schnell, ALNS for CB-CTT, Ann Oper Res 252 (2017): link.springer.com/article/10.1007/s10479-016-2151-2
4. Ahuja, Orlin, Very large-scale neighborhood search, DAM 123 (2002)
5. Schrimpf et al., Record breaking optimization results using the ruin and recreate principle, JCP 159 (2000)
6. Timefold docs, Constraints and score / Overview: docs.timefold.ai/timefold-solver/latest/constraints-and-score/overview
7. Timefold over-constrained planning (SO 78489123, docs common-patterns)
8. Glover-style strategic oscillation: academia.edu/18670990, icaps06 infeasible search analysis
9. OR-Tools CP-SAT soft constraints: github.com/google/or-tools/discussions/2488, developers.google.com/optimization/cp
10. ParadisEO-MO fitness landscape analysis (hal-00832029); Talbi, Metaheuristics (landscape chapter)
