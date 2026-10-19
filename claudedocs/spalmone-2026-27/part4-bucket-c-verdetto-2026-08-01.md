# Parte 4 - verdetto bucket C (zai, 2026-08-01, durante A/B TASK-468)

Mandato: dal doc `misure-golden-bucket-c-2026-07-31.md`, per le regole che il golden rispetta al
100% e NON sono gia' coperte, prototipi flag-OFF o proponi task (scelta motivata).

## Verdetto: NESSUN prototipo flag-OFF e' giustificato dal bucket C. Si propone un task (T11).

Criterio del programma: regola rispettata al 100% E non coperta E meritevole di prototipo.
Incrocio con le 5 misure:

| regola | rispetto golden | coperta? | prototipabile ora? |
|---|---|---|---|
| (i) Sicurezza 1e 2gg cumulativi | VIOLATA (8h/8sett) | no | no (golden-first: non si modella cio' che la scuola non pratica) |
| (ii) T21 prime ore | VIOLATA (26/42 slot0) | no | no (T21=T30? da confermare) |
| (iii) T11 6h post-stage | RISPETTATA su G10, **off-by-one G08 (5h)**, scope diverso G11 | no | **no, gated referente T4** (vedi sotto) |
| (iv) Termo lab prime 4h | PARZIALE (75% slot0-3) | no | no (8-12 vs 8-13 da chiarire) |
| (v) Stage G10 no-lunedi' | RISPETTATA (0 lun, deliberato) | **SI' (H25 + filtro VRP confinano le date stage)** | no (esclusa dal programma, basso impatto) |

L'unica regola al 100% rispettata e non coperta sarebbe (v), ma il programma la esclude
esplicitamente (H25 gia' confina lo stage alle date con stageHours>0; una regola "no-lunedi"
morderebbe solo se il solver spostasse stage su un lunedi' non-stage, che il date-filter gia'
vietta; il sabato e' off-grid e gia' contabilizzato).

T11 (iii), l'esempio del programma, NON e' al 100%: G10 = 6h pulite in 3 blocchi 2h
consecutivi (modello pulito), ma G08 = 5h (off-by-one) e G11 = 29h di Personalizzazione
(materia pratica, scope diverso). Un vincolo "6h post-stage di curriculum" penalizzerebbe il
golden su G08 (5h < 6h) -> violerebbe golden-first finche' referente non chiarisce il scope (T4).

## Scelta motivata: propongo un TASK (non un prototipo)
Il design di T11 ha domande aperte che un prototipo ora congelerrebbe come ipotesi:
- qual e' il set "curriculum" (Comportamento sociale / Educazione civica / Competenze di
  cittadinanza, ma G11 usa Personalizzazione)?
- G08 e' 5h o 6h (off-by-one reale o arrotondamento di foglio)?
- la forma "2h consecutivi" e' parte della regola o solo descrittiva?

Task: TASK-469 (gated alla conferma referente T4, tier MEDIUM una volta confermata). Il prototipo
flag-OFF si fa DOPO la risposta, col design chiuso, cosi' il vincolo cristallizza la pratica
(quasi-gratis su G10) invece di scommettere sullo scope.

Coerente col criterio golden-first e con la sintesi del doc stesso (nessun prototipo senza
risposta referente). Le 3 regole violate/parziali ((i),(ii),(iv)) restano righe di notifica referente
(gia' nei T3/T5/T6 del doc referente).
