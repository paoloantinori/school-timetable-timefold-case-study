# SPIKE queue (addendum 29/07) - misurazioni checkpoint-based

Tutti in-harness (H2, isolati dal cert prod), flag default-OFF / nessuna modifica
ai file protetti. Consegna a 3 righe ciascuno (ipotesi / risultato / tenere-buttare).

## SPIKE 1 - Sweep peso M37 (DONE, vedi anche faseB-m37/VERDICT.md)
- Ipotesi: alzare M37 (100->400->800) riduce i cambi d'aula.
- Risultato: PIATTO 106/107/106 su 100/200/400/800/1500 (countRoomChanges su export
  gate-finale, 90s). I ~106 residui sono forzati dalla disponibilita' di stanza.
- Raccomandazione: TENERE 100. La leva e' piu' stanze/tag (FASE C), non il peso M37.

## SPIKE 4 - De-tag sweep oltre lo scope autorizzato (DONE, in-harness via PLANNER_TEST_ROOM_TAG_UNTAG)
Misura indipendente (ciascuno da solo, su baseline lab room-null = 251):
- **servizio stilistico** de-tag: 251 -> **166** (chiude ~85). LEVA GROSSA.
- **taglio metalli** de-tag: 251 -> **233** (chiude ~18). Piccola, genuinamente lab.
- (per confronto, FASE C autorizzata: progettazione termoidraulica + tecnologia tessile
  -> ~179, chiude ~72.)
- Ipotesi: de-taggare servizio stilistico / taglio metalli chiude ore lab senza stanza.
- Risultato: servizio stilistico chiude ~85 (ma maintainer lo flagga come pratica-su-cliente
  nell'acconciatura -> la domanda alla scuola arriva col numero 85); taglio metalli
  solo ~18 (ambiguo, basso impatto).
- Raccomandazione: TENERE come numeri per la scuola (non adottare: file protetto,
  decisione scuola). La variante "taglio metalli -> tag saldatura + R17 a capacita'
  reale" (re-tag, non de-tag) non e' misurata (l'harness ha solo hook de-tag); basso
  impatto atteso visto il ~18 del de-tag.

## SPIKE 5 / lead VRP da FASE D (DONE, root-cause)
- Ipotesi: i 111 donatori coda+liberi VRP-esclusi dallo slot-0 del venerdi per G11
  sono un'esclusione spuria (bug VRP); sbloccarli chiude i 16 ritardi di G11 ven.
- Risultato: NON e' un bug. G11 venerdi (es. 2026-09-18) ha labHours=0 (giornata
  NON-lab) con calendar slot-0 open. I 111 donatori sono lezioni LAB escluse dal VRP
  della lab-quota (H23: lab lesson su giorno a labHours=0) - comportamento CORRETTO.
  Non ci sono donatori non-lab coda+liberi (sono nei 396 teacher-busy o 159 unsafe).
- Raccomandazione: BUTTARE l'ipotesi "VRP spurio". G11 ven va risolto a livello di
  COSTRUZIONE (direzione c: ancora settimanale nel template), non di repair ne' di VRP.
  (Inferenza supportata da Friday labHours=0 + regola H23; per conferma rigorosa del
  lab/non-lab split dei 111 serve strumentare lo spike - follow-up opzionale.)

## SPIKE 6 - Anatomia G04 (DONE, read-only, da Fedora)
- Ipotesi: G04 finisce prima nel run (28/05 vs golden 15/06) per un difetto nostro di
  sotto-piazzamento, o e' riflesso dell'input.
