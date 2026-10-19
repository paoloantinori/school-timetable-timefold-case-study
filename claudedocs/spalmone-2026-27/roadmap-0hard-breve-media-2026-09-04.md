# Roadmap verso 0hard: breve e media distanza

Data: 2026-09-04. Compagno di `audit-ultimi-2-mesi-2026-09-04.md` (fatti e fonti). Qui il ragionamento strategico e le raccomandazioni. Principio guida ereditato dal piano del 28/08 (`piano-ritorno-0hard-2026-08-28.md`) e confermato dall'audit: **la chiave di volta non e' piu' la ricerca ne' il modello dei vincoli, e' la meccanica deterministica sulle celle residue, piu' la fiducia del dato persistito.**

Regola di lettura: 0hard e' **binario e bloccante** (direttiva maintainer 02/08). Un orario con hard residuo non e' "quasi pronto". Ogni via sotto e' valutata per: cosa sblocca, costo, rischio, e precondizioni.

---

## 0. Precondizione di fiducia (fare PRIMA di tutto)

**TASK-526, il bug score-vs-DB, e' la priorita' zero.** Un run puo' dichiarare 0hard in-memory mentre il DB persiste migliaia di lezioni senza stanza (H29). Finche' questo canale non e' chiuso, **ogni 0hard dichiarato e' una congettura**: va verificato sul DB (`room_id IS NULL` sulle lezioni collocate), non sullo score in-memory. Nessuna celebrazione di 0hard e' credibile finche' il gate di consegna non confronta score in-memory e riconteggio DB sul tier hard. Azione: chiudere TASK-526 e aggiungere un check automatico di concordanza hard score-vs-DB al confine di consegna del wrapper (stesso spirito del doctor TASK-579). Costo basso, sblocca la fiducia in tutto il resto.

---

## 1. Via breve (giorni): chiudere i tre buchi meccanici della linea SurplusHarvest

La famiglia hard dominante residua e' **H34 (5-6 celle strutturali, invarianti al seme, mag-giu)**. La cura (SurplusHarvest) esiste ed e' sana: il problema e' che **gira nella finestra sbagliata**. Tre fix meccanici, in ordine di dipendenza:

