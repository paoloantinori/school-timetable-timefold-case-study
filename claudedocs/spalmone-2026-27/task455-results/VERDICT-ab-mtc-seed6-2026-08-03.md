# VERDICT TASK-455 (M-TC). A/B controllato seed-6, 2026-08-03

> Risponde alle tre domande di maintainer (03/08). Stesso seme GRASP=6, source=input, unica variabile
> `-Dplanner.tag-capacity.enabled=true`. Banner verificato: `M-TC lab tag-capacity: enabled=true,
> rooms-per-tag={acconciatura=1, termoidraulica=1, saldatura=1, moda=1, informatica=2, meccanica=1}`.
> Raw: `task455-results/ab-mtc-seed6/` (ON) vs `task480-results/badseed-frequency/grasp-seed-6/` (OFF).

## (a) Il room-null di G04 del 2026-11-04 e' sparito? Chiude 0-hard?

**SI'.** OFF: `-1hard` (H29 "Assigned lesson must have a room", matches=1, la lezione meccanica di
G04 / T15). ON: **`0hard`**, nessun constraint hard con matches>0 (no H29, no H2, no H7).

Inoltre M-TC ha relief-ato la collisione: a fine run le celle meccanica in overflow sono **0**
(erano 1 in OFF). La costruzione e' identica (GRASP seed=6 = stesso template), quindi e' la LS,
spinta dalla penalta' medium di M-TC, a spostare una delle due lezioni meccaniche fuori dalla cella
satura. Entrambe trovano stanza. Il meccanismo morde sul target.

## (b) Prezzo vs OFF (stesso seme, unica variabile M-TC)

| KPI | OFF seed-6 | M-TC ON | Delta |
|-----|-----------|---------|-------|
| **hard** | -1 (H29) | **0** | **curato** |
| medium | -1.259.388 | -1.464.102 | **-204.714** |
| full-day | 78,6% | 77,8% | -0,8pp (-16 giorni) |
| ricorrenza unpinned | 75,5% | 75,5% | flat (7104 vs 7103) |
| buchi interni (H17) | 0 | 0 | flat |
| fine-anticipata | 199 | 241 | +42 |
| wall | 1794s | 1975s | +10% |

Scomposizione del costo medium (-204.714):
- `-120.000` e' la penalta' **di M-TC stesso** (120 matches x peso 1000, dall'analysis.json).
- `-84.714` residuo e' spostamento secondario (la LS che riloca lezioni per ridurre la domanda di
  tag, impattando altri medium).