- Risultato: NON e' (soprattutto) un difetto G04. (1) Ordinarie (non-stage): il run piazza
  ESATTAMENTE 750h = totale corso (incarichi 990 - stage 240); il golden ne over-piazza 758
  diluendole al 15/06. Il "28/05" e' la data dell'ULTIMA lezione regolare del run: il run e'
  piu' disciplinato (consuma il budget 750h e si ferma), non carente. (2) Il foglio DEF FC
  TUTTI (calendario_fc.csv) stesso diluisce G04 a 0 dopo aprile (expected aula+lab: mag=20,
  giu=0; totale atteso aula+lab=835 concentrato set-ago/apr) -> il piano di casa fa finire G04
  ANCHE prima del run; il golden al 15/06 e' l'outlier. (3) L'unico gap reale e' 32h STAGE non
  piazzate (208/240), ma NON e' specifico di G04: e' un deficit GLOBALE di copertura stage
  (run 1993 vs golden 2320 stage; G12 -183, G10 -72, G08 -24, G04 -32). Il "battiamo
  il golden sul full-day 54% vs 32%" e' il rovescio: il run anticipa G04 addensandolo (750h
  tutte entro 28/05) -> piu' giornate piene.
- Raccomandazione: BUTTARE l'ipotesi "difetto G04". La chiusura anticipata e' input-driven
  (budget 750h fedele + taper del foglio ad aprile). Il gap azionabile e' il sotto-piazzamento
  STAGE globale (capofila G12 -183), non G04. Dettaglio raw: SPIKES-RAW.md sezione SPIKE 6.

## SPIKE 7 - Anatomia erosione ricorrenza (74% run vs 77% template) (DONE, read-only)
- Ipotesi: la LS erode il template di costruzione in modo uniforme (76,3% -> 74,4%).
- Risultato: erosione DISUNIFORME. Peggiore su G14 (-20pp, caso limite 98->115 lezioni) e
  sui gruppi 3° anno/stage (G11 -5,0, G09 -3,9, G10 -3,7) + MODA (1/G05 ~-5); la LS
  MIGLIORA G12 (+6,9, costruiva basso 55,9%) e G07/FC (+1,5/+2,7). CAVEAT: a costruzione
  tutte unpinned, a finale la pre-assegnazione pinna ~3090 lezioni -> la popolazione unpinned
  cambia composizione tra i cut, quindi parte del delta e' composizione non pura erosione LS.
- Raccomandazione: TENERE come segnale. L'erosione concentra sui gruppi 3° anno/stage (stesso
  contorno del gap stage globale di SPIKE 6) -> leva candidata move-selection/difesa M32 su
  quei gruppi, non costruzione (coerente col verdetto SPIKE 3). Per misura pulita serve
  tracking same-lesson costruzione->finale (follow-up). Dettaglio raw: SPIKES-RAW.md sezione SPIKE 7.

## SPIKE 8 - Anatomia sotto-piazzamento STAGE (capofila G12) (DONE, read-only)
- Ipotesi: il run piazza meno stage del golden (1993 vs 2320h) per capacita' giornaliera o
  per un difetto di search.
- Risultato: deficit di COPERTURA DATE, non di capacita'. G12 (peggiore): run 112h su 16
  date vs golden 295h su 43 date; riempimento per-data IDENTICO (~7,0 vs ~6,9 h/data).
  Finestra uguale (entrambi dal 2026-11-20); il run concentra lo stage su ~1/3 delle date del
  golden (dic 73->21, mar 63->21). Le ~183h non piazzate NON sono boxed (H0 globali=3) ->
  expected-unscheduled / stub non piazzati.
- Raccomandazione: TENERE come segnale. E' un comportamento di pinning/placement stage a
  costruzione/importer (CalendarioImportService.planStagePins / StagePattern), NON di search.
  Root-cause del "perche' meno date" richiede il meccanismo di pinning stage (follow-up).
  Diverso dal contenzione-docente di TASK-450: e' copertura date del pinning stage. Dettaglio
  raw: SPIKES-RAW.md sezione SPIKE 8.

