# Domande per la scuola — Spalmone 2026/27

Ciao referente,

nel confrontare il file "Spalmone 2026_2027 FC DUALE condiviso maintainer.xlsx" e il "Calendario scolastico 2026_2027.xlsx" con il nostro sistema abbiamo trovato alcuni punti che non riusciamo a risolvere da soli, perché richiedono informazioni che avete solo voi (o decisioni organizzative della scuola). Le ho raggruppate per argomento. Per ognuna trovi un po' di contesto, la domanda precisa e uno spazio per la risposta.

Non c'è fretta su tutte: alcune sono bloccanti per far ripartire il motore di orario, altre sono solo da chiarire quando hai un attimo (vedi anche le conferme rapide in fondo, che dovrebbero richiederti letteralmente un sì/no a testa).

Grazie in anticipo!

> Le domande già risposte sono spostate in fondo, nell'archivio "Risposte ricevute" — qui sopra restano solo quelle aperte.

---

## Q12-bis — T16: quale giorno è la seconda mattina libera dalle 11:00?

**Contesto:** Q12 (archiviata sotto, PARZIALMENTE DERIVATA) ha confermato meccanicamente dal golden che il martedì è una delle due mattine libere di T16 (0 violazioni). Il mercoledì, l'altro giorno inizialmente ipotizzato, è stato invece escluso dalla stessa misurazione meccanica: 34 lezioni di T16 su 495 (6.87%) cadono comunque negli slot 0-2 del mercoledì, ben oltre la soglia del 2% — il mercoledì mattina non è scarico nel golden, quindi non può essere la seconda mattina libera. Nessun altro giorno è stato testato/confermato meccanicamente.

**Domanda:** Qual è il secondo giorno (diverso dal mercoledì) in cui T16 è libera dalle 11:00 per gli adulti — se esiste davvero un secondo giorno fisso?

**Stato:** aperta — regola parziale (solo martedì) già in `availability_rules.csv` (TASK-374). Dettaglio della misurazione in `availability-not-yet-modeled.md` §4.

---

Grazie mille per il tempo, fammi sapere se preferisci rispondere per iscritto qui sotto o discuterne a voce — nel secondo caso aggiorno io il documento con quello che ci diciamo.

---

## Risposte ricevute (archivio)

### Q10 — T11: quale giorno "dalle 8.00"?

**Contesto:** Il vincolo di T11 è "9-14 sempre e un giorno alle 8.00": in generale lavora solo 9:00-14:00, ma un giorno della settimana (non specificato) è disponibile anche dalle 8:00.

**Domanda:** Quale giorno della settimana T11 è disponibile dalle 8:00 invece che dalle 9:00?

**Risposta (docx "Vincoli Spalmone in parole", 17/07):** un giorno **a scelta** può iniziare alle 08:00 — la scelta è libera (la fa il motore). Modellazione in TASK-368.

### Q15 — T05: il "giorno leggero" (max 1 ora) tra lun/mer/gio è fisso o a scelta della scuola?

**Contesto:** "No lezione in uno di questi giorni AL MASSIMO UN'ORA" indica che uno dei tre giorni lun/mer/gio (8:00-14:00) dovrebbe restare "leggero" (al massimo 1 ora di lezione), ma non è specificato quale. Il nostro motore di orario non ha ancora un vincolo per "scegli tu quale dei tre giorni tenere leggero" (vedi availability-not-yet-modeled.md).

**Domanda:** Il giorno leggero di T05 è fisso (sempre lo stesso) o può cambiare di settimana in settimana a scelta del sistema?

**Risposta (docx "Vincoli Spalmone in parole", 17/07):** superata la lettura "giorno leggero": T05 lavora 8–14 lun–gio e **un giorno a scelta deve restare completamente libero**. La scelta è del motore. Modellazione in TASK-368.

### Q11 — T10: giovedì è libero tutto il giorno o solo la mattina?

**Contesto:** Il testo dice insieme "Libero il Giovedì mattina" (solo mattina) e "per LES e G14 9,00-17,00" (un intervallo che copre tutta la giornata scolastica, oltre le 16:30). Avevamo trascritto in modo conservativo l'intera giornata di giovedì come non disponibile.

**Domanda:** T10 è indisponibile TUTTO il giovedì, o solo la mattina (e da che ora a che ora esattamente)?

**Risposta (DERIVATA dal golden, 17/07 — da confermare al rientro di referente):** solo la mattina, slot 0-3 (8:00-12:00) — il vecchio blocco a giornata intera era troppo largo. Misurazione e dettaglio in `availability-not-yet-modeled.md` §6. Regola aggiornata in `availability_rules.csv` (TASK-374).

### Q12 — T16: quali 2 mattine libera dalle 11:00?

