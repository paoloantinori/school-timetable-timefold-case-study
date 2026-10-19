# PROTOTIPO CURA TASK-613 (test-side nel banco): DFS teacher-aware, -3 -> 0 su m39-off

Mandato Fedora 15/09. Copia HoleRelayFinisherDiff (da 262cc255) + step21
(env PLANNER_TEST_CHAIN_CURE=1). HEAD 116cd20a, heap 12g, EXIT 0 entrambe.

## La cura prototipata (3 punti, nel banco)

1. Verifica simbolica DENTRO la DFS (nessuna applicazione):
   (a) HRF-SYM-HOLE: nessuna lezione della catena col docente della CODA puo'
   atterrare su holeTs (lo slide della coda va lì);
   (b2) HRF-SYM-CODEST / -PARENT: nessun two-movers stesso-docente stessa-dest
   (nel batch o nella catena genitore);
   (b) il bloccante vero: l'occupante pre-stato di dest con lo stesso docente
   di m e' permesso SOLO se e' esso stesso in movimento (batch o catena);
   altrimenti ricorsione su di LUI (niente esenzione a pioggia del batch:
   era il difetto v0.4/v0.5, l'inserzione deposita docenti nell'ora critica).
2. Backtracking: i `to` cattivi ora muoiono DENTRO il loop della DFS e il loop
   prosegue col prossimo `to` (prima la catena "riusciva" simbolicamente e
   moriva solo in verifyComposite, senza ritorno sui fratelli).
3. Misura contro il bersaglio.

## Risultati

m39-off: **-3 -> 0** (slides=3 rotations=14 moves=17), verifyComposite VERDE
(zero reject compositi), zero famiglie hard residue.
- D1 costruita (NON le 13 cieche: una EQUIVALENTE PULITA trovata dalla DFS):
  `G14 8<->9 (swap), G10 3-ciclo {9->10,10->11,11->9}, G02 swap
  {11->12,12->11}, slide G11 12->11`.
- D2: la catena v0.2d (G08 swap + slide G13). D3: G03 3-ciclo + G04
  swap + slide G13 13->12.

m39-on (cross-basin, non-regressione): **-6 -> -4** (meglio del -5 di v0.2d):
chiude 2 buchi (cascata G11 locale: G06 3-ciclo + G03 3-ciclo + slide;
+ l'G13 G08), residuo H17 x2 + H32 x1 + H7 x1. Improve-or-noop intatto.

Census del backtracking al lavoro: m39-off {HRF-SYM-HOLE=3, HRF-VISITED=139,
HRF-H1=3, HRF-SYM-CODEST-PARENT=44, HRF-DEPTH=116}: i SYM-* sono i `to`
scartati simbolicamente che prima arrivavano al composito e lo sporcavano.

## Per il porting di domani (Fedora)

- I tre check vivono nella DFS della copia: segnaposto CUR_HOLE_TS /
  CUR_TAIL_TEACHER da rendere parametri veri.
- La verifyComposite resta come cintura finale (ora passa).
- Nota alfabeto: la DFS raw-axis ha trovato equivalenti puliti SENZA
  inserzione compatta (i giorni coinvolti sono quasi contigui); se servisse
  esprimere i 3-cicli ciechi alla lettera, l'inserzione compatta (fra slot
  OCCUPATI) e' la forma esatta dei blocchi ciechi (verificato in analisi).
- Nessun src/main toccato; nessuna adozione: il prototipo vive nel banco.
