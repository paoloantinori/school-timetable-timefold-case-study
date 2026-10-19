# Domande per la scuola, ordinate per fatica di risposta

Ciao referente. Abbiamo ricavato da soli molte risposte misurandole sul vostro orario definitivo
2026/27, che trattiamo come valido per definizione. Quindi la maggior parte di quello che segue
non e' una domanda aperta: e' una **nostra conclusione da confermare o smentire**.

Come rispondere, in ordine di fatica crescente:
- **Sezione A**: metti solo SI o NO accanto al numero. Una riga di spiegazione solo dove metti NO.
- **Sezione B**: cerchia una delle due opzioni.
- **Sezione C**: qui serve davvero la tua conoscenza, sono quattro.

Il dettaglio di ogni punto, con le misure che ci hanno portato alla conclusione, sta in
`domande-per-la-scuola.md`.

---

## REVISIONE 30/07 (dopo le misure di oggi) - stato aggiornato

Abbiamo rimesurato le aule sui raw e alcune domande di questa lista sono **morte**: la risposta
ce l'eravamo gia' data da soli, oppure la domanda nasceva da un difetto del nostro motore, non
della scuola. Qui lo stato aggiornato. Il dettaglio delle domande originali resta nelle sezioni
A-D piu' sotto, con le morte marcate **[CHIUSA]**.

### Domande MORTE (non si chiedono piu')

- **"Vi serve un'altra aula di acconciatura?" e tutto il quadro delle ~189 ore senza aula.**
  [CHIUSA] Era la domanda strutturale che apriva la sezione B. Retratta: il laboratorio di
  acconciatura e' usato a circa meta' della capacita' annua (716 ore su 1.368 slot di un'unica
  stanza), e le 142 ore restate senza stanza nascono dal fatto che il NOSTRO motore concentra
  le lezioni (fino a 3 nello stesso ora; picco di 65 sulla stessa cella del giovedi) mentre il
  vostro orario non supera mai 2. Non manca l'aula: manca un vincolo nostro, che stiamo mettendo.
  Nessuna domanda per la scuola su questo.
- **14-bis - dove si fa il Lavaggio oltre al laboratorio.** [CHIUSA] Nel vostro orario
  definitivo il Lavaggio non si sovrappa MAI a nient'altro. La compressione che lo lascia senza
  stanza la fa il nostro motore, non e' un problema di spazi della scuola. Non si chiede.

### Domande VIVE, per urgenza

**(1) Urgenti - determinano chi trattiamo come docente vincolato (pre-assegnazione).** Sono le
piu' importanti: da queste dipende chi collochiamo a priori prima del solve.
- **(Q4)** I 6 docenti con giorno fisso dedotto dal vostro orario (T07 solo mercoledi',
  T19 lun+mar, T24 lun+mar, T22 lun+gio, T12 lun+mar, T17 mai lun): per
  ciascuno e' un **vincolo di disponibilita' reale** o una **scelta organizzativa di
  quest'anno** che possiamo cambiare? (T01 solo-giovedi' e' gia' confermato da maintainer, non
  serve.) Se qualcuno e' solo una scelta di comodo, dicci quale: lo liberiamo e guadagniamo
  capienza.
- **(Q15)** T16, seconda mattina libera dalle 11:00 - quale giorno? (Il martedi' e'
  confermato dall'orario; la seconda no.)
- **(Q29) T05, venerdi'.** Nei vostri dati T05 (Matematica, 70h su G04) risulta **libero
  ogni venerdi** (tutto il giorno, 34 venerdi su 34). E' un **vincolo reale** (T05 non puo'
  proprio il venerdi') o una **preferenza organizzativa** di quest'anno che possiamo muovere?
  Perche' lo chiediamo: e' il vincolo che piu' stringe sul riempimento del venerdi di G04
  (la classe ha 19 venerdi in cui il nostro motore colloca 3 ore invece delle 5 attese dal
  calendario). T24 e' lo stesso caso (libero il venerdi), ma la sua e' gia' in attesa di
  conferma come gli altri Q4. Se T05-venerdi fosse mobile, guadagniamo capienza di venerdi
  per G04 (e le altre classi che ha).

**(2) Conferme di inferenze che abbiamo gia' fatto dai dati (SI/NO).**
- **(Q19)** G14 non occupa mai il laboratorio di acconciatura in esclusiva (sempre ospite
  mentre c'e' una classe titolare, oppure in aula). Confermi che non toglie mai il laboratorio
  a un'altra classe? (Nei vostri dati: 30 ore, tutte di giovedi', in cui G14 e' in
  contemporanea con una classe titolare.)
- **(Q20)** G12 non usa il laboratorio di acconciatura in esclusiva come le altre classi:
  confermi che quelle 12 ore di sovrapposizione di giovedi' (con T01 e un'altra classe) sono
  ospite o altrove?
- ~~(Q16, ampliata) Calendario G14~~ **[CHIUSA 30/07, non chiedere]**: maintainer ha indicato il
  blocco autoritativo, fa fede "G14 2" (120 ore: 70 aula + 50 lab); il foglio "G14" e' il
  default non compilato del workbook. Era un NOSTRO difetto di ingestione (TASK-462, corretto
  il 30/07: estrattore su 'G14 2' + split aula/lab nei fact). Riferimento:
  monte-ore-autoritativo-2026-07-30.md.

**(3) Conferme rapide minori (SI/NO).**
- (Q1) T10, giovedi': libero solo la mattina (8-12) o tutto il giorno?
- (Q2) T12, riunione di equipe: lunedi' o martedi'?
- (Q3) T13: nessun vincolo di disponibilita' (solo un appunto materia/aula)?
- (Q9) Ore MODA, dove foglio turni e orario non coincidono (es. G01 Confezione 180 vs 169):
  vale l'orario?
