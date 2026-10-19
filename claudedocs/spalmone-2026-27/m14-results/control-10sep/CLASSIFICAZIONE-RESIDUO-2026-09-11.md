# Classificazione del residuo control-10sep (m-14, 11/09, Fedora sui artifact di zai)

Consegna [−17/0]: H34=10, H0=6pt/3 lezioni, H7=1. Fonti: analysis.json + lessons.txt
(74404683) + DB del profilo control-10sep. Ogni cella/lezione riconciliata meccanicamente
(il conteggio delle celle vuote da lessons.txt + fogli modality A/L = 10/10, identico al
provider).

## H0 (6 punti): 3 ore G14 Confezione / T16 non collocate

Verbatim dal DB: tre righe `G14 | Confezione | T16 | 30`. E' ESATTAMENTE la
famiglia del triangolo Q1 del questionario referente: T16 al venerdi' e' satura di
Confezione MODA (G01 89h + G09 51h nel golden, sistematico) e la costruzione non
trova cella per le ore G14. slotretry ne perdeva 1 (2pt), control-10sep ne perde 3
(6pt): stesso vincolo, scambio diverso dentro lo stesso bacino.

**Cura identificata (golden-prima, Q1)**: il golden colloca TUTTA la Confezione G14
(30h T16) al GIOVEDI' e i venerdi' (3h × 14) con Competenze Digitali, Comportamento
Sociale e Orientamento. Le opzioni A (taglio 2h) e C (venerdi' da 2h) del questionario
sono smentite dai dati (venerdi' pieni a 3h, pool 115 intero collocato nel golden);
l'opzione B e' inutile (il golden non sposta nulla). La cura e' SOLVER-SIDE: il template
non deve pianificare Confezione-G14 al venerdi' (famiglia teacher-locked). Task aperto.

## H34 (10 punti): tutte classificate, nessuna nuova famiglia

| cella | famiglia ledger |
|---|---|
| G06 2027-06-04, 06-09, 06-10 (quota 4) | coda giugno, import teacher-locked (06-10 era gia' ALLOWANCE_EXHAUSTED nel censimento TASK-590: claim > pool a fine anno) |
| G10 2027-06-09 (quota 5, mod=L) | coda giugno, import teacher-locked |
| G11 2027-06-03 (quota 4) | coda giugno, import teacher-locked |
| G09 2027-05-10, 05-24 (quota 5) | coda maggio, import teacher-locked (nuove rispetto a slotretry che aveva 06-03) |
| G12 2027-05-21 (quota 4, ven) | NEVER_ATTEMPTED del censimento: NESSUNA cella template il venerdi' (famiglia template-side, non import) |
| G13 2027-04-26, 05-17 (quota 5, lun) | appena fuori/nei margini del periodo di stage, finestra 56gg del pre-booking (il ledger aveva 2026-11-06) |

Nessuna cella input-locked G14/RA (le 6 G14 del vecchio censimento sono uscite:
l'H33 e' a 0). Lo spostamento della popolazione (3 celle slotretry -> 10 control, tutte
maggio-giugno + G13 lunedi') e' coerente con la varianza del bacino + pool-cap-fill ON
(unico default comportamentalmente nuovo fra i due run, validato solo a banco prima d'ora).

## H7 (1 punto): NON riconciliato con l'SQL grezzo

La query per (docente, giorno) > COALESCE(maxDailyHours, 6) non trova nulla: il cap
effettivo (getEffectiveMaxDailyHours) diverge dalla colonna per almeno un docente, oppure
il conteggio del vincolo include lezioni che la mia query filtra. TODO proprio: estrarre
la coppia dal drill-down con il metro del vincolo. 1 punto, non blocca nulla.

## Sintesi per TASK-596 (gate di adozione)

Il residuo corrente si decomponde in: (a) G14 Confezione 6pt = cura solver-side
golden-prima identificata (Q1, task da aprire); (b) coda maggio-giugno 10pt = famiglia
import teacher-locked gia' nota (search-side/multi-start, o terapia input se referente
dichiara giorni voluti); (c) H7 1pt da classificare. La cura input RA (TASK-592, applicata
l'11/09) aggiunge 41h al pool di G08 e incide sul sanity-rosso G08 ma non su H34
(G08 e' escluso dallo scope H34: il suo target e' la forma-b pool-cap).