1. **Chiudere la questione "istanza orfana" (verifica, non implementazione).** Stabilire quale istanza conserva `AttemptOutcome.solution()`: se e' `finalSolution`, la catena post-B-ROOM (QFR-2, EDF, SurplusHarvest) e' orfana e le sue mosse non persistono; se e' `roomProblem` mutata dalla catena, il bug non esiste e resta da spiegare perche' le due passate v11 erano identiche. E' una lettura di `MultiStartRunner.runOneMultiStartAttempt` + grep dei save. **Mezza giornata, rischio zero, e decide dove deve girare la tail.** L'ipotesi orfana, se confermata, e' la spiegazione strutturale di perche' i finisher chiudono celle nei log ma il residuo H34 resta in consegna.
2. **Winner-tail nel wrapper multi-start.** Spostare (o aggiungere) l'occasione di SurplusHarvest sul winner dopo restore-best, prima della consegna definitiva. Le 10 passate `moves=0` del ciclo v11 dimostrano che al confine post-B-ROOM le celle non ci sono ancora; sul winner ci sono. Questo e' il fix che con la probabilita' piu' alta converte il pass da "sano ma inerte" a "convertitore di consegne". Dipende dal punto 1 (la finestra deve essere su un'istanza che persiste).
3. **Guardia per-cell sulla quinta via OOF `relieveOverloads`.** Stesso idioma delle guardie `isSlotLegalFresh` gia' migrate (TASK-578). Elimina i 2-3 assertValueRange non fatali per ciclo. Costo basso, igiene.

**Esito atteso del ramo breve:** se la finestra winner-tail trova le celle e l'oracolo improve-or-noop tiene, le consegne scendono dalla banda -13/-16 verso il basso. Non aspettarsi 0hard subito: restano i 3 one-off dichiarativi (punto 2) e i sanity rossi di coda-anno. Ma si misura se la cura H34 morde davvero.

---

## 2. Via breve-parallela (giorni): TASK-574, i 3 one-off fisici

Separato da SurplusHarvest perche' e' un residuo **dichiarativo** diverso (H7 overload + H29 roomless + H33 floor su 3 celle identificate a cella esatta). TASK-574 e' To Do alta priorita'; round8 ha mostrato uno shell-game perche' il OneOffFinisher v0 non ha guardie di contiguita'/quota sul donatore. Azione: redesign delle guardie (solver design), poi banco + UN ciclo di conferma. Target dichiarato: [0 puro / -7 dichiarative]. E' il complemento pulito di SurplusHarvest: uno chiude la coda strutturale H34, l'altro la coda fisica H7/H29/H33.

---

## 3. Via media (settimane): re-validare il claim 0hard sul vincolame corrente

Chiusi i buchi meccanici, serve rispondere alla domanda che l'audit ha aperto: **il multi-start consegna ancora 0hard statisticamente, col vincolame di oggi?**

- **Misura diretta:** ripetere il bad-seed frequency batch (come il pool N=32 del 9-10/08) ma sul vincolame corrente (H31+H32+H33+H34 DEFAULT ON, SurplusHarvest winner-tail armato). Se p(0-hard) per costruzione resta alta e N=3 copre con CI_lo > 0,95, il claim m-13 e' re-validato. Se no, si misura quanto e' calata e si decide il budget N.
- **Condizione:** questa misura ha senso solo DOPO i fix del punto 1, altrimenti misura il pass nella finestra sbagliata.
- **Cura input pendente (non bloccante):** la cella G09 04/06 (piano 28/08 FASE 1): assumere il golden (662h), correggere il foglio, notifica referente a posteriori. Chiude G1 (golden a 0hard sotto il vincolame corrente) e pulisce il pin del GoldenSolutionRegressionTest.

---

## 4. Via media-lunga (il reticolato di fondo): la questione golden

Sotto la linea calda c'e' la transizione strategica gia' decisa (direttiva solo-input, 30/07): il golden sparisce e ogni meccanismo che lo legge a runtime ha una data di scadenza. L'audit conferma che questo e' il vero debito di medio termine, piu' del singolo hard residuo:

- **TASK-452 (aperto, alta):** tre letture golden-runtime sul percorso di produzione (`ConstrainedTeacherAllocator`, `MonoRoomTagAllocator`, weekday-parity del pre-booking) da sostituire con input dichiarato nel workbook (TASK-445). E' la via per non dipendere piu' da una soluzione di esempio che in esercizio la scuola non passera'.
- **TASK-441 (aperto, alta):** la policy anno-senza-golden (come derivare M21, T20, e i riferimenti annuali in modo riproducibile).
- Questi non muovono lo score di domani, ma determinano se lo 0hard di quest'anno e' replicabile l'anno prossimo senza il golden. Sono il contenuto reale di m-13 oltre il claim statistico.

---

## 5. Vie da NON riaprire (perimetro chiuso con evidenza)

Per evitare di spendere compute su strade gia' misurate come vicoli ciechi:

- **Metaeuristiche** (tabu, LA, VND, ACL sweep): famiglia chiusa definitivamente (TASK-569). Il collo non e' la ricerca.
- **Quasi-tier / BendableScore** (TASK-538): chiuso negativo. Il landscape H32/H34 e' monotono per-ora; il collo e' il campionamento, non la gerarchia. Non aggiungere un livello di score.
- **Composition hole-closer / Kempe-chain** (TASK-481/482): shell-game dimostrato. La chiusura locale di H17 non converge.
- **Cambiare il metro per far tornare i numeri**: vietato dalla direttiva primaria (correttezza logica sopra il costo). I quattro vincoli hard nuovi riflettono la pratica della scuola; non si annacquano per far risalire lo score.

---

## 6. Sequenza raccomandata (un piano in 5 passi)

| # | Azione | Tipo | Sblocca |
|---|---|---|---|
| 1 | Chiudere TASK-526 + check hard score-vs-DB al confine di consegna | fiducia | credibilita' di ogni 0hard |
| 2 | Verifica istanza orfana (quale istanza in `AttemptOutcome`) | verifica | dove gira la tail |
| 3 | Winner-tail SurplusHarvest nel wrapper + ciclo di misura | meccanica | conversione H34 |
| 4 | TASK-574 redesign guardie OneOffFinisher + ciclo | meccanica | coda H7/H29/H33 |
| 5 | Re-validazione 0hard sul vincolame corrente (batch bad-seed) + cura input G09 | misura | claim m-13 onesto |

In parallelo, non bloccante: spedire il pacchetto referente e avviare TASK-452/441 (la transizione anno-senza-golden).

---

## Nota sui rischi

- Il rischio maggiore della via breve e' che l'oracolo improve-or-noop della winner-tail reverti tutto anche nella finestra giusta (vista parziale dei gate, famiglia gia' vista in v6: il pass chiudeva celle ma rompeva l'hard altrove). La mitigazione e' l'atomicita' per-cella gia' costruita in v10 (o la cella si completa o torna indietro).
- Il rischio della via media e' scoprire che col vincolame rigido p(0-hard) per costruzione e' calata molto: in quel caso la risposta non e' piu' metaeuristica (chiusa) ma alzare N nel multi-start o accettare un budget di consegna piu' lungo. Entrambe sono decisioni di costo, non di modello.
