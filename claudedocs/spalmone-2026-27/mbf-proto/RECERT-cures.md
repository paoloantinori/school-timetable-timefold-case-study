# RE-CERT wt/mbf-cures (b2df358f) sul laptop, 17/09 notte

Braccia richieste: REPLAY STATIC (export integrato-1) + GOLDEN.

| braccio | pre-cure (78a07f698) | post-cure (b2df358f) | predizione Fedora |
|---|---|---|---|
| replay statico hard | -4 -> -4 | **-4 -> -4** | identico: CONFERMATA |
| replay statico bill medium | -142.800 | **-142.800** | identico: CONFERMATA |
| giorni frammentati (meter unico) | 79 | **79** | identica: CONFERMATA |
| gap-minuti (print corretto) | 7140 non stampabile (helper flattenato cross-date, bug di print) | **7140** (= 119 gap-ore, coincide col bill /20) | - |
| golden M-BF | 0 match | **0 match** | identico: CONFERMATA |

Il bill NON scende: i 79 giorni erano TUTTI multi-docente, il vecchio helper
NON stava contando i MONO-FRAG (la riserva di Fedora non si e' materializzata).
Il print del gap-minuti ora e' positivo e coerente (7140 x 20 = 142800
esatto): la cura del meter unico rende anche il banco leggibile.

Esito: re-cert IDENTICA al numero su tutte le righe. Il pacchetto ratifica
per maintainer e' completo: banco (+ controllo) + A/B ciclo completo + gates
(5 agent) + cure + re-cert. Merge wt/mbf-cures -> wt/mbf-proto eseguito per
storia lineare (vedi commit di merge).

Log: /tmp/mbfc-replay.log, /tmp/mbfc-golden.log (locali: *.log e' gitignorato
nel repo; i numeri sono integralmente nel presente referto e nel commit).
