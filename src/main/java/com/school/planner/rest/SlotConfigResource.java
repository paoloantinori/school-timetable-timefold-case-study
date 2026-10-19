package com.school.planner.rest;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.solver.TimeslotSequence;

/**
 * Exposes the slot schema (TASK-341's 8 slots/day) so the frontend can render
 * time periods and the lunch-break separator from data instead of the
 * hardcoded 6-row {@code ALL_TIME_PERIODS} list (TASK-358). A single new
 * file, kept independent of {@code ScheduleResource}, so this addition
 * doesn't conflict with parallel work touching that resource.
 */
@Path("/api/config/slots")
@Produces(MediaType.APPLICATION_JSON)
public class SlotConfigResource {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @GET
    public SlotSchema get() {
        LocalTime[] starts = TimeslotSequence.SLOT_START_TIMES;
        List<SlotDefinition> slots = new ArrayList<>(starts.length);
        for (int i = 0; i < starts.length; i++) {
            LocalTime start = starts[i];
            LocalTime end = start.plusHours(1);
            boolean isAfternoon = !start.isBefore(TimeslotSequence.LUNCH_BREAK_END);
            slots.add(new SlotDefinition(i, start.format(HHMM), end.format(HHMM), isAfternoon));
        }
        return new SlotSchema(slots,
                TimeslotSequence.LUNCH_BREAK_START.format(HHMM),
                TimeslotSequence.LUNCH_BREAK_END.format(HHMM));
    }

    public static class SlotDefinition {
        public int index;
        public String startTime;
        public String endTime;
        public boolean isAfternoon;

        public SlotDefinition() {
        }

        public SlotDefinition(int index, String startTime, String endTime, boolean isAfternoon) {
            this.index = index;
            this.startTime = startTime;
            this.endTime = endTime;
            this.isAfternoon = isAfternoon;
        }
    }

    public static class SlotSchema {
        public List<SlotDefinition> slots;
        public String lunchBreakStart;
        public String lunchBreakEnd;

        public SlotSchema() {
        }

        public SlotSchema(List<SlotDefinition> slots, String lunchBreakStart, String lunchBreakEnd) {
            this.slots = slots;
            this.lunchBreakStart = lunchBreakStart;
            this.lunchBreakEnd = lunchBreakEnd;
        }
    }
}
