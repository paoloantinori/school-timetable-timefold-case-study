package com.school.planner.util;

import java.util.Map;
import java.util.Set;

/**
 * Subject-name case-duplicate normalization for the 2026/27 spalmone seed
 * data (analysis.md sections 1/4.11): incarichi.csv/lessons.csv contain the
 * same subject spelled two ways in different rows (e.g. "Lingua italiana" vs
 * "Lingua Italiana"). Canonical form picked to match the majority
 * two-capitalized-words convention already used elsewhere in the sheet.
 *
 * <p>Single source of truth for this mapping (TASK-346 code review): both
 * {@code DataGenerator} (seeding) and {@code GoldenScheduleImportService}
 * (golden import) call {@link #normalize} - the map used to be manually
 * duplicated in {@code DataGenerator} because that file is protected from
 * modification without explicit user permission (see
 * {@code .claude/rules/datagenerator-protection.md}); the duplication was
 * removed with explicit authorization to unify on this util instead.</p>
 *
 * <p><b>Charter (broadened, TASK-468/470):</b> this util is the single source not only for
 * subject-NAME normalization ({@link #normalize}) but also for the subject-CLASSIFICATION facts
 * the M-TR theory-adjacent constraint and the TASK-470 construction prototype must agree on:
 * {@link #THEORY_RULE_PRACTICAL_EXEMPT} + {@link #isTheoryForRule} (what counts as "theory") and
 * {@link #THEORY_RULE_SUBJECT_ALIAS} + {@link #theoryRuleGroupingKey} (what counts as "same
 * subject"). Keeping these here means a borderline flip (e.g. referente says Figurino is practical)
 * lands in every consumer at once instead of silently diverging across the constraint and the
 * builder.</p>
 */
public final class SubjectNormalization {

    private SubjectNormalization() {
        // Utility class - prevent instantiation
    }

    public static final Map<String, String> SUBJECT_CASE_NORMALIZATION = Map.of(
        "Lingua italiana", "Lingua Italiana",
        "Educazione civica", "Educazione Civica",
        "Comportamento sociale", "Comportamento Sociale",
        "Sviluppo sostenibile", "Sviluppo Sostenibile"
    );

    /**
     * Normalize a raw subject name from a seed CSV row. Returns the
     * canonical spelling if the raw form is a known case-duplicate,
     * otherwise returns the input unchanged.
     */
    public static String normalize(String rawSubject) {
        return SUBJECT_CASE_NORMALIZATION.getOrDefault(rawSubject, rawSubject);
    }

    // M-TR (TASK-468) + TASK-470 theory-spread construction prototype: the tab "Vincoli DA
    // ANALIZZARE" exempts "laboratori, COMPRESO disegno o informatica" from the "no 2 ore
    // vicine della stessa materia teorica" rule. Labs are already excluded by the caller's
    // own isLab flag (Lesson.isLab()/Course.isLab, authoritative per-course), so this set
    // covers the NON-lab subjects that are nonetheless practical/technical and may take
    // multi-hour blocks (Disegno family, the FB thermohydraulic/electrical subjects, the
    // FC machining subjects, the FA "Ra lab aperto"). These are the isLab=false rows of
    // course_islab_raw.tsv that the AC#1 measurement (theory_adjacent_residual.py) excluded
    // to obtain the 432-day "stretta" golden residual, so a consumer reproduces that 432 on
    // the golden for self-gating.
    //
    // SINGLE SOURCE OF TRUTH, shared by SchoolScheduleConstraintProvider (M-TR constraint)
    // and WeeklyTemplateBuilder (TASK-470 theory-spread construction prototype): the two
    // MUST agree on what "theory" means, otherwise the construction spreads what the
    // constraint does not measure and the residual comparison is meaningless. Subject
    // strings are the AUTHORITATIVE DB spellings (course_islab_raw.tsv), NOT the shorthand
    // in the design doc: "Metrologia" and "RA" do not exist in the seed, the real rows are
    // the full forms below. The three borderline subjects (Figurino, Personalizzazione,
    // Tecnologia e creativita') are deliberately ABSENT: the design treats them as THEORY
    // by default (subject to the rule); moving them here is a one-line flip if referente answers
    // "pratici" (AC#2 open question), and because this is the single source that flip then
    // lands in both the constraint and the construction at once.
    public static final Set<String> THEORY_RULE_PRACTICAL_EXEMPT = Set.of(
            "Disegno", "Disegno Edile ed Impiantistico", "Progettazione Termoidraulica",
            "Elettrotecnica", "Metrologia e Tecnologia dei materiali", "Tecnologia Meccanica",
            "Taglio Metalli", "Ra lab aperto");

    /**
     * M-TR/TASK-470: true when the subject is subject to the theory-adjacent rule, i.e.
     * neither a lab nor a practical/technical exempt subject. {@code isLab} is the caller's
     * per-course authority (Lesson.isLab()/Course.isLab); the exempt set is the subject-name
     * authority kept here as the single source shared by the M-TR constraint and the TASK-470
     * construction prototype. A null subject is counted (not exempt), matching M21's stance;
     * the {@link #normalize} call is reached only for non-null subjects.
     */
    public static boolean isTheoryForRule(boolean isLab, String subject) {
        if (isLab) {
            return false;
        }
        return subject == null || !THEORY_RULE_PRACTICAL_EXEMPT.contains(normalize(subject));
    }

    // M-TR (TASK-468) + TASK-470: the T06 alias hook. The planner note asks (for some
    // cases) that "Informatica" + "Gestione aziendale" be treated as ONE subject for the
    // theory-adjacent rule. The rule groups by subject, so coalescing happens here. Default empty
    // = identity (no aliasing), so both consumers are inert; populating the map is a one-line
    // change once referente confirms the coupling (AC#2 "Nota T06"). SINGLE SOURCE: both the
    // M-TR constraint (groupBy key) and the TASK-470 builder (subjectAlreadyOnDay) call
    // {@link #theoryRuleGroupingKey}, so the construction's notion of "same subject" cannot drift
    // from the constraint's. Keys/values are NORMALIZED forms (apply {@link #normalize} first).
    public static final Map<String, String> THEORY_RULE_SUBJECT_ALIAS = Map.of();

    /**
     * M-TR/TASK-470: the grouping key for "same subject" in the theory-adjacent rule, after
     * applying {@link #normalize} and the {@link #THEORY_RULE_SUBJECT_ALIAS} T06 hook
     * (identity by default). Single source shared by the M-TR constraint's groupBy and the
     * TASK-470 builder's per-day dedup, so the two agree on what counts as a repeat. Null stays
     * null.
     */
    public static String theoryRuleGroupingKey(String subject) {
        if (subject == null) {
            return null;
        }
        String normalized = normalize(subject);
        return THEORY_RULE_SUBJECT_ALIAS.getOrDefault(normalized, normalized);
    }
}
