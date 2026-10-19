# Golden mining ROUND 2 (laptop, 2026-08-16 pomeriggio)

> Filone 1 del mandato. La finestra golden e' aperta: ogni pattern estratto e'
> patrimonio che sopravvive alla scomparsa del file. Round 1
> (`golden-mining-2026-08-15.md`): 5 pattern, 2 confutati, fogli 22/23 proposti.
> Script ripetibile: `scripts/analyze/golden-mining-r2.py` (read-only, exit 0;
> popolazione con le trappole golden escluse: closed, sabati, Fasullo, pseudo
> Stage; 10.542 lezioni filtrate).

## TL;DR

| candidato | verdetto | cosa resta |
|---|---|---|
| A. intra-giorno (meta' preferita) | **CONFONDATO dalla compattazione** (l'87% supera la barra ma e' la firma di M18/H17, non una pratica per-materia) | il residuo onesto: nei giorni lunghi il lab sta DOPO la teoria (60/40 seconda meta' vs 55/45), ~0,6 slot piu' tardi: ordinamento debole, nessun foglio |
| B. concentrazione mensile | **PARZIALE**: la soglia 2x e' lasca (parte e' rumore multinomiale); l'insieme FORTE (>=4x) sono 6 coppie, tutte Servizio*/Barberia/Recuperi | foglio workbook candidato (solo insieme forte) + domanda per caso |
| C. coppie adiacenti | **CONFUTATO** (1 coppia a 3,0x su 458 distinte: non e' pratica) | niente |
| D. carico lun/ven | **CORRETto (16/08 sera): 4 celle reali, tutte G04 lun-gio** (68/68/79/67%); il quadro "venerdi' leggeri" della prima stesura era artefatto della baseline buggata (stage-Friday contati come atteso aula+lab) | foglio 25_WeekdayFill sulle 4 celle + domanda referente ( concatenate alla domanda 835-vs-750 gia' fatta il 14/08) |

## A. Distribuzione intra-giorno

MISURA (per (gruppo, materia) con >=20h, quota ore in slot 0-3): **188/216 = 87%
con preferenza chiara (>60% in una meta')**, ben sopra la barra del 70% del
mandato. MA il controllo d'insieme smentisce la lettura "preferenza":
**24 materie sono AM-consistenti in >=3 gruppi, ZERO PM-consistenti** (Scienze
12/12, Matematica 10/10, Lingua Inglese 9/9... e anche i LAB: Lavorazioni
meccaniche, Barberia, Cad, Confezione tutti AM). Un meccanismo per-materia non
puo' produrre "tutto al mattino"; la compattazione verticale (day-start 99,86%,
giorni front-loaded) si'. Il round 1 (sez. 3) arrivo' alla stessa conclusione con
teoria 78% AM vs lab 70% AM.

RESIDUO ONESTO (giorni lunghi, >=6 ore collocate): teoria 55%/45%
prima/seconda meta', **lab 40%/60%**, slot medio 2,35 vs 2,91 (~0,6 slot di
differenza), other 50/50. C'e' un ordinamento teoria-prima/lab-dopo, debole.
RIDUZIONE-SPAZIO: bassa e in gran parte gia' servita (M18/H17 spingono il
front-loading; M15/S17 formano i blocchi). PROPOSTA: NESSUNA (misura
registrata; dichiarare la meta'-preferenza dichiarerebbe la compattazione due
volte). Il PM residuo vero e' per-(gruppo,materia) (Informatica PM in 4 gruppi,
Disegno/Elettrotecnica/Cosmetologia PM): se un giorno verra' chiesto, e' una
colonna del foglio pratiche-per-gruppo, non un foglio.

## B. Raggruppamenti mensili

