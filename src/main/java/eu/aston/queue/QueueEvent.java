package eu.aston.queue;

import java.util.HashMap;
import java.util.Map;

public class QueueEvent {
    private String id;

    private String method;
    private String path;
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private final long created = System.currentTimeMillis();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public long getCreated() {
        return created;
    }
}
