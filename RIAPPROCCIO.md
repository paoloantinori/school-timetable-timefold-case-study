# Come ri-approccerei un progetto simile, sapendo quello che so oggi

Documento gemello di [STORIA.md](STORIA.md), che racconta com'è andata davvero. Questo
invece è prescrittivo: se domani dovessi ricominciare da zero un sistema che costruisce
l'orario annuale di una scuola (o, più in generale, che automatizza una pianificazione
che oggi qualcuno fa a mano con una sequenza di passi collaudati), farei così.

Vale per chiunque lavori con un risolutore a vincoli su un problema reale, ma soprattutto
per il caso specifico e frequente in cui **esiste già una soluzione umana valida** da cui
imparare.

---

## Parte 1. Le sette cose che rifarei identiche

**1. Trattare la sequenza del pianificatore umano come un algoritmo, non come folklore.**
Quando la scuola dice "prima sistemo le rotazioni di acconciatura, poi i laboratori, poi
le professionalizzanti", non sta descrivendo un'abitudine: sta descrivendo una
decomposizione del problema che funziona, trovata in anni di tentativi. Codificarla come
fase di pre-assegnazione che fissa le scelte facili prima che il solver parta è stata la
decisione con il miglior rapporto fra valore e costo di tutto il progetto. Il solver
lavora su ciò che resta, che è enormemente più piccolo.

**2. Costruire, non solo cercare.** Il momento in cui il progetto ha svoltato è quando
una struttura (il template settimanale per gruppo) ha smesso di essere qualcosa che si
sperava emergesse dalla ricerca locale ed è diventata qualcosa che si costruisce prima e
si stampa. Regola pratica: **se un gruppo ha capacità uguale alla domanda, la ricerca non
ce la farà mai**, perché serve un abbinamento perfetto e la ricerca locale si ferma a uno
scarto. Un caso reale nel progetto: 990 ore da collocare in 990 slot disponibili, la
ricerca locale si fermava a 989. Quello non è un problema di ricerca, è un problema di
costruzione.

**3. Il criterio unico per restringere lo spazio di ricerca.** Solo un fatto di
qualità-input (un calendario dichiarato dalla scuola, una quota giornaliera che la scuola
stessa ha scritto) può eliminare valori dal dominio. Un desiderio, per quanto ragionevole,
non può. Ogni volta che abbiamo ristretto il dominio partendo da un desiderio abbiamo
rotto la fattibilità; ogni volta che l'abbiamo fatto partendo da un input autoritativo
abbiamo guadagnato. Il test in una riga: **"l'orario che la scuola usa davvero violerebbe
questa regola?"**. Se sì, non è materiale per restringere il dominio.

**4. Operatori con un contratto, non passaggi opportunistici.** Ogni riparazione
deterministica scritta dopo la ricerca dichiara la propria invariante d'ingresso, la
garanzia d'uscita, e soprattutto ha **migliora-o-non-fare-nulla** con ripristino. Senza
quella clausola, un operatore che sembra chiudere un buco lo sta quasi sempre spostando
altrove, e te ne accorgi tre giorni dopo. Con quella clausola, il peggio che può fare è
niente.

**5. Il completamento esatto sulla coda.** Quando la ricerca stocastica lascia un residuo
piccolo (poche ore non collocate), smettere di insistere con la ricerca ed enumerare in
modo esatto lo spazio rimasto. È un pattern noto in letteratura (*fix-and-optimize*) e ha
convertito la chiusura della coda da probabilistica a deterministica. La regola
generalizzabile: **stocastico per il grosso, esatto per il residuo**, e il criterio per
passare dall'uno all'altro è la dimensione dimostrata del residuo.

**6. Una batteria di indicatori canonica, prima delle ottimizzazioni.** Un solo script di
cattura che, dopo ogni run, produce sempre gli stessi indicatori (tasso di giornate
piene, ancoraggio della prima ora, ricorrenza settimanale, cadenza, topologia dei buchi).
Il valore non è nei singoli numeri, è che sono **gli stessi** ogni volta: senza questo,
ogni confronto diventa una misura ad hoc e le misure ad hoc non si confrontano fra loro.

