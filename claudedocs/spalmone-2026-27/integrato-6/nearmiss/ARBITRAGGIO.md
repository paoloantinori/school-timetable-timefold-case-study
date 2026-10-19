# ARBITRAGGIO near-miss integrato-6 (21/09 notte, Fedora)

Consegna: [-1/0]hard, famiglia H34x1. Cella identificata con scope corretto
(date con timeslot, modality A/L, gruppi in-scope H32/H34): **G06 2027-06-09
(mercoledi'), quota ordinaria 4, giorno COMPLETAMENTE vuoto**. Unica cella: il
-1 e' esattamente questa.

## Cieco: soluzione in 1 mossa, legalissima a vincoli

`MOVE T23/Educazione Civica G06 2027-06-07 @12 -> G06 2027-06-09 @8`
(l'ora SURPLUS del 7/6: il giorno aveva 5 ore contro quota 4). Arbitrata sul
dump completo: T23 assente dal 9/6 su tutte le linee, cap 5->4 e 0->1,
H17/H31/H28 puliti, il 7/6 torna esattamente a quota.

## MA sotto il punteggio reale la mossa e' VELENO (e qui sta la scoperta)

Il giorno VUOTO paga solo H34 (-1): H32 non lo vede, perche' il suo groupBy
conta solo i giorni CON lezioni (e' il motivo per cui H34 esiste, TASK-557).
Appena 1 lezione atterra, il giorno ENTRA nel groupBy di H32: 1 contro quota 4
= **-3 hard**. La mossa cura H34 e partorisce H32: +2 hard netto, PEGGIO. La
mia specifica del puzzle ("basta 1 ora") era sbagliata rispetto allo scoring
reale: il cieco ha fatto esattamente cio' che gli ho chiesto; l'errore era
nella meta.

## La cura vera e' il pieno, e il pieno e' strutturalmente irraggiungibile

Riempire a 4 (come il golden: 4h Ra lab aperto 8-11) = H34 0, H32 0: l'unica
forma net-migliore. Ma il surplus di ore dell'orario e' esattamente 1 (la prova
del cieco e' corretta: tutti gli altri giorni sono a quota, un swap e'
count-neutrale): 3 ore nette per 06-09 non esistono senza portare sotto quota
altri giorni = scambiare un H34 con tre H32.

**VERDETTO: il -1 e' STRUTTURALE alla distribuzione attuale delle ore.** La
coda di G06 e' sotto-approvvigionata a monte (le ore Ra lab aperto esaurite
entro aprile, il golden invece le conserva per giugno). La cura non e' una
mossa del delivery: e' o (a) una cura input (le righe quota di giugno di G06
vs la conservazione delle ore: famiglia TASK-550, domanda per referente: le 4h di
giugno erano attese?), o (b) pressione di distribuzione in costruzione
(conservare ore per la coda d'anno invece di front-loadare).

## Regola per i prossimi near-miss (la lezione)

Il goal di un puzzle near-miss non e' "almeno un'ora sul giorno": e' "il delta
hard DELTA del punteggio reale" (post-mossa vs pre-mossa, famiglie incluse le
invisibili). Ogni puzzle near-miss va scritto col post-condizione di punteggio,
non con la post-condizione visiva.

## Aggiornamento mattutino (21/09, dopo tre rovesciamenti: la catena completa)

1. Il verdetto notturno "strutturale" era basato su dati miei sbagliati (quota 06-03
   letta 6 invece di 4 al puzzle del cieco).
2. La verifica golden richiesta da maintainer rovescia: il -1 nasce dal 6h T01 pinnato
   sul 3/6 (giorno a quota 4): sono le ore che il 9/6 attende.
3. La verifica provenienza (domanda di maintainer sui calendari-lab pre-pinnati dei docenti)
   rovescia di nuovo, nella direzione giusta: Servizio tecnico E' lab (acconciatura)
   ma quei pin NON sono input del docente (nessun calendario li dichiara; l'import
   pre-pinnato copre solo i MODA): sono PROMOZIONI del nostro template guidate dallo
   steering golden-derived (T01 THU 1.00). Conteggio esatto: 60h come incarichi
   e golden, ma il golden le concentra in 10 blocchi entro il 25/02 mentre il nostro
   ne spalma 9 pieni fino al 3/6 piu' 6 ore sparse.
4. CHIUSURA architetturale (maintainer): la forma del calendario lab e' un input in arrivo
   dal workbook/web app (l'estensione esiste; lo steering stesso dichiara la domanda
   workbook "qual'e' il tuo giorno fisso"). QUANDO l'input arriva, la distribuzione
   la esegue il solver e questa classe muore alla radice.

OPZIONI INTERIM (finche' l'input non arriva), decisione di maintainer:
(a) pressione di distribuzione in costruzione (il builder concentra le ore dei corsi
lab dei docenti "fissi" come pratica golden invece di spalmarle: la domanda e' quanto
generalizzare senza hardcodare il caso);
(b) accettare la coda -1 come costo noto dell'assenza dell'input (la serie e' comunque
0-1 con la terna che chiude l'ultimo chilometro).

## Ri-solve cieco v2 (21/09 ~12:30, su richiesta maintainer: le regole di ingaggio corrette)

Spec corretta: economia REALE nello spec (vuoto=-1, mezzo-pieno=-3) e GOAL =
minimizzo il totale, non la post-condizione visiva. Cieco zero-contesto,
zero tool. VERDETTO: **NO SOLUTION**, con la prova indipendente e le tre
famiglie chiuse da solo: (1) MOVE: l'unico surplus del dataset e' l'ora del
07-06, max 1 ora consegnabile; (2) SWAP: conteggi invarianti, D0 vuoto non
ha nulla da scambiare; (3) catene creatrici di slack: nessuna mossa crea o
distrugge ore, servono +4 nette su D0 e il pool e' 1. Ha anche corretto da
solo l'atterraggio @8 (contiguita', regola 4). La mossa di riempimento
parziale la valuta e la RIFIUTA come esito (totale 3 > baseline 1): esattamente
il comportamento che v1 non poteva avere. La strutturalita' del -1 e' ora
confermata MECCANICAMENTE e senza arbitrato manuale. Puzzle: /tmp/i6-nearmiss-puzzle-v2.txt.
