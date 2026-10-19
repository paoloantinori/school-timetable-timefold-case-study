# VERDICT final experiments - 449 input + 470 theory-spread (zai, 2026-08-11)

Ultimi 2 A/B della catena Fedora-offline (recovery batch dopo il reboot 17:07). Chiudono il
ciclo: 449 testato su entrambi i regimi (golden + input), 470 testato live (mai fatto prima).
Contrasto pulito: 449 degrada, 470 neutro.

## 449 input A/B (source=input, regime target room-tier)

| arm | hard | medium | ricorrenza | tentativi |
|-----|------|--------|------------|-----------|
| OFF (control) | **0hard** | -1.308k | 74,5% | (0hard) |
| ON (449 pre-booking) | **-5hard** | -1.379k | 71.7% | 3, nessuno a 0hard |

**449 ON su source=input degrada come sul golden**: -5hard (NON 0hard in 3 tentativi),
ricorrenza -2,8pp, medium -71k peggiore. Lo stesso -5hard del golden (coerenza strutturale:
il pre-booking mono-room-tag introduce un residuo hard fisso che il multi-start non cura).
**449 RULED OUT su entrambi i regimi** (golden -5hard, input -5hard). Non adottare. La
soluzione strutturale resta capacita' (piu' stanze/tag) o de-tag, non l'allocatore.

## 470 theory-spread A/B (source=golden, construction dedup live)

| arm | hard | medium | ricorrenza | tentativi |
|-----|------|--------|------------|-----------|
| OFF (control) | **0hard** | -1.284k | 75.8% | (0hard) |
| ON (theory-spread) | **0hard** | -1.230k | 75.6% | 1 (0hard, no retry) |

**470 ON NON degrada** (0hard come OFF, 1 tentativo senza retry): la construction dedup
theory-spread e' INNOQUA, al contrario del pre-booking 449. Medium -54k MIGLIORE (-1230k vs
-1284k), ricorrenza flat (75.8/75.6, -0.2pp = entro la spread 0.2pp della stability). Lettura:
470 NEUTRO-to-leggermente-positivo. Il medium -54k e' ~2x la stability spread (28k), quindi
suggestivo ma non conclusivo (single A/B; servirebbe re-run per confermare il guadagno).

## Sintesi per Fedora (adozione)

- **449 = RULED OUT** (degrada hard -5hard su golden + input; non chiude capacita'). Prototipo
  resta default-OFF. Soluzione = capacita'/de-tag.
- **470 = NEUTRO** (0hard, non degrada; medium -54k suggestivo ma entro ~2x varianza, non
  confermato). Il verdetto 08-01 (construction lever parziale) era misurato a construction-end
  (flat M-TR); il live A/B conferma neutro: non aiuta in modo conclusivo. Resta default-OFF
  (nessun benefit chiaro; la cura reale della pratica teoria e' il workbook TASK-471, fatto).
  NON ruled out (non danneggia, a differenza di 449): se un futuro re-run confermasse il -54k
  medium, si potrebbe riaprire. Per ora: committed default-OFF come artifact, non adottato.

## Nota metodologica
Il contrasto 449-vs-470 e' istruttivo: due prototipi flag-gated default-OFF, entrambi prototipi
"non adottati", ma con esiti LIVE opposti (449 degrada, 470 neutro). Conferma che il verdetto
"prototipo non adottato" dal solo construction-end (470 flat) non predice l'impatto live: il
live A/B e' necessario (come il campaign-status-08-05 imponerebbe per qualunque adozione). 449
sembrava innocuo dal pin-log (pinnava 69% con stanza) ma degradava l'hard live; 470 sembrava
flat a construction-end ma e' neutro/slightly-positive live.
