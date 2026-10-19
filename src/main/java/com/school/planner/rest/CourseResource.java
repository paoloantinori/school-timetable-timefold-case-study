package com.school.planner.rest;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.service.LessonGenerator;

@Path("/api/courses")
@Produces(MediaType.APPLICATION_JSON)
public class CourseResource {

    @Inject
    LessonGenerator lessonGenerator;

    @GET
    public List<Course> list() {
        return Course.listAll();
    }

    @POST
    @Transactional
    public Course create(CourseRequest request) {
        Course course = new Course();
        course.subject = request.subject;
        if (request.teacherId != null) {
            course.teacher = Teacher.findById(request.teacherId);
        }
        if (request.studentGroupId != null) {
            course.studentGroup = StudentGroup.findById(request.studentGroupId);
        }
        course.totalHours = request.totalHours;
        course.isLab = request.isLab;
        course.labBlockSize = resolveLabBlockSize(request.labBlockSize, course.labBlockSize);
        course.requiredRoomTags = request.requiredRoomTags != null ? request.requiredRoomTags : List.of();
        course.color = request.color;
        course.persist();
        return course;
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Course update(@PathParam("id") Long id, CourseRequest request) {
        Course course = Course.findById(id);
        if (course == null) {
            throw new jakarta.ws.rs.NotFoundException("Course not found: " + id);
        }
        course.subject = request.subject;
        course.teacher = request.teacherId != null ? Teacher.findById(request.teacherId) : null;
        course.studentGroup = request.studentGroupId != null ? StudentGroup.findById(request.studentGroupId) : null;
        course.totalHours = request.totalHours;
        course.isLab = request.isLab;
        course.labBlockSize = resolveLabBlockSize(request.labBlockSize, course.labBlockSize);
        course.requiredRoomTags = request.requiredRoomTags != null ? request.requiredRoomTags : List.of();
        course.color = request.color;
        return course;
    }

    /**
     * Batch update for the course-hours grid editor (TASK-356): saves every edited
     * row in a single transaction so validation runs once per save instead of once
     * per row. A null teacherId/studentGroupId means "unassign" - the grid's dropdown
     * editors offer an explicit blank option for that. labBlockSize is the one field
     * where null means "keep existing" instead: it's a defensive guard so that any
     * caller (present or future) which omits the field can never silently zero out an
     * existing lab/block course's block size.
     */
    @PUT
    @Path("/batch")
    @Transactional
    public List<Course> batchUpdate(List<CourseBatchRequest> requests) {
        List<Course> updated = new ArrayList<>();
        for (CourseBatchRequest request : requests) {
            Course course = Course.findById(request.id);
            if (course == null) {
                throw new jakarta.ws.rs.NotFoundException("Course not found: " + request.id);
            }
            course.subject = request.subject;
            course.teacher = request.teacherId != null ? Teacher.findById(request.teacherId) : null;
            course.studentGroup = request.studentGroupId != null ? StudentGroup.findById(request.studentGroupId) : null;
            course.totalHours = request.totalHours;
            course.isLab = request.isLab;
            course.labBlockSize = resolveLabBlockSize(request.labBlockSize, course.labBlockSize);
            course.requiredRoomTags = request.requiredRoomTags != null ? request.requiredRoomTags : List.of();
            course.color = request.color;
            updated.add(course);
        }
        return updated;
    }

    /** labBlockSize null means "keep existing" - see {@link #batchUpdate} javadoc. */
    private static int resolveLabBlockSize(Integer requested, int existing) {
        return requested != null ? requested : existing;
    }

    /**
     * Deletes the course, cascading to its generated {@link Lesson} rows first
     * (mirrors {@code TeacherResource#delete}'s TASK-345 cascade precedent):
     * a course with lessons has a Lesson.course FK pointing at it, so deleting
     * the course row alone violates that FK and 500s. Deletes are idempotent
     * for a non-existent course id (matches the prior no-cascade behavior).
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") Long id) {
        Lesson.delete("course.id", id);
        Course.deleteById(id);
    }

    @POST
    @Path("/{id}/generate-lessons")
    public GenerateLessonsResponse generateLessons(@PathParam("id") Long id) {
        int count = lessonGenerator.generateForCourse(id);
        return new GenerateLessonsResponse(count);
    }

    public static class CourseRequest {
        public String subject;
        public Long teacherId;
        public Long studentGroupId;
        public int totalHours;
        public boolean isLab;
        public Integer labBlockSize;
        public List<String> requiredRoomTags;
        public String color;
    }

    public static class CourseBatchRequest extends CourseRequest {
        public Long id;
    }

    public record GenerateLessonsResponse(int lessonsGenerated) {}
}
