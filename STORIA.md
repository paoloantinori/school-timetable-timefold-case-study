# Storia del progetto Planner

**Periodo coperto:** 13 febbraio 2026 - 3 agosto 2026 (2.292 commit)
**Oggetto:** costruire l'orario annuale di una scuola professionale italiana con un
risolutore automatico, partendo dai fogli che la scuola compila a mano.

Questo documento racconta cosa è successo, nell'ordine in cui è successo, incluse le
strade sbagliate. È scritto per chi (persona o agente) dovrà affrontare un problema
simile e vuole sapere non solo dove siamo arrivati, ma perché ci siamo arrivati per
quella strada. Il documento gemello, [RIAPPROCCIO.md](RIAPPROCCIO.md), dice cosa
faremmo diversamente sapendo quello che sappiamo oggi.

Non è un documento di specifica. Le regole vive stanno in `CLAUDE.md`, la storia
tecnica dettagliata delle ricalibrazioni in
`claudedocs/constraint-recalibration-history.md`, le lezioni operative in
`tasks/lessons.md`. Qui c'è la narrazione che tiene insieme quei pezzi.

---

## Il problema, in una pagina

Una scuola professionale con 14 gruppi classe, 26 docenti, 18 aule e laboratori deve
distribuire circa 12.900 ore di lezione su 171 giorni di scuola, otto ore al giorno,
da settembre a giugno. Non è il classico "orario settimanale che si ripete": è quello
che la scuola chiama **spalmone**, cioè la distribuzione dell'intero monte ore
dell'anno data per data, con periodi di stage aziendale che tolgono interi gruppi
dalla griglia per settimane, rotazioni di laboratorio che ruotano fra i gruppi, docenti
esterni disponibili un solo giorno a settimana, e un calendario che apre e chiude fasce
orarie diverse per gruppo e per giorno.

Il pianificatore umano ci mette settimane e lo fa con una sequenza di passi
deterministici: prima le rotazioni di acconciatura, poi i laboratori, poi le materie
professionalizzanti, poi il riempimento. Il risultato di quel lavoro, per l'anno
2026/27, esiste ed è valido: lo chiamiamo **golden**. Averlo è stato il singolo
vantaggio più grande di tutto il progetto, e allo stesso tempo la trappola più
insidiosa, per ragioni che questa storia racconta.

Lo stack: Quarkus con Timefold Solver (edizione community), PostgreSQL, frontend React.
L'unica entità di pianificazione è la singola ora di lezione.

---

## Epoca 1, febbraio: costruire la macchina (971 commit)

Il primo commit è del 13 febbraio: `v0.1 - Initial working release`. Nel giro di tre
giorni ci sono già il punteggio a tre livelli (hard, medium, soft), le aule
assegnabili a null, e il primo esperimento di modellazione serio.

Quell'esperimento merita di essere raccontato perché **il nome del branch su cui il
progetto vive ancora oggi ne è il fossile**. L'idea era che un blocco di laboratorio
(tre ore consecutive dello stesso docente nella stessa aula) fosse un'entità di
pianificazione a sé, un `LabBlock`, invece che tre ore separate che il solver deve
faticosamente tenere unite. Sembrava elegante: la contiguità diventava strutturale
invece che un vincolo da premiare. Vennero costruite tre famiglie di branch parallele
(baseline, shadow, block-entity) per confrontare gli approcci.

La conclusione, mesi dopo, fu che l'entità dedicata non serviva: le sessioni di
laboratorio sono ore ordinarie che la fase di pre-assegnazione **fissa** (pinna) prima
che il solver parta, e la contiguità viene dalla costruzione, non dal modello. `LabBlock`
fu cancellata, ma il branch continua a chiamarsi `feature/cartesian-block-entity`.
Prima lezione, gratuita: un nome sopravvive alla ragione che l'ha prodotto, quindi va
scritto da qualche parte cosa significava.

Il 23 febbraio, da solo, conta 201 commit. È il ritmo di quella fase: modellazione,
UI, tabella canonica degli identificativi dei vincoli, primo motore di pre-assegnazione
per le rotazioni.

## Epoca 2, marzo: il pianificatore umano come algoritmo (525 commit)

