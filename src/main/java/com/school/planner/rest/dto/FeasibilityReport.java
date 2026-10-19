package com.school.planner.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for schedule feasibility response.
 * Verdict is binary: hard == 0. The message field carries a stable code the
 * frontend localizes; medium/soft are context. The constraint-match breakdown
 * lives in GET /analysis (TASK-660 removed it from here: a verdict needs only
 * the score, and a full match recompute costs minutes).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeasibilityReport {

    private boolean feasible;
    private String message;
    private String score;
    private Long hard;
    private Long medium;
    private Long soft;

    public FeasibilityReport() {
    }

    public FeasibilityReport(boolean feasible, String message, String score,
                            Long hard, Long medium, Long soft) {
        this.feasible = feasible;
        this.message = message;
        this.score = score;
        this.hard = hard;
        this.medium = medium;
        this.soft = soft;
    }

    // Getters and setters
    public boolean isFeasible() { return feasible; }
    public void setFeasible(boolean feasible) { this.feasible = feasible; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public Long getHard() { return hard; }
    public void setHard(Long hard) { this.hard = hard; }

    public Long getMedium() { return medium; }
    public void setMedium(Long medium) { this.medium = medium; }

    public Long getSoft() { return soft; }
    public void setSoft(Long soft) { this.soft = soft; }
}
