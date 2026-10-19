# TASK-542 - Reverse-engineering del bacino del golden (mandato maintainer 23/08 07:31)

Dati (Fedora, replicati): 3 buchi interni totali su 2213 giorni-gruppo, 16 late-start,
18 giorni corti. Tesi di maintainer: la compattezza del golden e' COSTRUITA, non riparata.
Questa analisi e' il secondo livello: COME e' costruita. Fonte: `seed/lessons.csv`, solo
lun-ven, elaborazione in `/tmp/golden-basin-raw.txt` (script inline riproducibile).

## (1) Composizione dei blocchi: menu per (gruppo, weekday)

Il golden NON e' un menu fisso per settimana intera: la sequenza modale esatta di materie
combacia solo nel **31.9%** dei giorni (712/2230). MA la struttura e' a due livelli:

- **giorni MONOGRAFICI (lab-day)**: menu fississimo, match 65-79% e blocco mono-docente:
  G13 venerdi' (Lavorazioni/T10, 65%), G14 giovedi' (40%) e venerdi' (79%),
  G03 lunedi'/venerdi' (61%/76%, Laboratorio), G02 venerdi' (RA lab), G06 martedi'
  (Barberia), G08 lunedi' (Lavorazioni, 74%). In questi giorni il menu e' PRATICAMENTE
  IL TEMPLATE: la varianza residua e' la finestra stage che sostituisce il giorno.
- **giorni a menu variabile**: match 15-35%, con un sotto-pattern stabile per PAIA di ore
  (es. G02 mercoledi' 54%: Gestione->Informatica->Italiano->Analisi->Inglese: il menu
  e' quasi fisso, varia l'ordine di 1-2 ore).

**La regola emergente**: il golden costruisce blocchi CONTIGUI di 2-5 ore della stessa
materia prima (lab/monografici) e riempie il resto con ore singole a menu quasi-fisso.
La ricorrenza noto ~76% del resemblance e' la CELLA (gruppo,weekday,slot): il menu-esatto
e' 32% ma il menu-a-blocchi (stessa materia, slot contigui, weekday fisso) e' molto piu'
alto: i blocchi sono l'unita' di costruzione, non l'ora.

## (2) Struttura docente-giorno

- **3.49 docenti distinti per gruppo-giorno** (mediana 4, max 6): il giorno-tipo del golden
  usa 4 docenti per 4-6 ore: UNA MATERIA BLOCCO (2-5h, docente dedicato) + 2-3 ore singole.
- **Matrice docente-weekday: la concentrazione e' la NORMA, non l'eccezione**:
  | Docente | Weekday | Concentrazione |
  |---|---|---|
  | T07 | Mer | 100% (72h) |
  | T01 | Gio | 100% (231h) |
  | T24 | Mar | 93% (190h) |
  | T12 | Mar | 78% |
  | T10 | Ven | 52% (230h, l'altra meta' e' G13 venerdi') |
  Otto docenti hanno >=50% delle proprie ore su UN SOLO weekday. La scuola assegna i docenti
  per GIORNO FISSO: e' la struttura piu' forte del golden (il nostro allocator gia' la
  coglie per i constrained, il golden la pratica per quasi tutti).

## (3) Ricostruibilita' da template+menu

- Exact-cell match vs il menu modale: **51.2%** (6495/12675 celle).
- Con il rispetto dei BLOCCHI (materia, weekday) senza pretesa di ordine interno:
  e' il 76% noto del resemblance (M32 reference).
- Il restante ~24%: finestre stage (che SOPRAVVANNO il giorno: i "Stage x5" nei menu modali
  di 2/G11, G09 mar/mer/ven, G10 mar/mer/gio), ponti/chiusure, e i 16 late-start.
  **Non e' rumore: e' calendario**: il golden cambia menu quando il giorno e' dentro una
  finestra stage del gruppo (menu = Stage x N) o dopo un ponte (13 dei 16 late-start sono
  primi giorni).

## (4) FABBRICANTE DAY-COMPACT (concepito, non implementato)

Design: costruzione che **non puo' creare il buco** per costruzione, non che lo ripara:
1. Per ogni (gruppo, weekday): un blocco contiguo di N ore (N = quota del giorno) a partire
   dal primo slot aperto del calendario (zero H17 e zero H31 per costruzione);
2. La composizione del blocco: menu golden-derived per (gruppo, weekday) come LISTA ORDINATA
   DI BLOCCHI (materia, ore) - non di ore singole - letta dal golden con la stessa tecnica
   della depth map H-COPRESENZA (input-derived quando il golden sparisce: la cadenza del
   foglio incarichi per (gruppo, materia) determina la dimensione del blocco);
3. I docenti: matrice docente-weekday golden-derived (o workbook: "il tuo giorno fisso"),
   che rende la domanda docente per weekday STABILE e prenotabile;
4. Guardia TASK-517 (placement-parity): il piano dei blocchi non puo' essere piu' rigido
   della pratica golden sullo stesso (docente, gruppo): check meccanico pre-adoption;
5. Le finestre stage: il menu del giorno diventa "Stage x N" (come fa il golden), non
   un'eccezione ma il caso previsto.

**Stima d'effetto (da validare)**: costruzione con 0-5 buchi di partenza invece di 66-261.
Il bacino buono (66 buchi) era gia' il deterministico: il day-compact punta a <10, cioe'
profilo deliverable per COSTRUZIONE, non per fortuna del seed.

## Predizione vs misura (iter-19)

- Predetto: template+menu fisso 80-90% delle celle. **MISURATO: 51% exact-cell, 32% menu
  esatto, ~76% cella (nota)**. La predizione era OTTIMISTA: il menu del golden non e'
  fisso, e' A BLOCCHI variabili. La forma corretta del fabbricante e' blocco-contiguo col
  menu a blocchi, non il menu fisso per ora. Smentita parziale registrata.
