# I due run NON sono confrontabili sul fronte orari: il riferimento usava un flag default OFF (2026-07-30)

Verifica mia (Fedora) sui dump `lessons.txt`, colonna pinned, non sui VERDICT.

## Il fatto
Ore di T01 (Servizio tecnico e stilistico) collocate e pinnate nei due run:

| gruppo | 447 collocate / pinnate | 29b collocate / pinnate |
|---|---|---|
| G02 | 60 / **60** | 60 / 0 |
| G06 | 60 / **60** | **25** / 0 |
| G10 | 65 / **65** | 65 / 0 |
| G12 | 46 / **46** | 46 / 0 |
| totale | 231 / **231** | 196 / 0 |

Nel run certificato di riferimento **tutte e 231 le ore di T01 erano PINNATE prima del
solve**; nel run di stanotte nessuna. Le 35 ore mancanti di G06 sono esattamente il buco.

## La causa
E' la pre-assegnazione constrained-teacher del TASK-435
(`planner.pre-assign.constrained-teacher.enabled` in `HybridPhase1aService`), che riserva le
celle del docente piu' vincolato prima del solve. Il flag e' **default OFF** e TASK-435 non e'
mai stato ratificato: il run 447 lo aveva acceso, il run di stanotte gira coi default piu' il
solo pacchetto aule appena ratificato.

## Le due conseguenze, entrambe importanti
1. **Il confronto 29b contro 447 sul fronte orari NON e' apples-to-apples.** I 35 boxed e
   almeno una parte del calo di full-day (84,3% contro 87,0% ex-G14) vengono dalla
   pre-assegnazione assente, non dal pacchetto aule. Il verdetto sulle AULE regge comunque,
   perche' e' strutturale e si legge su una popolazione sua: room-null non-lab = 0, verificato
   sull'export filtrando pinnate e pseudo-docente (167 room-null, tutte lab).
2. **Lo stato "certificato" della campagna non e' riproducibile coi default.** I numeri di
   riferimento (-195 hard, full-day 87,0%, ricorrenza 74,4%) dipendono da un flag spento per
   default e non ratificato. Chiunque rilanci col protocollo standard NON li ritrova, ed e'
   esattamente quello che e' successo stanotte.

## Cosa serve
La misura confrontabile e' un run col flag esplicitamente acceso sulla riga `java -jar`
(parametro di run, non cambio di default). La DECISIONE vera resta di maintainer: se lo stato
certificato dipende dalla pre-assegnazione constrained-teacher, quel flag va ratificato e
acceso per default, altrimenti la campagna inseguira' numeri che il suo stesso protocollo non
riproduce. Checklist storica: `ratifica-pacchetto-435-446-447-448.md`.

Nota: lo slack misurato da zai (`task459-slack-triangolo-g06-t01-2026-07-30.md`,
overdemand -30h senza pre-assegnazione, +5h con) e' coerente con questo e ne e' il meccanismo:
senza pin la costruzione greedy assegna i giovedi di T01 agli altri tre corsi e G06
resta corta.
