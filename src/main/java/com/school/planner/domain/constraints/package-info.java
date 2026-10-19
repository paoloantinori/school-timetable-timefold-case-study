/**
 * Constraint justification records for violation reporting.
 * <p>
 * <strong>Pattern:</strong> Justifications store entity {@code long} IDs for constraint violations.
 * String details (names, subjects, times) are resolved on-demand by
 * {@link com.school.planner.service.ConstraintMessageFormatter} to avoid
 * hot-path string allocation during solver runs.
 * <p>
 * <strong>Why Separate Records?</strong> Each conflict type has a dedicated record because:
 * <ul>
 *   <li><strong>Different semantics:</strong> Teacher vs room vs student group conflicts represent distinct domain concepts</li>
 *   <li><strong>Different formatting rules:</strong> {@code ConstraintMessageFormatter} uses different i18n keys per type</li>
 *   <li><strong>Type-safe discrimination:</strong> Sealed interface enables compile-time type checking</li>
 * </ul>
 * <p>
 * <strong>Performance Note:</strong> Justification records are allocated in the solver hot path
 * during constraint evaluation. The ID-based design (3 primitives per record) minimizes allocation
 * overhead compared to storing full entity references or formatted strings.
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // For binary conflict constraints (H1, H2, H3)
 * .justifyWith((a, b, score) -> new XxxConflictJustification(
 *     a.entity.id,  // ID only, not full entity reference
 *     a.id,
 *     b.id
 * ))
 *
 * // For aggregate constraints (H7, H11, M20)
 * .indictWith((lesson, score) -> new XxxConstraintJustification(
 *     lesson.entity.id,
 *     lesson.id
 * ))
 * }</pre>
 *
 * @see ai.timefold.solver.core.api.score.stream.ConstraintJustification
 * @see com.school.planner.service.ConstraintMessageFormatter
 */
package com.school.planner.domain.constraints;
