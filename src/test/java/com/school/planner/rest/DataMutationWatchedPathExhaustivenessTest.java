package com.school.planner.rest;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustiveness guard for {@link DataMutationTrackingFilter} (TASK-354 code review B6).
 *
 * <p>Scans every class in {@code com.school.planner.rest} (a plain directory/classpath
 * scan — this project has no Jandex/ClassGraph dependency to do it more cleverly) for
 * JAX-RS methods annotated with a mutating verb (POST/PUT/DELETE/PATCH), resolves each
 * method's full path (class {@code @Path} + method {@code @Path}), and asserts it is
 * covered by either {@link DataMutationTrackingFilter#WATCHED_PATH_PREFIXES} or
 * {@link DataMutationTrackingFilter#WATCHED_EXCLUSIONS} — so a newly added mutating
 * endpoint can't silently slip past the watchdog without a deliberate decision either way.
 *
 * <p>Only scans classes carrying a class-level {@code @Path} (i.e. conventional JAX-RS
 * resources); a handful of classes in this package ({@code DataMutationTrackingFilter}
 * itself, {@code PreAssignmentExceptionMapper}, {@code RootPathHandler}) aren't resources
 * at all and are skipped. {@code IndexResource} has no class-level {@code @Path} either
 * but only declares {@code @GET} methods, so it never reaches the mutating check.
 */
class DataMutationWatchedPathExhaustivenessTest {

    private static final String REST_PACKAGE = "com.school.planner.rest";

    @Test
    void everyMutatingEndpointIsWatchedOrExplicitlyExcluded() throws Exception {
        List<String> uncovered = new ArrayList<>();

        for (Class<?> clazz : findRestClasses()) {
            Path classPathAnn = clazz.getAnnotation(Path.class);
            if (classPathAnn == null) continue; // not a conventional JAX-RS resource

            for (Method method : clazz.getDeclaredMethods()) {
                if (!isMutating(method)) continue;

                Path methodPathAnn = method.getAnnotation(Path.class);
                String fullPath = normalize(classPathAnn.value()
                        + (methodPathAnn != null ? methodPathAnn.value() : ""));

                boolean watched = DataMutationTrackingFilter.WATCHED_PATH_PREFIXES.stream()
                        .anyMatch(fullPath::startsWith);
                boolean excluded = DataMutationTrackingFilter.WATCHED_EXCLUSIONS.stream()
                        .anyMatch(fullPath::startsWith);

                if (!watched && !excluded) {
                    uncovered.add(clazz.getSimpleName() + "#" + method.getName() + " -> " + fullPath);
                }
            }
        }

        assertTrue(uncovered.isEmpty(),
                "Mutating JAX-RS endpoints not covered by WATCHED_PATH_PREFIXES nor WATCHED_EXCLUSIONS "
                        + "(add a path prefix to one of those lists in DataMutationTrackingFilter): " + uncovered);
    }

    private static boolean isMutating(Method m) {
        return m.isAnnotationPresent(POST.class) || m.isAnnotationPresent(PUT.class)
                || m.isAnnotationPresent(DELETE.class) || m.isAnnotationPresent(PATCH.class);
    }

    private static String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    /** Directory-based classpath scan of {@link #REST_PACKAGE} (works for {@code mvn test}'s exploded classes dir). */
    private static List<Class<?>> findRestClasses() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String packagePath = REST_PACKAGE.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(packagePath);
        while (resources.hasMoreElements()) {
            File dir = toDirectory(resources.nextElement());
            if (dir == null || !dir.isDirectory()) continue;
            File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
            if (files == null) continue;
            for (File f : files) {
                String simpleName = f.getName().substring(0, f.getName().length() - ".class".length());
                if (simpleName.contains("$")) continue; // skip inner/anonymous classes
                classes.add(Class.forName(REST_PACKAGE + "." + simpleName));
            }
        }
        return classes;
    }

    private static File toDirectory(URL url) throws URISyntaxException {
        if (!"file".equals(url.getProtocol())) return null; // packaged-jar case: not exercised by `mvn test`
        return new File(url.toURI());
    }
}