## SPIKE 8b - STAGE root-cause PERCHE' (maintainer 29/07) (DONE, read-only, INDAGINE CHIUSA)
- Ipotesi: le 27 date "mancanti" di G12 si perdono nel pinning/import (bug codice) o nei dati.
- Risultato: NON e' un difetto (ne' codice ne' dati). Lo stage di SABATO e' un dato di input reale
  dettato dal calendario (confermato da maintainer, non in discussione). G12: 43 date stage = 16
  on-grid pinzate (112h) + 19 SABATI (131h) + 7 chiusure weekday Natale/Carnevale (45h) = 176h
  off-grid, contabilizzate CORRETTAMENTE come expectedUnscheduledHours (ore conservate: 295 = 112 +
  176 + 7 residuo). Il "deficit stage" (run 1993 vs golden 2320) e' un ARTEFATTO DI METRICA:
  confronta on-grid-pinnato (run) contro totale-incluso-off-grid (golden lista i sabati). I ~272h
  off-grid sono in expectedUnscheduled, non mancano.
- Raccomandazione: INDAGINE CHIUSA, niente da fissare. La metrica "stage run vs golden" va letta
  netto-off-grid. Residuo minore ~7h (1 Friday 2027-01-22, probabile stageSkippedNoBudget, follow-up
  opzionale). Dettaglio raw: SPIKES-RAW.md sezione SPIKE 8b.

## SPIKE 3 - Ancora settimanale riservata nel template (TASK-450 dir c) (DONE, checkpoint)
- Ipotesi: riservare slot-0 a costruzione (WeeklyTemplateBuilder riserva (venerdi, slot-0) per
  G11) fa' che M32 difenda l'ancora e chiuda i 15-16 ritardi di G11 ven. MECCANISMO GIA'
  ESISTENTE: e' TASK-430 `reserveSlot0Anchors` (flag `planner.construction.slot0-balancing.enabled`,
  default OFF), gia' bocciato al gate2 a SOLVE COMPLETO (-hard/-full-day); qui misurato al
  CHECKPOINT DI COSTRUZIONE + day-start-anatomy (la lente che il gate2 non aveva usato).
- Risultato: NON REGGE, e per una ragione strutturale. G11 venerdi e' GIA' ancorato a slot 0
  a fine costruzione in ENTRAMBI gli stati (baseline: Matematica/T08 @h8; flag-ON: Disegno
  Edile/T12 @h8; 0 celle "G11 venerdi" nella matrice day-start di costruzione in entrambi).
  I 15-16 ritardi sono EROSIONE DEL SOLVER: a costruzione 2026-09-18 inizia alle 8, a SOLVE
  FINALE (cert) inizia alle 9 (hour 8 vuoto). reserveSlot0Anchors cambia *quale* corso sta a
  slot 0, non *se* slot 0 e' ancorato. Metriche costruzione flag-ON ~neutre (ricorrenza
  76,3->76,8%, full-day scoped 85,5->84,9%, late-days 25->20); ma INTRODUCE nuove celle con
  scarto>=3 (G12 ven 4, G14 giov 4) mentre ne chiude altre (G07 lun 6->1): sposta, non
  risolve. Il checkpoint di costruzione NON PUO' validare "15-16->0" perche' li' e' gia' 0.
- Raccomandazione: BUTTARE la direzione (c) "riserva a costruzione". CORREGGE la raccomandazione
  FASE D/SPIKE 5 (che ipotizzava la costruzione come leva): il problema NON e' di costruzione
  (l'ancora c'e' gia'), e' di DIFESA IN SOLVE - M32 non tiene l'ancora sotto pressione H1
  (teacher-contention, FASE D: 396/666 candidati teacher-busy allo slot-0 ven). La leva e'
  solve-time (peso M37/M32 sulla cella, o pinning esplicito di G11 ven slot 0), non la
  costruzione. Dettaglio raw: SPIKES-RAW.md sezione SPIKE 3.

## SPIKE 2 - Allocatore lab a tag mono-stanza (TASK-449) (DONE + PROTOTIPO flag-OFF)
- Ipotesi: un allocatore pre-assegnazione pinni i blocchi lab mono-tag CON stanza su celle disgiunte
  (cadenza golden), chiudendo le ~189 ore lab room-null.
