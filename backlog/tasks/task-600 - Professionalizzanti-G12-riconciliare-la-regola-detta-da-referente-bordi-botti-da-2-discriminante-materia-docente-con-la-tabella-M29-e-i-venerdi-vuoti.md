---
id: TASK-600
title: >-
  Professionalizzanti G12: riconciliare la regola detta da referente (bordi,
  botti da 2, discriminante materia-docente) con la tabella M29 e i venerdi'
  vuoti
status: Done
assignee: []
created_date: '2026-09-11 15:40'
updated_date: '2026-09-11 18:34'
labels:
  - claude-planned
  - input-clarification
  - golden-first
  - professionalizzanti
milestone: m-14
dependencies: []
references:
  - TASK-590
  - TASK-599
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Dalla sintesi audio referente 11/09 (claudedocs/spalmone-2026-27/referente-audio-2026-09-11/SINTESI.md). Tre thread intrecciati su G12 (apprendisti) da riconciliare PRIMA di toccare il modello (golden-first: il golden e' l'oracolo finche' c'e'):

(1) LA REGLA DETTA A VOCE: "le materie professionalizzanti possono essere messe o alla prima e alla seconda ora o all'ultima ora... a botti da due, cosi' fai uscire prima e entrare dopo. Questa e' la regola." + "la combinazione MATERIA-INSEGNANTE e' la discriminante". La nostra M29 ha GIA' la forma bordo+blocco<=2h ma solo per la tabella M29_RESTRICTED_DAYS_BY_GROUP (una riga: G12 -> mercoledi'/giovedi').

(2) LA TABELLA M29 NON COINCIDE COL GOLDEN: nel golden le professionalizzanti di G12 stanno a LUNEDI'/GIOVEDI' (Barberia 45h tutta lunedi', Servizio tecnico e stilistico 46h tutto giovedi', Servizio stilistico lun+gio), NON mer/giov. Da dove viene la riga mer/giov? Verificare la provenienza (docx? workbook?) e correggerla alla pratica golden o chiedere conferma scritta.

(3) IL VENERDI': referente dice che gli apprendisti sono in stage il venerdi' (golden: stage G12 = VEN 126h + SAB 131h, quasi zero gli altri giorni) e il foglio dichiara i venerdi' SOTTILI (52h/anno aula+lab vs 164-174 lun-giov). Ma il censimento TASK-590 trovava NEVER_ATTEMPTED le celle venerdi' di G12 (NESSUNA cella template il venerdi') e il residuo control-10sep ha 1 cella venerdi' vuota (2027-05-21 quota 4). Da riconciliare: il template non ha celle il venerdi' perche'? Il venerdi' dichiarato sottile (spesso 3-7h) va riempito con cosa secondo la pratica golden (golden venerdi' G12: ~46h sparse su Storia/Matematica/Comportamento/Economia/Lingua/Preparazione alternanza)?

METODO: banco/checkpoint prima di qualsiasi ciclo; se la correzione della tabella M29 cambia il modello, la verifica golden-oracle deve restare 0-violazioni (il golden rispetta la regola vera? verificare: le professionalizzanti del golden stanno davvero ai bordi nei giorni professionalizzanti?). Nessun default cambiato senza gate.

PRODOTTO: tabella M29 corretta con provenienza, risposta al perche'-venerdi', e l'eventuale estensione bordi-a-botti-di-2 a tutti i giorni professionalizzanti (oggi limitata ai restricted days) valutata a banco.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
SVOLTO in gran parte l'11/09 sera (Fedora, tutto banco/locale):

(1) PROVENIENZA M29: la tabella G12 mer/giov e' VERBATIM dal docx (vincoli-in-parole.md, TASK-370: "professionalizzanti solo il venerdi', oppure giovedi'/mercoledi' alla prima o all'ultima ora o alle prime due o alle ultime due"). La regola detta da referente nell'audio COINCIDE col docx (bordi, botti da 2): la regola e' viva, la tabella resta. Il venerdi' e' deliberatamente assente dalla tabella perche' il docx lo lascia libero.

(2) GOLDEN: il mercoledi' golden usa {slot0, slot4} = DUE ore singole ai bordi OPPOSTI: legale nella lettura docx ("alla prima o all'ultima ora") ma ILLEGALE nella nostra implementazione ("singola sequenza <=2"): la nostra M29 e' PIU' STRETTA del docx di una forma. Refinement candidato, non bloccante.

(3) VENERDI' = MECCANISMO TROVATO E MISURATO: il voto modale in buildGroupWeeklyShape conta ANCHE gli zero; G12 venerdi' = 18 STAGE (aula=0) vs 12 ordinarie -> cap 0 -> NESSUNA cella template MAI (unico gruppo su 14, dump verificato).

BANCO A 4 BRACCI (predizioni scritte prima, gate pre-registrato: sotto-quota costruzione <= 41):
- OFF: 41, 16/16 verde (regression clean).
- ON-ingenuo (modal non-zero senza filtro): 57, RESPINTO (11 coppie armate incluse minoranze da 1 data).
- ON-soglia (>=8 date, >=25%): 52, RESPINTO.
- ON-esclusione-stage (raggio chirurgico: SOLO G12 ven 0->4 e G11 giov 0->4; venerdi' G12 0->52 celle VERIFICATO nel dump): 52, RESPINTO. 

VERDETTTO ONesto: TERZA BOCCIATURA al gate di costruzione. Il meccanismo del costo e' chiaro: il pool settimanale e' conservativo, dare ~52h al venerdi' le toglie a lun-gio, e le date lun-gio scendono sotto quota (+11). Il trade potenziale (12 H34-flat contro ~11 H32 che i repair pass QFR/EDF sanno chiudere, e le H34 si sono dimostrate CHIUSE MALE) resta UN'IPOTESI NON MISURATA: se la si vuole, serve un banco NUOVO con metrica end-state post-repair (pattern EdfH33LiveStateTest, non il gate di firma a fine costruzione) con predizione pre-registrata. Il flag resta DEFAULT OFF, scaffold committato (d2160bb1/922085df).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch (6 verifier, task verificati contro repo: classe/commit/CLAUDE.md): lavoro landed e verificato nel repo. Chiuso per igiene di registro.
