# Pacchetto referente: tre domande nuove (27/08) - MESSAGGIO PRONTO DA INCOLLARE

BOZZA NON INVIATA: maintainer rivede e spedisce. Queste tre si aggiungono alle domande
gia' in coda (Q4/Q29 preferenze-docente, Q1 T10, Q2 T12, Q20, coda-anno
G04, P3-G14, conferme cortesia: vedi domande-brevi.md rev 15-ter).

---

Ciao referente, tre domande nuove emerse dal confronto fra i due fogli che ci avete
dato (calendario giorno per giorno e monte-ore per materia). In tutti e tre i
casi abbiamo prima misurato cosa fa il vostro orario definitivo: chiediamo solo
le cose che i fogli da soli non ci possono dire.

**1. G09, 4 giugno: abbiamo corretto il calendario, confermate.**
Nel calendario di G09 le dichiarazioni superavano di 4 ore cio' che il
vostro orario definitivo pratica: 4 e 7 giugno erano dichiarati, ma nel
orario definitivo entrambe le giornate restano vuote. Abbiamo quindi assunto
l'orario definitivo come riferimento (la regola che usiamo per tutti i dati:
pratica conta piu' del foglio) e tolto dal calendario dei vincoli le 4 ore
del 4 giugno (il 7 giugno era gia' stato tolto). Nota: il monte-ore dei corsi
(666 ore) resta invariato e l'orario definitivo lo colloca per intero, quindi
nessuna ora va persa. Domanda: confermate che il 4 giugno la classe G09
non c'e', oppure quelle 4 ore erano reali? Nel secondo caso le rimettiamo con
una riga e la giornata torna vincolante.

**2. I giorni corti delle classi FC: voluti o subiti?**
Nel vostro orario definitivo le classi G04 e G08 hanno giorni da 2-3 ore
(vi sono classi con decine di giorni sotto le 4 ore), mentre il calendario per
quelle classi dichiara quasi sempre giornate piene. Non e' un caso isolato ma
uno schema ricorrente. Domanda: quei giorni corti sono una scelta della scuola
(compatibilita' stage, trasporti, altre ragioni) oppure un limite dell'orario
che avete dovuto accettare? Nel primo caso ce li annotiamo come pratica da
rispettare e smettiamo di forzare il riempimento; nel secondo sappiamo che il
riempimento pieno e' il bersaglio.

**3. Le ore di recupero: vengono fatte dopo le regolari?**
Le ore di Recuperi ed Approfondimenti nel vostro orario definitivo sono
concentrate nella seconda parte dell'anno (gennaio-giugno circa 156 ore su 189;
settembre-dicembre solo 33), mentre il nostro software le distribuisce in
modo uniforme (1-2 ore a settimana tutto l'anno). Domanda: il concentrarle a
fine anno e' una regola (prima si chiudono le materie regolari, poi si
recupera) o e' semplicemente come e' venuto l'orario? Se e' una regola, la
mettiamo nei vincoli e il nostro orario vi si conformera'.

---

Fine del messaggio. Note per maintainer (NON per il messaggio): la domanda 1 e'
diventata una NOTIFICA-CON-DOMANDA con la cura golden-first F1 del 28/08
(la cella G09 04/06 non e' piu' dichiarata: pin golden H34 0; revert a una
riga per copia CSV se referente conferma le 4h). Le altre due coprono la famiglia
giorni-corti del sanity gate (FC) e il filone RA (TASK-552 gia' in backlog
come soft candidato). Evidenza completa nel registro 25-28/08 e in
referente-domanda-g09-2026-08-27.md.
