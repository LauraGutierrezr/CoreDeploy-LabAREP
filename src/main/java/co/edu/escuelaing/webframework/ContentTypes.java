package co.edu.escuelaing.webframework;

import java.util.Locale;
import java.util.Map;

/**
 * Maps file extensions to MIME types for static file responses. A small,
 * hardcoded table is enough for a teaching framework and avoids pulling in
 * a dependency just to guess content types.
 */
final class ContentTypes {

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon")
    );

    private ContentTypes() {
    }

    /** Returns the MIME type for a file name's extension, or {@code null} if it is not recognized. */
    static String forFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return BY_EXTENSION.get(extension);
    }
}
