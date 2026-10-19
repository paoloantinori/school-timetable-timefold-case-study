---
name: zero-hard-loop
description: "Il loop distillato della strada verso 0-hard: analizza una consegna, decomponi il residuo, decidi se e cosa vale la pena migliorare. Da usare a OGNI consegna di ciclo/banco con residuo non zero. Ordine di maintainer 21/09."
---

# Il loop zero-hard (distillato dai cicli 20-21/09)

Per ogni consegna con residuo hard non zero. Fasi in ordine, niente saltato.
Lo scopo: aumentare p(0) sistematicamente, non inseguire il residuo a mano.

## 1. Decodifica meccanica (prima sempre)

```
python3 scripts/analyze/residual_decomposition.py <capture-dir>
```

Produce famiglie + celle (scope-corrette: le regole imparate il 20/09 sono
dentro lo script) + lookup nel registro cure (`cure-registry.json`) + il
ranking "vale-la-pena" + GENERA DA SOLO il suo strato di consumo: day-anatomy
per ogni cella residua e course-pacing dei gruppi toccati, nella capture dir.
APRI QUEI VIZ prima del verdetto: la fase 1 non e' completa finche' non sono
stati letti (la forma che i numeri nascondono e' il punto: la lezione del
21/09 e' arrivata guardando, non contando). Incrociare SEMPRE con
analysis.json (lo script e' un proxy-radar: le candidate vanno confermate
dallo score vivo) e con la sanity gate obbligatoria (solution-sanity-check.py:
gia' in capture-run.sh, col suo stoplight).

## 2. Golden-first su ogni cella (obbligatorio: c'e' l'hook)

Per la cella residua: cosa pratica il golden su quel (gruppo, data, docente,
materia)? Tre esiti, tre strade:
- **il golden la riempie/pratica** → la cura e' nostra (search, costruzione,
  distribuzione): vai a 3.
- **il golden la lascia vuota/non pratica** → input fantasma: famiglia 550
  (correggi input golden-first, notifica referente: con l'analisi golden ALLEGATA).
- **il golden diverge strutturalmente** → domanda workbook/web-app (input
  dichiarato che manca: registrala, non risolverla nel solver).

## 3. Near-miss → cieco in parallelo (la regola)

Se il residuo e' piccolo (-1..-4): puzzle neutro COMPLETO (H1+H5+H7+H17+H20+
H31+H32+VRP+pin) e cieco zero-contesto SUBITO, in parallelo. LA POST-CONDIZIONE
E' IL DELTA DEL PUNTEGGIO REALE, non la condizione visiva (lezione 21/09: il
riempimento parziale di un giorno vuoto accende H32 a -3 = veleno). Arbitra le
mosse sui dati completi: legali che il sistema non sa esprimere = famiglia
mancante; nessuna legale = strutturale → vai a 4.

## 4. Traccia al meccanismo (i 4 livelli del 636 come guida)

Osservazione sul consegnato → dump di costruzione → contatori del log
(stampSkipsByGroup e' cause-indexed) → lettura del codice. Cerca DOVE la forma
e' fabbricata, non come patcharla. Auto-correggiti quando un livello si
rivelain seguito dormiente o sbagliato (il 636 ha richiesto 4 livelli con 2
autocorrezioni: e' il metodo, non un fallimento).

## 5. Cura all'altitudine giusta

In ordine di preferenza: input (golden-first, 550) > costruzione
(preferenza-mai-forza nel builder: la forma 599) > belt/gamba nei finisher
(classe-616 style) > nuova famiglia di mosse (ultimo: solo se il cieco ha
mostrato mosse che nessun braccio sa esprimere).

**La scala di validazione (direttiva ITERAZIONE SU SNAPSHOT, 6/09: il ciclo
completo e' SOLO il gate di adozione)**:
1. Test localizzati (unit + banco live-state: minuti) per ogni candidato.
2. Replay su stato del catalogo (634: seed hash verificato, room-faithful se
   la cura tocca stanze): minuti, non ore. Se il catalogo non ha lo stato
   giusto, la prossima cattura di ciclo lo produce (capture ON sempre).
3. Warm start verso 0: se una consegna e' VICINA a zero, i candidati girano
   sullo snapshot prefinale fino allo zero; SOLO il vincitore paga il ciclo
   completo.
4. Il ciclo completo serve per: adozione/ratifica, misura end-to-end di
   consegna, cattura di snapshot freschi. MAI come primo strumento di
   verifica di un'idea.

Poi: gates (/simplify + code-review high + suite) > ratifica maintainer > ciclo
di adozione.

## 6. Registro e stima

Aggiorna cure-registry.json (la voce della famiglia curata), la STIMA p0 se
la conoscenza e' cambiata, e il task backlog. Il multi-start N=5 con
p(0|tentativo) corrente decide la frequenza: ogni cura ratificata che chiude
una famiglia sposta il pavimento della serie.

## Cosa NON fare

- Non dichiarare "strutturale" senza la prova del surplus di ore (cieco) o del
  golden.
- Non inseguire l'H25 senza verificare l'hash seed (replay-artifact).
- Non curare il codice prima di avere la catena causale completa.
- Non saltare la sanity gate ("0hard e' binario").
- Non lanciare un ciclo completo per verificare un'idea: prima snapshot, il
  ciclo e' il gate di adozione (il collo di bottiglia non e' il solve, e'
  pagarlo quando non serve).
