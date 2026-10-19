# SPIKE: le 3 lezioni non collocate = G06 / Barberia / T19 (2026-07-30)

Read-only, stesso schema a 3 condizioni di G06/T01 e G04-venerdi. Nessun codice,
nessun prototipo.

## Consegna in tre righe

- **Ipotesi:** le 3 Barberia non collocate sono un gap di ricerca/coordinamento su un docente
  vincolato (T19, Q22, solo Lun+Mar), NON un tetto hard di capacita'.
- **Numero misurato (run 30d):** sulle 396 celle legali (Lun/Mar in sessione, calendario G06
  aperto, quota > 0), T19 e' libero in **271** ma G06 e' libero in sole **2** (G06 satura
  Lun/Mar a 8h/giorno in 394 celle su 396); nessun blocco H11/H17 sulle 2 residue. Il golden
  colloca **tutte 47** le Barberia di G06 su **martedi'** con T19; il run ne piazza 44 e
  riempie il Lun/Mar di G06 con altre materie.
- **Conclusione operativa:** e' ricerca, non capacita' (G06 ha ~381 celle di slack annuo per
  rimescolare). La leva e' coordinare l'allocazione di T19 (Barberia a G02 36h + G06 47h
  + G12 45h = 128h tutte Lun/Mar) come gia' facciamo per T01. MA T19 e' Q22 NON
  confermato: la cura dipende dalla risposta della scuola alla domanda Q4 (vincolo reale o
  scelta organizzativa?). Nessun prototipo finche' la scuola non risponde.

## I 3 numeri (condizioni sul run 30d)

Le 3 lezioni sono identiche: G06, Barberia, **T19**, corso 265, non pinned. Sono
intercambiabili, quindi si misura lo spazio legale per una lezione marginale (la 45a-47a).

Popolazione: G06 in sessione 171 giorni; calendario aperto su 31 celle (weekday, slot); T19
disponibile solo Lun+Mar (VACATION Wed/Thu/Fri, regola Q22).

| condizione | celle | nota |
|---|---|---|
| LEGALI (Lun/Mar in sessione, calendario G06 aperto, quota > 0) | 396 | T19 disponibile per regola |
| + T19 libero | 271 | T19 NON e' il collo (ha 528 celle annue libere su 656) |
| + G06 libero | **2** | G06 e' il collo: satura Lun/Mar a 8h/giorno |
| di cui H11 bloccherebbe (G06 giorno >= 8h) | 0 | le 2 residue non sono piene |
| **libere senza blocco hard (candidati raggiungibili dalla ricerca)** | **2** | entrambe su 2026-09-14 (Mon 08:00, 13:00), giorno cerimoniale |

Le 2 celle libere sono residui del primo giorno di scuola. Per collocare 3 Barberia servirebbe
liberare 3 celle Lun/Mar spostando altrettante lezioni non-Barberia di G06 verso Mer/Gio/Ven
(dove G06 ha ~381 celle di slack annuo: 987 collocate su 1.368 di capacita'). E' uno
rimescolamento che la ricerca non ha fatto, non un muro hard.

## Golden sulla stessa materia

G06 / Barberia / T19: **47 ore, tutte di martedi', tutte con T19.** Nessuna difficolta':
il golden dedica ~1,15h/martedi' a Barberia e tiene il resto del martedi' per le altre materie,
cosi' T19 insegna un gruppo alla volta e G06 non satura il martedi'. Il run fa il contrario:
riempie Lun/Mar di G06 con altre materie e poi non trova posto per le ultime 3 Barberia.
**Differenza nostra**, non vincolo di dominio.

## Cosa regge e cosa no

- **Regge: e' la TERZA occorrenza indipendente dello stesso meccanismo** (la tripletta, vedi
  `docs/superpowers/specs/scarsity-aware-assignment.md` §2.5 e `QUADRO-2026-07-30.md`): docente
  vincolato su pochi giorni fissi (T19 Q22 lun+mar), domanda concentrata li', ricerca che non
  coordina le celle scarse fra i gruppi che se le contendono. Come G06/T01 (giovedi') e
  G04-venerdi. Non e' capacita' ne' vincolo hard: e' ricerca/coordinamento. Tre occorrenze
  indipendenti valgono piu' di qualunque argomento e giustificano il rilevatore unico della spec.
- **Collegamento alla spec:** T19 e' esattamente un docente vincolato (Q22, Lun/Mar) che la
  spec descrive come scarsita' congiunta (risorsa, giorno). La cura e' la stessa di T01:
  pre-assegnazione coordinata del monte-ore di T19 sui tre gruppi prima del solve.
- **Blocco alla cura:** T19 e' Q22 **non confermato**. La domanda Q4 (vincolo reale o scelta
  organizzativa?) decide se trattare T19 come T01 (pre-assegnazione DEFAULT) o come
  coordinabile a piacere. Nessun prototipo finche' la scuola non risponde: se il Lun/Mar e' una
  scelta di comodo, la cura e' piu' leggera (si libera un giorno e il collo sparisce).

## Lascito

- Le 3 Barberia sono l'ultimo difetto "nostro" sul cammino dell'orario consegnabile (insieme alle
  167 ore senza aula, che sono instead il difetto di modello/TASK-455).
- Non si prototipa niente: la decisione e' di maintainer dopo la risposta della scuola su Q4, e la
  forma della cura (estendere TASK-435 a T19 vs coordinamento leggero) dipende da quella.