**Contesto:** "Libera 2 mattine dalle 11,00 per adulti" — due mattine a settimana T16 è impegnata altrove (corsi adulti) fino alle 11:00, poi disponibile per noi.

**Domanda:** Quali due giorni della settimana, e sono sempre gli stessi durante tutto l'anno o cambiano per periodo?

**Risposta (DERIVATA PARZIALMENTE dal golden, 17/07 — da confermare al rientro di referente):** martedì confermato (blocco slot 0-2). Il mercoledì mattina invece NON è scarico nel golden, quindi non è stato bloccato — **resta aperto quale sia la seconda mattina libera, vedi Q12-bis in cima al documento.** Misurazione e dettaglio in `availability-not-yet-modeled.md` §4. Regola parziale (solo martedì) in `availability_rules.csv` (TASK-374).

### Q13 — T12: equipe il lunedì o il martedì?

**Contesto:** Il foglio riporta due alternative per il giorno dell'equipe di T12: "Il martedì" e, in una riga successiva, "Oppure il lunedì dalle 10.00".

**Domanda:** Il giorno dell'equipe di T12 (mattina libera dalle 10:00) è il lunedì o il martedì? È fisso tutto l'anno?

**Risposta (DERIVATA dal golden, 17/07 — da confermare al rientro di referente):** l'ambiguità è stata aggirata bloccando ENTRAMBI i giorni, slot 0-1 (8:00-10:00) — il golden non ha lezioni di T12 in quella finestra su nessuno dei due giorni. Misurazione e dettaglio in `availability-not-yet-modeled.md` §5. Regola aggiunta in `availability_rules.csv` (TASK-374); se la scuola conferma quale sia il giorno reale, la regola dell'altro giorno può essere rimossa.

### Q14 — T13: cosa significa "Comportamento sociale lab due"?

**Contesto:** La riga di T13 nel foglio Vincoli Docenti riporta solo "Comportamento sociale lab due", senza giorno, orario, o indicazione del tipo di vincolo.

**Domanda:** È un vincolo di disponibilità (e se sì, quale), o è una nota su aula/materia che non c'entra con la disponibilità oraria?

**Risposta (DERIVATA, 17/07 — da confermare al rientro di referente):** è una nota su materia/aula, non un vincolo di disponibilità — negli incarichi T13 insegna proprio "Comportamento Sociale" a G14 (20h), quindi "lab due" sembra riferirsi all'aula/sezione di quel corso. Nessuna regola di disponibilità aggiunta.

### Q16 — G14: lunedì-mercoledì sì o no?

**Contesto:** I due documenti che ci avete condiviso si contraddicono su G14: lo Spalmone (orario definitivo) mostra G14 solo giovedì e venerdì, mentre il Calendario scolastico segna presenze G14 anche lunedì-mercoledì (171 giornate discordanti nel controllo incrociato automatico).

**Domanda:** Qual è il calendario giusto per G14 — solo gio+ven come nello Spalmone, o anche lun-mer come nel Calendario scolastico?

**Risposta (DERIVATA, 17/07 — da confermare al rientro di referente):** confermato solo gio+ven come nello Spalmone — il nostro seed (`GroupCalendarRule`/`DataGenerator.GROUP_CALENDAR_PATTERNS`) ha già G14 chiuso lun/mar/mer e aperto solo giovedì (0-4) e venerdì (0-2), coerente con lo Spalmone. Nessuna modifica necessaria.

### Conferma — pausa pranzo
La giornata è 8:00–14:00 + 14:30–16:30 (pausa 14:00–14:30). **Confermato** dal docx "Vincoli Spalmone in parole" (17/07), punto sui vincoli generali.

### Q1 — Il "doppio" esiste ancora nel 2026/27?

**Contesto:** Il nostro sistema ha un vincolo (H-D1) che gestisce la co-docenza ("doppio"): un secondo insegnante deve essere presente insieme al titolare durante certe ore di laboratorio. Questo vincolo è ancora configurato sui dati del vecchio anno scolastico. La griglia del nuovo Spalmone ("Orario in righe") però ha una sola cella per (classe, slot orario): non può rappresentare due docenti sulla stessa ora, quindi da lì non riusciamo a capire se il doppio è ancora praticato.

**Domanda:** Il doppio (co-docenza) esiste ancora per l'anno 2026/27? Se sì, su quali corsi/classi e con quali coppie di docenti?

**Risposta (referente, 17/07):** PER ORA NO — niente co-docenza nel 2026/27. H-D1 resta nel motore ma senza corsi doppio configurati.


### Q2 — Chi copre le ore di "Jory" in G12?

**Contesto:** Nella nota professionalizzanti di G12 compare l'elenco "T20/Jory/T19/T26", ma "Jory" non compare né nel foglio Docenti né da nessuna parte nell'orario effettivo. Il vecchio T28 (esterno, solo giovedì) risulta invece completamente sparito dai nuovi dati.

