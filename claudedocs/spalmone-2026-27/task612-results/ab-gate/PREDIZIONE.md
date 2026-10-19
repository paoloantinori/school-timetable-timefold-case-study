# PREDIZIONE gate A/B TASK-612 (scritta PRIMA di leggere i risultati, 14/09 ~12:00)

Mandato (maintainer da Fedora, 14/09): misurare sul laptop, nessuna adozione.

## Contesto del banco (gia' noto, STEP 1 verde 13/13)

Il codice post-hoist GATE-E riproduce i pin: m39-off -11 -> -8 (CSF) -> -3 (EDF ext,
5 celle chiuse, RELAY-ACCEPTED=13); m39-on -13 -> -10 -> -6; no-op su stato pulito.

## Predizione

1. **Il ciclo completo con multi-start ferma al primo 0-hard**: se entrambe le
   braccia consegnano 0-hard, il residuo fill-family consegnato e' VUOTO per
   costruzione e l'EDF di consegna e' atteso NO-OP nel braccio B (riga
   `[delivery]` con moves=0). Le braccia restano comparabili e il dato e'
   raccolta evidenza: counter=0, nessun criterio di morte.
2. **Se una braccia atterra in un bacino con residuo fill-family (H34/H32)**
   (GRASP bad basin su un tentativo, oppure residuo che sopravvive alla LS):
   il braccio B chiude un sottoinsieme NON VUOTO di quelle celle alla
   consegna (`[delivery]` moves>0, cellsClosed>0) e **hard(B) <= hard(A)**
   non per costruzione ma per improve-or-noop full-hard: il pass consegnato
   non puo' peggiorare l'hard, quindi atteso hard B >= hard A in senso di
   non-regressione (piu' vicino a 0 o uguale).
3. **Nessuna regressione di famiglia attesa**: medium e KPI (full-day,
   ricorrenza, buchi interni) entro la banda fra le due braccia; un delta
   medium favorevole a B e' possibile (banco: medium -798k -> -774k su
   m39-off) ma non e' criterio di adozione.
4. **Banner check**: braccio B deve loggare
   `EmptyDayFinisher relay family (TASK-612 prototype): relay=true h32=true`
   alla partenza e una riga `[delivery]` al punto pre-save. Se il banner
   manca, la braccia e' INVALIDA a prescindere dai numeri.

Falsificabile: se B consegna hard peggiorato rispetto ad A con riga
`[delivery]` moves>0, la predizione 2 e' falsificata (l'oracle improve-or-noop
avrebbe un buco) e il gate e' FALLITO.