L'intuizione centrale di marzo è che **la sequenza di passi del pianificatore umano non
è folklore, è un algoritmo**. Nasce `HybridPhase1aService`: una ricerca a vincoli (CSP)
che imita quei passi, prova le rotazioni candidate una per una, tiene quelle che non
producono violazioni hard e le fissa. Il solver poi lavora solo su ciò che resta libero.

È anche l'epoca del primo ciclo di irrigidimento. Due vincoli che erano preferenze
diventano requisiti: la compattezza verticale della giornata di un gruppo (niente buchi
in mezzo) passa da medium a hard e diventa H17; il superamento della data di fine ideale
di un corso passa a hard. Il ragionamento era sensato: come preferenze non venivano
rispettate, quindi alziamole di livello.

Mesi dopo, metà di quelle promozioni verrà annullata. Non perché il ragionamento fosse
sbagliato, ma perché mancava il termine di paragone che sarebbe arrivato a luglio.

## Epoca 3, aprile-giugno: la pausa e la manutenzione (20 commit in tre mesi)

Il progetto si ferma quasi del tutto. I pochi commit fanno lavoro di infrastruttura:
l'aggiornamento di Timefold dalla 1.31 alla 2.x, che porta con sé il passaggio a Java
21, la migrazione dell'API dello score director, la terminazione a rendimenti
decrescenti. In mezzo, una ristrutturazione della pipeline da cinque a sei fasi con
l'aggiunta di una fase di *ruin and recreate* che si rivelerà inerte e verrà rimossa a
luglio.

Vale la pena notarlo: **una fase aggiunta perché "la letteratura dice che aiuta" e mai
misurata è rimasta lì per due mesi senza fare nulla.**

## Epoca 4, 16 luglio: arriva il dato vero (il punto di svolta)

Il 16 luglio la scuola consegna i dati reali dell'anno 2026/27 e la decisione è netta,
scritta nel file di milestone: *questo file OVERRIDES tutti i dati precedenti*. Finisce
l'era dei dati sintetici, inizia la campagna sul dato reale, che occuperà tutto luglio
e la prima settimana di agosto (655 commit a luglio, 121 nei primi tre giorni di agosto).

Cambia tutto il modello dati: giornata a otto slot, calendario di apertura per gruppo e
per giorno, importazione del golden come lezioni fissate, e soprattutto la comparsa dei
**fatti di quota giornaliera** (`DailyActivityQuota`): per ogni gruppo e per ogni data,
quante ore la scuola si aspetta in aula, in laboratorio, in stage. Non è una preferenza
nostra, è un input autoritativo, e questo diventerà il criterio per decidere cosa può e
cosa non può restringere lo spazio di ricerca.

## Epoca 5, seconda metà di luglio: il golden ci smentisce (golden-first)

Qui il progetto cambia filosofia, ed è la parte più istruttiva di tutta la storia.

Avendo il golden, diventa possibile fare una domanda che prima non si poteva fare: **il
nostro modello considera valido l'orario che la scuola usa davvero?** La risposta, per
diversi vincoli, era no. Il 22 luglio il commit `model(TASK-392): demote H15 -> M15`
lo dice esplicitamente: il golden spezza 98 volte la regola che imponeva alle ore della
stessa materia nello stesso giorno di essere consecutive. Come vincolo hard, eravamo
**più rigidi della pratica reale della scuola**.

Da lì parte una catena di declassamenti, ciascuno con la stessa forma: misura quante
volte il golden viola la regola, e se la viola in modo sistematico, la regola non è un
requisito, è una preferenza.

- I pomeriggi riservati allo stage: il golden ci mette 575 lezioni ordinarie, perché gli
  servono come capacità. Da hard a medium.
- Le materie professionalizzanti ai bordi della giornata: il golden non ha mai seguito
  quella forma. Da hard a medium.
- Il vincolo che imponeva di non cambiare aula a metà blocco: qui il meccanismo è più
  sottile e più interessante. Come vincolo hard, la ricerca locale preferiva lasciare
  l'aula **nulla** (violazione silenziosa) piuttosto che cambiarla: 291 ore di lezione
  non di laboratorio si ritrovavano senza aula. Declassato a medium, quelle 291 sono
  diventate 3.

Quest'ultimo è il caso da ricordare: **un vincolo hard troppo severo non viene
rispettato, viene aggirato**, e l'aggiramento è di solito peggiore della violazione che
si voleva impedire.

