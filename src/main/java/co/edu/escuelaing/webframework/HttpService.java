package co.edu.escuelaing.webframework;

/**
 * A lambda-friendly contract for a route handler.
 *
 * <p>Applications register these with {@link WebFramework#get(String, HttpService)}:
 *
 * <pre>{@code
 * WebFramework.get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
 * }</pre>
 *
 * The single abstract method makes this a functional interface, so any
 * {@code (Request, Response) -> String} lambda satisfies it without the
 * caller ever needing to implement or name a class.
 */
@FunctionalInterface
public interface HttpService {

    /**
     * Produces the response body for one request.
     *
     * @param request  the incoming request (path + query parameters)
     * @param response the response being built; handlers may set the
     *                 status code and content type on it before returning
     * @return the response body to send back to the client
     */
    String handle(Request request, Response response);
}
