package co.edu.escuelaing.webframework;

/**
 * The framework's view of an outgoing HTTP response. A fresh instance is
 * handed to every {@link HttpService} lambda so it can adjust the status
 * code and content type before returning the response body as a String.
 *
 * <p>Defaults to {@code 200 OK} with {@code text/plain; charset=utf-8},
 * so a handler that only cares about the body can ignore this entirely.
 */
public final class Response {

    private int status = 200;
    private String contentType = "text/plain; charset=utf-8";

    /** Sets the HTTP status code. Returns {@code this} so calls can be chained. */
    public Response status(int status) {
        this.status = status;
        return this;
    }

    /** Sets the {@code Content-Type} header value. Returns {@code this} so calls can be chained. */
    public Response type(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }
}
