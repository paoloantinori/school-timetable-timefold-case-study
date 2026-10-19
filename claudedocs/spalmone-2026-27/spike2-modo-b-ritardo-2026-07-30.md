# SPIKE 2 - Modo B del ritardo (front-gap a fine costruzione) - 2026-07-30 notte

## Consegna (3 righe)
- **Ipotesi:** le 3/24 giornate di G11 gia' in ritardo profondo a fine costruzione (front-gap 2
  slot, modo B) sono un meccanismo DIVERSO dal late-start da slide (modo A) e con cause proprie
  (docente unico, quota bassa, stage adiacente, calendario stretto).
- **Numero misurato:** modo B (front-gap, prima lezione alle 10+) = **5 giorni** nel dump
  costruzione test-harness, di cui **2 su G11 mercoledi' (2027-05-19, 2027-05-26)**, prima lezione
  alle 11 (Lingua Italiana/T04). Su quei giorni T04 e' conteso a slot0 (h8->G02,
  h9->G02, h10->G03) e G11 lo ottiene solo h11-13. **Quota NORMALE (5h, non quota-bassa)**;
  pool docente G11 a **maggio = 8 vs 12-13 a ott/nov**. Nel solve reale (30b phase-2) G11 mer e'
  ancorato@8 tranne 05-19@9.
- **Tenere: BUTTARE come leva separata.** Modo B non e' un meccanismo autonomo: e' contenzione slot0
  di FINE ANNO (stessa famiglia del difetto principale), amplificata dal pool docente residuo piu'
  sottile a maggio. Non si riproduce nel solve reale. Si ripiega in TASK-457 (dinamica fine-anno) e
  nella cura coordinata (SPIKE 1), non giustifica una leva di costruzione deddivcata.

## Caratterizzazione (dump costruzione test-harness `target/construction-lessons.txt`)
Front-gap days (prima lezione slot>=2, excl G14): G11 mer x2 (2027-05-19, 05-26), G04 gio
(2027-04-01), G09 gio (2027-06-03), G13 ven (2026-12-04). Tutti a FINE ANNO (apr-giu).

G11 mercoledi' 2027-05-19 (esempio, = 05-26):
- sole 3 lezioni piazzate: T04/Lingua Italiana a h11,12,13. slot 8-9-10 vuoti.
- T04 quel giorno (tutti i gruppi): h8->G02, h9->G02, h10->G03, h11-13->G11.
  Quindi G11 ottiene T04 solo dopo che ha servito G02 e G03 a slot0-2.
- Quota attesa G11 quel giorno = 5h (aula), UGUALE al mid-year: NON e' un giorno a quota bassa.

## Cosa ESCLUDE le ipotesi dell'utente
- **Docente unico?** No. G11 ha 8 docenti attivi a maggio (T04, T05, T06, T08,
  T11, T12, T23, T24). Ma a fine anno il pool e' piu' sottile (8 vs 12-13 di ott/nov).
- **Quota bassa?** No. Quota 5h, normale.
- **Stage adiacente / calendario stretto?** Non pertinente: G11 non e' un gruppo FC/split e
  maggio non e' periodo di stage per G11.

## Cosa e' davvero
La STESSA contenzione slot0 del difetto principale (modo A), ma a fine anno dove il pool di docenti
con ore residue per G11 e' piu' sottile: e' piu' difficile trovare un docente libero a slot0, e
quello con ore residue (T04, condiviso con G02/G03) e' gia' preso a slot0 -> G11
scivola a inizio-tardivo profondo. E' un'istanza della dinamica tier-drift (generici spesi presto ->
residuo scarso a fine anno, TASK-457), non un meccanismo di costruzione separato.

## Perche' NON prioritarlo
1. Non si riproduce nel solve reale: 30b phase-2 (costruzione reale, flag OFF) ha G11 mer
   ancorato@8 (tranne 05-19@9). Il solve reale (input completo + stage pin + LS) assorbe il modo B.
2. E' raro (5 giorni su ~2000) e concentrato a fine anno.
3. La cura coordinata (SPIKE 1) lo coprirebbe indirettamente (pre-assegnare l'ancora slot0-1 toglierebbe
   a T04 la liberta' di far scivolare G11 a h11).

## Domanda aperta (scritta, vado avanti)
Il modo B scompare nel solve reale ma NEL TEST HARNESS no: il test harness (bare construction, senza
stage pin ne' import-calendario) ha una costruzione piu' nuda che lascia il modo B visibile. Se si
vuole usare il test harness come proxy di costruzione, va dichiarato che sottostima la qualita' della
costruzione reale. Non blocca niente.
