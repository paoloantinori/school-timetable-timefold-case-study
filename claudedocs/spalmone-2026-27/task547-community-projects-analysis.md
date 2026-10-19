# TASK-547: analisi progetti Timefold community (sintesi finale)

Censimento: 2 progetti community verificati su ~232 discussioni passate (le condivisioni di
codice sono rare; quasi tutti i thread sono domande d'uso). Esclusi come non-community:
org TimefoldAI (ufficiale), optaweb-employee-rostering (kiegroup), quickstarts, un annuncio
di lavoro. Clone e report completi negli agent del 24/08; questo documento e' la sintesi
per maintainer. Nessuna adozione: solo raccomandazioni.

## Tabella incrociata per il nostro dominio

| Punto | Provenienza | Verdetto | Azione suggerita |
|---|---|---|---|
| Pattern context-shift al confine della finestra di solve (fuori-finestra pinnato, contato nei vincoli di confine, mai penalizzato tra se') | employee-scheduling (commenti a `weeklyHoursLimit`, `noOverlappingShifts`) | ADOTTARE (idea) | Il nostro problema di confine post-costruzione e' lo stesso: la regola "le coppie context-context non si penalizzano perche' fatto immutabile" e' la semantica pulita che manca ai nostri confini; rilevante quando solveremo su sottofinestre |
| Gradiente non monotono sui minimi con chiavi assenti (groupBy che non vede i gruppi a zero: SVUOTARE migliora lo score) | employee-scheduling (fix `minimumWeeklyShiftsEmptyWeek`, ifNotExists) | VALUTARE (verifica meccanica) | Controllare i NOSTRI vincoli di minimo su chiavi (gruppo, data) eventualmente assenti: M20 deficit, H33 floor, M20.2. Se un gruppo-data senza lezioni riduce la penalta', abbiamo lo stesso ottimo locale perverso |
| Pesi nella stessa unita' (minuti x weight, mai pesi raw accanto a pesi proporzionali) | employee-scheduling (bug `unassignedSoft`: un giorno indesiderato costava 48x uno shift scoperto) | CONFERMA | Gia' nostra regola di famiglia (M20/M15/M22); precedente esterno in piu' per non derogare |
| `DiminishedReturnsTerminationConfig` per-job via SolverConfigOverride (ratio clampato) | employee-scheduling | VALUTARE (differita) | Il pattern se mai la termination dovra' diventare configurabile dal workbook senza rebuild |
| `Joiners.overlapping` su intervalli LocalDateTime | employee-scheduling | SCARTARE (per ora) | Interessante solo se introducessimo blocchi a durata variabile fuori griglia 1h |
| CustomPhaseConfig guscio vuoto nel port indipendente | TimefoldSharp (`NotImplementedException`) | DATO | Conferma esterna che le custom phase sono la parte meno documentata/piu' accoppiata dell'API: coerente col nostro wiring via reflection; argomento in piu' per la discussione upstream #2615 e per trattare il nostro wiring come fragile con test di guardia |
| forEach vs forEachIncludingUnassigned reificato in classi separate nel port | TimefoldSharp (commit `46b596b`) | CONFERMA | Nessuna novita': e' la nostra TASK-377 vista da fuori; il punto semanticamente piu' delicato del constraint-stream API |
| Multi-threading assente nel port | TimefoldSharp | NEUTRO | La concorrenza resta Java-only: nessun esempio esterno da cui imparare per il nostro decider |

## Sintesi in tre righe

1. Il bottino concreto e' un pattern (context-shift al confine) e una verifica da fare
   (minimi monotoni su chiavi assenti): entrambi da employee-scheduling, progetto piccolo
   ma scritto con archeologia nei commenti.
2. TimefoldSharp vale come specchio: dove un port indipendente si arrende dice dove l'API
   e' fragile; per noi rafforza due verdicti gia' presi (guardie sul wiring custom, rispetto
   della famiglia forEach).
3. Nessuna lezione direttamente riusabile su ciclo staged, multi-start o MT-LS: a quella
   scala non c'e' nessuno, in community, che sia passato di li'.
