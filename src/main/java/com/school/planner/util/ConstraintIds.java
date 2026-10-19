package com.school.planner.util;

/**
 * Helpers for working with Timefold constraint IDs.
 *
 * <p>Timefold's {@link ai.timefold.solver.core.api.score.stream.ConstraintRef#id()}
 * returns {@code "{package}/{constraintName}"} (e.g.,
 * {@code "com.school.planner.domain/H1.1"}). Most display layers only want the
 * short constraint name after the slash.
 */
public final class ConstraintIds {

    private ConstraintIds() {}

    /**
     * Strip the package prefix from a Timefold constraint ref ID.
     *
     * <p>Example: {@code "com.school.planner.domain/H1.1 - Teacher conflict"} →
     * {@code "H1.1 - Teacher conflict"} (the full {@code .asConstraint(...)} string,
     * description included). Returns the input unchanged if no slash is present.
     */
    public static String shortName(String fullId) {
        if (fullId == null) return null;
        int slash = fullId.indexOf('/');
        return slash >= 0 ? fullId.substring(slash + 1) : fullId;
    }

    /**
     * Canonical constraint code, with both the package prefix and the
     * {@code " - description"} suffix stripped.
     *
     * <p>Example: {@code "com.school.planner.domain/H6 - School holiday"} → {@code "H6"}.
     */
    public static String canonicalId(String fullId) {
        String name = shortName(fullId);
        if (name == null) return null;
        int sep = name.indexOf(" - ");
        return sep > 0 ? name.substring(0, sep).trim() : name;
    }
}
