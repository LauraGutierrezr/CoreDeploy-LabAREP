package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentTypesTest {

    @Test
    void resolvesKnownExtensions() {
        assertEquals("text/html; charset=utf-8", ContentTypes.forFileName("index.html"));
        assertEquals("text/javascript; charset=utf-8", ContentTypes.forFileName("app.js"));
        assertEquals("image/png", ContentTypes.forFileName("logo.png"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals("image/jpeg", ContentTypes.forFileName("PHOTO.JPG"));
    }

    @Test
    void returnsNullForUnknownOrMissingExtension() {
        assertNull(ContentTypes.forFileName("no-extension"));
        assertNull(ContentTypes.forFileName("archive.zip"));
        assertNull(ContentTypes.forFileName("trailing-dot."));
    }
}
