# Proposta: workbook Excel di input per un nuovo anno scolastico (2026-07-28)

Direttiva maintainer: progettare i file Excel con cui la scuola immette i dati di un anno
nuovo. Base di partenza: gli input che lo human planner ha gia' prodotto spontaneamente
(Spalmone, Calendario scolastico, Vincoli docenti, Elenchi), riorganizzati secondo le
informazioni che il sistema realmente consuma.

Riferimenti letti: `claudedocs/audit-genericita-multi-anno-2026-07-28.md` (mappa dei
dati oggi hardcoded), `src/main/resources/seed/*.csv` (gli input attuali),
`claudedocs/spalmone-2026-27/seed-csv-format.md` (schemi e provenienza), `CLAUDE.md`
(chi consuma cosa), piu' verifica diretta dei letterali nel codice
(`DataGenerator`, `TimeslotSequence`, `SchoolScheduleConstraintProvider`,
`BaseConstraintProvider`, `DailyActivityQuota`, `SubjectNormalization`).

Perimetro: questa e' una proposta di FORMATO DI INPUT, non un piano di implementazione.
Copre ogni voce dell'audit che rappresenta un dato di dominio (cluster C1, C2, C3, C5,
parte di C6 e C7). Non copre i pesi di calibrazione del solver, che restano
legittimamente in codice (vedi sezione 7).

---

## 1. Principi

1. **Una riga = un fatto.** Nessuna cella che contiene due informazioni, nessun blocco
   di colonne ripetuto per gruppo, nessuna intestazione a due livelli. Il foglio
   "MODA TUTTI" attuale (blocchi PE/MU/BO affiancati) e' l'anti-esempio: tre gruppi in
   orizzontale, valori negativi che codificano il turno.
2. **Nomi canonici in una colonna, grafie alternative in un foglio a parte.** Oggi
   "Lingua italiana" e "Lingua Italiana" convivono nello stesso file e il sistema le
   riconcilia con una mappa Java (`SubjectNormalization`). La mappa diventa un foglio.
3. **Pattern piu' eccezioni esplicite, mai diluizione del pattern.** Coerente con il
   principio pattern-exhaustion di `CLAUDE.md`: il piano-giorni si dichiara come pattern
   settimanale per periodo, e le rotture si scrivono una per una con motivo. Il per-data
   completo (oggi 2.413 + 909 righe scritte a mano) viene GENERATO dal sistema e
   restituito al planner per revisione, non digitato.
4. **Validazione all'inserimento, non al solve.** Ogni incoerenza che oggi emerge come
   "hard non si azzera" o come discrepanza in un report di import deve essere una cella
   rossa nel workbook, con messaggio in italiano. Il caso G14 (990 attese contro 115
   assegnate) e il caso G04 (stage nel piano senza riga di monte-ore) sono i due casi
   di prova.
5. **L'Excel resta lo strumento dello human planner.** Tabelle piatte, filtro
   automatico, convalida dati a tendina, formule leggibili. Niente macro obbligatorie,
   niente tabelle pivot come formato di consegna, niente form che richiedano training.
   Chi oggi compila lo Spalmone deve poter compilare questo senza corso.
6. **Concisione: si dichiara il fatto, non il suo complemento.** Nessun foglio puo'
   obbligare a stendere un calendario per-data per esprimere una regola che in italiano
   sta in tre parole. Vale per la disponibilita' dei docenti ("solo giovedi'", oggi
   scritta come quattro righe di chiusura sugli altri giorni) e vale per il
   piano-giorni. Dove serve un token testuale per rendere la compilazione naturale, il
   token e' zucchero: la struttura accanto resta leggibile dalla macchina e prevale.
7. **Il sistema precompila tutto cio' che sa.** Un anno nuovo parte da un workbook gia'
   pieno con quanto e' derivabile dall'anno precedente e dai dati anagrafici, in colore
   diverso, con la colonna `origine` che dice se il valore e' confermato dalla scuola o
   proposto dal sistema.

---

## 2. Struttura: un workbook, fogli normalizzati

Un solo file, `AnnoScolastico_2027-28.xlsx`, con i fogli sotto. L'unica eccezione
consigliata e' il foglio generato `15_PianoGiorniGenerato`, che per dimensione
(circa 2.400 righe) puo' essere consegnato come file affiancato
`PianoGiorni_2027-28.xlsx`, prodotto dal sistema e rimandato indietro per revisione.

| # | Foglio | Scopo (una riga) | Righe attese 2026/27 |
|---|--------|------------------|----------------------|
| 00 | `00_Istruzioni` | Legenda, convenzioni, changelog di compilazione | testo |
| 01 | `01_Anno` | Parametri dell'anno scolastico (chiave/valore) | ~10 |
| 02 | `02_Campanella` | Schema slot orari della giornata | 8 |
| 03 | `03_Chiusure` | Date di chiusura della scuola | 29 |
| 04 | `04_GiorniSpeciali` | Giorni con orario ridotto o cerimoniale | 13 |
| 05 | `05_Docenti` | Anagrafica docenti e cap personali | 26 |
| 06 | `06_DocentiDisponibilita` | Regole ricorrenti concise, una riga per regola | ~12 |
| 07 | `07_DocentiEccezioni` | Eccezioni datate, foglio opzionale | 0-N |
| 08 | `08_Materie` | Anagrafica materie e loro proprieta' | ~90 |
| 09 | `09_MaterieAlias` | Grafie alternative verso nome canonico | ~10 |
| 10 | `10_Classi` | Anagrafica classi e loro eccezioni | 14 |
| 11 | `11_ClassiCalendario` | Finestre di apertura per classe e giorno | 70 |
| 12 | `12_Incarichi` | Docente x materia x classe x ore | 266 |
| 13 | `13_PianoGiorniPattern` | Pattern settimanale del piano-giorni per classe | ~120 |
| 14 | `14_PianoGiorniEccezioni` | Rotture del pattern, per data | ~150 |
| 15 | `15_PianoGiorniGenerato` | Espansione per-data (generata, rivedibile) | ~2.400 |
| 16 | `16_Stage` | Finestre di stage per classe | 8 |
| 17 | `17_Aule` | Aule, capienza, tag | 18 |
| 18 | `18_RotazioniLab` | Rotazioni di laboratorio da pre-assegnare | 0-N |
| 19 | `19_Doppio` | Co-docenza e assistenti di laboratorio | 0-N |
| 20 | `20_GiorniProfessionalizzanti` | Giorni riservati alle professionalizzanti | 2 |
| 21 | `21_CadenzaAttesa` | Cadenza settimanale attesa (opzionale, vedi gap G1) | 0-N |
| 99 | `99_Validazione` | Riconciliazione e controlli, con esito per riga | ~20 blocchi |

### Convenzioni di formato (valgono per tutti i fogli)

| Regola | Dettaglio |
|--------|-----------|
| Riga 1 | intestazioni esatte come in questa proposta, mai tradotte o rinominate |
| Date | ISO `AAAA-MM-GG`, formato testo o data, mai formato locale ambiguo |
| Booleani | `SI` / `NO`, mai `X`, mai celle colorate come dato |
| Liste | valori separati da `;` senza spazi (es. `informatica;meccanica`) |
| Vuoto | cella vuota = "non applicabile" o "default"; mai `0` per dire "non so" |
| Virgole | vietate dentro i campi (il parser dei CSV di seed non gestisce le virgolette) |
| Celle unite | vietate |
| Colonna `origine` | `scuola` (confermato) oppure `sistema` (proposto, da confermare) |
| Colonna `note` | testo libero, ignorata dal sistema, sempre presente |

---

## 3. Fogli in dettaglio

### 3.1 `01_Anno` - parametri dell'anno

Scopo: definire l'anno scolastico una volta sola, al posto delle costanti di
`TimeslotSequence` e dei letterali di data sparsi.

