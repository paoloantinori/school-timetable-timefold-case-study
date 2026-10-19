# TASK-528 prep dati: inventario fonti vincolo dal DB (19/08 mattina, profilo m13-gf2)

> Popolamento iniziale della pagina Vincoli: i DATI prima della UI. Inventario
> meccanico per entita' (conteggi + scomposizioni utili), pronto da servire.

## Inventario per entita'

| fonte | righe | scomposizione | nota per la UI |
|---|---|---|---|
| TeacherAvailability | 1.338 | 1.305 VACATION + 33 PREFERENCE | 12 docenti con VACATION (esterni es. T28) + 1 con PREFERENCE |
| RecurringAvailabilityRule | 42 | per weekday: lun 10, mar-gio 8, ven 8 | 12 docenti distinti |
| GroupCalendarRule | 83 | 14 gruppi; lun 27 regole, mar-gio-ven 14 | finestre orarie per gruppo/weekday |
| DailyActivityQuota | 2.254 | per gruppo 168-177 (G14 30) | quote per (gruppo, data) |
| StagePattern | 3 | G07 23/03-28/05, G11 13/01-19/03, G12 20/11-17/04 | |
| ProfessionalizzantiDay | **0** | | VUOTA: la tabella esiste ma il seed non la popola (DataGenerator la genera idempotente ma il profilo corrente ha 0 righe: verificare se il generate e' condizionato) |
| LabRotation | **0** | | VUOTA NOTA: le rotazioni sono state congelate fuori tabella (rehearsal golden-free); le 15 righe derivate vivono solo nei profili della chain |
| LabAssistant | **0** | | VUOTA: nessun assistente configurato |

## Campi scalari Teacher non-nulli

| campo | valore | docente |
|---|---|---|
| maxDailyHours | 8 | T01 (default 6 per tutti gli altri) |
| maxEarlyStartDaysPerWeek | 1 | T11 |
| requiredFreeDaysPerWeek | 2 | T05 |
| external | 0 | (nessuno flagged; gli esterni come T28 girano su VACATION) |
| pseudo | 1 | Stage |

## Stringhe nessuna-dato pronte ( categorie vuote )

- ProfessionalizzantiDay: "Nessun giorno professionalizzante riservato configurato"
- LabRotation: "Nessuna rotazione lab configurata (le 15 rotazioni della chain sono congelate come input statico, non in tabella)"
- LabAssistant: "Nessun assistente lab configurato"
