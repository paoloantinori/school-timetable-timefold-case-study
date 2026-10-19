# Sintesi audio referente 11/09 (2 file, parakeet-mlx + diarizzazione pyannote)

Trasmissione: maintainer intervista referente (SPEAKER_01 in entrambi i file; SPEAKER_02/00 =
maintainer e la lettura delle domande). Transcript grezzi a fianco; la sintesi qua e' la
lettura proiettata sul nostro modello. Le citazioni sono verbatim dai transcript.

## Le risposte alle 5 domande del pacchetto 9/09

| # | domanda | risposta colta | stato per noi |
|---|---|---|---|
| Q5 | chi tiene le 40h RA di G08 | "inizialmente non erano assegnati a nessuno, poi mi hanno detto distribuiscili un po'... un po' a T23, un po' a T03"; "se io lo faccio a caso, ma diverso dal tuo ce lo si fa andare bene" | **VALIDA la nostra cura golden-first**: la ripartizione esatta non importa, qualsiasi distribuzione dichiarata va bene. Confermato anche "molte le fa T23" |
| Q1 | G14 Confezione al giovedi'? | "perche' ha funzionato le ore... Questi qua sono ragazzi progetto... li fanno venire il giovedi' e venerdi' da noi... carico ridotto, pratica" | Conferma la PRASSI ma NON e' un vincolo esplicito: il giovedi' e' dove ha funzionato. Lo steering TASK-599 resta la direzione giusta (golden-practice, non hard) |
| Q2 | G12 giorni vuoti | "ok, ok" dopo la lettura (conferma debole) | Con l'evidenza golden (100% giorni riempiti) trattiamo come SUBITI; la precisazione professionalizzanti di oggi (sotto) spiega anche le celle venerdi' |
| Q3 | G13 | (ritirata da noi prima dell'audio: premessa era un nostro doppio conteggio) | chiusa |
| Q4 | sabato G14 | "il G14 hanno messo delle ore il sabato... il sabato di solito non si fa lezione" | Confermato, off-grid come gia' assunto |

## La regola professionalizzanti DETTA AD ORA (file 2, la piu' preziosa)

Verbatim chiave: "le materie professionalizzanti possono essere messe o alla prima e alla
seconda ora o all'ultima ultima ora... a botti da due, cosi' fai uscire prima e entrare
dopo. Dopo questa e' la regola." E: "la combinazione MATERIA-INSEGNANTE e' la
discriminante", "solo quelle professionalizzanti non possono andare in quei giorni li'".

E inoltre: "il venerdi' sono solo materie professionalizzanti, che loro sono in stage il
giovedi' e il mercoledi' e il lunedì mi sembra" (G12 = apprendisti: "gli apprendisti
fanno le materie professionalizzanti IN STAGE... non devono venire a scuola a fare le
materie professionalizzanti").

**CROSS-CHECK COL GOLDEN (eseguito, 11/09)**: lo stage di G12 nel golden e'
VENERDI'+SABATO (126+131h; mer/giov/mar quasi zero), e il foglio dichiara i venerdi'
SOTtili (52h aula+lab in tutto l'anno contro 164-174 di lun-giov). Le professionalizzanti
nel golden stanno a LUNEDI'/GIOVEDI' (Barberia 45h tutta lunedi'; Servizio tecnico e
stilistico 46h tutto giovedi'; Servizio stilistico lun+gio). Quindi la frase di referente va
lettura cosi': gli apprendisti fanno stage in azienda (ven+sab nel golden) e le
professionalizzanti a scuola vanno ai BORDI a botti da 2 perche' entrano dopo/escono
prima. La nostra M29_RESTRICTED_DAYS_BY_GROUP (G12 -> mer/giov) NON coincide ne' coi
giorni professionalizzanti del golden (lun/giov) ne' con la dichiarazione di referente: la
tabella e' da verificare. Il venerdi' dichiarato sottile + le nostre celle venerdi'
vuote (censimento TASK-590: NEVER_ATTEMPTED, nessuna cella template il venerdi') restano
da riconciliare nel task nuovo: TASK-600 aperto con tutti i dati.

## La matrice rosso/verde che la scuola vuole darci

referente + maintainer convergono su un foglio SETTIMANALE per (materia, docente) con le caselle
dove il corso NON puo' andare ("T09... io gli indico le caselle dove non puo' essere
messa"; "sulla settimana fai, e poi copia e incolla in tutto l'anno"), solo per i
docenti vincolati ("neanche tutti gli insegnanti, solo quelle professionalizzanti / con
vincoli"). E' esattamente la forma del nostro foglio workbook steering
(teacher-weekday-steering.csv): il veicolo esiste, va popolato con loro. NOTA: nel
nostro modello la forma naturale e' per (gruppo, corso) non (docente): da chiarire al
popolamento quale chiave usano davvero loro ("materia-insegnante e' la discriminante").

## Fatti NUOVI (non nel modello, da gestire)

1. **Ora di tutor al rientro stage** (file 1, 3:44): "quando la classe rientra [dallo
   stage], il tutor del corso dovrebbe avere un'ora, cosi' li vede... parla dello stage,
   raccoglie i fogli". referente stesso NON sa chi siano i tutor: domanda workbook, potenziale
   nuovo vincolo (1h del tutor la settimana di rientro per ogni gruppo con stage).
2. **AULA 11** (file 1, 7:38): "c'e' un altro laboratorio che io non ti ho detto: un'aula
   normale, l'aula 11, che verra' attrezzata con delle prese" (il laboratorio di G14 si
   fa li'). Nuova stanza da aggiungere all'inventario quando attrezzata; coerente con la
   risposta referente Q19 del 01/08 (G14 in aula) e con il de-tag Confezione TASK-550.
3. **Corsi apprendisti esterni** (file 1, 8:27): due corsi di apprendisti fuori-obbligo
   usano il laboratorio di informatica e l'R18 (17 persone): "siamo
   sottodimensionati come aule". Pressione sulle stanze ESTERNA al nostro scheduling ma
   da dichiarare nel workbook (capacita' effettiva delle stanze condivise); maintainer ha
   chiesto i dati per darli in pasto al modello.
4. **Spostamenti manuali a posteriori** (open day ecc.): "si decide dopo... conta come
   lezione": rumore non modellabile, conferma che il foglio non potra' mai essere
   completo di questi eventi.

## Le euristiche del pianificatore (file 1, 0:00-2:55)

L'ordine con cui referente costruisce l'orario a mano (da cross-riferire con
claudedocs/human-planner-heuristics.md):
1. le ore di LABORATORIO per prime ("me ne metto giu' prima le ore di laboratorio");
2. i vincoli espliciti a giorno fisso (es. T24 T12 solo martedi', ma "se li metto
   sempre solo il martedi' non terminano l'incarico" -> anche altro giorno);
3. gli STAGE;
4. G14 per primo ("talmente poche ore... si possono giocare poco", gio/ven);
5. regolarita' dello slot per docente: stessa materia stessa ora settimanale dove il
   monte e' basso; per i monti alti DUE giorni scelti ("una al giovedi' o una al lunedi'"),
   "cercando di mantenere quanto e' possibile la regolarita'";
6. quota di ore a fine anno per le materie con tesina (informatica ecc.).
E il riconoscimento esplicito che le nostre domande producono risposte post-hoc: "mi
hanno detto dopo: 5 le fa lui, 5 le fa qua" (le ore RA), "vengono fatti gli spostamenti
manuali" (open day): il foglio e' un'istantanea, la pratica slitta.
