# Doppio R16 - modellazione preparata (TASK-475, 2026-08-01)

> Stato: PREPARAZIONE di modellazione, NON adottata. Il doppio R16 e' una domanda
> referente (T10) in attesa di conferma scuola: chi e' il secondo assistente moda, e i budget
> orari 100+100 / 100+85 sono indicativi o vincolanti? maintainer (01/08): "fai assunzioni tu,
> eventualmente piu' di una, e prepara la modellazione per ognuna". Questo doc prepara le tre
> assunzioni (A1/A2/A3) affinche' l'adozione sia un flip quando referente risponde.
> Sorgente decisioni: `referente-autoverifica-domande-2026-08-01.md` (T10). Seed corrente: esiste
> gia' `LabAssistant(T14, "moda")` (DataGenerator:1071, dal docx TASK-368/369).

## Cosa c'e' gia' nel motore (M-LA)

`BaseConstraintProvider.labAssistantConflictLessonLesson` (M-LA, ONE_MEDIUM): penalizza quando
la lezione PROPRia di un assistente sovrappone (stesso timeslot) un lab nella stanza-tag del
suo `LabAssistant.roomTag`. Cioe': M-LA scoraggia l'assenza dell'assistente dal lab assegnato,
ma NON conta le ore di co-presenza ne' enforce un monte-ore. Aggiungere un `LabAssistant` fa
scattare M-LA per quel docente automaticamente (nessun altro lavoro). Serve un vincolo NUOVO
solo se si vuole garantire un budget di co-presenza (A2).

## A1 (minima, default proposto) - T20 e' un secondo assistente moda

Assunzione: T20 e' un secondo assistente di R16 (moda), i budget sono indicativi.
La modellazione e' una riga di seed: M-LA fa il resto.

**CONFERMA DOCUMENTALE (01/08 sera, cross-finding dalla prep TASK-474):** l'extract verbatim
`vincoli-tabs-golden-xlsx-estratto-2026-07-31.txt` (tab "Vincoli DA ANALIZZARE", righe 96-97)
cita ESPRESSAMENTE entrambi: `T14 | Libera per doppio in 2°(100 ore) e 3° moda (100 ore)
in R16` e `T20 | Libero per doppio in 2° (100 ore) e 3° moda (85 ore) in R16`. Quindi
A1 NON e' piu' un'assunzione pendente referente: T20 E' il secondo assistente moda per documento, e
i budget (100/100 T14, 100/85 T20) hanno fonte verbatim. Resta da chiarire solo se i
budget sono vincolanti (A2) o indicativi (A1 + M-LA basta). Il verbo "Libera/libero per doppio"
descrive esattamente cio' che M-LA modella (l'assistente libero durante il lab), quindi A1 e'
l'interpretazione letterale. Promuove A1 da "default proposto" a "interpretazione documentale".

Patch PREPARATA (NON applicata - DataGenerator e' file protetto, serve OK di maintainer; la conferma
documento riduce il rischio ma il file protetto resta). In `DataGenerator.createLabAssistants`
(dopo riga 1071, sotto `createLabAssistant(findTeacherByExactName(teachers, "T14"), "moda");`):

```java
        // TASK-475 A1 (PREPARATO, non applicato): T20 come secondo assistente moda
        // (domanda referente T10). Conferma scuola pendente. M-LA gestisce la co-presenza.
        createLabAssistant(findTeacherByExactName(teachers, "T20"), "moda");
```

Verifica fattibilita': T20 e' docente del seed (`incarichi.csv`: Merceologia 1/2/G09
30/22/15h), quindi `findTeacherByExactName(teachers, "T20")` risolve. Effetto con M-LA: sia
T14 che T20 vengono penalizzati (ONE_MEDIUM) quando una loro lezione propria
sovrappone un lab nella room tag "moda" (R16). Nessun nuovo vincolo, nessun flag, nessun
test nuovo oltre la sopravvivenza di M-LA. Da misurare al primo run: quanta penalita' M-LA in
piu' e se il solve la rispetto (entrambi liberi nei lab moda).

## A2 (budget vincolanti) - vincolo M nuovo di co-presenza budget

