package co.edu.escuelaing.webframework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The actual network layer: one {@link ServerSocket}, one sequential
 * {@code accept()} loop. Exactly like the Networking Lab's server, this
 * server never spawns a thread per connection — each request is read,
 * dispatched and answered completely before the next one is accepted.
 * That makes request handling trivially easy to reason about, at the cost
 * of one slow client blocking everyone behind it (an explicit, documented
 * trade-off, not an oversight).
 *
 * <p>Request resolution order for every path: a registered lambda route
 * first, then a static file, then {@code 404}. Any exception thrown by a
 * route lambda or while reading a static file is caught and turned into a
 * {@code 500} response instead of crashing the accept loop.
 */
final class HttpServer {

    private final Router router;
    private final StaticFileService staticFileService;
    private final int port;

    private volatile ServerSocket serverSocket;

    HttpServer(Router router, StaticFileService staticFileService, int port) {
        this.router = router;
        this.staticFileService = staticFileService;
        this.port = port;
    }

    /** Binds the server socket and runs the accept loop until {@link #stop()} is called. Blocks the caller. */
    void start() throws IOException {
        try (ServerSocket socket = new ServerSocket(port)) {
            this.serverSocket = socket;
            System.out.println("Lambda Web Framework listening on port " + port);
            if (staticFileService.isFilesystemMode()) {
                System.out.println("Serving static files from filesystem: " + staticFileService.getFilesystemRoot());
            } else {
                System.out.println("Serving static files from classpath: " + staticFileService.getClasspathRoot());
            }
            acceptLoop(socket);
        } finally {
            this.serverSocket = null;
        }
    }

    /** Stops the accept loop. Safe to call from within a route handler that is currently answering a request. */
    void stop() {
        ServerSocket socket = this.serverSocket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing an already-idle server socket cannot meaningfully fail
                // in a way the caller could act on.
            }
        }
    }

    private void acceptLoop(ServerSocket socket) {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                handleConnection(client);
            } catch (SocketException closedWhileAccepting) {
                break; // stop() closed the socket while we were blocked in accept()
            } catch (IOException e) {
                if (socket.isClosed()) {
                    break;
                }
                System.err.println("Error handling connection: " + e.getMessage());
            }
        }
        System.out.println("Lambda Web Framework stopped.");
    }

    private void handleConnection(Socket client) throws IOException {
        client.setSoTimeout(10_000);
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = client.getOutputStream();

        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isBlank()) {
            return; // client closed the connection without sending anything usable
        }
        consumeHeaders(in);

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            HttpResponses.sendText(out, 400, "Bad Request", "Malformed request line");
            return;
        }
        String method = parts[0];
        String target = parts[1];
        if (!"GET".equals(method)) {
            HttpResponses.sendText(out, 405, "Method Not Allowed", "Only GET is supported");
            return;
        }

        dispatch(out, target);
    }

    private void consumeHeaders(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            // Headers are not needed by this framework's routing or static
            // serving, so they are read (to drain the socket correctly) and
            // discarded.
        }
    }

    private void dispatch(OutputStream out, String target) throws IOException {
        String path = target;
        Map<String, String> queryParams = Map.of();
        int questionMark = target.indexOf('?');
        if (questionMark >= 0) {
            path = target.substring(0, questionMark);
            queryParams = parseQueryParams(target.substring(questionMark + 1));
        }

        HttpService route = router.find(path);
        if (route != null) {
            dispatchRoute(out, route, path, queryParams);
            return;
        }

        StaticFileService.StaticFile file = staticFileService.resolve(path);
        if (file != null) {
            HttpResponses.sendBytes(out, 200, "OK", file.contentType, file.content);
            return;
        }

        HttpResponses.sendText(out, 404, "Not Found", "No route or static file matches " + path);
    }

    private void dispatchRoute(OutputStream out, HttpService route, String path, Map<String, String> queryParams) throws IOException {
        Request request = new Request(path, queryParams);
        Response response = new Response();
        try {
            String body = route.handle(request, response);
            byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
            HttpResponses.sendBytes(out, response.getStatus(), reasonFor(response.getStatus()), response.getContentType(), bytes);
        } catch (Exception handlerFailure) {
            System.err.println("Route handler for " + path + " failed: " + handlerFailure);
            HttpResponses.sendText(out, 500, "Internal Server Error", "The handler for " + path + " threw an exception");
        }
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            return value;
        }
    }

    private static String reasonFor(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "OK";
        };
    }
}
