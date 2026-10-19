package com.school.planner.solver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.school.planner.domain.Timeslot;

import static org.junit.jupiter.api.Assertions.*;

class TimeslotSequenceTest {

    private static final LocalDate MON = LocalDate.of(2025, 10, 6);
    private static final LocalDate TUE = LocalDate.of(2025, 10, 7);

    // Monday slots (indices 0-7)
    private Timeslot monSlot0, monSlot1, monSlot2, monSlot3;
    private Timeslot monSlot4, monSlot5, monSlot6, monSlot7;
    // Tuesday slot
    private Timeslot tueSlot0;

    private TimeslotSequence sequence;

    @BeforeEach
    void setUp() {
        monSlot0 = ts(1L, MON, 8, 0, 9, 0);
        monSlot1 = ts(2L, MON, 9, 0, 10, 0);
        monSlot2 = ts(3L, MON, 10, 0, 11, 0);
        monSlot3 = ts(4L, MON, 11, 0, 12, 0);
        monSlot4 = ts(5L, MON, 12, 0, 13, 0);
        monSlot5 = ts(6L, MON, 13, 0, 14, 0);
        monSlot6 = ts(7L, MON, 14, 30, 15, 30);
        monSlot7 = ts(8L, MON, 15, 30, 16, 30);
        tueSlot0 = ts(9L, TUE, 8, 0, 9, 0);

        sequence = new TimeslotSequence(List.of(
                monSlot0, monSlot1, monSlot2, monSlot3,
                monSlot4, monSlot5, monSlot6, monSlot7,
                tueSlot0));
    }

    @Test
    void getSlotIndex() {
        assertEquals(0, sequence.getSlotIndex(monSlot0));
        assertEquals(3, sequence.getSlotIndex(monSlot3));
        assertEquals(6, sequence.getSlotIndex(monSlot6));
        assertEquals(7, sequence.getSlotIndex(monSlot7));
        assertEquals(0, sequence.getSlotIndex(tueSlot0));
    }

    @Test
    void getSlotIndex_null() {
        assertEquals(-1, sequence.getSlotIndex(null));
    }

    @Test
    void getTimeslotAtOffset_forward() {
        assertSame(monSlot1, sequence.getTimeslotAtOffset(monSlot0, 1));
        assertSame(monSlot3, sequence.getTimeslotAtOffset(monSlot0, 3));
        assertSame(monSlot6, sequence.getTimeslotAtOffset(monSlot5, 1)); // crosses lunch break
    }

    @Test
    void getTimeslotAtOffset_outOfRange() {
        assertNull(sequence.getTimeslotAtOffset(monSlot7, 1));
        assertNull(sequence.getTimeslotAtOffset(monSlot6, 3));
    }

    @Test
    void getTimeslotAtOffset_crossDay() {
        // Offset from Monday last slot should not find Tuesday
        assertNull(sequence.getTimeslotAtOffset(monSlot7, 1));
    }

    @Test
    void getTimeslotAtOffset_null() {
        assertNull(sequence.getTimeslotAtOffset(null, 1));
    }

    @Test
    void getConsecutiveTimeslots_block4() {
        List<Timeslot> block = sequence.getConsecutiveTimeslots(monSlot0, 4);
        assertNotNull(block);
        assertEquals(4, block.size());
        assertEquals(monSlot0.id, block.get(0).id);
        assertEquals(monSlot3.id, block.get(3).id);
    }

    @Test
    void getConsecutiveTimeslots_block5_crossesBreak() {
        // Starting at slot 3 (11:00), block of 5: 11,12,13,14:30,15:30
        List<Timeslot> block = sequence.getConsecutiveTimeslots(monSlot3, 5);
        assertNotNull(block);
        assertEquals(5, block.size());
        assertEquals(monSlot3.id, block.get(0).id);
        assertEquals(monSlot7.id, block.get(4).id);
    }

    @Test
    void getConsecutiveTimeslots_tooFewRemaining() {
        // Starting at slot 6 (14:30), block of 4 → only 2 remaining
        assertNull(sequence.getConsecutiveTimeslots(monSlot6, 4));
    }

    @Test
    void isValidBlockStart_block4() {
        assertTrue(sequence.isValidBlockStart(monSlot0, 4));  // slot 0, needs 0-3
        assertTrue(sequence.isValidBlockStart(monSlot4, 4));  // slot 4, needs 4-7
        assertFalse(sequence.isValidBlockStart(monSlot5, 4)); // slot 5, needs 5-8 → only 3 remaining
    }

    @Test
    void isValidBlockStart_block5() {
        assertTrue(sequence.isValidBlockStart(monSlot0, 5));  // slot 0, needs 0-4
        assertTrue(sequence.isValidBlockStart(monSlot3, 5));  // slot 3, needs 3-7
        assertFalse(sequence.isValidBlockStart(monSlot4, 5)); // slot 4, needs 4-8 → only 4 remaining
    }

    private static Timeslot ts(Long id, LocalDate date, int startH, int startM, int endH, int endM) {
        Timeslot t = new Timeslot(
                LocalDateTime.of(date, LocalTime.of(startH, startM)),
                LocalDateTime.of(date, LocalTime.of(endH, endM)));
        t.id = id;
        return t;
    }

    // ─── Static slot-schema utilities (TASK-341) ──────────────────────────