Assunzione: i 100+100 / 100+85 sono monte-ore di co-presenza da garantire (non indicativi).
M-LA non basta: serve contare le ore in cui l'assistente e' LIBERO/co-schedulato durante i lab
di R16 e penalizzare il deficit rispetto al budget. Prototipo dietro flag default OFF.

Design del vincolo `coPresenceBudget` (M, flag `planner.doppio-R16.enabled` default OFF,
knob `planner.doppio-R16.weight`):

1. `LabAssistant` ottiene un campo `Integer coPresenceBudgetHours` (monte-ore da garantire;
   null/0 = inerte, l'assistente e' solo M-LA). Seed: T14=100, T20=100 (scenario
   100+100) o T20=85 (scenario 100+85). Singolo campo copre entrambi gli scenari.
2. Il vincolo iter gli assistenti che hanno un budget > 0 (filtro early come M-LA, ~2 docenti).
3. Per ciascun assistente con budget B e roomTag T: considera l'insieme L dei lab lesson nella
   stanza taggata T (i lab di R16 del docente titolare).
4. `coPresenceHours(a) = |{ t in timeslot(L) : l'assistente a NON ha una lezione propria in t }|`
   (le ore-lab in cui l'assistente e' libero = co-presente disponibile).
5. `deficit(a) = max(0, B - coPresenceHours(a))`.
6. `penalizeMedium(weight x deficit(a))`, per assistente (group-by assistantId).
7. Realizzazione pratica del passo 4: specchio del join di M-LA. Per ogni (assistantLesson,
   labLesson) stesso timeslot in roomTag T = un'ora di ASSENZA (M-LA la conta). Le ore-lab
   totali meno le assenze = coPresenceHours. Quindi: `coPresenceHours = totalLabHours(T) -
   conflictCount(a, T)`, dove conflictCount e' esattamente il match-count di M-LA per quell'
   assistente. Questo RIUSA la struttura di M-LA (stesso join) invece di duplicarla.
8. Tier MEDIUM: il budget e' un target di copertura, non una feasibility (la scuola stessa
   indicava i budget come "indicativi"); il trade-off co-presenza vs riempimento si misura.
9. Interazione con A1: A1 aggiunge l'assistente, A2 aggiunge il budget sullo stesso record.
   Compongono: `LabAssistant(T20,"moda", budget=100)` attiva sia M-LA che coPresenceBudget.
10. Gate: checkpoint stanze + full-solve A/B x2 prima di qualunque adozione (lezione E3). Il
    vincolo e' sensibile al totalLabHours(T) (quanti lab moda ci sono), quindi va misurato sul
    seed reale, non sintetico.

Nota onesta: il passo 7 (coPresenceHours = totalLabHours - conflictCount) e' elegante perche'
riusa M-LA, ma `totalLabHours(T)` e' un aggregato per-tag calcolato a runtime (lab lesson nella
room taggata T, su tutto l'orizzonte). Va verificato che coincida col "lab del titolare" (non
tutti i lab moda coinvolgono il doppio). Se la scuola specifica quali lab sono "di R16 con
doppio", serve un marcatore sul Course (es. `course.doppioAssistant` citato in CLAUDE.md ma non
implementato come pin). Da chiarire con referente prima di A2.

## A3 (tab stale) - fallback

Assunzione: solo T14 e' il doppio (come gia' nel seed), T20 e' un refuso del tab.
Nessun cambiamento di codice ne' di seed. Resta il fallback se referente smentisce A1. M-LA resta
cosi' com'e' (solo T14 assistente moda).

## Come si decide all'arrivo della risposta referente

- "T20 e' il secondo assistente, budget indicativi" -> A1 (flip della patch preparata).
- "T20 e' il secondo assistente, budget da garantire" -> A1 + A2 (prototipo A2 da buildare).
- "Solo T14 / T20 refuso" -> A3 (nulla da fare).
In tutti i casi la modellazione e' pronta; nessuna decisione architetturale pendente oltre la
risposta. La verifica a posteriori resta nel pacchetto referente come conferma-cortesia.
