package com.school.planner.service;

import ai.timefold.solver.core.api.score.BendableScore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tracks solver phase progression history.
 * Records when each phase started/ended, scores, and time spent.
 * In-memory only - cleared on solver stop/restart.
 */
@ApplicationScoped
public class PhaseProgressionService {

    @Inject
    SolverConfigParser solverConfigParser;

    /**
     * A single phase record with start/end times and score data.
     */
    public static class PhaseRecord {
        private final int phaseNumber;
        private final String phaseName;
        private final Instant startTime;
        private Instant endTime;
        private BendableScore startScore;
        private BendableScore endScore;
        private long timeSpentMillis;
        private boolean isComplete;

        // Timeout configuration from solverConfig.xml
        private Integer secondsSpentLimit;
        private Integer unimprovedSecondsSpentLimit;

        public PhaseRecord(int phaseNumber, String phaseName, Instant startTime, BendableScore score) {
            this.phaseNumber = phaseNumber;
            this.phaseName = phaseName;
            this.startTime = startTime;
            this.startScore = score;
            this.isComplete = false;
        }

        public int getPhaseNumber() {
            return phaseNumber;
        }

        public String getPhaseName() {
            return phaseName;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void setEndTime(Instant endTime) {
            this.endTime = endTime;
        }

        public BendableScore getStartScore() {
            return startScore;
        }

        public void setStartScore(BendableScore startScore) {
            this.startScore = startScore;
        }

        public BendableScore getEndScore() {
            return endScore;
        }

        public void setEndScore(BendableScore endScore) {
            this.endScore = endScore;
        }

        public long getTimeSpentMillis() {
            return timeSpentMillis;
        }

        public void setTimeSpentMillis(long timeSpentMillis) {
            this.timeSpentMillis = timeSpentMillis;
        }

        @JsonProperty("isComplete")
        public boolean isComplete() {
            return isComplete;
        }

        public void setComplete(boolean complete) {
            isComplete = complete;
        }

        public Integer getSecondsSpentLimit() {
            return secondsSpentLimit;
        }

        public void setSecondsSpentLimit(Integer secondsSpentLimit) {
            this.secondsSpentLimit = secondsSpentLimit;
        }

        public Integer getUnimprovedSecondsSpentLimit() {
            return unimprovedSecondsSpentLimit;
        }

        public void setUnimprovedSecondsSpentLimit(Integer unimprovedSecondsSpentLimit) {
            this.unimprovedSecondsSpentLimit = unimprovedSecondsSpentLimit;
        }
    }

    private final List<PhaseRecord> history = new CopyOnWriteArrayList<>();
    private final AtomicReference<PhaseRecord> currentPhase = new AtomicReference<>();

    /**
     * Reset all progression data. Called when solver starts or stops.
     */
    public void reset() {
        history.clear();
        currentPhase.set(null);
    }

    /**
     * Record a phase transition.
     * Completes the previous phase (if any) and starts a new one.
     *
     * @param phaseNumber The new phase number (1-5)
     * @param phaseName Human-readable phase name
     * @param score Current score at transition time
     * @param timestamp When the transition occurred
     */
    public void onPhaseTransition(int phaseNumber, String phaseName,
                                  BendableScore score, Instant timestamp) {
        // Complete previous phase
        PhaseRecord prev = currentPhase.get();
        if (prev != null && !prev.isComplete()) {
            prev.setEndTime(timestamp);
            prev.setEndScore(score);
            prev.setTimeSpentMillis(Duration.between(prev.getStartTime(), timestamp).toMillis());
            prev.setComplete(true);
            history.add(prev);
        }

        // Start new phase
        PhaseRecord newPhase = new PhaseRecord(phaseNumber, phaseName, timestamp, score);
        currentPhase.set(newPhase);
    }

    /**
     * Finalize the current phase (call when solver ends).
     */
    public void finalizeCurrentPhase(BendableScore finalScore, Instant timestamp) {
        PhaseRecord curr = currentPhase.get();
        if (curr != null && !curr.isComplete()) {
            curr.setEndTime(timestamp);
            curr.setEndScore(finalScore);
            curr.setTimeSpentMillis(Duration.between(curr.getStartTime(), timestamp).toMillis());
            curr.setComplete(true);
            history.add(curr);
            currentPhase.set(null);
        }
    }