- (Q10) G04, 23h di quota lab non coperte da materie di lab: si riempiono con altre (tipicamente
  Informatica), ed e' corretto?
- (Q17) G08, 23 e 26 aprile 2027: stage (8h, dice il foglio) o lezione normale (5h, dice
  l'orario)?
- (Q28) **G14, sabato 16/01/2027**: il foglio "G14 2" segna 5 ore d'aula quel sabato
  (nota: "colloqui t14 da orientamento"), e sono esattamente le 5 ore di differenza
  fra il foglio (120h) e gli incarichi (115h). E' lezione vera (fuori dalla nostra griglia
  lun-ven) o un appunto organizzativo?
- (Q27) **G04, stage**: il foglio calendario dichiara stage ZERO per G04, ma l'orario
  d'esempio colloca 240 ore di stage (30 date, 26/04-14/06). Noi seguiamo l'orario (240h di
  stage, e in quei giorni niente lezioni d'aula). Confermate che G04 fa lo stage? E' una
  presa d'atto: se il foglio avesse ragione lui, ce lo dite e adeguiamo.

**Gia' risposte da referente (29/07) - chiuse, non si ri-chiedono:** Q5 (G14 thu-ven, corso breve),
Q8 (stage a chiusura/sabato non si recupera), Q11 (Progettazione Termoidraulica in aula), Q12
(Tecnologia tessile in aula), Q13 (Taglio Metalli in aula), Q14 (Servizio stilistico e Servizio
tecnico e stilistico = pratica in laboratorio, NON si de-taggano), Q18 (R17 22 postazioni),
Q21 (aule ordinarie + laboratori usabili come aula normale, R18 esclusa).

---

## A. Conferme rapide (SI / NO)

Sono conclusioni gia' ricavate dal vostro orario. Se sono tutte giuste, sono dodici SI.

**Disponibilita' dei docenti**

1. **T10, giovedi'.** Il foglio dei vincoli dice due cose insieme, "Libero il Giovedi'
   mattina" e "per LES e G14 9,00-17,00": la prima riga blocca solo la mattina, la seconda
   sembra coprire tutta la giornata. Noi teniamo occupata solo la MATTINA (8:00-12:00) e lo
   consideriamo disponibile per le lezioni dal primo pomeriggio. E' giusto?

2. **T12, riunione di equipe.** Il foglio offre due alternative, "il martedi'" oppure "il
   lunedi' dalle 10.00", e non dice quale valga. Noi gli teniamo libere le prime due ore
   (8:00-10:00) di ENTRAMBI i giorni, e infatti nel vostro orario non insegna mai in quella
   fascia ne' il lunedi' ne' il martedi'. Se il giorno vero e' uno solo, dicci quale e
   liberiamo l'altro per le lezioni.

3. **T13.** Alla sua riga il foglio dei vincoli riporta soltanto "Comportamento sociale
   lab due", senza giorno ne' orario. Abbiamo concluso che sia un appunto su materia e aula
   (insegna Comportamento Sociale a G14, e "lab due" sarebbe l'aula), non un vincolo di
   disponibilita'. Confermi che non dobbiamo tenerle libero nessun momento della settimana?
4. **Docenti con giorni fissi non scritti nei fogli.** Perche' lo chiediamo: da questa risposta
   dipende se trattiamo il docente come **vincolato** e lo **pre-assegniamo** prima del solve
   (chi ha un solo giorno a disposizione va collocato a priori, altrimenti il solver non ce la
   fa). Per ciascuno: e' una **vera indisponibilita'** (vincolo da rispettare ogni anno) oppure
   una **preferenza organizzativa di quest'anno** che possiamo cambiare?
   - **T01** solo giovedi' (231h) - **CONFERMATO (maintainer 30/07)**, non serve rispondere.
   - **T07** solo mercoledi' - vero vincolo o preferenza?
   - **T19** solo lunedi' e martedi' - vero vincolo o preferenza?
   - **T24** solo lunedi' e martedi' - vero vincolo o preferenza?
   - **T22** lunedi' e giovedi' - vero vincolo o preferenza?
   - **T12** lunedi' e martedi' - vero vincolo o preferenza?
   - **T17** mai lunedi' - vero vincolo o preferenza?
   - **T16** quasi mai martedi' - vero vincolo o preferenza?
   (Se qualcuno e' solo una scelta di comodo, dicci quale: lo liberiamo e guadagniamo capienza.)

**Calendario e organizzazione**

5. **G14** fa lezione solo giovedi' e venerdi'. [RISPOSTA GIA' NOTA, maintainer 29/07: corretto.
   G14 e' un corso breve "overview" per studenti piu' giovani, un riassunto illustrativo
   dell'offerta della scuola, quindi NON ha 990 ore. Resta solo da capire perche' il foglio dei
   giorni di lezione gliene assegni 990, vedi punto 16.]
6. **Pomeriggi**: gli slot del pomeriggio servono soprattutto allo stage, ma restano usabili per
   lezioni ordinarie quando serve capienza (il vostro orario ne usa 575).
7. **Spalmare fino a giugno.** [DEDOTTA DAL VOSTRO ORARIO, non serve rispondere se e' giusta:
   ogni classe ha lezione in ogni mese e l'ultima lezione cade tra il 3 e il 15 giugno 2027,
   cioe' alla fine dell'anno scolastico. Nessuna classe chiude in anticipo; i mesi a zero sono
   periodi di stage, non fine anticipata. Segnalaci solo se qualche classe DEVE finire prima,
   per esami o altro.]
8. **Stage a scuola chiusa e di sabato**: 280 ore di stage cadono su chiusure e sabati (anche
   G10 e G12). Le contiamo fuori griglia e non le recuperiamo, perche' i luoghi di lavoro
   restano aperti. [RISPOSTA GIA' NOTA, maintainer 29/07: corretto, il conto ore deve tornare ma
   quelle ore non vanno recuperate. Resta a te solo un'eventuale smentita.]
9. **Ore MODA**: dove il foglio turni e l'orario non coincidono (es. G01 Confezione, 180
   contro 169), vale l'orario.
10. **G04**: le 23 ore di quota lab che nessuna materia di laboratorio copre si riempiono con
    altre materie, tipicamente Informatica, ed e' corretto cosi'.

**Aule dei laboratori** (nate misurando il vostro orario questa settimana)

11. **Progettazione Termoidraulica** si svolge in aula normale, NON nel laboratorio
    termoidraulico. (Nel vostro orario va in parallelo con Laboratorio Termoidraulico di
    un'altra classe piu' di cinquanta volte, e il laboratorio e' uno solo.)
12. **Tecnologia tessile** si svolge in aula normale, NON in R16. (Stessa misura: in
    parallelo con Confezione di un'altra classe ventiquattro volte, e la R16 e' una sola.)

---

## RISPOSTE DI referente, 29/07 ore 15:09-15:13 (via maintainer)

- **Servizio stilistico e Servizio tecnico e stilistico: acconciatura.** Sono pratica in
  laboratorio, quindi NON si de-taggano. Cade la leva da 89 ore che avevamo misurato.
- **Due classi diverse nello stesso laboratorio: NO.** "Una classe in laboratorio, le altre no.
  I laboratori sono uno per specialita'." Vedi sotto la discrepanza che resta da chiarire.
- **G14**: "corso particolare che si svolge solamente per alcune settimane all'anno, il
  giovedi' e il venerdi', per tre-quattro ore al giorno". Coerente con le ~115 ore degli
  incarichi e con la chiusura del punto 16. Non risponde pero' a dove sia G14 quando il
  laboratorio e' occupato da un'altra classe: vedi discrepanza.
- **Taglio Metalli: in aula.** Da de-taggare (vale 4 ore direttamente, ma toglie anche pressione
  sull'officina).
- **R17: usarla solo per saldatura, 22 postazioni.** Quindi da capienza 0 a 22, tag
  saldatura: "Tecniche di saldatura" di G13 ha finalmente una stanza legale.
- **R18: non usarla.** Resta fuori dal parco aule.
- **Progettazione Termoidraulica e Tecnologia tessile: in aula normale.** Conferma quanto
  avevamo gia' dedotto e applicato.

### Discrepanza che resta aperta (orario contro risposta)

Applicando le tre riclassificazioni confermate e contando la capacita' vera (informatica ha DUE
laboratori), gli slot in cui il vostro orario mette piu' classi dello stesso laboratorio
scendono da 258 a **44**. Non sono zero, e si concentrano tutti sull'acconciatura:
- **30 riguardano G14** (25 volte insieme a G06, 5 insieme a G02): quando G14 ha
  Servizio stilistico nella stessa ora in cui il laboratorio e' occupato da un'altra classe,
  dove si trova G14?
- **14 riguardano G12** (Servizio stilistico, insieme a G02, G06 o G10): il quarto anno
  usa il laboratorio di acconciatura o lavora altrove, per esempio in salone?

Sono le due uniche domande rimaste sulle aule, e insieme valgono le 75 ore di Lavaggio che
restano senza spazio. Riformulate qui sotto come conferme rapide.

---

## INFERENZA DAL VOSTRO ORARIO (Fedora, 29/07 sera) - restringe le due domande

Abbiamo cercato la struttura delle 44 sovrapposizioni residue invece di chiedervele alla cieca,
e la struttura c'e', fortissima:
- **Tutte e 44 cadono di GIOVEDI'**, nessuna negli altri giorni.
- **42 su 44 sono la stessa coppia di docenti**: T01 con una classe titolare (1, 2 o G10)
  e contemporaneamente T26 con G14 oppure con G12. Le altre 2 sono T16 e T17
  sulla R16.

E il conto per classe separa nettamente i due casi:
- **G14: 30 ore in acconciatura, e in TUTTE E 30 c'e' un'altra classe nello stesso momento
  (100%).** Non ha mai il laboratorio per se'. Un'occupazione esclusiva non esiste proprio,
  quindi o e' ospite o e' altrove: in entrambi i casi non toglie il laboratorio a nessuno.
  Questa la consideriamo risolta dai dati, salvo vostra smentita.
- **G12: 121 ore in acconciatura, di cui solo 12 in contemporanea con un'altra classe (10%).**
  Qui e' il contrario: G12 usa il laboratorio normalmente, e restano da spiegare soltanto
  quelle 12 ore, tutte di giovedi' con T26 mentre T01 ha un'altra classe.

Quindi delle due domande sotto, la 19 e' quasi chiusa e la 20 si restringe a dodici ore.

## D. Ultime due, di nuovo solo SI o NO

19. **G14 non occupa mai un laboratorio "da sola".**
    Cioe': o le sue ore si fanno in aula, oppure i ragazzi entrano come ospiti mentre dentro
    c'e' gia' una classe titolare. Per noi le due cose sono equivalenti, in entrambi i casi
    smettiamo di contare G14 come occupante del laboratorio: quello che ci serve e' sapere
    che non toglie mai il laboratorio a un'altra classe. Oggi lo trattiamo come occupante
    (30 ore di Servizio stilistico e 30 di Confezione) e questo genera 30 sovrapposizioni che
    probabilmente non esistono nella realta'. Confermi che non lo toglie mai a nessuno? Se
    poi ti va di dirci quale delle due (aula oppure ospite in laboratorio) ce lo segniamo, ma
    non cambia il risultato.

    **Cosa vediamo nel vostro orario**, per farti capire da dove nasce la domanda: sono 30
    ore, tutte di GIOVEDI', in cui G14 con T26 e una classe titolare con T01
    risultano insieme in acconciatura, per ore consecutive della stessa mattina. Per esempio
    giovedi' 28 gennaio 2027 dalle 8 alle 13: G06 fa Servizio tecnico e stilistico con
    T01 e G14 fa Servizio stilistico con T26. Stesso schema il 4 febbraio e negli
    altri giovedi'. Sono due docenti diversi e due classi diverse, quindi non e' una
    compresenza: o G14 e' altrove, o entra come ospite.

21. **Quante aule ordinarie avete davvero?** [RISPOSTA GIA' NOTA, maintainer 29/07: i laboratori
    possono essere usati anche per lezioni normali, dando priorita' alle attivita' di
    laboratorio vere. Questo spiega i 66 slot e toglie il bisogno di aule fantasma. Il nostro
    motore lo VIETAVA, non con un vincolo ma nel dominio dei valori: una lezione non di
    laboratorio poteva vedere solo le aule senza tag. In correzione. A referente resta da
    confermare solo se qualche laboratorio faccia eccezione, cioe' non sia mai usabile come
    aula normale.]
    Questa e' nata da una vostra risposta e conta piu' delle altre. Ci hai detto di non usare
    l'R18, ma noi contiamo **10 aule ordinarie** (R01, R02, R03,
    5, 6, 7, 8, 9, 10, R10) e nel vostro orario ci sono **66 occasioni in cui 11, 12 o
    13 classi hanno contemporaneamente una lezione fuori dai laboratori**. Con dieci aule non
    ci starebbero. Quindi delle due l'una: o esistono altre aule che non conosciamo (e ci
    servono nome e capienza), oppure in quei momenti qualche classe usa un laboratorio libero
    come aula normale, e in tal caso dicci quali laboratori si possono usare cosi'. Finche'
    non lo sappiamo teniamo l'R18 nel conteggio, perche' togliendola il motore
    non troverebbe posto a circa 181 lezioni.

20. **G12 fa Servizio stilistico e Servizio tecnico e stilistico nel laboratorio di
    acconciatura, come le altre classi.**
    Se invece il quarto anno lavora altrove, per esempio in salone o durante lo stage, dicci
    dove: sono 76 ore che oggi contendono il laboratorio alle altre classi, e nel vostro orario
    si sovrappongono a G02, G06 e G10 in 14 occasioni.

## B. Due opzioni, cerchia quella giusta

> **[SUPERSEDATO 30/07]** Il quadro "189 ore senza aula = serve spazio" e' retrattato: il
> laboratorio di acconciatura e' a circa meta' della capacita' annua e le ore senza stanza
> nascono dalla compressione del nostro motore, non da uno spazio mancante. Vedi REVISIONE
> 30/07 in cima al file. Il testo originale resta qui sotto per traccia.

Nota sul peso di queste risposte: abbiamo misurato quante ore di laboratorio restano oggi
senza un'aula assegnabile, e sono 189. Le risposte qui sotto ne recuperano al massimo 107.
Le altre 82 non dipendono da come classifichiamo le materie: sono ore che non hanno
materialmente uno spazio, e la parte grossa e' il Lavaggio (75 ore). Per quelle serve la
domanda 14-bis.

13. **Taglio Metalli** si fa in **officina meccanica** oppure in **R17**?
    Nel vostro orario va spesso in parallelo con Lavorazioni meccaniche di un'altra classe, e
    l'officina meccanica e' una sola: se Taglio Metalli sta in R17, il conflitto sparisce.
    (Vale 18 ore se sta in R17. Se invece si facesse in aula normale ne varrebbe 4, quindi
    la domanda utile e' proprio officina contro R17.)

14. **Servizio stilistico** e **Servizio tecnico e stilistico** sono **pratica nel laboratorio
    acconciatura** oppure **teoria in aula**? Anche questi vanno in parallelo tra classi diverse
    e il laboratorio acconciatura e' uno solo. La nostra ipotesi e' pratica, ma allora due
    classi condividono davvero il laboratorio nella stessa ora: funziona cosi'?
    (E' la risposta piu' pesante di tutte: se si fanno in aula, si sistemano 89 ore.)

14-bis. **[CHIUSA 30/07]** ~~Il Lavaggio dove si fa, oltre al laboratorio di acconciatura?~~
    Nel vostro orario definitivo il Lavaggio non si sovrappa mai a nient'altro: la compressione
    che lo lascia senza stanza la fa il nostro motore, non e' un problema di spazi della scuola.
    Non si chiede. *(Testo originale, per traccia:)* Questa era la domanda strutturale, e da sola
    pesa quanto tutte le altre messe insieme: 75 ore di Lavaggio restano senza spazio perche' il
    laboratorio di acconciatura e' uno solo ed e' gia' pieno in quelle ore. Nessuna
    riclassificazione di materie le sistema. Esiste un secondo punto lavaggio, oppure due classi
    lavorano insieme, oppure quelle ore si fanno altrove?

---

## C. Qui serve la tua conoscenza (quattro domande vere)

15. **T16, seconda mattina libera dalle 11:00: quale giorno?**
    Il martedi' e' confermato dall'orario. Il mercoledi' no (T16 insegna comunque nelle
    prime ore del mercoledi' in 34 lezioni su 495), quindi la seconda mattina libera e' un altro
    giorno, oppure non esiste.

16. **G14: perche' il foglio le assegna 990 ore?**
    Sappiamo che G14 e' un corso breve di panoramica (circa 115 ore negli incarichi) e il
    motore la tratta gia' cosi'. Il foglio dei giorni di lezione, pero', le assegna 990 ore come
    a una classe a tempo pieno. E' una riga da correggere nel foglio, oppure quelle giornate
    hanno un altro significato che ci sfugge?

17. **G08, 23 e 26 aprile 2027: stage o lezione?**
    Il foglio FC dice 8 ore di stage in entrambe le date, il vostro orario dice 5 ore di lezione
    normale. Quale dei due vale?

18. **R17: quanti allievi ci stanno?**
    Nei nostri dati risulta a capienza zero, quindi il motore la tratta come inagibile. Se e'
    un'aula vera ci serve il numero.

---

Se una delle conclusioni della sezione A e' sbagliata, basta il NO con una riga: correggiamo il
motore e ti diciamo cosa cambia.

---

## REVISIONE 14/08 (dalla giornata golden-oracle: TASK-210/462/472/395/461 chiuse)

### Nuove domande (breve, misurata sul vostro orario)

**A. (SI/NO) I giorni "corti" di G04 sono voluti.**
G04 ha 835 ore di scuola disponibili in calendario ma 750 ore di insegnamento assegnato:
il vostro orario lascia esattamente 90 giorni con UN'ORA in meno (mai due). Noi leggiamo:
mettete l'orario al monte-ore assegnato, non a riempire tutto il calendario.
SI = confermato cosi'. NO = quei buchi erano da colmare (e come?).

**A. (SI/NO) Stessa cosa per G08** (690 disponibili, 666 assegnate: ~24 ore sparse).

**C. (conoscenza vera) Le 5 ore "fuori calendario" di G04.**
Il vostro orario definitivo colloca 5 ore di lezione in date dove il foglio calendario
(DEF FC TUTTI) non prevede alcuna ora disponibile. Chi ha ragione: quelle 5 ore sono
reali (e il calendario ha 5 ore mancanti) o sono ore che il calendario non concedeva?

**Riferimento per il pacchetto teoria**: le 440 giornate con 2 ore della stessa materia
teorica si scompongono (report task472-results/report-occorrenze-2026-08-14.md): 268 su
materie che fate a meta' e meta' (la domanda del foglio 22 del workbook), 60 su materie
che ammettete col blocco 2h, 36 compattate in un giorno, 76 senza spiegazione nostra.

### Ratifica 15/08 (notifica, conferma a posteriori)

**A. (SI/NO) Il "tetto" di riempimento al monte-ore assegnato.**
Abbiamo allineato il nostro motore alla pratica del vostro orario: il riempimento giornaliero
non insegue piu' tutta la capacita' del calendario ma il monte-ore assegnato per gruppo
(G04: 750 assegnate su 835 disponibili; G08: 666 su 690), col deficit sparso in giorni
da un'ora in meno, come fate voi. Eseguite production cosi' dal 15/08. Segnalaci se la
lettura e' sbagliata.

**A. (SI/NO) Sicurezza SEMPRE prima dello stage (promossa a vincolo rigido).**
Il vostro orario non la viola mai (6 gruppi con Sicurezza e stage: tutte le ore di Sicurezza
prima della prima data di stage). L'abbiamo resa vincolo rigido (hard). Confermate che e'
una regola assoluta e non una coincidenza dell'anno.

## REVISIONE 16/08 sera (golden mining round 2: fogli 24 e 25 del workbook)

### Nuove domande (brevi, misurate sul vostro orario; i fogli sono gia' nel workbook)

**A. (SI/NO per riga) Le concentrazioni mensili del foglio 24_ConcentrazioneMensile sono scelte.**
6 casi in cui il vostro orario concentra oltre il QUADRUPLO dell'atteso di un mese una
materia che sta li' almeno un quarto dell'anno: G02 Servizio stilistico (gennaio 18h),
G02 Barberia (febbraio 18h), G12 Servizio tecnico e stilistico (aprile 32h, il 70%
dell'anno!), G04 Recuperi ed Approfondimenti (aprile 28h), G06 Barberia (marzo 23h),
G02 Barberia (dicembre 12h). SI = il corso si recupera a blocchi in quel mese per scelta
(lo dichiariamo come input). NO = e' contingenza (non si dichiara, il solver la ricrea da
solo quando la capacita' lo costringe).

**B. (SI/NO) Le 4 celle under-fill del foglio 25_WeekdayFill: il calendario sovrastima G04 su lun-gio, o la scuola lascia corto per scelta?**
Correlata alla domanda A del 14/08 (835 ore disponibili vs 750 assegnate a G04): il gap
sta tutto su lunedi'-giovedi' (68/68/79/67% di riempimento rispetto al vostro calendario;
il venerdi' e' al 97%). SI = voluto (dichiarato il fill praticato). NO = il foglio
calendario di G04 prevede troppe ore su quei giorni (va corretto). NOTA: la prima
versione di questa domanda citava anche venerdi' leggeri di G12/G10/G11/G13:
era un artefatto del nostro conteggio (contavamo i venerdi' di stage come ore attese);
con il conteggio corretto quelle celle sono piene al 95-100% e NON sono in domanda.

## REVISIONE 16/08 notte (TASK-506 stadio 2, calibrazione golden)

**C. (SI/NO) Le 16 ore di stage di G04 oltre la fine ufficiale dell'anno.**
Il piano stage di G04 (e la vostra soluzione reale) usa giovedi' 11 e lunedi' 14
giugno 2027, oltre la fine ufficiale del 10 giugno: 2 giornate da 8 ore che il
calendario dichiara ma che cadono fuori dalla griglia dell'orario. SI = l'anno si
estende per quelle giornate di stage (le dichiariamo come off-grid attese). NO =
quelle 16 ore vanno recuperate dentro la griglia (e il calendario va corretto).

**D. (SI/NO per riga) I giorni-L del foglio calendario vincolano i laboratori?**
Il vostro orario definitivo colloca ore di laboratorio anche in giorni che il
foglio non marca come L (misurato: G03 55h fuori, G07 40h su 64 totali, G08
94h, G12 70h). SI = i giorni-L sono vincolanti (l'orario li sta violando, o sono
da ridichiarare). NO = i giorni-L sono indicativi e il lab va dove serve.

## REVISIONE 17/08 mattina (autorisposte dal golden, metodo TASK-466)

Il golden ha risposto a otto domande. Restano a referente solo le intenzioni (vincolo vs preferenza)
e due conferme di cortesia. Ogni autorisposta adotta la pratica del golden e resta notifica a
posteriori non bloccante.

### AUTORISPOSTE (evidenza misurata sul vostro orario)

- **Q15 T16, seconda mattina libera dalle 11:00 = MERCOLEDI'.** Ore di T16 dalle 11:00
  in poi, per giorno della settimana, su tutto l'anno: lunedi' 98, venerdi' 52, giovedi' 12,
  MERCOLEDI' 2, martedi' 1. Le due mattine praticamente vuote sono martedi' (nota) e mercoledi'.
  Adottiamo: T16 libero da slot >=11 anche il mercoledi'.
- **Q3 T13 = nessun vincolo di disponibilita'.** Insegna tutti e cinque i giorni
  (mattina sempre; qualche pomeriggio lun/gio/mar). Nessun buco sistematico: nessun vincolo
  praticato. Adottiamo: nessuna restrizione.
- **Q17 G08, 23 e 26 aprile 2027 = LEZIONE NORMALE, non stage.** Il vostro orario in quelle
  due date colloca due giornate piene di lezione (23/4: Storia, Lingua Italiana, Scienze,
  Tecnologia Meccanica x2; 26/4: Controllo numerico + Lavorazioni meccaniche x4, ore 8-13).
  Il foglio calendario che le dichiara stage 8h e' smentito dall'orario: seguiamo l'orario.
  Divergenza da correggere nel foglio (2 righe).
- **Q28 G14, sabato 16/01/2027 = attivita' reale, fuori griglia, non recuperata.** Le 5 ore
  sono nel vostro orario (Orientamento). Stessa famiglia delle 280 ore di stage su chiusure e
  sabati gia' confermata da maintainer il 29/07 (non si recuperano). Chiudiamo: contate come
  off-grid attese nel monte-ore (expectedUnscheduledHours).
- **Q10 G04, le 23h di quota lab non coperte da materie di lab = SI', si riempiono con
  altre.** Nei giorni di G04 a quota lab > 0 il vostro orario colloca, oltre ai lab veri
  (Lavorazioni meccaniche 148, Cad 20): Recuperi ed Approfondimenti 46, Informatica 26, e
  ordinarie (Competenze 9, Storia 8, Lingua Inglese 6, Economia 6). Confermiamo la lettura.
- **Q9 ore MODA = vale l'orario** (gia' politica golden-first, censimento divergenze TASK-466
  fatto: resta solo la notifica formale).
- **Q27 G04 stage 240h = presa d'atto confermata** (gia' adottato: in quelle 30 date niente
  aula; il foglio che dice stage-zero e' smentito dall'orario).
- **14/08 C, le 5 ore fuori calendario di G04 = Scienze/T25, 15 giugno 2027.** Sono una
  giornata reale dell'orario su una data che il calendario non concede (quota zero) e che cade
  OLTRE la fine ufficiale del 10 giugno. CONSEQUENZA: il vostro orario di fatto estende l'anno
  al 15 giugno (stage gio 11 + lun 14 + Scienze mar 15). Chiudiamo la domanda 16/08-C nello
  stesso modo: quelle giornate sono reali, off-grid, non recuperate (precedente maintainer 29/07).
  Da qui una NUOVA domanda di correzione input, vedi sotto.
- **16/08 D, giorni-L = INDICATIVI, non vincolanti.** Il vostro orario colloca laboratori
  fuori dai giorni-L su quattro classi (misurato: G08 94h, G12 70h, G03 55h, G07
  40h su 64). La pratica dell'orario smentisce il vincolo: adottiamo i giorni-L come
  indicativi, la quota lab resta il fatto che conta. Da correggere nel foglio o dichiarare.
- **16/08 A concentrazioni: il golden dice NON-forzate (a livello di classe).** Ogni mese
  dell'anno ha 30-66 ore di spazio residuo per ciascuna classe (misurato giorni x finestra -
  collocate): le concentrazioni del foglio 24 non erano obbligate dalla capacita' della
  classe. Nota di onestà: non abbiamo modellato la capacita' del DOCENTE (per materie di
  docenti condivisi, es. T01 solo-giovedi', la stretta puo' venire dal docente): la
  lettura forte "scelta" resta da confermare, ma "necessitate dalla classe" e' escluso.

### RESTANO A referente (le vere)

1. **Q4/Q29 vincolo o preferenza** (T07, T19, T24, T22, T12, T17,
   T16-martedi', T05-venerdi'): il golden mostra la pratica, non la volonta'. Se qualcuno
   e' solo scelta di comodo, dirci quale libera capacita'.
2. **Q1 T10 giovedi' pomeriggio**: l'orario non lo usa MAI il giovedi' pomeriggio (max
   slot 12:00), quindi la pratica non distingue "libero tutto il giorno" da "solo mattina"-
   per default noi lo scheduliamo anche al pomeriggio: confermare che si puo'.
3. **Q2 T12, riunione lunedi' o martedi'** (noi teniamo liberi 8-10 entrambi).
4. **Q20 G12, 12 ore di giovedi' ospite o altrove** (l'orario non registra le aule).
5. Conferme di cortesia delle ratifiche gia' in produzione (tetto monte-ore 15/08, Sicurezza
   hard 15/08, foglio 24/25).

