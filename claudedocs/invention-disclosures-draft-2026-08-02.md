# Bozze di invention disclosure (v0, 2026-08-02)

> Bozze per iterazione di maintainer. Repo verificato PRIVATO in data odierna: nessuna divulgazione
> pubblica avvenuta. Se il repo diventasse pubblico prima di un eventuale deposito, questo file
> e i doc di architettura collegati diventerebbero prior art contro di noi (in EU la novita' e'
> assoluta, senza grace period). Evidenza sperimentale citata: capture e verdetti in
> claudedocs/spalmone-2026-27/.

---

## Invenzione A: operatori a confine di fase con contratto di miglioramento verificato,
## ripristino automatico e gating a livelli di vincolo

**Titolo di lavoro**: "Metodo e sistema per l'esecuzione affidabile di operatori deterministici
in una pipeline di risoluzione vincoli multi-stadio, con contratto improve-or-noop verificato,
ripristino transazionale e metrica di accettazione a livelli".

**Problema tecnico.** Nelle pipeline ibride per problemi di pianificazione fortemente vincolati
(metaeuristica + passi di riparazione deterministici), tre guasti ricorrenti: (a) un passo di
riparazione puo' corrompere silenziosamente la soluzione quando il suo modello interno dei
conflitti e' incompleto (il contatore interno dice "0 conflitti aggiunti" mentre lo score reale
peggiora); (b) la difesa ingenua (abortire il ciclo alla violazione) butta l'intero run, ore di
computazione; (c) lo score hard aggregato non sa guidare le transizioni di stadio quando mescola
famiglie di vincoli risolvibili in stadi diversi (es. conflitti di orario vs assegnazione stanze:
a meta' pipeline le stanze mancano per costruzione e l'hard aggregato e' privo di significato).

**Stato dell'arte e limite.** Gli anytime algorithms garantiscono monotonicita' del risultato ma
non verificano contratti per-operatore ne' ripristinano stati; il solution-based phase saving
(CP) conserva soluzioni tra restart ma non e' un contratto di pass; le hyper-heuristics
orchestrano la SCELTA delle euristiche, non ne garantiscono l'effetto; fix-and-optimize non
definisce garanzie transazionali. Nessuno combina verifica indipendente del contratto, revert
transazionale con prosecuzione, e metrica scoped per stadio.

**Meccanismo (gist).**
1. Ogni operatore deterministico eseguito a confine di fase dichiara un contratto
   improve-or-noop rispetto a una METRICA SCOPED: il sottoinsieme delle famiglie di vincoli hard
   pertinenti allo stadio corrente (es. "timeslot-tier" = tutte le hard tranne le famiglie
   room-tier, elencate esplicitamente).
2. Prima dell'esecuzione, snapshot minimale dello stato mutabile (mappa entita' -> (slot,
   stanza)): sufficiente per il ripristino totale perche' gli operatori mutano solo quelle
   variabili (proprieta' verificata del sistema).
3. Dopo l'esecuzione, un guard INDIPENDENTE ricalcola la metrica scoped dallo stato reale (non
   si fida dei contatori interni dell'operatore) con doppio trigger: (i) metrica peggiorata,
   (ii) conflitti strutturali nuovi (double-booking) anche se compensati a saldo zero.
4. Su violazione: RIPRISTINO dello snapshot e PROSECUZIONE della pipeline (revert-and-continue),
   con logging della famiglia violata; mai abort del ciclo.
5. La stessa metrica a livelli governa le transizioni di stadio (gate: lo stadio N e' completo
   quando la sua metrica scoped raggiunge la soglia, ignorando le famiglie degli stadi futuri).

**Effetto tecnico misurato.** (a) Eliminazione degli abort di ciclo: un run che ai primi di
agosto 2026 abortiva interamente per una violazione del contratto (contatore interno cieco a una
famiglia) completa dopo la cura, e il revert e' scattato dal vivo su una famiglia DIVERSA da
quella prevista (H28), dimostrando il valore del guard family-agnostic; (b) robustezza a
operatori con modelli interni incompleti senza doverli rendere onniscienti; (c) garanzia
formale per-pass piu' forte della monotonicita' anytime (verificata, non assunta). Evidenza:
task452-results/ (verdetti run3/run4), diagnosi TASK-473, cronologia commit.

**Perimetro/variante.** Applicabile a qualunque solver a pipeline con operatori deterministici
interleaved a fasi stocastiche; la metrica scoped generalizza a N livelli (non solo 2).

---

## Invenzione B: pinning a stadi con promozione e demozione selettiva del template

**Titolo di lavoro**: "Metodo per la preservazione di struttura regolare in ricerca locale su
problemi di scheduling, mediante promozione a variabili fisse delle assegnazioni conformi a un
template e demozione selettiva guidata da deficit".

**Problema tecnico.** Su orari scolastici/industriali la qualita' percepita richiede REGOLARITA'
settimanale (stessa materia, stessa cella, settimane successive). La local search, ottimizzando
score aggregati, erode sistematicamente questa struttura (misurato: ricorrenza ~52,8% senza
protezione). Vincolare rigidamente il template rende il problema infeasible; lasciarlo libero
distrugge la regolarita'. I vincoli soft/medium di "stabilita'" pagano un costo di ricerca
continuo e proteggono poco.

**Stato dell'arte e limite.** Pinning statico (entita' bloccate a priori dall'utente); LNS con
fissaggio casuale di sottoinsiemi; POPMUSIC con parti fisse per sottoproblema. Nessuno usa la
promozione DINAMICA e REVOCABILE di un template costruito, con demozione selettiva a criteri
dichiarati e release controllato tra stadi.

**Meccanismo (gist).**
1. La fase di costruzione produce un template settimanale per gruppo e lo stampa sulle date; le
   assegnazioni conformi al template vengono PROMOSSE a pinned (immutabili) per lo stadio di
   ricerca successivo: la LS ottimizza solo il resto, e la struttura non puo' essere erosa.
2. Durante lo stadio, DEMOZIONI SELETTIVE e limitate (numero massimo di iterazioni, criteri
   espliciti: es. entita' delle celle coinvolte in deficit hard irrisolvibili altrimenti)
   riaprono il minimo necessario: il pattern si esaurisce al 100% prima di ammettere eccezioni.
3. A fine stadio, RELEASE controllato: i pin promossi tornano liberi per gli stadi successivi
   (riparazioni, assegnazione stanze), che operano con i propri contratti (Invenzione A).
4. Contabilita' dei pin a due classi (autoritativi dell'utente vs promossi dal sistema) che
   alimenta i TARGET dei vincoli di riempimento (quota netta dei pin autoritativi), evitando
   doppi conteggi.

**Effetto tecnico misurato.** Ricorrenza settimanale delle lezioni non pinnate da ~52,8% a
~74-76% con erosione del template ~0, mantenendo hard 0 e tempi di solve invariati (campagna
luglio 2026, verdetti in claudedocs/spalmone-2026-27/, ratifiche TASK-419/424 in CLAUDE.md).

**Perimetro/variante.** Ogni dominio con "pattern regolare desiderato + eccezioni inevitabili"
(turni ospedalieri, produzione ciclica); il criterio di demozione e' pluggabile.

---

## Nota di combinazione e prossimi passi
Le due invenzioni sono indipendenti ma sinergiche (B produce gli stadi su cui A applica i
contratti); depositabili come famiglie separate o come sistema unico con rivendicazioni
dipendenti. Prossimi passi se si procede: (1) ricerca prior art BREVETTUALE professionale
(Espacenet/USPTO: l'assenza dalla letteratura accademica non basta); (2) mantenere il repo
privato fino alla decisione; (3) mandatario per la forma delle rivendicazioni (inquadrare come
miglioramento tecnico del sistema di risoluzione, non come metodo di scheduling).
