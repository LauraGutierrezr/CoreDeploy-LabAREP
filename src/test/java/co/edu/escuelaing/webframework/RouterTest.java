package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    @Test
    void findsRegisteredRouteByExactPath() {
        Router router = new Router();
        HttpService handler = (req, resp) -> "ok";
        router.addGet("/hello", handler);

        assertTrue(router.hasRoute("/hello"));
        assertEquals(handler, router.find("/hello"));
    }

    @Test
    void returnsNullForUnregisteredPath() {
        Router router = new Router();
        assertNull(router.find("/not-registered"));
        assertFalse(router.hasRoute("/not-registered"));
    }

    @Test
    void rejectsPathsNotStartingWithSlash() {
        Router router = new Router();
        assertThrows(IllegalArgumentException.class, () -> router.addGet("hello", (req, resp) -> "ok"));
    }

    @Test
    void rejectsNullHandler() {
        Router router = new Router();
        assertThrows(IllegalArgumentException.class, () -> router.addGet("/hello", null));
    }

    @Test
    void laterRegistrationOverridesEarlierOneForSamePath() {
        Router router = new Router();
        router.addGet("/x", (req, resp) -> "first");
        router.addGet("/x", (req, resp) -> "second");
        assertEquals("second", router.find("/x").handle(null, null));
    }
}
