# TASK-364 follow-up: LabRotation deriver golden-validato (2026-08-16 pomeriggio)

> Filone 2 del mandato. TASK-364 in se' e' gia' Done (2026-07-17: CalendarioImportService,
> 922 lezioni MODA pinnate). Il problema del mandato e' l'altro pezzo: LabRotation vuota
> sul seed 2026/27 rende il pre-assign Phase 1/2/3 (e l'enumerator TASK-467)
> strutturalmente irraggiungibili.

## Cosa e' fattibile SENZA dati scuola nuovi: TUTTO il weekday, nulla dei periodi

CENSUS (misurato qui, riproducibile col test real-seed): sul golden RAW la
famiglia acconciatura conta 15 coppie, ma la POPOLAZIONE DI PRODUZIONE e' 13:
il filtro e' lo stesso di Phase 1 (`MEMBER OF requiredRoomTags`), quindi i
de-tag di subject_overrides (G14 e G12 Servizio stilistico) valgono anche
qui - quelle lezioni sono aula per Phase 1 e restano fuori pure dalla
derivazione. Nelle 13 coppie: weekday modale al 100% in 11 casi, distribuzione
mista in 2 (T18 G02 Tue 60/Fri 40, T18 G06 Fri 71/Wed 29).
**Tutti i weekday proposti sono giorni-L nei fogli calendario del gruppo**
(input autoritativo): il golden PROPUONE, l'input DISPONE. Totale righe
seminate: 15. Le shape dei blocchi sono la run modale PER WEEKDAY (non per
coppia: T18 G06 pratica venerdi' 5h da slot 0 ma mercoledi' ore singole
da slot 5, e la forma per-coppia sbaglierebbe il giorno minoritario): per
questo si deriva invece di assumere una forma.

## Implementazione (gated)

`service/LabRotationDeriver.java`: flag
`planner.pre-assign.labrotation.derive-from-golden.enabled` DEFAULT OFF (forma
opt-in standard). Hook in HybridPhase1aService PRIMA del cortocircuito
count()==0: se armato e la tabella e' vuota, deriva e persiste (idempotente:
righe presenti = no-op). Regola pura (testabile senza DB): una riga per ogni
weekday con quota >= 20% delle ore della coppia E confermato giorno-L;
slotStart/blockSize = run modale della coppia; periodo = anno intero del
golden (vedi sotto). Banner di provenienza + echo del flag in
refreshSolverStatics (anti -D-dropped).

Armarlo rende Phase 1/2/3 raggiungibili e con loro l'enumerator TASK-467
(il cui gate di efficacia diventa finalmente MISURABILE). NESSUNA efficacia
e' claimata qui: adozione solo col gate A/B (regola del mandato). DUE effetti
dichiarati dell'arming: (a) i periodi derivati (span min/max delle date
golden della coppia, NON anno-pieno) alimentano anche ExpectedDaysCalculator,
quindi i KPI expected-days/completion si spostano; (b) il path a flag spento
non apre transazione (guard anche nel caller).

## Le dipendenze che restano alla scuola (documentate)

1. **SPLIT DI PERIODO**: il modello LabRotation ha periodStart/periodEnd per
   ruotare i docenti fra i gruppi per trimestre; il deriver semina anno-pieno
   (due righe per le coppie rotanti = la forma MULTI_DAY documentata, "T15
   Mon+Tue" style). I confini reali dei trimestri di rotazione NON sono
   deducibili ne' dal golden ne' dai fogli: vanno chiesti (workbook).
2. **SENZA GOLDEN IL DERIVER DEGRADA A NO-OP LOGGATO** (non abortisce: la
   lettura mancata del golden e' catturata, le Phase 1/2/3 restano skippate
   come oggi). La provenienza e' golden-derived (famiglia censita TASK-452;
   stessa natura di Q22 e delle statiche TASK-451/464). Quando il golden
   sparira', le righe dovranno arrivare da input dichiarato: e' esattamente
   il foglio 23_GiornoFisso proposto dal mining round 1 (weekday fisso per
   coppia docente-gruppo). Il deriver e' il PONTE: oggi esercita il lever col
   golden, domani la stessa tabella si riempie dal workbook senza toccare il
   codice a valle.
3. Il mixer subjectPrefix (coppie con piu' materie su weekday diversi) resta
   null: nessuna coppia del census lo richiede (le distribuzioni sono pulite
   a livello di coppia).

## Test

`LabRotationDeriverRealSeedCensusTest` (6/6): sintetici (coppia pulita = 1
riga modale; rotante = 2 righe; weekday non confermato dall'input = NON
seminato; sotto-soglia scartato) + real-seed (popolazione produzione 13
coppie, 15 righe, rotating set esatto T18 x2, shape per-weekday misurate
incluse le DUE forme di T18 G06) + flag default OFF.
`HybridPhase1aZeroRotationTest` (2/2): il path senza rotazioni resta
byte-identico a flag spento.
