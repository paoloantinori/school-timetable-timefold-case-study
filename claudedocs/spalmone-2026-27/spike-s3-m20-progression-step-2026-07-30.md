# SPIKE S3 - M20_PROGRESSION_STEP a 1500: forma del deficit (2026-07-30 notte)

**Domanda (mandato):** il calcolo M20_PROGRESSION_STEP era gia' fatto (500 batte una
M15 singola ma non i bundle; 1500 batte M15 singola). Verificare al checkpoint cosa fa
1500 alla FORMA del deficit senza solve intero.

## Misurazione (A/B checkpoint di costruzione, ~1.5min/run)

| metrica (construction-end) | step 500 (baseline) | step 1500 (spike) |
|---|---|---|
| ricorrenza TASK-381 L2 | 76.1% (7905/10382) | **76.1% (7905/10382)** |
| full-day RAW (14 gruppi) | 74.7% (1683/2254) | **74.7% (1683/2254)** |
| full-day scoped (excl G14+stage) | 85.5% (1670/1954) | **85.5% (1670/1954)** |
| boxing "could not place" (linee) | 70 | **70** |

## Verdetto: NO-OP al checkpoint (come S1). Conferma la lezione di metodo.

Cambiare M20_PROGRESSION_STEP e' un **no-op completo al checkpoint di costruzione**.
Questo conferma quanto trovato in S1 (isG14, che governa la quota M20, era no-op qui):
l'M20 e' un vincolo di **ricerca locale**, il builder e' pre-score, quindi la forma del
deficit a fine costruzione e' decisa dal builder (dal template stampato), NON dal costo
progressivo M20. Il parametro agisce solo quando la LS muove le lezioni per riempire.

**Tenere o buttare (per il QUADRO):** il calcolo teorico (1500 batte M15 singola ma non i
bundle) resta valido come analisi LS, ma **non e' misurabile al checkpoint**: la forma del
deficit che 1500 produrrebbe richiede un **A/B full-solve** (gate del mattino). Niente da
adottare stanotte; il step resta 500. La lezione generale (con S1): un parametro che agisce
solo in LS non si vede al checkpoint di costruzione, misurarlo li produce un no-op silenzioso.