**7. Un registro delle lezioni, aggiornato quando l'errore è ancora caldo.** Il file
`tasks/lessons.md` è la cosa più preziosa del repository dopo il codice. Non contiene
teoria: contiene errori con la data, il sintomo e la regola derivata. Metà delle voci sono
state scritte perché lo stesso errore era stato fatto due volte nella stessa nottata.

---

## Parte 2. Le sei cose che cambierei

### 2.1. Chiederei la soluzione di riferimento il primo giorno, e la userei come oracolo

Il golden è arrivato a metà progetto e ha invalidato un pezzo del lavoro precedente. Non
per colpa di nessuno, semplicemente non era stato chiesto prima.

Quello che oggi so è che **una soluzione umana valida non serve solo come metro di
paragone finale: serve come oracolo sui dati durante tutto lo sviluppo**. Un esempio
concreto che è costato un'ora di archeologia sui fogli: "questo gruppo deve fare 835 ore
o 750?". La risposta non si trova ricostruendo da quale foglio venga l'835, si trova
contando le ore che la soluzione umana colloca per quel gruppo (750). Ricostruire la
provenienza di un numero serve solo dopo, e solo se l'input e la soluzione si
contraddicono.

Corollario che vale in generale: quando un numero non torna, la prima ipotesi non è "il
dato è sbagliato", è **"non ho capito la trasformazione che l'ha prodotto"**.

### 2.2. Non promuoverei nulla a vincolo rigido prima di aver misurato il residuo umano

Questo è l'errore concettuale più costoso della prima metà del progetto, e ha una cura
meccanica che oggi applicherei dal primo giorno.

**Per ogni regola candidata, prima di scriverla, misuro quante volte la soluzione umana la
viola.** Poi classifico:

