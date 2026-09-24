package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Writes well-formed HTTP/1.1 responses to a socket's output stream. */
final class HttpResponses {

    private HttpResponses() {
    }

    static void sendText(OutputStream out, int status, String reason, String body) throws IOException {
        sendBytes(out, status, reason, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    static void sendBytes(OutputStream out, int status, String reason, String contentType, byte[] body) throws IOException {
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        headers.append("Content-Type: ").append(contentType).append("\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n");
        headers.append("\r\n");

        out.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }
}
