package com.school.planner.solver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the current solver phase for UI display.
 * This is a simple static tracker that can be updated by solver phase listeners
 * and read by the REST API to show current progress.
 */
public class PhaseTrackingListener {

    private static final AtomicInteger currentPhase = new AtomicInteger(0);
    private static final AtomicReference<String> phaseName = new AtomicReference<>("Not running");

    /**
     * Reset phase tracking state. Called when solver starts or stops.
     */
    public static void reset() {
        currentPhase.set(0);
        phaseName.set("Not running");
    }

    /**
     * Set the current phase number and name.
     */
    public static void setPhase(int phase, String name) {
        currentPhase.set(phase);
        phaseName.set(name);
    }

    /**
     * Get the current phase number (0 = not running, 1+ = active phases).
     */
    public static int getCurrentPhase() {
        return currentPhase.get();
    }

    /**
     * Get the human-readable phase name.
     */
    public static String getPhaseName() {
        return phaseName.get();
    }
}
