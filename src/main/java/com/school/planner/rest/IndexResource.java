package com.school.planner.rest;

import com.school.planner.util.ContentTypeUtil;

import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.*;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Serves the index.html page with proper HTTP header ordering.
 *
 * <p>This works around a Vert.x FileServer bug where body content can be sent
 * before HTTP headers, causing curl "HTTP/0.9 when not allowed" errors.</p>
 *
 * <p>By serving index.html through JAX-RS with @Blocking, we ensure headers
 * are written before the body.</p>
 *
 * <p>Supports both dev mode (file system paths) and production mode (classpath resources).</p>
 */
@jakarta.ws.rs.Path("/")
@ApplicationScoped
public class IndexResource {

    private static final Logger LOG = Logger.getLogger(IndexResource.class);
    private static final String DEV_INDEX_PATH = "target/quinoa/build/index.html";
    private static final String DEV_ASSETS_PATH = "target/quinoa/build/assets/";

    @GET
    @Blocking
    public Response getIndex(@Context UriInfo uriInfo, @Context HttpHeaders httpHeaders) {
        LOG.info("IndexResource.getIndex() called - Host: " + httpHeaders.getHeaderString("Host"));
        return serveIndexHtml();
    }

    /**
     * Serve index.html for all SPA routes (React Router handles client-side routing).
     * This is needed because quarkus.quinoa.enable-spa-routing=false to fix HTTP/0.9 bug.
     * The path param catches everything except /api/* and /q/* (excluded by regex).
     */
    @GET
    @Path("/{path:((?!api|q).)*$}")
    @Blocking
    public Response getSpaRoute() {
        return serveIndexHtml();
    }

    private Response serveIndexHtml() {
        // Try classpath first (production JAR mode)
        InputStream classPathStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("META-INF/resources/index.html");

        if (classPathStream != null) {
            try {
                String indexHtml = new String(classPathStream.readAllBytes(), StandardCharsets.UTF_8);
                LOG.info("Served index.html from classpath (production mode)");
                return Response.ok(indexHtml, MediaType.TEXT_HTML_TYPE)
                        .header("Cache-Control", "public, immutable, max-age=86400")
                        .build();
            } catch (IOException e) {
                LOG.errorf("Failed to read index.html from classpath: %s", e.getMessage());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("<html><body>Error loading page</body></html>")
                        .type(MediaType.TEXT_HTML_TYPE)
                        .build();
            } finally {
                try {
                    classPathStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        // Fall back to file system (dev mode)
        try {
            java.nio.file.Path indexPath = Paths.get(DEV_INDEX_PATH);

            if (!Files.exists(indexPath)) {
                LOG.warnf("Index file not found at: %s (classpath also failed)", indexPath.toAbsolutePath());
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("<html><body>Index file not found. Run frontend build first.</body></html>")
                        .type(MediaType.TEXT_HTML_TYPE)
                        .build();
            }

            String indexHtml = Files.readString(indexPath);
            LOG.info("Served index.html from file system (dev mode)");

            return Response.ok(indexHtml, MediaType.TEXT_HTML_TYPE)
                    .header("Cache-Control", "public, immutable, max-age=86400")
                    .build();

        } catch (IOException e) {
            LOG.errorf("Failed to read index.html from file system: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body>Error loading page</body></html>")
                    .type(MediaType.TEXT_HTML_TYPE)
                    .build();
        }
    }

    /**
     * Serve static assets (JS, CSS, fonts) from Quinoa build directory.
     * Supports both classpath resources (production) and file system (dev).
     */
    @GET
    @Path("/assets/{path:.*}")
    @Blocking
    public Response getAsset(@Context UriInfo uriInfo) {
        String path = uriInfo.getPathParameters().getFirst("path");
        String classPathResource = "META-INF/resources/assets/" + path;

        // Try classpath first (production JAR mode)
        InputStream classPathStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(classPathResource);

        if (classPathStream != null) {
            try {
                byte[] content = classPathStream.readAllBytes();
                String contentType = ContentTypeUtil.getContentType(path);
                LOG.debugf("Served asset from classpath: %s", path);
                return Response.ok(content, contentType)
                        .header("Cache-Control", "public, immutable, max-age=31536000")
                        .build();
            } catch (IOException e) {
                LOG.errorf("Failed to read asset from classpath: %s", e.getMessage());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Error loading asset")
                        .build();
            } finally {
                try {
                    classPathStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        // Fall back to file system (dev mode)
        String fullPath = DEV_ASSETS_PATH + path;

        try {
            java.nio.file.Path assetPath = Paths.get(fullPath);

            if (!Files.exists(assetPath) || !Files.isRegularFile(assetPath)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Asset not found: " + path)
                        .build();
            }

            byte[] content = Files.readAllBytes(assetPath);
            String contentType = ContentTypeUtil.getContentType(path);
            LOG.debugf("Served asset from file system: %s", path);

            return Response.ok(content, contentType)
                    .header("Cache-Control", "public, immutable, max-age=31536000")
                    .build();

        } catch (IOException e) {
            LOG.errorf("Failed to read asset from file system: %s", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error loading asset")
                    .build();
        }
    }
}