Nella stessa epoca arriva anche una bocciatura importante. Il 17 luglio,
`docs(TASK-351): weekly-template research - NO-GO`: l'idea di costruire un unico
template settimanale e replireferente per tutto l'anno viene misurata e scartata, perché la
ricorrenza reale del golden è del 41,5% in senso stretto e il tetto teorico è del 72%.
La scuola non ripete la settimana. Quella misura ha impedito di costruire un intero
sottosistema su una premessa falsa.

E il 21 luglio arriva il risultato più contro-intuitivo di tutti:
`run 16 budget verdict - more time did WORSE (-3902 vs -426), budget refuted`. Dare
più tempo al solver **peggiorava** il risultato. Non era un problema di budget, era
instabilità di convergenza. Da quel momento i benchmark hanno preso di mira la
stabilità, non il tempo.

## Epoca 6, 22-27 luglio: costruire invece di cercare

Il NO-GO al template come strategia globale non significava che il template fosse
inutile. Significava che non poteva essere l'unica cosa. La sintesi, arrivata con il
lavoro chiamato L2, è stata: **costruire prima un template settimanale per gruppo e
stamparlo cronologicamente su tutte le date compatibili**, lasciando alla ricerca locale
solo il compito di rifinire.

La regola progettuale che ha salvato quella fase è che lo stampo può solo **rinunciare**
a una cella, mai forzarla. Non produce mai un conflitto: se la cella non è compatibile,
salta e si arrangia dopo. Questo rende la costruzione verificabile con un test unitario
puro, senza far girare il solver.

Ha funzionato per una ragione che vale la pena isolare, perché è una legge generale del
mestiere: la ricorrenza settimanale costruita a mano è sopravvissuta alle fasi di
ricerca locale non perché fosse protetta da un vincolo forte, ma **perché la ricerca
locale non aveva niente da guadagnare a romperla**. Una struttura costruita sopravvive
finché nessuna fase successiva ha un gradiente contro di essa. Il giorno in cui un nuovo
vincolo acquista quel gradiente, la struttura collassa in silenzio.

Il 26-27 luglio arriva la seconda idea portante: il **ciclo a stadi**, che in casa
abbiamo chiamato "il treno". Invece di un solve monolitico, una sequenza:
costruzione, promozione del template a fissato, ricerca sui soli orari con demozioni
selettive, rilascio, riparazioni deterministiche, e solo alla fine l'assegnazione delle
aule. I numeri del gate di adozione: zero violazioni hard su due run indipendenti, zero
buchi interni, ricorrenza sulle lezioni libere salita dal 52,8% al 73,5-74%.

## Epoca 7, 28 luglio - 2 agosto: le riparazioni e il livello di orchestrazione

L'ultima epoca è quella in cui il progetto ha smesso di essere "un solver configurato"
ed è diventato **un livello di orchestrazione sopra il solver**. Si accumulano operatori
deterministici, ciascuno con un contratto esplicito: chiudi i buchi interni, ancora la
prima ora, ribilancia il surplus, consolida la giornata di laboratorio su un docente
solo, completa in modo esatto il residuo che la ricerca ha lasciato scoperto.

Ognuno di questi ha la stessa forma difensiva: **migliora o non fare nulla**, con
ripristino dello stato precedente se il tentativo peggiora le cose. È la contromisura
imparata a caro prezzo contro il gioco delle tre carte, cioè l'operatore che sembra
risolvere un problema mentre lo sta solo spostando altrove.

Due adozioni finali meritano il nome. Il **completamento esatto** (adottato il 2 agosto):
quando la ricerca stocastica lascia poche ore non collocate, non si insiste con la
ricerca, si enumera in modo esatto lo spazio residuo, che a quel punto è piccolo. È il
pattern *fix-and-optimize* della letteratura sulle matheuristiche, e ha convertito la
chiusura della coda da probabilistica a deterministica: 18 ore su 18 chiuse sul caso
peggiore noto. E il **multi-start**: siccome la costruzione randomizzata produce bacini
di partenza diversi, si lanciano più costruzioni indipendenti e si tiene la migliore.

Su quest'ultimo c'è un episodio che è il cuore metodologico di tutta la campagna. Un
primo tentativo di portfolio era stato **confutato**: tre candidati che sembravano
diversi in realtà condividevano la stessa costruzione e le stesse 7.504 ore fissate,
quindi non erano affatto indipendenti. Il multi-start è stato riaperto solo dopo aver
introdotto un **gate di indipendenza** da superare prima di spendere un solo minuto di
solve: generare cinque costruzioni randomizzate e verificare che il profilo di
saturazione dello slot critico cambiasse davvero. Cinque profili distinti su cinque,
13 gruppi su 14 che variano. Solo allora si è misurato.

