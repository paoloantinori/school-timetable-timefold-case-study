# FCC cure (isSlotLegalFresh) vs catena Grid#cellOk: confronto e verifica anti-loop

Lettura-solo su HEAD 6ef98e6f (la cura di Fedora non e' nel repo: valutata nella
forma descritta dal mandato). Nessun tocco a src/.

## 1. Cosa controlla PureHoleRepair.Grid#cellOk (righe 272-302)

In ordine: (a) H1 teacherCell live-index (non-pseudo, con ignoreA/ignoreB);
(b) H5 come **set di DATE per docente**, costruito SOLO dai record
`AvailabilityType.VACATION` (SICK ESCLUSO dal filtro a riga 163, pseudo
esclusi), espansi giorno per giorno fra start ed end; (c) H3 cella gruppo
(con ignores); (d) conflitto room-cell se la lezione porta gia' una stanza;
(e) **`isSlotLegalFresh(ts)` come ULTIMO check** (commento TASK-578 "VRP,
evaluated last").

## 2. Lesson.isSlotLegalFresh (gia' censita nel walk D1)

Gli 8 filtri (H6.1 holiday, H5.1 indisponibilita' docente per OVERLAP
datetime su VACATION **e SICK**, H4.1 stage blackout, H3 pinned-cell, H20
calendario, L1-bis quota permits, stage-confinement, no-session) + il
grandfather: `ts.equals(timeslot)` ritorna true. Per un landing FCC il target
e' sempre diverso dallo slot di partenza della lezione mossa => il grandfather
e' inerte e la valutazione e' piena. Le statiche che i filtri consultano sono
pubblicate pre-solve e immutate al confine FCC (post-multi-start, stesso
thread/solve): il gate vede gli stessi fatti del live.

## 3. Differenze semantiche per un landing FCC

- cellOk INCLUDE isSlotLegalFresh: cellOk e' un sovrainsieme. La cura
  isSlotLegalFresh-only non puo' far passare nulla che cellOk rifiuterebbe sul
  piano VRP/H5: e' lo stesso link decisionale, preso da solo.
- I link extra di cellOk sono ridondanti per FCC: H1 lo possiede gia'
  (teacherBusy index), la cella gruppo e' il buco stesso (libera per
  costruzione), la room viene nullata sui cross-day (nullRoom).
- Unico caso "cellOk passa, isSlotLegalFresh rifiuta": IMPOSSIBLE (e' l'ultimo
  check di cellOk). Il viceversa esiste ed e' a favore della cura: il
  date-set di cellOk copre solo VACATION e solo a granularita' giorno; H5.1 di
  isSlotLegalFresh e' overlap datetime e include SICK. La cura e' quindi
  STRETTAMENTE piu' fine su H5 della parte (b) della catena Grid.
- Grandfather (la cautela del mandato): non e' un buco. Il gate valuta lo slot
  TARGET, mai lo slot di partenza; lo slot di partenza non torna legale "per
  altra via" perche' il gate non lo consulta affatto.

## 4. I due punti di inserimento e il rischio anti-riproposta

Struttura rilevante (FinalClosingCampaign HEAD): findDonor filtra
`movedDonors.contains(cand)` e i candidati stesso-giorno, poi per ogni
candidato check H1 (`refusedByDirection++; continue` DENTRO il loop dei
candidati), ultima-ora-del-giorno-donore, H28, costi quasi/budget. Il while
esterno gira solo su `changed=true` (una mossa applicata).

- **Nessun loop infinito possibile in nessuna delle due forme del gate**: un
  donore rifiutato con `continue` non setta `changed`, il while termina, e
  `findDonor` riparte solo per il buco SUCCESSIVO (dove il riuso dello stesso
  donore e' CORRETTO: il VRP e' per-slot e un altro target puo' essere legale).
  Il non-aggiornamento di movedDonors/donorDay per un rifiutato e' giusto:
  non e' stato mosso nulla, lo stato e' as-was.
- Pecca di EFFICACIA (non correttezza) forma (a), pull-up: se il gate-fail fa
  `continue` PRIMA del ramo v2, il buco perde anche l'opzione donore, che e'
  indipendente dal VRP del mover (il pull-up bloccato non pregiudica un import
  legale di ALTRO materiale). Meglio: gate-fail del pull-up => prosegue verso
  il ramo v2 (fall-through), non continue dell'intera iterazione.
- Pecca di EFFICACIA forma (b), donore: se il gate sta DOPO findDonor (sul
  donore ritornato, `rejected++; continue`), il buco resta aperto anche se un
  candidato SUCCESSIVO era legale. La forma equivalente al check H1 gia'
  presente (gate DENTRO il loop di findDonor: `!cand.isSlotLegalFresh(targetTs)`
  => refusedByDirection++; continue) chiude il buco col primo candidato legale
  e rende il contatore rejected-by-direction omnivoro (H1 e VRP sono entrambe
  "direction-true" refusali).

## Verdetto

1. `isSlotLegalFresh` BASTA: e' letteralmente l'ultimo e decisivo check della
   catena Grid#cellOk, i link aggiuntivi di cellOk sono o gia' posseduti da FCC
   (H1, cella, budget) o piu' deboli (date-set VACATION-only vs overlap
   datetime VACATION+SICK). Non serve importare la catena Grid.
2. Nessun rischio di loop: la forma corretta del gate donore e' DENTRO il loop
   candidati di findDonor (come l'H1 gia' presente), non dopo il ritorno; e il
   gate-fail del pull-up dovrebbe fare fall-through al ramo v2, non `continue`
   dell'iterazione, per non sottrarre al buco l'opzione donore.
