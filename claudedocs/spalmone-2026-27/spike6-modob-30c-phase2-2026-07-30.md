# SPIKE 6 - Modo B del ritardo sul construction-end REALE di 30c (phase-2) - 2026-07-30/31 notte

Mandato: quantificare sul dump construction-end di 30c (phase-2, gia' catturato) i giorni
gia' in ritardo profondo a fine costruzione (front-gap 2 slot) e dire se la cura TASK-451
(pre-assegnazione slot0-1 cross-gruppo) li coprirebbe o serve una leva separata. Read-only,
distingue dal round-1 `spike2-modo-b-ritardo-2026-07-30.md` che usava il test-harness
(bare construction). Qui si misura sulla costruzione REALE del run 30c fase 2.

## Consegna (3 righe)
- **Ipotesi:** modo B (front-gap >= 2 a fine costruzione: slot0+slot1 vuoti, prima lezione
  alle h10+) e' un meccanismo autonomo che richiede una **leva di costruzione separata**
  (costruire lo slot0, non solo chiudere il buco slot1 del modo A).
- **Numero misurato (30c phase-2, dump reale):** modo B = **18 giorni** su 1924 attesi
  con lezione, firma **G04 ven** (10/18) + **mar-giu** (15/18), stesso family del
  deep-deficit (SPIKE 3/4/5). **17/18 sono "ancorabili"** (slot0-1 vuoti con **>=3 lezioni**
  collocate dopo: manca solo l'ancora -> TASK-451 li coprirebbe meccanicamente); **1 e'
  thin** (G08 2027-04-01, quota 2h, deficit 0, mixed aula+stage day gia' noto).
  **Sopravvivenza al finale: 1/18** (l'AnchorRepair TASK-433 adottato + la LS chiudono
  gia' 17/18 front-gap-2; l'unico sopravvissuto e' il thin non-deficit).
- **Tenere: BUTTARE come leva separata (come round-1, ma ora sul dump reale).** TASK-451
  coprirebbe modo B (17/18 ancorabili) ma e' **ridondante**: il solver finale lo copre gia'
  via AnchorRepair. Nessuna leva di costruzione dedicata e' necessaria; l'1 thin non e' un
  deficit reale e non la giustifica. Modo B reale = **G04 ven** (family deficit), NON
  "G11 mer" (quello era artefatto test-harness, come round-1 sospettava). Priorita'
  resta bassa (coincide col "accettare il difetto o chiudere TASK-450" del QUADRO).

## Metodo
Per ogni (gruppo, data) atteso (DailyActivityQuota aula+lab > 0, escl. G14 e sabati)
con >= 1 lezione collocata nel dump: `front-gap = min(slot occupati da lezioni REALI)`,
slot = hour-8 (slot0=08:00 ... slot5=13:00, slot6=14:30, slot7=15:30; `shared.real_slots`
esclude Stage/Esame/Fasullo). front-gap>=2 = prima lezione h10+ (slot0+slot1 vuoti).
Criterio di copertura TASK-451: giorno **ancorabile** se >=3 lezioni collocate (c'e'
materia da ancorare, slot0-1 vuoti solo per mancata ancora); **thin** se <=2 lezioni
(niente da ancorare). Sopravvivenza = stesso (g,d) front-gap>=2 anche al finale.
Dump: `certification-2026-07-30c/phase-transitions/phase-2-2-ls-compactness.txt`
(construction-end) vs `lessons.txt` (finale). Script `/tmp/spike2_modob.py` (ephemeral).

## Misure

| | phase-2 (construction-end) | finale |
|---|---|---|
| giorni attesi con >=1 lez | 1924 | 1921 |
| ancorati@slot0 | 1876 | 1907 |
| front-gap1 (h9) | 30 | 13 |
| **front-gap>=2 (h10+)** | **18** | **1** |
| copribili da TASK-451 (>=3 lez) | 17 | 0 |
| thin (<=2 lez, leva separata) | 1 | 1 |

phase-2 front-gap>=2 per gruppo: **G04 10**, G10 3, G13 2, G09 2, G08 1.
Per weekday: **ven 12**, gio 3, mer 3. Per mese: mar 3, apr 7, mag 5 (nov/dic/feb 1 cadauno).
Esempi (tutti ven, G04, exp 5h, 3 lez collocate a slot 2-4): 2027-02-26, 03-05, 03-12,
03-19, 04-02, 04-09, 04-16, 04-23.
Sopravvissuto al finale: solo **G08 2027-04-01** (gio, slot4, 2 lez, exp 2, deficit 0).

## Interpretazione
1. Modo B esiste a construction-end (18 giorni) con una firma netta: **G04 ven, mar-giu**.
   E' la **stessa cella** del deep-deficit (SPIKE 3: G04 ven = 13 deep; SPIKE 4: ricerca).
   Modo B e' dunque il deficit che si manifesta (e poi si risolve) lungo il ciclo staged.
2. 17/18 sono **ancorabili**: il giorno ha le sue lezioni (3+, exp 5h) ma la costruzione
   non ha piantato l'ancora slot0-1, quindi parte a slot2+. TASK-451 (pre-assegnazione
   deterministica cross-gruppo del blocco slot0-1) le coprirebbe meccanicamente.
3. MA non serve: **17/18 sono gia' chiuse al finale** (sopravvivenza 1/18). L'AnchorRepair
   (TASK-433, adottato incondizionato nel ciclo staged) chiude i buchi LEADING di M18 per
   import cross-date, ed e' esattamente questo che recupera i front-gap-2. La cura
   coordinata di COSTRUZIONE (TASK-451) agirebbe su un difetto che la pipeline adottata
   risolve da sola dopo.
4. L'1 sopravvissuto (G08 2027-04-01) e' thin e **deficit 0** (mixed aula+stage day a
   quota 2h gia' censito in `daily-fill-kpi.py`): non e' un difetto, non giustifica una
   leva. Correzione al round-1: il modo B reale e' G04 ven, non "G11 mer" (artefatto
   del test-harness `target/construction-lessons.txt`, che sottostima la qualita' della
   costruzione reale - lo disse lo stesso round-1).

## Verdetto / prossimo passo
- **TASK-451 coprirebbe modo B, ma e' ridondante vs AnchorRepair adottato. Nessuna leva
  separata necessaria.** BUTTARE la pista di una cura di costruzione dedicata al modo B.
- Resta valido il quadro TASK-450: il late-start residuo al finale e' quasi tutto modo A
  (ancorato@8 + buco@slot1 -> slide), non modo B; la priorita' di cura (se si vuole) e'
  la coordinazione cross-gruppo della prima ora, non il modo B.
- Se in futuro si volesse un proxy di costruzione dal test harness, dichiarare che
  sottostima la qualita' reale (il modo B li e' visibile, nel reale si risolve).

## Riferimenti
- round-1: `spike2-modo-b-ritardo-2026-07-30.md` (test-harness, aveva gia' intuito il
  "non si riproduce nel reale").
- TASK-450 / TASK-451: QUADRO colonna 2-3 (catena ritardo prima ora, cura coordinata).
- TASK-433 AnchorRepair adottato: `certification-2026-07-30c/VERDICT.md`, CLAUDE.md
  "RATIFICA TASK-433".
- family deficit: SPIKE 3/4/5 (`spike{3,4,5}-*-2026-07-30.md`).
