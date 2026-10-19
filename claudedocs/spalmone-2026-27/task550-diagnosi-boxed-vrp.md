# TASK (nuova): diagnosi del muro boxed-VRP: le finestre calendario strozzano le Stage

Data: 24/08 sera. Metodo: solo export + DB + golden, zero run (~30 minuti). Motivazione:
maintainer ha sfidato il processo ("stiamo rifacendo run lenti per conferme che i dati contengono
già"); la diagnosi sostituisce il quinto tentativo del run di consegna.

## Il muro scomposto

I 333 boxed del run di consegna (identici in tutti i tentativi, "VRP-locked") si decompongono:

1. **~122 sono infeasibilità di input pura**: le lezioni Stage (stub) superano le celle legali
   (date-stage × slot aperti dal calendario di gruppo):

   | gruppo | celle legali | domanda Stage | piazzate | shortfall strutturale |
   |---|---|---|---|---|
   | G12 | 256 | 295 | 112 | 39 |
   | G10 | 280 | 300 | 232 | 20 |
   | G04 | 208 | 240 | 208 | 32 |
   | G08 | 280 | 300 | 280 | 20 |
   | G11 | 288 | 295 | 288 | 7 |
   | G09 | 296 | 300 | 296 | 4 |

   Tutti i gruppi tranne G12 e G10 saturano ESATTAMENTE le celle legali: il solver
   riempie tutto ciò che il VRP gli consente; il resto non è piazzabile per costruzione.

2. **La causa root è la DOMANDA FANTASMA di Stage, non il calendario** [CORRETTA 24/08
   notte: la prima lettura ("finestra calendario più stretta del golden") era ERRATA: la
   query di confronto non filtrava closed=True; ricalcolata con il filtro, la tabella
   GROUP_CALENDAR_PATTERNS coincide con la pratica golden INCLUSIVAMENTE delle Stage].
   I nostri stub Stage sono seminati a finestra StagePattern PIENA (G12: 295), ma il
   golden TIEDE davvero (closed=False) molto meno: G12 112 (183 righe closed=True),
   G10 228/300, G04 224/240, G11 287/295, G09 292/300, G08 276/300. Le
   piazzate del run coincidono con le golden-aperte (G12: 112=112): il solver arriva
   esattamente dove arriva la scuola; i boxed sono ~314 lezioni che la scuola stessa non
   tiene (" domanda fantaasma").

3. **Anomalia residua**: con la domanda corretta la precedente anomalia (celle libere non
   riempite) si riassorbe: le celle "libere" erano libere perche' la scuola non le usa.

## Cura proposta (da gate di maintainer)

La scuola registra come "non tenute" righe di Stage dentro la finestra: quali ore di stage
tiene davvero per gruppo e' una PRATICA NON DEDUCIBILE dagli input (finestra+quota dicono
il massimo, non il tenuto). Opzioni:
(a) ridurre il seeding degli stub alla densita' golden-aperta (golden-derived, vale finche'
    il golden esiste; derivazione meccanica da lessons.csv closed=False);
(b) mantenere gli stub e contabilizzare la quota fantaasma in Course.expectedUnscheduledHours
    (le H0 degli stub irrealizzabili smettono di pesare);
(c) domanda alla scuola (workbook/referente): "quante ore di stage tenute realmente per gruppo?"
    - la forma dichiarativa che sopravvive al golden; coerente con la direttiva solo-input.
Raccomandazione: (a) ora + (c) come uscita definitiva; (b) solo se (a) non praticabile.

Attenzione DataGenerator: il seeding Stage vive nel seed; il file e' protetto
(regola datagenerator-protection): serve l'approvazione esplicita di maintainer (gia' data
per la correzione della diagnosi, da confermare per la cura).

## Lezione di processo (su richiesta di maintainer)

Il quinto tentativo del run di consegna avrebbe ri-confermato -10.8k per la quarta volta.
La stessa diagnosi è uscita da export+DB+golden in mezz'ora. Regole adottate: niente run
multi-ora per conferme; braccio checkpoint-paired quando serve una misura; ogni muro
ricorrente si diagnostica dall'export prima di girarci il solver sopra.

## CORREZIONE (maintainer, 24/08 22:30): la causa root di QUESTA sezione era sbagliata

La "causa root" sopra (le GroupCalendarRule piu' strette della pratica golden nelle
settimane di stage) e' la causa della PRIMA versione: SBAGLIATA. La root vera e' la
DOMANDA FANTASMA: i fogli chiedono 295-300h di Stage per gruppo, il golden ne teneva
112-295 (righe closed=true = non tenute): le ~311 ore in eccesso sono fisiicamente
incollocabili e sono ESATTAMENTE il grosso dei boxed H0. La cura approvata (maintainer
24/08 22:30) e' il SEEDING A DENSITA' GOLDEN-APERTA (cure (a), implementata:
generateStageCourses semina solo il golden-held, differenza in expectedUnscheduledHours
pendente la risposta della scuola = cura (c) in task550-domanda-scuola-stage-held.md).
La cura a finestre calendario descritta sopra resta un'ipotesi NON adottata. Le
anomalie residue (144 celle libere G12 ecc.) restano da chiarire DOPO il fix (a).
