# TASK-517: golden placement-parity check (laptop, 17/08 notte)

> Filone 3 del mandato notturno. Script: `scripts/analyze/placement-parity-check.py`
> (read-only, exit 0). Chiude la famiglia B della meta-indagine: la direttiva
> golden-first (TASK-379) controlla le REGOLE, non i PIANI.

## Cosa misura

Tre assi, tutti PIANO (lezioni PINNATE di un export) vs GOLDEN sulla stessa coppia:

- **A. densita' per-weekday (docente, gruppo)**: flag quando il piano e' piu'
  denso del golden di >= 1h medio/giorno su TUTTI i weekday comuni (>= 3 giorni
  per lato). Firma chain r2-6: T15/G04 saturo a 6h dove il golden tiene 4.
- **B. ancora slot-0 (gruppo, weekday)**: flag quando il piano ancora al 100%
  dove il golden sta sotto il 99,5%. Firma TASK-430.
- **C. concentrazione (gruppo, materia)**: flag quando il golden spalma su >= 2
  weekday (ognuno >= 15%) e il piano concentra > 95% su uno. Firma TASK-432.

## Validazione (AC#3) e calibrazione (AC#4)

- `--self-test`: golden contro se stesso = **0 flag** (calibrazione); le tre
  firme storiche perturbate sul golden scattano tutte:
  A (T15/G04 +2,0/+1,9 h/giorno), B (G01 lunedi' 100% vs golden 97%),
  C (G03 Scienze 100% vs golden spalmato su 5 weekday).
- Piano REALE della chain (run4 export, ab-on-run4): **23 flag**, tra cui
  esattamente la firma della famiglia B: [A] T15/G04 +1,9 h/giorno,
  T15/G08 +5,0, T15/G13 +2,2; [C] Lavorazioni meccaniche G04
  100% mercoledi' vs golden spalmato 70/82 (mer/gio).
- I piani ratificati non aggiungono pin oltre il golden (import calendario) e
  il golden importato coincide con se stesso: 0 flag per costruzione.

## Proposta di paragrafo CLAUDE.md (AC#5, DA RATIFICARE, non applicato)

> **Direttiva golden-first estesa ai PIANI (famiglia B, TASK-517).** Nessun piano
> di pre-assegnazione puo' essere piu' rigido della pratica di piazzamento del
> golden sulle stesse coppie: per ogni (docente, gruppo) che il piano pinnerebbe,
> la distribuzione di carico per weekday del piano non puo' essere ovunque piu'
> densa di quella del golden (asse A); le ancore e le concentrazioni monografiche
> seguono la frequenza del golden, non il 100% (assi B/C). Verifica meccanica:
> `scripts/analyze/placement-parity-check.py --plan <export>` prima di ogni gate
> di adozione di un meccanismo che pinni; un piano flaggato non si adotta (o si
> dichiara la divergenza come domanda alla scuola).

## Parentela col TASK-518

Stessa idea vista dai due lati: il check (TASK-517) e' la RILEVAZIONE a
posteriori (audit), il budget-reservation/gate (TASK-518) e' la PREVENZIONE a
monte. Il check resta il metro con cui si giudica ogni futuro round.
