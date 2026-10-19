# Audit genericita' / data-driven multi-anno (2026-07-28, direttiva maintainer)

Obiettivo: ogni nuovo anno scolastico si pianifica SENZA toccare il codice, solo dati.
Fotografia a campagna IN CORSO (la soluzione non e' finita: rivalidare a modello
stabilizzato). Metodo: 4 sweep paralleli + verifica diretta di ogni claim load-bearing.

## Verdetto

Il progetto e' data-driven ai BORDI (fogli, golden, cap docenti, schema slot servito al
frontend) e hardcoded al CUORE. Il blocco piu' grande non sono le date: e' la TASSONOMIA
DELLE MATERIE (~48 prefissi di nomi italiani dentro DataGenerator). Le date sono il
secondo blocco ma economiche (16 riferimenti che passano tutti da 2 costanti). Un nuovo
anno oggi richiederebbe di editare ~15 file Java.

## Bug/latenti trovati DALL'audit (da fixare subito, indipendenti dalla migrazione)

1. DayClusteredConstructionPhase.java:479 `LocalDate.of(2025, 9, 10)` VIVO (chiamato a
   648/675/750) e :521 fallback `LocalDate.of(2026, 7, 1)` PRIMA dell'inizio anno.
2. TargetEndDateCalculator.java:95/99/317 orElse con date 2025/2026 stantie.
3. Lesson.java:38 + StudentGroupCompletionService.java:37: slot = hour-8, corretto oggi
   SOLO per coincidenza (14:30 -> 14-8=6); bypassa TimeslotSequence.slotIndexOf.
4. AFTERNOON_START_SLOT_INDEX=6 letterale, non derivato dal confine pranzo.
5. DataGenerator.createLabAssistants MORTO (verificato): LabAssistant sempre vuota,
   l'intera famiglia M-LA NON SCATTA MAI.
6. ImportService export: mancano 6 tabelle (dailyactivityquota, groupcalendarrule,
   labrotation, professionalizzantiday, recurringavailabilityrule, validationsnapshot).
7. File tracked da rimuovere: DataGenerator.java.hardcoded-backup, application
   .properties.bak, dir spuria src/main/webui/src/main/webui/e2e/.
8. Doc drift: CLAUDE.md dice H22_RESTRICTED (codice: M29_RESTRICTED), 268 corsi (sono
   266), 7 Stage pseudo (sono 8 post-434), rotazioni acconciatura "placeholder" (il
   metodo e' vuoto); messages: constraint.H8 rimosso, descrizione M20 pre-385.

## Cluster di lavoro (ordinati per valore; S/M/L = sforzo; rischio = farlo ORA)

C1. ANAGRAFICA MATERIE come dati (M, rischio basso-medio): subjects.csv (subject,
    canonicalName, isLab, professionalizzanti, roomTags) + colonna tags su rooms.csv;
    elimina getRoomTags (17 prefissi), isProfessionalizzante (31), ROOM_TAG_MAP,
    SubjectNormalization, M21_EXEMPT_SUBJECTS, SICUREZZA_SUBJECT, set T20, vocabolario
    pseudo. Verifica meccanica: flag derivati byte-identici agli attuali su 266 righe.
    IL singolo cambio che piu' riduce "edita Java per pianificare un anno".
C2. CALENDARIO SCOLASTICO parametrico (M, basso): config SchoolYear (start, end,
    closures.csv, weekday set, weeks) al posto di: 2 costanti TimeslotSequence,
    CLOSURES_2026_27, 3 StagePattern letterali, FALLBACK_END_DATE, DEFAULT_WEEKS=38,
    21 siti Mon-Fri sparsi. Rimuove di rimbalzo i bug #1-2.
C3. ECCEZIONI PER-GRUPPO come dati (M, SPLIT): meta' seeding (colonne+admin gia'
    esistenti: MAX_HOURS_PER_DAY, NO_PROF_DURING_STAGE, GROUP_CALENDAR_PATTERNS,
    M5_TARGET_GROUPS, EXCLUDED_ROOMS, FC_QUOTA_GROUP_NAMES) = rischio basso, fattibile
    in campagna; meta' solver-facing (isG14, M29_RESTRICTED_DAYS_BY_GROUP verso il
    gia' esistente ProfessionalizzantiDay, QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP) = a
    model-freeze.
C4. PULIZIA LEGACY (S, basso): 4 copie di T28_TEACHER_ID + H11-T28 sostituiti da
    Teacher.getEffectiveMaxDailyHours; createLabAssistants morto (e decidere se M-LA
    deve vivere); backup tracked; refuseIfSolverRunning doppio; lista export
    ImportService; 6 doc drift.
C5. PROCESSO RIFERIMENTI GOLDEN ANNUALI (S-M, basso): path per-anno + regola scritta
    per l'anno-senza-golden (M34/M35 si auto-spengono in silenzio: policy esplicita o
    carry-forward rimappato); script di derivazione anche per soglia M21 e finestre
    T20 (oggi numeri golden-derived NON riproducibili). Il pattern aspirazionale e'
    M32 (riferimento computato pre-solve dai dati vivi, nessun golden richiesto).
C6. SUPERFICIE DI CONFIGURAZIONE (M, basso): blocco planner.school.* in
    application.properties (oggi: ZERO configurazione di dominio) e/o entita'
    SchoolYear con admin; prerequisito perche' C1-C3 atterrino puliti; supera anche la
    trappola MAVEN_OPTS per i knob.
C7. SCHEMA SLOT configurabile (M-L, rischio ALTO ora): SLOT_START_TIMES, pranzo,
    durata 1h in 3 posti, packing base-10, fallback frontend. DEFERIRE la
    parametrizzazione a model-freeze (tocca M15/H17/S1 e tutti i repair); fare ORA solo
    i 2 fix di correttezza (#3 e #4 sopra) che rendono sicura la migrazione futura.
C8. SCRIPT KPI parametrici (S, basso ma valore tardivo): anno/ramp/closures/DATA_DIR
    in shared.py via CLI; terza copia delle chiusure in golden-vs-run-viz da eliminare;
    letterali 2025/26 e denominatore 1176 stantii; path assoluti /Downloads. Ultimo.

## Numeri gia' data-driven (pattern buoni da estendere)

Campi per-docente (maxDailyHours, maxEarlyStartDaysPerWeek, requiredFreeDaysPerWeek) da
teachers.csv; fogli DailyActivityQuota; stage-eligible da incarichi (post-434);
golden-cadence-reference.json con lettura fail-safe e path overridabile; M32 reference
computato pre-solve. I pesi di MODELLO (M20 curve, M33/M34/M35, M15 x20, M18 x50, ...)
restano legittimamente in codice: calibrazione di ricerca, non dati scuola.
