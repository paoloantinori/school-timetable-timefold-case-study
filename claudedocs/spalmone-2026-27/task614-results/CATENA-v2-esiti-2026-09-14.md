# CATENA v2 TASK-614/612 (laptop, 14/09 sera, HEAD 707c8853): stadi 1-2 VERDI, stadio 3 BLOCCATO

## STADIO 1 - suite integrale (heap 12g): VERDE

```
./scripts/core/test.sh -mva "-Dsurefire.heap=12g"
Tests run: 1326, Failures: 0, Errors: 0, Skipped: 65  =>  EXIT 0, BUILD SUCCESS
```

I 9 failure FCC dello stadio-1 della catena v1 sono spariti
(FinalClosingCampaignReviewTest 9/9, CampaignTest 2/2, V2Test 2/2).
Log: suite-614-laptop-v2.log (sul laptop in /tmp; copia .txt accanto non necessaria:
il fix e' di Fedora e la suite e' verde).

## STADIO 2 - step14 con census coda (m39-off, heap 12g): EXIT 0, riproducibile

```
[HRF-debug] reject census: {HRF-H1=3, HRF-VRP=2}
=== TASK-613 step14 ... targets=3 imports=0 quota-swaps=0 moves=0
                        | hard -11 -> -3 (CSF+EDF) -> -3 (holes, delta +0)
```

Lettura (i fatti misurati, per TASK-613):
- **ZERO chiavi HRF-TAIL-***: il ramo quota-swap NON viene mai raggiunto su
  questo banco. Il pass muore sul lato IMPORT: 3 reject H1 (conflitto docente)
  + 2 reject VRP (isSlotLegalFresh). La mia ricostruzione aritmetica post-D2
  ("importer trovato, coda morta silenziosa") NON e' confermata dal census
  esteso: con la coda strumentata, zero valutazioni coda = findImporter ha
  sempre ritornato null.
- Census deterministico: rilancio identico => {HRF-H1=3, HRF-VRP=2} identico.
- ANOMALIA da segnalare (non risolta, non improvvisata): la prima corsa census
  (d276912e, stesso comando) stampava {HRF-H1=2}; oggi {H1=3, VRP=2}. Il diff
  HoleRelayFinisher nel range e' SOLO il census coda (verificato), e la catena
  di step14 (CSF/EDF/SnapshotFixture/test) e' immutata nel range. Inoltre il
  totale di 5 candidati valutati per 3 target e' inatteso (~170 giorni-gruppo
  per target dovrebbero produrre decine di voci MOVABLE o gate): o la
  popolazione scanned e' molto piu' piccola di quanto il modello suggerisce,
  o il census under-counta. Da chiarire su Fedora con un contatore di
  candidati-enumerati accanto ai reject.
- hard invariato: -3 -> -3 (no-op confermato, non-regressione OK).

## STADIO 3 - A/B appaiabile ripetuto: BLOCCATO (file mandato mai arrivato)

`/tmp/mandato-ab614.txt` NON ESISTE sul laptop (verificato: ls /tmp completo +
find nel repo). L'unico file consegnato stasera e' mandato-catena2.txt
(17:56), che RIFERISCE il file ab614 senza contenerlo: laptop-send-mandate
spedisce un file per mandato e l'A/B non e' partito. I criteri a-d e il
controllo crash dedicato vivono solo su Fedora: eseguire un A/B da ~2.5h con
criteri improvvisati viola la regola del mandato. FERMATO e documentato.

Per sbloccare: reinviare il file (laptop-send-mandate dedicato, o push nel repo
sotto claudedocs/ e lettura da qui). Il protocollo TASK-612 (run-measure,
profili freschi, doppia guardia, capture, VERDICT in ab-gate2/) e' gia'
rodato su questo laptop e ripetibile appena arriva la spec.
