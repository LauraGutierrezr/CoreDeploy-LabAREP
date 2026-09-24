package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestTest {

    @Test
    void getValueReturnsQueryParam() {
        Request request = new Request("/hello", Map.of("name", "Ana"));
        assertEquals("Ana", request.getValue("name"));
    }

    @Test
    void getValueReturnsNullWhenParamMissing() {
        Request request = new Request("/hello", Map.of());
        assertNull(request.getValue("name"));
    }

    @Test
    void getValueWithDefaultFallsBackWhenMissing() {
        Request request = new Request("/hello", Map.of());
        assertEquals("World", request.getValue("name", "World"));
    }

    @Test
    void handlesNullQueryParamsMapGracefully() {
        Request request = new Request("/hello", null);
        assertNull(request.getValue("name"));
        assertEquals(Map.of(), request.getQueryParams());
    }
}