**Domanda:** Chi copre in pratica le ore attribuite a "Jory" nella nota di G12? È un refuso per un altro nome (es. uno dei tre elencati insieme: T20, T19, T26) o manca davvero un docente da inserire?

**Risposta (referente, 17/07):** "Jory" **è T01** (stesso docente, nome diverso sullo spalmone). Spiega anche le sue giornate da 8 ore il giovedì, come il vecchio T28 esterno-solo-giovedì.


### Q3 — Chi ha ragione tra Incarichi e Orario sulle ore FC DUALE ancora in disallineamento?

**Contesto:** Confrontando il foglio "Incarichi in riga" (ore assegnate ai docenti) con "Orario in righe" (ore effettivamente in griglia) e con il vostro stesso foglio "Classi" (che segna "A posto"/"Analizzare"), sono rimasti alcuni scostamenti concentrati su FC DUALE, che sembrano ancora in lavorazione da parte vostra:

- **Laboratorio Termoidraulico**: scambio T03 ↔ T22, 100h vs 135h.
- **Taglio Metalli**: 60h assegnate negli incarichi vs 20h effettivamente in orario.
- **T15** — Cad, Lavorazioni meccaniche, Controllo numerico: grossi scostamenti ore assegnate/ore in orario.
- **T13** — Orientamento G12: 10h assegnate ma mai comparse in orario.

**Domanda:** Per questi quattro casi, qual è la versione definitiva — le ore degli Incarichi o quelle effettivamente in Orario — o sono ancora aperti e in fase di sistemazione da parte vostra? Se sono ancora aperti, quando pensate di avere la versione consolidata?

**Risposta (referente, 17/07):** **ha ragione "Orario in righe"** — "Incarichi in riga" non è aggiornato. Le ore corsi dei casi elencati vanno corrette dal conteggio della griglia (TASK-371).


### Q4 — A quali aule sono vincolati i corsi che nella griglia non hanno aula assegnata?

**Contesto:** Il foglio "Elenchi" elenca 16 aule, ma R17 e R18 risultano con capienza 0. Inoltre l'orario fatto a mano ("Orario in righe") non assegna nessuna aula: ogni cella è solo (classe, slot), senza indicazione del locale usato.

**Domanda:** Nella pratica quotidiana, quali corsi/materie sono vincolati a quali aule specifiche (es. laboratori, officine, aula di saldatura)? E qual è la capienza reale di R17 e R18 (visto che 0 non può essere il valore vero)?

**Risposta (referente, 17/07):** R17 e R18 non vanno usate; per ora **lasciar perdere le aule** in generale (tema deprioritizzato).


### Q5 — Chi è il docente indicato come "??????" nei Vincoli Docenti?

**Contesto:** Nel foglio "Vincoli Docenti" (disponibilità/vincoli orari in testo libero per ciascun insegnante) una riga riporta come nome del docente letteralmente "??????", senza indicazione di chi sia.

**Domanda:** A quale docente si riferisce quella riga e quali sono i suoi vincoli di disponibilità reali?

**Risposta (referente, 17/07):** superata — i vincoli validi sono quelli del documento Google "Vincoli Spalmone in parole" (recepito il 17/07, vedi vincoli-in-parole.md).


### Q6 — Le giornate d'esame oltre la fine dell'anno rientrano nella pianificazione?

**Contesto:** L'anno scolastico nello Spalmone va dal 2026-09-14 al 2027-06-10, ma i gruppi FC hanno giornate d'esame fino al 2027-06-15 (nel foglio Incarichi, il docente pseudo "Esame" ha 24h assegnate per le terze/G08, non presenti nella griglia oraria).

**Domanda:** Le giornate d'esame oltre la fine dell'anno scolastico (11–15 giugno 2027 per FC) devono rientrare nel perimetro che pianifichiamo con il motore di orario, o sono gestite separatamente da voi (fuori dal nostro sistema)?

**Risposta (referente, 17/07):** gli esami NON sono pianificati da noi (fuori perimetro). Restano solo come voce contabile.


### Q7 — G04 (DUALE): che pattern segue lo stage/apprendistato?

**Contesto:** G04 ha 224 ore di stage sparse su tutti e cinque i giorni della settimana tra aprile e giugno, senza finestra stage in "Vincoli Classi" e senza riga Stage negli incarichi. Sul fronte ore, la discrepanza 1040 vs 990 ("Totale ore corso") ci risulta ora spiegata: negli incarichi di G04 compaiono SIA la riga reale `T15, Recuperi ed Approfondimenti, 50h` SIA la riga pool `RA, Recuperi ed Approfondimenti, 50h` — stesse 50 ore contate due volte (990 + 50 = 1040). La griglia lo conferma: tutte le 50 ore di Recuperi di G04 sono tenute da T15.

