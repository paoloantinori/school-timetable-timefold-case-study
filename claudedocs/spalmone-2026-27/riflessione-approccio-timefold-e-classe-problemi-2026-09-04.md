# Riflessione critica: l'approccio Timefold e la classe di problemi che stiamo risolvendo

Data: 2026-09-04. Compagno dell'audit e della roadmap. Qui non ci sono nuovi fatti ma una **valutazione ragionata** dell'approccio seguito, dei suoi punti di forza, dei suoi errori, e dei rischi strutturali che l'audit ha messo in luce. Le fonti fattuali sono nei due documenti gemelli; qui si ragiona sopra di esse e sopra la letteratura gia' prodotta internamente (`claudedocs/research_*`).

---

## 1. In che classe di problemi siamo davvero

Il problema e' un **school timetabling curriculum-based con vincoli italiani di produzione**: 14 gruppi, 26 docenti reali + pseudo-docente Stage, ~1.392 timeslot (174 giorni x 8 slot, lun-ven), ~12.500 lezioni da collocare, stanze con tag-laboratorio, quote giornaliere per-data dichiarate dalla scuola, e un vincolame che cresce per ratifica (oggi ~30 vincoli hard/medium/soft attivi). E' un problema **NP-hard di scheduling con vincoli di risorsa e di forma**, imparentato col CB-CTT dell'ITC2007 ma con una differenza semantica decisiva: **molti dei nostri vincoli "di forma" non sono preferenze del pianificatore ma dati dichiarati dalla scuola** (la quota per-data e' input autoritativo, e il golden converge esattamente). Questo e' verificato nella research interna (`research_h32-h34-forma-vincoli-timetabling_2026-09-01.md`): il trasferimento automatico della forma SOFT dal benchmark mondiale al nostro caso e' stato **confutato** perche' la semantica e' diversa.

Conseguenza: stiamo risolvendo un problema in cui la **feasibility stessa e' il prodotto** (0hard e' binario e bloccante), non un'approssimazione di un ottimo. Questo cambia la gerarchia degli obiettivi: prima la feasibility, poi la qualita' (medium/soft).

---

## 2. Cos'e' andato bene: il pivot dal solver-alla-volta all'orchestrazione

La decisione architetturale piu' importante dei due mesi e' stata riconoscere, il 5/08 (`campaign-status-2026-08-05.md`), che la **metaeuristica era esaurita** e che il guadagno veniva dall'**orchestrazione**: staged cycle a 5 fasi + multi-start GRASP + una batteria di finisher deterministici (ExactFinisher, RoomTailFinisher, EmptyDayFinisher, QuotaFillRepair, SurplusHarvest). Questo e' coerente con la letteratura matheuristics (fix-and-optimize) e con ALNS: la research interna ha classificato SurplusHarvest come un **operatore destroy-and-repair specializzato** scoperto indipendentemente. Il risultato di punta (0hard end-to-end il 25/08) e' venuto da li', non dalla LS.

**Giudizio: approccio corretto e ben eseguito.** Il pattern "la ricerca locale porta in zona, i pass deterministici chiudono la coda che la LS non campiona" e' la forma giusta per questo problema. La LS e' *cieca alla sequenza* lungo-raggio (misurato: ~15Mx il budget per la mossa concatenata giusta), quindi affidarle la chiusura della coda sarebbe stato l'errore; spostarla su pass deterministici e' stata la scelta vincente.

---

## 3. Gli errori e le fragilita' che l'audit ha messo in luce

Questa e' la parte che conta per il futuro. Quattro rilievi onesti.

### 3.1 Il claim di garanzia non ha seguito il vincolame

Il nodo centrale dell'audit: m-13 e' stata dichiarata sbloccata l'11/08 su un vincolame che il 26/08 era gia' superato. **La proprieta' "0hard statisticamente garantito" non e' stata re-validata quando sono entrati quattro vincoli hard nuovi.** Non e' un errore di metodo (la misura N=32 era corretta per il vincolame di allora), ma e' un errore di *regime*: una garanzia statistica e' un'ipotesi sul contesto, e quando il contesto (il vincolame) cambia, la garanzia va ri-misurata, non ereditata. Regola che ne discende: **ogni misura di garanzia statistica porta la data e il set di vincoli attivi; a ogni nuovo vincolo hard DEFAULT ON, la garanzia scade fino a re-validazione.**

### 3.2 Il perimetro dei finisher e' cresciuto piu' in fretta della loro verifica di persistenza

La scoperta del 4/09 (istanza orfana della catena post-B-ROOM, con antitesi) e' il sintomo di un rischio strutturale: **abbiamo aggiunto pass deterministici (QFR-2, EDF, SurplusHarvest) a una catena la cui semantica di persistenza non era stata verificata come un tutto.** Se l'ipotesi orfana e' confermata, parte del lavoro dei finisher potrebbe non essere mai arrivato al DB. Non e' un fallimento dei finisher (che nei loro banchi localizzati funzionano), ma un'**integrazione non verificata end-to-end**. Lezione: ogni nuovo pass in una catena che tocca lo stato deve avere un test che prova che le sue mosse *persistono* fino alla consegna, non solo che *si applicano* nel pass.

### 3.3 Il bug score-vs-DB (TASK-526) mina la fiducia del metro

Il bug per cui un run dichiara 0hard in-memory mentre il DB persiste migliaia di lezioni senza stanza e' il punto piu' delicato di tutto l'approccio: **se il metro (lo score) non e' allineato al dato persistito (il DB), ogni verdetto e' una congettura.** Va chiuso per primo, prima di qualunque altra cosa, perche' e' la precondizione di fiducia di tutta la misura. Questo non e' un problema di Timefold ma di come persistiamo il risultato del wrapper multi-start.

