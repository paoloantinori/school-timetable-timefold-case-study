package com.school.planner.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses solverConfig.xml to extract phase timeout configurations.
 * Reads XML at startup and caches timeout values for each phase.
 */
@ApplicationScoped
public class SolverConfigParser {

    private static final Logger LOGGER = Logger.getLogger(SolverConfigParser.class);

    /** Overall solver timeout (hard stop) in seconds */
    private int overallSecondsSpentLimit = 28800; // Default 8 hours

    /** Per-phase timeout configs: phaseNumber -> PhaseTimeoutConfig */
    private final Map<Integer, PhaseTimeoutConfig> phaseTimeouts = new HashMap<>();

    /**
     * Human-readable phase names with number prefix for UI display, in pipeline order.
     *
     * <p>Public: this is the single source of truth for phase naming, also used by
     * {@link com.school.planner.solver.SolverProgressionListener#getPhaseName(int)} so the
     * two never drift apart again (they were a byte-for-byte duplicated switch/map pair
     * before this consolidation).
     */
    // TASK-378: the fallback CH phase was deleted (QueuedEntityPlacer re-placed
    // fully initialized entities); the custom phase 1 now completes placement.
    // TASK-420: the inert RuinRecreate LS was removed from slot 3, so Compaction
    // and the room phases shifted down to 3/4/5.
    public static final Map<Integer, String> PHASE_NAMES = new java.util.LinkedHashMap<>();
    static {
        PHASE_NAMES.put(1, "1. CH - Lesson Placement");
        PHASE_NAMES.put(2, "2. LS - Compactness");
        PHASE_NAMES.put(3, "3. Compaction");
        PHASE_NAMES.put(4, "4. CH - Room Assignment");
        PHASE_NAMES.put(5, "5. LS - Room Refinement");
    }

    /**
     * Timeout configuration for a single phase.
     */
    public static class PhaseTimeoutConfig {
        private final int phaseNumber;
        private final int secondsSpentLimit;
        private final int unimprovedSecondsSpentLimit;

        public PhaseTimeoutConfig(int phaseNumber, int secondsSpentLimit, int unimprovedSecondsSpentLimit) {
            this.phaseNumber = phaseNumber;
            this.secondsSpentLimit = secondsSpentLimit;
            this.unimprovedSecondsSpentLimit = unimprovedSecondsSpentLimit;
        }

        public int getPhaseNumber() {
            return phaseNumber;
        }

        public int getSecondsSpentLimit() {
            return secondsSpentLimit;
        }

        public int getUnimprovedSecondsSpentLimit() {
            return unimprovedSecondsSpentLimit;
        }

        /**
         * Returns secondsSpentLimit, or 0 if no limit configured.
         */
        public int getHardLimitSeconds() {
            return secondsSpentLimit;
        }

        /**
         * Returns unimprovedSecondsSpentLimit, or 0 if no limit configured.
         */
        public int getUnimprovedLimitSeconds() {
            return unimprovedSecondsSpentLimit;
        }
    }

    /**
     * Initialize by parsing solverConfig.xml.
     * Called automatically by CDI on application startup.
     */
    @PostConstruct
    public void init() {
        parseSolverConfig();
    }

    private void parseSolverConfig() {
        try (InputStream is = getClass().getResourceAsStream("/solverConfig.xml")) {
            if (is == null) {
                LOGGER.warn("solverConfig.xml not found, using default timeout values");
                return;
            }

            // Simple regex-based parsing for termination elements
            // This avoids pulling in full XML parsing dependencies for the solver schema
            String content = new String(is.readAllBytes());

            // Parse overall secondsSpentLimit
            java.util.regex.Pattern overallPattern = java.util.regex.Pattern.compile(
                "<termination>\\s*<secondsSpentLimit>(\\d+)</secondsSpentLimit>\\s*</termination>"
            );
            java.util.regex.Matcher overallMatcher = overallPattern.matcher(content);
            if (overallMatcher.find()) {
                overallSecondsSpentLimit = Integer.parseInt(overallMatcher.group(1));
                LOGGER.infof("Parsed overall secondsSpentLimit: %d", overallSecondsSpentLimit);
            }

            // Parse phase-specific termination configs (TASK-420: 5-phase pipeline).
            // 2 LS phases: phase 2 (Compactness) is the 1st <localSearch> in the XML,
            // phase 5 (Room Refinement) the 2nd. Phase 1 (construction), phase 3
            // (Compaction) and phase 4 (room CH) are custom/CH phases with no LS
            // termination of their own.
            parseLocalSearchPhase(content, 2, "LS - Compactness", 1);
            parseLocalSearchPhase(content, 5, "LS - Room Refinement", 2);

            LOGGER.infof("Parsed %d phase-specific timeout configs", phaseTimeouts.size());

        } catch (Exception e) {
            LOGGER.warnf(e, "Failed to parse solverConfig.xml, using defaults");
        }
    }