**Domanda:** Il pattern dei giorni di apprendistato è a giorni fissi, a settimane alternate, o davvero irregolare per studente? E confermate la nostra lettura del 1040 vs 990 — cioè che la riga RA di G04 è un duplicato del corso reale di T15 (stesse 50 ore) e non un pool aggiuntivo da coprire?

**Risposta (referente, 17/07):** apprendistato a **GIORNI FISSI**; per il resto "lo stage va bene così". Sul 1040 vs 990: alla domanda secca ha risposto "No" (non un doppione), ma combinato con la risposta Q3 ("gli Incarichi non sono aggiornati") l'effetto operativo è lo stesso: per G04 esistono solo le 50h di Recuperi di T15 presenti in griglia; la riga pool RA è incarichi stantio.


### Q8 — I delta stage sono attesi?

**Contesto:** Per G08 lo stage ha 300 ore assegnate ma 276 in griglia; scostamenti simili per altri gruppi.

**Domanda:** Questi delta stage sono attesi/accettati (quindi da marcare "A posto" nel nostro pannello di quadratura)?

**Risposta (referente, 17/07):** "in griglia sono 300" — e ha ragione: 300 righe Stage per G08, di cui **24 nei giorni di chiusura** (vacanze di Pasqua) che il nostro conteggio scartava. Conferma la regola "lo stage può cadere nei giorni di chiusura" del docx. Correzione del nostro accounting in TASK-373; nessun delta reale.


### Q9 — RA di 1/G08: pool libero o da assegnare?

**Contesto:** Le ore RA (Recuperi e Approfondimenti) di G04 (50h) e G08 (40h) sono coperte da molti docenti diversi senza assegnazione fissa negli incarichi.

**Domanda:** Il pianificatore deve distribuirle a docenti specifici, o restano un pool che il coordinatore riempie a mano di volta in volta?

**Risposta (referente, 17/07):** vanno bene così — il pool RA resta riempito a mano dal coordinatore, il motore non lo distribuisce.


### Q17 — Pomeriggi: solo stage o anche lezioni?

**Contesto:** Nel documento "Vincoli Spalmone in parole" scrivete che normalmente la lezione è solo fino alle 14 e che lo stage può arrivare alle 16:30. Però nello Spalmone definitivo ci sono 575 ore di lezione vera (non stage) negli slot 14:30–16:30, su 9 classi (es. T18 con G06 il mercoledì pomeriggio).

**Domanda:** Le lezioni pomeridiane vanno evitate quando possibile (preferenza) o erano una necessità di quest'anno da non ripetere? Il motore deve poterle usare liberamente o solo come ultima risorsa?

**Risposta (referente, 17/07, via conferma C1):** i pomeriggi (14:30–16:30) **vanno usati solo per lo STAGE**. Le 575 lezioni pomeridiane di quest'anno sono da non ripetere: per il motore i pomeriggi sono off-limits alle lezioni normali (TASK-372).


### Q18 — "Mai 2 ore vicine della stessa materia": quanto è rigida?

**Contesto:** La regola (con eccezione per Informatica, Competenze Digitali e Disegno) equivale a "massimo 1 ora al giorno per materia" fuori dalla whitelist. Ma lo Spalmone fatto a mano contiene 1.299 blocchi multi-ora, molti fuori dalla whitelist (Confezione 2-4h, Lab. Termoidraulico 5h, Scienze 2h, ecc.). Anche la regola "stesso docente mai due ore attaccate" è contraddetta dall'orario reale.

**Domanda:** Queste regole valgono solo per le materie "d'aula" teoriche (e i laboratori/professionalizzanti sono TUTTI eccezioni, non solo i 3 elencati)? Sono desiderata per il nuovo orario generato dalla macchina, o vincoli veri? Con che priorità rispetto a "finire le ore nel monte ore"?

**Risposta (referente, 17/07):** "i LABORATORI, Informatica e Disegno possono avere due ore vicine, gli altri **possibilmente** no" — quindi whitelist = tutti i laboratori + Informatica + Disegno (+ Competenze Digitali dal docx), e la regola è SOFT ("possibilmente"). Calibrazione in TASK-365/349.

## Conferme rapide — tutte confermate (17/07)

1. Slot pomeridiani 14:30–15:30 e 15:30–16:30: **SÌ, ma usati solo per lo stage** (→ Q17).
2. Il Calendario scolastico è il file preparato dal coordinatore e riempito dai docenti di laboratorio: **SÌ, ma "va riorganizzato"** (nuova versione in arrivo, TASK-364).
3. Convenzione segni MODA TUTTI (negativo = primo turno): **SÌ**.
4. Le ore di Stage contano nel monte ore annuale: **SÌ**.
5. Primo giorno (14/09) dalle 9:00: **SÌ**.
6. Pausa pranzo 14:00–14:30: **SÌ** (già in archivio dal docx).
