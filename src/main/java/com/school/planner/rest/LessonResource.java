package com.school.planner.rest;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.Timeslot;

@Path("/api/lessons")
@Produces(MediaType.APPLICATION_JSON)
public class LessonResource {

    /**
     * Toggles the pin flag. Pinning an unassigned lesson (timeslot == null) is
     * rejected with 409: a pin is meant to fix a lesson to a real slot, so
     * "pinned to nothing" is a semantically-invalid state. (Before TASK-398 it
     * was also a Timefold-invalid state that threw {@code IllegalStateException}
     * deep inside {@code solutionManager.update()} because {@code timeslot} lacked
     * {@code allowsUnassigned}; TASK-398 added {@code allowsUnassigned = true} to
     * enable "the box", so a null timeslot is now a legal value and no longer
     * throws — but a pinned null is still wrong, so this 409 stays.) Unpinning is
     * always allowed, including on an already-corrupted row, since it can only move
     * the lesson toward a valid state.
     */
    @PUT
    @Path("/{id}/pin")
    @Transactional
    public Lesson togglePin(@PathParam("id") Long id) {
        Lesson lesson = Lesson.findById(id);
        if (lesson == null) {
            throw new jakarta.ws.rs.NotFoundException("Lesson not found: " + id);
        }
        if (!lesson.pinned && lesson.timeslot == null) {
            throw new WebApplicationException(
                    "Cannot pin an unassigned lesson (id=" + id + "): assign a timeslot first",
                    Response.Status.CONFLICT);
        }
        lesson.pinned = !lesson.pinned;
        return lesson;
    }

    @PUT
    @Path("/{id}/assign")
    @Transactional
    public Lesson assign(@PathParam("id") Long id, AssignRequest request) {
        Lesson lesson = Lesson.findById(id);
        if (lesson == null) {
            throw new jakarta.ws.rs.NotFoundException("Lesson not found: " + id);
        }
        if (request.timeslotId != null) {
            lesson.timeslot = Timeslot.findById(request.timeslotId);
        } else {
            lesson.timeslot = null;
        }
        if (request.roomId != null) {
            lesson.room = Room.findById(request.roomId);
        } else {
            lesson.room = null;
        }
        return lesson;
    }

    public static class AssignRequest {
        public Long timeslotId;
        public Long roomId;
    }
}
