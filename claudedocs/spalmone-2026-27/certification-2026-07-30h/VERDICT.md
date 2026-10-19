# VERDICT 30h - TASK-455 POINT 3: M-TC sopra v1 (30g) = LEVA DUD, non adottare

**Run:** 2026-07-30, profilo dev fresco (stop+delete+reseed, baseline G14 nuova TASK-462 =
1739 righe expected-daily-hours), import-calendario (stagePinned 1993), pre-assign (3090 pin
totali, **identico al 30g**; T01 nel totale), solve-staged con **M-TC ON**
(`PLANNER_JAVA_OPTS=-Dplanner.tag-capacity.enabled=true` sulla riga `java -jar`, banner
verificato `enabled=true, rooms-per-tag={acconciatura=1,...}`), constrained-teacher default
ON, M32 default. Ciclo ~70min (B-TIMESLOT 3 iter ~60min, piu' lungo del 30g ~20min: M-TC ha
aggiunto churn). n=1 NON_REPRODUCIBLE.

**Domanda (mandato POINT 3):** M-TC chiude le 119 room-null acconciatura del 30g senza
degradare i 3 KPI di guardia?

## RISPOSTA: NO. M-TC chiude 12/119 room-null (10%) a costo di -121k medium. Leva DUD.

### Hard: M-TC chiude 12 room-null, tutte acconciatura TUE/WED. Il picco THU e' IMMOBILE.
| metrica | 30g (M-TC OFF) | 30h (M-TC ON) | delta |
|---|---|---|---|
| **H29 room-null** | 119 | **107** | **-12 (chiuse)** |
| H0 boxed | 3 | 4 | +1 |
| **hard totale** | -125 | **-115** | **+10** |

Breakdown room-null per (tag, weekday), acconciatura (cap 1):
| weekday | 30g | 30h | delta |
|---|---|---|---|
| MON | 1 | 0 | -1 |
| TUE | 13 | 8 | -5 |
| WED | 31 | 25 | -6 |
| **THU** | **50** | **50** | **0 (IMMOBILE)** |
| FRI | 0 | 0 | 0 |
| acconciatura TOT | 95 | 83 | -12 |
| termoidraulica (TUE) | 24 | 24 | 0 |

M-TC ha sparso l'acconciatura TUE/WED (lezioni unpinned), chiudendo 12 room-null. MA il picco
**giovedi' 50 e' immobile**: e' dominato da T01 (constrained-teacher, pinnato,
spostabile zero). La termoidraulica (T03, tutta martedì) e' invariata (giorno unico
concentrato, non spostabile entro il vincolo). Quindi **il soffitto di M-TC e' ~12
room-null** (la coda TUE/WED unpinned): non puo' toccare ne' il picco THU pinned ne' la
termoidraulica. Causalita' 100% contesa confermata (0/107 in cella con capienza residua).

### Il prezzo (medium): M-TC costa -121.000, dominante. Ratio pessimo.
| vincolo | 30g | 30h | delta |
|---|---|---|---|
| **M-TC** tag capacity | 0 (OFF) | **-121.000 (12 celle)** | **-121.000 (nuovo)** |
| M-LF non-lab in lab | -113.000 (113) | -108.000 (108) | +5.000 |
| M20 daily fill | -103.000 | -101.500 | +1.500 |
| M37 keep-room | -34.200 | -35.000 | -800 |
| M15 same-day contig. | -14.400 (6) | -8.400 (3) | +6.000 |
| **medium TOTALE** | -995.929 | **-1.100.964** | **-108.035** |
| soft | 18.175 | 16.771 | -1.404 |

M-TC aggiunge -121.000 di penalita' propria (direttamente dallo score, non confondato). Gli
altri medium migliorano di ~13k (lo spreading di M-TC riduce M15/M20, piu' il confound
G14). **Netto: M-TC scambia 12 room-null HARD (valore +12 hard, +10 netto dopo l'extra
boxed) per ~108k medium.** Cioe' ~10k medium per ogni room-null chiusa. Ratio pessimo: M-TC
non si paga da solo.

### I 3 KPI di guardia: tengono tutti (marginalmente migliori)
- **Buchi interni H17: 0 -> 0.** Nessuna regressione. ✓
- **Full-day ex-G14: 88,1% -> 88,4%** (+0,3pp; G14-pulita). Shape: 156 sotto (vs 159),
  147 a -1h (94%), 9 profondi (vs 8). Equivalente. ✓
