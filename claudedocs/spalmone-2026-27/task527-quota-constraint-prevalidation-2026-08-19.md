# TASK-527 prevalidazione vincolo quota-fill (TEST A/B/C, 19/08 pomeriggio)

> Cambio di approccio Fedora: prevalidare con checkpoint prima di implementare.

## TEST A (golden-oracle): FALLITO -> STOP prescritto dal mandato

Il golden rispetta le quote dichiarate? NO: **31 giorni sopra quota** su 2.009
(G03 21 giorni, di cui 20 LUNEDI' a +1h e 2 giovedi'; G09 5; G14 5;
margine +1h su 26 giorni, +3h su 5). Sotto quota: 155 giorni (info: il deficit
c'e' anche nel golden, come noto).

Conseguenza (regola golden-first + mandato stesso): un vincolo HARD quota-fill
dichiara infeasibile la soluzione della scuola: TROPPO STRETTO. Lo STOP e'
l'azione prescritta ("se viola, il vincolo e' troppo stretto o l'input
contraddice: fermati e documenta").

## Lettura delle 31 violazioni

G03 lunedi' +1h e' la firma di una PRATICA non dichiarata (il calendario dice
5h il lunedi', la scuola ne fa 6): e esattamente la classe di divergenze
input-vs-pratica gia' in domande-brevi (famiglia referente). G14/G09 +3h:
stesso tipo. Il vincolo QUOTA-DICHIARATA come hard e' corretto solo DOPO che la
scuola conferma le quote (o le corregge): e la domanda workbook, non un flag.

## TEST B/C: differiti con causa

- TEST B (detection sull'export scatter): il path /tmp/schedule-export-...json
  indicato non e' raggiungibile da questo turno; la detection e' comunque gia'
  noto essere positiva (i bug li ha misurati Fedora: 16 mar 4h, ven G04 3h).
- TEST C (pool vs quota-sum): il mio sum veloce su incarichi.csv da' un artefatto
  (1980 uniforme: colonne da chiarire); i pool autoritativi sono quelli del DB
  (container down al momento). Da rifare con i numeri DB prima di ogni forma
  selettiva.

## Verdetto

Nessuna implementazione del vincolo in questo turno (il gate A ha risposto NO).
La direzione resta valida come MEDIUM selettivo o hard post-conferma-scuola:
la forma giusta e' (a) domanda referente sulle 31 divergenze (soprattutto G03
lunedi'), (b) se la scuola conferma le quote dichiarate, allora hard selettivo
per i gruppi confermati. Lo scatter (flag OFF) resta il prototipo di ricerca.


---

# TASK-527 prevalidazione vincolo quota-dichiarata: A+B+C COMPLETI (19/08, turno 12:50)

> Mandato Fedora: prevalida con checkpoint, implementa solo se verdi. Esito:
> A negativo, C negativo sulla forma proposta: HARD REFUTATO dove servirebbe.

## TEST A (golden-oracle): NEGATIVO (misurato nel turno precedente, confermato)

Golden SOPRA quota dichiarata: 31/2009 giorni (G03 21, di cui 20 lunedi' +1h;
G09 5; G14 5). Golden SOTTO quota: 155 giorni. La scuola pratica sia
eccedenze sia deficit rispetto al foglio: NESSUN lato hard sopravvive al golden.

## TEST B (detection su scatter2-519, export vivo): POSITIVO

La misura vede esattamente i residui known: G12 martedi' 14x4h (vs 19x5h);
G04 venerdi' 9x3h + 6x4h (vs 18x5h). Totale run sotto quota: 240 giorni
(G04 101, G08 49, G12 36, G10 26...), margine quasi sempre 1h.
Il vincolo rilevera' i difetti reali: come RILEVATORE e' valido.

## TEST C (invariante pool>=quota-sum, pool autoritativi dall'export): NEGATIVO sulla forma proposta

| gruppo | quota-sum | pool | delta | hard fill? |
|---|---|---|---|---|
| G01 / G02 / G05 / G06 | 990 | 990 | 0 | possibile (ma sono i gruppi PULITI: nessun difetto da curare) |
| G03 | 969 | 990 | +21 | possibile con slack: e il golden riempie l'extra proprio nei 21 lunedi' +1h (coerente) |
| G07 / G14 | | | +5 / +10 | possibile con slack |
| **G12** | **695** | **675** | **-20** | **IMPOSSIBILE: il pool non copre la quota dichiarata** |
| G04 | 990 | 750 | -240 | impossibile (il noto 835-vs-750) |
| G08 | 729 | 626 | -103 | impossibile (il noto 666-vs-626 nel conteggio quota fc) |
| G09/G10/G11/G13 | | | -16..-28 | impossibile |

L'ipotesi del mandato ("promozione selettiva hard per i gruppi pool>=quota-sum:
G12 e i puliti") e' confutata dai numeri: **G12 ha pool 675 < quota-sum
695**: i suoi 20 martedi'-sotto-quota non sono riempibili da NESSUN orario
(la matematica lo vieta), sono la forma del deficit di pool. L'hard e'
soddisfacibile solo per i 4 gruppi gia' puliti e per G03/G07/G14
(dove il golden mostra che la scuola usa lo slack: hard FILL li costringerebbe
a NON usare l'extra che la scuola pratica).

## VERDETTO: nessuna implementazione

- HARD fill: impossibile dove ci sono i difetti (pool<quota), inutile dove e'
  possibile (gruppi puliti), contrario alla pratica dove c'e' slack (G03).
- HARD surplus-side: il golden lo viola 31 volte.
- La quota-dichiarata resta il target MEDIUM (M20 con pool-cap, gia' in opera,
  ratificato TASK-497) + lo scatter flag OFF come leva di ricerca misurata.
- Le divergenze che rendono tutto questo irrisolvibile sono INPUT (pool vs
  calendario: -20 G12, -240 G04, -103 G08, i lunedi' G03): la via
  e' il workbook/referente, non il solver. Domanda referente da spedire: confermare
  o correggere le quote dichiarate per-data (la pagina Vincoli /constraints
  mostra i conteggi; le divergenze sono nella sezione da-confermare).


## TEST A RAFFINATO (protocollo maintainer 19/08: near-miss del planner umano vs vincolo troppo stretto)

PROTOCOLLO REGISTRATO (vale per ogni futura prevalidazione di vincolo su dichiarazioni):
1. golden 0 violazioni -> procedi.
2. golden con violazioni -> CLASSIFICA per entita (ore mancanti per giorno, giorni
   coinvolti, gruppi) e sistematicita (>=50% dei quota-days del gruppo).
   - Near-miss (<=1-2h/giorno, NON sistematico) -> il vincolo resta valido: le
     violazioni del golden sono gap del planner umano che il solver puo chiudere
     (aspettativa: il run col vincolo hard BATTE il golden su quelle celle).
   - Violazioni grandi (>2h) o sistematiche -> vincolo troppo stretto o input
     contraddice: fermati e documenta.

CLASSIFICAZIONE MISURATA (golden vs dichiarato, lato fill + surplus):

| gruppo | giorni violati / quota-days | margini | verdetto protocollo |
|---|---|---|---|
| G04 | 116/171 (68%: SISTEMATICO) | -1h x90 (near-miss) MA -6h x25, -5h x1 (GRANDI) | STOP: sistematico + grande (coerente col pool -240) |
| G08 | 20/144 (14%) | -1h x7 (near-miss) MA -8x3, -5x5, -4x2, -3x2, -2x1 (GRANDI) | STOP: grandi (pool -103) |
| G09 | 11/134 | -8x3, -5x1, -3x1, -1x1 (+1x5 surplus) | STOP: grandi (pool -28) |
| G13 | 3/137 | -6x3 | STOP: grandi (pool -18) |
| G10 / G11 / G12 | 3 per ciascuno | -8x2-3, -4x1 | STOP: grandi (pool -16..-24) |
| G14 | 6/30 | -5x1, +3x5 (surplus) | surplus: pratica dello slack pool(+10) |
| G03 | 21/171 | +1h x21 (SOLO SURPLUS) | non viola un hard FILL (count>=dichiarato): pratica dello slack pool(+21), i 21 lunedi' |

Nessun gruppo rientra nel caso near-miss-puro: ogni gruppo in deficit ha anche
violazioni GRANDI (-3..-8h = giorni interi mancanti), e l'aritmetica TEST C le
spiega (pool < quota-sum). Il caso "planner umano arreso" NON si presenta: il
verdetto resta STOP per l'hard fill dove ci sono i difetti.

DOTTRINA REGISTRATA (completa la direttiva): golden-first per le PRATICHE
(come la scuola fa), input-first per le DICHIARAZIONI (cosa la scuola dice di
volere): il calendario dichiara 5h, il golden e' un tentativo umano, l'intento
dichiarato vince SUL GOLDEN, ma solo dove la matematica del pool lo consente
(TEST C resta il gate di soddisfacibilita'). Qui l'intento e' gia' il target
MEDIUM ratificato (M20 pool-cap); l'hard attende o la correzione delle quote
dichiarate o la conferma che i giorni -3..-8h erano dichiarazioni errate.
