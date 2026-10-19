# TASK-472 pilota: no-2h-teoria (M-TR / TASK-468) - classificazione H-vs-M

Zai, 2026-08-01 (durante i run appaiati post-cura). Caso pilota della chiave di lettura
di maintainer (TASK-472): per ogni vincolo scritto violato dal golden, classificare H-vero vs
M-alto-valore con report ripetibile delle occorrenze + spiegazione tentata.

## Strumento (ripetibile)

`scripts/analyze/theory_adjacent_report.py` - report input-agnostico (golden CSV o run
`lessons.txt`) che riusa la classificazione teorica di `theory_adjacent_residual.py`
(mirror del vincolo M-TR via `SubjectNormalization.isTheoryForRule` + DB `Course.isLab`).
Per ogni cella (gruppo, materia teorica, data) con 2+ ore: occorrenza + spiegazione tentata
('capacity-driven', 'deep-block', 'inspiegata') + clustering + proposta di tier golden-first.
Output golden: `no-2h-teoria-golden-report.txt`.

## Misure sul golden (raw CSV, mode "stretta" = M-TR)

- celle teoria (gruppo,materia,data): 6218; **432 violate (2+ h stessa teoria), 6,9%**.
- residual (somma eccessi = unita' penalty M-TR): 541. (Il runtime da' 440 sul golden
  IMPORTATO: le 41 righe raw `RA` di G08 sono rinominate a `Recuperi ed Approfondimenti`
  dal `GoldenScheduleImportService`, vedi AC#1. Il conteggio celle = 432 coincide con AC#1.)
- capacity-driven: **103/432 (24%)**. Il resto e' sporadico/spread.
- clustering: weekday Wed 109 > Thu 96 > Mon 93 > Tue 82 > Fri 52; subject Scienze 86 >
  Matematica 63 > Recuperi 33; gruppo G01 59 > G04 51 > G13 49; month picco mag-2027.

## Classificazione (verdetto)

**M-HIGH-VALUE, non H.** Golden-first: il golden lo viola 432 volte -> un HARD a copertura
totale vieterebbe il golden (infeasible). Quindi M. Ma NON M-debole: solo il 24% delle
violazioni e' capacity-driven (materie ad alta montura costrette a ripetersi); il 76% e'
sporadico, cioe' la regola HA leva per spingerle fuori.

## Cosa questo dice sulla cura (collega al DIAGNOSIS M-TR)

Le violazioni sono **per lo piu' sporadiche, non strutturali**: la regola ha spazio di
manovra. Questo coerisce con la DIAGNOSI M-TR (CLAUDE.md): i blocchi 2h teoria **nascono
in costruzione** (residuo fine construction = 1672) e la LS li eredita senza crearli; lo
sweep peso 1..1000 era inerte (~1% su 1000x) perche' il peso e' il lever sbagliato. La
cura sta a monte (WeeklyTemplateBuilder: dedup per (gruppo, giorno, materia-teorica) nel
placement greedy), non sul peso. Il peso inerte NON significa "regola debole": significa
"lever sbagliato". Conferma TASK-468 medium + indica dove costruire la sostanza.

## Nota anti-overfitting (maintainer)
L'euristica di costruzione "evita 2h teoria same-day" inferita dal golden e' una
CANDIDATA legittima (riduce lo spazio di ricerca), non overfitting: il report mostra che
la pratica golden la rispetta al 93% (6218-432 su 6218), quindi replicarla e' fedelta',
non sovradefinizione.
