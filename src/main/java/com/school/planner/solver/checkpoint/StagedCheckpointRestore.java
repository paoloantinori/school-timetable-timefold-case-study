package com.school.planner.solver.checkpoint;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;

/**
 * TASK-542 stage 2: applies a {@link StagedCheckpoint} back onto the live lessons and
 * republishes the cycle-derived statics. Pure: the resume driver (stage 3) loads the lessons,
 * calls this, THEN calls {@code refreshSolverStatics} - that order is mandatory (backlog #5:
 * the pinnedLessonTimeslots map filters on {@code lesson.pinned}, which the restore has just
 * written back, promoted pins included).
 *
 * <p>Restore semantics: the grid placement (timeslot/room by id, null = unassigned), BOTH pin
 * flags ({@code pinned} and {@code promotedPin}: the resumed cycle's demotion/release read
 * them from the entity), and the promoted statics from the grid's promoted flag. Any
 * checkpoint id the host cannot resolve fails loud.
 */
public final class StagedCheckpointRestore {

    private StagedCheckpointRestore() {
    }

    public static void apply(StagedCheckpoint checkpoint, List<Lesson> lessons,
            Map<Long, ?> timeslotsById, Map<Long, ?> roomsById) {
        Map<Long, Lesson> lessonsById = lessons.stream()
                .collect(Collectors.toMap(l -> l.id, Function.identity()));
        for (LessonPlacement p : checkpoint.placements()) {
            Lesson lesson = lessonsById.get(p.lessonId());
            if (lesson == null) {
                throw new IllegalArgumentException("checkpoint references lesson " + p.lessonId()
                        + " which the resume host does not have - wrong profile or stale checkpoint?");
            }
            lesson.timeslot = p.timeslotId() == null ? null : resolve(timeslotsById, p.timeslotId(), "timeslot");
            lesson.room = p.roomId() == null ? null : resolve(roomsById, p.roomId(), "room");
            lesson.pinned = p.pinned();
            lesson.promotedPin = p.promoted();
        }
        SchoolSchedule.setPromotedLessonIdsStatic(checkpoint.promotedLessonIds());
    }

    private static <T> T resolve(Map<Long, ?> byId, Long id, String kind) {
        Object value = byId.get(id);
        if (value == null) {
            throw new IllegalArgumentException("checkpoint references " + kind + " " + id
                    + " which the resume host does not have - wrong profile or stale checkpoint?");
        }
        @SuppressWarnings("unchecked")
        T cast = (T) value;
        return cast;
    }
}
