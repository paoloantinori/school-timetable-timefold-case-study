# Classificazione del residuo control-11sep (m-14, 11/09 sera, Fedora sui artifact di zai)

Consegna [−26/0] su HEAD 654c52e3 (prima misura end-to-end della cura RA): H34=13,
H0=6pt/3 lezioni, H7=3, H33=2, H29=1, H31=1. Fonti: analysis.json + lessons.txt (955be7b3)
+ drill-down API sulla consegna in-memory. AVVISO METODO: il volume DB del profilo e'
STANTIO rispetto alla consegna in-memory (verificato: T26 ha le sue 7h del 16/23-09
nell'analisi API ma ZERO righe quelle date nel DB; il restore del vincitore multi-start
non persiste tutte le placements: stessa classe del "v132 DB drift" del residual-ledger).
REGOLA RINFORZATA: le classificazioni leggono SOLO export/dump API, mai il volume vivo.

## Il lieto della cura RA (il suo scopo e' riuscito)

**G08 e' USCITO dai sanity reds** (control-10sep: G07, G08, G09, G10;
control-11sep: G07, G09, G10, G14). I giorni magri di G08 col pool 667 sono
riempiti. La correzione input fa cio' che doveva fare.

## H34 = 13 celle, TUTTE coda maggio-giugno + lunedi' G13

G06 06-04/06-09/06-10, G07 06-04, G09 05-10/05-24, G10 06-09, G11
05-27/06-03, G12 06-04 (VENERDI'), G13 04-19/05-10/05-17.

Confronto col control-10sep (10 celle): il nucleo G06-giugno, G09-maggio, G10
06-09, G13 lunedi' e' INVARIATO; le nuove (G07 06-04, G11 05-27, G12 06-04,
G13 05-10) sono tutte nello stesso quartiere. Lettura: le 41h RA aggiunte consumano
capacita' costruttiva PRECOCE che prima colava in coda d'anno -> coda leggermente piu'
vuota (+3). Famiglia unica: import teacher-locked di coda + G13 lunedi' + venerdi'
G12 (quest'ultimo in cura nel banco TASK-600: stage-weekday vote exclusion).

## H0 = 6pt: le 3 ore G14 Confezione/T16, INVARIATE

La famiglia TASK-599 (triangolo del venerdi'): la cura RA non la tocca, come previsto.

## H7 = 3pt (DECODIFICATO, era il TODO del control-10sep)

Drill-down API (verbatim): "Teacher **T26** has 7 hours on 2026-09-23 (max 6)" e
2026-09-16 (Servizio stilistico, G10); "Teacher **T25** has 7 hours on
2026-12-17 (max 6)" (Scienze, G07). Giorni da 7 ore in 7 slot distinti (nessun H1).
Sono artefatti del nuovo bacino (il builder ha il gate teacherDailyCap: emergono dopo
la costruzione o in LS). NOTA STORICA: il "non riconciliato" del control-10sep era lo
stesso fenomeno letto sul DB stantio (zero 7h-days nel volume): il metodo giusto era
il drill API.

## H33=2, H31=1, H29=1

Residui minimi del nuovo bacino; H29 = la lezione G14 senza stanza del sanity.

## Sintesi per TASK-596

Il residuo [−26] si scompone: coda maggio-giugno 13pt (import teacher-locked: search-side
o giorni voluti da dichiarare), G14 Confezione 6pt (TASK-599, cura identificata),
H7 3pt (bacino, cura search-side multi-start/gate LS), minimi 4pt. NESSUNA regressione
strutturale: e' la stessa fame di coda del control-10sep con 41h di capacita' spostate
in avanti. La banda di variabilita' del bacino deterministico su tre consegne consecutive
(−13, −17, −26) con input in evoluzione conferma che il gate di adozione va letto sul
VETTORE-FAMIGLIE, non sul totale.
