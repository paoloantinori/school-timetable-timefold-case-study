# PREDIZIONE re-certifica cf936bcc (Fedora, pre-registrata; laptop la riporta PRIMA delle corse)

> step40 transfer (predizione: -1 -> 0 INALTERATO, stessa coppia: i gate nuovi
> non toccano il path misurato; uniche novita' possibili nei census
> HRF-H7CAP-EXPORT-H7/SOURCE-YIELD a 0), inerzia m39 (pinnuovi), suite.

Esito misurato: VEDI SOTTO.

## ESITO MISURATO: DIVERGENZA. FERMATO al primo stadio come da regola

- step40 transfer: hard **-1 -> -1** (moves=0): la coppia NON viene piu' applicata.
- Census: {HRF-H7CAP-SOURCE-YIELD=7, HRF-H7CAP-NO-CANDIDATE=1}: il nuovo gate
  classe-616 rifiuta TUTTI e 7 i candidati export. HRF-H7CAP-EXPORT-H7 non
  compare (a 0 come previsto, quello e' a posto).
- La predizione ('gate nuovi non toccano il path misurato, SOURCE-YIELD a 0')
  e' VIOLATA: il SOURCE-YIELD e' la causa dell'inerzia del braccio.

## Diagnosi laptop (da confermare da Fedora, proprietario del gate)

Conflitto semantico fra il gate classe-616 e la FAMIGLIA 620: il gate valuta il
yield del giorno SORGENTE su OGNI esportazione con l'aritmetica del 619 (il
sorgente e' un donatore vero che deve poter cedere). Ma nel paio 620 il
"sorgente" e' il GIORNO OVERCAP = il BERSAGLIO della cura: sta tipicamente a
quota (non in surplus), cede un'ora al paio e la RIOTTIENE dal donatore
tollerante (il conteggio finale torna pieno). Il gate pre-paio lo condanna per
uno stato intermedio che il paio risolve: e' la stessa classe di conflitto
H31-per-esportazione vs H31-final-state che la specifica 620 aveva gia'
dichiarato e risolto FINAL-STATE. (Dati: G09 06-03 quota 5, ore 5, export
@8 -> 4 ore: linea strict 4 < 5 REJECT; linea tollerante 4 >= 5-1 PASS; e il
donatore ripristina comunque a 5.)

## Stato

FERMATO prima di inerzia e suite (regola stop-al-punto-divergente). Il branch
NON e' pronto per merge cosi': la decisione e' di Fedora (per esempio gate
620-aware: yield tollerante sul sorgente-overcap, o valutazione final-state
del paio).