Scomposizione dei 120 match per tag (calcolato dall'export, replicando il groupBy M-TC):

| tag | celle overflow | capacita' stanze |
|-----|---------------|------------------|
| **acconciatura** | **107** | 1 |
| **moda** | **13** | 1 |
| meccanica | **0** | 1 |
| termoidraulica / saldatura / informatica | 0 | 1/1/2 |

Fatto portante: tutti i 120 match sono **acconciatura (107) + moda (13)**, cioe' CO-PRESENZA
PRATICATA DAL GOLDEN. La scuola colloca routineariamente piu' lezioni di un tag oltre la capacita'
dell'unica stanza, e lo fa praticando l'ospite. E' il motivo per cui H2 e H-COPRESENCE esentano le
coppie praticate depth>=2. M-TC **non** ha quell'esenzione: tassa a peso pieno 1000 cio' che la
scuola accetta e che i vincoli sibling gia' esentano. Meccanica (la collisione che volevamo curare)
e' a 0: li' M-TC ha fatto il suo lavoro.

## (c) Quale esito: cura pulita / cura con prezzo da tarare / vincolo che non morde

**Esito (b): cura che chiude l'hard, ma con un prezzo quasi interamente PENALE FALSA su co-presenza
praticata, non un costo di qualita' reale.** Morde eccome (120 match, target relief-ato).

La diagnosi precisa cambia la raccomandazione rispetto al "(b) serve taratura del peso":

- **NON e' un problema di peso.** Un peso piu' basso ridurrebbe la penalta' ma anche il segnale
  sugli overflow REALI (quelli non praticati, dove M-TC e' l'unico vincolo a dire che li' non ci
  sono stanze per tutti). E' la trappola M-TR: gli overflow sono PRATICATI, non scarsi, quindi il
  peso e' la sub-leva sbagliata. NON aprire uno sweep di peso.
- **La leva giusta e' l'esenzione co-presenza praticata**, mirror di H-COPRESENCE. M-TC dovrebbe
  esentare le celle (tag, startDateTime) la cui domanda e' coppie praticate dal golden depth>=2
  (stessa esenzione che H2/H-COPRESENCE gia' applicano). Con quell'esenzione, su questo seed M-TC
  sparerebbe 0 match (i 120 sono tutti praticati), costo medium ~nullo, **cura pulita (esito a)**,
  mantenendo il relief del target meccanica (se la coppia G04+G13 non e' praticata, vedi sotto).

KEY FOLLOW-UP (era gia' differito nell'handoff badseed): la coppia meccanica G04 + G13 del
2026-11-04 e' praticata dal golden (depth>=2)? Se NO, l'esenzione co-presenza la lascia fuori e M-TC
continua a relief-arla, cura preservata a costo ~nullo. Se SI, esentarla toglierebbe anche il
relief del target e il room-null tornerebbe (andrebbe gestito come eccezione esplicita). Va
verificato sul golden depth map prima di costruire l'esenzione.

## Raccomandazione (NESSUNA adozione, M-TC resta default-OFF)

1. M-TC e' la cura della seconda modalita' di fallimento (room-tier saturation del tag), non
   appena completata con l'esenzione co-presenza praticata. Sul bacino seed-6 il meccanismo ha
   relief-ato la collisione meccanica e chiuso a 0-hard.
2. Prossimo step (task nuovo, non qui): aggiungere a `labTagCapacity` l'esenzione coppie praticate
   depth>=2 (mirror `loadGoldenCoPresenceDepth` / H-COPRESENCE), dopo aver verificato la
   practiced-status della coppia G04+G13 meccanica. Poi re-run seed-6: atteso 0-hard con medium
   ~come OFF (cura pulita).
3. NON fare sweep di peso (parallelo M-TR documentato).
4. Default invariato: M-TC resta opt-in OFF. Ratifica a maintainer quando l'esenzione e' misurata pulita.

## Riproducibilita'

```
./scripts/benchmark/task455-mtc-seed6.sh   # boot prod multistart + M-TC on + GRASP seed=6, capture
```
Baseline OFF: `task480-results/badseed-frequency/grasp-seed-6/` (stesso seme, M-TC off).
Confronto HTML: `ab-mtc-seed6/confronto-ab-mtc-seed6.html`.

---

## FOLLOW-UP: esenzione co-presenza praticata (TASK-485), A/B di conferma 2026-08-03

> Stesso seed GRASP=6, M-TC ON, + esenzione celle praticate depth>=2 (mirror H-COPRESENCE,
> riusando isCoPresenceCell). Raw: `task455-results/ab-mtc-seed6-exemption/`.

### Pre-condizione (1) verificata con il NUMERO

`meccanica|WEDNESDAY|13:00` ha **golden depth = 1** (solo "G04", mai G13) nella mappa che
H-COPRESENCE pubblica. **NON praticata** (depth<2). Quindi l'esenzione lascia fuori la cella del
target: M-TC continua a fare relief su G04+G13 a Wed 13:00 e la cura sopravvive. (Il golden
pratica co-presenza meccanica altrove: Friday 08-12, Thursday 08-12, Wednesday 08/10/11, tutte
depth 2-3, ma non Wed 13:00.) L'esenzione e' sicura.

### Aspettative falsificabili (dichiarate in anticipo) vs esito

| attesa | esito | |
|---|---|---|
| hard resta 0 | **0hard** | OK |
| match M-TC crollano 120 -> ~0 | **0 match** (M-TC assente dall'analysis, 0 penalita') | OK |
| medium torna vicino a OFF (-1.259.388) | **-1.353.502** (-94.114 sopra OFF) | NON pienamente |

### Diagnosi del -94.114 residuo (NON e' fallimento dell'esenzione)

L'esenzione ha fatto esattamente il suo lavoro: M-TC e' a 0 match, la penalita' falsa da -120.000
e' SPARITA, il medium e' sceso da -1.464.102 a -1.353.502 (-110.560 recuperati). Il residuo
-94.114 vs OFF e' **displacement reale**, spalmato su M20 (-53k daily-fill), M15 (-31k same-day
consecutive), M34/M33/M20.2 (piccoli), M18 (+4k meglio). Non e' concentrato in un bottleneck e non
e' penalita' falsa: e' il costo di spostare lezioni per dare una stanza alla lezione meccanica che
in OFF era room-null. Conferma: il full-day e' -0,8pp sia con sia senza esenzione (stesso
displacement), quindi l'esenzione ha rimosso SOLO la parte falsa, non il costo reale della cura.

### Esito rivisto

L'esenzione (TASK-485) e' il fix **corretto** del problema della penalita' falsa: M-TC ora cura il
room-null (0-hard) lasciando solo il costo reale del cure (~-94k medium di displacement, non
documentato come accettabile o meno in questo A/B). NON e' una cura a costo nullo: pagare -94k
medium + 0,8pp full-day per chiudere 1 room-null hard e' il trade-off onesto. L'esenzione e'
comunque necessaria: senza di essa lo stesso cure costava -204.714 (di cui -120.000 falsi).

### KEY OPEN QUESTION per la ratifica (maintainer)

Il -94k e' il costo del cure SU SEED-6 (bacino con il room-null). E' il costo di curare il
meccanica, o M-TC perturba la LS anche sui bacini buoni (costo fisso anche quando non serve)? Un
A/B su un buon seme (good-seed, M-TC+esenzione ON vs OFF) lo dice. Se sui bacini buoni il costo e'
~nullo, M-TC+esenzione si accende solo quando serve (acceptable). Se e' fisso, e' un costo
permanente da valutare. Non misurato in questo task.

M-TC resta DEFAULT-OFF. La ratifica la porta maintainer con questi numeri.