### NUOVA domanda di correzione input (da 14/08-C + 16/08-C)

**E. (correzione foglio) La coda dell'anno: G04.** Il vostro orario usa l'11, il 14 (stage,
16h) e il 15 giugno (Scienze, 5h), oltre la fine ufficiale del 10 giugno. Per noi sono 21 ore
off-grid non recuperate (le contiamo nel monte-ore fuori griglia). Se invece l'anno si estende
formalmente a quelle date, ditecelo: la griglia si allungherebbe di 3 giorni (171 -> 174) e
quelle ore tornerebbero collocabili.

### Dato nuovo per la Q20 (spike notte 19/08, attribuzione meccanica sul golden)

Le 12 ore di co-presenza G12/FA in materie lab-acconciatura sono TUTTE di
**T26** (G12, Servizio stilistico) in slot simultanei a ore
lab-acconciatura di **T01** (lato FA). Nessuna delle 12 e' di T01.
La domanda referente diventa: "le 12 ore di Servizio stilistico di T26 su
G12 in concomitanza con le ore di T01: co-presenza voluta (T26
segue la classe FA come assistente) o semplice sovrapposizione di slot?"
T26 non e' vincolata al giovedi': se e' solo sovrapposizione, il solver la
risolve da solo senza esenzioni; se e' co-presenza voluta, va dichiarata nel
workbook (foglio co-presenza).

