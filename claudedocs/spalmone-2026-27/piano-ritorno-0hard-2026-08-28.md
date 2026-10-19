# Piano: ritorno alle soluzioni vicine allo 0hard + golden a 0hard (28/08)

Obiettivi misurabili: (G1) golden = 0hard sotto il vincolame corrente (oggi -1: cella G09 04/06);
(G2) consegne nostre <= -2hard in modo sostenuto, con tentativi 0hard notturni (oggi -16/-17).

Principio guida: la chiave di volta e' ARITMETICA (verita' dell'input), non piu' ricerca.
Il exact-cover di G09 e' una domanda che il foglio formula e la scuola stessa non ha soddisfatto:
il golden e' la prova. Ogni passo sotto e' gia' misurato o misura una sola incognita.

## FASE 0 (oggi, in corso): VERDICT del gate cycle v2
Il ciclo TUTTO ON + pre-booking v2 in corsa arbitra end-to-end il trade (H34 8->1 contro
H32 -10/H17 -16/tier -27 misurati in localizzato). Esiti:
- netto-positivo o neutro con H34 molto migliore -> v2 CANDIDATO ratifica (decisione maintainer).
- netto-negativo -> v2 resta prototipo; si passa al RAMO B (sotto).

## FASE 1 (NON attende referente, direttiva maintainer 28/08: precedente TASK-550)
Il golden pratica 662h su griglia e lascia vuoto il 04/06: SI ASSUME IL GOLDEN ora.
- Cura input immediata (SOLO seed CSV + expectedUnscheduledHours, mai DataGenerator):
  drop della dichiarazione delle 4h del 04/06 G09 (o dell'intera riga se quota=4),
  +4h in Course.expectedUnscheduledHours di G09 (meccanismo TASK-550: domanda fantasma).
- Re-pin golden: GoldenSolutionRegressionTest H34 atteso 0hard (era -1), H0 atteso 0
  (le 4 lezioni non piazzabili ora attese). G1 CHIUSO.
- Notifica a posteriori nel pacchetto referente (la domanda #3 diventa: "abbiamo assunto il
  pool praticato 662 e corretto il foglio; confermate o le 4h erano reali?"). Se referente
  risponde "reali": revert della cura (una riga + un numero), nessun'altro impatto.
- DERIVA da verificare subito dopo la cura: la v2 pre-booking non riservera' piu' il 04/06
  (cella fuori scope) e la conservazione G09 si chiude a 662=662: atteso H32 di G09
  piu' pulito, nessun nuovo boxed (le 4 lezioni ora attese).

## FASE 2 (G2: consegne vicine allo 0) - RAMO A se v2 sara' ratificato
Il debito H32/H17 della v2 e' il prezzo delle prenotazioni: si riduce con tre rifiniture,
ognuna banco localizzato + UNA sola ciclo di conferma a fine serie:
1. PARITA' GOLDEN sulle prenotazioni (TASK-517 applicato a v2): placement-parity-check sul
   piano di riserve: nessuna riserva piu' rigida della pratica golden sulla stessa cella.
2. RISERVA QUOTA-AWARE: dimensione min(quota cella, surplus realmente disponibile), non fissa.
3. H17-DEBT SOURCE: censire quali riserve rompono blocchi docente-giorno (le 16 celle H17):
   escludere le date la cui riserva costa un blocco (la cella rara vale meno del blocco).
Ordine: 1 -> 2 -> 3 (la piu' potente prima: la parita' golden taglia le riserve infondate).
Target dopo rifiniture: H34 residuo <=2, H32 nuovo debito ~0, H17 tornato ai livelli baseline.

## FASE 2 - RAMO B se v2 sara' bocciato
Senza v2: EDF v0.1 (4 celle) + QFR v2 + multi-start CON screening A5 short-attempt
(P(0hard)/wall: piu' tentativi a notte) porta le consegne a ~-8..-10, NON vicine allo 0.
L'estensione strutturale onesta del ramo B e' il quasi-hard (TASK-538 BendableScore:
H34/H32-family in un livello fra hard e medium, cosi' la LS tratta invece di murarsi).
Migrazione grossa: si apre SOLO se il ramo A muore, con gate maintainer esplicito.

## FASE 3 (verifica G1): dopo cura input (se referente)
GoldenSolutionRegressionTest atteso 0hard (re-pin -1 -> 0 con commento della causa);
suite completa verde; registro aggiornato. La finestra che si chiude: questa verifica e'
possibile solo finche' il golden esiste, va fatta SUBITO dopo la risposta.

## FASE 4 (accettazione G2): sanita' e metrica
Ogni ciclo di conferma: sanity per-gruppo obbligatoria (un rosso = non conforme), viz letti,
metro P(0hard)/wall nel verdetto. Con G1 chiuso + ramo A rifinito: le famiglie hard residue
sono le vecchie (H0 boxed: ExactFinisher deterministico; H17: multi-start): 0hard diventa
di nuovo un evento notturno probabile, non una caccia.

## Proprieta'
- maintainer: spedire pacchetto referente (F1, bloccante per G1); ratifiche v2 e eventuali curve (F0/F2).
- laptop: banchi F2 (1-2-3), ciclo di conferma finale, tutto con predizione PRIMA.
- FEDORA (io): verifiche sui raw, merge, re-pin golden F3, quadri.
- Sequenza realistica: F0 oggi; F1 quando maintainer spedisce; F2 domani; conferma day+1;
  con risposta referente entro la settimana: G1 in un pomeriggio, G2 entro day+3.
