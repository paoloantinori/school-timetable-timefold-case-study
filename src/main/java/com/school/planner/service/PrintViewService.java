package com.school.planner.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

import io.quarkus.panache.common.Sort;

/**
 * TASK-659 (b)+(c): ONE server-rendered weekly timetable sheet. The (b) dedicated
 * view is this HTML served at {@code /print/{kind}/{id}}; the (c) PDF is the SAME
 * document rendered through openhtmltopdf. One template, no layout duplication,
 * no app chrome: what the school prints is what the school sees.
 *
 * <p>The document is strict XHTML because openhtmltopdf parses it as XML: every
 * tag closed, no HTML5-only constructs, entities limited to the XML predefined
 * set (apostrophes are literal, the Italian text never needs {@code &nbsp;}).</p>
 */
@ApplicationScoped
public class PrintViewService {

    private static final DateTimeFormatter DAY_HDR = DateTimeFormatter.ofPattern("EEE d/M", Locale.ITALIAN);
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ITALIAN);

    /**
     * The institution line printed on every sheet: official-document content, so it
     * is school input, not a code literal (altitude review F2). Overridable with
     * {@code -Dplanner.print.school-name="..."} without touching the document.
     */
    @ConfigProperty(name = "planner.print.school-name", defaultValue = "ISTITUTO PROFESSIONALE DI STATO")
    String schoolName;

    /** Kind of sheet, with the REST-facing Italian names as the wire values. */
    public enum Kind { DOCENTE("docente"), CLASSE("classe"), AULA("aula");

        public final String wire;

        Kind(String wire) {
            this.wire = wire;
        }

        public static Kind fromWire(String s) {
            for (Kind k : values()) {
                if (k.wire.equals(s)) {
                    return k;
                }
            }
            return null;
        }
    }

    public record Target(String title, String subtitle, Predicate<Lesson> matches) {}

    private Target resolveTarget(Kind kind, long id) {
        return switch (kind) {
            case DOCENTE -> {
                Teacher t = Teacher.findById(id);
                if (t == null) {
                    yield null;
                }
                yield new Target("Orario del docente", t.name,
                        l -> l.course != null && l.course.teacher != null && l.course.teacher.id != null
                                && l.course.teacher.id == id);
            }
            case CLASSE -> {
                StudentGroup g = StudentGroup.findById(id);
                if (g == null) {
                    yield null;
                }
                yield new Target("Orario della classe", g.name,
                        l -> l.getStudentGroup() != null && l.getStudentGroup().id != null
                                && l.getStudentGroup().id == id);
            }
            case AULA -> {
                Room r = Room.findById(id);
                if (r == null) {
                    yield null;
                }
                yield new Target("Orario dell'aula", r.name,
                        l -> l.room != null && l.room.id != null && l.room.id == id);
            }
        };
    }

    /**
     * The whole sheet as XHTML. {@code includeToolbar} adds the screen-only bar
     * (print button + PDF link): the HTML view keeps it, the PDF omits it.
     */
    public String renderWeekSheet(Kind kind, long id, LocalDate weekAnchor, boolean includeToolbar) {
        Target target = resolveTarget(kind, id);
        if (target == null) {
            return null;
        }

        LocalDate monday = weekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate friday = monday.plusDays(4);

        List<Lesson> week = new ArrayList<>(lessonsBetween(monday, friday).stream().filter(target.matches()).toList());
        // Deterministic within-cell order (code-review F5): two groups can share an
        // aula in one slot and equal timeslot keys keep DB return order otherwise.
        week.sort(java.util.Comparator
                .comparing((Lesson l) -> l.course != null ? l.course.subject : "")
                .thenComparing(l -> l.getStudentGroup() != null ? l.getStudentGroup().name : ""));
        // grid[slotStartTime][date] -> lessons at that cell (a cell can hold more than
        // one lesson only for the room view when two groups share an aula: the cell
        // lists both, that IS the aula's timetable). An empty week (holiday, or a
        // week outside the subject's calendar) simply leaves no data rows.
        Map<LocalTime, Map<LocalDate, List<Lesson>>> grid = new TreeMap<>();
        for (Lesson l : week) {
            grid.computeIfAbsent(l.timeslot.startDateTime.toLocalTime(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(l.timeslot.getDate(), k -> new ArrayList<>()).add(l);
        }

        String yearLabel = schoolYearLabel(monday);
        StringBuilder sb = new StringBuilder(16 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>")
                .append(xml(target.title())).append(" ").append(xml(target.subtitle()))
                .append("</title><style>\n")
                .append(css())
                .append("\n</style></head><body>\n");
        if (includeToolbar) {
            sb.append(toolbar(kind, id, monday));
        }
        sb.append("<div class=\"sheet\">")
                .append("<div class=\"hdr\"><div><div class=\"school\">").append(xml(schoolName))
                .append("</div><div class=\"year\">A.S. ").append(yearLabel)
                .append(" &#183; ").append(xml(target.title()))
                .append("</div><div class=\"range\">Settimana dal ")
                .append(monday.format(FULL)).append(" al ").append(friday.format(FULL))
                .append("</div></div><div class=\"who\">").append(xml(target.subtitle()))
                .append("</div></div>")
                .append("<table><tr><th class=\"hour\"></th>");
        for (LocalDate d = monday; !d.isAfter(friday); d = d.plusDays(1)) {
            sb.append("<th>").append(d.format(DAY_HDR)).append("</th>");
        }
        sb.append("</tr>\n");
        for (Map.Entry<LocalTime, Map<LocalDate, List<Lesson>>> row : grid.entrySet()) {
            LocalTime start = row.getKey();
            sb.append("<tr><td class=\"hour\">").append(start).append("</td>");
            for (LocalDate d = monday; !d.isAfter(friday); d = d.plusDays(1)) {
                List<Lesson> cell = row.getValue().get(d);
                sb.append("<td>");
                if (cell != null) {
                    for (Lesson l : cell) {
                        sb.append(cellInner(kind, l));
                    }
                }
                sb.append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>")
                .append(legend(kind, week))
                .append("<div class=\"foot\">Documento generato dall'applicazione Orario &#183; settimana del ")
                .append(monday.format(FULL)).append("</div>")
                .append("</div></body></html>");
        return sb.toString();
    }

    /** Cell body per kind: docente sheet shows the group, others show the teacher. */
    private String cellInner(Kind kind, Lesson l) {
        Course c = l.course;
        String subject = c != null ? c.subject : "";
        String who = kind == Kind.DOCENTE
                ? (l.getStudentGroup() != null ? l.getStudentGroup().name : "")
                : (c != null && c.teacher != null ? c.teacher.name : "");
        String room = l.room != null ? l.room.name : "";
        return "<div class=\"cell\" style=\"background:" + colorFor(kind, l) + "\"><span class=\"s\">"
                + xml(subject) + "</span><span class=\"t\">" + xml(who) + "</span><span class=\"r\">"
                + xml(room) + "</span></div>";
    }

    private String legend(Kind kind, List<Lesson> week) {
        Map<String, String> colors = new LinkedHashMap<>();
        for (Lesson l : week) {
            if (l.course != null && !colors.containsKey(l.course.subject)) {
                colors.put(l.course.subject, colorFor(kind, l));
            }
        }
        if (colors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<div class=\"legend\">");
        colors.forEach((s, c) -> sb.append("<span class=\"lg\"><span class=\"sw\" style=\"background:")
                .append(c).append("\"></span>").append(xml(s)).append("</span>"));
        return sb.append("</div>").toString();
    }

    private String toolbar(Kind kind, long id, LocalDate monday) {
        return "<div class=\"toolbar noprint\"><a class=\"btn\" href=\"javascript:window.print()\">Stampa questa pagina</a>"
                + "<a class=\"btn primary\" href=\"/print/" + kind.wire + "/" + id + ".pdf?week=" + monday
                + "\">Scarica PDF</a><a class=\"btn\" href=\"/\">Torna all'applicazione</a></div>\n";
    }

    private String css() {
        return """
                @page { size: A4 landscape; margin: 10mm; }
                body { font-family: 'DejaVu Sans', Helvetica, Arial, sans-serif; color: #1a1a1a; margin: 0; }
                .toolbar { padding: 6px 8px 10px 8px; }
                .toolbar .btn { display: inline-block; margin-right: 8px; padding: 6px 14px; border: 1px solid #2a313b;
                                border-radius: 6px; color: #1a1a1a; text-decoration: none; font-size: 13px; }
                .toolbar .btn.primary { background: #2a6496; border-color: #2a6496; color: #fff; }
                @media print { .noprint { display: none; } }
                .sheet { padding: 4px 2px; }
                .hdr { display: flex; justify-content: space-between; align-items: flex-end;
                       border-bottom: 3px solid #1a1a1a; padding-bottom: 6px; margin-bottom: 8px; }
                .school { font-size: 13px; font-weight: bold; letter-spacing: 0.5px; }
                .year { font-size: 11px; color: #333; margin-top: 2px; }
                .range { font-size: 10px; color: #555; margin-top: 1px; }
                .who { font-size: 20px; font-weight: bold; }
                table { width: 100%; border-collapse: collapse; font-size: 9px; }
                th { background: #1a1a1a; color: #fff; padding: 4px 5px; font-size: 9px; text-align: center; }
                th.hour { width: 52px; }
                td { border: 1px solid #999; padding: 2px 3px; height: 30px; vertical-align: middle; }
                td.hour { background: #eeeeee; font-weight: bold; font-size: 8.5px; text-align: center; }
                .cell { padding: 2px 3px; border-radius: 3px; }
                .cell .s { font-weight: bold; font-size: 9px; display: block; }
                .cell .t { font-size: 8px; color: #222; display: block; }
                .cell .r { font-size: 7.5px; color: #444; display: block; }
                .legend { margin-top: 8px; font-size: 9px; color: #333; }
                .lg { margin-right: 14px; }
                .sw { display: inline-block; width: 9px; height: 9px; border: 1px solid #888; margin-right: 3px; }
                .foot { margin-top: 8px; font-size: 8px; color: #888; text-align: right; }
                """;
    }

    /**
     * Cell/legend color, matching what the app shows on screen (code-review F2):
     * classe/aula sheets follow the app's priority {@code teacher.color > course.color
     * > pastel hash} (every teacher is seeded a color, so the printout matches the
     * timeline); the docente sheet would be monochrome under teacher color, so there
     * the priority is {@code course.color > pastel hash} and the sheet stays
     * per-materia readable. Course/teacher colors pass through {@link #safeHex}:
     * they are free-form admin input that must never break out of the style
     * attribute (injection on the HTML view, XML breakage on the PDF).
     */
    private String colorFor(Kind kind, Lesson l) {
        String teacherColor = l.course != null && l.course.teacher != null ? l.course.teacher.color : null;
        String courseColor = l.course != null ? l.course.color : null;
        String subject = l.course != null ? l.course.subject : null;
        if (kind != Kind.DOCENTE) {
            String t = safeHex(teacherColor);
            if (t != null) {
                return t;
            }
        }
        String c = safeHex(courseColor);
        if (c != null) {
            return c;
        }
        if (subject == null || subject.isBlank()) {
            return "#f2f2f2";
        }
        float hue = (Math.abs(subject.hashCode()) % 360) / 360.0f;
        return String.format(Locale.ROOT, "hsl(%d, 55%%, 88%%)", Math.round(hue * 360));
    }

    /** Only strict #rgb/#rrggbb[#aa] strings may enter the style attribute. */
    private static String safeHex(String s) {
        if (s == null) {
            return null;
        }
        return s.matches("#[0-9a-fA-F]{3,8}") ? s : null;
    }

    private List<Lesson> lessonsBetween(LocalDate from, LocalDate to) {
        // Single ordered fetch for the week; filtering by kind happens in memory on
        // this same list (the sheet is one-shot, no hot path).
        return Lesson.<Lesson>find(
                "timeslot is not null and timeslot.startDateTime >= ?1 and timeslot.startDateTime < ?2",
                Sort.by("timeslot.startDateTime"),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay()).list();
    }

    private String schoolYearLabel(LocalDate d) {
        int start = d.getMonthValue() >= 9 ? d.getYear() : d.getYear() - 1;
        return start + "/" + String.valueOf(start + 1).substring(2);
    }

    /** XML-escape: the sheet goes through an XML parser, apostrophes stay literal. */
    private String xml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