| Violazioni nella soluzione umana | Cosa significa | Come la modello |
|---|---|---|
| Zero | requisito vero | vincolo rigido, e posso anche usarlo per restringere il dominio |
| Poche, concentrate | probabilmente un requisito che il pianificatore non è riuscito a rispettare | vincolo rigido con esenzione esplicita e documentata, oppure preferenza a peso molto alto |
| Molte, sistematiche | non è un requisito, è una preferenza (o l'ho capito male) | preferenza, e vado a chiedere cosa intendevano davvero |

Il motivo per cui non basta "alzare il peso finché non viene rispettato" è che **un
vincolo rigido troppo severo non viene rispettato, viene aggirato**. Nel progetto, il
vincolo che imponeva di non cambiare aula a metà blocco ha prodotto 291 ore lasciate
senza alcuna aula: la ricerca aveva trovato che la violazione silenziosa costava meno
della violazione dichiarata. Declassato, quelle 291 sono diventate 3.

Terza regola collegata: **se una regola risulta violata dalla pratica, produci il report
delle occorrenze con una spiegazione per ciascuna**, prima di decidere. Spesso le
occorrenze si spiegano da sole e rivelano un vincolo che non conoscevi.

### 2.3. Separerei fin dal primo giorno "leggere un input" da "leggere una soluzione"

Questo è il debito tecnico più insidioso accumulato, ed è invisibile finché non è tardi.

Avere la soluzione umana a disposizione durante lo sviluppo rende naturale prendere
scorciatoie: derivare da lì un'ancora, una profondità di co-presenza, una cadenza attesa.
Ogni scorciatoia funziona benissimo e finisce sul percorso di produzione senza che nessuno
se ne accorga. Ma **in esercizio quella soluzione non ci sarà**: il cliente consegna gli
input, non un esempio svolto.

Quello che farei diversamente, da subito: due sorgenti separate e dichiarate a livello di
architettura, con l'accesso alla soluzione di riferimento confinato in un componente
identificabile e un test che fallisce se il percorso di produzione lo tocca. Per ogni
fatto che oggi prendo dalla soluzione, esistono solo due esiti onesti: **è deducibile
dagli input** (allora cambio sorgente e verifico che il risultato coincida, cosa
possibile solo finché ho ancora la soluzione a disposizione), oppure **è una pratica non
deducibile** (allora nessuna statistica la inventa: va chiesta e deve diventare un input
dichiarato).

Quella verifica è una finestra che si chiude. Va usata mentre è aperta.

### 2.4. Metterei un cancello di indipendenza davanti a qualunque strategia di diversificazione

Il primo tentativo di portfolio (lanciare più candidati e tenere il migliore) è stato
confutato: i candidati erano diversi solo in apparenza, condividevano la stessa
costruzione e le stesse 7.504 ore fissate. Non stavamo esplorando bacini diversi,
stavamo rifacendo lo stesso identico solve con rumore diverso.

Oggi, prima di spendere un minuto di calcolo su qualunque strategia multi-candidato,
misuro una cosa sola: **i candidati differiscono davvero sulla dimensione che conta?**
Nel nostro caso la dimensione era il profilo di saturazione dello slot critico, e la
misura è costata minuti, non ore. Se i profili coincidono, la strategia è archiviata
senza aver bruciato nulla.

Vale in generale: **una strategia di diversificazione ha un prerequisito misurabile, e
misurarlo costa sempre meno che scoprire a posteriori che non era soddisfatto.**

### 2.5. Diagnosticherei la fase prima di toccare i pesi

Ho visto due volte lo stesso schema: un fenomeno indesiderato, un peso che dovrebbe
combatterlo, uno sweep del peso, un risultato **piatto**. Moltiplicare per mille il peso
cambiava il fenomeno dell'uno per cento.

La lettura sbagliata è "il peso è ancora troppo basso". La lettura giusta è: **il peso è
la leva sbagliata**, perché il fenomeno nasce in una fase in cui quel peso non ha
gradiente. Nel caso concreto i blocchi indesiderati nascevano nella costruzione, e la
ricerca locale (dove il peso agisce) li ereditava senza crearli né avere modo di
smontarli.

Quindi, prima di qualunque taratura: **in quale fase nasce il fenomeno?** Misurarlo è
facile se, come dovresti, hai un dump dello stato a fine costruzione da confrontare con
quello finale. Se i due numeri coincidono, la ricerca locale non c'entra e stai per
sprecare una giornata di sweep.

### 2.6. Non duplicherei mai un fatto in due documenti

La regola l'abbiamo scritta dopo tre incidenti reali: un file di regole che duplicava e
contraddiceva le istruzioni di build al punto di ordinare un comando che cancellava i
dati; una descrizione dei dati che ripeteva un valore e ha continuato a ripeterlo dopo la
correzione; un indice che duplicava le tabelle dei vincoli finché non hanno divergito.

**Un fatto duplicato non resta sincronizzato: diverge, e la copia stantia viene letta
come verità.** Nella documentazione va il puntatore a dove vive il fatto e perché quel
posto è autoritativo, mai la copia. Lo stesso vale nel codice: se due componenti devono
concordare su "cosa conta come materia teorica", devono condividere **un** predicato, non
ridefinirlo ciascuno. Due definizioni coincidono sui dati di oggi e divergono in silenzio
su quelli di domani.

---

## Parte 3. L'ordine in cui lavorerei

Non è un piano di progetto, è una successione di cancelli. Ogni fase produce qualcosa che
rende falsificabile la fase successiva.

**Fase 0, prima di scrivere codice.** Raccogliere gli input autoritativi (calendari,
incarichi, disponibilità, quote) **e** la soluzione umana dell'anno scorso. Se la
soluzione umana non esiste, saperlo subito cambia tutto il resto del piano, perché
significa che ogni pratica non scritta andrà chiesta esplicitamente.

**Fase 1, la contabilità prima del modello.** Per ogni gruppo: ore da collocare contro
slot disponibili nel calendario. Questo conto, che si fa in un pomeriggio con un foglio
di calcolo, dice in anticipo quali gruppi hanno margine zero e quindi richiedono
costruzione invece di ricerca. Nel nostro caso avrebbe risparmiato tre ore di ricerca
locale su un nodo che nessuna ricerca poteva sciogliere.

**Fase 2, il censimento delle regole contro la pratica.** Ogni regola candidata,
raccolta dalle interviste e dai documenti, viene misurata contro la soluzione umana con
la tabella della sezione 2.2. Il risultato è la classificazione rigido/preferenza, e
soprattutto una lista di domande vere da fare al cliente, che sono molte meno di quelle
che credevi (nel nostro caso, tredici domande sono diventate tre dopo l'autoverifica sui
dati).

**Fase 3, la spina dorsale end-to-end sul caso più difficile.** Il gruppo con margine
zero, non quello facile. Costruzione, ricerca, riparazione, misura. Serve a scoprire
subito le sorprese dell'infrastruttura, che nel nostro caso includevano: la memoria della
macchina virtuale sottodimensionata che rendeva il garbage collector il vero collo di
bottiglia (183 millisecondi di pausa media, il solver strozzato mentre credevamo di
misurare la metaeuristica), e il fatto che il monitoraggio stesso, se troppo invasivo,
faceva morire il solver.

**Fase 4, la batteria di indicatori.** Prima delle ottimizzazioni, non dopo. Altrimenti
ogni miglioramento sarà rivendicato con una misura diversa.

**Fase 5, l'ottimizzazione vera**, con il protocollo di evidenza della parte 4.

**Fase 6, il taglio del cordone.** Sostituire ogni lettura della soluzione di riferimento
con la lettura dell'input equivalente, verificando che il risultato coincida **mentre la
soluzione di riferimento è ancora disponibile**. Questa fase non è rimandabile a dopo la
consegna: dopo la consegna il termine di paragone non c'è più.

---

## Parte 4. Il metodo di lavoro

### 4.1. Il protocollo di evidenza

Cinque regole che hanno retto per tutta la campagna:

1. **Mai dichiarare qualcosa "fatto" o "risolto" senza aver eseguito in quel momento il
   comando che lo prova e averne letto l'uscita.** Non il riassunto, l'uscita.
2. **Prima di un run misurato, ricompilare e verificare che il banner dei flag attivi
   stampato all'avvio coincida con quello che credi.** Un run misura il codice che c'è nel
   pacchetto, non quello che c'è nella tua testa. Ci siamo cascati con un pacchetto vecchio
   di un'ora e venti.
3. **Due misure si confermano a vicenda solo se sono lo stesso censimento.** Due numeri
   vicini calcolati con perimetri diversi non sono una conferma, sono una coincidenza.
4. **La stessa violazione residua su run indipendenti è struttura, non rumore.** È il
   segnale che smetti di cercare una configurazione migliore e vai a guardare i dati.
5. **Le adozioni chiedono almeno due run indipendenti concordi**, e il verdetto lo
   ricalcola chi decide, dai file grezzi, non chi ha eseguito.

E una regola sull'osservazione, che sembra banale finché non ti costa un run: **prima di
dichiarare in stallo un processo lungo, confronta l'istante corrente con la cronologia
per fase di un run di riferimento.** Nel nostro ciclo il punteggio rigido resta congelato
per sedici minuti e converge solo nell'ultima fase: chi guarda al minuto 26 di 49 vede
un piatto e conclude male.

### 4.2. Il doppio agente, con tmux su ssh

Lo schema che ha reso possibile lavorare a questo ritmo, e che rifarei senza cambiare
nulla nell'impianto.

**Due agenti, ruoli asimmetrici.** Uno di **controllo** che decide cosa misurare, scrive
i mandati, verifica i risultati sui dati grezzi, ratifica o boccia, e custodisce la
memoria del progetto. Uno di **esecuzione**, su una macchina dedicata che resta accesa,
che esegue i lavori lunghi (solve, benchmark, spike) e pubblica gli artefatti.

**Il canale è tmux su ssh.** La sessione dell'esecutore vive in una finestra tmux
persistente; l'agente di controllo la osserva con `capture-pane` e le consegna i mandati
scrivendoli in un file, copiandolo sulla macchina remota e incollandolo nel buffer tmux.
Solo comandi one-shot, nessuna connessione da tenere viva, nessun processo che muore se
cade la rete. **Il canale dei risultati però non è il terminale: è git.** L'esecutore
committa artefatti e verdetti, chi controlla fa pull e ricalcola.

Le regole che rendono il meccanismo affidabile, tutte nate da un guasto:

- **Chi esegue non adotta.** Nessun cambio di default, nessun revert, nessun invio verso
  l'esterno dalla macchina esecutrice. Decide chi ha il contesto completo.
- **Indirizzare le finestre per nome, mai per indice.** Gli indici slittano quando
  qualcuno riordina le finestre, e un mandato è finito nella sessione sbagliata.
- **La prova che un messaggio è stato ricevuto è che il contatore di contesto remoto
  cresce**, non che lo schermo sembri giusto. Altrimenti si spediscono duplicati mentre
  il primo è già in elaborazione.
- **Se l'esecutore fa una domanda, chi controlla risponde subito** a nome dell'umano. Se
  la decisione è davvero riservata all'umano, si risponde con la variante che non
  impegna (prototipo dietro flag spento, patch preparata e non applicata) e si segnala la
  decisione vera. Un esecutore in attesa è tempo di macchina buttato: una notte è quasi
  andata persa così.
- **Ogni mandato notturno ha due code**: quella approvata e una di esplorazione su ipotesi
  non ancora approvate. Se la prima finisce alle due di notte, la macchina passa alla
  seconda. Idle notturno o run di conferma ripetuti valgono come mandato fallito.
- **I compiti di sorveglianza ripetitivi vanno a un modello economico**, con l'obbligo di
  riportare le righe grezze catturate. Il modello costoso serve per le decisioni, le
  verifiche finali e la consegna dei mandati, non per il polling.

### 4.3. Il backlog come unità di lavoro

Ogni incremento passa dal proprio elemento di backlog, con criteri di accettazione
scritti prima e una definizione di fatto che include: revisione di semplificazione,
revisione del codice con verificatori indipendenti, e la verifica finale della suite di
test **letta dal codice di uscita**, mai da una coda di output filtrata.

Il punto meno ovvio, imparato sul campo: **il ciclo di revisione di chi orchestra trova
bug reali anche dopo che l'agente implementatore ha già fatto la propria revisione.** Ha
funzionato ogni singola volta. Non si salta perché "ha già revisionato lui": un
verificatore a contesto fresco batte l'autocritica, e l'indipendenza è una questione di
contesto, non di modello.

---

## Parte 5. Trappole specifiche di questo dominio

Se il tuo problema è un orario scolastico, queste te le risparmio.

**I dati di riferimento contengono segnaposto.** Nel nostro golden ci sono 117 ore di
sabato, di cui 112 in un solo giorno con docente e materia entrambi chiamati "Fasullo":
un segnaposto dei pianificatori. Inseguirle produce un deficit fantasma. Ogni confronto
sul monte ore deve dichiarare cosa esclude, e un docente presente nella soluzione ma
assente dagli incarichi va scartato **e dichiarato**, mai assorbito in silenzio.

**Le fasce pomeridiane aperte non sono un invito.** Il calendario le lascia aperte per lo
stage, non per le lezioni ordinarie. Un modello che le vede come capacità libera le
riempie e nessuno se ne lamenta finché non lo vede un umano.

**La compattezza della giornata dello studente è un vincolo di prima grandezza.** Un buco
in mezzo alla giornata di una classe è inaccettabile per la scuola in modo molto più
netto di quanto un modellatore si aspetti, ed è anche il vincolo più difficile da
soddisfare quando una fascia oraria è satura su tutti i gruppi contemporaneamente.

**Il residuo rigido è binario.** Un orario con anche una sola violazione rigida non è
"quasi consegnabile", è **non consegnabile**. Questo cambia il modo di ragionare sulla
ricerca: non stai ottimizzando una quantità, stai colpendo un evento binario, e la
strategia giusta per un evento binario ad alta probabilità per tentativo è ripetere il
tentativo con condizioni iniziali davvero diverse, non insistere più a lungo sullo stesso.

**Attenzione ai vincoli che diventano ciechi.** Nel nostro framework, iterare sulle entità
"completamente inizializzate" salta quelle a cui manca ancora un pezzo di assegnazione:
tre vincoli sono rimasti **inerti** per intere fasi senza che nessuno se ne accorgesse,
con 6.343 lezioni impilate e un contatore di conflitti che diceva zero. Un vincolo che
non trova mai violazioni merita sempre un controllo: potrebbe essere soddisfatto, oppure
non stare guardando.

---

## In sintesi, se dovessi dire una cosa sola

Il valore di questo progetto non sta nella configurazione del solver. Sta in due cose:
la **decomposizione** (costruire ciò che si può costruire, cercare solo ciò che va
cercato, completare in modo esatto il residuo) e la **disciplina dell'evidenza** (ogni
affermazione portante ricalcolata dai dati grezzi da qualcuno che non l'ha prodotta).

La prima si può copiare. La seconda va praticata, e l'unico modo che ho trovato per
praticarla in modo affidabile è scrivere ogni errore, con la data, mentre brucia ancora.
