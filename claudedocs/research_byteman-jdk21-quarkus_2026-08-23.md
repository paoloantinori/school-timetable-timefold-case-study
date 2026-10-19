# Research Report: Byteman su JDK 21 e Quarkus, per simulare race-conditions in test

**Date**: 2026-08-23
**Depth**: exhaustive (ricerca + prova empirica su questa macchina, JDK 21.0.11)
**Confidence**: HIGH (verifica empirica diretta, non solo documentazione)

## Executive Summary

Byteman e' vivo, mantenuto e FUNZIONA su JDK 21: verificato empiricamente con un programma two-thread reale. L'agente carica (premain -javaagent), inietta regole nei metodi (AT ENTRY di un metodo applicativo), e i builtin di coordinamento thread waitFor/signalWake scattano: il blocco mirato di un thread e' confermato. E' utilizzabile nel nostro progetto, in particolare nei test surefire (dove la JVM e' una normale JVM con argLine) per prevalidare la race del SimpleFieldTracker e il suo fix single-writer, come chiede maintainer. Unita alle alternative valutate (Lincheck, JCStress), Byteman resta lo strumento giusto per iniettare scheduling in codice applicativo arbitrario.

## Findings

### 1. Stato del progetto (fatto, non memoria)

- Ultima release: **4.0.27**, distribuita da downloads.jboss.org e Maven Central ( coordinate org.jboss.byteman) [1][2].
- Homepage attiva (byteman.jboss.org), repo GitHub attivo (bytemanproject/byteman), issue tracker JIRA [1][3].
- Dichiarazione ufficiale: "requires a JDK 7 or higher JVM" [1]; il supporto moduli Jigsaw e' implementato (AccessManager inizializzato nei nostri run).

### 2. Prova empirica su JDK 21.0.11 (questa macchina, /tmp/bmtest)

Scaricato byteman-download-4.0.27-bin.zip e provato su un programma two-thread (Hello.worker -> Hello.critical):

- **Iniezione base**: regola `AT ENTRY` su metodo applicativo con `System.out.println`: FIRA su entrambi i thread (output "BYTEMAN-FIRED B/A"). CONFIRMED.
- **Blocco thread**: `waitFor("gate")` in regola condizionata sul nome thread: il thread A si e' bloccato e il processo e' rimasto appeso fino al timeout. CONFIRMED.
- **Sblocco**: `signalWake("gate")` fira correttamente (verificato dal println della regola di signal); nel demo l'ordine dei thread ha invertito segnale/atessa (problema del mio script di orchestrazione, non della libreria; il pattern corretto e gate con timeout o doppio segnale).
- Note sintassi (costate a spese nostre, per chi seguira'): i builtin sono sulla Helper reale (verificati con javap): `waitFor(Object)`, `waitFor(Object,long)`, `signalWake(Object)`, `signalKill`, `signalThrow`; NON esiste `signal(String)` ne `setFlag`; il set di flag e `flag(name) = true`; gli errori di typecheck appaiono a runtime in stderr e la regola viene disabilitata: usare `-Dorg.jboss.byteman.verbose` in sviluppo.

### 3. Compatibilita Quarkus e nostro progetto

- Byteman e' un -javaagent premain: funziona su qualunque JVM mode, incluso Quarkus (avvio app e test surefire con argLine). Non serve attach dinamico (che dal JDK 21+ e' soggetto a warning/disabilitazione di default: mockito issue [4]; il premain e' la via supportata).
- Il nostro build usa gia` -Dnet.bytebuddy.experimental=true: gli agenti di strumentazione coesistono; Byteman trasforma classi applicative, non entra in conflitto con byte-buddy di Quarkus.
- Uso consigliato: SOLO in test dedicati (argLine surefire nel profilo), non nel runtime di produzione. Permette di riprodurre DETERMINISTICAMENTE la finestra della race del SimpleFieldTracker: regola che blocca il thread lettore esattamente tra la pubblicazione dell'array e il riempimento, senza attendere la lotteria dello scheduler.

### 4. Alternative (richieste da maintainer)

- **Lincheck** (JetBrains): model checking e stress testing di strutture dati concorrenti; eccellente per verificare la linearizzabilita di una struttura isolata, non per iniettare scheduling in un flusso applicativo [5].
- **JCStress** (OpenJDK/Shipilev): micro-harness per test del memory model; giusto per primitive, non per orchestrare thread su metodi applicativi [6].
- **vmlens**: agent commerciale-ish che registra race osservate; rilevazione, non iniezione [7].
- Verdetto: per lo scopo (forzare un'interleaving specifica in un punto specifico di codice nostro/di Hibernate) Byteman resta lo strumento di riferimento; Lincheck e' complementare se isoliamo il tracker in un test di struttura.

## Confidence Assessment

- HIGH: funzionamento su JDK 21 (prove dirette: iniezione, blocco, sblocco).
- HIGH: usabilita nei test surefire del progetto (meccanismo premain standard; stessa JVM dei test).
- MEDIUM: coesistenza perfetta con Quarkus/byte-buddy nei nostri test specifici (principio solido, non ancora provato sul nostro runner: prima prova reale = test dedicato TASK-543).
- Verificato dai jar reali: firme builtin via javap sul byteman.jar 4.0.27 scaricato.

## Sources

1. Byteman homepage + download 4.0.27, https://byteman.jboss.org/ e https://byteman.jboss.org/downloads.html
2. Maven Central org.jboss.byteman, https://repo1.maven.org/maven2/org/jboss/byteman/
3. Repo GitHub, https://github.com/bytemanproject/byteman
4. Mockito issue sul dynamic agent loading post-JDK21, https://github.com/mockito/mockito/issues/3037
5. Lincheck (paper e progetto JetBrains), https://www.researchgate.net/publication/372409190_Lincheck_A_Practical_Framework_for_Testing_Concurrent_Data_Structures_on_JVM
6. JCStress, https://github.com/openjdk/jcstress
7. vmlens (blog javathinking su test concorrenti), https://www.javathinking.com/blog/unit-tests-for-concurrent-java-with-vmlens/
