# m-13 - Chiusura procedurale del path input APPROVATA (maintainer, 2026-08-11)

**DECISIONE maintainer (2026-08-11): APPROVATO.** Il multi-start (TASK-483, ratificato DEFAULT ON)
e' accettato come chiusura procedurale del path source=input per il gate m-13
(genericita'/riuso multi-anno). 0-hard e' consegnato come esito **statisticamente garantito**
(pick-best su N tentativi), non deterministico per costruzione.

## Dato che regge la decisione

Bad-seed frequency N=32 (pooled, due batch laptop, config corrente con M-TC per-retry ON):
- **p(0-hard) = 32/32 = 1,0**, Wilson95 ~[0,89, 1,000]. Bacino H17 NON uscito in 32 costruzioni.
- P(>=1 0-hard | N=3 tentativi) > 0,999. Wall medio ~27 min/costruzione.
- Raw: `task480-results/badseed-frequency-laptop-2026-08-09/` (N=16) + `...-seeds17-32/` (N=16 ext).
- Verdetti: `VERDICT-badseed-frequency-laptop-2026-08-09.md`, `VERDICT-badseed-n32-pooled-2026-08-10.md`.

## Cosa significa (il gate m-13)

m-13 era BLOCCATO sul path source=input: good seed 0hard, bad seed -6 H17 (G11 mercoledi
ore 10). 0-hard e' BINARIO e BLOCCANTE end-to-end (direttiva maintainer 2026-08-02). La domanda era:
accettare la chiusura procedurale (0-hard statisticamente garantito via multi-start) o
inseguire una cura deterministica/strutturale dell'H17.

**Risposta: accettata.** La cura deterministica dell'H17 e' fuori portata (TASK-481/482:
shell-game depth-3, nessuna riparazione locale converge; CompositionHoleCloser resta
committed default-OFF come building block corretto ma inerte sul bad-seed noto). Il multi-start
e' la procedura ratificata che seleziona il bacino buono, e su n=32 non ha mai fallito.

## Caveat onesto (da registrare)

- **Statistically guaranteed, NON deterministic per costruzione.** Una singola costruzione
  GRASP ha p(0-hard)~0,75-1,0 (varianza LS NON_REPRODUCIBLE); e' il multi-start N=3 che porta
  P(>=1 0-hard) > 0,999. La direttiva 0-hard-binario e' rispettata OPERATIVAMENTE (la procedura
  consegna 0hard), non per-costruzione.
- L'H17 resta LS-probabilistico: n=32 clean non lo esclude al 100%, ma lo rende raro + coperto
  dal multi-start N=3.
- Il bad-seed frequency e' within-machine (laptop sola). Cross-environment (Fedora al rientro)
  servirebbe a generalizzare, ma la config e' la stessa, quindi risultato atteso coerente.

## Cosa cambia

- **m-13 si sblocca** (era BLOCKED on source=input H17). Il path input e' ora considerato
  chiuso proceduralmente.
- **Source DEFAULT resta golden** (decisione ready-when-needed invariata): il path input e'
  viable + proceduralmente chiuso, ma il golden resta il default (medium migliore + vantaggio
  test finche' la finestra e' aperta). Flip ready-when-needed.
- **NESSUN cambio al codice solver**: il multi-start e' gia' DEFAULT ON (TASK-483/486). Questa
  e' una decisione di gating (accettazione del significato), non un'implementazione.

## Per Fedora (review indipendente)
Verifica il dato (n=32, 32/32 0hard) dai raw. Se confermato, ratifica la decisione di maintainer.
Se il dato non regge (es. un bad-seed sfuggito), m-13 torna blocked.
