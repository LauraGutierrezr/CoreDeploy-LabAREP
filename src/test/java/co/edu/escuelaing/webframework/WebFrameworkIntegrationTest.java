package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test through the real public API: {@code get()}, {@code start(port)}
 * and {@code stop()}, hitting the server over an actual socket exactly like a
 * browser or curl would. This is the same style of verification used for the
 * Networking Lab's sequential server, adapted to the lambda-route framework.
 */
class WebFrameworkIntegrationTest {

    @Test
    void registeredRouteAndUnknownPathBehaveCorrectlyOverHttp() throws Exception {
        int port = findFreePort();

        WebFramework.get("/ping", (req, resp) -> "pong");
        WebFramework.get("/echo", (req, resp) -> {
            String value = req.getValue("value", "none");
            resp.type("text/plain; charset=utf-8");
            return "echo:" + value;
        });
        WebFramework.get("/boom", (req, resp) -> {
            throw new RuntimeException("simulated handler failure");
        });

        Thread serverThread = new Thread(() -> WebFramework.start(port));
        serverThread.setDaemon(true);
        serverThread.start();
        waitForPortToAcceptConnections(port);

        try {
            assertEquals("pong", get("http://127.0.0.1:" + port + "/ping"));
            assertEquals("echo:hola", get("http://127.0.0.1:" + port + "/echo?value=hola"));
            assertEquals("echo:none", get("http://127.0.0.1:" + port + "/echo"));

            HttpURLConnection notFound = openGet("http://127.0.0.1:" + port + "/does-not-exist");
            assertEquals(404, notFound.getResponseCode());

            HttpURLConnection serverError = openGet("http://127.0.0.1:" + port + "/boom");
            assertEquals(500, serverError.getResponseCode());
        } finally {
            HttpURLConnection shutdownTrigger = openGet("http://127.0.0.1:" + port + "/ping");
            shutdownTrigger.disconnect();
            WebFramework.stop();
            serverThread.join(5_000);
            assertTrue(!serverThread.isAlive(), "server thread should stop after WebFramework.stop()");
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = openGet(url);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HttpURLConnection openGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        return connection;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPortToAcceptConnections(int port) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket probe = new java.net.Socket("127.0.0.1", port)) {
                return;
            } catch (IOException notReadyYet) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("Server did not start listening on port " + port + " in time");
    }
}
