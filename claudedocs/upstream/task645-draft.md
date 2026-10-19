# TASK-645 bozza flagship: ViolationScopedMoveProvider

STATO: SCHELETRO INTERNA. Nessun invio senza revisione e approvazione di
maintainer (direttiva 23/09). Aggiornato col setaccio 23/09: lo spazio upstream
e' LIBERO, c'e' un invito esplicito (#891) e un vincolo di forma preciso
(#2582/#2610). Quadro delle sorelle: la 643 e' diventata bozza discussion,
la 644 e' chiusa (l'accessor tipizzato esisteva gia'), quindi la flagship
non aspetta piu' il rapporto delle due minori. Testo inglese da completare
quando l'A/B 641 e' terra.

## Posizione (cosa chiediamo, in una frase)

Un modo supportato per generare mosse ONLY attorno alle entita' giustificate
dai constraint match hard-negativi (piu' il loro contesto di pattern), invece
di campionare tutto lo spazio.

## Le ancore upstream del setaccio (verificate 23/09)

- SPAZIO LIBERO: nessuna issue o PR upstream su violation-guided move
  selection. Nessuno ci ha preceduto.
- INVITO ESPLICITO nella issue #891 (Guided Local Search, aperta 2024). Due
  citazioni verbatim: triceo "PoC pull requests are welcome!"; ge0ffrey "The
  Indictment API is a building block of building Guided Local Search some
  day".
- VINCOLO DI FORMA: la preview Neighborhoods va verso l'iterator paradigm
  (#2582) e le coarse-grained generic moves (#2610). La proposta DEVE
  attestarsi li': niente estensioni ai move-selector XML.
- PoC in preparazione sul fork: branch `poc/violation-scoped-move-provider`.

## Perche' oggi non si puo' (la conferma loro)

Il team ha confermato che i move selector enumerano liste statiche di
entita'/valori per performance: per-proposal costerebbe un score. Non c'e'
API per indicizzare la selezione per constraint match. Risposta rintracciata
su StackOverflow (setaccio della ricerca 22-23/09).

## L'evidenza nostra (i numeri della discussione)

- Contesto: school timetabling, ~12.500 lezioni, BendableScore, decine di
  vincoli hard/medium, delivery chain deterministica post-LS.
- Il meccanismo (production, ratificato): score con ConstraintMatchPolicy
  ENABLED -> target = lezioni dei match hard-negativi + lezioni CONTESTO dei
  pattern (la scoperta chiave: la cura di una violazione A..B..A sta sulla
  lezione di mezzo, che NON e' un fatto del match) -> Change/Swap solo attorno
  ai target -> steepest con valutazione incrementale executeTemporaryMove,
  0,05ms/eval -> improve-or-noop sull'hard pieno.
- Risultati: chiude residui COMPOSITE_MISSING che la LS non chiude (nessuna
  singola mossa migliorativa esiste e nessuna LS gira sullo stato consegnato,
  diagnosi con esperimento cieco + arbitrato). La stessa cura trovata a mano
  nel blind puzzle e' stata applicata in produzione dal pass in 414ms. A/B
  pre-registrato 5/5. Catalog sweep su stati storici.
- Costo: il pass gira a consegna su residui 0..-3: 0,4-4s tipici, quindi il
  per-proposal score cost e' accettabile ESATTAMENTE dove serve (fine ciclo,
  residuo piccolo, budget di secondi). E' il trade che chiediamo di esporre.

## La forma proposta (riequipaggiata sulle ancore del setaccio)

- Forma primaria: un coarse-grained generic move nella direzione #2610,
  esposto come iterator (#2582): genera mosse solo attorno alle entita'
  giustificate dai match hard-negativi di un set di constraint id, con
  l'opzione "contesto di pattern" (le entita' in mezzo ai pattern joinati).
- Alternativa piu' piccola: un move iterator factory che accetti un
  `Predicate<ConstraintMatch>` o un set di constraint id piu' una funzione
  target-extractor; la forma finale la decidono loro.
- Quel che NON proponiamo: estensioni ai move-selector XML (il perche' e' la
  conferma qui sopra: enumerano liste statiche per performance) e la
  variante custom phase "violation-directed descent", che si propone SOLO se
  la discussione la chiede.

## Secondo data point (pendente: A/B TASK-641)

La grammatica si estende naturale ai compositi multi-variabile (timeslot +
room via Moves.compose): stesso oracolo, stesso costo per eval. Chiude la
classe "la mossa timeslot esiste ma le stanze viaggiano con le lezioni e
collidono". Esito dell'A/B in volo; se positivo, un paragrafo in piu'; se
parita', resta un paragrafo di estensibilita'.

## Materiale d'appoggio pronto

- Report ricerca con i precedenti letterari (GLS, QuikFix, fix-and-optimize,
  strategic oscillation): claudedocs/research_violation-directed-repair_2026-09-23.md
- Il reference implementation: ViolationScopedSteepestFinisher (production).
- Banco riproducibile: OffCapture638BenchTest mode=steepest.
- Il PoC sul fork (branch `poc/violation-scoped-move-provider`) appena
  pushato: e' la risposta concreta all'invito di #891.

## Regole di ingaggio (nostre, interne)

- Inglese, /pstack:unslop, prima persona singolare nel nome di maintainer.
- Porta d'ingresso naturale: la issue #891 e' APERTA con l'invito esplicito
  di triceo ("PoC pull requests are welcome!") e ge0ffrey nomino' l'Indictment
  API come building block di GLS: entrare citandola, non fingendo di scoprire
  lo spazio adesso.
- Aspettarsi iterazione; il rapporto aperto via PR 2610 comment e' il
  precedente di rapporto. NON citare il PATAT (649) se non serve.
- La forma sta nell'iterator paradigm (#2582) e nei coarse-grained generic
  moves (#2610): se la discussione spinge verso move-selector XML, declinare
  e riattestarsi li'.
- Ogni risposta upstream viene mostrata a maintainer prima di essere inviata.
