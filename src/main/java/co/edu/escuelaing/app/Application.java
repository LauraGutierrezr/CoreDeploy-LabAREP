package co.edu.escuelaing.app;

import co.edu.escuelaing.webframework.WebFramework;

/**
 * Demo application built on top of the lambda web framework. It registers
 * two data routes ({@code /hello} and {@code /pi}), serves the static
 * single-page front end from {@code src/main/resources/webroot}, and, only
 * outside production, exposes a {@code /shutdown} route used to stop the
 * server cleanly during local development and testing.
 *
 * <h2>Environment variables</h2>
 * <ul>
 *   <li>{@code PORT} — port to listen on (default {@code 8080}).</li>
 *   <li>{@code GREETING_PREFIX} — text prepended to the {@code /hello}
 *       response (default {@code "Hello"}), e.g. set to {@code "Hola"} for
 *       a Spanish greeting without touching any code.</li>
 *   <li>{@code APP_ENV} — set to {@code production} to disable the
 *       {@code /shutdown} route before deploying publicly.</li>
 *   <li>{@code STATIC_FILES_PATH} — optional filesystem directory to serve
 *       static files from instead of the packaged classpath resources.</li>
 * </ul>
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        WebFramework.staticfiles("/webroot");

        String greetingPrefix = System.getenv().getOrDefault("GREETING_PREFIX", "Hello");
        boolean isProduction = "production".equalsIgnoreCase(System.getenv("APP_ENV"));

        WebFramework.get("/hello", (req, resp) -> {
            String name = req.getValue("name");
            if (name == null || name.isBlank()) {
                name = "World";
            }
            resp.type("application/json; charset=utf-8");
            return "{\"message\": \"" + greetingPrefix + ", " + escapeJson(name) + "!\"}";
        });

        WebFramework.get("/pi", (req, resp) -> {
            resp.type("application/json; charset=utf-8");
            return "{\"value\": " + Math.PI + "}";
        });

        if (isProduction) {
            System.out.println("APP_ENV=production: the /shutdown route is disabled.");
        } else {
            WebFramework.get("/shutdown", (req, resp) -> {
                resp.type("application/json; charset=utf-8");
                WebFramework.stop();
                return "{\"status\": \"shutting down\"}";
            });
        }

        WebFramework.start();
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