    @Test
    void slotIndexOf_allEightSlots() {
        assertEquals(0, TimeslotSequence.slotIndexOf(monSlot0));
        assertEquals(1, TimeslotSequence.slotIndexOf(monSlot1));
        assertEquals(2, TimeslotSequence.slotIndexOf(monSlot2));
        assertEquals(3, TimeslotSequence.slotIndexOf(monSlot3));
        assertEquals(4, TimeslotSequence.slotIndexOf(monSlot4));
        assertEquals(5, TimeslotSequence.slotIndexOf(monSlot5));
        assertEquals(6, TimeslotSequence.slotIndexOf(monSlot6));
        assertEquals(7, TimeslotSequence.slotIndexOf(monSlot7));
    }

    @Test
    void slotIndexOf_null() {
        assertEquals(-1, TimeslotSequence.slotIndexOf(null));
    }

    @Test
    void slotIndexOf_unknownStartTime() {
        Timeslot odd = ts(99L, MON, 10, 15, 11, 0);
        assertEquals(-1, TimeslotSequence.slotIndexOf(odd));
    }

    @Test
    void isAdjacent_backToBack() {
        assertTrue(TimeslotSequence.isAdjacent(monSlot0, monSlot1));
        assertTrue(TimeslotSequence.isAdjacent(monSlot1, monSlot0)); // order-independent
    }

    @Test
    void isAdjacent_acrossLunchBreak() {
        // Slot 5 (13:00-14:00) and slot 6 (14:30-15:30): lunch is not a gap.
        assertTrue(TimeslotSequence.isAdjacent(monSlot5, monSlot6));
        assertTrue(TimeslotSequence.isAdjacent(monSlot6, monSlot5));
    }

    @Test
    void isAdjacent_realGapNotAdjacent() {
        // Slot 4 (12:00-13:00) and slot 6 (14:30-15:30): slot 5 is skipped, a real gap.
        assertFalse(TimeslotSequence.isAdjacent(monSlot4, monSlot6));
    }

    @Test
    void isAdjacent_differentDayNotAdjacent() {
        assertFalse(TimeslotSequence.isAdjacent(monSlot7, tueSlot0));
    }

    @Test
    void isAdjacent_crossDateLunchPatternNotAdjacent() {
        // day1 13:00-14:00 (matches the lunch-break start time) vs day2 14:30
        // (matches the lunch-break end time): same LocalTime pattern as the real
        // lunch transition, but on different dates - must NOT be a false positive.
        Timeslot day2Slot6 = ts(10L, TUE, 14, 30, 15, 30);
        assertFalse(TimeslotSequence.isAdjacent(monSlot5, day2Slot6));
        assertFalse(TimeslotSequence.isAdjacent(day2Slot6, monSlot5));
    }

    @Test
    void isAdjacent_null() {
        assertFalse(TimeslotSequence.isAdjacent(null, monSlot0));
        assertFalse(TimeslotSequence.isAdjacent(monSlot0, null));
    }

    @Test
    void isGap_realGap() {
        assertTrue(TimeslotSequence.isGap(monSlot4, monSlot6));
    }

    @Test
    void isGap_lunchBreakIsNotAGap() {
        assertFalse(TimeslotSequence.isGap(monSlot5, monSlot6));
    }

    @Test
    void isGap_differentDayIsNotAGap() {
        // Cross-day comparisons are out of scope for "gap" (same-day concept only).
        assertFalse(TimeslotSequence.isGap(monSlot7, tueSlot0));
    }

    @Test
    void slotIndexOf_equalsLegacyHourMinusEightOnStandardSchema() {
        // TASK-436 audit #3: Lesson/StudentGroupCompletionService used `hour - 8` to derive a
        // slot index (correct only because slots start at 8:00) and now call slotIndexOf. This
        // pins the equivalence on the standard schema so the replacement is provably invisible:
        // for every standard slot, slotIndexOf == startHour - 8. If the schema ever shifts so
        // this breaks, this test fails BEFORE hour-8 silently diverges in any caller.
        LocalTime[] starts = TimeslotSequence.SLOT_START_TIMES;
        for (int i = 0; i < starts.length; i++) {
            Timeslot ts = ts(100L + i, MON, starts[i].getHour(), starts[i].getMinute(),
                    starts[i].getHour() + 1, starts[i].getMinute());
            assertEquals(starts[i].getHour() - 8, TimeslotSequence.slotIndexOf(ts),
                    "slotIndexOf must match legacy hour-8 for slot " + i + " (" + starts[i] + ")");
        }
    }

    @Test
    void afternoonStartSlotIndex_isDerivedFromLunchBoundary() {
        // AFTERNOON_START_SLOT_INDEX is the first slot at or after LUNCH_BREAK_END (14:30),
        // DERIVED from SLOT_START_TIMES, not a literal 6 (TASK-436 audit #4). Asserting the
        // value, its anchor (slot 6 starts exactly at lunch end), and that re-deriving it
        // independently matches - so a slot-schema or lunch-window change can't silently desync.
        assertEquals(6, TimeslotSequence.AFTERNOON_START_SLOT_INDEX);
        assertEquals(TimeslotSequence.LUNCH_BREAK_END, TimeslotSequence.SLOT_START_TIMES[6]);
        int independentlyDerived = -1;
        for (int i = 0; i < TimeslotSequence.SLOT_START_TIMES.length; i++) {
            if (!TimeslotSequence.SLOT_START_TIMES[i].isBefore(TimeslotSequence.LUNCH_BREAK_END)) {
                independentlyDerived = i;
                break;
            }
        }
        assertEquals(independentlyDerived, TimeslotSequence.AFTERNOON_START_SLOT_INDEX);
    }
}
