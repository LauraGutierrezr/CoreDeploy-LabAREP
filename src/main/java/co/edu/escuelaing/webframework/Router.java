package co.edu.escuelaing.webframework;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the table of GET routes registered by the application, in
 * registration order. This is intentionally simple: exact-path matching
 * only (no path variables/wildcards), which is all the framework's
 * contract ({@code get(path, lambda)}) promises.
 *
 * <p>Package-private: applications never touch this directly, only
 * through {@link WebFramework#get(String, HttpService)}.
 */
final class Router {

    private final Map<String, HttpService> getRoutes = new LinkedHashMap<>();

    void addGet(String path, HttpService service) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Route path must start with '/': " + path);
        }
        if (service == null) {
            throw new IllegalArgumentException("Route handler for " + path + " must not be null");
        }
        getRoutes.put(path, service);
    }

    /** Returns the handler registered for this exact path, or {@code null} if there is none. */
    HttpService find(String path) {
        return getRoutes.get(path);
    }

    boolean hasRoute(String path) {
        return getRoutes.containsKey(path);
    }
}
