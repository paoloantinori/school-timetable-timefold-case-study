# TASK-506 stadio 2: calibrazione golden (16/08 notte)

Proprieta': ogni check dell'audit deve PASSARE sul golden (l'unico input
noto-feasibile). Uno che scatta sul golden e' bug del check o pratica della scuola,
mai soppresso in silenzio.

## Caso G04 H25 (+16): RISOLTO con il metodo golden-oracolo, assorbito

Dati (verificati da me sul CSV e sul golden, coincidono con Fedora): il calendario
DICHIARA 30 giorni-stage x 8h = 240h per G04; il golden colloca ESATTAMENTE 240h
su quelle 30 date (26/04..14/06/2027, tutte 8h); 2 delle 30 (2027-06-11 e
2027-06-14, 8h cadauna) cadono oltre la fine griglia (2027-06-10): in-grid restano
28 giorni = 224h. Il +16 NON e' errore del dato ne' del modello: sono le 2 giornate
stage oltre la fine ufficiale dell'anno. FIX APPLICATO all'audit (dim 7 off-grid
aware): le date stage dichiarate oltre YEARS_END si leggono dal FOGLIO
(calendario_fc.csv, perche' il DB le scarta come out-of-horizon), si riportano in
colonne separate (# off-grid d, off-grid h) e le loro ore si SOTTRAGONO dalla
domanda H25. Esito sul seed: G04 240h - 16 off-grid = 224 domanda in-grid vs 224
capacita' = **GAP 0, il check PASSA**. Verdetto: MODELLO GIUSTO, TENSIONE FITTIZIA (era il conteggio che ignorava l'off-grid), nessuna domanda workbook
necessaria oltre quella gia' aggiunta (estensione anno per lo stage, vedi sotto).

## Dim 8 OVERDEMAND (G07 +16, G08 +16, G12 +11, G03 +9): il golden
LI VIOLA, e sono PRATICA della scuola (voce workbook), non modello stretto

Golden-oracolo applicato: ore lab-tagged del golden per gruppo SU giorni a quota
lab vs FUORI. Il golden piazza consapevolmente ore lab fuori dai giorni-L:

| gruppo | lab-tagged su giorni-quota | FUORI giorni-quota | esito |
|---|---|---|---|
| G03 | 230 | 55 | pratica: lab anche fuori giorno-L |
| G07 | 24 | 40 | pratica: la MAGGIORANZA fuori giorno-L |
| G08 | 130 | 94 | pratica |
| G12 | 91 | 70 | pratica |

Il modello H23 (quota-bound lab lesson su giorno a quota zero) e' pinned-exempt e
il golden e' tutto pinned: cioe' la scuola fa esattamente questo, tiene lab in
giorni che il foglio calendario non marca L. Classificazione: PRATICA (domanda
workbook: i giorni-L del foglio sono vincolanti per i lab o indicativi?). Il check
resta giusto (segnala tensione reale input-vs-input), NON corretto.

## Dim 9 tag-weekday (informatica Gio 9 gruppi vs 2 stanze, acconciatura Gio 4 vs 1):
il golden RISOLVE con la co-presenza, il check e' un falso positivo di GRANULARITA'

Golden-oracolo: concorrenza MASSIMA per (tag, weekday, slot) nel golden:
- informatica: max 2 gruppi concorrenti su OGNI weekday (Lun..Ven): esattamente le
  2 stanze. Mai 3+. La riga "9 gruppi il giovedi'" conta i gruppi che hanno un
  giorno-L quel weekday, NON la concorrenza simultanea: il check aggrega male.
- acconciatura Giovedi': max 2 gruppi concorrenti (G06 T01 + G12 T26,
  ore 08:00, ricorrente): la scuola li tiene INSIEME nella stanza unica come
  co-presenza ospite (il meccanismo H-COPRESENCE/H2-exemption che abbiamo gia'
  modellato, TASK-464).

Classificazione: BUG DEL CHECK (granularita'): la domanda va misurata come
concorrenza (tag, date, slot), non come conteggio gruppi-con-giorno-L. Da correggere
nel prossimo giro dell'audit (non stanotte: la correzione richiede la mappa
co-presenza per distinguere ospite-legale da sovrapposizione illegale).

## Domanda referente aggiunta (domande-brevi.md)

Off-grid stage G04: l'anno si estende per lo stage (11 e 14 giugno 2027, 16h) o
quelle ore vanno recuperate dentro la griglia? (con i numeri sopra).
