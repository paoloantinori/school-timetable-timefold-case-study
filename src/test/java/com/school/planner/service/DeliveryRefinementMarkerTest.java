package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/**
 * TASK-509 follow-up: the quiescence marker contract, structurally. The run3 incident:
 * the corner-b delivery refinement runs on an ad-hoc solver (NOT solverManager) after
 * the cycle's narration closed, so NOT_SOLVING + stagedStage null fired ~2 min into a
 * ~10 min refinement and the driver killed it (its in-memory -42hard was never saved).
 *
 * <p>The contract this test pins:
 * <ol>
 *   <li>a {@code volatile boolean refiningForDelivery} field exists on ScheduleService
 *       (visibility across the solve thread and the REST poller threads);</li>
 *   <li>it is set AROUND the corner-b call (source-level: the set-true precedes the
 *       runRoomSolveStage call inside a try, reset in the finally) and reset in the
 *       wrapper's finally (no leak across solves);</li>
 *   <li>a public {@code isRefiningForDelivery()} accessor exists (the REST payload
 *       reads it) and the field reads false by default;</li>
 *   <li>the status payload exposes it (source-level on ScheduleResource).</li>
 * </ol>
 *
 * <p>Honestly NOT tested here: the end-to-end race (a driver polling during a real
 * refinement) needs a live solve; that is the round-4 gate's own verification (the
 * driver now waits the marker out, so score.txt and analysis.json must agree).
 */
class DeliveryRefinementMarkerTest {

    @Test
    void markerFieldExistsAndIsVolatile() throws Exception {
        Field f = ScheduleService.class.getDeclaredField("refiningForDelivery");
        assertTrue(Modifier.isVolatile(f.getModifiers()),
                "the marker is written by the solve thread and read by REST pollers");
        assertEquals(boolean.class, f.getType());
    }

    @Test
    void accessorExistsAndDefaultsFalse() throws Exception {
        Method m = ScheduleService.class.getMethod("isRefiningForDelivery");
        assertEquals(boolean.class, m.getReturnType());
        ScheduleService svc = new ScheduleService();
        assertFalse(svc.isRefiningForDelivery(),
                "no refinement in flight outside a corner-b delivery");
    }

    @Test
    void markerBracketsTheRefinementAndResetsInFinally() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/school/planner/service/ScheduleService.java"));
        int setTrue = src.indexOf("refiningForDelivery = true;");
        int call = src.indexOf("refined = runRoomSolveStage(result.best().solution(), false);");
        int finallyReset = src.indexOf("refiningForDelivery = false;", call);
        assertTrue(setTrue >= 0 && setTrue < call, "set-true precedes the refinement call");
        assertTrue(finallyReset > call, "reset follows the call (try/finally)");
        // and the wrapper's outer finally resets it too (no leak across solves)
        int outerFinally = src.lastIndexOf("refiningForDelivery = false;");
        assertTrue(outerFinally > finallyReset, "a second reset exists (wrapper finally)");
    }

    @Test
    void statusPayloadExposesTheMarker() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/school/planner/rest/ScheduleResource.java"));
        assertTrue(src.contains("out.put(\"refiningForDelivery\""),
                "the /status payload carries the marker");
        String driver = java.nio.file.Files.readString(
                java.nio.file.Path.of("scripts/benchmark/baseline-golden.sh"));
        assertTrue(driver.contains("RFD") && driver.contains("refiningForDelivery"),
                "the A/B driver waits the marker out");
    }

    private static void assertEquals(Class<?> expected, Class<?> actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
