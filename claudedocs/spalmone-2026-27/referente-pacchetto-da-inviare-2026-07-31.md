# Domande per referente, pacchetto pronto da inviare (31/07/2026)

Ciao referente. Abbiamo misurato sul tuo orario definitivo 2026/27 (che trattiamo come valido
per definizione) come sono organizzate le materie di acconciatura, ed e' emersa una struttura
chiara che vorremmo farti confermare. Poi ci sono due domande di aula che si sono ristrette a
pochissime ore, e alcune tensioni nei fogli dei vincoli che abbiamo bisogno di dirimere con te.

Per ogni punto trovi: la nostra conclusione dai dati, la domanda, e cosa cambia per noi a
seconda della risposta. Tutto quello che avevi gia' confermato il 29/07 non e' ripetuto qui.

## Sintesi: come rispondere (in ordine di fatica)

1. **Rotazione acconciatura** (§1, la domanda principale): un **SI/NO** alla conferma che
   funziona a rotazione per periodi, e una indicazione su come decidete la rotazione ogni
   anno (prassi fissa della scuola o scelta libera di quest'anno).
2. **Q19, G14 ospite** (§2): un **SI** ci basta (G14 non toglie mai il laboratorio).
3. **Q20, G12, 12 ore di giovedi'** (§2): una risposta, **aula normale SI/NO**.
4. **Tensioni T1–T8** (§3): per ciascuna, un'indicazione su quale vuoi che modelliamo.

---

## 1. La rotazione acconciatura per periodi (la domanda principale)

**Cosa vediamo nel tuo orario.** L'acconciatura e' organizzata come una **rotazione per
periodi**: uno slot settimanale fisso (stesso giorno, stessa ora) e' tenuto da un gruppo per
un tratto dell'anno, poi quello stesso slot passa a un altro gruppo. L'esempio piu' pulito e'
il blocco del **giovedi' mattina** (8-13) di T01, che ruota cosi':

> G06 (set-ott) -> G02 (ott-nov) -> G10 (dic-gen) -> G06 (gen-feb) -> G02 (feb-mar)
> -> G12 (apr-mag) -> G10 (mag-giu)

Lo stesso schema a rotazione si ripete per gli altri tre docenti (T26 di Servizio
stilistico, T18 di Lavaggio, T19 di Barberia): ciascuno tiene un blocco
(giorno, fascia-oraria) per un periodo e poi lo lascia a un altro gruppo. T01 e'
l'unico vincolato a un solo giorno (giovedi', 231h tutte li'), e infatti il suo blocco e'
l'ancora attorno a cui gli altri si dispongono.

**Domanda.** Confermi che l'acconciatura funziona cosi', a **rotazione per periodi**? E in
quest'anno la rotazione (quale gruppo in quale periodo) come l'avete decisa: e' una prassi
fissa della scuola, oppure una scelta organizzativa che fate caso per caso ogni anno?

**Cosa cambia per noi.** Oggi ricostruiamo la rotazione dall'orario. Se la rotazione e' una
**prassi strutturale** che voi decidete in input, allora ha senso che ce la passiate compilata
(un foglio "rotazioni" nel workbook: docente, giorno, slot, gruppo, periodo), e noi la
rispettiamo. Se invece e' una **scelta libera di quest'anno**, la trattiamo come istanza e
provvediamo noi a generarne una valida ogni anno.

---

## 2. Le due domande di aula (Q19 / Q20), riformulate con la rotazione

Sulle 44 sovrapposizioni residue nel laboratorio di acconciatura avevamo gia' visto che tutte
cadono di giovedi' e sono sempre la coppia T01 (con la titolare FA) + T26 (con
G14 o G12). La rotazione le spiega quasi tutte.

**Q19, G14 e' ospite, non toglie mai il laboratorio (conferma).** Nel tuo orario G14 ha
30 ore di Servizio stilistico con T26, **tutte di giovedi', sempre in contemporanea con
una classe titolare** (G06 per 25h, G02 per 5h) tenuta da T01. Non c'e' una sola ora
in cui G14 occupa il laboratorio da sola. Confermi che G14 entra come **ospite** (o sta in
aula) e non toglie mai il laboratorio a una classe titolare? Per noi i due casi sono
equivalenti: in entrambi smettiamo di contare G14 come occupante. Se ci dici quale delle due
(ospite nel laboratorio oppure in aula) ce lo segniamo, ma non cambia il risultato.

**Q20, G12: le 12 ore parallele del giovedi' sono in aula normale? (la domanda che
resta).** A differenza di G14, G12 e' **titolare del laboratorio al 90%** (109 ore su 121
sono esclusive). L'unica eccezione sono **12 ore, tutte di giovedi'**, in cui T26 fa
Servizio stilistico con G12 mentre T01 tiene una titolare FA di Servizio tecnico.
Confermi che quelle 12 ore sono una **sessione parallela in aula normale** (non nel
laboratorio di acconciatura)? E' l'unica sovrapposizione reale del quarto anno; se ci confermi
"aula normale" la modelliamo cosi' e chiude.

---

## 3. Tensioni nei fogli dei vincoli (da dirimere con te)

Abbiamo allineato i tre tab "Vincoli*" del tuo xlsx con cio' che abbiamo gia' modellato. Gran
parte coincide. Restano queste, dove il foglio dice una cosa che non riusciamo a far quadrare
con l'orario o con gli altri fogli. Per ognuna ti diamo anche **cosa fa il tuo orario** (il
dato golden), per aiutarti a decidere se e' una regola reale andata perse o un appunto non
applicato.

