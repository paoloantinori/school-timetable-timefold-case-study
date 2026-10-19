# SPIKE S2 - Builder consumatore spec: conta stanze cross-gruppo (2026-07-30 notte)

**Domanda:** il WeeklyTemplateBuilder e' per-gruppo e cieco alle stanze: quando sceglie
la cella per un corso con tag mono-stanza, non sa che altri gruppi hanno gia' messo
corsi dello stesso tag in quella cella, e li ammucchia (acconciatura mercoledi 189h).
La cura indicata da TRE evidenze (tagging v1, slot0 dud, M-TC dud: funziona solo cio'
che agisce PRIMA del pin): un builder che conti le celle cross-gruppo per tag.

## Misurazione "PRIMA" (sul dump di costruzione, builder attuale room-blind)

Contesa per tag = celle (weekday, hour) con piu' gruppi della capacita' di stanze del tag:

| tag | stanze | celle usate | celle contese | lezioni-eccedenti (gruppi oltre cap.) |
|---|---|---|---|---|
| acconciatura | 1 | 29 | **22** | **33** |
| informatica | 2 | 35 | 10 | 15 |
| meccanica | 1 | 24 | 11 | 13 |
| moda | 1 | 27 | 7 | 7 |
| termoidraulica | 1 | 11 | 1 | 1 |
| saldatura | 1 | 4 | 0 | 0 |

Totale: **~69 lezioni-eccedenti in ~51 celle contese**. Picco acconciatura: Thu ore 9
(4 gruppi: 1/2/G10 + G14) e Tue ore 11 (1/2/G10 + G12), Wed ore 12-13 (G10).

## Cosa conferma (premessa della cura VERIFICATA)

Il builder produce 33 lezioni-eccedenti di acconciatura in celle dove la stanza unica
(R11) ne ospita una sola. Al solve (fase stanze) solo 1 gruppo ottiene
l'aula, le altre restano room-null o spostate. **Questo lega il room-null acconciatura
alla COSTRUZIONE** (conferma empirica di TASK-449: e' un difetto di costruzione, non di
ricerca), con i numeri esatti del pile-up. Coincide col meccanismo della tripletta del
QUADRO (scarsita' congiunta risorsa-giorno fra gruppi che se la contendono).

## Verdetto (per il QUADRO, colonna spike): CURA REFUTATA al cell-selection. Pile-up strutturale.

Prototipo COSTRUITO dietro flag `planner.template.room-aware.enabled` (default OFF, stessa
meccanica di slot0-balancing). A ON, per un corso con tag mono-stanza, il builder fa un
two-pass first-fit: pass 1 preferisce una cella (tag, weekday, slot) libera cross-gruppo,
pass 2 fallback = oggi (mai forzare). OFF e' byte-identico per costruzione (monoTag=null ->
nessuna logica tag). Misura A/B nello STESSO run (cosi' la varianza run-to-run del builder
non inquina):

| metrica (construction-end) | OFF (flag off) | ON (room-aware) |
|---|---|---|
| ricorrenza TASK-381 L2 | 76.1% | **76.5%** (tiene, +0.4) |
| stamp skipConflict | 0 | **0** (non forza mai) |
| stamp decline totale | 22.1% | 22.1% (invariato) |
| celle contese (tutti i tag) | 52 | 53 |
| lezioni-eccedenti (tutti i tag) | 73 | **74** |
| acconciatura eccedenti | 35 | **34** (-1) |

**La cura NON riduce il pile-up** (74 vs 73, acconciatura 34 vs 35: rumore, non segnale).
Recurrence e skipConflict reggono (il prototipo e' sicuro), ma il pile-up resta.

**Perche' non funziona (il perche' e' il risultato vero):** il pile-up NON e' un artefatto
di scelta del builder. Il builder non sta scegliendo male tra celle libere: i corsi
acconciatura di 1/2/G10 + G14 condividono docenti e giorni FISSI (il meccanismo della
tripletta del QUADRO: scarsita' congiunta risorsa-giorno). Quando il pass 1 scarta una cella
tag-presa, l'alternativa (altro weekday) e' a sua volta tag-presa o il docente non c'e': non
c'e' cella libera da preferire, quindi si cade nel pass 2 (= oggi). La preferenza su cella e'
inerte perche' la risorsa e' gia' satura.

**Tenere o buttare (per il QUADRO):** BUTTARE LA CURA (cell-preference), tenere il prototipo
come reperto. La cura del room-null acconciatura NON e' un problema di selezione cella del
builder: e' scarsita' strutturale teacher-day. Leva reale sta A MONTE (coordinazione
cross-gruppo dei giorni del docente vincolato, vedi tripletta / TASK-449, o e' genuinamente
infeasibile in cassa di stage come M-TC ha misurato). Il prototipo resta dietro flag OFF
(harmless, recurrence anche migliora di 0.4), la decisione di accenderlo e' di maintainer ma i
numeri dicono che non risolve il problema.

## Misurazione "PRIMA" (sul dump di costruzione, builder attuale room-blind)
