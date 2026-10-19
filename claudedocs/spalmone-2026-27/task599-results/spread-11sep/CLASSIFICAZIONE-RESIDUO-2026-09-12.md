# Classificazione del residuo spread-11sep (m-14, 12/09 mattina, Fedora)

Consegna [−11/0] (la migliore della serie): H34=10, H7=1. Fonti: lessons.txt del ciclo
(e5151a86) + fogli + tsdates. Ogni cella riconciliata meccanicamente (10/10 identiche al
provider).

## La scoperta strutturale: NON e' fame, e' DIREZIONE

Per ogni gruppo con celle vuote: **pool = ore collocate, slack ZERO** (G06 990/990,
G07 695/695, G09 666/666, G10 670/670, G11 671/671, G12 675/675, G13
683/683). Il pool e' ESAURITO ESATTAMENTE. Le celle dichiarate vuote non sono mancanza
di capacita': le ore che il foglio mette su quelle date stanno da un'altra parte.

## Dove stanno: le ore sono parcheggiate sui GIORNI DI STAGE

I giorni "ghost" (ore collocate dove il foglio dichiara quota-aula 0) sono tutti righe
**S** del foglio: G13 2027-06-09 (foglio: stage 4h; run: 6h ORDINARIE non-pinnate
 piazzate li'), G07 06-09 e 05-28 (S; 2h ordinarie ciascuno), G09 04-21 (S; 2h).
 Somma surplus-su-dichiarate: G13 23h, G09 10h, G07 10h, G10 7h, G12 5h,
 G06 4h, G11 4h.

Le celle vuote sono l'immagine speculare: giorni ORDINARI dichiarati (A, 4-5h) lasciati
a zero mentre le loro ore stanno su giorni che il foglio assegna allo stage. Il caso
puro: 2027-06-09, G13 dichiara stage-4h (e ci finiscono 6h ORDINARIE) mentre G06
dichiara 4h ordinarie (e resta vuoto). **Il solver parcheggia ordinaria sui giorni di
stage e svuota i giorni ordinari.**

## Le 10 celle (tutte in coda aprile-giugno, giorni ISOLATI con vicini pieni)

G06 06-09, G07 06-03+06-04 (settimana di coda interamente vuota), G09 05-10 +
05-24 (lunedi' isolati fra 6h venerdi' e 6h martedi'), G10 05-17 (lunedi'), G11
06-04, G12 04-16 (venerdi'), G13 04-26 + 05-24 (lunedi' fra venerdi'/martedi' a 8h
saturi).

## CORREZIONE DELLA PRIMA LETTURA (verificato in DB + golden, 12/09 ~08:15)

La prima ipotesi "parcheggio sui giorni di stage" va corretta su due punti:

(1) G13 NON e' un caso stage-day: le sue quote arrivano TUTTE da calendario_fc.csv
(171 righe, FCQuota=true; le 171 righe class_days di G13 sono ignorate). Il foglio
FC dichiara 06-09 e 06-10 come GIORNI LAB (lab=4): le ore ordinarie li' sono
LABORATORIO legale, non parcheggio. L'autorita' per G13 e' il foglio FC, non
class_days (il mio classificatore iniziale misurava sul foglio sbagliato: coincideva
solo perche' sulle date in questione i due fogli concordano su aula+lab>0).

(2) Per i gruppi NON-FC (G07, G09...) l'ordinaria sui giorni S e' LEGALE nel
modello: il filtro quota VRP e' FC-only (TASK-385: "i consumatori FC-specifici
restano filtrati su FCQuota == true"), quindi H23/H24 non bloccano. Il dump lo
mostra: G07 06-09 = Lingua Italiana 8-9 + Stage 10-15, stessa giornata.

## GOLDEN-FIRST: la scuola cosa pratica

Ordinaria su giorni dichiarati-S nel golden: G07 5h, G10 4h, G08 16h, G13 4h
= 29h/anno TOTALE (praticata ma rarissima: il 99%+ dei giorni S resta solo-stage).
Giorni con ordinaria E stage insieme: 2 in tutto l'anno (G10 e G08, entrambi
01/04). Il nostro run ci mette ~16h ordinarie su giorni S: stessa scala, ma le nostre
finiscono sui giorni SBAGLIATI mentre i giorni dichiarati ordinari restano vuoti.

## MECCANISMO COMPLETO (finale)

1. Pool esaurito esattamente (slack 0): nessuna fame.
2. L'ordinaria su giorno S e' legale (non-FC) e golden-praticata al margine.
3. Quando il pool finisce, QUALI giorni dichiarati restano vuoti e' deciso
   dall'ordine costruzione/repair: niente preferisce i giorni ordinari dichiarati
   alle mattinate dei giorni S per l'ora marginale.
4. H32 e' strutturalmente cieco dei giorni vuoti (il suo groupBy vede solo giorni
   CON lezioni): solo H34 la paga, flat 1/cella, prezzo abbastanza basso da essere
   scambiato.

## CURA (direzione, banco prima)

Una PREFERENZA (non vincolo: il golden pratica le 29h/anno di eccezione, un hard
filter sarebbe piu' rigido della pratica = violazione golden-first): il fill/stamp
prezza il collocamento ordinario su giorno-S sopra quello su giorno dichiarato
ordinario ancora libero, o equivalentemente il repair preferisce donatori
giorno-S/quota-0 quando la ricevente e' una cella dichiarata. Forme candidate:
penalita' M-tier sul collocamento ordinario in giorno quota-0-con-stage (lo specchio
di M28 pomeridiana), o l'ordinamento dei candidati nel fill tail. Banco sui
checkpoint spread-11sep con predizione scritta.

## Domande residue

1. H7=1: chi sfora (drill API sulla consegna se serve, il DB e' stantio).
2. G13 04-26/05-24 restano le 2 celle dove nemmeno questa spiegazione copre: sono
   giorni AULA dichiarati dall'FC sheet, vuoti, con vicini 8h saturi: pura
   distribuzione (QFR strozzato dai vicini saturi), la cura-preferenza li aiuta
   indirettamente spostando le ore dai giorni lab/S attigui.

## Cura gia' in mano per 1 delle 10

G12 04-16 (venerdi'): e' la cella del filone TASK-600 (il venerdi' G12 senza
celle template, cura stage-exclusion prototipata flag-OFF, banco end-state differito):
il trade H34<->H32 per chiuderla e' gia' registrato li'.