Forma chiave/valore (due colonne piu' note), perche' sono fatti singoli e non una
tabella.

| chiave | tipo | obblig. | esempio 2026/27 | note di validazione |
|--------|------|---------|-----------------|---------------------|
| `annoEtichetta` | testo | si | `2026/27` | usato nei nomi di file e nei report |
| `dataInizio` | data | si | `2026-09-14` | deve essere un giorno feriale non in `03_Chiusure` |
| `dataFine` | data | si | `2027-06-10` | maggiore di `dataInizio` |
| `giorniSettimana` | lista | si | `LUN;MAR;MER;GIO;VEN` | sottoinsieme di LUN..DOM |
| `settimaneDidattiche` | intero | no | `38` | se vuoto, calcolato dalle date meno le chiusure |
| `oreAnnueStandard` | decimale | no | `990` | valore di riferimento per il controllo V1 |
| `dataEsamiDa` / `dataEsamiA` | data | no | `2027-06-11` / `2027-06-15` | date fuori griglia: nessuno slot generato, ore contabilizzate come fuori griglia |

Consumatori: `TimeslotSequence.SCHOOL_YEAR_START`/`SCHOOL_YEAR_END` (oggi letterali),
generazione dei `Timeslot`, `TargetEndDateCalculator` (oggi con `orElse` di date
stantie, bug 2 dell'audit), `DEFAULT_WEEKS`, i 21 siti che assumono Lun-Ven, M12
(overflow oltre la data ideale). Cluster C2 dell'audit.

### 3.2 `02_Campanella` - schema degli slot

Scopo: dire quante ore ha la giornata, a che ora iniziano e dove cade la pausa pranzo.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `slot` | intero | si | `0` | progressivo da 0, senza buchi |
| `inizio` | ora `HH:MM` | si | `08:00` | crescente rispetto alla riga precedente |
| `fine` | ora `HH:MM` | si | `09:00` | maggiore di `inizio` |
| `fascia` | enum | si | `mattino` | `mattino` o `pomeriggio` |
| `note` | testo | no | `dopo la pausa pranzo` | |

Esempio (2026/27, 8 slot):

| slot | inizio | fine | fascia |
|------|--------|------|--------|
| 0 | 08:00 | 09:00 | mattino |
| 5 | 13:00 | 14:00 | mattino |
| 6 | 14:30 | 15:30 | pomeriggio |

Consumatori: `TimeslotSequence.SLOT_START_TIMES`, `SLOTS_PER_DAY`,
`AFTERNOON_START_SLOT_INDEX` (soglia di M28, oggi il letterale 6),
`LUNCH_BREAK_START`/`END` (semantica di adiacenza usata da M15, H17, S1, S7, S8).
La prima riga con `fascia = pomeriggio` deriva la soglia pomeridiana invece di
codificarla: risolve il bug 4 dell'audit.

Nota di realismo: il cluster C7 dell'audit classifica la parametrizzazione dello schema
slot come rischio ALTO a campagna aperta. Il foglio va comunque chiesto e conservato
subito (costa nulla e documenta l'anno); il consumo pieno lato codice si abilita a
model-freeze. Vedi gap G4.

### 3.3 `03_Chiusure` - date di chiusura

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `data` | data | si | `2026-12-24` | dentro `[dataInizio, dataFine]`, altrimenti avviso "inerte" |
| `descrizione` | testo | si | `Vacanze di Natale` | |
| `tipo` | enum | no | `vacanza` | `festivo`, `vacanza`, `ponte`, `chiusura` |
| `origine` | enum | si | `scuola` | |

Esempi reali 2026/27: `2026-12-08 / Immacolata Concezione / festivo`,
`2027-02-08 / Carnevale / vacanza`, `2027-01-22 / Chiusura confermata dai fogli /
chiusura`.

Consumatori: `SchoolHoliday` (vincolo H6) ed esclusione delle date dalla generazione
dei `Timeslot`. Oggi entrambi da `DataGenerator.CLOSURES_2026_27`, trascritte a mano;
il TASK-408 ha corretto un typo (2026-01-22 al posto di 2027-01-22) rimasto inerte per
settimane, esattamente il tipo di errore che il controllo "data fuori anno" del foglio
99 intercetta all'inserimento.

### 3.4 `04_GiorniSpeciali` - giorni a orario ridotto o cerimoniale

Scopo: le date in cui l'orario non e' quello normale della classe (primo giorno,
cerimonie, assemblee, uscite, prove di evacuazione).

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `data` | data | si | `2026-09-14` | dentro l'anno, non in `03_Chiusure` |
| `classe` | testo | si | `TUTTE` | `TUTTE` oppure un nome di `10_Classi` |
| `slotMin` | intero | si | `1` | dentro `02_Campanella` |
| `slotMax` | intero | si | `4` | maggiore o uguale a `slotMin` |
| `motivo` | testo | si | `Primo giorno, ingresso posticipato` | |

Esempio reale: il primo giorno 2026-09-14 tutte le classi (tranne G14, chiusa il
lunedi') stanno in 09:00-13:00, cioe' slot 1-4. Oggi questa regola vive come commento
piu' codice dedicato dentro `generateGroupCalendarRules`.

Consumatori: `GroupCalendarRule` con ambito di data (risoluzione "piu' specifico
vince"), quindi H20 e il filtro VRP degli slot disponibili.

### 3.5 `05_Docenti` - anagrafica e cap personali

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T01` | chiave usata da tutti gli altri fogli, univoca |
| `nomeCompleto` | testo | si | `T01` | solo visualizzazione |
| `email` | testo | no | `t01@example.org` | |
| `sigla` | testo | no | `PE` | univoca; usata dai fogli calendario a iniziali |
| `esterno` | SI/NO | si | `NO` | se `SI`, deve avere righe in `06` che chiudano i giorni non previsti |
| `maxOreGiorno` | intero | no | `8` | vuoto = default 6 |
| `note` | testo | no | `Laboratorio a giornata intera` | |

Esempi reali: `T01 / T01 / sigla vuota / esterno SI / maxOreGiorno 8`
(laboratorio a giornata intera che scavalca la pausa pranzo), `T17 / sigla PE`.

Divisione del lavoro con il foglio `06`: qui stanno gli attributi COSTANTI del docente
(chi e', quanto puo' fare in un giorno), in `06` stanno le regole su QUANDO lavora,
comprese quelle di conteggio settimanale. Per questo `maxGiorniInizioPresto` e
`giorniLiberiRichiesti`, che nel formato CSV attuale sono colonne di `teachers.csv`,
qui diventano righe-regola di `06`: sono fatti sulla settimana, non attributi
anagrafici, e tenerli in due posti violerebbe il principio "una riga uguale un fatto".

Consumatori: entita' `Teacher`; `maxOreGiorno` alimenta H7 (e rende finalmente
superflua la regola legacy hardcoded H11-T28, cluster C4 dell'audit); `sigla`
sostituisce la mappa `CalendarioImportService.INITIAL_TO_TEACHER` (oggi PE, MU, BO
codificate in Java); `esterno` e' la bandierina che obbliga il foglio `06` ad avere
almeno una regola per quel docente (controllo V23).

Nota importante: nel formato nuovo **spariscono gli pseudo-docenti**. Le righe
`Stage`, `Esame`, `RA`, `Totale ore corso` che oggi stanno mescolate nell'anagrafica
incarichi non sono docenti: lo stage va in `16_Stage`, esame e pool RA diventano
incarichi con `tipo` esplicito, il totale della classe va in `10_Classi`.

### 3.6 Disponibilita' docenti: modello a due livelli

Requisito esplicito (maintainer, 2026-07-28): la compilazione deve poter essere CONCISA. Chi
compila non deve mai essere obbligato a stendere un calendario per-data di tutto l'anno
per dire una cosa che in italiano si dice in tre parole.

Il modello ha due livelli, che rispecchiano uno a uno le entita' gia' esistenti nel
dominio:

| Livello | Foglio | Entita' di destinazione | Obbligatorio |
|---------|--------|-------------------------|--------------|
| 1. Regole ricorrenti | `06_DocentiDisponibilita` | `RecurringAvailabilityRule` piu' i campi di conteggio settimanale di `Teacher` | si, almeno per i docenti con vincoli |
| 2. Eccezioni datate | `07_DocentiEccezioni` | `TeacherAvailability` (righe datate) | no, solo per i casi rari |

Il livello 1 e' il caso normale e sta su una riga per regola. Il livello 2 esiste per
la visita medica del 16 novembre, non per descrivere l'anno.

Il caso reale che motiva tutto: **T01 (T28) insegna solo il giovedi'**. Oggi
quella frase e' scritta nel sistema come QUATTRO righe di indisponibilita' a giornata
intera (lunedi', martedi', mercoledi', venerdi'), cioe' si dichiara il complemento
invece del fatto. Stessa goffaggine per T24 (tre righe per dire "solo lunedi' e
martedi'") e per T12 (due righe per dire "mai mercoledi' e giovedi'"). Nel formato
nuovo T01 e' una riga sola.

#### 3.6.1 `06_DocentiDisponibilita` - una riga per regola

La colonna `regola` e' il token testuale in italiano, cioe' zucchero per chi compila.
Le colonne che seguono sono la forma strutturata, ed e' quella che il sistema legge.
Chi vuole scrive solo il token e lascia che il sistema riempia il resto (colonne
proposte con `origine = sistema`); chi vuole compila direttamente la struttura e lascia
vuoto il token. Se compila entrambi e non coincidono, e' un errore bloccante (V21): il
token non puo' mai divergere in silenzio dalla struttura.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T01` | deve esistere in `05` |
| `regola` | testo | no | `solo giovedi` | deve corrispondere alla legenda; se presente deve coincidere con le colonne strutturate |
| `tipo` | enum | si | `SOLO_GIORNI` | vocabolario chiuso, vedi legenda |
| `giorni` | lista | dipende dal tipo | `GIO` | sottoinsieme di `giorniSettimana` di `01_Anno` |
| `fascia` | enum | no | `giornata` | `mattino`, `pomeriggio`, `giornata`; ricavata da `02_Campanella` |
| `slotMin` / `slotMax` | intero | no | | se presenti vincono su `fascia`; dentro `02_Campanella` |
| `valore` | intero | dipende dal tipo | | obbligatorio per i tipi di conteggio, vietato per gli altri |
| `forza` | enum | si | `VINCOLO` | `VINCOLO` (hard) o `PREFERENZA` (soft) |
| `validoDa` / `validoA` | data | no | vuoto | vuoto = tutto l'anno |
| `note` | testo | no | `Docente esterna` | vietata la virgola |

#### 3.6.2 Legenda, da riportare dentro il foglio stesso

La legenda va scritta nel foglio (blocco di righe sotto la tabella, oppure foglio
`00_Istruzioni` richiamato da un commento di cella), perche' un vocabolario che vive
solo in un documento tecnico non viene letto da chi compila.

| Come si dice | `tipo` | `giorni` | `fascia` / slot | `valore` | Significato |
|--------------|--------|----------|-----------------|----------|-------------|
| `solo giovedi` | `SOLO_GIORNI` | `GIO` | | | disponibile SOLO nei giorni elencati, chiuso in tutti gli altri |
| `mai lunedi` | `MAI_GIORNI` | `LUN` | | | chiuso nei giorni elencati, invariato negli altri |
| `solo pomeriggio` | `SOLO_FASCIA` | vuoto = tutti | `pomeriggio` | | nei giorni indicati e' disponibile solo in quella fascia |
| `lun-mer mattina` | `SOLO_FASCIA` | `LUN;MAR;MER` | `mattino` | | forma combinata: giorni piu' fascia |
| `mar 9-13` | `FINESTRA` | `MAR` | `slotMin 1`, `slotMax 4` | | disponibile solo dentro quella finestra, in quei giorni |
| `max 2 giorni/settimana` | `MAX_GIORNI_SETTIMANA` | | | `2` | al massimo N giorni con lezione per settimana |
| `almeno 1 giorno libero/settimana` | `MIN_GIORNI_LIBERI` | | | `1` | almeno N giorni senza alcuna lezione per settimana |
| `max 1 giorno con inizio presto/settimana` | `MAX_INIZI_PRESTO` | | | `1` | al massimo N giorni per settimana con lezione al primo slot |
| `preferisce il martedi mattina libero` | `SOLO_FASCIA` o `MAI_GIORNI` con `forza = PREFERENZA` | `MAR` | `mattino` | | stessa struttura, tier soft |

Tre note di semantica, tutte necessarie perche' il vocabolario resti privo di
ambiguita':

- `SOLO_GIORNI` e `MAI_GIORNI` sono complementari e non vanno mai messi insieme per lo
  stesso docente: la combinazione e' un errore (V22), perche' produce quasi sempre una
  disponibilita' vuota per distrazione.
- `MAX_GIORNI_SETTIMANA` e `MIN_GIORNI_LIBERI` sono la stessa famiglia vista dai due
  lati: con cinque giorni settimanali, `MAX_GIORNI_SETTIMANA = 2` equivale a
  `MIN_GIORNI_LIBERI = 3`. Il sistema normalizza al campo `Teacher.requiredFreeDaysPerWeek`
  che gia' esiste e che alimenta M25, e riporta la conversione nel report di import.
  Il planner scrive la forma che gli viene naturale.
- `MAX_INIZI_PRESTO` mappa sul campo `Teacher.maxEarlyStartDaysPerWeek` gia' esistente,
  che alimenta M24. "Inizio presto" significa lezione al primo slot di `02_Campanella`,
  non un orario fisso: se la campanella cambia, la regola non va riscritta.

#### 3.6.3 Esempi reali 2026/27, scritti nel formato nuovo

| docente | regola | tipo | giorni | fascia | valore | forza |
|---------|--------|------|--------|--------|--------|-------|
| `T01` | `solo giovedi` | `SOLO_GIORNI` | `GIO` | `giornata` | | VINCOLO |
| `T24` | `solo lunedi e martedi` | `SOLO_GIORNI` | `LUN;MAR` | `giornata` | | VINCOLO |
| `T12` | `mai mercoledi e giovedi` | `MAI_GIORNI` | `MER;GIO` | `giornata` | | VINCOLO |
| `T04` | `mai venerdi` | `MAI_GIORNI` | `VEN` | `giornata` | | VINCOLO |
| `T04` | `lun-gio mattina` | `SOLO_FASCIA` | `LUN;MAR;MER;GIO` | `mattino` | | VINCOLO |
| `T05` | `almeno 2 giorni liberi/settimana` | `MIN_GIORNI_LIBERI` | | | `2` | VINCOLO |
| `T11` | `max 1 giorno con inizio presto/settimana` | `MAX_INIZI_PRESTO` | | | `1` | VINCOLO |
| `T24` | `preferisce martedi mattina libero` | `SOLO_FASCIA` | `MAR` | `pomeriggio` | | PREFERENZA |

Conteggio: le 23 righe di `availability_rules.csv` piu' le due colonne di conteggio in
`teachers.csv` diventano circa 12 righe. T01 passa da 4 righe a 1, T24 da 4 a 2,
T04 da 5 a 2, T11 da 5 a 1.

#### 3.6.4 Espansione lato sistema

Il sistema espande le regole concise nelle entita' che gia' esistono, senza toccare il
modello:

| `tipo` | Espansione |
|--------|------------|
| `SOLO_GIORNI` | una `RecurringAvailabilityRule` VACATION a giornata intera per ogni giorno NON elencato |
| `MAI_GIORNI` | una `RecurringAvailabilityRule` VACATION a giornata intera per ogni giorno elencato |
| `SOLO_FASCIA` | una `RecurringAvailabilityRule` VACATION sugli slot fuori fascia, per ogni giorno indicato |
| `FINESTRA` | una `RecurringAvailabilityRule` VACATION sugli slot fuori finestra, per ogni giorno indicato |
| `MIN_GIORNI_LIBERI` / `MAX_GIORNI_SETTIMANA` | campo `Teacher.requiredFreeDaysPerWeek` |
| `MAX_INIZI_PRESTO` | campo `Teacher.maxEarlyStartDaysPerWeek` |
| `forza = PREFERENZA` | stesse espansioni con tipo `PREFERENCE` invece di `VACATION` |

Le `RecurringAvailabilityRule` sono poi espanse da `AvailabilityExpansionService` in
righe datate `TeacherAvailability`, lette da H5 (hard) e S3 (preferenza). Il report di
import deve stampare l'espansione ottenuta, riga di workbook per riga di workbook: chi
ha scritto `solo giovedi` deve poter verificare che il sistema ha capito "chiuso
lunedi', martedi', mercoledi', venerdi'".

### 3.7 `07_DocentiEccezioni` - livello 2, opzionale

Per i casi rari e datati: visita medica, formazione, permesso, accompagnamento a una
uscita. Il foglio puo' restare completamente vuoto.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T12` | deve esistere in `05` |
| `dataDa` | data | si | `2026-11-16` | dentro l'anno |
| `dataA` | data | no | vuoto | vuoto = stesso giorno di `dataDa`; permette un intervallo senza una riga per data |
| `slotMin` / `slotMax` | intero | no | `2` / `7` | vuoti = giornata intera |
| `tipo` | enum | si | `ASSENTE` | `ASSENTE` (hard), `PREFERISCE_LIBERO` (soft), `DISPONIBILE_EXTRA` (deroga a una regola di `06`) |
| `motivo` | testo | si | `Visita medica` | obbligatorio: un'eccezione senza motivo e' un refuso |

Esempi reali: `T12 / 2026-09-14 / / 2 / 7 / ASSENTE / Visita medica`, tre righe
analoghe a novembre; nel formato attuale sono gia' tre righe distinte, qui restano tre
righe perche' sono tre date sparse, ma una settimana di formazione diventa una riga
sola con `dataDa` e `dataA`.

Chi preferisce consegnare un calendario pieno (una riga per ogni data dell'anno) e'
accettato: il sistema lo importa senza protestare e propone la compattazione in regole
di `06`, mostrando quali righe sarebbero coperte dal pattern. Ma non gli viene mai
chiesto.

Il tipo `DISPONIBILE_EXTRA` e' la valvola per il caso "quel giovedi' no ma quel lunedi'
si'", che senza di esso costringerebbe a spezzare una regola concisa in due periodi.
Vince sulle regole di `06` per quella data.

Consumatori: `TeacherAvailability` diretta (senza passare per l'espansione), quindi
H5 e S3.

### 3.8 `08_Materie` - anagrafica materie

Questo e' il foglio piu' importante della proposta: l'audit lo indica come blocco
numero uno, circa 48 prefissi di nomi italiani dentro `DataGenerator` piu' i set del
constraint provider. Oggi non esiste alcun file di input per le materie: esistono solo
come stringhe dentro le righe di incarico, e ogni loro proprieta' e' dedotta da un
prefisso in Java.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `materia` | testo | si | `Lavorazioni meccaniche` | nome canonico, univoco |
| `laboratorio` | SI/NO | si | `SI` | se `SI`, `tagAula` non puo' essere vuoto |
| `professionalizzante` | SI/NO | si | `SI` | |
| `tagAula` | lista | no | `meccanica` | ogni tag deve esistere in `17_Aule` |
| `blocco` | intero | no | `2` | ore consecutive per sessione; vuoto = 1 |
| `capOreGiorno` | intero | no | vuoto | vuoto = cap standard; `0` = nessun cap |
| `settimanaInizioMax` | intero | no | `3` | deve iniziare entro la settimana N |
| `settimanaInizioMin` | intero | no | `4` | non prima della settimana N |
| `primaDelloStage` | SI/NO | no | `SI` | tutte le ore prima dell'inizio stage della classe |
| `dopoLoStage` | SI/NO | no | `NO` | preferenza per collocazione post-stage |
| `origine` | enum | si | `scuola` | |
| `note` | testo | no | | |

Esempi reali (valori presi dal comportamento attuale del codice):

| materia | laboratorio | professionalizzante | tagAula | blocco | capOreGiorno | settimanaInizioMax | primaDelloStage |
|---------|-------------|---------------------|---------|--------|--------------|--------------------|-----------------|
| `Lingua Italiana` | NO | NO | | | | | NO |
| `Lavorazioni meccaniche` | SI | SI | `meccanica` | | | | NO |
| `Sicurezza` | NO | SI | | | | | SI |
| `Merceologia` | NO | SI | | | | 3 | NO |
| `Tecnologia tessile` | SI | SI | `moda` | | | | NO |
| `Competenze Digitali` | NO | NO | | | 0 | | NO |
| `Disegno Edile ed Impiantistico` | NO | SI | | 2 | 0 | | NO |

Consumatori: `Course.isLab`, `Course.requiredRoomTags`, `Course.professionalizzanti`,
`Course.labBlockSize` (che pilota `LessonGenerator` e M23); H-M2 e H-M2b (idoneita'
aula), S10, S11; `M21_EXEMPT_SUBJECTS` diventa `capOreGiorno = 0`; `SICUREZZA_SUBJECT`
di M26 diventa `primaDelloStage`; le finestre T20 di M27 (`T20_EARLY_SUBJECTS` con
settimana massima 3, `T20_LATE_SUBJECTS` con settimana minima 4) diventano
`settimanaInizioMax`/`settimanaInizioMin`; M5 (Informatica dopo lo stage) diventa
`dopoLoStage` incrociato con la classe; H4 e M29 usano `professionalizzante`.
Il foglio elimina in un colpo `getRoomTags` (17 prefissi), `isProfessionalizzante`
(31 prefissi), `isLabSubject`, `M21_EXEMPT_SUBJECTS`, `SICUREZZA_SUBJECT` e i due set
T20. Cluster C1 dell'audit.

Nota su `Disegno Edile ed Impiantistico`: il codice oggi lo classifica
professionalizzante ma NON lo tagga come laboratorio, mentre `CLAUDE.md` lo descrive
come lab a tag informatica. La discrepanza esiste da tempo ed e' esattamente cio' che
un foglio compilato dalla scuola risolve una volta per tutte.

### 3.9 `09_MaterieAlias` - grafie alternative

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `grafia` | testo | si | `Lingua italiana` | univoca, non deve coincidere con un canonico |
| `materia` | testo | si | `Lingua Italiana` | deve esistere in `08_Materie` |
| `origine` | enum | si | `sistema` | |

Esempi reali: `Lingua italiana` verso `Lingua Italiana`, `Educazione civica` verso
`Educazione Civica`, `RA` verso `Recuperi ed Approfondimenti`.

Consumatori: `SubjectNormalization`, usata da seeding, import golden, M21, M26 e dal
legame quota-laboratorio. L'alias `RA` merita una nota: nell'anno 2026/27 la griglia
golden usava l'abbreviazione `RA` mentre gli incarichi usavano il nome esteso, e 31
righe di lezione sono rimaste non abbinate proprio per questo. Con il foglio alias
quel caso diventa una riga di dati.

### 3.10 `10_Classi` - anagrafica classi ed eccezioni

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G04` | chiave univoca |
| `codiceCorso` | testo | no | `C04` | solo visualizzazione |
| `iscritti` | intero | si | `18` | usato per la capienza aula |
| `dataInizio` | data | no | `2026-09-14` | vuoto = inizio anno |
| `maxOreGiorno` | intero | si | `8` | coerente con le finestre di `11` |
| `oreAnnueAttese` | decimale | si | `990` | monte-ore dichiarato dalla scuola |
| `profDuranteStage` | SI/NO | si | `SI` | `NO` blocca le professionalizzanti in finestra stage |
| `pianoGiorniFine` | SI/NO | si | `SI` | `SI` = piano con separazione aula/lab/stage |
| `esenteRiempimento` | SI/NO | si | `NO` | classe esclusa dai vincoli di riempimento giornata |
| `tesinaDopoStage` | SI/NO | no | `NO` | preferenza Informatica post-stage |
| `note` | testo | no | | |

Esempi reali:

| classe | iscritti | maxOreGiorno | oreAnnueAttese | profDuranteStage | pianoGiorniFine | esenteRiempimento | tesinaDopoStage |
|--------|----------|--------------|----------------|------------------|-----------------|-------------------|-----------------|
| `G03` | 22 | 6 | 990 | SI | NO | NO | NO |
| `G08` | 20 | 8 | 990 | NO | SI | NO | NO |
| `G09` | 13 | 8 | 990 | NO | NO | NO | SI |
| `G14` | 12 | 5 | 120 | SI | NO | SI | NO |

Consumatori: entita' `StudentGroup`; `maxOreGiorno` alimenta H11,
`dataInizio` alimenta H14, `profDuranteStage` alimenta H4 (oggi il set hardcoded
`NO_PROFESSIONALIZZANTI_DURING_STAGE` con G08 e G09), `esenteRiempimento`
sostituisce la funzione `isG14` usata da M20 e M33, `pianoGiorniFine` sostituisce
`FC_QUOTA_GROUP_NAMES` (oggi G04, G08, G13) che decide se le righe del piano
hanno la separazione aula/lab/stage, `tesinaDopoStage` sostituisce `M5_TARGET_GROUPS`,
`oreAnnueAttese` sostituisce le righe pseudo-docente `Totale ore corso` e alimenta il
controllo V1. Cluster C3 dell'audit.

### 3.11 `11_ClassiCalendario` - finestre di apertura

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G12` | deve esistere in `10` |
| `giorno` | enum | si | `LUN` | una riga per (classe, giorno) al massimo |
| `aperto` | SI/NO | si | `SI` | se `NO`, slotMin e slotMax vuoti |
| `slotMin` | intero | se aperto | `0` | dentro `02_Campanella` |
| `slotMax` | intero | se aperto | `4` | maggiore o uguale a `slotMin` |

Esempi reali: `G12 / LUN / SI / 0 / 4`, `G12 / GIO / SI / 0 / 7`,
`G14 / LUN / NO`, `G14 / GIO / SI / 0 / 4`.

Consumatori: `GroupCalendarRule` (H20), il filtro VRP degli slot disponibili, il gate
di M18 e il perimetro di M22. Oggi la tabella `GROUP_CALENDAR_PATTERNS` in
`DataGenerator`, derivata meccanicamente dal golden dell'anno prima: in un anno senza
golden non ci sarebbe nulla da cui derivarla, e questo foglio e' l'unica risposta.

Discrepanza nota da chiudere con la scuola: per G14 il calendario di input dice
lunedi'-mercoledi', mentre il golden dice giovedi' e venerdi'. Sono due sorgenti che si
contraddicono. Con un foglio unico la contraddizione non e' piu' rappresentabile.

### 3.12 `12_Incarichi` - monte-ore per docente, materia, classe

E' il foglio piu' vicino a quello che la scuola gia' produce ("Incarichi in riga"), con
quattro colonne in piu'.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T01` | deve esistere in `05` |
| `materia` | testo | si | `Servizio tecnico e stilistico` | deve esistere in `08` o `09` |
| `classe` | testo | si | `G10` | deve esistere in `10` |
| `ore` | decimale | si | `65` | maggiore di zero |
| `tipo` | enum | si | `ordinario` | `ordinario`, `esame`, `pool` |
| `blocco` | intero | no | `6` | vuoto = valore di `08_Materie` |
| `legataQuotaLab` | SI/NO | no | `NO` | `SI` solo per classi con `pianoGiorniFine = SI` |
| `coDocente` | testo | no | | deve esistere in `05`; genera un vincolo di compresenza |
| `oreFuoriGriglia` | decimale | no | `0` | ore attese ma non collocabili sulla griglia |
| `note` | testo | no | | |

Esempi reali:

| docente | materia | classe | ore | tipo | legataQuotaLab |
|---------|---------|--------|-----|------|----------------|
| `T04` | `Lingua Italiana` | `G03` | 80 | ordinario | NO |
| `T01` | `Servizio tecnico e stilistico` | `G10` | 65 | ordinario | NO |
| `T15` | `Lavorazioni meccaniche` | `G04` | 120 | ordinario | SI |
| `-` | `Recuperi ed Approfondimenti` | `G08` | 40 | pool | NO |

Consumatori: entita' `Course` e generazione delle `Lesson` (`LessonGenerator`);
`legataQuotaLab` sostituisce la mappa `QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP` che oggi vive
in `DailyActivityQuota` e alimenta H23, M30, M31 e il filtro VRP; `coDocente` alimenta
la co-docenza (H-D1 e la famiglia M-LA); `oreFuoriGriglia` alimenta
`Course.expectedUnscheduledHours`, cioe' la contabilita' delle ore che cadono su sabati,
chiusure o settimana d'esame e che il solver non deve cercare di collocare.

La chiave (docente, materia, classe) deve essere univoca: oggi il codice somma le righe
duplicate con un warning, comportamento difensivo che nel workbook diventa un errore
visibile.

### 3.13 `13_PianoGiorniPattern` - il piano-giorni come pattern

Questa e' la riorganizzazione piu' sostanziale. Oggi la scuola compila due file per
data: `calendario_class_days.csv` con 2.413 righe (classe, data, ore, modalita') e
`calendario_fc.csv` con 909 righe (classe, data, ore aula, ore lab, ore stage). Sono
espansioni a mano di regole che nella testa del planner sono settimanali: "il primo
periodo il primo FC fa officina il martedi' e il giovedi', poi da gennaio cambia".

La proposta e' dichiarare il pattern e poi le sole rotture.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G04` | deve esistere in `10` |
| `validoDa` | data | si | `2026-09-14` | dentro l'anno |
| `validoA` | data | si | `2026-12-22` | maggiore o uguale a `validoDa` |
| `giorno` | enum | si | `MAR` | |
| `oreAula` | intero | si | `0` | |
| `oreLab` | intero | si | `8` | |
| `oreStage` | intero | si | `0` | |

Regole: i periodi di una stessa classe non possono sovrapporsi; la somma
`oreAula + oreLab + oreStage` non puo' superare l'ampiezza della finestra di `11` per
quel giorno; per una classe con `pianoGiorniFine = NO` si compila una sola colonna
diversa da zero (il sistema la interpreta come modalita' aula, laboratorio o stage,
esattamente come oggi fa la colonna `modalita` con i valori A, L, S).

Esempio realistico:

| classe | validoDa | validoA | giorno | oreAula | oreLab | oreStage |
|--------|----------|---------|--------|---------|--------|----------|
| `G04` | 2026-09-14 | 2026-12-22 | MAR | 0 | 8 | 0 |
| `G04` | 2026-09-14 | 2026-12-22 | MER | 8 | 0 | 0 |
| `G08` | 2027-01-12 | 2027-03-19 | LUN | 0 | 0 | 8 |
| `G01` | 2026-09-14 | 2027-06-10 | MAR | 6 | 0 | 0 |

### 3.14 `14_PianoGiorniEccezioni` - le rotture del pattern

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G01` | |
| `data` | data | si | `2026-09-14` | non in `03_Chiusure` (altrimenti errore) |
| `oreAula` | intero | si | `4` | |
| `oreLab` | intero | si | `0` | |
| `oreStage` | intero | si | `0` | |
| `motivo` | testo | si | `Primo giorno` | obbligatorio: una rottura senza motivo e' un errore di battitura |

La riga `oreAula = 0, oreLab = 0, oreStage = 0` con motivo esplicito e' il modo per
dichiarare "quel giorno la classe non e' in sessione", cioe' l'informazione che oggi il
sistema deduce dall'ASSENZA di una riga nel foglio per-data (vincolo H26). Renderla
esplicita elimina l'ambiguita' fra "non in sessione" e "riga dimenticata", che e'
proprio la fonte del buco del G04.

### 3.15 `15_PianoGiorniGenerato` - l'espansione per-data

Generato dal sistema da `13` + `14` + `03` + `01`, consegnato al planner per revisione,
non digitato. Stesse colonne del per-data attuale piu' la provenienza.

| colonna | tipo | esempio | note |
|---------|------|---------|------|
| `classe` | testo | `G04` | |
| `data` | data | `2026-09-15` | |
| `oreAula` | intero | `0` | |
| `oreLab` | intero | `8` | |
| `oreStage` | intero | `0` | |
| `origine` | enum | `pattern` | `pattern`, `eccezione`, `chiusura` |
| `regola` | testo | `G04 2026-09-14..2026-12-22 MAR` | la riga di `13` o `14` che l'ha prodotta |

Il planner puo' correggere una cella qui: il sistema, alla reimportazione, la
riconosce come divergenza dal pattern e propone di promuoverla a riga di `14`, con il
motivo da scrivere. Cosi' il pattern non si diluisce mai in silenzio.

Consumatori (per `13`, `14` e `15` insieme): entita' `DailyActivityQuota`, cioe' i
fatti di ore attese per (classe, data) che alimentano il target di riempimento M20,
il suo speculare M33 (surplus), M30 e M31 sul giorno di laboratorio, i vincoli hard
H23, H24 e H26, il filtro VRP degli slot disponibili e il calcolo della data di fine
ideale che M12 usa.

### 3.16 `16_Stage` - finestre di stage

L'anno 2026/27 ha dimostrato che questo foglio serve: la scuola aveva finestre di stage
reali per FC e G13 che non erano modellate come `StagePattern`, e il G04 aveva ore
di stage nella griglia senza alcuna riga di monte-ore corrispondente. Il TASK-434 ha
dovuto instradare a mano 16 ore oltre l'orizzonte.

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G11` | deve esistere in `10` |
| `inizio` | data | si | `2027-01-13` | dentro l'anno |
| `fine` | data | si | `2027-03-19` | maggiore di `inizio` |
| `giorniStage` | lista | si | `MAR;MER;GIO;VEN` | sottoinsieme di `giorniSettimana` |
| `giorniRientro` | lista | no | `LUN` | complemento di `giorniStage`, ridondante ma leggibile |
| `oreGiorno` | intero | si | `8` | |
| `oreTotaliAttese` | decimale | si | `295` | deve conciliare con `giorniStage` per settimane |
| `oreFuoriGriglia` | decimale | no | `16` | ore su sabati, chiusure o oltre la fine anno |
| `note` | testo | no | `Rientro tutti i lunedi` | |

Esempi reali: `G11 / 2027-01-13 / 2027-03-19 / MAR;MER;GIO;VEN / LUN / 295`,
`G07 / 2027-03-23 / 2027-05-28 / MAR;MER;GIO;VEN / LUN / 295`,
`G12 / 2026-11-20 / 2027-04-17 / GIO;VEN / LUN;MAR;MER / 295`,
`G04 / ... / 240 / oreFuoriGriglia 16`.

Consumatori: entita' `StagePattern` (la maschera dei giorni di blackout alimenta H4,
incrociata con `profDuranteStage` della classe), il confinamento dello stage H25 e il
filtro VRP gemello, la generazione dei corsi Stage e il piano dei relativi pin,
`Course.expectedUnscheduledHours` per le ore fuori griglia, M26 per il taglio delle ore
di Sicurezza prima dell'inizio.

### 3.17 `17_Aule`

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `aula` | testo | si | `R12` | univoca |
| `capienza` | intero | si | `24` | `0` ammesso ma segnalato come avviso |
| `tag` | lista | no | `informatica` | vocabolario controllato, tendina |
| `utilizzabile` | SI/NO | si | `SI` | `NO` esclude l'aula dall'assegnazione |
| `note` | testo | no | | |

Esempi reali: `R14 / 20 / termoidraulica`, `R15 / 18 / meccanica`,
`R16 / 15 / moda`, `R17 / 0 / saldatura`, `R18 / 0 / (nessun tag)`.

Consumatori: entita' `Room`; i tag alimentano H-M2 e H-M2b insieme al `tagAula` di
`08_Materie` (oggi la mappa `ROOM_TAG_MAP` in Java), la capienza alimenta M3, il nome
alimenta H2, S11 e S10. Il vocabolario dei tag deve essere lo stesso nei due fogli:
si realizza con una lista di convalida condivisa (celle a tendina).

### 3.18 `18_RotazioniLab`

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T26` | deve esistere in `05` |
| `classe` | testo | si | `G06` | deve esistere in `10` |
| `materia` | testo | no | `Lavaggio` | se presente, deve esistere in `08` |
| `giorno` | enum | si | `MAR` | |
| `slotInizio` | intero | si | `0` | |
| `blocco` | intero | si | `6` | `slotInizio + blocco` non oltre l'ultimo slot del giorno |
| `periodoDa` / `periodoA` | data | si | `2026-09-14` / `2026-12-22` | dentro l'anno |
| `tagAula` | testo | no | `acconciatura` | deve esistere in `17` |

Consumatori: entita' `LabRotation`, letta dalla pre-assegnazione CSP
(`HybridPhase1aService`) che ne prova la fattibilita' e ne fissa i pin prima del solve.
Stato attuale: la tabella e' volutamente vuota nel 2026/27, perche' l'import del
calendario MODA e dello stage produce gia' i pin autoritativi. Il foglio va comunque
previsto: in un anno in cui la scuola decide le rotazioni a tavolino, e' l'unico modo
per dichiararle.

### 3.19 `19_Doppio` - co-docenza e assistenti di laboratorio

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `docente` | testo | si | `T23` | deve esistere in `05` |
| `tagAula` | testo | si | `meccanica` | deve esistere in `17` |
| `periodoDa` / `periodoA` | data | no | vuoto | vuoto = tutto l'anno |
| `note` | testo | no | | |

La scuola definisce il "doppio" come legame fra docente e AULA, non fra docente e
corso: chi assiste in officina assiste chiunque ci sia. Il foglio rispetta quella
semantica; il caso piu' semplice (co-docente di un singolo incarico) resta esprimibile
con la colonna `coDocente` di `12_Incarichi`.

Consumatori: entita' `LabAssistant`, famiglia M-LA e H-D1. Avvertenza onesta: l'audit
ha verificato che oggi il metodo che popola gli assistenti e' morto, quindi la famiglia
M-LA non scatta mai. Il foglio ha senso solo insieme alla decisione se quel meccanismo
debba vivere (cluster C4). Vedi gap G7.

### 3.20 `20_GiorniProfessionalizzanti`

| colonna | tipo | obblig. | esempio | note di validazione |
|---------|------|---------|---------|---------------------|
| `classe` | testo | si | `G12` | deve esistere in `10` |
| `giorno` | enum | si | `MER` | |
| `descrizione` | testo | no | `Giornata professionalizzante` | |

Esempi reali: `G12 / MER`, `G12 / GIO`.

Consumatori: entita' `ProfessionalizzantiDay` e M29 (professionalizzanti ai bordi della
giornata), che oggi legge la mappa hardcoded `M29_RESTRICTED_DAYS_BY_GROUP` con una
sola riga.

### 3.21 `21_CadenzaAttesa` (opzionale, condizionale)

Foglio ponte per M34 e M35. POPOLATO (TASK-445/441) con default `sistema` derivati dal
`golden-cadence-reference.json` che e' gia' il riferimento di M34/M35: 258 coppie
(classe, materia) con `oreSettimanaMax` (max settimanale golden) e `settimaneAttive`.
CONDIZIONALE (censimento #3): la scuola conferma la cadenza solo se vuole i capi M34/M35
(origine -> scuola attiva i vincoli); lasciata `sistema`/vuota i vincoli restano inerti
(graceful, nessun danno). In un anno senza golden questo foglio e' la via di sopravvivenza
dichiarata (il JSON sparisce, M34/M35 self-gate inerte senza di esso). Generato da
`cadence_reference.py` (solo rename dei campi JSON, nessuna re-derivation); gate
`test_cadence_identity.py`.

| colonna | tipo | obblig. | esempio | note |
|---------|------|---------|---------|------|
| `classe` | testo | si | `G04` | |
| `materia` | testo | si | `Scienze` | |
| `oreSettimanaMax` | intero | no | `4` | tetto settimanale |
| `settimaneAttive` | intero | no | `24` | numero di settimane in cui la materia deve comparire |
| `origine` | enum | si | `sistema` | `scuola`, `sistema` (carry-forward), `assente` |

---

### 3.22 `22_TeoriaBlocco` - distribuzione teoria come input dichiarato (TASK-471)

Per ogni (classe, materia teorica), la politica di blocco same-day che la scuola
pratica: `1h/giorno (stretta)`, `2h ammessa (blocco)`, o `mista/da-chiedere`. Derivata
dal golden come proposta `sistema` (il golden e' l'oracolo che rivela la pratica non
deducibile dai soli input di costruzione, TASK-466 + verdetto TASK-470); referente conferma
o corregge la colonna `bloccoTeoria` e l'origine passa a `scuola`. Non e' adozione: M-TR
(TASK-468) resta flag OFF, e' INPUT DICHIARATO. Generato da `theory_block_policy.py`
(golden-runtime read, `is_theory` importato dal modulo analyze autorevole); gate
`test_theory_block_identity.py` (policy live == draft Fedora-verificato, set-based).
Le colonne evidenza (ore/giorni/media) sono `sistema` e non sono input scolastico.

| colonna | tipo | obblig. | esempio | note |
|---------|------|---------|---------|------|
| `classe` | testo | si | `G01` | canonica (10_Classi) |
| `materia` | testo | si | `Matematica` | teorica (`is_theory`) |
| `oreTeoria` | intero | si | `80` | ore totali golden (evidenza) |
| `giorniAttivi` | intero | si | `59` | giorni in cui compare (evidenza) |
| `giorniConBlocco` | intero | si | `21` | giorni con >=2h (evidenza) |
| `mediaOreGiorno` | decimale | si | `1.36` | ore/giorno attivo (evidenza) |
| `bloccoTeoria` | enum | si | `mista/da-chiedere` | `1h/giorno (stretta)`, `mista/da-chiedere`, `2h ammessa (blocco)` |
| `origine` | enum | si | `sistema` | `scuola`, `sistema` |

---

## 4. `99_Validazione` - riconciliazione incorporata

Il foglio si autocompila con formule e mostra, per ogni blocco, `OK` oppure la lista
delle righe che non tornano. Formattazione condizionale: rosso per `ERRORE`, giallo per
`AVVISO`. La regola operativa e' che un workbook con anche un solo `ERRORE` non viene
consegnato al sistema.

| ID | Controllo | Regola | Livello | Caso reale che intercetta |
|----|-----------|--------|---------|---------------------------|
| V1 | Monte-ore classe | `SUMIFS(12!ore; 12!classe; classe)` piu' `16!oreTotaliAttese` per la stessa classe deve uguagliare `10!oreAnnueAttese` | ERRORE | G14: 120 attese contro 115 assegnate, residuo 5 |
| V2 | Incarichi contro piano-giorni | `SUMIFS(12!ore; classe)` deve uguagliare la somma di `oreAula + oreLab` di `15` per quella classe | ERRORE | tutte le classi il cui piano non regge il monte-ore |
| V3 | Stage tripla coerenza | ore stage in `15`, `16!oreTotaliAttese` e `16!oreFuoriGriglia` devono conciliare | ERRORE | G04: 224 ore di stage nella griglia, nessuna riga di monte-ore, 16 ore oltre orizzonte |
| V4 | Capacita' docente annua | `SUMIFS(12!ore; docente)` non oltre la capacita' derivata dalle regole concise di `06`, al netto di `07` | ERRORE | il docente sovraccarico prima ancora di solvare |
| V4b | Saturazione della capacita' | rapporto fra ore assegnate e capacita' oltre l'80 per cento | AVVISO | T01: solo giovedi', 4 classi, 231 ore su 288 disponibili |
| V4c | Capacita' per singolo giorno | per ogni (docente, giorno aperto), le ore che possono cadere solo li' non oltre `maxOreGiorno` per il numero di occorrenze reali di quel giorno | ERRORE | domanda concentrata su un weekday che non ci sta |
| V5 | Materie non mappate | ogni `12!materia` e ogni `13/14!materia` deve trovarsi in `08` o in `09` | ERRORE | `RA` contro `Recuperi ed Approfondimenti`, 31 righe golden non abbinate nel 2026/27 |
| V6 | Date fuori anno | ogni data di `03`, `04`, `07`, `13`, `14`, `16`, `18` dentro `[dataInizio, dataFine]` | ERRORE | il typo `2026-01-22` per `2027-01-22`, rimasto inerte per settimane |
| V7 | Ore contro finestra | per ogni riga di `15`, `oreAula + oreLab + oreStage` non oltre `slotMax - slotMin + 1` di `11` per quel giorno | ERRORE | G14: 171 giorni oltre capacita' fra calendario e finestre |
| V8 | Giorno chiuso con ore | riga di `15` con ore maggiori di zero su una data di `03` o su un giorno con `aperto = NO` in `11` | ERRORE | contraddizione fra chiusure e piano |
| V9 | Cap giornaliero classe | ore per (classe, data) in `15` non oltre `10!maxOreGiorno` | ERRORE | |
| V10 | Nomi non risolti | ogni riferimento incrociato (docente, classe, materia, aula, tag) esiste nel foglio anagrafico | ERRORE | refusi nei nomi, la causa piu' banale e piu' frequente |
| V11 | Chiavi duplicate | (docente, materia, classe) in `12`; (classe, giorno) in `11`; (classe, data) in `14` e `15` | ERRORE | oggi il codice somma i duplicati con un warning che nessuno legge |
| V12 | Blocco e residuo | `12!ore` diviso `blocco` con resto diverso da zero | AVVISO | corsi che non si chiudono in blocchi interi |
| V13 | Capienza aula | per ogni materia con `tagAula`, esiste un'aula con quel tag e capienza almeno pari agli iscritti della classe | AVVISO | R17 e R18 a capienza zero |
| V14 | Rotazioni compatibili | `slotInizio + blocco` dentro la finestra di `11` per quella classe e quel giorno, e il docente disponibile in `06` | ERRORE | rotazioni infattibili, oggi scoperte come "INFEASIBLE" dopo la pre-assegnazione |
| V15 | Stage e professionalizzanti | se `profDuranteStage = NO`, nessun incarico professionalizzante della classe puo' avere ore che cadono solo dentro la finestra stage | AVVISO | |
| V16 | Copertura piano-giorni | ogni giorno scolastico aperto per la classe ha una riga in `15` (anche a zero ore, con motivo) | AVVISO | distingue "non in sessione" da "riga dimenticata" |
| V17 | Coerenza pattern | ogni riga di `15` con `origine = eccezione` ha un motivo non vuoto in `14` | ERRORE | impedisce che le eccezioni diluiscano il pattern in silenzio |
| V18 | Sigle univoche | `05!sigla` senza duplicati fra i docenti che compaiono nei fogli a iniziali | ERRORE | |
| V19 | Slot contigui | `02_Campanella` numerata da 0 senza salti, orari crescenti | ERRORE | |
| V20 | Alias e canonici disgiunti | nessuna `09!grafia` coincide con una `08!materia` | ERRORE | ciclo di normalizzazione |
| V21 | Token e struttura coerenti | se `06!regola` e' compilata, deve produrre esattamente le colonne strutturate della stessa riga | ERRORE | il token che dice una cosa e le colonne un'altra |
| V22 | Regole non contraddittorie | per lo stesso docente non convivono `SOLO_GIORNI` e `MAI_GIORNI`; la disponibilita' risultante non e' vuota | ERRORE | disponibilita' azzerata per distrazione |
| V23 | Docenti esterni dichiarati | ogni docente con `05!esterno = SI` ha almeno una regola in `06` | AVVISO | l'esterno senza regole verrebbe trattato come sempre disponibile |

### Il controllo di capacita' legge le regole concise

V4 e' il controllo che giustifica da solo il modello a due livelli, e va scritto con
cura perche' e' facile sbagliarlo in due modi.

Primo: la capacita' NON si calcola su settimane nominali. Si calcola contando le
occorrenze REALI dei giorni disponibili nel calendario generato, cioe' al netto delle
chiusure di `03` e dei giorni speciali di `04`. Nel 2026/27 l'anno ha 171 giorni di
scuola e 36 giovedi' aperti, non 38.

Secondo: la capacita' si deriva dalle regole di `06`, non da un calendario per-data che
nessuno ha compilato. Il calcolo e' una catena breve e verificabile:

```
giorniAperti(docente)   = giorni della settimana ammessi dalle regole SOLO_GIORNI / MAI_GIORNI
occorrenze(docente)     = quante volte quei giorni ricorrono nel calendario reale
oreGiorno(docente)      = 05!maxOreGiorno, ridotto dalle regole SOLO_FASCIA / FINESTRA
capacita'(docente)      = occorrenze x oreGiorno, meno le assenze di 07
saturazione(docente)    = SUMIFS(12!ore; docente) / capacita'
```

Il caso T01, con i numeri reali del 2026/27:

| Voce | Valore |
|------|--------|
| regola in `06` | `solo giovedi`, tipo `SOLO_GIORNI`, giorni `GIO` |
| `05!maxOreGiorno` | 8 (laboratorio a giornata intera che scavalca la pausa pranzo) |
| giovedi' aperti nell'anno | 36 (171 giorni di scuola, chiusure escluse) |
| capacita' | 288 ore |
| incarichi assegnati | 231 ore su 4 classi (G02 60, G06 60, G10 65, G12 46) |
| saturazione | 80,2 per cento |

Esito: nessun errore, ma avviso di saturazione. Ed e' l'informazione giusta da dare al
planner mentre compila, perche' l'80 per cento di saturazione su un giorno solo della
settimana significa che quel docente e' il collo di bottiglia dell'anno. Nella campagna
2026/27 questo e' emerso come nodo sistemico solo dopo settimane di solve.

Le formule sono deliberatamente elementari. Esempi:

```
V1  = SE(SOMMA.PIU.SE(Incarichi[ore]; Incarichi[classe]; [@classe])
         + SOMMA.PIU.SE(Stage[oreTotaliAttese]; Stage[classe]; [@classe])
         = [@oreAnnueAttese]; "OK"; "ERRORE monte-ore")

V5  = SE(VAL.NON.DISP(CERCA.X([@materia]; Materie[materia]; Materie[materia]))
         * VAL.NON.DISP(CERCA.X([@materia]; Alias[grafia]; Alias[grafia]));
         "ERRORE materia sconosciuta"; "OK")

V7  = SE([@oreAula]+[@oreLab]+[@oreStage] >
         CERCA.X([@classe]&[@giorno]; Calendario[chiave]; Calendario[ampiezza]; 0);
         "ERRORE oltre finestra"; "OK")
```

In cima al foglio, tre celle di sintesi: numero di errori, numero di avvisi, data
dell'ultimo ricalcolo. Chi consegna il file guarda quelle tre celle.

Nota di metodo: le stesse regole vanno implementate anche lato sistema
all'importazione, perche' un Excel puo' sempre arrivare con le formule rotte o
sostituite da valori. Il foglio non e' la difesa, e' l'anticipo della difesa: serve a
far vedere l'errore a chi lo sta commettendo, nel momento in cui lo commette.

---

## 5. Migrazione dai file attuali

### 5.1 Mappa foglio per foglio

| Sorgente attuale | Foglio nuovo | Trattamento |
|------------------|--------------|-------------|
| `teachers.csv` (sheet "Docenti") | `05_Docenti` | RIUSO parziale. `maxDailyHours` resta; `maxEarlyStartDaysPerWeek` e `requiredFreeDaysPerWeek` migrano in `06` come righe-regola. Nuove: `sigla`, `esterno` |
| `availability_rules.csv` (sheet "Vincoli Docenti", trascritto a mano) | `06` + `07` | RIORGANIZZA e COMPRIME. Le 23 righe attuali, che dichiarano il complemento ("chiuso lunedi', martedi', mercoledi', venerdi'" per dire "solo giovedi'"), diventano circa 12 regole positive; le tre assenze datate di T12 passano in `07`. La prosa del docx resta come `note` |
| `groups.csv` (sheet "Elenchi") | `10_Classi` | RIUSO parziale. Nuove: `maxOreGiorno`, `oreAnnueAttese`, `dataInizio`, `profDuranteStage`, `pianoGiorniFine`, `esenteRiempimento`, `tesinaDopoStage` |
| `rooms.csv` | `17_Aule` | RIUSO. Nuove: `tag` (oggi mappa Java), `utilizzabile` |
| `incarichi.csv` (sheet "Incarichi in riga") | `12_Incarichi` | RIORGANIZZA. Le righe pseudo-docente escono: `Stage` va in `16`, `Totale ore corso` diventa `10!oreAnnueAttese`, `Esame` e `RA` diventano `tipo`. Nuove: `blocco`, `legataQuotaLab`, `coDocente`, `oreFuoriGriglia` |
| `calendario_class_days.csv` (14 fogli per classe) | `13` + `14`, poi `15` generato | RIORGANIZZA. Il per-data smette di essere l'input e diventa l'output rivedibile |
| `calendario_fc.csv` (sheet "DEF FC TUTTI") | `13` + `14` | RIORGANIZZA. Le tre colonne aula/lab/stage sopravvivono come tre colonne del pattern, non come 909 righe per data |
| `calendario_moda.csv` (sheet "MODA TUTTI", blocchi PE/MU/BO, turno codificato nel segno) | `13` + `14` piu' `12!coDocente` | RIORGANIZZA in profondita'. Le iniziali si risolvono con `05!sigla`; il turno codificato come segno negativo diventa `slotInizio` esplicito nel pattern |
| `lessons.csv` (sheet "Orario in righe", la griglia golden) | nessun foglio | NON e' input. E' il risultato dell'anno precedente. Resta come riferimento per la precompilazione e per M34/M35 (vedi gap G1) |
| `closures.csv` (sheet "Parametri") | `03_Chiusure` | RIUSO. Nuove: `descrizione` e `tipo`, oggi inferite a mano nel codice |
| Sheet "Vincoli Classi" (finestre stage) | `16_Stage` | RIUSO ed ESTENSIONE. Oggi copre 3 classi su 8 che hanno ore di stage |
| (nessuna sorgente) | `01_Anno` | NUOVO |
| (nessuna sorgente) | `02_Campanella` | NUOVO |
| (nessuna sorgente) | `04_GiorniSpeciali` | NUOVO |
| (nessuna sorgente) | `08_Materie` | NUOVO, il piu' importante |
| (nessuna sorgente) | `09_MaterieAlias` | NUOVO, precompilabile |
| (nessuna sorgente) | `11_ClassiCalendario` | NUOVO. Oggi derivato dal golden, cosa impossibile in un anno senza golden |
| (nessuna sorgente) | `18`, `19`, `20`, `21` | NUOVO |

### 5.2 Cosa il sistema puo' precompilare

Un anno nuovo non parte da un foglio bianco. Il sistema puo' produrre una bozza con la
colonna `origine = sistema`, che la scuola conferma o corregge.

| Foglio | Cosa il sistema propone | Da cosa lo deriva |
|--------|-------------------------|-------------------|
| `03_Chiusure` | festivi nazionali, ponti, vacanze standard | calendario civile piu' le chiusure dell'anno precedente |
| `05_Docenti` | anagrafica e cap dell'anno precedente | il workbook precedente |
| `06` | regole ricorrenti dell'anno precedente, gia' in forma compressa | il workbook precedente; per la prima migrazione, compattazione automatica delle righe VACATION esistenti in `SOLO_GIORNI` / `MAI_GIORNI` / `SOLO_FASCIA`, da far confermare al planner |
| `06` | il token `regola` a partire dalle colonne strutturate, o viceversa | il vocabolario della legenda, applicato in entrambe le direzioni |
| `08_Materie` | riga per ogni materia che compare negli incarichi, con `laboratorio`, `professionalizzante` e `tagAula` precompilati dai classificatori a prefisso attuali | i prefissi oggi in `DataGenerator`, eseguiti una volta sola per generare i dati e poi rimossi |
| `09_MaterieAlias` | le grafie divergenti trovate nei fogli piu' quelle gia' note | la mappa di normalizzazione attuale piu' una scansione fuzzy sui nomi |
| `11_ClassiCalendario` | finestre osservate nell'anno precedente | aggregazione degli slot occupati per (classe, giorno) |
| `13_PianoGiorniPattern` | i pattern periodo per periodo dedotti dal per-data dell'anno precedente | segmentazione: si cerca il pattern settimanale piu' lungo che spiega il maggior numero di date, il resto diventa `14` |
| `15_PianoGiorniGenerato` | espansione completa | `13` + `14` + `03` + `01` |
| `16_Stage` | finestre osservate | date con modalita' stage nel piano dell'anno precedente |
| `21_CadenzaAttesa` | carry-forward rimappato | il riferimento di cadenza dell'anno precedente |

Verifica meccanica obbligatoria della migrazione (metodo dell'audit, cluster C1): i
flag generati per `08_Materie` devono risultare byte-identici a quelli che i
classificatori a prefisso producono oggi sulle 266 righe di incarico reali. Se
divergono anche di una riga, e' la migrazione a essere sbagliata, non il codice.

### 5.3 Sequenza consigliata

1. Generare la bozza del workbook 2026/27 dai dati attuali e verificare l'identita'
   byte a byte. Nessun cambiamento di comportamento: e' solo un cambio di sorgente.
2. Far compilare alla scuola i soli fogli veramente nuovi: `08_Materie` (conferma dei
   flag proposti), `11_ClassiCalendario` (conferma delle finestre), `16_Stage`
   (completamento delle classi mancanti).
3. Attivare il foglio `99_Validazione` sui dati 2026/27 e leggere i risultati: i casi
   G14, G04 e la divergenza G14 calendario contro golden devono comparire come
   errori. Se non compaiono, e' il foglio di validazione a essere sbagliato.
4. Solo dopo, aprire il workbook 2027/28.

---

## 6. Gap aperti

Cose che il sistema usa e che nemmeno questo workbook copre ancora.

**G1. Riferimenti di cadenza per M34 e M35 in un anno senza golden.** M34 (tetto
settimanale per classe e materia) e M35 (settimane attive minime) leggono un file di
riferimento derivato dal golden dell'anno precedente e si auto-spengono in silenzio se
il file manca. In un anno nuovo senza golden non esiste sorgente. Tre opzioni, da
decidere con maintainer e con la scuola, in ordine di preferenza:
(a) rendere i due vincoli auto-riferiti come M32, cioe' calcolare il riferimento
pre-solve dal template settimanale costruito sui dati vivi, senza alcun golden;
(b) carry-forward esplicito e rimappato dall'anno precedente, con `21_CadenzaAttesa`
che documenta la rimappatura classe per classe;
(c) chiedere alla scuola di compilare `21_CadenzaAttesa` a mano, che pero' e'
esattamente il tipo di dato che il planner umano non ha in testa in forma numerica.
In tutti i casi vale la regola scritta del cluster C5 dell'audit: il vincolo non si
spegne mai in silenzio, lo stato del riferimento (`presente`, `carry-forward`,
`assente`) deve comparire nel report di avvio del solve.

**G2. Numeri golden-derived non riproducibili.** La soglia di 2 ore al giorno di M21 e
le settimane 3 e 4 delle finestre T20 sono state ricavate osservando il golden, ma
non esiste uno script che le rideriva. Il workbook copre il DOVE (colonne
`capOreGiorno`, `settimanaInizioMax`, `settimanaInizioMin`) ma non il COME si sceglie il
valore in un anno nuovo. Servono o gli script di derivazione o una dichiarazione della
scuola sotto forma di regola.

**G3. Legame fra modalita' del giorno e materia, per le classi non FC.** Il piano dice
"quel giorno la classe fa laboratorio per 6 ore", ma non dice QUALE laboratorio. Per le
tre classi FC il legame e' oggi una mappa in Java che il foglio `12` sostituisce con
`legataQuotaLab`. Per le altre undici classi il legame non e' mai stato dichiarato: il
solver lo deduce dai vincoli. Se la scuola ha in testa un legame preciso, va chiesto;
se non ce l'ha, va scritto che non c'e'.

**G4. Consumo dello schema campanella.** Il foglio `02_Campanella` e' progettato, ma il
codice assume oggi la durata di un'ora in tre punti diversi, la soglia pomeridiana come
letterale e un impacchettamento in base dieci degli indici. Il cluster C7 dell'audit
classifica la parametrizzazione come rischio alto a campagna aperta perche' tocca M15,
H17, S1 e tutti i passi di riparazione. Decisione proposta: raccogliere il foglio
subito, consumarlo a model-freeze, e nel frattempo fare i due soli fix di correttezza
(indice di slot calcolato via la funzione canonica invece che come `ora - 8`, soglia
pomeridiana derivata dal confine pranzo).

**G5. Clausole dei vincoli docenti che non sono trascrivibili in righe.** Il docx della
scuola contiene frasi come "un giorno a scelta senza ore" che sono state modellate a
mano come M24 e M25, con una interpretazione non letterale documentata. Restano
clausole libere non ancora modellate. Il workbook le raccoglie come `note`, ma la
traduzione in vincolo resta un lavoro umano, non un import.

**G6. Assistenti di laboratorio e famiglia M-LA.** Il foglio `19_Doppio` esiste, ma
l'audit ha verificato che il meccanismo che popola gli assistenti e' morto e che la
famiglia M-LA non scatta mai. Prima di chiedere alla scuola di compilare quel foglio,
va deciso se il meccanismo deve vivere. Chiedere dati per un vincolo inerte e' peggio
che non chiederli.

**G7. Vocabolario controllato dei tag aula.** I tag oggi sono sette stringhe libere
(informatica, meccanica, termoidraulica, acconciatura, moda, saldatura, piu' l'assenza
di tag). Il workbook li usa come chiave fra `08_Materie` e `17_Aule` con una tendina
condivisa, ma non definisce chi possa aggiungere un tag nuovo e con quale procedura.
Serve una regola: probabilmente "un tag nuovo si aggiunge in `17_Aule` e diventa
disponibile nella tendina di `08_Materie`, mai il contrario".

**G8. Data di fine ideale e obiettivi di distribuzione.** M12 penalizza le ore oltre la
data di fine ideale, calcolata dai fatti di ore attese. Non e' un input della scuola ed
e' giusto cosi', ma la scuola potrebbe volerla imporre per certe classi (esami, fine
corso anticipata). Se serve, e' una colonna `dataFineImposta` in `10_Classi`, oggi non
prevista perche' nessuno l'ha chiesta.

---

## 7. Cosa NON va nel workbook

Per chiarezza, e per non far crescere il file oltre il suo scopo. Restano fuori, e
restano legittimamente in codice, i pesi e le curve di calibrazione della ricerca:
la progressione di M20, il peso di M33, i pesi di M34 e M35, il fattore 20 al minuto di
M15, il gradiente 50 di M18, i parametri di scarsita' di M32, la configurazione delle
metaeuristiche. Non sono dati della scuola: sono il modo in cui il solver cerca. La
direttiva primaria di `CLAUDE.md` separa esattamente su questa linea la correttezza del
modello dal costo computazionale, e il workbook sta tutto dal lato del modello.

---

## Sommario (10 righe)

1. Proposta di un solo workbook `AnnoScolastico_AAAA-AA.xlsx` con 21 fogli dati piu' uno di validazione, ognuno normalizzato a una riga uguale un fatto.
2. Il foglio nuovo piu' importante e' `08_Materie`: oggi le materie non hanno alcun file di input e ogni loro proprieta' e' dedotta da circa 48 prefissi di stringhe italiane in Java.
3. Il secondo blocco e' il calendario dell'anno, coperto da `01_Anno`, `02_Campanella`, `03_Chiusure` e `04_GiorniSpeciali`, che sostituiscono due costanti e una ventina di letterali sparsi.
4. Il piano-giorni cambia forma: si dichiara come pattern settimanale per periodo piu' eccezioni datate con motivo obbligatorio, e il per-data (oggi 3.300 righe scritte a mano) viene generato dal sistema e restituito per revisione.
5. La disponibilita' dei docenti e' a due livelli e concisa: `06` con una riga per regola ricorrente e un vocabolario italiano documentato in legenda (`solo giovedi`, `mai lunedi`, `solo pomeriggio`, `lun-mer mattina`, `max 2 giorni/settimana`, `almeno 1 giorno libero/settimana`), `07` con le sole eccezioni datate ed e' opzionale; il calendario pieno e' accettato ma mai richiesto.
6. Il token testuale e' zucchero e le colonne strutturate accanto restano autoritative, con un controllo bloccante se divergono; l'espansione ricade sulle entita' gia' esistenti (`RecurringAvailabilityRule`, `TeacherAvailability`, i due campi settimanali di `Teacher`), e T01 "solo giovedi" passa da 4 righe di complemento a 1 riga positiva.
7. Le eccezioni per gruppo che oggi sono set hardcoded diventano colonne di `10_Classi`, e spariscono gli pseudo-docenti dagli incarichi: lo stage va in `16_Stage`, il totale di classe diventa `oreAnnueAttese`, esame e pool RA diventano una colonna `tipo`.
8. Il foglio `99_Validazione` implementa 23 controlli con formule elementari; i casi di prova sono G14 990 contro 115, lo stage del G04 presente nella griglia ma assente dal monte-ore, e la saturazione di T01 al 80,2 per cento (231 ore su 288, calcolate sui 36 giovedi' reali e non su settimane nominali).
9. La migrazione e' quasi tutta riuso o riorganizzazione, con verifica meccanica: veramente nuovi da chiedere alla scuola sono solo `08_Materie`, `11_ClassiCalendario` e il completamento di `16_Stage`, e i flag generati per le materie devono uscire byte-identici a quelli dei classificatori a prefisso attuali sulle 266 righe reali.
10. Restano otto gap dichiarati, il piu' rilevante dei quali e' il riferimento di cadenza per M34 e M35 in un anno senza golden, con la preferenza per renderli auto-riferiti come M32 invece di chiedere alla scuola numeri che il planner umano non ha in forma numerica.
