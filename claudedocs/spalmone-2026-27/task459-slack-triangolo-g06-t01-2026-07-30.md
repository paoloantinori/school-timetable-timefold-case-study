# Slack del triangolo G06 / Servizio tecnico e stilistico / T01 (2026-07-30)

Domanda: le 35 ore boxed di Servizio tecnico (G06 / T01) nel run 2026-07-29b
sono varianza fra run, oppure saturazione strutturale, e di quanto slack ci passa?

Numero misurato sull'export (29b vs 447, celle legali = min(T01 libero,
G06 libera nella finestra Thu) sui giovedi in-grid in sessione):

| run | collocate / boxed | celle legali | SLACK | pin G06 |
|---|---|---|---|---|
| 29b (TASK-456, no constrained-teacher) | 25 / **35** | 30h su 11 giovedi | **-30h** | **0** |
| 447 (riferimento, constrained-teacher ON) | 60 / 0 | 65h su 13 giovedi | **+5h** | **60** |

Conclusione operativa: le 35 boxed sono STRUTTURALI, non varianza. Senza
pre-assegnazione il triangolo e' in overdemand di 30h (capacita 30h < domanda
60h): la costruzione assegna greedy i giovedi di T01 agli altri 3 corsi
(G02 / G10 / G12) e G06 resta corta. Il 447 lo risolveva PINNANDO le
60h di G06 via constrained-teacher (60 lezioni pinnate -> riservava le celle
prima del solve -> tutte collocate, slack +5h). La leva e' l'ALLOCAZIONE
ANTICIPATA alla 435 su quel corso (constrained-teacher / pin anticipato), NON
il tuning. Il secondo run (29b senza constrained-teacher) misurera' la stessa
saturazione (35 boxed +/-), non una regressione.

Ipotesti alternativa (#4, pin autoritativi occupano le celle): CONFUTATA nel
verso, CONFERMATA nell'antitesi. I pin sono la SOLUZIONE, non il problema: nel
447 i 60 pin di G06 SONO le celle riservate per Servizio tecnico; nel 29b
l'assenza di pin lascia il greedy libero di saturare T01 sugli altri corsi.
Cambia la leva: non si tolgono pin, se ne AGGIUNGONO (anticipati) su quel corso.

Misura read-only sull'export (scripts/analyze + export.json.gz). Nessun codice,
nessun prototipo. Vedi anche VERDICT certification-2026-07-29b (catena boxed).