Il residuo aperto a fine periodo: su alcuni semi di partenza restano sei violazioni di
compattezza verticale, tutte concentrate sul mercoledì alle 10 di un gruppo specifico,
in una cella satura su undici gruppi. È dimostrato che nessuna cascata locale di
profondità fino a tre la chiude senza spostare il buco altrove. La direttiva su questo
punto è stata inequivocabile: **zero hard è binario**, nessun residuo è accettabile a
prescindere da quanto sia piccolo. Il multi-start è la copertura operativa; una
soluzione strutturale (le catene di Kempe) è stata valutata e, per ora, scartata con
verdetto negativo motivato.

---

## Il metodo: due agenti, uno che decide e uno che esegue

Una parte sostanziale della velocità di luglio non viene dal codice ma
dall'organizzazione del lavoro, e vale la pena descriverla perché è replicabile.

Il lavoro è stato diviso fra **due agenti su due macchine diverse**, con ruoli
asimmetrici e non intercambiabili:

- Un **agente di controllo** (su Linux) che decide cosa misurare, formula i mandati,
  verifica i risultati sui dati grezzi, ratifica o boccia, e tiene la memoria del
  progetto. Non esegue i lavori lunghi.
- Un **agente esecutore** (su un portatile laptop, che resta acceso di notte) che esegue i
  solve, i benchmark, gli spike, cattura gli artefatti e li pubblica.

Il canale fra i due è **tmux su ssh**: la sessione dell'esecutore vive in una finestra
tmux persistente, e l'agente di controllo la osserva con `capture-pane` e le consegna
mandati con `load-buffer` più `paste-buffer`. Nessuna connessione da mantenere aperta,
solo comandi one-shot. Il canale di ritorno per i risultati veri non è il terminale: è
**git**. L'esecutore committa artefatti e verdetti, l'agente di controllo fa pull e
verifica.

Le regole che hanno reso il meccanismo affidabile sono nate quasi tutte da un guasto:

- **L'esecutore misura, non decide.** Nessuna adozione, nessun cambio di default, nessun
  revert sulla macchina esecutrice. Serve a evitare che una decisione venga presa da chi
  ha in mano solo un pezzo del contesto.
- **Mai fidarsi del verdetto, ricontrollare i numeri sui file grezzi.** Un verdetto
  sbagliato è passato per giorni perché confondeva due grandezze diverse. Da allora ogni
  numero portante viene ricalcolato dall'esportazione, non letto dal riassunto.
- **La prova che un messaggio è arrivato non è quello che vedi sullo schermo**, è che il
  contatore di contesto della sessione remota cresce. Un falso allarme ha prodotto un
  mandato duplicato mentre il primo era già in elaborazione.
- **Indirizzare le finestre per nome, mai per numero.** Un mandato è finito nella
  sessione sbagliata perché gli indici delle finestre erano slittati dopo un riordino.
- **Se l'esecutore fa una domanda, l'agente di controllo risponde subito**, a nome
  dell'umano, con la variante che non impegna (prototipo dietro flag spento, patch
  preparata e non applicata) se la decisione è davvero riservata all'umano. Lasciare
  l'esecutore in attesa è tempo di macchina buttato: una notte è andata quasi persa
  esattamente così.
- **Il mandato notturno prevede sempre una coda di esplorazione** oltre alla coda
  approvata, perché una macchina ferma alle tre di notte è un mandato fallito.

Il costo di questo schema è la disciplina di scrittura dei mandati: ogni consegna deve
essere autosufficiente, perché la sessione remota non condivide il contesto. Il
guadagno è che le otto ore di sonno diventano otto ore di misure.

---

## Gli errori, perché sono la parte utile

Un racconto senza errori non serve a nessuno. Questi sono documentati nel repo con la
data, e diversi hanno prodotto una regola scritta.