### 3.4 La crescita del numero di flag/meccanici ha un costo di controllo

Conteggio: ~30 vincoli, decine di flag opt-out ratificati, finisher multipli, banner di stato. La research interna ha gia' dovuto introdurre la regola "letto PER MATCH, non captured" dopo l'incidente m22MinDays (cache a boot) e il "banner-check" dopo il jar-stantio. **Il rischio e' che la superficie di configurazione diventi troppo grande per essere verificata a ogni run.** La contromisura e' meccanica (pinnare i banner via test, preflight obbligatorio), e va applicata con la stessa disciplina con cui si aggiungono i meccanismi.

---

## 4. L'approccio Timefold: punti di forza e limiti che stiamo toccando

### Forza
- **Constraint stream + score lessicografico**: la separazione hard/medium/soft mappa bene la gerarchia feasibility -> qualita'. La forma per-ora dei nostri vincoli di quota e' gia' monotona (verificato nella research: ogni ora aggiunta migliora, niente plateau), quindi la FORMA del vincolo non e' il collo.
- **Custom phases + custom moves**: la capacita' di inserire pass deterministici Java puri (WeeklyTemplateBuilder, finisher) dentro il ciclo Timefold e' cio' che ha reso possibile il pivot orchestrativo. E' la feature che stiamo usando di piu' e meglio.
- **Community edition sufficiente**: la research `research_timefold_enterprise_fork_2026-08-12.md` ha valutato il fork Enterprise; siamo su 2.3.0 community e le feature critiche le abbiamo reimplementate dove serviva.

### Limiti che stiamo toccando (e come li stiamo aggirando)
- **La LS community non campiona mosse lungo-raggio / multi-entita' complesse.** E' il limite strutturale che ha reso necessari i finisher. Lo aggiriamo con pass deterministici, ma e' un aggiramento: la LS resta cieca alla sequenza. Se in futuro servisse davvero la mossa lunga dentro la ricerca, la via canonica e' ALNS con operatori adattivi (la research lo indica; la nostra infrastruttura adaptive-weights TASK-500 e' il gancio), non la LS standard.
- **Score-vs-DB e cache VRP**: la famiglia di bug TASK-509/526/578 (cache stale, istanze riusate tra tentativi, score che non riflette il persistito) nasce dal fatto che facciamo girare il solver in un wrapper multi-start ad hoc fuori dal SolverManager, con entita' che sopravvivono tra i tentativi. E' il prezzo dell'orchestrazione custom; va pagato con verifiche di persistenza esplicite.
- **Costo per run alto** (~40 min/ciclo, 7-8h per un multi-start da 5): il multi-start e' la scelta debole quando il run costa caro (Lourenco et al., citato in `research_multistart-vs-alternative_2026-08-02.md`), MA la logica del contro-argomento non si applica perche' il nostro obiettivo e' un evento binario ad alta probabilita' per campione, non il minimo costo. Scelta consapevole e corretta, ma il costo impone di tenere N basso e di usare il predictor di doom (TASK-581, schema gia' creato) per scartare presto i tentativi condannati.

---

## 5. Raccomandazioni sull'approccio (non sui singoli task)

1. **Trattare ogni garanzia statistica come deperibile.** Data + set vincoli attivi su ogni claim; re-validare a ogni nuovo hard DEFAULT ON. (Nasce da 3.1.)
2. **Nessun nuovo pass deterministico senza un test di persistenza end-to-end.** Il test deve provare che le mosse arrivano al DB, non solo che si applicano. (Nasce da 3.2.)
3. **Chiudere TASK-526 prima di qualunque celebrazione di 0hard.** Il metro deve essere allineato al persistito. (Nasce da 3.3.)
4. **Tenere la superficie di configurazione sotto controllo meccanico.** Pinnare i banner via test, preflight obbligatorio, sweep dei flag morti. Ogni flag e' un punto dove un run puo' silenziosamente girare diverso da come crediamo. (Nasce da 3.4.)
5. **Non riaprire le vie chiuse con evidenza.** Metaeuristiche (TASK-569), quasi-tier (TASK-538), hole-closer locale (TASK-481), Kempe (TASK-482): tutte chiuse con misura. Riaprirle solo se cambia il contesto che le ha chiuse (es. se la LS community introducesse un vicinato lungo-raggio nativo).
6. **Considerare ALNS adattivo come unica evoluzione di ricerca sensata, se mai servira'.** Non ora (il perimetro dice che non serve), ma e' la direzione che la letteratura indica per il collo del campionamento, e abbiamo gia' il gancio (adaptive-weights). Da tenere come nota, non come task.

---

## 6. Giudizio di sintesi

L'approccio seguito e' **sostanzialmente sano e ben radicato nella letteratura del dominio**: il pivot all'orchestrazione era la mossa giusta, la forma dei vincoli e' corretta per la semantica del problema, e la lettura del collo (campionamento, non forma dello score) e' stata confermata da verifica avversariale. Gli errori emersi non sono di direzione ma di **regime di verifica**: garanzie non re-validate al cambio di contesto, integrazione dei finisher non verificata end-to-end sulla persistenza, e un metro (score-vs-DB) che va reso affidabile prima di fidarsi di qualunque 0hard. Sono errori correggibili e localizzati, non strutturali. La classe di problemi e' ben capita; il rischio residuo non e' "stiamo risolvendo il problema sbagliato" ma "stiamo misurando la soluzione con strumenti la cui fiducia va prima garantita".
