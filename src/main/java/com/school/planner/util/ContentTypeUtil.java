package com.school.planner.util;

/**
 * Utility class for determining HTTP Content-Type based on file extension.
 */
public final class ContentTypeUtil {

    private ContentTypeUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns the appropriate Content-Type header value for a given filename.
     *
     * @param filename The filename to determine content type from
     * @return The MIME type string (e.g., "application/javascript", "text/css")
     */
    public static String getContentType(String filename) {
        if (filename.endsWith(".js")) {
            return "application/javascript";
        } else if (filename.endsWith(".css")) {
            return "text/css";
        } else if (filename.endsWith(".woff")) {
            return "font/woff";
        } else if (filename.endsWith(".woff2")) {
            return "font/woff2";
        } else if (filename.endsWith(".ttf")) {
            return "font/ttf";
        } else if (filename.endsWith(".svg")) {
            return "image/svg+xml";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
