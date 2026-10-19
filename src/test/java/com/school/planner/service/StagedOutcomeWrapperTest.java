package com.school.planner.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.event.Event;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK-667/670: the outermost cycle wrapper owns three last-line guarantees -
 * the terminal outcome classification, the stage clear, and the liveness-flag
 * clear - on EVERY exit path (live-verified 25/09: the quick-cancel path skips
 * every other owner). This unit-tests the wrapper semantics directly, no
 * Quarkus context needed: the wrapper touches only volatiles and LifecycleLog.
 */
class StagedOutcomeWrapperTest {

    @BeforeAll
    static void keepLifecycleLogOutOfTheRepoRoot() {
        // The wrapper's publishStage appends to the lifecycle log; without this the
        // plain (non-Quarkus) test run drops a lifecycle.jsonl in the repo root.
        System.setProperty("planner.lifecycle.file", "target/lifecycle-wrapper-test.jsonl");
    }

    private final ScheduleService service = new ScheduleService();

    /** Records every INTERNAL trigger the wrapper fires (code-review F5: the fire
     *  is load-bearing; a regression that drops it must fail here, not on the
     *  public site two days later). A dynamic proxy: the Event interface carries
     *  many default-less select() overloads we do not care about. */
    private final List<String> firedTriggers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private Event<DataMutationEvent> recordingEvent() {
        return (Event<DataMutationEvent>) java.lang.reflect.Proxy.newProxyInstance(
                Event.class.getClassLoader(),
                new Class<?>[] { Event.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("fire") && args[0] instanceof DataMutationEvent e) {
                        firedTriggers.add(e.method() + " " + e.path());
                    }
                    return null;
                });
    }

    private void set(String field, Object value) {
        try {
            Field f = ScheduleService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String field) {
        try {
            Field f = ScheduleService.class.getDeclaredField(field);
            f.setAccessible(true);
            return (T) f.get(service);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Runnable wrapped(Runnable cycle) {
        try {
            Method m = ScheduleService.class.getDeclaredMethod("withStagedOutcome", Runnable.class);
            m.setAccessible(true);
            return (Runnable) m.invoke(service, cycle);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void runCycle(Runnable cycle) {
        set("validationMutationEvent", recordingEvent());
        wrapped(cycle).run();
    }

    @Test
    void normalCompletionClassifiesCompleteAndClearsStageAndLiveness() {
        set("stagedStage", "A-CONSTRUCTION");
        set("cycleThreadAlive", true);

        runCycle(() -> { });

        assertEquals(ScheduleService.StagedOutcome.COMPLETE, service.getStagedOutcome());
        assertNull(service.getStagedStage(), "the wrapper is the last-line owner of the stage clear");
        boolean aliveAfter = get("cycleThreadAlive");
        assertFalse(aliveAfter, "liveness cleared last, every exit path");
        assertEquals(List.of("INTERNAL staged-cycle-complete"), firedTriggers,
                "the terminal outcome fires the watchdog refresh trigger (the 26/09 stale-snapshot cure)");
    }

    @Test
    void stopRegisteredAnywhereInTheCycleWinsTheLabel() {
        set("stagedCancelRequested", true);

        runCycle(() -> { });

        assertEquals(ScheduleService.StagedOutcome.CANCELLED, service.getStagedOutcome(),
                "a cancel requested in a stage-less window (finisher tail, inter-attempt gap) must not be COMPLETE");
        assertEquals(List.of("INTERNAL staged-cycle-cancelled"), firedTriggers);
    }

    @Test
    void exceptionOutsideTheCycleCatchClassifiesFailedNeverComplete() {
        runCycle(() -> { throw new IllegalStateException("wrapper bookkeeping blew up"); });

        assertEquals(ScheduleService.StagedOutcome.FAILED, service.getStagedOutcome());
        boolean aliveAfter = get("cycleThreadAlive");
        assertFalse(aliveAfter);
    }

    @Test
    void quickCancelExitStillClearsStageAndLiveness() {
        // The live-verified 25/09 shape: the body returns before any runStagedCycle
        // entry, leaving the stage at PRE-ASSIGN and nobody else to clear it.
        set("stagedStage", "PRE-ASSIGN");
        set("stagedCancelRequested", true);
        set("cycleThreadAlive", true);

        runCycle(() -> { });

        assertEquals(ScheduleService.StagedOutcome.CANCELLED, service.getStagedOutcome());
        assertNull(service.getStagedStage(), "no permanently-busy app after a stop during the ensure step");
        boolean aliveAfter = get("cycleThreadAlive");
        assertFalse(aliveAfter);
    }

    @Test
    void errorBeatsCancelForHonesty() {
        set("stagedCancelRequested", true);

        runCycle(() -> { throw new RuntimeException("died mid-cancel"); });

        assertEquals(ScheduleService.StagedOutcome.FAILED, service.getStagedOutcome(),
                "FAILED has precedence: a crashed cycle must not pose as a clean user stop");
        assertEquals(List.of("INTERNAL staged-cycle-failed"), firedTriggers);
    }

    @Test
    void livenessStaysTrueWhileTheCycleBodyRuns() {
        set("cycleThreadAlive", true);
        boolean[] observedInsideBody = { false };

        runCycle(() -> observedInsideBody[0] = get("cycleThreadAlive"));

        assertTrue(observedInsideBody[0], "alive for the whole body (stop registration + busy gate cover)");
        boolean aliveAfter = get("cycleThreadAlive");
        assertFalse(aliveAfter);
    }

}
