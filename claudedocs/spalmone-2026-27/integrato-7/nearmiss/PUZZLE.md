# PUZZLE (cieco, zero-context)

Un istituto ha un orario gia' costruito. resta UNA violazione hard, poi l'orario
e' perfetto. Il tuo obiettivo: portare il punteggio hard a zero con le mosse che
vuoi (spostare una lezione su un'altra cella, scambiare due lezioni, catene di
spostamenti, riassegnare la stanza). Ogni lezione si puo' muovere solo su celle
coerenti con le sue disponibilita'. Il punteggio hard conta cosi': ogni violazione
elencata sotto costa il suo peso; una mossa e' migliorante solo se il totale hard
scende. Il costo medium/soft non conta per l'obiettivo, ma una mossa non puo'
CREARE nuove violazioni hard.

## Regole hard pertinenti

- H1: un docente non puo' avere due lezioni nella stessa ora dello stesso giorno.
- H5: un docente non puo' avere lezioni nelle sue ore di indisponibilita'.
- H7: un docente non puo' superare il suo massimo di ore al giorno (default 6;
  eccezioni dichiarate in tabella docenti).
- H17: le ore di un gruppo nello stesso giorno devono essere CONTIGUE (nessun
  buco interno fra la prima e l'ultima ora del giorno).
- H20: una lezione deve cadere in una cella aperta dal calendario del gruppo.
- H23: le materie di laboratorio "quota-bound" di un gruppo non possono cadere
  in un giorno con quota laboratorio = 0 (vedi tabella quote).
- H26: un gruppo non puo' avere lezioni in un giorno senza riga di quota.
- H31: il primo giorno di lezioni di un gruppo deve partire dalla prima ora
  aperta del calendario.
- Stanze: ogni lezione deve avere una stanza; le materie con tag richiesto
  richiedono una stanza con quel tag; capacita' della stanza sotto il numero
  alunni e' SOLO un costo medium (non hard), cosi' come una lezione teorica in
  un laboratorio.

## Stato: il giorno difettoso

Gruppo **G04**, **venerdi' 20 novembre 2026**, 18 alunni. Calendario aperto
8-14. Quota del giorno: R04, lab 0. Situazione ora per ora:

| ora | materia | docente | stanza |
|-----|---------|---------|--------|
| 8   | Scienze | T25 | R03 |
| 9   | Scienze | T25 | R03 |
| 10  | Lingua Italiana | T14 | R04 |
| 11  | VUOTA   |         |        |
| 12  | Orientamento | T23 | R04 |
| 13  | Informatica | T06 | R13 |

L'ora 11 vuota fra le 10 e le 12 e' la violazione H17 (unica del puntoaggio:
hard = -1).

## Docenti coinvolti il 20/11 (ore totali / cap)

| docente | ore 20/11 | cap |
|---------|-----------|-----|
| T25 | 8,9 (G04), 10,11 (G07), 12 (G08) = 5 | 6 |
| T14 | 8,9 (G01), 10 (G04), 11 (G09) = 4 | 6 |
| T23 | 10 (G03), 11 (G06), 12 (G04) = 3 | 6 |
| T06 | 8 (G10), 10 (G11), 11 (G03), 12 (G03), 13 (G04) = 5 | 6 |

Tutti e quattro sono OCCUPATI alle ore 11 di quel giorno, quindi nessuno puo'
scivolare nel buco direttamente.

## Docenti del gruppo G04 liberi alle ore 11 del 20/11

T15 (3h quel giorno, cap 6), T24 (0h), T05 (0h). Gli altri docenti che
insegnano al gruppo (T21, T10, T03, T11, T25,
T14, T06, T23) sono occupati alle 11.

## Corsi del gruppo G04 (docente, ore totali, tag stanza richiesto)

| materia | docente | tag |
|---------|---------|-----|
| Lavorazioni meccaniche | T15 | meccanica |
| Recuperi ed Approfondimenti | T15 | nessuno |
| Cad | T15 | meccanica |
| Matematica | T05 | nessuno |
| Disegno | T24 | nessuno |
| Lingua Italiana | T14 | nessuno |
| Lingua Inglese | T21 | nessuno |
| Scienze | T25 | nessuno |
| Informatica | T06 | informatica |
| Orientamento / Storia / Economia | T23 | nessuno |
| Educazione Civica / Competenze | T11 | nessuno |
| Sicurezza | T03 | nessuno |
| Tecnologia Meccanica | T10 | meccanica |

## Materie "quota-bound lab" del gruppo G04

Lavorazioni meccaniche, Cad, Recuperi ed Approfondimenti. Queste materie NON
possono essere collocate in un giorno con quota lab = 0 (H23). Il venerdi' di 1
FC ha sempre quota lab = 0 (il calendario del gruppo prevede lab il mercoledi'
e il giovedi').

## Quote giornaliere G04, settimana del 16-20 nov (aula+lab)

16/11: 6, 17/11: 6, 18/11: 6, 19/11: 6, 20/11: 5.

## Stanze libere alle ore 11 del 20/11

| stanza | capacita' | tag |
|--------|-----------|-----|
| R14 | 24 | termoidraulica |
| R12 | 22 | informatica |
| R17 | 22 | saldatura |
| R11 | 20 | acconciatura |
| R13 | 20 | informatica |
| R02 | 15 | nessuno |

Tutte le altre stanze sono occupate alle 11 (inclusi tutti gli aula senza tag
con capacita' >= 18).

## Indisponibilita' docenti

T15, T24 e T05 non hanno indisponibilita' il venerdi' 20/11 (nessuna
vacation su quella data/ora).

## Vincolo di continuita' del piano annuo

Le lezioni esistono gia': la materia/ora annua di ogni corso e' fissata. Puoi
SPOSTARE lezioni (anche fra giorni diversi, anche fra settimane), non
aggiungerne. Una lezione spostata libera la sua cella di origine: anche il
giorno di origine deve restare senza buchi interni (H17) e con il primo slot
occupato alla prima ora aperta (H31). I giorni del gruppo G04 non hanno un
minimo di ore oltre il pavimento di 4 (sotto il pavimento e' violazione hard).

## OBIETTIVO

Portare il punteggio hard da -1 a 0. Riporta la sequenza di mosse scelta
(origine -> destinazione per ogni lezione mossa) e il conteggio finale delle
violazioni. Se dimostra che NESSUNA sequenza di mosse migliora il punteggio,
dichiara NO SOLUTION con la ragione di blocco per ogni tentativo.