**Diagnosi corrette pubblicamente.** Il 31 luglio i 18 buchi introdotti da una modifica
erano stati attribuiti a un gruppo (G13, venerdì); il 2 agosto la catena causale
verificata end-to-end ha mostrato che erano un altro gruppo e un altro giorno (G11,
mercoledì). Sempre il 2 agosto, un documento diagnostico affermava che la ricerca girava
senza metaeuristica; la lettura della configurazione ha mostrato che tabu e mosse a
pilastro erano già attivi da tempo. Entrambe le correzioni sono nel repo come commit di
correzione del record, non come silenziosa riscrittura.

**Una certificazione fermata a metà.** Un ciclo che dura 49 minuti fu osservato al
minuto 26 e dichiarato in stallo. Non era in stallo: in quel ciclo il punteggio hard
resta congelato per quasi 16 minuti e converge solo nell'ultima fase. La diagnosi fu
ritirata e la lezione codificata: **un solve lungo non si interrompe per un sospetto,
prima si confronta l'istante corrente con la cronologia per fase di un run di
riferimento.**

**Due run confrontati che non erano confrontabili**, perché un flag non ancora ratificato
era acceso in uno e spento nell'altro. E, poco dopo, un run misurato su un JAR vecchio di
un'ora e venti rispetto al codice che si voleva misurare. Da qui la regola: prima di ogni
run misurato, ricompilare e verificare che il banner dei flag attivi stampato all'avvio
coincida con quello che il documento dice. **Il banner è la verità, il timestamp del file
e le affermazioni nei documenti non lo sono.**

**Uno sweep di pesi completamente piatto.** Moltiplicando per mille il peso di un vincolo,
il fenomeno che doveva combattere passava da 1694 a 1679 occorrenze. La tentazione era
concludere "il peso è ancora basso". La diagnosi corretta era un'altra: il fenomeno
**nasce nella fase di costruzione**, e la ricerca locale, che è dove quel peso agisce, non
lo crea né ha modo di smontarlo. Regola derivata: uno sweep piatto non dice che il peso è
basso, dice che il peso è la leva sbagliata.

**Un test di equivalenza circolare.** Un test confrontava i dati derivati dall'input con
il golden per dimostrare che l'input bastava. Ma l'input era stato normalizzato **sul**
golden mesi prima: coincidevano per costruzione. Il test non dimostrava nulla, e ora lo
dice nel proprio commento.

**Citazioni verbatim normalizzate.** Nel lavoro di tracciabilità che collega ogni vincolo
alla riga del documento della scuola che lo giustifica, le citazioni erano state estratte
attraverso una mappa di trascrizione che "puliva" accenti e spazi doppi. Tre citazioni
sbagliate hanno superato una revisione e sono state prese solo da un verificatore che ha
confrontato i byte con un dump esadecimale.

E poi gli infortuni operativi puri, che costano ore e non hanno nulla di intellettuale:
un `git add` con più percorsi che fallisce interamente se uno solo non combacia, con
l'errore silenziato; uno `git stash` con un flag in coda che non crea nulla e il `pop`
successivo che pesca lo stash di un altro branch; un `rm -rf` su una cartella con un
typo nel nome, eseguito senza guardarci dentro, che ha portato via tre file non tracciati.

---

## Dove sta la verità, oggi

Chi arriva adesso e deve orientarsi:

| Cosa | Dove |
|---|---|
| Regole operative e tabelle dei vincoli (autoritative) | `CLAUDE.md` |
| Indice di navigazione del codice | `PROJECT_INDEX.md` |
| Storia dettagliata delle ricalibrazioni dei vincoli | `claudedocs/constraint-recalibration-history.md` |
| Il livello di orchestrazione, operatori e contratti | `claudedocs/orchestration-layer-architecture.md` |
| Lezioni operative, da leggere a inizio sessione | `tasks/lessons.md` |
| Architettura delle cinque fasi del solver | `claudedocs/solver-phases.md` |
| Campagna 2026/27, evidenze per attività | `claudedocs/spalmone-2026-27/` |
| Cosa faremmo diversamente | [RIAPPROCCIO.md](RIAPPROCCIO.md) |

Una nota finale sull'orizzonte. Il golden è un vantaggio della fase di test e **sparirà**:
in esercizio la scuola consegnerà gli input, non una soluzione di esempio. Ogni
meccanismo che oggi legge il golden a tempo di esecuzione ha quindi una data di scadenza,
ed è censito come debito. La finestra per verificare quali fatti del golden sono
deducibili dagli input è aperta solo finché il golden esiste. È il lavoro che resta.
