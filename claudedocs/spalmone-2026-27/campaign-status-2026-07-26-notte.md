# Verifica Fedora campagna notturna 2026-07-25/26 (dose-response ph2 + sonda M32) ~03:45

7 run laptop (b600 x3, b1800 x2, W-m32x3 x2), zero crash, zero 429, driver chiuso 02:48:34.
Verifica critica SUI RAW fatta qui (KPI rilanciati su tutti i lessons.txt); il VERDICT di
zai coincide. Baseline 3.600s = val408-run1/2. Un run extra (b600 delle 18:44) scartato da
zai per una scoperta vera: MAVEN_OPTS -D non raggiunge la JVM dell'app (mvn quarkus:run
forka java -jar); fix meccanicamente verificato, nota aggiunta a CLAUDE.md, tutti i 7 run
validi sono post-fix (nel braccio W knob line verificata: numerator=300000 min=60
max=1500, e M32/match x3,0 esatto vs baseline).

## Tabella dose-response (verificata sui raw)

| Metrica | B600 r1/r2/r3 | B1800 r1/r2 | 3600 (val408) |
|---|---|---|---|
| hard | -10 / -8 / -6 | -4 / 0 | -1 / -1 |
| medium (score, con M32) | -2,03M / -2,12M / -2,09M | -1,98M / -1,98M | -1,90M / -1,91M |
| ricorrenza unpinned | 51,5 / 51,4 / 51,9% | 52,2 / 51,3% | 52,8 / 53,6% |
| full-day | 77,9 / 76,7 / 77,2% | 79,0 / 78,8% | 79,9 / 80,0% |
| day-start | 92,6 / 91,8 / 91,5% | 92,9 / 93,2% | 93,7 / 93,7% |
| sharpness pooled | 75,9 / 75,1 / 73,4% | 78,3 / 76,8% | 79,7 / 77,5% |
| wall solve | 4.511 / 1.187 / 4.510s | 2.461 / 5.195s | ~4.585 / ~4.069s |

**Lettura (TASK-326, chiusa Done):** tutto scala monotonamente col budget; la ricorrenza
e' PIATTA (51-54%) su tutti i budget: l'ipotesi "fase 2 corta preserva il template" e'
falsa sotto randomicita' vera (il segnale locale era un artefatto del seed fisso, gia'
refutato ieri sera e ora confermato a scala). Nemmeno il wall si accorcia in modo
affidabile. **Raccomandazione: fase 2 resta 3.600s.** Domanda chiusa con dati, spesa 5 run.

## Braccio W (sonda per TASK-419): il tetto della penalita' e' reale

M32 TRIPLICATO (300000/60/1500, attivo e verificato): hard -1/-2 ok, ma ricorrenza
53,5/53,6% contro 52,8/53,6% della baseline: ZERO guadagno. La penalita', a qualunque
peso ragionevole, non si avvicina al 73,8% del pinning (spike E1). **La 419 serve
intera** (solve a stadi con promozione pinned); prerequisito invariato: semantica
M20-family per i pin promossi.

## TASK-403: evidenza DECISIVA, ottenuta in locale alle 03:31

I dump per fase della notte portano il box (estensione di ieri sera): a fine fase 2 il
box reale e' 26-46 lezioni (34 in b1800-r1). L'harness 403 (ora parametrizzabile) sullo
stato REALE di fine-ph2 di b1800-r1: box 34 -> 0 (23 eviction, 0 conflitti aggiunti),
gap 5 -> 4, hard -39 -> -4, in <1s deterministico. Il repair anticipato fa al confine
ph2/ph3 esattamente cio' che la fase 4 fa 20+ minuti dopo. Resta da misurare su laptop solo
l'effetto DOWNSTREAM (una fase 3 che parte hard-pulita smette di essere inerte?): arm
economico per il prossimo ciclo (marker + CompactionPhase tra ph2 e ph3, ripple
solver-config-sync).

## Ratifiche e decisioni per maintainer (mattina)

1. **326**: ratificare "nessun cambio di budget" (gia' Done col dato; nessuna modifica a
   solverConfig, che infatti NON e' stato toccato).
2. **419**: e' ufficialmente il prossimo grosso pezzo (il W-arm ha eliminato la
   scorciatoia); il primo sotto-lavoro e' la decisione di design M20-family per i pin
   promossi.
3. **403**: promuovere l'arm "repair tra ph2 e ph3" nel prossimo ciclo laptop (costo ~zero,
   possibile passeggero del primo A/B della 419).
4. Nota operativa permanente (gia' in CLAUDE.md via zai): i -D per la JVM dell'app NON
   passano da MAVEN_OPTS con mvn quarkus:run.
