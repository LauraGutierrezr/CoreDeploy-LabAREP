package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Public facade of the framework and the only class application code needs
 * to import. Modeled after Spark's static-method style so a whole demo app
 * can be written as a handful of {@code get(...)} calls followed by
 * {@code start()}:
 *
 * <pre>{@code
 * WebFramework.staticfiles("/webroot");
 * WebFramework.get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
 * WebFramework.start();
 * }</pre>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li>{@code PORT} (env var) — port {@link #start()} binds to when the
 *       caller does not pass one explicitly. Defaults to {@code 8080}.</li>
 *   <li>{@code STATIC_FILES_PATH} (env var) — if set, static files are read
 *       from this directory on disk instead of from the classpath folder
 *       configured with {@link #staticfiles(String)}.</li>
 * </ul>
 *
 * <p>Routes and the static files root must be registered <em>before</em>
 * calling {@code start()}: once the accept loop is running, this is a
 * sequential server with nothing pulling requests off the socket to notice
 * a change.
 */
public final class WebFramework {

    private static final int DEFAULT_PORT = 8080;

    private static final Router router = new Router();
    private static String staticFilesClasspathRoot = "";
    private static HttpServer server;

    private WebFramework() {
    }

    /**
     * Registers a lambda to handle GET requests for an exact path.
     *
     * @param path    the request path, e.g. {@code "/hello"}; must start with {@code "/"}
     * @param service the handler; typically a lambda {@code (req, resp) -> body}
     */
    public static void get(String path, HttpService service) {
        router.addGet(path, service);
    }

    /**
     * Configures the classpath folder that static files (HTML, CSS, JS,
     * images) are served from when no {@code STATIC_FILES_PATH} override is
     * set. The folder must be on the classpath, e.g.
     * {@code src/main/resources/webroot} becomes {@code staticfiles("/webroot")}.
     */
    public static void staticfiles(String classpathFolder) {
        staticFilesClasspathRoot = classpathFolder;
    }

    /** Starts the server on the port from the {@code PORT} environment variable (default {@code 8080}). Blocks the caller. */
    public static void start() {
        start(resolvePort());
    }

    /** Starts the server on an explicit port, overriding {@code PORT}. Blocks the calling thread until {@link #stop()} is called. */
    public static void start(int port) {
        String filesystemOverride = System.getenv("STATIC_FILES_PATH");
        StaticFileService staticFileService = new StaticFileService(staticFilesClasspathRoot, filesystemOverride);
        server = new HttpServer(router, staticFileService, port);
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start server on port " + port, e);
        }
    }

    /**
     * Stops the accept loop, letting {@link #start()}/{@link #start(int)}
     * return. Safe to call from inside a route handler (e.g. a
     * {@code /shutdown} route): the response currently being written is not
     * affected, and the server simply does not accept a next connection.
     */
    public static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private static int resolvePort() {
        String fromEnv = System.getenv("PORT");
        if (fromEnv == null || fromEnv.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(fromEnv.trim());
        } catch (NumberFormatException invalid) {
            System.err.println("Ignoring invalid PORT value '" + fromEnv + "', using default " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }
}