    /**
     * @param localSearchOrdinal which {@code <localSearch>} block of the XML backs this phase,
     *        1-based in document order. Passed explicitly (rather than derived from the phase
     *        number) because the pipeline interleaves custom/CH phases between the local
     *        searches, so phase number and localSearch position are unrelated.
     */
    private void parseLocalSearchPhase(String content, int phaseNumber, String phaseName, int localSearchOrdinal) {
        // Look for localSearch blocks with termination
        // This is a simplified parser that looks for patterns like:
        // <localSearch> ... <termination> ... </termination> ... </localSearch>
        String[] parts = content.split("<localSearch>");
        // First <localSearch> is at index 1 (parts[0] is the content before it).
        if (localSearchOrdinal >= parts.length) {
            return; // Not enough localSearch blocks
        }

        String localSearchBlock = parts[localSearchOrdinal];
        if (localSearchBlock == null) {
            return;
        }

        // Extract termination section
        int termStart = localSearchBlock.indexOf("<termination>");
        int termEnd = localSearchBlock.indexOf("</termination>");
        if (termStart == -1 || termEnd == -1) {
            return;
        }

        String terminationBlock = localSearchBlock.substring(termStart, termEnd + "</termination>".length());

        // Extract secondsSpentLimit
        java.util.regex.Pattern hardPattern = java.util.regex.Pattern.compile(
            "<secondsSpentLimit>(\\d+)</secondsSpentLimit>"
        );
        java.util.regex.Matcher hardMatcher = hardPattern.matcher(terminationBlock);
        int hardLimit = 0;
        if (hardMatcher.find()) {
            hardLimit = Integer.parseInt(hardMatcher.group(1));
        }

        // Extract unimprovedSecondsSpentLimit
        java.util.regex.Pattern unimprovedPattern = java.util.regex.Pattern.compile(
            "<unimprovedSecondsSpentLimit>(\\d+)</unimprovedSecondsSpentLimit>"
        );
        java.util.regex.Matcher unimprovedMatcher = unimprovedPattern.matcher(terminationBlock);
        int unimprovedLimit = 0;
        if (unimprovedMatcher.find()) {
            unimprovedLimit = Integer.parseInt(unimprovedMatcher.group(1));
        }

        if (hardLimit > 0 || unimprovedLimit > 0) {
            phaseTimeouts.put(phaseNumber, new PhaseTimeoutConfig(phaseNumber, hardLimit, unimprovedLimit));
            LOGGER.debugf("Parsed phase %d (%s): hard=%ds, unimproved=%ds",
                phaseNumber, phaseName, hardLimit, unimprovedLimit);
        }
    }

    public int getOverallSecondsSpentLimit() {
        return overallSecondsSpentLimit;
    }

    public Map<Integer, PhaseTimeoutConfig> getPhaseTimeouts() {
        return phaseTimeouts;
    }

    public PhaseTimeoutConfig getPhaseTimeout(int phaseNumber) {
        return phaseTimeouts.get(phaseNumber);
    }

    public String getPhaseName(int phaseNumber) {
        return PHASE_NAMES.getOrDefault(phaseNumber, "Phase " + phaseNumber);
    }

    public int getPhaseCount() {
        return PHASE_NAMES.size();
    }

    /**
     * Get all phase names as a list (1-indexed).
     */
    public java.util.List<String> getAllPhaseNames() {
        return java.util.List.copyOf(PHASE_NAMES.values());
    }
}