## REVISIONE 19/08 (ispezione run golden-free: pattern di qualita' da confermare)

Domande di PREFERENZA QUALITATIVA (non bug): nel nostro orario automatico alcuni pattern sono piu' regolari del vostro orario fatto a mano. La regolarita' puo' essere voluta o puo' essere il caso del vostro orario. Per ognuna: la codifichiamo come preferenza (il solver la cerchera' sempre) o lasciamo il solver libero?

**P1. (SI/NO) G06, venerdi' corti regolari.** Il vostro orario ha 25 giorni da 4h sparsi; il nostro ne ha 23 di cui 19 al venerdi' (schema fisso). Teniamo lo schema fisso?

**P2. (SI/NO) G07, distribuzione 5h/6h.** Il vostro orario ha 97 giorni da 5h e 33 da 6h; il nostro 91 e 38. Il nostro sposta sei giorni da 5h a 6h. Preferenza?

**P3. (SI/NO) G14, giorni da 4 ore.** Il vostro orario non ha MAI giorni da 4h per G14 (solo 3h e 5h); il nostro ne produce 2 (28/01 e 13/05). Sono accettabili o da evitare?

## REVISIONE 21/08 (revisione pacchetto referente: autorisposte da golden/input, maintainer)

### AUTORISPOSTE (non si chiedono piu')

**P1 (G06 venerdi' corti) - RITIRATA.** Il golden pratica 25 giorni da 4h SPARSI (non ha
schema fisso del venerdi'): quindi sia lo sparso che lo schema sono compatibili con la pratica
della scuola. Default: solver libero (golden-conforme per costruzione). Nessuna preferenza da
chiedere: se un giorno la scuola vorra' lo schema fisso, sara' una dichiarazione NUOVA, non
una conferma.

**P2 (G07 distribuzione 5h/6h) - RITIRATA.** Golden 97x5h + 33x6h, nostro 91+38: entrambe
entro la gamma praticata. Solver libero, nessuna domanda.

**H11 cap 6h vs 8h (conferma di cortesia pianificata il 20/08) - MAI INVIATA.** L'input
dichiara gia' i cap per gruppo (maxHoursPerDay: 8 per G06/G10/G12, 6 o 8 per gli altri)
e il golden li rispetta tutti (i 43 giorni oltre-6h sono tutti di gruppi cap-8, dove 8h e'
LEGALE). Input e golden concordano: non c'e' nulla da chiedere. (Era stata inserita come
"conferma cortesia" nella TASK-537: annullata.)

### AFFILATA

**P3 (G14 giorni da 4h) - la domanda giusta e' un'altra.** Il golden NON ha mai 4h per
G14: solo 3h (20 giorni) e 5h (10). Non e' "4h accettabile si/no", e' che G14 ha DUE
schemas distinti (3h e 5h, forse legati a tipo di giornata). Domanda riformulata: "i giorni
G14 sono sempre o 3 o 5 ore, mai 4: e' un pattern da rispettare?" (intanto il nostro H33
floor 3h copre il minimo; i 2 giorni da 4h prodotti restano fuori pratica finche' non
rispondete).

### RESTANO GESTERNE (elenco definitivo del pacchetto)

Q4/Q29 preferenze-docente, Q1 T10, Q2 T12, Q20/T26 (voluta o sovrapposizione),
E coda-anno G04, P3-G14 riformulata, 5h-aspirazione (giorni da 4h voluti o subiti),
conferme cortesia (tetto, Sicurezza, fogli 24/25).

### rev 15 (25/08 sera, da ispezione visiva maintainer sul ciclo finisher-ON - Fedora ha verificato col golden)

**RA/Recuperi: back-loaded nel golden, piatti nel nostro run - VERO GAP DI PRATICA.**
Domanda per la scuola: "le ore di recupero (Recuperi ed Approfondimenti) vengono esplicate
DOPO le regolari? Il golden le concentra a fine anno (gen-giu 156h su 189 totali; set-dic
solo 33h), il nostro solver le spalma uniformi (1-2h/settimana tutto l'anno)." Se confermato,
entra un vincolo RA-after-base (candidato SOFT, da gate A/B, tracciato in backlog TASK sotto).
Le altre 3 anomalie dell'ispezione sono VERIFICATE COERENTI col golden (registro 25/08):
G12 giorni-zero (il golden fa lo stesso, no-session), 3/12 vuoto (idem), ideal end G08
15/06 (target cumulativo del foglio, il golden vi mette le ultime RA: marcatore corretto).

### rev 15-bis (ADDIZIONE maintainer 17:05: DUE domande oltre alle 4 verificate)

**Q-RA (formulazione maintainer): "le ore di recupero vengono esplicate preferibilmente DOPO le
ore regolari? Il golden le concentra a fine anno (gen-giu 156h su 189 totali) mentre i
nostri run le spalmano."**

**Q-G12-VUOTI (nuova, importante): "G12 ha 35 giorni feriali vuoti sparsi (18 giovedi',
9 venerdi', alcuni gia' a settembre): sono giorni voluti (es. visita aziendale/alternanza
fuori griglia) o e' effetto del non aver trovato di meglio? Nel secondo caso noi potremmo
riempirli."** FORK: se SUBITI, i giorni-zero diventano capacita' recuperabile e la regola
golden-count-wins NON si applica cieca (qui il golden potrebbe essere near-miss umano, vedi
memoria golden-vs-input-intent); se VOLUTI, restano no-session e vanno dichiarati come input.

### rev 15-ter (precisazione maintainer, verificata da Fedora, sulla Q-RA)
Il golden G08 tiene un MONTE MINORE del foglio (non-stage 667 vs 690, stage 276 vs 300) e
le RA nel golden sono una VOCE SEPARATA ("RA: 41h"), mentre nel foglio sono fuse nel monte
aula. Il marcatore M12/ideal-end usa la cumulativa del FOGLIO (690 -> meta' giugno), ma la
coda reale del golden sono le RA. SUB-DOMANDA aggiunta alla Q-RA: "le RA vanno escluse dal
calcolo del monte-ore/ideal-end (il golden le tiene come voce separata a fine anno)?"
(La risposta tocca anche il target M12/ideal-end del G08, oggi costruito sul 690 del foglio.)

### rev 16 (7/09, Fedora, dal filone TASK-577: triangolo G14/T16/G05)

**Q-G14-VENERDI (input in contraddizione, 2 ore NON piazzabili + 2 giorni sotto pavimento
con dati certi): "il foglio G14 dichiara 3h nei venerdi' 19/03 e 9/04, ma T16 (l'unico
docente G14 con ore residue, Confezione 2h) in quelle date e' occupato alle 10:00 dalle
sue ore di Confezione di G05, che sta a quota ESATTA 5/5 tutti i 174 giorni (zero
surplus: spostarla apre un buco altrove). Ogni altra ora G14 e' gia' colloccata e i
giovedi' G14 sono pieni al tetto della finestra (5h, slot 8-12). Come si concilia?"**
FORK: (a) le 2 ore di Confezione G14 in piu' vanno treatte come non-collocabili di
dichiarazione (le togliamo dal monte, come gia' fatto per l'eccesso G09 TASK-550);
(b) in quei due venerdi' la scuola sposta altrove un'ora di Confezione di G05 (ce lo
dice la scuola, non lo inventiamo noi); (c) il venerdi' G14 di quelle due date e' in
realta' da 2h (errore di riga del foglio: si corregge la riga 3->2).
NOTA: le due celle H33 (pavimento 3, collocate 2) NON dipendono da questa risposta: il
giovedi' 13/5 e' surplus +2 e la cura deterministica esiste (EDF-H33, costruita, flag
DEFAULT OFF, da misurare al prossimo ciclo).

AGGIORNAMENTO rev 16 (7/09 sera, evidenza gemella su v132-attempt1): il triangolo e'
RIPRODOTTO in un bacino DIVERSO con venerdi' DIVERSI (12/03 e 30/04 al posto di
19/03 e 9/04: le celle v131 erano chiuse, due nuove ne hanno preso il posto) e la
meccanica e' misurata per-filtro: il donatore giovedi' (13/05, surplus +2, nessun
pin) non puo' cedere ore perche' ogni estrazione richiede che T16 scivoli allo
slot delle 10 del giovedi', dove fa G01; e le ore di T16 non atterrano al
venerdi' perche' li' alle 10 fa G05. Il difetto e' CRONICO (ogni venerdi' G14
cade nel triangolo appena il surplus giovedi' e' murvaniano) e copre insieme le
celle H33-G14 e le 2 ore H0 di Confezione: UNA sola risposta della scuola li
sblocca entrambi.
