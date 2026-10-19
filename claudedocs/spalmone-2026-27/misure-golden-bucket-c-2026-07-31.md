# Misure golden bucket C (read-only) - 5 regole non modellate (zai, 2026-07-31 notte4-seguito)

Criterio golden-first (direttiva maintainer): una regola scritta non modellata si misura sul golden.
**Rispettata dal golden = candidata quasi-gratis** (un vincolo che cristallizza la pratica a
costo ~0). **Violata = riga di notifica referente** (non si modella una regola che la scuola stessa
non segue; si chiede se e' aspirazionale o reale). Qui sotto 5 regole del bucket C, 3 righe
ciascuna, verdetto + destinazione. Script riproducibile: `/tmp/golden_bucket_c.py` (re-spiegato
in fondo per riproducibilita'); raw ri-computati indipendentemente (cross-script + grep) e
sottoposti a verifica avversaria (alternative confutate una per una).

Golden: `src/main/resources/seed/lessons.csv` (12.979 lezioni). Trappole filtrate per CLAUDE.md
(sabati off-grid, docente Fasullo/Fasutto segnaposto 19/09), tranne dove la regola riguarda
Stage (off-grid per natura). Slot: 08-09=0 (prime ora) ... 14:30-15:30=6, 15:30-16:30=7.

## (i) Sicurezza delle 1e in 2 giorni cumulativi [bucket C1 / T5] -> VIOLATA -> notifica referente
Misura: per i 4 gruppi 1e (G03/G02/G01/G04) la materia `Sicurezza` e' **8 ore
spalmate su 8 giorni distinti** (cadenza 1h/settimana: G03/G01/G04 tutti i lunedi',
G02 mar/ven), NON concentrata in 2 giorni. (Verificato anche che non e' `Igiene e sicurezza`
il soggetto cumulativo: quello esiste solo per G02, 30h spalmate.)
Verdetto: il golden NON segue la regola scritta. **Notifica referente (T5)**: la regola e'
aspirazionale/non applicata quest'anno, oppure va ristretta a un sottoinsieme delle 8h?

## (ii) T21 "T30": non prime ore in G08/4 FA, ore di mezzo [bucket C2 / T3] -> VIOLATA -> notifica referente
Misura: T21 (Lingua Inglese) ha **26 ore su 42 a slot0** per G08 (oltre meta') e **5
su 40 a slot0** per G12; distribuzione lontana dalle "ore di mezzo" (G08: slot0=26,
slot1=9, slot2=5, slot3=2).
Verdetto: il golden mette T21 pesantemente a prime ore. **Notifica referente (T3)**:
T21 e' davvero "T30"? La regola e' reale (e quest'anno non rispettata) o e' un appunto
non applicato? (Non si modella finche' non e' confermata: today metterebbe una regola che
contraddice 26 ore di pratica golden.)

## (iii) T11 "6h post-stage per curriculum, anche 2 consecutive" con G08/3 OIT/G10 [bucket C3 / T4] -> RISPETTATA su G10 -> mini-domanda referente
Misura: **G10 = esattamente 6h post-stage** di Comportamento sociale (stage finisce
2027-04-23), in **3 blocchi di 2h consecutive** (giovedi' 29/04, 06/05, 13/05), combaciando
alla lettera con la nota "anche 2 consecutive". G08 = 5h post-stage (Competenze di
cittadinanza, off-by-one). G11 = 29h ma di Personalizzazione (materia pratica, non
curriculum: scope diverso).
Verdetto: la regola e' REALE e il golden la rispetta in pulito su G10. **Mini-domanda referente
(T4)**: confermi che vale per G08/G11/G10 e che il curriculum e' Comportamento
sociale/Educazione civica/Competenze di cittadinanza? (G10 e' il modello; l'off-by-one di 2
FC e lo scope G11 da chiarire.) Candidata a modellazione MEDIUM una volta confermata.

## (iv) Laboratorio Termoidraulico "prime 4 ore" [bucket B, tracciato] -> PARZIALE -> chiarire referente
Misura: **235h su 313 (75%) a slot 0-3 (8-12)**, 78h (25%) a slot 4-5 (12-14). Nessuna al
pomeriggio (slot 6-7). Distribuzione: slot0=43, slot1=63, slot2=65, slot3=64, slot4=58, slot5=20.
Verdetto: il lab e' concentrato al mattino ma **non rigorosamente nelle prime 4 ore** (un
quarto scola in 12-14, coerente con blocchi lab di 5h 8-13). **Chiarire referente**: "prime 4 ore"
= esattamente slot 0-3, o blocco mattutino 8-13? Se e' "8-13 mattino" e' rispettato; se e'
"8-12 tassativo" e' parziale. Non bloccante (la forma emerge dai pin/quote).

## (v) G10 "stage NO LUNEDI, SI SABATO" [bucket C7] -> RISPETTATA -> candidata quasi-gratis (basso impatto)
Misura: **0 lezioni Stage di G10 di lunedi'** (su 300h: Mar=56, Mer=64, Gio=60, Ven=64,
Sab=56). Verificato che NON e' artefatto di calendario: fuori dal periodo stage il lunedi' e'
giorno scolastico normale per G10 (24 lunedi', 117h di teoria), quindi durante lo stage il
no-lunedi' e' **deliberato**. Sabato confermato presente (56h, off-grid nostra).
Verdetto: il golden rispetta la regola in modo pulito e deliberato. **Candidata quasi-gratis**,
MA basso impatto: Stage e' gia' confinato alle date con stageHours>0 (H25 + filtro VRP), quindi
una regola "no-lunedi" morderebbe solo se il solver spostasse stage su un lunedi' non-stage,
che il date-filter gia' vieta. Il sabato e' off-grid e gia' contabilizzato (`expectedUnscheduledHours`).
Da confermare con referente se e' policy (modellabile) o conseguenza del calendario stage.

## Sintesi (destinazione)
| regola | bucket | verdetto golden | destino |
|---|---|---|---|
| (i) Sicurezza 1e 2gg | C1 | VIOLATA (8h/8sett) | T5 referente (aspirazionale?) |
| (ii) T21 prime ore | C2 | VIOLATA (26/42 slot0) | T3 referente (T21=T30? reale?) |
| (iii) T11 6h post-stage | C3 | RISPETTATA su G10 | T4 referente (conferma scope) -> poi MEDIUM |
| (iv) Termo lab prime 4h | B | PARZIALE (75% slot0-3) | chiarire referente (8-12 vs 8-13) |
| (v) Stage G10 no-lun | C7 | RISPETTATA (0 lun, deliberato) | quasi-gratis ma basso impatto (H25 gia' confina) |

Nessuna nuova prototipo di vincolo parte da qui senza risposta referente: le 2 rispettate ((iii)
parziale, (v)) sono candidate a basso impatto o gated a conferma, le 3 violate/parziali ((i),
(ii), (iv)) diventano righe di notifica. Coerente col criterio golden-first: non si modella
cio' che la scuola non pratica.

## Riproducibilita' (script)
Logica in `scripts/analyze/golden_bucket_c_measures.py` (legge il golden, filtra trappole,
calcola le 5 misure). Misure ri-computate indipendentemente via `csv.DictReader` diretto + grep
per i segnali piu' portanti ((v) lunedi'=0, (iii) G10 = 6h/2-consec, mappatura slot); match
esatto, e lo script repo-tracked riproduce gli stessi numeri.
