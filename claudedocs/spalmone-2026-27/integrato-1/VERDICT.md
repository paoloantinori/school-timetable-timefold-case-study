# VERDICT integrato-1: il ciclo fresco con la catena COMPLETA ratificata (d6ab4fd5)

Profilo fresco integrato-1, default di main (bundle + 619 + 620 tutti ON per
ratifica, NESSUN -D oltre il protocollo), banner-check cwd+commit d6ab4fd5,
run-measure + capture + doctor. Meta-bacino: tutti-condannati come le ultime
tre consegne fresche (attempt-1 CH-only rifinito, attempt-2 girato e scartato,
restore del best, durata ~3h53m). Zero IllegalStateException, doctor concorde.

## CONSEGNA: [-4/0]hard = H17 -2 (1 match doppio) + H28 -1 + H7 -1

Predizione (registrata PRIMA in CRITERI-PREDIZIONE.md): "hard finale 0 o -1...
SE peggio di -2: qualcosa non e' come i banchi dicevano: cattura TUTTO e
fermati." La consegna e' -4: fermo, cattura, verdetto. La suite finale NON e'
stata corsa (fermata per regola del mandato; e' la certificazione in coda al
prossimo giro).

## (1) Census delle righe [delivery]: le braccia SONO SCATTATE

```
EmptyDayFinisher [delivery] (relay=true h32=true): 0 moves, 0 cells, 0 conflicts, 0 swap-compound
HoleRelayFinisher [delivery] (v0.6 teacher-aware): targets=6, slides=4, rotations=2, moves=9
```
- CSF/EDF/v2-swap/tol-donor: INERTI alla consegna (0 mosse, 0 celle: il bacino
  non aveva fill-residual da curare a quel confine). Non e' anomalia: e' il
  no-op certificato del re-A/B.
- HRF: 6 buchi alla consegna, 4 CHIUSI (moves=9). Le braccia-buchi hanno
  lavorato DURO (slide/compact-swap/import/DFS mescolati nel conteggio: 4
  chiusure su 6 target). RESTANO 2 slot H17 (1 match di peso 2) + H28 -1 +
  H7 -1.
- h7cap (620): e' il SECONDO LOOP dentro la stessa chiamata HRF: nessuna riga
  dedicata nel log (il census HRF-H7CAP-* stampa SOLO sotto -Dhrf.debug, non
  armato in questa corsa protocollo-puro). L'H7 -1 residuo dimostra che NON ha
  chiuso: il PERCHE' non e' osservabile in questo log (vedi punto 3).

## (2) Confronto con la predizione: -4 atteso 0/-1

- La banda di variabilita' GRASP documentata e' ±5 (613: A3 -14 vs B3 -9 con
  pass inerti; endgame: -2 vs -7 pre-cura): -4 vs -1 atteso e' DENTRO banda.
- La firma e' IDENTICA al braccio B dell'endgame A/B pre-cura (H17+H7+H28:
  quella consegnava -7) e al braccio A/B post-cura che consegnava -1: cioe'
  questo bacino e' della CLASSE che porta H7+H28 alla consegna. Con -4 il
  quadro e' coerente: i pass sono scattati, hanno lasciato il residuo della
  loro classe di bacino.
- La domanda giusta (da mandato): "i pass sono scattati e cosa hanno lasciato?"
  Risposta: scattati sì (4/6 buchi chiusi alla consegna), lasciato H17x2 +
  H28x1 + H7x1.

## (3) Perche' l'H7 residuo non l'ha chiuso il 620: NON OSSERVABILE in questo log

Il census HRF-H7CAP-* richiede -Dhrf.debug (non armato: corsa protocollo-puro a
default). Le ipotesi in campo (da verificare col replay in banco, NESSUNA
misurata qui):
- NO-CANDIDATE/DONOR-TOL: l'overcap specifico (docente/giorno di QUESTO bacino)
  non ha un paio legale (niente coda stesso-gruppo sotto quota, o niente
  donatore tollerante tail-only);
- gates: EXPORT-VRP o H1 sull'unico candidato;
- forma non coperta: overcap conexport mid-day + donatore coda (la forma
  branch-aware della cura ba694e1b copre prima-ora e mid-day, ma il path mid-day
  col yield tollerante potrebbe non trovare donatore).
La via concreta: replay step40-style sull'export di questa consegna con
-Dhrf.debug: il census dira' quale gate. E' il primo item del prossimo giro
(alimenta direttamente la famiglia 620 o il prossimo round cieco).

## Batteria (completa comunque, per il record)

- sanity: NON CONFORME 4 rossi (G07, G08, G09, G13) - in banda della
  classe condannata (4-5 rossi nelle consegne fresche recenti);
- full-day 94,7% (banda 94,2-94,7: al top);
- ricorrenza unpinned 70,1% (banda classe 68-70);
- doctor: hard concorde -4/-4, WARN export 106MB (locale).

## VERDETTO (tre righe)

1. IPOTESI: catena completa a bordo, consegna 0 o -1 (0-hard probabile col
   multi-start N=5).
2. MISURATO: -4 (H17x2 + H28 + H7) su meta-bacino tutti-condannati: firma
   IDENTICA alla classe endgame-pre-cura, dentro la banda GRASP ±5, con le
   braccie ATTIVE alla consegna (4/6 buchi chiusi) e i fill inerti come da
   certificazione. L'H7 residuo NON osservabile nel log (census dietro debug
   flag non armato): il -4 atteso-0/-1 va attribuito a bacino finche' il
   replay col census non dice quale gate ha fermato il 620.
3. RACCOMANDAZIONE: replay step40 su QUESTO export con -Dhrf.debug (census
   HRF-H7CAP-*): decide se il -4 e' bacino (il paio non esiste qui) o un gap
   della famiglia 620 (gate troppo stretto). Nessuna nuova corsa integrale
   prima di quel dato. La suite finale su d6ab4fd5 resta in coda.