    /**
     * Get all completed phases plus the current phase (if any).
     * Includes timeout configuration from solverConfig.xml.
     */
    public List<PhaseRecord> getProgression() {
        List<PhaseRecord> result = new java.util.ArrayList<>(history);

        // Populate timeout configs from solver config
        for (PhaseRecord phase : result) {
            SolverConfigParser.PhaseTimeoutConfig config = solverConfigParser.getPhaseTimeout(phase.getPhaseNumber());
            if (config != null) {
                if (config.getHardLimitSeconds() > 0) {
                    phase.setSecondsSpentLimit(config.getHardLimitSeconds());
                }
                if (config.getUnimprovedLimitSeconds() > 0) {
                    phase.setUnimprovedSecondsSpentLimit(config.getUnimprovedLimitSeconds());
                }
            }
        }

        PhaseRecord curr = currentPhase.get();
        if (curr != null) {
            // For current phase, calculate elapsed time dynamically
            PhaseRecord currCopy = new PhaseRecord(
                curr.getPhaseNumber(),
                curr.getPhaseName(),
                curr.getStartTime(),
                curr.getStartScore()
            );
            currCopy.setTimeSpentMillis(Duration.between(curr.getStartTime(), Instant.now()).toMillis());
            currCopy.setEndScore(curr.getEndScore()); // May be null if still running

            // Mark as complete if end time is set (solver finished or phase transitioned)
            if (curr.getEndTime() != null) {
                currCopy.setComplete(true);
            }

            // Populate timeout config
            SolverConfigParser.PhaseTimeoutConfig config = solverConfigParser.getPhaseTimeout(curr.getPhaseNumber());
            if (config != null) {
                if (config.getHardLimitSeconds() > 0) {
                    currCopy.setSecondsSpentLimit(config.getHardLimitSeconds());
                }
                if (config.getUnimprovedLimitSeconds() > 0) {
                    currCopy.setUnimprovedSecondsSpentLimit(config.getUnimprovedLimitSeconds());
                }
            }

            result.add(currCopy);
        }
        return result;
    }

    /**
     * Get the overall solver timeout in seconds.
     * Used to display total time limit in the UI.
     */
    public int getOverallSecondsSpentLimit() {
        return solverConfigParser.getOverallSecondsSpentLimit();
    }

    /**
     * Export progression data as JSON string.
     */
    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        List<PhaseRecord> phases = getProgression();
        for (int i = 0; i < phases.size(); i++) {
            PhaseRecord p = phases.get(i);
            sb.append("  {\n");
            sb.append("    \"phaseNumber\": ").append(p.getPhaseNumber()).append(",\n");
            sb.append("    \"phaseName\": \"").append(escapeJson(p.getPhaseName())).append("\",\n");
            sb.append("    \"startTime\": \"").append(p.getStartTime()).append("\",\n");
            if (p.getEndTime() != null) {
                sb.append("    \"endTime\": \"").append(p.getEndTime()).append("\",\n");
            }
            sb.append("    \"startScore\": \"").append(p.getStartScore()).append("\",\n");
            if (p.getEndScore() != null) {
                sb.append("    \"endScore\": \"").append(p.getEndScore()).append("\",\n");
            }
            sb.append("    \"timeSpentMillis\": ").append(p.getTimeSpentMillis()).append(",\n");
            sb.append("    \"isComplete\": ").append(p.isComplete());
            if (p.getSecondsSpentLimit() != null) {
                sb.append(",\n    \"secondsSpentLimit\": ").append(p.getSecondsSpentLimit());
            }
            if (p.getUnimprovedSecondsSpentLimit() != null) {
                sb.append(",\n    \"unimprovedSecondsSpentLimit\": ").append(p.getUnimprovedSecondsSpentLimit());
            }
            sb.append("\n  }").append(i < phases.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