- **Ricorrenza UNPINNED: 74,5% -> 74,9%** (+0,4pp; INCLUDE G14, quindi confondata dal
  TASK-462, ma migliora comunque). ✓

Le guardie non degradano: M-TC non rompe nulla. Semplicemente non porta guadagno hard che
giustifichi il suo costo medium.

## Lettura: perche' M-TC fallisce (ed e' strutturale, non di tuning)

M-TC e' un vincolo medium a valle (ricerca locale). Punisce le celle (tag, datetime)
sovra-capienza per spingere il solver a SPARGERE. Ma la concentrazione di acconciatura su
WED/THU **non e' un artefatto di ricerca che la LS ha prodotto**: e' il **template
settimanale che la ricrea ogni settimana** (il builder e' cieco alle stanze e lavora
per-gruppo, vedi `task455-tag-capacity-30e` sez. "perche' il template ammucchia") + il picco
THU inchiodato da T01 (constrained-teacher pinned). Un vincolo a valle non disfa una
concentrazione che la costruzione ricrea: per questo il picco THU (50) non si muove e il
guadagno si ferma alla coda TUE/WED unpinned (12 ore).

E' lo **stesso meccanismo del REFUTO di slot0-balancing (30c, QUADRO)**: "la cura sposta la
risorsa scarsa invece di coordinarla". M-TC e' della stessa famiglia di leve (spargimento),
e fallisce allo stesso modo: sposta la coda unpinned ma non tocca il nucleo pinned/strutturale.
Il B-TIMESLOT allungato a 3 iter (~60min vs ~20 del 30g) e' il sintomo: M-TC ha aggiunto
pressione/churn senza tradurla in guadagno hard.

La cura giusta (già indicata dalla spec sez. 5 e dai commit "template ammucchia") e' il
**consumatore-costruzione**: coordinare i template cross-gruppo contro la capienza dell'unica
stanza acconciatura (TASK-449, "insegnare al builder a contare le stanze"), non un vincolo di
ricerca. M-TC lo conferma empiricamente: il downstream non basta.

## Confronto G14 (TASK-462) - confound dichiarato e gestito

30h gira sulla baseline G14 nuova (120h, non piu' 990h del 30g). Due fattori cambiano
(M-TC + G14). MA l'attribuzione del risultato principale e' PULITA:
- **room-null e' G14-indipendente su v1** (il Servizio stilistico G14 e' de-taggato, e
  la spec 30e mostra che era l'unica acconciatura G14). Le 107 room-null sono
  acconciatura (2/G10 + G12) + termoidraulica (G03), nessuna G14. Quindi "M-TC
  chiude 12 acconciatura" e' attribuzione pulita, non confound G14.
- **M-TC penalty -121k** e' direttamente dallo score (il vincolo stesso), non confondato.
- **full-day ex-G14** e' G14-esente nel motore (Q16): pulita, e tiene.
- L'UNICA metrica confondata e' **ricorrenza unpinned** (include G14), ma migliora comunque.

Un A/B perfetto richiederebbe 30g' (M-TC OFF, G14 nuovo) oltre al 30h. Non serve: la
domanda primaria (M-TC chiude room-null acconciatura?) ha risposta pulita (NO, solo 12, e a
costo -121k) anche senza il controllo, perche' la metrica target e' G14-indipendente.

## Conclusione

M-TC e' confermata leva **DUD come vincolo di ricerca**: chiude 12/119 room-null (10%, solo
coda TUE/WED unpinned; picco THU 50 pinned immobile) a costo di -121k medium (ratio ~10k
medium/room-null). Le guardie tengono ma il guadagno hard non giustifica il costo medium, e
il soffitto e' strutturale (template + pin), non di tuning. **Non adottare: M-TC resta
DEFAULT OFF.** La room-null acconciatura e' un problema di costruzione (TASK-449, coordinare
i template cross-gruppo contro la stanza unica), non di ricerca. Decisione di maintainer.

Cosa e' rimasto ACCESO e utile per la cura costruttiva (TASK-449): il rilevatore di scarsita'
(spec componente 4) e il diagnostico `tag-capacity-excess-kpi.py` identificano gia' le celle
critiche (acconciatura THU 50 / WED 25 / TUE 8 + termoidraulica TUE 24). M-TC il vincolo non
serve; la diagnosi si'.
