# NOTA riproduzione firme + separazione RELEASE (laptop, 2026-08-15)

> Filone B del mandato notturno 15/08. Re-run di 6 seed noti col driver aggiornato
> (a1f8728b: preserva score-stream.txt per run, il gitignore *.log li mangiava).
> Protocollo IDENTICO al census: MULTI_START_OFF=1 MT_LS_OFF=1, profilo bsinput14
> --no-setup, banner grasp seed=N verificato per run.

## 1. Riproduzione firme: 6/6 concordi, le firme n=1 diventano n=2

| seed | census 14/08 | re-run 15/08 | firma re-run | esito riproduzione |
|------|--------------|--------------|--------------|--------------------|
| 17 (bad) | -7 H17 | **-6 H17** | 6x G13 mercoledi' slot 12, occupazione [8,9,10,11,13] IDENTICA al 14/08 | **RIPRODOTTA n=2** |
| 18 (bad) | -32 H28 | **-34 H28** | 15 giorni A-B-A su G13/Lavorazioni meccaniche (T10..T15..T10) | **RIPRODOTTA n=2** |
| 1 (bad) | -10 H17+H29 | **-12 H17+H29** | H17 G11 (buco slot 11) + H29 -1 | RIPRODOTTA (3a osservazione con seed-9) |
| 9 (bad) | -9 H17 | **-7 H17** | H17 G11 (buco slot 10) | RIPRODOTTA |
| 5 (good) | 0 | **0** | - | confermato good |
| 19 (good) | 0 | **0** | - | confermato good |

Nessuna firma e' un singleton: tutte le 5 firme censite hanno n>=2 osservazioni
ripetute con stesso gruppo/stesso meccanismo. La gravita' e' stabile entro pochi hard
(più o meno 1-2 per firma). Le firme sono PROPRIETA' DEL SEED (deterministicita' del
path GRASP), non eventi casuali della LS.

## 2. Separazione al confine RELEASE (nuova misura laptop, stream preservati)

Vista per-run delle boundary (da score-stream.txt): CH-end, B-TIMESLOT(-1/-2/-3),
B-ROOM. Il confine RELEASE = ultima B-TIMESLOT prima di B-ROOM.

| run | classe | hard CH-end | hard RELEASE | hard finale |
|-----|--------|-------------|--------------|-------------|
| seed-5 | good | -9804 | **-1809** | 0 |
| seed-19 | good | -9681 | **-1833** | 0 |
| seed-1 | bad | -9803 | **-1847** | -12 |
| seed-17 | bad | -9789 | **-1851** | -6 |
| seed-18 | bad | -9769 | **-1848** | -34 |
| seed-9 | bad | -9794 | **-1985** | -7 |

Risultati:
1. **Separazione RELEASE confermata 6/6** (good -1809/-1833 TUTTI meno negativi di bad
   -1847/-1848/-1851/-1985) ma con **margine SOTTILE: 14 unita'** (worst good -1833 vs
   best bad -1847). Una soglia fissa tipo -1840 funzionerebbe qui, ma e' fragile.
2. **CH-end NON separa** (buono a sapersi: era gia' noto dalla confutazione early-abort):
   good e bad mescolati -9769..-9804, e il good seed-19 ha MENO boxed dei bad. Il
   numero di boxed a CH-end non predice nulla: conferma meccanica del filone 2 del 14/08.
3. **La baseline good al RELEASE NON e' -9404** in questo protocollo: e' la banda
   [-1833, -1809]. Il -9404 della premessa Fedora (TASK-498) non appare: probabilmente
   letto a un confine diverso o su stream con contabilita' H29 diversa. Registrato
   come commento su TASK-498: il gate skip-B-ROOM deve usare la BANDA good come
   riferimento, non una costante.
4. B-ROOM e' sempre 604s in tutti i run: il risparmio skip-B-ROOM e' ~10 min/tentativo
   condannato, coerente con la stima TASK-498.

## 3. Script

`scripts/analyze/score-stream-separation.py <run-dirs...>`: tabella divergenza
posizionale per boundary + vista RELEASE-boundary con range good/bad e tight margin.
Smoke-tested sui 6 stream di questa directory e sugli A/B TASK-497.

## Artefatti

- 6 run dir complete (score, analysis, export, KPI battery, score-stream.txt,
  score-stream-boot.txt, boot.log per-run).
- Driver: multistart-ab.sh (a1f8728b), wrapper /tmp/filoneB-repro.sh (catena 6 seed).
