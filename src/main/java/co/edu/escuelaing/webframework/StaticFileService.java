package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves a request path to a static resource's bytes and content type.
 *
 * <p>Two modes, chosen once at startup:
 *
 * <ul>
 *   <li><b>Filesystem mode</b> — used when the {@code STATIC_FILES_PATH}
 *       environment variable is set. Files are read from that directory on
 *       disk, so they can be edited/replaced without rebuilding the jar.
 *       Requests are contained to the configured root with
 *       {@link Path#normalize()} + a {@code startsWith} check.</li>
 *   <li><b>Classpath mode</b> (default) — files are read as classpath
 *       resources from the folder configured with
 *       {@link WebFramework#staticfiles(String)}, so a single packaged jar
 *       is self-contained. Any {@code ".."} path segment is rejected
 *       outright, since classpath resources have no {@code Path} to
 *       normalize against.</li>
 * </ul>
 */
final class StaticFileService {

    /** A resolved static resource: its bytes and the content type to serve them with. */
    static final class StaticFile {
        final byte[] content;
        final String contentType;

        StaticFile(byte[] content, String contentType) {
            this.content = content;
            this.contentType = contentType;
        }
    }

    private final String classpathRoot;
    private final Path filesystemRoot;

    StaticFileService(String classpathRoot, String filesystemOverride) {
        this.classpathRoot = normalizeClasspathRoot(classpathRoot);
        this.filesystemRoot = (filesystemOverride == null || filesystemOverride.isBlank())
                ? null
                : Paths.get(filesystemOverride).toAbsolutePath().normalize();
    }

    boolean isFilesystemMode() {
        return filesystemRoot != null;
    }

    Path getFilesystemRoot() {
        return filesystemRoot;
    }

    String getClasspathRoot() {
        return classpathRoot;
    }

    /**
     * Resolves a request path to its bytes and content type.
     *
     * @return the static file, or {@code null} if it does not exist, is not
     *         a regular readable file, escapes the configured root, or has
     *         an unrecognized extension
     */
    StaticFile resolve(String requestPath) {
        String decoded = decodePath(requestPath);
        if (decoded == null) {
            return null;
        }
        String effectivePath = decoded.equals("/") ? "/index.html" : decoded;
        return filesystemRoot != null
                ? resolveFromFilesystem(effectivePath)
                : resolveFromClasspath(effectivePath);
    }

    private StaticFile resolveFromFilesystem(String requestPath) {
        Path candidate = filesystemRoot.resolve(requestPath.substring(1)).normalize();
        if (!candidate.startsWith(filesystemRoot)) {
            return null; // path traversal attempt, e.g. ../../etc/passwd
        }
        if (!Files.isRegularFile(candidate) || !Files.isReadable(candidate)) {
            return null;
        }
        String contentType = ContentTypes.forFileName(candidate.getFileName().toString());
        if (contentType == null) {
            return null;
        }
        try {
            byte[] content = Files.readAllBytes(candidate);
            return new StaticFile(content, contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private StaticFile resolveFromClasspath(String requestPath) {
        if (containsTraversalSegment(requestPath)) {
            return null;
        }
        String contentType = ContentTypes.forFileName(requestPath);
        if (contentType == null) {
            return null;
        }
        String resourcePath = classpathRoot + requestPath;
        try (InputStream in = StaticFileService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            byte[] content = in.readAllBytes();
            return new StaticFile(content, contentType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean containsTraversalSegment(String path) {
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static String decodePath(String rawPath) {
        try {
            // URLDecoder is written for application/x-www-form-urlencoded (query
            // strings), where '+' means space. A URL *path* never encodes space
            // as '+', so it is protected here before delegating the rest of the
            // percent-decoding to URLDecoder.
            String protectedPlus = rawPath.replace("+", "%2B");
            return URLDecoder.decode(protectedPlus, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEscape) {
            return null;
        }
    }

    private static String normalizeClasspathRoot(String classpathRoot) {
        if (classpathRoot == null || classpathRoot.isBlank()) {
            return "";
        }
        String root = classpathRoot.startsWith("/") ? classpathRoot : "/" + classpathRoot;
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }
}
