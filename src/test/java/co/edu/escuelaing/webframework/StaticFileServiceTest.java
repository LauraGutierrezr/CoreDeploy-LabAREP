package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StaticFileServiceTest {

    // -- filesystem mode (STATIC_FILES_PATH override) --------------------

    @Test
    void filesystemModeServesIndexHtmlForRootPath(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("index.html"), "<h1>home</h1>");
        StaticFileService service = new StaticFileService("/webroot", tempDir.toString());

        StaticFileService.StaticFile file = service.resolve("/");

        assertNotNull(file);
        assertEquals("<h1>home</h1>", new String(file.content, StandardCharsets.UTF_8));
        assertEquals("text/html; charset=utf-8", file.contentType);
    }

    @Test
    void filesystemModeServesNestedFile(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/data.json"), "{\"ok\":true}");
        StaticFileService service = new StaticFileService("/webroot", tempDir.toString());

        StaticFileService.StaticFile file = service.resolve("/sub/data.json");

        assertNotNull(file);
        assertEquals("{\"ok\":true}", new String(file.content, StandardCharsets.UTF_8));
        assertEquals("application/json; charset=utf-8", file.contentType);
    }

    @Test
    void filesystemModeRejectsPathTraversalOutsideRoot(@TempDir Path tempDir) throws IOException {
        Path secretOutsideRoot = Files.writeString(tempDir.resolveSibling("secret-" + tempDir.getFileName() + ".txt"), "top secret");
        StaticFileService service = new StaticFileService("/webroot", tempDir.toString());

        StaticFileService.StaticFile file = service.resolve("/../" + secretOutsideRoot.getFileName());

        assertNull(file);
        Files.deleteIfExists(secretOutsideRoot);
    }

    @Test
    void filesystemModeReturnsNullForMissingFile(@TempDir Path tempDir) {
        StaticFileService service = new StaticFileService("/webroot", tempDir.toString());
        assertNull(service.resolve("/does-not-exist.html"));
    }

    @Test
    void filesystemModeReturnsNullForUnknownExtension(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("archive.zip"), "binary-ish");
        StaticFileService service = new StaticFileService("/webroot", tempDir.toString());
        assertNull(service.resolve("/archive.zip"));
    }

    // -- classpath mode (default, no STATIC_FILES_PATH) -------------------

    @Test
    void classpathModeServesResourceFile() {
        StaticFileService service = new StaticFileService("/webroot-test", null);

        StaticFileService.StaticFile file = service.resolve("/hello.txt");

        assertNotNull(file);
        assertEquals("hello from classpath\n", new String(file.content, StandardCharsets.UTF_8));
        assertEquals("text/plain; charset=utf-8", file.contentType);
    }

    @Test
    void classpathModeRejectsDotDotSegments() {
        StaticFileService service = new StaticFileService("/webroot-test", null);
        assertNull(service.resolve("/../hello.txt"));
    }

    @Test
    void classpathModeReturnsNullForMissingResource() {
        StaticFileService service = new StaticFileService("/webroot-test", null);
        assertNull(service.resolve("/nope.txt"));
    }
}
