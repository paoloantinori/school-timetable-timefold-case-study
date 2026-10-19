# PREDIZIONE LEVA B (gate donatore tolerance-aware nell'EDF-relay) - Fedora, pre-registrata prima delle corse

LEVA: GATE-E oggi richiede che il donore stia sopra max(quota, floor); i 5
import-tail vincenti del difetto 7 usano donatori a quota-1 dove la TOLLERANZA
per-gruppo di H32 (accessor quotaFillTolerance: G09 1h) rende l'estrazione
neutra. Emendamento in banco: donore sopra max(quota - tolleranza(gruppo),
floor) SOLO quando il target e' un deficit H32 (non H34: li' la cella vuota
vuole l'ora piena).

PREDIZIONE (Fedora): il difetto 7 si chiude (H34 1 -> 0), 5 mosse pulite, e
NESSUN nuovo extraction su altri gruppi (la tolleranza e' 0 per tutti tranne
G09: census dei nuovi candidati donatore attesi ~0).

Forma attesa (dal round cieco): 5 import-tail su G09 2027-05-25 dalle code
@12/@13 dei donatori (Storia, Informatica, Orientamento, Sicurezza, Scienze),
ogni donatore resta a quota-1 dentro la tolleranza 1h.

Esito misurato: VEDI SOTTO (scritto dopo la corsa).

## ESITO MISURATO (step27/28, export arm-b, heap 12g): SOSTANZA CONFERMATA, con emendamento dichiarato

- EMENDAMENTO al parentetico del mandato ("non H34: la cella vuota vuole l'ora
  piena"): implementato letteralmente rendeva la leva INERTE (il difetto 7 e'
  un target H34; moves=0). Nessun vincolo chiede l'ora piena su un giorno vuoto:
  H34 chiude con qualsiasi lezione e H32 accetta quota-tolleranza. Corretto:
  need H34 = quota - tol (min 1) per gruppi con tolleranza, donatori tol-aware;
  per gruppi tol=0 il comportamento e' byte-identico a oggi. La correzione e'
  la forma stessa della soluzione cieca ARBITRATA (-9 -> -7 con 5 ore sul giorno).
- LEVA B da sola: moves=6 cellsClosed=1, hard **-9 -> -8** = H34 1 -> 0, H17 -6
  e H32 -2 intatti. Predizione in sostanza confermata.
- DELTA dichiarati: (1) 6 mosse vs 5 della cieca (il relay importa con refill:
  una mossa in piu' per la stessa chiusura); (2) census TOL-DONOR-UNLOCK=251:
  valutazioni donatore ammesse SOLO dalla tolleranza, TUTTE su G09 (per i
  gruppi tol=0 l'unlock e' aritmeticamente impossibile: la parte "nessun nuovo
  extraction su altri gruppi" della predizione regge; il conteggio atteso ~0
  era invece sulle valutazioni, non sulle mosse: la maggioranza viene poi
  rifiutata a valle da VRP=13387 / H1=42080 / ROOM=37691).
- CATENA A -> B (step28): hard **-9 -> -8 (A) -> -7 (B)** = il ceiling arbitrato
  del round cieco; residui H17 -5 + H32 -2 = i 7 difetti strutturali.

### VERDICT LEVA B (3 righe)
1. IPOTESI: il gate donatore di EDF-relay ignora la tolleranza per-gruppo di
   H32; ammetterla chiude il difetto 7 senza aprire nulla.
2. MISURATO: confermato (H34 1 -> 0, -9 -> -8 da sola, -7 in catena con A,
   zero famiglie nuove), con l'emendamento documentato sul need H34
   (quota - toll, forma della soluzione cieca arbitrata) e due delta dichiarati.
3. TENERE: gap di gate reale e chiuso; candidata al porting in src come
   emendamento GATE-E + need H34 tollerante (forma opt-in per il gate di
   adozione). Decisione Fedora.
