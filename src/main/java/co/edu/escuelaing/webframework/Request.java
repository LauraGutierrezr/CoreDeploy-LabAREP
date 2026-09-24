package co.edu.escuelaing.webframework;

import java.util.Collections;
import java.util.Map;

/**
 * The framework's view of an incoming HTTP request: the path that was
 * requested, plus whatever query-string parameters came with it.
 *
 * <p>Instances are immutable and built once per request by {@link HttpServer}
 * before a matching {@link HttpService} lambda is invoked.
 */
public final class Request {

    private final String path;
    private final Map<String, String> queryParams;

    public Request(String path, Map<String, String> queryParams) {
        this.path = path;
        this.queryParams = queryParams == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(queryParams);
    }

    /** The request path, e.g. {@code "/hello"} (query string excluded). */
    public String getPath() {
        return path;
    }

    /**
     * Returns the value of a single query-string parameter, or {@code null}
     * if it was not present. This is the primary way application lambdas
     * read request input, e.g. {@code req.getValue("name")} for a request
     * to {@code /hello?name=Ana}.
     */
    public String getValue(String name) {
        return queryParams.get(name);
    }

    /** Same as {@link #getValue(String)}, but returns {@code defaultValue} instead of {@code null}. */
    public String getValue(String name, String defaultValue) {
        String value = queryParams.get(name);
        return value != null ? value : defaultValue;
    }

    /** All query-string parameters, as an unmodifiable map. */
    public Map<String, String> getQueryParams() {
        return queryParams;
    }
}
