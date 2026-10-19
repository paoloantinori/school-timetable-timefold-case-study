package com.school.planner.solver;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

/**
 * TASK-542 iter-25 (mandato maintainer 23/08): the golden-derived TEACHER-WEEKDAY steering
 * matrix, frozen to a seed CSV with the same technique as the H-COPRESENCE depth map and
 * the day-compact menu (the golden is a test-phase advantage; its derived facts survive
 * as declared input - the workbook question is "qual'e' il tuo giorno fisso").
 *
 * <p>Format ({@code seed/teacher-weekday-steering.csv}): one row per steered teacher:
 * {@code teacher,weekday,goldenShare,goldenHours}. Share/hours are PROVENANCE columns
 * (the code steers on the pair alone); the derivation gate (>=50% of the teacher's golden
 * hours on one weekday, min 10 hours) is documented in the file header.
 */
public final class TeacherWeekdaySteeringSource {

    private static final Logger LOG = Logger.getLogger(TeacherWeekdaySteeringSource.class);

    private static final String RESOURCE = "/seed/teacher-weekday-steering.csv";

    private TeacherWeekdaySteeringSource() {
    }

    /** One steering row: the teacher name and their golden-concentrated weekday. */
    public record SteeringRow(String teacher, DayOfWeek weekday, double goldenShare, int goldenHours) {
    }

    private static DayOfWeek parseWeekday(String token) {
        return switch (token.toUpperCase()) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            default -> null;
        };
    }

    public static List<SteeringRow> load() {
        List<SteeringRow> rows = new ArrayList<>();
        try (InputStream in = TeacherWeekdaySteeringSource.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.warnf("teacher-weekday steering matrix not found (%s): no steering", RESOURCE);
                return rows;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = r.readLine(); // header
                while ((line = r.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split(",");
                    if (p.length < 2) {
                        continue;
                    }
                    DayOfWeek wd = parseWeekday(p[1].trim());
                    if (wd == null) {
                        continue;
                    }
                    double share = p.length > 2 ? Double.parseDouble(p[2].trim()) : -1;
                    int hours = p.length > 3 ? Integer.parseInt(p[3].trim()) : -1;
                    rows.add(new SteeringRow(p[0].trim(), wd, share, hours));
                }
            }
        } catch (Exception e) {
            LOG.warnf("teacher-weekday steering read failed (%s): %s", RESOURCE, e.getMessage());
        }
        return rows;
    }

    /**
     * Rows keyed by teacher NAME (the construction phase resolves the seeded Teacher
     * entities by name; absent teachers are skipped with a warning, never a failure).
     */
    public static Map<String, DayOfWeek> byTeacherName(List<SteeringRow> rows) {
        Map<String, DayOfWeek> map = new LinkedHashMap<>();
        for (SteeringRow row : rows) {
            map.putIfAbsent(row.teacher(), row.weekday());
        }
        return map;
    }
}
