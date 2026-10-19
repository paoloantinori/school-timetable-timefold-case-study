package com.school.planner.service;

import com.school.planner.util.SeedCsv;
import com.school.planner.util.SubjectNormalization;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for the SEEDING-side subject taxonomy (TASK-437, audit
 * C1): every classification that used to live as a hardcoded Italian-prefix
 * matcher in {@code DataGenerator} ({@code getRoomTags} / {@code isLabSubject}
 * / {@code isProfessionalizzante}) now lives as DATA in
 * {@code src/main/resources/seed/subjects.csv}. A new school year is planned by
 * editing that CSV, not Java.
 *
 * <p>Scope of this checkpoint (Tappa 1): only the two flags the seed actually
 * consumes, {@code roomTags} and {@code professionalizzanti}. The solver-side
 * subject classifications ({@code M21_EXEMPT_SUBJECTS} / {@code SICUREZZA_SUBJECT}
 * / T20 windows, still constants in the constraint provider) are NOT carried
 * here yet: carrying them unconsumed would be an unguarded second source of
 * truth. They land in a later checkpoint the moment the constraint provider
 * reads them, so the CSV is always the live source for every column it has.</p>
 *
 * <p><b>Per-(group, subject) exceptions.</b> {@code seed/subject_overrides.csv}
 * lets a single (group, subject) pair override the base flags without touching
 * the subject's global classification. TASK-455 v1 (2026-07-30) applies the first
 * two rows: G14 and G12 Servizio stilistico de-tagged (NONE), the golden-
 * evidenced pair that drove the 42 acconciatura overloads; all other (group,
 * subject) pairs keep the base subjects.csv classification.</p>
 *
 * <p>Loaded once at startup via {@link PostConstruct}. Plain-JUnit tests
 * construct the bean and call {@link #load()} directly (the
 * {@code DataGeneratorSeedTest} convention: no {@code @QuarkusTest}).</p>
 */
@ApplicationScoped
public class SubjectRegistry {

    /** Override cell value that forces {@code roomTags} to empty (a de-tag). */
    static final String NONE_SENTINEL = "NONE";

    /** Immutable classification for one subject (a row of subjects.csv). */
    public record SubjectFlags(List<String> roomTags, boolean professionalizzanti) {
        /** A subject is a lab iff it requires a tagged room (matches the old {@code isLabSubject}). */
        public boolean isLab() {
            return !roomTags.isEmpty();
        }
    }

    // Read-only after load() in production (only the test-only putOverrideForTest
    // mutates overrides); plain HashMap, not ConcurrentHashMap, since there is no
    // concurrent access to pay for. The later solver-side snapshot will be an
    // immutable Map.copyOf published pre-solve.
    private final Map<String, SubjectFlags> base = new HashMap<>();
    private final Map<String, SubjectOverride> overrides = new HashMap<>();

    /** (Re)load both CSVs. Public so plain-JUnit tests (same package) and
     * cross-package fixtures (e.g. GoldenSolutionRegressionTest) can prime it. */
    @PostConstruct
    public void load() {
        base.clear();
        overrides.clear();
        for (String[] row : SeedCsv.load("subjects.csv", 3)) {
            String subject = SubjectNormalization.normalize(row[0].trim());
            if (base.containsKey(subject)) {
                throw new RuntimeException("SubjectRegistry: duplicate subject '" + subject
                        + "' in seed/subjects.csv - keep one row per normalized subject");
            }
            base.put(subject, new SubjectFlags(parseTagList(row[1].trim()),
                    parseStrictBool(row[2].trim(), "professionalizzanti for " + subject)));
        }
        for (String[] row : SeedCsv.load("subject_overrides.csv", 4)) {
            String group = row[0].trim();
            String subject = SubjectNormalization.normalize(row[1].trim());
            String overrideKey = key(group, subject);
            if (overrides.containsKey(overrideKey)) {
                throw new RuntimeException("SubjectRegistry: duplicate override '" + overrideKey
                        + "' in seed/subject_overrides.csv");
            }
            overrides.put(overrideKey, new SubjectOverride(row[2].trim(), row[3].trim()));
        }
    }

    /**
     * Base classification for a subject. Fail-loud on an unclassified subject:
     * every seed subject must have a row, and a gap is a real data bug (the
     * SeedCsv "hand-verified extracts" philosophy), not something to default
     * silently.
     */
    public SubjectFlags flagsFor(String subject) {
        return baseFlags(SubjectNormalization.normalize(subject), subject);
    }

    /**
     * Classification for a (group, subject) pair, applying any selective
     * override from {@code subject_overrides.csv}. Fields the override leaves
     * blank are inherited from the base subject row.
     */
    public SubjectFlags flagsFor(String subject, String group) {
        String norm = SubjectNormalization.normalize(subject);
        SubjectFlags baseFlags = baseFlags(norm, subject);
        SubjectOverride override = overrides.get(key(group, norm));
        return override == null ? baseFlags : override.applyTo(baseFlags);
    }

    /** Number of classified subjects (for the byte-identity guard test). */
    int subjectCount() {
        return base.size();
    }

    /**
     * Test-only: inject a (group, subject) override without writing a CSV row,
     * so the overlay path can be exercised against the live
     * {@link #flagsFor(String, String)}. Not used by production seeding.
     */
    void putOverrideForTest(String group, String subject, String roomTagsCell, String professionalizzantiCell) {
        overrides.put(key(group, SubjectNormalization.normalize(subject)),
                new SubjectOverride(roomTagsCell, professionalizzantiCell));
    }

    private SubjectFlags baseFlags(String normalizedSubject, String displaySubject) {
        SubjectFlags flags = base.get(normalizedSubject);
        if (flags == null) {
            throw new RuntimeException("SubjectRegistry: unclassified subject '" + displaySubject
                    + "' (not in seed/subjects.csv) - add a row before seeding");
        }
        return flags;
    }

    private static String key(String group, String subject) {
        return group + "|" + subject;
    }

    private static List<String> parseTagList(String cell) {
        if (cell.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cell.split(";")).map(String::trim)
                .filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Parse a boolean seed cell strictly: only {@code true}/{@code false} (case-insensitive)
     * are accepted. A typo like {@code flase} would otherwise be silently read as {@code false}
     * by {@link Boolean#parseBoolean}, quietly de-classifying a subject - the SeedCsv
     * "hand-verified extracts, fail-loud on any shape mismatch" philosophy applies.
     */
    private static boolean parseStrictBool(String cell, String context) {
        if (cell.equalsIgnoreCase("true")) {
            return true;
        }
        if (cell.equalsIgnoreCase("false")) {
            return false;
        }
        throw new RuntimeException("SubjectRegistry: malformed boolean '" + cell + "' (" + context
                + ") in seed CSV - expected 'true' or 'false'");
    }

    /**
     * Selective override for one (group, subject) pair. A blank cell means
     * "inherit the base row"; {@code roomTags} additionally accepts
     * {@link #NONE_SENTINEL} to force an empty tag list (a de-tag).
     */
    private record SubjectOverride(String roomTagsCell, String professionalizzantiCell) {
        SubjectFlags applyTo(SubjectFlags b) {
            List<String> tags = NONE_SENTINEL.equals(roomTagsCell)
                    ? List.of()
                    : (roomTagsCell.isBlank() ? b.roomTags() : parseTagList(roomTagsCell));
            boolean prof = professionalizzantiCell.isBlank()
                    ? b.professionalizzanti()
                    : parseStrictBool(professionalizzantiCell, "override professionalizzanti");
            return new SubjectFlags(tags, prof);
        }
    }
}
