# TASK-644 nota di chiusura: l'accessor tipizzato ESISTE GIA'

STATO (per maintainer): CHIUSO, SUPERATO DAI FATTI. Nessuna PR da inviare
upstream. Il setaccio 23/09 ha verificato che l'API che volevamo proporre
esiste gia' in rc-1, anche meccanicamente sul jar nostro. Questo file non e'
piu' una bozza outbound: e' la nota di chiusura e il registro dell'azione
locale che ne deriva.

## Cosa esiste gia' (fatti verificati)

- `GenuineEntityMetaModel.basicVariable(String name, Class valueClass)` torna
  direttamente `PlanningVariableMetaModel`. Nessun downcast.
- Introdotto dalla PR #2037 (gennaio 2026); le domande di partenza sono le
  issue #2030 e #2293.
- Verifica nostra sul jar 2.7.0-rc-1: `genuineEntity(Class).basicVariable(
  String, Class)` esiste con firma tipizzata.

## Cosa era il presunto gap

Il nostro codice usava `entity(Class).variable(name, type)` + downcast
perche' non conoscevamo l'accessor tipizzato. Bug di conoscenza LOCALE, non
un gap upstream. La PR che stavamo scrivendo avrebbe proposto un'API gia'
esistente.

## Cosa resta upstream (poco, priorita' bassa)

Solo una micro-discussione possibile: deprecare o meno gli accessor generici
(`entity().variable(...)`) ora che la via tipizzata esiste. Nessuna azione
nostra dedicata; al massimo una menzione se capita dentro le discussioni 643
o 645, mai come apertura a se' stante.

## Azione locale che ne deriva

- Sostituire i 7 downcast (le 7 copie del resolver censite dal TASK-642) con
  `genuineEntity().basicVariable(...)`.
- TASK-642 nel backlog diventa banale: aggiornarlo con questo esito. Il
  consolidamento in helper unico resta utile, ma ogni copia perde il cast.
- Il task backlog TASK-644 (tracciava questa PR) va chiuso come superato con
  puntatore a questa nota. L'aggiornamento del backlog e' del thread
  principale, non di questa passata di correzione bozze.