MISURA: per (gruppo, materia), ore osservate per mese vs attesa = ore del corso
x quota delle ore-attese del gruppo in quel mese (base = FOGLI CALENDARIO:
class_days hours + fc aula+lab; quindi i periodi di stage sono GIA'
contabilizzati nell'attesa: l'eccesso e' oltre quota). Su 171 coppie >=30h:
41 con un mese >=2x attesa (con >=25% dell'anno li'), 26 >=3x, **8 >=4x**.

La soglia 2x del mandato e' LASCA: con quote attese ~10%/mese, il vincolo
"un mese con >=25% dell'anno" scatta da solo per rumore multinomiale (ordine
di grandezza: ~10% di falsi positivi attesi sulle coppie piccole). L'insieme
DICHIARABILE e' quello >=4x, oltre ogni rumore:

| ratio | gruppo | materia | mese | concentrazione |
|---|---|---|---|---|
| 4,8x | G02 | Servizio stilistico | 2027-01 | 18h su 40h |
| 4,8x | G02 | Barberia | 2027-02 | 18h su 36h |
| 4,6x | G12 | Servizio tecnico e stilistico | 2027-04 | 32h su 46h (70%) |
| 4,4x | G04 | Recuperi ed Approfondimenti | 2027-04 | 28h su 50h |
| 4,1x | G06 | Barberia | 2027-03 | 23h su 47h |
| 4,1x | G02 | Barberia | 2026-12 | 12h su 36h |

CORREZIONE (16/08 sera, trovata costruendo l'estrazione live del foglio 24):
la baseline del primo giro conteva anche le ore delle righe S (stage) di
class_days come capacita' aula+lab: 2.092 ore gonfiate su 7 gruppi. Con la
baseline corretta (solo A+L, stage escluso, fc aula+lab) tre righe della
tabella precedente erano artefatti (Cad G08 febbraio/maggio scendono a
3,1x/2,8x, G10 Servizio stilistico settembre sotto 4x) e i ratio delle
rimanenti si assestano come sopra. L'insieme forte passa da 8 a 6 coppie.

Tutte le materie dell'insieme forte sono professionalizzanti/lab "da
parcheggio" (Servizio*, Barberia, Recuperi): il modello e' "il corso sta
in coda e viene recuperato a blocchi in mesi scelti". RIDUZIONE-SPAZIO: alta
per cella dichiarata (il G12 aprile vincola il 70% del corso in un mese =
~8 celle-giorno). PROPOSTA (per maintainer): foglio workbook
**24_ConcentrazioneMensile**, SOLO righe dell'insieme >=4x, con domanda per
riga: "concentrazione scelta o contingenza del recupero?" (se contingenza, non
si dichiara: il solver la ricrea da solo quando la capacita' lo costringe).

## C. Coppie di materie adiacenti

MISURA: per (gruppo, coppia), adiacenze osservate (slot consecutivi, stesso
giorno, materie diverse) vs attesa casuale per giorno 2*countA*countB/L.
Risultato: 458 coppie distinte osservate, **1 sola sopra 3x** (Approfondimenti
tecnica operativa + Igiene e sicurezza, 3,0x, n=17: due corsi da 1h che
finiscono in coda ai blocchi, compatibile con il riempimento). VERDETO:
**CONFUTATO come pratica**: la scuola non co-schedula coppie di materie; la
adiacenza e' dominata dai blocchi same-subject (gia' modellati da M15/S17).
NESSUNA proposta.

## D. Carico lunedi/venerdi

MISURA (CORRETTA, 16/08 sera): fill per (gruppo, weekday) = ore golden sui
GIORNI ATTESI del calendario / ore attese dei fogli (semantica sources:
skip FC in class_days, guard S, osservato solo su giorni attesi; le ore
golden fuori calendario sono la domanda separata gia' al trillo 14/08).
Soglia del mandato: fill < 80% con >= 10 giorni attesi.

Le celle sotto soglia sono 4, tutte G04: lunedi' 68% (134/196), martedi'
68% (135/198), mercoledi' 79% (166/210), giovedi' 67% (145/216); venerdi'
G04 e' al 97%. E' la faccia per-weekday della domanda 835-disponibili-vs-
750-assegnate gia' fatta a referente il 14/08: il gap sta su lun-gio, non sul
venerdi'. G08 (83-97%) e G09 (92-96%) stanno sopra soglia.

CORREZIONE della prima stesura: il quadro "venerdi' sistematicamente leggeri"
(G12 29%, G10/G11 61%, G13 60%, G07 64%, lunedi' 1/G08 68/67%,
G09 64/68%) era un artefatto DOPPIO della baseline difettosa: (a) le ore
delle righe S venivano contate come atteso aula+lab (i venerdi' di stage di
G12 risultavano "attesi e non riempiti"), (b) il numeratore includeva ore
golden su giorni fuori calendario e il denominatore no. Con la semantica
corretta TUTTE quelle celle sono al 95-100% (G12 venerdi': 52/52 sui 12
venerdi' davvero attesi). Il TL;DR sopra e la domanda a referente sono stati
riscritti sui numeri corretti.

RIDUZIONE-SPAZIO: dichiarare le 4 celle come fill praticato (es. G04
lun-gio ~4h invece di 6) toglierebbe ~2h x ~33 giorni x 4 weekday dallo
spazio di quella classe, o meglio: correggere l'attesa del foglio. PROPOSTA
(per maintainer): foglio 25_WeekdayFill con le 4 celle, origine=sistema, domanda
"il calendario sovrastima G04 su lun-gio o la scuola lascia corto per
scelta?" (concatenata alla domanda 835-vs-750 del 14/08).

## Riproduzione

```
./scripts/analyze/golden-mining-r2.py [--top N]
```
Read-only, exit 0. La consistenza A per-materia (sez. "24 materie AM") e il
bucket B >=3x/4x sono nelle note del doc, ricavabili dallo stesso script.
