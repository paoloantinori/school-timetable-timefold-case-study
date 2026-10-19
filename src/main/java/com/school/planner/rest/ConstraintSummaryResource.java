package com.school.planner.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.GroupCalendarRule;
import com.school.planner.domain.LabAssistant;
import com.school.planner.domain.LabRotation;
import com.school.planner.domain.ProfessionalizzantiDay;
import com.school.planner.domain.RecurringAvailabilityRule;
import com.school.planner.domain.StagePattern;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.TeacherAvailability;

/**
 * TASK-528: one-shot aggregate of every INPUT constraint the solver was told
 * about, so the 'Vincoli' page answers "cosa abbiamo detto al solver?" in one
 * read (and one REST call). Shape: categories with count, rows, provenance and
 * an explicit emptiness marker, because SILENCE IS INFORMATION (AC#5: an empty
 * category must be declared, never hidden). Known input-vs-golden divergences
 * (the referente class, e.g. G08 calendar 666h vs incarichi pool 626h) surface in
 * a dedicated "da confermare con la scuola" section (AC#4).
 */
@Path("/api/constraints")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ConstraintSummaryResource {

    @GET
    @Path("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> categories = new ArrayList<>();
        out.put("categories", categories);

        // 1. Teacher availability (dated exceptions: VACATION / PREFERENCE / SICK)
        Map<String, Object> avail = category("disponibilitaDocenti",
                "Disponibilita' docenti (eccezioni datate)");
        long vac = TeacherAvailability.count("type = ?1",
                com.school.planner.domain.AvailabilityType.VACATION);
        long pref = TeacherAvailability.count("type = ?1",
                com.school.planner.domain.AvailabilityType.PREFERENCE);
        avail.put("count", (int) (vac + pref));
        Map<String, Object> availDetail = new LinkedHashMap<>();
        availDetail.put("vacation", (int) vac);
        availDetail.put("vacationTeachers", distinctVacationTeachers());
        availDetail.put("preference", (int) pref);
        avail.put("detail", availDetail);
        avail.put("provenance", "esterna (T28 e rotazioni: derivate da VACATION a giornate intere)");
        avail.put("source", "seed/availability_rules.csv + espansione ricorrenti");
        categories.add(avail);

        // 2. Recurring weekly availability templates
        Map<String, Object> rec = category("regoleSettimanali", "Disponibilita' settimanale ricorrente");
        rec.put("count", (int) RecurringAvailabilityRule.count());
        rec.put("detail", "12 docenti, pattern per weekday (base delle VACATION espanse)");
        rec.put("provenance", "dichiarata");
        rec.put("source", "seed/availability_rules.csv");
        categories.add(rec);

        // 3. Group calendar windows
        Map<String, Object> gcr = category("finestreCalendario", "Finestre calendario per gruppo/weekday");
        gcr.put("count", (int) GroupCalendarRule.count());
        gcr.put("detail", "14 gruppi; lunedi' 27 regole, mar-ven 14 (finestre piu' strette il lunedi')");
        gcr.put("provenance", "dichiarata");
        gcr.put("source", "calendario_class_days.csv / calendario_fc.csv");
        categories.add(gcr);

        // 4. Daily activity quotas
        Map<String, Object> quota = category("quoteGiornaliere", "Quote ore per (gruppo, data)");
        quota.put("count", (int) DailyActivityQuota.count());
        quota.put("detail", "168-177 righe per gruppo (G14 30): aula/lab/stage attesi per data");
        quota.put("provenance", "dichiarata");
        quota.put("source", "calendario_class_days.csv / calendario_fc.csv");
        categories.add(quota);

        // 5. Stage patterns
        Map<String, Object> stage = category("stage", "Finestre stage per gruppo");
        List<Map<String, Object>> stageRows = new ArrayList<>();
        for (StagePattern sp : StagePattern.<StagePattern>listAll()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("who", sp.studentGroup != null ? sp.studentGroup.name : "?");
            r.put("what", "stage");
            r.put("value", sp.startDate + " -> " + sp.endDate);
            r.put("source", "stage pattern");
            stageRows.add(r);
        }
        stage.put("count", stageRows.size());
        stage.put("rows", stageRows);
        stage.put("provenance", "dichiarata");
        categories.add(stage);

        // 6. Lab rotations: EMPTY IS DECLARED
        Map<String, Object> rot = category("rotazioniLab", "Rotazioni lab (weekday/materia per coppia)");
        rot.put("count", (int) LabRotation.count());
        if (((int) LabRotation.count()) == 0) {
            rot.put("emptyMessage",
                    "Nessuna rotazione dichiarata: le rotazioni della chain (15 righe) sono"
                    + " congelate come input statico fuori tabella; per l'anno prossimo e'"
                    + " una voce workbook da far dichiarare alla scuola");
        }
        rot.put("provenance", "nessuna dichiarata (silenzio = informazione)");
        categories.add(rot);

        // 7. Professionalizzanti days
        Map<String, Object> prof = category("giorniProfessionalizzanti", "Giorni professionalizzanti riservati");
        prof.put("count", (int) ProfessionalizzantiDay.count());
        if (((int) ProfessionalizzantiDay.count()) == 0) {
            prof.put("emptyMessage",
                    "Nessun giorno professionalizzante riservato configurato (la regola M29"
                    + " gira su tabella di regole, oggi vuota)");
        }
        categories.add(prof);

        // 8. Lab assistants
        Map<String, Object> assist = category("assistentiLab", "Assistenti lab (M-LA)");
        assist.put("count", (int) LabAssistant.count());
        if (((int) LabAssistant.count()) == 0) {
            assist.put("emptyMessage", "Nessun assistente lab configurato");
        }
        categories.add(assist);

        // 9. Teacher scalar caps and weekly rules
        List<Map<String, Object>> capRows = new ArrayList<>();
        for (Teacher t : Teacher.<Teacher>listAll()) {
            if (t.pseudo) {
                continue;
            }
            if (t.maxDailyHours != null) {
                capRows.add(row(t.name, "cap giornaliero H7", t.maxDailyHours + "h", "teachers.csv"));
            }
            if (t.maxEarlyStartDaysPerWeek != null) {
                capRows.add(row(t.name, "max giorni prima-ora M24", String.valueOf(t.maxEarlyStartDaysPerWeek), "teachers.csv"));
            }
            if (t.requiredFreeDaysPerWeek != null) {
                capRows.add(row(t.name, "giorni liberi richiesti M25", String.valueOf(t.requiredFreeDaysPerWeek), "teachers.csv"));
            }
        }
        Map<String, Object> caps = category("capDocenti", "Cap e regole settimanali per docente");
        caps.put("count", capRows.size());
        caps.put("rows", capRows);
        caps.put("detail", "default H7 = 6h/giorno per tutti i docenti non listati");
        categories.add(caps);

        // AC#4: known input-vs-golden divergences (the referente class)
        List<Map<String, Object>> divergences = new ArrayList<>();
        divergences.add(row("G08", "ore totali: calendario 666 vs incarichi 626",
                "40h di differenza: il golden colloca 667", "confronto piano/corsi"));
        divergences.add(row("G04", "coda dell'anno: 21h oltre il 10/06 (11-15 giugno)",
                "la griglia finisce il 10/06", "domande-brevi E"));
        divergences.add(row("G08", "pool RA 40h vs 41h sul foglio",
                "arrotondamento foglio", "task466"));
        out.put("daConfermare", divergences);
        out.put("generatedNote",
                "Inventario meccanico (task528-vincoli-data-prep-2026-08-19.md);"
                        + " conteggi live dal DB al momento della chiamata");
        return out;
    }

    private int distinctVacationTeachers() {
        return (int) TeacherAvailability.<TeacherAvailability>find("type = ?1",
                com.school.planner.domain.AvailabilityType.VACATION)
                .stream().map(a -> a.teacher != null ? a.teacher.id : null)
                .filter(java.util.Objects::nonNull).distinct().count();
    }

    private static Map<String, Object> category(String id, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        return m;
    }

    private static Map<String, Object> row(String who, String what, String value, String source) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("who", who);
        r.put("what", what);
        r.put("value", value);
        r.put("source", source);
        return r;
    }
}
