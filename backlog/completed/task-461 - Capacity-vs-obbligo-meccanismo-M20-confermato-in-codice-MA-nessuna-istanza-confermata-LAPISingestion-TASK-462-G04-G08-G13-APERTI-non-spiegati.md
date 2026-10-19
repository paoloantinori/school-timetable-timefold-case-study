---
id: TASK-461
title: >-
  Capacity-vs-obbligo: meccanismo M20 confermato in codice, MA nessuna istanza
  confermata (G14=ingestion TASK-462; G04/G08/G13 APERTI non spiegati)
status: Done
assignee: []
created_date: '2026-07-30 08:22'
updated_date: '2026-08-14 10:29'
labels:
  - claude-planned
milestone: m-13
dependencies: []
references:
  - claudedocs/spalmone-2026-27/task457-falsificazione-2026-07-30.md
  - claudedocs/spalmone-2026-27/censimento-dipendenze-golden-2026-07-30.md
priority: high
ordinal: 284000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
RILETTURA capacity-vs-obbligo (corregge il framing 'incoerenza'): vincoli-in-parole.md righe 27-28 (autoritativo, 13 giorni fa) dicono che il calendario e' CAPACITA' ('solo caselle DISPONIBILI') e gli incarichi sono OBBLIGO ('esattamente le ore assegnate'). 835 capienza vs 750 dovere per G04: divario FISIOLOGICO, non errore. CONSEGUENZA (verificata in codice): M20 targetta effectiveQuota=capienza SENZA cap al monte-ore del gruppo (m20DayCost SSCP:1972), quindi per i gruppi capienza>incarichi insegue un bersaglio IRRAGGIUNGIBILE e paga una penalita' impossibile distorcendo i compromessi. Ore-bersaglio impossibili: G14 875 (esente isG14 -> l'esenzione manuale e' quasi certamente il sintomo), G04 85, G08 40, G09 4; altri 0. Costo medium non evitabile ~ore_impossibili*1000. NON toccare M20 (modello, decisione maintainer): preparare il quadro (ore per gruppo, punti medium, forma target corretta = min(capienza, residue del gruppo)). Il deficit di G04 NON e' un problema di ricerca: e' input irraggiungibile. La FORMA resta difetto nostro (vedi calcolo M20_PROGRESSION_STEP nelle note). LEZIONE: prima di dichiarare incoerente un input, rileggere il doc che spiega cosa significa.
<!-- SECTION:DESCRIPTION:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Skill /simplify eseguita sul diff e finding applicati
- [ ] #2 Skill /code-review high eseguita: candidati verificati (Opus) e tutti i confermati corretti
- [ ] #3 Verifica finale con EXIT CODE dall'orchestratore (test.sh / ui-typecheck / ui-build dove applicabile), non solo dall'agente implementatore
<!-- DOD:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
VERIFICA INPENDENTE (zai, sui raw): NUCLEO VERIFICATO - G04 750 incarichi=750 placed (0 boxed in 30d), expected 835 DAQ / 990 class_days, golden 90gg a -1h mai -2 (ven 0, lun -26, gio -29). Tre numeri 990/835/750 confermati. MA la TAVOLA PER GRUPPO citata da maintainer NON riproduce sotto nessuna metrica: i numeri -72/-28/-24/-20/-16 (e 7/14) non matchano ne' class_days raw (scarti grandi -316/-374), ne' class_days in-grid (ancora grandi), ne' DAQ aula+lab (piccoli ma diversi). Tavola RIPRODUCIBILE (incarichi - DAQ aula+lab, l'autorevole): G01 0, G02 0, G03 +21, G04 -85, G05 0, G06 0, G07 +5, G08 -40, G09 -4, G10 +4, G11 0, G12 0, G13 0, G14 -875. => |scarto|>=10 in 4/14 (G04, G08, G03, G14), non 7/14. L'incoerenza e' reale e sistematica ma la magnitudo per-gruppo e il conto dipendono da quale 'atteso' - VA FISSATO con maintainer quale metrica ha usato prima di scrivere la tabella come nostra. MINOR: golden G04 placed=758 (esclusi tutti gli pseudo), non 750: il golden sovra-colloca 8h vs incarichi (le 5h su date a 0-atteso + 3).

