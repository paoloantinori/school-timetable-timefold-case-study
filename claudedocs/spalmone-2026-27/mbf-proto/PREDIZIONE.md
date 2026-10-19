# PREDIZIONE M-BF prototipo (registrata PRIMA delle misure, 17/09)

Flag: planner.constraints.mbf.enabled opt-in default OFF (M-TR pattern), peso
default 20/min gap (M15 parity: 1200/ora di buco), solo INTERLEAVED
multi-docente (MONO-FRAG escluso per spec: pratica della scuola).

(a) GOLDEN arm (mbfGoldenCheck): il golden importato paga esattamente 1 match
    (G05 2027-03-17, l'unico giorno interleaved del census) = -1200 medium.
    Tolleranza dichiarata: se isLab() diverge dalle famiglie del census
    (es. materie taggate che il census non conta), i match possono salire:
    attesi 1-6, ogni match sopra 1 va nominato nel VERDICT.
(b) REPLAY STATIC (integrato-1 [-4]): hard IDENTICO off/on (medium lever),
    delta medium atteso -(54 ± 10) giorni * gap: atteso tra -10.000 e -60.000
    medium (54 giorni, gap medi 1-2 ore). Match attesi ~54 ± 10 (isLab vs
    famiglie).
(c) REPLAY LS (mini-LS 600s armata): interleaved multi-docente 54 -> SOTTO 54
    (atteso forte calo, 20-40 chiusi: la frammentazione ora costa e la LS ha
    mosse change/swap con cui scambiarla); hard MAI peggiore (assert);
    KPI di guardia letti DOPO sui dump (full-day, ricorrenza) dichiarati nel
    VERDICT, attesi dentro la variabilita' con possibile piccolo costo
    (il prezzo esplicito della forma).
