package com.school.planner.rest;

import com.school.planner.util.ContentTypeUtil;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Vert.x route handler that serves index.html with proper HTTP header ordering.
 *
 * <p>This handler is installed at startup with HIGHER priority (lower priority number)
 * than Vert.x StaticHandler, ensuring it intercepts root path requests first.
 * This prevents the StaticHandler race condition that causes "HTTP/0.9" errors.</p>
 *
 * <p>The @Blocking annotation ensures the file read and response are handled
 * synchronously on the worker thread, preventing the race condition where
 * body content is sent before headers.</p>
 *
 * <p>Supports both dev mode (file system paths) and production mode (classpath resources).
 * The serving mode is determined once at startup to avoid repeated classpath checks.</p>
 */
@Blocking
public class RootPathHandler {

    private static final Logger LOG = Logger.getLogger(RootPathHandler.class);
    private static final String CLASSPATH_INDEX = "META-INF/resources/index.html";
    private static final String CLASSPATH_ASSETS_PREFIX = "META-INF/resources/assets/";
    private static final String DEV_INDEX_PATH = "target/quinoa/build/index.html";
    private static final String DEV_ASSETS_PATH = "target/quinoa/build/assets/";

    @Inject
    Router router;

    // Cached at startup: whether to serve from classpath (production) or filesystem (dev)
    private volatile boolean useClasspathResources;

    void installRoutes(@Observes StartupEvent startupEvent) {
        LOG.info("Installing RootPathHandler with priority 10 (before StaticHandler)");

        // Determine serving mode ONCE at startup to avoid repeated classpath checks
        this.useClasspathResources = Thread.currentThread()
                .getContextClassLoader()
                .getResource(CLASSPATH_INDEX) != null;

        String servingMode = useClasspathResources ? "classpath (production mode)" : "file system (dev mode)";
        LOG.infof("RootPathHandler serving mode: %s", servingMode);

        // Install route for root path - runs BEFORE StaticHandler (priority 10 < 1000)
        router.route()
                .path("/")
                .order(10)  // Lower order = higher priority
                .handler(this::handleRoot);

        // Install routes for SPA paths - client-side routing
        router.route()
                .path("/admin/*")
                .order(10)
                .handler(this::handleSpaRoute);

        router.route()
                .path("/docs")
                .order(10)
                .handler(this::handleSpaRoute);

        router.route()
                .path("/complexity")
                .order(10)
                .handler(this::handleSpaRoute);

        router.route()
                .path("/phases")
                .order(10)
                .handler(this::handleSpaRoute);

        router.route()
                .path("/benchmarks")
                .order(10)
                .handler(this::handleSpaRoute);

        // Install route for static assets
        router.route()
                .path("/assets/*")
                .order(10)
                .handler(this::handleAsset);
    }

    private void handleRoot(RoutingContext rc) {
        LOG.infof("RootPathHandler.handleRoot: host=%s", rc.request().host());
        serveIndexHtml(rc);
    }

    private void handleSpaRoute(RoutingContext rc) {
        LOG.infof("RootPathHandler.handleSpaRoute: path=%s", rc.normalizedPath());
        serveIndexHtml(rc);
    }

    private void handleAsset(RoutingContext rc) {
        String path = rc.normalizedPath().substring("/assets/".length());
        serveAsset(rc, path);
    }

    private void serveIndexHtml(RoutingContext rc) {
        if (useClasspathResources) {
            // Serve from classpath (production JAR mode)
            try (InputStream classPathStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(CLASSPATH_INDEX)) {

                if (classPathStream == null) {
                    LOG.errorf("Classpath resource not found: %s", CLASSPATH_INDEX);
                    sendErrorResponse(rc);
                    return;
                }

                byte[] content = classPathStream.readAllBytes();
                sendHtmlResponse(rc, content, "classpath (production mode)");
            } catch (IOException e) {
                LOG.errorf("Failed to read index.html from classpath: %s", e.getMessage());
                sendErrorResponse(rc);
            }
        } else {
            // Serve from file system (dev mode)
            try {
                byte[] content = Files.readAllBytes(Paths.get(DEV_INDEX_PATH));
                sendHtmlResponse(rc, content, "file system (dev mode)");
            } catch (IOException e) {
                LOG.errorf("Failed to serve index.html from file system: %s", e.getMessage());
                sendErrorResponse(rc);
            }
        }
    }

    private void serveAsset(RoutingContext rc, String path) {
        if (useClasspathResources) {
            // Serve from classpath (production JAR mode)
            String classPathResource = CLASSPATH_ASSETS_PREFIX + path;
            try (InputStream classPathStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream(classPathResource)) {

                if (classPathStream == null) {
                    LOG.debugf("Classpath asset not found: %s", classPathResource);
                    sendAssetErrorResponse(rc, path);
                    return;
                }

                byte[] content = classPathStream.readAllBytes();
                String contentType = ContentTypeUtil.getContentType(path);
                sendAssetResponse(rc, content, contentType, path, "classpath (production mode)");
            } catch (IOException e) {
                LOG.errorf("Failed to read asset from classpath: %s", e.getMessage());
                sendAssetErrorResponse(rc, path);
            }
        } else {
            // Serve from file system (dev mode)
            try {
                byte[] content = Files.readAllBytes(Paths.get(DEV_ASSETS_PATH + path));
                String contentType = ContentTypeUtil.getContentType(path);
                sendAssetResponse(rc, content, contentType, path, "file system (dev mode)");
            } catch (IOException e) {
                LOG.errorf("Failed to serve asset from file system: %s", e.getMessage());
                sendAssetErrorResponse(rc, path);
            }
        }
    }

    private void sendHtmlResponse(RoutingContext rc, byte[] content, String source) {
        HttpServerResponse response = rc.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/html;charset=UTF-8")
                .putHeader("Cache-Control", "public, immutable, max-age=86400")
                .putHeader("Content-Length", String.valueOf(content.length));

        response.end(Buffer.buffer(content));
        LOG.infof("Served index.html from %s: %d bytes, host=%s", source, content.length, rc.request().host());
    }

    private void sendAssetResponse(RoutingContext rc, byte[] content, String contentType, String path, String source) {
        HttpServerResponse response = rc.response()
                .setStatusCode(200)
                .putHeader("Content-Type", contentType)
                .putHeader("Cache-Control", "public, immutable, max-age=31536000")
                .putHeader("Content-Length", String.valueOf(content.length));

        response.end(Buffer.buffer(content));
        LOG.debugf("Served asset from %s: %s (%d bytes)", source, path, content.length);
    }

    private void sendErrorResponse(RoutingContext rc) {
        rc.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "text/html;charset=UTF-8")
                .end("<html><body>Error loading page</body></html>");
    }

    private void sendAssetErrorResponse(RoutingContext rc, String path) {
        rc.response()
                .setStatusCode(404)
                .end("Asset not found: " + path);
    }
}
