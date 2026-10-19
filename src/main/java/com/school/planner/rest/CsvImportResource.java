package com.school.planner.rest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import com.school.planner.domain.Course;
import com.school.planner.domain.SchoolHoliday;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;

@Path("/api/import")
@Produces(MediaType.APPLICATION_JSON)
public class CsvImportResource {

    @POST
    @Path("/{entityType}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public ImportResult importCsv(@PathParam("entityType") String entityType,
                                  @RestForm("file") FileUpload file) throws Exception {
        List<String> lines = readLines(file);
        return switch (entityType) {
            case "teachers" -> importTeachers(lines);
            case "groups" -> importGroups(lines);
            case "courses" -> importCourses(lines);
            case "holidays" -> importHolidays(lines);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    private List<String> readLines(FileUpload file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (InputStream is = java.nio.file.Files.newInputStream(file.uploadedFile());
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false; // skip header
                    continue;
                }
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }

    private ImportResult importTeachers(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String name = line.split(",")[0].trim();
            if (!name.isEmpty()) {
                new Teacher(name).persist();
                count++;
            }
        }
        return new ImportResult(count, "teachers");
    }

    private ImportResult importGroups(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String[] parts = line.split(",");
            String name = parts[0].trim();
            int size = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 20;
            if (!name.isEmpty()) {
                new StudentGroup(name, size).persist();
                count++;
            }
        }
        return new ImportResult(count, "student groups");
    }

    private ImportResult importCourses(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length < 2) continue;
            String subject = parts[0].trim();
            String teacherName = parts[1].trim();
            Teacher teacher = Teacher.find("name", teacherName).firstResult();
            if (teacher != null) {
                Course course = new Course(subject, teacher);
                if (parts.length > 2 && !parts[2].isBlank()) {
                    StudentGroup group = StudentGroup.find("name", parts[2].trim()).firstResult();
                    course.studentGroup = group;
                }
                if (parts.length > 3 && !parts[3].isBlank()) {
                    course.totalHours = Integer.parseInt(parts[3].trim());
                }
                if (parts.length > 4 && !parts[4].isBlank()) {
                    course.isLab = Boolean.parseBoolean(parts[4].trim());
                }
                if (parts.length > 5 && !parts[5].isBlank()) {
                    course.labBlockSize = Integer.parseInt(parts[5].trim());
                }
                course.persist();
                count++;
            }
        }
        return new ImportResult(count, "courses");
    }

    private ImportResult importHolidays(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String[] parts = line.split(",", 2);
            LocalDate date = LocalDate.parse(parts[0].trim());
            String description = parts.length > 1 ? parts[1].trim() : "";
            new SchoolHoliday(date, description).persist();
            count++;
        }
        return new ImportResult(count, "holidays");
    }

    public record ImportResult(int imported, String entityType) {}
}