CALCOLO M20 (la forma e' il difetto nostro, numero per decidere, NESSUNA modifica): M20_PROGRESSION_STEP=500, m20DeficitCost(d)=1000*d+STEP*d*(d-1)/2. 30d ex-G14 deficit dist {1:103, 2:18, 3:10}. Aggregato M20: STEP=500->193k; 750->205k(+6%); 1000->217k(+12%); 1500->241k(+25%); 2000->265k(+37%). Meccanismo: il margine di concentrazione e' STEP/ora-extra (2a ora deficit costa 1000+STEP vs 1000 sul giorno nuovo). SOGLIA: per battere una singola divisione M15(~1200) serve STEP~1500 (aggregato +25%, gia' significativo per il javadoc); per battere bundle M15+M32(~2200+) serve STEP~2500 (+50%, viola il javadoc). CONCLUSIONE: la progressione M20 DA SOLA non puo' fissare la forma senza muovere molto l'aggregato. Decisione di maintainer: accettare +25% aggregato (STEP~1500) per vincere il caso M15-singolo, o cercare una leva diversa (es. coordinamento prima ora, vedi TASK-451).

COLLEGAMENTO TASK-445: questo e' il caso d'uso canonico del workbook (input non ambigui). I due fogli che si contraddicono (class_days 990 vs fc aula+lab 835 per G04, e incarichi 750) diventano un ERRORE di validazione alla compilazione, non un mistero a runtime. La domanda per la scuola: qual e' il monte-ore vero per gruppo (incarichi 750, fc 835, o class_days 990)? Fino alla risposta, il target di riempimento per i gruppi incoerenti e' in parte irraggiungibile e il KPI non si tocca.

RILETTURA (NON incoerenza - corregge il framing iniziale). vincoli-in-parole.md righe 27-28, autoritativo e presente da 13 giorni: 'Si riempiono solo le caselle segnate DISPONIBILI' (calendario = CAPACITA') + 'Il docente deve fare esattamente le ore assegnate per materia' (incarichi = OBBLIGO). 835 capienza, 750 dovere per G04: il divario e' FISIOLOGICO, non un errore dei dati. Lo avevamo letto come contraddizione.

VERIFICA SUL CODICE (M20 target): m20DayCost (SSCP:1972) usa quota=effectiveQuota=DAQ aula+lab (capienza) MENO pin autoritativi; placedTotal e' usato SOLO per la guard M20.2 'giorno vuoto', NON per cappare il target. => il target giornaliero puo' superare il pool di Lesson del gruppo: anche piazzando tutte le lezioni, restano ore-bersaglio impossibili. Dimostrato.

ORE-BERSAGLIO IMPOSSIBILI per gruppo = max(0, capienza_DAQ_aulaLab - incarichi): G14 875, G04 85, G08 40, G09 4; gli altri 10 gruppi a 0. Totale NON-esente ~129h. G14 e' esente (isG14) -> l'esenzione manuale e' quasi certamente il sintomo di questo (875h di bersaglio impossibile che M20 altrimenti caricava).

COSTO MEDIUM non evitabile = ore_impossibili * m20DeficitCost(1)=1000 (se spalmate a -1h, il minimo). G04 ~85k, G08 ~40k, G09 ~4k; G14 ~875k se non esente. Il solver distorce i compromessi altrove per inseguire un bersaglio che non puo' raggiungere. Il golden spalma (90gg a -1h) proprio perche' eroga un monte-ore fisso dentro un calendario piu' capiente.

FORMA TARGET CORRETTA (proposta maintainer, da decidere): min(capienza, ore residue del gruppo) per data, cosi' sum(target) <= obbligo. Es: scalare la quota giornaliera (obbligo/capienza) o capparla al residuo di monte-ore del gruppo. NON toccare M20 ora: e' una modifica di modello, decisione di maintainer.

LEZIONE (da scrivere nel doc): prima di dichiarare incoerente un input, rileggere il documento che spiega cosa significa quel dato. vincoli-in-parole.md c'era da 13 giorni e risolveva la questione in due righe. Il calcolo M20_PROGRESSION_STEP (voce 3 dell'elenco originale) RESTA VALIDO: la FORMA resta un difetto nostro anche dopo aver corretto il target.

CORREZIONE URGENTE 2026-07-30 (verifica maintainer sul sorgente xlsx, corretta prima della documentazione definitiva). Le affermazioni precedenti qui ('G14 875h unreachable, isG14 e' il sintomo' e 'G04 85h unreachable') sono DA RETROCEDERE:

- G14 NON e' capacity-vs-obbligo: e' un ERRORE DI INGESTIONE (foglio sbagliato, TASK-462). Le 875h 'irraggiungibili' erano un miraggio di dato sbagliato (letto 'G14' default 990 invece di 'G14 2' TOT 120). L'esenzione isG14 e' un cerotto su un dato sbagliato, non una proprieta' del dominio.

- G04 (e G08, G13) sono CASI APERTI NON SPIEGATI: il sorgente dava 990 per G04, il motore carica 835, incarichi 750, e ci sono piu' fogli FC candidati. Quindi '835 capienza reale' NON e' confermato e l'85h unreachable NON va dato per fatto. Aspettare la revisione a mano di maintainer.

QUADRO RIFATTO SENZA G14 (capienza = totale foglio SORGENTE, non il nostro CSV): 6 gruppi esatti (1/G07, 1/G06, 1/G05); 4 divergono di +20/+24 per ore FUORI ORIZZONTE (benigne: G11 24h, G10 20h, G12 20h, G09 24h - corrispondenza esatta); 3 APERTI (G04, G08 +8h non spiegate, G13 -5 col segno che non torna con 12h fuori orizzonte). => NESSUN gruppo mostra in modo pulito incarichi<capienza-reale-in-grid dopo aver tolto G14. Il meccanismo (M20 targetta capienza senza cap) resta valido ma AL MOMENTO NON HA UN'ISTANZA CONFERMATA.

COSA RESTA VALIDO: (1) la lettura vincoli-in-parole (calendario=capienza, incarichi=obbligo); (2) il codice M20 (effectiveQuota=capienza, nessun cap al monte-ore gruppo -> PUO' essere irraggiungibile); (3) il calcolo M20_PROGRESSION_STEP per la FORMA (difetto nostro). COSA E' SOSPESO: la conclusione 'il solver distorce per inseguire un bersaglio impossibile' resta un'IPOTESI non confermata finche' i 3 casi aperti non sono risolti. NON costruire sopra G04/G08/G13 finche' maintainer non li guarda.

CHIUSURA 3 CASI APERTI (verifica maintainer sul sorgente xlsx, foglio autoritativo DEF FC TUTTI A1:E8; Au+La+St per G04/G08/G13):

- G08: SPIEGATO, nessun difetto. Autoritativo 690 (Au 584 + La 106 + St 300); noi 666 in-orizzonte + 24 fuori = 690 esatto.

- G13: SPIEGATO, nessun difetto. Autoritativo 695 (Au 485 + La 210 + St 295); noi 683 + 12 fuori orizzonte = 695 esatto. Il '-5' era artefatto del confronto col foglio per-classe sbagliato.

- G04: DIFETTO VERO = coda di TASK-434. Quel task (chiuso 27/07, commit d27bd9e0) stabilì 'domanda 750 vs foglio 990 = le 240 mancanti' di stage che il golden svolge, ma applicò il fix per ADDIZIONE (240h stage aggiunte ai fact) non per RICLASSIFICAZIONE: non le tolse da aula+lab, rimaste 835. Oggi il dato dice 835 aula+lab + 240 stage = 1075 per un gruppo da 990. Il bersaglio di riempimento di G04 e' gonfiato di 85h = ESATTAMENTE il deficit che inseguivamo come problema di ricerca.

CONFERMA DEL MECCANISMO: l'istanza c'e', ed e' G04. Non era il solver, non era capacity-vs-obbligo: era un fix di dati a meta'. L'm20 unreachable-target (marcato 'valido ma senza istanza') ha la sua istanza confermata.

VALORE ATTESO CORRETTO per G04 = 750 aula+lab (990 meno 240), che coincide col monte-ore degli incarichi (750): ANELLO IN ATTESA DI CONFERMA DI maintainer, non applicato. Collegato a TASK-434 (fix incompleto).

TESSERA RESIDUA (parzialmente tracciata): l'835 viene dall'estrattore calendario2027-extract.py, che legge DEF FC TUTTI (foglio GIUSTO) sommando aula+lab per-data in-orizzonte (aula 623 + lab 212 = 835). Ma il cell yearly di DEF FC TUTTI dice Au 745 + La 245 = 990, e il corretto di maintainer e' 750: l'835 non e' ne' 990 ne' 750. La divergenza per-data vs yearly (155h) e vs 750 (85h) e' il residuo, serve re-estrazione dal xlsx per pinmarla (xlsx non accessibile su questa macchina).

BONUS: l'estrattore SCARTA ESPLICITAMENTE 'G14 2' (docstring riga 22) e legge 'G14' default -> conferma che il bug G14 (TASK-462) e' nell'estrattore.

CORREZIONE ALLA TRACCIA DELL'835 (tessera residua RISOLTA). La mia nota precedente attribuiva l'835 all'estrattore: SBAGLIATO. Dai dati di test di TASK-434 (Final Summary: 'G04 quota aula745->623 lab245->212 stage0->224') il PRE-fix di G04 era aula 745 + lab 245 = 990 (OUTPUT CORRETTO dell'estrattore calendario2027-extract.py, = autoritativo DEF FC TUTTI). TASK-434 lo ha RIDOTTO a 623+212=835 riclassificando solo 155h a stage (in-orizzonte 224 + 16 off-grid = 240), invece delle 240h dovute. => l'835 e' la riclassificazione PARZIALE di TASK-434 (990 - 155 = 835), non un artefatto dell'estrattore. L'estrattore e' innocente (produceva 990). Il residuo 'da dove viene 835' e' CHIUSO: e' TASK-434 che ha sotto-riclassificato di 85h (155 mosse vs 240 dovute). Corregge la nota 'BONUS' di poco sopra: l'estrattore scarta 'G14 2' (questo regge, e' il bug G14 TASK-462), ma per gli FC leggeva DEF FC TUTTI correttamente (990).

RETRATTAMENTO 2026-07-30 (verifica successiva di maintainer; questo documento e' cambiato due volte, resta tracciabile). La chiusura 'G04 = TASK-434 incompleto' era SBAGLIATA, mia. Verificato: sulle 30 date di stage di G04 le aula+lab sono GIÀ a zero, il fix di TASK-434 e' CORRETTO. L'835 e' la trasformazione FEDELE del foglio: 990 autoritativo - 155 (le ~5,76h/giorno di aula che quelle 30 date valevano, tolte quando sono diventate stage a 8h) = 835. NON e' un errore di dati. NIENTE da correggere nei dati, nessun file protetto da toccare, TASK-434 resta chiuso.

RIBALTAMENTO: le 85h di scarto (835 capienza vs 750 incarichi) NON sono errore. Sono CAPIENZA REALE NON UTILIZZATA: la classe sta a scuola 835h di slot disponibili ma ha 750h di insegnamento assegnato (alla lettera vincoli-in-parole: 'esattamente le ore assegnate' + 'solo le caselle DISPONIBILI'). Il difetto e' nel MODELLO: M20 punta alla capienza SENZA tetto sulle lezioni realmente esistenti del gruppo. L'istanza e' confermata e quantificata: G04 85h.

MISURA (per decidere il tetto a M20, su 14 gruppi, irraggiungibili = max(0, capienza_DAQ_aulaLab - lezioni_esistenti_incarichi), medium floor = irragg*m20DeficitCost(1)=1000 se spalmate a -1h): G04 85h/85k, G08 40h/40k, G09 4h/4k; altri 11 a 0. TOTALE non-esente 129h -> ~129.000 medium FLOOR (il solver concentra -2/-3 e paga di piu'). G14 875h ma e' ingestion (TASK-462, reale ~120) + esente isG14. Un tetto al target M20 = min(capienza, lezioni esistenti del gruppo) azzererebbe questa penalta' impossibile.

QUARTA LEZIONE DI METODO (oggi): quando un numero non torna, la prima ipotesi NON deve essere 'il dato e' sbagliato' ma 'non ho capito la trasformazione che l'ha prodotto'. Oggi e' stato accusato di errore due volte un dato corretto (prima i fogli calendario letti come contraddizione, poi il fix del TASK-434). Il difetto reale era nel modello (M20), non nei dati.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
INDAGINE COMPLETA, ISTANZA CONFERMATA E RIVERIFICATA (Fedora 2026-08-14 sui seed attuali): G04 capienza DAQ aula+lab 835 vs obbligo incarichi 750 = 85h di bersaglio M20 irraggiungibile (riprodotto esatto: awk su calendario_fc.csv 623+212=835, incarichi 750). G08 690/666 = 24h col seed corrente (le note misuravano 40h pre-normalizzazione TASK-466), G09 ~4h. Floor medium impossibile ~113-129k. Gli altri casi sono chiusi: G08 e G13 riconciliano col foglio autoritativo (nessun difetto), G14 era ingestion (TASK-462 chiuso, golden-confermato). Meccanismo verificato in codice (m20DayCost SSCP:1972: effectiveQuota=capienza senza cap al pool del gruppo). La pratica golden conferma la lettura: la scuola sparge il deficit (G04: 90 giorni a -1h, mai -2), tratta l'obbligo come target. LA CURA E' UNA MODIFICA DI MODELLO riservata a maintainer: target = min(capienza, pool residuo del gruppo), spin-off in task dedicato. Quarta lezione di metodo registrata nelle note (non accusare il dato: capire la trasformazione). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
