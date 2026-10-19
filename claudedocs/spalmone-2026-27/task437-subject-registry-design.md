# TASK-437 - C1 Anagrafica materie come dati: design / checkpoint

Data: 2026-07-29. Stato: in esecuzione (Tappa 1). Riferimento: audit
`claudedocs/audit-genericita-multi-anno-2026-07-28.md` cluster C1, direttiva maintainer 2026-07-28.

## Obiettivo

Ogni nuovo anno scolastico si pianifica senza editare Java, solo dati. La tassonomia delle
materie (~48 prefissi cablati) e' il blocco piu' grande. C1 la sposta in `subjects.csv` + colonna
`tags` su `rooms.csv`, lasciando i pesi di MODELLO (M20 curve, M33/34/35, M15 x20, M18 x50,
finestre T20 week 3/4) in codice perche' sono calibrazione di ricerca, non dati scuola.

## Requisito aggiuntivo (gruppo, materia)

Il modello deve poter esprimere un'eccezione per COPPIA (gruppo, materia), non solo per materia.
Caso d'uso: G14 non occupa il laboratorio di acconciatura per Servizio stilistico (assistenza
nel lab della classe titolare), mentre per le altre classi Servizio stilistico resta lab tagged.
Si realizza con `subject_overrides.csv` (riga per (gruppo, materia) che sovrascrive flag selettivi).
File VUOTO sul 2026/27 = comportamento invariato, ma il meccanismo e' live.

## Split in tappe (subject->tag e roomName->tag sono direzioni indipendenti)

### Tappa 1 - lato MATERIA (elimina ~48 prefissi)
- `src/main/resources/seed/subjects.csv`: 52 materie normalizzate, colonne
  `subject,roomTags,professionalizzanti,m21Exempt,sicurezza,t20`.
  - `subject` = nome normalizzato (SubjectNormalization).
  - `roomTags` = tag singolo o vuoto ( oggi ogni materia ha 0 o 1 tag).
  - `t20` = `early`/`late`/vuoto (le SOGLIE settimanali 3/4 restano in codice).
  - Le colonne m21Exempt/sicurezza/t20 sono popolate ora (dato completo), consumate in Tappa 3.
- `src/main/resources/seed/subject_overrides.csv`: header-only sul 2026/27.
- `SubjectRegistry` (@ApplicationScoped bean): carica subjects.csv + subject_overrides.csv,
  espone `flagsFor(subject)` e `flagsFor(subject, group)` (applica overlay). Record `SubjectFlags`.
- DataGenerator: inietta SubjectRegistry; generateCourses usa `flagsFor(subject, group)`;
  rimuove getRoomTags/isLabSubject/isProfessionalizzante e il force-update professionalizzanti
  a ogni startup (fix "stop overwrite admin corrections": il flag si setta solo al seed fresco,
  le correzioni admin persistono).

### Tappa 2 - lato STANZA
- Colonna `tags` su `rooms.csv` (sostituisce ROOM_TAG_MAP, 7 entry). generateRooms la legge.

### Tappa 3 - solver-side
- M21_EXEMPT_SUBJECTS / SICUREZZA_SUBJECT / T20_EARLY/LATE -> lookup registry pubblicato come
  static map pre-solve (pattern M32). Le soglie settimanali T20 restano costanti in codice.

## Verifica byte-identica (comportamento invariato 2026/27)

- subjects.csv e' GENERATO dagli attuali matcher (script `/tmp/derive_subjects.py`, poi
  `scripts/analyze/derive-subjects.py` se lo si rende ripetibile), non trascritto a mano.
  Invariante di gruppo verificata: le 4 coppie di case-duplicate producono flag identici.
- `SubjectRegistryTest`: carica subjects.csv, asserisce le 52 materie + casi non-ovvi
  (Informatica = lab ma NON professionalizzante; Cad/Controllo numerico = entrambi;
  Disegno Edile ed Impiantistico = m21Exempt AND professionalizzanti; Taglio Metalli = no tag
  [referente]; Servizio stilistico = acconciatura [referente conferma lab]).
- `DataGeneratorSeedTest` (266 corsi, 18 stanze, chiavi uniche) resta guard di integrita' runtime.
- Risposte referente PRESERVATE nel dato: Taglio Metalli -> aula, Progettazione/Tecnologia tessile
  -> aula (FASE C), Servizio stilistico/tecnico -> acconciatura (lab), R17 cap 22 (rooms).

## Vincoli tecnici scoperti
- `SeedCsv.load` richiede conteo colonne ESATTO e rigetta le virgolette: subjects.csv a colonne
  fisse, niente quote. Le materie con apostrofi ("Consulenza d'immagine") sono OK (niente virgole).
- Lookup seeding e' FAIL-LOUD su materia ignota (filosofia SeedCsv: dato hand-verified). Lookup
  solver-side (Tappa 3) sara' default-graceful (non deve crashare a meta' solve).

## DoD
/simplify + /code-review high + test.sh exit code (orchestratore) a ogni tappa. Commit+push a tappa.
