package com.school.planner.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * TASK-456 value-domain rules: a room with capacity <= 0 is not assignable (general rule on
 * capacity, never a room name), and the lab-fallback default is now ON (ratified). Plain unit
 * test: {@link Lesson#getAvailableRooms()} reads {@link SchoolSchedule#getAllRooms()} (static)
 * and the {@code planner.room.lab-fallback.enabled} system property, both settable without the
 * Quarkus runtime. State (rooms + property) is saved and restored so the suite is unaffected.
 */
class LessonAvailableRoomsTest {

    private static final String PROP = "planner.room.lab-fallback.enabled";

    private String prevProp;
    private List<Room> prevRooms;

    /** AULA X (cap>0, ordinary), R16 (cap>0, tagged "moda"), R18 (cap-0,
     *  ordinary), LAB ZERO (cap-0, tagged "moda"). LAB ZERO proves the cap-0 filter beats a
     *  matching tag (a lab lesson must not be placed in a zero-seat lab either). */
    private List<Room> fixture() {
        return List.of(
                new Room("AULA X", 24, List.of()),
                new Room("R16", 20, List.of("moda")),
                new Room("R18", 0, List.of()),
                new Room("LAB ZERO", 0, List.of("moda")));
    }

    private void applyFixture() {
        SchoolSchedule.setAllRoomsStatic(fixture());
    }

    @BeforeEach
    void saveState() {
        prevProp = System.getProperty(PROP);
        prevRooms = SchoolSchedule.getAllRooms();
        System.clearProperty(PROP);  // ratified default ON; a test sets "false" itself when needed
    }

    @AfterEach
    void restore() {
        if (prevProp == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, prevProp);
        }
        SchoolSchedule.setAllRoomsStatic(prevRooms);
    }

    @Test
    void cap0ExcludedNonLabFallbackOn() {
        applyFixture();
        Lesson l = new Lesson(new Course());  // no requiredRoomTags -> non-lab
        List<Room> avail = l.getAvailableRooms();
        assertTrue(avail.stream().noneMatch(r -> r.capacity <= 0), "cap-0 rooms excluded");
        assertTrue(avail.stream().anyMatch(r -> r.name.equals("AULA X")), "ordinary aula kept");
        assertTrue(avail.stream().anyMatch(r -> r.name.equals("R16")), "tagged lab kept (fallback ON)");
        assertEquals(2, avail.size());
    }

    @Test
    void cap0ExcludedLabEvenWithTag() {
        applyFixture();
        Course c = new Course();
        c.requiredRoomTags = List.of("moda");  // lab lesson
        List<Room> avail = new Lesson(c).getAvailableRooms();
        assertTrue(avail.stream().anyMatch(r -> r.name.equals("R16")), "tag-matching cap>0 lab kept");
        assertTrue(avail.stream().noneMatch(r -> r.capacity <= 0), "cap-0 lab (LAB ZERO) excluded");
        assertEquals(1, avail.size());
    }

    @Test
    void pinnedGrandfatheredInCap0Room() {
        applyFixture();
        Room cap0 = new Room("R18", 0, List.of());
        Lesson l = new Lesson(new Course());
        l.pinned = true;
        l.room = cap0;  // a human pin in a cap-0 room
        List<Room> avail = l.getAvailableRooms();
        // A pin is a human-fixed room: grandfathered (NOT nulled). Nulling would freeze a pinned
        // entity room-null for the whole solve (immovable) with no recovery. See Lesson.getAvailableRooms.
        assertEquals(1, avail.size());
        assertSame(cap0, avail.get(0));
    }

    @Test
    void cap0ExclusionIndependentOfFallbackFlag() {
        System.setProperty(PROP, "false");  // opt out of lab fallback
        applyFixture();
        List<Room> avail = new Lesson(new Course()).getAvailableRooms();
        assertTrue(avail.stream().noneMatch(r -> r.capacity <= 0), "cap-0 excluded even with fallback OFF");
        // fallback OFF also drops tagged R16; only the ordinary cap>0 aula remains
        assertEquals(List.of("AULA X"), avail.stream().map(r -> r.name).toList());
    }

    @Test
    void labFallbackDefaultOnOptOutFalse() {
        assertTrue(SchoolSchedule.isLabFallbackEnabled(), "default ON since TASK-456 ratification");
        System.setProperty(PROP, "false");
        assertFalse(SchoolSchedule.isLabFallbackEnabled(), "explicit false opts out");
        System.setProperty(PROP, "true");
        assertTrue(SchoolSchedule.isLabFallbackEnabled());
    }
}
