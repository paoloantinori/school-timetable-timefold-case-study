# Notifica a referente: cure golden-first derivate dal suo orario di riferimento (11/09/2026)

Premessa metodologica (direttiva golden-first estesa agli input, 31/07): dove un dato di
input diverge dalla PRATICA del suo stesso orario di riferimento (lessons.csv), adottiamo
il valore praticato dal golden e notifichiamo a posteriori; la risposta di referente resta
benvenuta come correzione. Questo documento registra cosa abbiamo adottato, cosa abbiamo
inferito e cosa resta genuinamente suo, per le 5 domande del pacchetto del 9/09.

## Q5 (G08, 40h "RA") - ADOTTATA la distribuzione del golden

Il foglio incarichi portava le 40h come docente "RA" (sigla senza nome): nessuna
assegnazione possibile, i giorni di G08 restavano magri. Il suo orario di riferimento
colloca 41h di Recuperi ed Approfondimenti distribuite su 9 docenti:

T23 19, T15 6, T10 5, T05 3, T06 2, T14 2, T21 2,
T03 1, T11 1 (totale 41h; il foglio diceva 40: arrotondamento notificato a parte).
Cadenza praticata: soprattutto il martedi' (25 delle 41 ore).

ADOZIONE: la riga "RA" e' sostituita da 9 righe incarichi con questa esatta
distribuzione. Verifica meccanica: derive-golden-incarichi-corrections.py ora da 0
divergenze incarichi-vs-golden su tutte le triple reali. Se la ripartizione di quest'anno
e' diversa, basta indicarla: la correggiamo come input dichiarato.

## Q4 (sabato G14 16/01/2027) - CONFERMATA dal golden, nessuna cura necessaria

L'orario di riferimento mette proprio 5h di Orientamento (T14) il sabato
16/01/2027. Il pool incarichi (115h) non le contiene e non le deve contenere: la
differenza 120 (foglio calendario) vs 115 (incarichi) e' esattamente il sabato. Nessun
cambio input; le 5h restano fuori dalla griglia lun-ven come tutte le ore di sabato.

## Q1 (triangolo G14 / T16) - RIFORMULATA: la risposta e' nel golden

Il suo orario NON ha alcun triangolo: la Confezione G14 (30h, T16) sta TUTTA al
giovedi'; i venerdi' G14 (3h per 14 venerdi') si riempiono con Competenze Digitali,
Comportamento Sociale e Orientamento; T16 al venerdi' fa Confezione con G01 e
G09. Le opzioni che avevamo posto (togliere 2h / spostare un'ora MODA / venerdi' da
2h) sono tutte smentite dai dati: i venerdi' praticati sono da 3h pieni e il monte da
115h viene collocato per intero.

Il difetto e' nostro (il motore pianifica Confezione G14 al venerdi' dove T16 e'
satura) e la cura e' solver-side. Domanda che resta a referente, come conferma di prassi:
"Confezione G14 al giovedi' e venerdi' con le altre materie, come nel vostro orario?"

## Q3 (G13 sovradichiara?) - RITIRATA: premessa errata

Ricalcolo per-data foglio vs golden: delta mediano 0 (153 date su 171 identiche, ±1h
nelle restanti), totali 966 vs 978. Il "10-12h/giorno" citato nasceva dal raddoppio del
monte con la riga di totalizzazione del foglio (990+990): artefatto di lettura nostro,
non una divergenza del foglio. Nessuna domanda.

## Q2 (G12 giorni vuoti) - risposta suggerita dai dati: "subiti"

L'orario di riferimento riempie il 100% dei giorni dichiarati con griglia (quota esatta,
zero giorni sotto-quota); le 8 eccezioni sono tutte chiusure dove il foglio dichiara ore
nonostante non ci sia scuola. Nessun motivo non-raccolto emerge dai dati; se esistono
motivi (visite, alternanza), vanno dichiarati e diventano input.

## Quadro d'insieme

Dopo le adozioni Q4/Q5, incarichi e golden riconciliano su OGNI tripla docente-materia-
gruppo (0 divergenze meccaniche). Restano in attesa le risposte del pacchetto del 9/09
per ciò che e' genuina intenzione d'anno (titolarita' confermate, eventuali giorni voluti,
conferma prassi giovedi' G14).

## AGGIUNTA 18/09 (post-pacchetto): G07 09/06, il foglio dice (5,S) ma l'orario fa 5 ore ordinarie

Il calendario_class_days dichiara il 9 giugno 2027 per la G07 come giornata di stage
(5 ore, modality S). Il suo orario di riferimento quel giorno svolge invece 5 ore
ordinarie in aula: Educazione Civica (T23) x3 + Scienze (T25) x2, prima ora
alle 8. Adottiamo la pratica dell'orario: la riga e' ora (5,A), le 5 ore si spostano
dal monte stage al monte aula del gruppo. Notifica a posteriori come le precedenti:
se il marking stage era intenzionale (recupero stage a fine anno), una riga torna
indietro con una parola.

Contesto: con la riga a S, la giornata restava senza bersaglio dichiarato per le ore
ordinarie e la nostra consegna la trattava come giorno di stage parziale; la
contraddizione era emersa dal confronto sanity cella-per-cella del 17/09 (le celle
"1h" dei giorni S erano ore ordinarie innestate su giorni di stage: famiglia M39,
censita e chiusa). Le altre due giornate genuinamente miste che il golden pratica
(G10 01/04 e G13 09/06) NON sono toccate: restano dichiarate S e la domanda
"giorni misti voluti?" resta nel pacchetto aperto.