**(T1) Professionalizzanti di G12: quali giorni?** Il nostro vincolo dice che le
professionalizzanti di G12 stanno di **mercoledi' e giovedi'**; il tab "Vincoli Classi"
invece dice *"Professionalizzanti solo il giovedi' e il venerdi'"* (dal 19/11, con la regola
salto-prime/ultime-2-ore il martedi'/mercoledi'). Quali dei due e' quello giusto? (E' una
contraddizione tra i tuoi fogli, non tra noi e te: dicci quale fa fede e allineiamo.)

**(T2) T20 "inizia materia e finiscila".** Il tab chiede la **serializzazione** delle
materie (una materia cominciata va finita prima di passare alla successiva). Il tuo orario NON
la segue (le materie sono distribuite su tutto l'anno). E' un desiderio non realizzato, oppure
vale solo per alcune materie T20 (Merceologia, Tecnologia tessile)? Oggi non la modelliamo;
se la vuoi, la mettiamo come preferenza debole.

**(T3) T21 "T30: non le prime ore in G08 e 4 FA; ore di mezzo nella giornata".**
Chiede un posizionamento **a meta' giornata** per-docente-per-gruppo. Non l'abbiamo
modellato. **Dato del tuo orario**: T21 (Lingua Inglese) ha **26 ore su 42 a prime ore
(slot 8-9)** per G08 e 5 su 40 per G12, ben lontano dalle "ore di mezzo". E' un vincolo
reale che quest'anno non e' stato rispettato, oppure e' un appunto non applicato (o T21
non e' "T30")? Se non e' una regola reale la lasciamo cosi'; se lo e', va rispettata.

**(T4) T11 "6 ore dopo stage per curriculum (G08, 3 OIT, G10, anche 2 consecutive)".**
Ore-curriculum post-stage per gruppi specifici. Non modellate. **Dato del tuo orario**: per
**G10** lo rispetta in modo pulito, **esattamente 6 ore** di Comportamento sociale dopo lo
stage, in **3 blocchi di 2 ore consecutive** (giovedi' 29/04, 06/05, 13/05). Per G08 ne ha 5
(Competenze di cittadinanza) e per G11 29 ma di Personalizzazione. Confermi che il
curriculum e' Comportamento sociale / Educazione civica / Competenze di cittadinanza e che
vale per questi tre gruppi ogni anno? (G10 e' il modello pulito; se confermi lo modelliamo.)

**(T5) "Sicurezza delle prime in 2 giorni cumulativi" (4 ore con le 1°).** Concentrare la
Sicurezza dei primi anni in 2 giorni. Non modellato (oggi modelliamo solo il "Sicurezza prima
dello stage" delle terze). **Dato del tuo orario**: NON la concentra, spalma **8 ore di
Sicurezza su 8 settimane** (1h/settimana: G03/G01/G04 al lunedi', G02 mar/ven). E'
una prassi che volevi ma non e' uscita, oppure va intesa per un sottoinsieme delle 8 ore?

**(T6) Laboratorio Termoidraulico "le prime 4 ore".** Il tab chiede che il lab termo stia
nelle prime 4 ore della giornata. **Dato del tuo orario**: lo concentra al mattino ma non
rigorosamente, **235h su 313 (75%) nelle ore 8-12**, il restante 25% scolano in 12-14
(coerente con blocchi lab di 5h 8-13). "Prime 4 ore" intendi l'intervallo 8-12 tassativo, o il
blocco mattutino 8-13? (Se 8-13 e' gia' rispettato; oggi non lo modelliamo esplicitamente.)

**(T7) G10 "stage NO LUNEDI, SI SABATO".** **Dato del tuo orario**: non mette mai lo stage di
G10 di lunedi' (0 su 300h: mar/mer/gio/ven/sab), e fuori dal periodo stage il lunedi' e' un
giorno normale per G10, quindi il no-lunedi' durante lo stage e' una scelta deliberata, non
un caso. Confermi che e' una regola di scuola (stage mai di lunedi')? Se si, la cristallizziamo
come vincolo (oggi e' garantito solo dal fatto che lo stage e' legato a date fisse, non da una
regola esplicita sul weekday).

**(T8) Due dettagli sulla regola "niente 2 ore vicine della stessa materia teorica".** (Quella
regola la stiamo gia' modellando come vincolo debole: il tuo orario la viola ~432 volte e lo
accettiamo come residuo calibrato, non come divieto.) Due dettagli per chiuderla:
- **Figurino, Personalizzazione, Tecnologia e creativita'**: nel tuo orario fanno blocchi di 2h
  ma non sono nè laboratorio nè disegno/informatica. Le consideri **materia teorica** (niente
  2h nello stesso giorno, come lettere/scienze) oppure **pratiche** (blocco permesso, come
  disegno)? (Di default le trattiamo come teoria; se sono pratiche le esentiamo.)
- **T06 "non 1h info + 1h gest ma stessa materia"**: Informatica e Gestione aziendale
  vanno trattate come un'unica materia?

---

Se ti va bene rispondere cosi: un SI/NO per la rotazione (punto 1), un SI per Q19 (punto 2),
una risposta (aula normale si/no) per Q20, e un'indicazione per T1–T8 su quale fare
modellare. Tutto quello che e' gia' confermato dalle risposte del 29/07 non e' ripetuto qui.
