# TASK-472 AC#3/AC#4 - Report standing delle occorrenze golden (2026-08-14)

Chiude AC#3 (spiegazione per-occorrenza o 'inspiegata' esplicita) e alimenta
AC#4 (doc referente: le tensioni T1-T10 del 31/07 hanno qui i numeri di contesto).
Script ripetibile: `scripts/analyze/golden-violation-occurrences.py [--full]
[golden.csv]`. Output completo: `occorrenze-golden-full-2026-08-14.txt`.

## M-TR (no-2h teoria): 440 giorni-2+ nel golden, scomposte

| categoria | n | % | lettura |
|-----------|---|---|---------|
| mista (pratica ambivalente) | 268 | 61% | coppie che il golden fa a meta' 1h/meta' 2h: la domanda referente del foglio 22 (borderline Figurino/Personalizzazione/Tecnologia e creativita') copre la maggioranza del residuo |
| inspiegata (policy 1h, nessuna forzante) | 76 | 17% | 2h contro la pratica dominante della coppia stessa, senza carico che forzi: lista completa nel txt, per la scuola come domanda di preferenza residua |
| deliberata (policy 2h, foglio 22) | 60 | 14% | pratica dichiarata: NON e' una violazione della scuola, e' il vincolo M-TR che misura contro una pratica che ammette il blocco |
| compattazione settimanale (economia docente probabile) | 36 | 8% | tutte le ore della settimana della coppia nello stesso giorno: pattern di economia del viaggio docente |

Lettura per il tier (conferma VERDICT-472): il 61%+14% del residuo e' pratica
dichiarata o ambivalente -> MEDIUM resta corretto, nessuna promozione H. La
cura del residuo resta INPUT (foglio 22 confermato dalla scuola), non vincolo.

## M24 (T11 early-start max 1/sett): 15 settimane violate, tutte inspiegate

Il carico settimanale di T11 non supera mai le 25h nelle settimane violate:
nessuna forzante meccanica, la scuola apre semplicemente 2 giorni in anticipo.
Tier MEDIUM a residuo calibrato confermato (preferenza, non capacita').

## M25 (T05 free days min 2/sett): 27 settimane violate, tutte inspiegate

T05 insegna 4+ giorni/sett senza che il carico settimanale (mai >= 24h) lo
forzi: anche qui preferenza pratica, non capacita'. Tier MEDIUM confermato.

## Per il pacchetto referente (AC#4)

Le tensioni del doc `domande-rotazione-e-tensioni-referente-2026-07-31.md` possono
citare questi numeri: la domanda-teoria (foglio 22) coprirebbe il 75% del
residuo M-TR (268 mista + 60 deliberata); le 76 inspiegate sono il_candidate
domanda di preferenza residua (lista per-coppia nel txt). M24/M25 restano
preferenze non-forzate: nessuna azione richiesta alla scuola oltre la conferma
dei due parametri (maxEarlyStartDaysPerWeek=1, requiredFreeDaysPerWeek=2).

## Limiti dichiarati (proxy onesti)

- La policy per-coppia e la classificazione derivano dallo STESSO golden
  (auto-consistenti, non prova indipendente): dichiarare sempre la fonte.
- Il test "forzata dal carico" usa la soglia 5 giorni Mon-Fri lordi (senza
  festivita'/stage per gruppo): proxy dichiarato, alcune "forzate" potrebbero
  essere piu' larghe su settimane accorciate.
- M24/M25: i conteggi sono settimane-violate (15/27), il runtime M24 conta
  unita' (16): metriche diverse, entrambe dichiarate.
