# Verifica Fedora A/B val408 (treno 408+416+376+418), 2026-07-25 ~15:00

Verifica critica SUI RAW di entrambi i run (KPI ricalcolati qui dai lessons.txt; score e
analysis letti dai file): il VERDICT di zai coincide. Baseline: val413-run1/2 (stack con
M32, senza il treno).

## Cifre verificate (val413 -> val408)

| Metrica | val413 r1/r2 | val408 r1/r2 | Gate |
|---|---|---|---|
| hard | 0 / -1 | -1 / -1 (solo H17, 1 match) | OK, in banda |
| box (H0) | 0 | **0 / 0** (12.286 = 12.301 - 15 ore stage off-grid del fix chiusura: conservazione esatta) | OK |
| analysis vs score | divergevano di ~271k (M32 assente) | **IDENTICI al centesimo su entrambi**; M32 presente (-279,8k/2.106 e -284,9k/2.136) | OK, **AC#1 TASK-416 provata live** |
| lezioni 2027-01-22 | 61 | **0 / 0** | OK, typo chiusura |
| full-day | 79,0 / 78,1% | **79,9 / 80,0%** (record campagna; G11 97%, G07 96%, G12 74%) | OK, leva giorni-fantasma |
| M20+M20.2 | -1.148k / -1.176k | **-983k / ~-1,0M** | OK, -165k |
| ricorrenza unpinned | 53,5 / 53,1% | 52,8 / 53,6% | OK, stabile |
| cadence burst>2 | 79,1 / 79,1% | 81,6 / **79,1%** | OK (r1 = varianza) |
| gap-weeks/pair | 9,84 / 9,49 | 9,86 / 9,55 | pari |
| day-start | 93,3 / 93,6% | **93,7 / 93,7%** | OK |
| nitidezza pooled | 76,2 / 74,8% | **79,7 / 77,5%** | OK, su |
| moda-knot | 0 | 0 / 0 | OK |

## Scoperta strutturale: attribuzione dell'erosione per fase (prima volta, AC#5 TASK-415)

I dump per confine di fase (flag TASK-415 attivo sul launcher) mostrano, IDENTICO nei due
run: la costruzione consegna ricorrenza unpinned **74,3%**; la fase 2 (LS Compactness) la
erode a **52,8 / 53,7%**; le fasi 3-6 non muovono piu' nulla (la ruin-recreate di fase 3
e' inerte per la terza validazione consecutiva). Conseguenze gia' codificate nel backlog:
- il cluster benchmark metaeuristiche (testa TASK-280) ha ora un bersaglio PRECISO:
  mosse/accettore di fase 2 template-rispettosi, non la fase 3;
- TASK-419 (solve a stadi con promozione pinned + degrado a quasi-pinned, proposta maintainer)
  ha la sua evidenza quantitativa: col template pinnato la ricorrenza finale sarebbe ~74%
  by construction, contro il tetto ~53% del single-solve con M32.

## RACCOMANDAZIONE (ratifica a maintainer): ADOTTARE il treno cosi' com'e'

Tutti i gate passano su due run concordi: la leva giorni-fantasma fa esattamente quanto
promesso dalla discovery (full-day record, M20 -165k, i gruppi stage-window in recupero
netto), l'osservabilita' M32 e' ripristinata (analysis == score), nessun KPI regredisce
(l'unico sospetto, il burst di run1, e' varianza smentita da run2). Nessun retuning
necessario. Prossima decisione strategica in coda: TASK-419 vs benchmark fase-2 (le due
strade attaccano lo stesso gap; i numeri per scegliere sono sopra).

## Stato

- Origin: `589f2cc4` (artifact val408 + VERDICT, zai ~14:45).
- Backlog: 408, 416, 415 chiuse Done con cifre verificate; coda ordinata attiva
  (ordinali 100-600 + dipendenze per `backlog sequence`).
- Nessuna adozione committata oltre a quanto gia' in HEAD (il treno E' in HEAD dal push
  di stamattina; "adozione" qui = non revertire e proseguire la coda): decisione a maintainer.