- Risultato: prototipo implementato e verificato (commit 4d33abf2, flag default-OFF, no-op esatto
  76,3%/85,5%). Pinnerebbe 1025h/1494 (69%) col stanza (acconciatura 365/719, termoidraulica 242/313,
  meccanica 418/462). CAVEAT ONESTO: "total"=pool lab completo, NON le 189 (room-null a solve finale);
  provare la reale riduzione delle 189 richiede A/B solve live (vietato, checkpoint only). FINDING
  STRUTTURALE: soffitto di CAPACITA' - la singola stanza acconciatura non regge 5 gruppi, G12 e
  G14 prendono 0 (celle golden collidono con 1/2/G10); meccanica invece copre al 90%.
- Raccomandazione: TENERE il prototipo (pattern 435 corretto) ma l'allocatore DA SOLO non chiude le
  189: il collo acconciatura e' capacita' (1 stanza, 5 gruppi), non search. Leva sull'overflow =
  piu' stanze/tag (scuola) o de-tag (SPIKE 4, servizio-stilistico ~85). Adozione = maintainer dopo A/B
  staged che misuri la reale riduzione room-null. Dettaglio raw: SPIKES-RAW.md sezione SPIKE 2.

---

## SINTESI del turno 29/07 pomeriggio (connessione dei finding)

CONVERGENZA: i finding si ricompongono in un quadro coerente. La LS erode la ricorrenza delle
materie AULA/teoriche unpinned (Consulenza -65, Informatica -40/-43, Merceologia/Tessile -47) per
riempire buchi e bilanciare (erosion-hotspot). MA i buchi headline (G11 ven slot-0) sono
departure-unsafe: la depth-2 non li chiude (TASK-451: 0/15, kill = la lezione di G2 nello slot
bersaglio e' l'inizio della giornata dell'altro gruppo). Quindi la LS paga il costo di ricorrenza
(perde cadenza settimanale delle aula) per buchi che NON riesce a chiudere a depth-2. Il discriminatore
dell'erosione NON e' il peso M32 (hours-proxy non supporta) ma lo STATO DI PIN: lab pinned immobili
(non erosi), aula unpinned mobile (erosa).

MAPPA DECISIONI (tutto tracciato, niente in attesa attiva):
- CHIUSO TASK-451 depth-3 (misurato 29/07): catena-3 = **0/15** sui buchi HARD finali (SWAP ed
  EXTEND). 5 gap donatore a coda pinnata (G09 Confezione/T16), 10 a docente-coda occupato
  a slot0 (trappola riprodotta, depth-5). Brute-force skeptic CONFIRMED. Per il gate "crolla a
  zero -> chiudi senza codice", LA TEMA SI CHIUDE senza prototipo (i 15 buchi G11 ven slot-0
  sono infattibili via catena timeslot same-day a depth 2-3). Script
  `scripts/analyze/chain-feasibility-kpi.py`, dettaglio SPIKES-RAW.md "TASK-451 depth-3".
- GATED maintainer: adozioni (flip default 435, SPIKE 2); verdetto erosione per-identita' (WORK 2:
  tool pronto, serve un solve staged flagged per produrre i numeri).
- DEFERRED m-13 (TASK-452): smarcare il golden dai runtime (435 + SPIKE 2 leggono seed/lessons.csv).
- ESCLUSO (direttiva correttezza-vs-costo): tarare pesi per l'erosione (e' costo di search, non
  correttezza di modello - non si annacqua il modello per ragioni di costo).
- DATA scuola: over-capacity FC pianificata (835h aula+lab vs 750h di corso, -1h rasato su molti
  giorni, run la concentra piu a fondo del golden); stage off-grid (sabati/chiusure, CHIUSO, input reale).
- CHIUSI: deficit stage (artefatto metrica on-grid vs totale); anatomia G04 (input-driven, non
  difetto); SPIKE 3 (slot-0 reservation non regge, l'ancora c'e' gia a costruzione).
