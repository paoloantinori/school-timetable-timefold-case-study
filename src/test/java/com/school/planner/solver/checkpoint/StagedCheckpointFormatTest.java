package com.school.planner.solver.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * TASK-542 stage 1: the checkpoint FORMAT contract, TDD. Three things are pinned here:
 * (1) the census of the staged cycle's phase boundaries stays in lockstep with the SOURCE of
 *     {@code ScheduleService.runStagedCycle} (a real oracle, not a mirror of the enum);
 * (2) resume points are derived (adjacency), so the full chain is asserted, not spot-checked;
 * (3) the grid+statics+cursor payload survives a serialize/deserialize round-trip.
 * The RESUME machinery (AC #2-#4) is later stages and is NOT under test here.
 */
class StagedCheckpointFormatTest {

    // ---- (1) boundary census: the cycle source is the oracle -------------------

    /**
     * The boundary-relevant stage calls of runStagedCycle, in the order the method makes them,
     * mapped to the boundary AFTER each. If someone adds/reorders/moves a repair pass in the
     * cycle without touching {@link StagedPhaseBoundary}, this test fails - the mirror-version
     * of this guard (asserting the enum against itself) could not catch that drift.
     */
    private static final Map<String, StagedPhaseBoundary> CYCLE_CALL_TO_BOUNDARY = Map.ofEntries(
            Map.entry("loadSchedule(true)", StagedPhaseBoundary.CYCLE_START),
            Map.entry("runConstructionStage(", StagedPhaseBoundary.POST_CONSTRUCTION),
            Map.entry("promoteTemplatePins(", StagedPhaseBoundary.POST_PROMOTION),
            Map.entry("runTimeslotSolveStage(", StagedPhaseBoundary.POST_B_TIMESLOT),
            Map.entry("runDemotionStage(", StagedPhaseBoundary.POST_DEMOTION),
            Map.entry("releasePromotedPinsAndRepublish(", StagedPhaseBoundary.POST_RELEASE),
            Map.entry("runHoleRepair(", StagedPhaseBoundary.POST_HOLE_REPAIR),
            Map.entry("runLabDayConsolidation(", StagedPhaseBoundary.POST_LAB_DAY_CONSOLIDATION),
            Map.entry("runCompositionHoleCloser(", StagedPhaseBoundary.POST_COMPOSITION_HOLE_CLOSER),
            Map.entry("runAnchorRepair(", StagedPhaseBoundary.POST_ANCHOR_REPAIR),
            Map.entry("runSurplusRebalance(", StagedPhaseBoundary.POST_SURPLUS_REBALANCE),
            Map.entry("runQuotaFillRepair(", StagedPhaseBoundary.POST_QUOTA_FILL_REPAIR),
            Map.entry("runExactFinisher(", StagedPhaseBoundary.POST_EXACT_FINISHER),
            Map.entry("runRoomSolveStage(", StagedPhaseBoundary.POST_B_ROOM),
            Map.entry("runEmptyDayFinisher(", StagedPhaseBoundary.POST_EMPTY_DAY_FINISHER),
            Map.entry("runSurplusHarvest(", StagedPhaseBoundary.POST_SURPLUS_HARVEST),
            Map.entry("runMediumPureArm(", StagedPhaseBoundary.POST_MEDIUM_PURE_ARM));

    /** POST_ROOM_QUOTA_FILL_REPAIR has no distinct call: it is the second runQuotaFillRepair. */
    private static final int EXPECTED_QUOTA_FILL_REPAIR_CALLS = 2;

    @Test
    void cycleSourceIsTheBoundaryOracle() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/school/planner/service/ScheduleService.java"));
        String body = extractRunStagedCycleBody(source);
        List<StagedPhaseBoundary> sourceOrder = boundaryCallsInSourceOrder(body);
        // negative half of the oracle: a NEW boundary-relevant pass added to the cycle without
        // a mapping here would be invisible to the order assertion above (it only matches known
        // calls), so every run* stage call in the body must be tracked. Excludes runStagedCycle
        // itself and nested helper names by requiring the call to appear as a statement call.
        Pattern runCalls = Pattern.compile("run[A-Z]\\w*\\(");
        Matcher runMatcher = runCalls.matcher(body);
        while (runMatcher.find()) {
            String call = runMatcher.group();
            if (call.equals("runStagedCycle(")) {
                continue; // the method's own declaration, matched because the body includes it
            }
            assertTrue(CYCLE_CALL_TO_BOUNDARY.containsKey(call),
                    "runStagedCycle calls " + call + " but it has no boundary mapping:"
                            + " a new stage pass was added to the cycle without extending the"
                            + " checkpoint census (StagedPhaseBoundary + this map)");
        }
        assertEquals(StagedPhaseBoundary.linearOrder(), projectOntoLinearOrder(sourceOrder),
                "runStagedCycle's stage-call order IS the boundary census: a pass was added,"
                        + " removed or moved in the cycle without updating StagedPhaseBoundary");
        assertEquals(EXPECTED_QUOTA_FILL_REPAIR_CALLS,
                countCalls(body, "runQuotaFillRepair("),
                "QuotaFillRepair fires twice by design (post-RELEASE + post-B-ROOM): if this"
                        + " changes, POST_ROOM_QUOTA_FILL_REPAIR and its resume point move too");
        assertEquals(StagedPhaseBoundary.POST_FINAL_SURPLUS_HARVEST,
                StagedPhaseBoundary.linearOrder().getLast(),
                "the TASK-577 v11-p2 final harvest chance closes the cycle now: its boundary"
                        + " must stay the last (flag-shaped like its siblings: the boundary"
                        + " always crosses, the pass runs only when armed)");
    }

    @Test
    void iteratingBoundariesAreParameterizedByIteration() {
        // B-TIMESLOT-<n> and C-DEMOTION-<n> run in a loop (max 2 demotion rounds today):
        // they are boundaries too, identified by (boundary, iteration) in the cursor.
        assertTrue(StagedPhaseBoundary.POST_B_TIMESLOT.isIterated());
        assertTrue(StagedPhaseBoundary.POST_DEMOTION.isIterated());
        // and they do NOT belong to the linear list (their position depends on the loop)
        assertFalse(StagedPhaseBoundary.linearOrder().contains(StagedPhaseBoundary.POST_B_TIMESLOT));
        assertFalse(StagedPhaseBoundary.linearOrder().contains(StagedPhaseBoundary.POST_DEMOTION));
    }

    // ---- (2) resume points: full adjacency, derived ------------------------------

    @Test
    void resumePointsAreDerivedForTheWholeLinearChain() {
        List<StagedPhaseBoundary> order = StagedPhaseBoundary.linearOrder();
        for (int i = 0; i < order.size() - 1; i++) {
            assertEquals(order.get(i + 1), order.get(i).resumeEntryPoint().orElseThrow(),
                    "resume point of " + order.get(i) + " must be the next linear boundary");
        }
        assertTrue(order.getLast().resumeEntryPoint().isEmpty(),
                "the cycle's last boundary has no resume point: the cycle is over");
    }

    // ---- (3) format round-trip -----------------------------------------------------

    @Test
    void checkpointRoundTripsThroughJson() {
        StagedCheckpoint original = new StagedCheckpoint(
                StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(
                        StagedPhaseBoundary.POST_B_TIMESLOT, 2, 1, 0L),
                "[-642/0]hard/[-1181262/32013]soft",
                12L, -642L,
                List.of(
                        new LessonPlacement(1L, 100L, 7L, false, false),
                        new LessonPlacement(2L, null, null, false, false),
                        new LessonPlacement(3L, 101L, null, true, true)));
        String json = StagedCheckpointSerializer.toJson(original);
        StagedCheckpoint back = StagedCheckpointSerializer.fromJson(json);
        assertEquals(original, back, "grid + statics + cursor must survive the round-trip intact");
        // promoted statics are reconstructible from the grid (the promoted flag IS the
        // promotedLessonIds static), so no separate section is carried
        assertEquals(1, back.promotedLessonIds().size());
        assertTrue(back.promotedLessonIds().contains(3L));
    }

    @Test
    void cursorCarriesAttemptContextForMultiStartResume() {
        StagedCheckpointCursor cursor = new StagedCheckpointCursor(
                StagedPhaseBoundary.POST_RELEASE, 0, 3, 3L);
        String json = StagedCheckpointSerializer.toJson(new StagedCheckpoint(
                StagedCheckpoint.CURRENT_VERSION, cursor, "[0/0]hard/[0/0]soft", 0L, 0L, List.of()));
        assertEquals(cursor, StagedCheckpointSerializer.fromJson(json).cursor());
    }

    @Test
    void versionIsExplicitAndRefusesUnknownFutureFormat() {
        StagedCheckpoint c = new StagedCheckpoint(
                StagedCheckpoint.CURRENT_VERSION,
                new StagedCheckpointCursor(StagedPhaseBoundary.POST_B_ROOM, 0, 1, 0L),
                "[0/0]hard/[0/0]soft", 0L, 0L, List.of());
        String json = StagedCheckpointSerializer.toJson(c).replace(
                "\"version\":" + StagedCheckpoint.CURRENT_VERSION, "\"version\":99");
        assertThrows(IllegalArgumentException.class,
                () -> StagedCheckpointSerializer.fromJson(json),
                "a checkpoint from a future format version must fail loud, not silently misload");
    }

    // ---- oracle helpers -----------------------------------------------------------

    private static String extractRunStagedCycleBody(String source) {
        int start = source.indexOf("SchoolSchedule runStagedCycle()");
        assertTrue(start >= 0, "runStagedCycle not found - was it renamed?");
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i);
                }
            }
        }
        throw new AssertionError("runStagedCycle body not closed");
    }

    private static List<StagedPhaseBoundary> boundaryCallsInSourceOrder(String body) {
        Pattern calls = Pattern.compile(
                String.join("|", CYCLE_CALL_TO_BOUNDARY.keySet().stream()
                        .map(k -> Pattern.quote(k)).toList()));
        Matcher m = calls.matcher(body);
        List<StagedPhaseBoundary> order = new java.util.ArrayList<>();
        while (m.find()) {
            order.add(CYCLE_CALL_TO_BOUNDARY.get(m.group()));
        }
        return order;
    }

    /**
     * Projects the source's call order onto the linear census: the iterated pair (inside the
     * B-TIMESLOT loop) is dropped, and the runQuotaFillRepair call that follows the room stage
     * is the distinct POST_ROOM_QUOTA_FILL_REPAIR boundary (the first is the post-RELEASE one).
     */
    private static List<StagedPhaseBoundary> projectOntoLinearOrder(List<StagedPhaseBoundary> order) {
        List<StagedPhaseBoundary> linear = new java.util.ArrayList<>();
        for (StagedPhaseBoundary b : order) {
            if (b.isIterated()) {
                continue;
            }
            if (b == StagedPhaseBoundary.POST_QUOTA_FILL_REPAIR
                    && !linear.isEmpty()
                    && linear.getLast() == StagedPhaseBoundary.POST_B_ROOM) {
                linear.add(StagedPhaseBoundary.POST_ROOM_QUOTA_FILL_REPAIR);
            } else if (b == StagedPhaseBoundary.POST_SURPLUS_HARVEST
                    && !linear.isEmpty()
                    && linear.getLast() == StagedPhaseBoundary.POST_MEDIUM_PURE_ARM) {
                // the second runSurplusHarvest call is the cycle-tail chance (v11-p2)
                linear.add(StagedPhaseBoundary.POST_FINAL_SURPLUS_HARVEST);
            } else {
                linear.add(b);
            }
        }
        return linear;
    }

    private static int countCalls(String body, String call) {
        int count = 0;
        int idx = 0;
        while ((idx = body.indexOf(call, idx)) >= 0) {
            count++;
            idx += call.length();
        }
        return count;
    }
}
