# Monte-ore autoritativo per gruppo (chiuso da maintainer, 2026-07-30 mattina)

Fonte: `/home/user/Downloads/Copy of Calendario scolastico 2026_2027.xlsx`, verificata
cella per cella con maintainer. Questa pagina esiste perche' per due gruppi il foglio che fa fede
NON e' quello che porta il loro nome, e quell'ambiguita' ha gia' prodotto un errore di
ingestione e mezza giornata di indagini su "ore mancanti" inesistenti. NON riaprire queste
questioni: sono decise.

## Quale foglio fa fede

- **Gruppi FC e G13**: foglio **`DEF FC TUTTI`**, blocco `A1:E8` (colonne = gruppi
  I / II / G13, righe = Tot / Au / La / St). I fogli per-classe `G04`, `G08`, `G13` e
  le varie "Copia di" NON fanno fede.
- **G14**: foglio **`G14 2`**, blocco `AY1:BA3`. Il foglio chiamato `G14` e' il
  **default non compilato** (aula 990, identico al foglio `Base`) e NON fa fede.
- **Tutti gli altri gruppi**: il foglio con il loro nome, blocco `Totale ORE` presso `AO1`
  (AO2 = aula, AO3 = stage, AO4 = lab).

## Valori decisi (aula + lab = ore collocabili in griglia; stage a parte)

| gruppo | aula | lab | stage | aula+lab |
|---|---|---|---|---|
| G04 | 745 | 245 | 0 | 990 |
| G08 | 584 | 106 | 300 | 690 |
| G13 | 485 | 210 | 295 | 695 |
| **G14** | **70** | **50** | 0 | **120** |
| G03 | 739 | 230 | 0 | 969 |
| G07 | 660 | 30 | 300 | 690 |
| G11 | 640 | 55 | 295 | 695 |
| G02 | 753 | 237 | 0 | 990 |
| G06 | 681 | 309 | 0 | 990 |
| G10 | 466 | 220 | 300 | 686 |
| G12 | 604 | 91 | 295 | 695 |
| G01 | 990 | 0 | 0 | 990 |
| G05 | 990 | 0 | 0 | 990 |
| G09 | 694 | 0 | 300 | 694 |

Note di riconciliazione gia' verificate (non ri-derivarle):
- Gli scarti di +20/+24h fra questi totali e i nostri fact (G11, G10, G12, G09,
  G08, G13) sono ore su date FUORI dall'orizzonte della griglia (14/09/2026-10/06/2027):
  benigni e spiegati.
- G04: il nostro 835 = 990 meno ~155h d'aula sostituite dalle 30 date di stage (8h/g).
  E' la trasformazione FEDELE del foglio (TASK-434 era corretto). Le 85h di scarto con gli
  incarichi (750) sono CAPIENZA NON UTILIZZATA, non ore mancanti: il golden colloca 750.
- **G14 e' l'unico errore di ingestione**: il nostro CSV ha il calendario di G01
  copiato (990h, 171 date identiche al 100%). Fix censito in **TASK-462** (leggere `G14 2`,
  portare lo split aula/lab). Le ore vere: incarichi 115, golden 128, foglio 120.

## Cosa NON e' piu' in discussione

1. "G04 deve fare 835 o 990 o 750 ore?" -> deve EROGARE 750 (incarichi, confermato dal
   golden); 835 e' la capienza di griglia; 990 il totale del foglio pre-stage.
2. "Qual e' il calendario vero di G14?" -> quello di `G14 2` (120h). Non e' una domanda
   per la scuola: e' il TASK-462.
3. "G08 e G13 divergono dal foglio?" -> no, tornano esatti al netto del fuori-orizzonte.
