package eu.aston.header;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.micronaut.http.HttpHeaders;

public class HeaderConverter {

    public static final String H_ID = "fw-event-id";
    public static final String H_STATUS = "fw-status";
    public static final String H_CALLBACK_URL = "fw-callback";
    public static final String H_CALLBACK_PREFIX = "fw-callback-";
    public static final Set<String> headerNamesRequest = Set.of("authorization", "x-api-key", "content-type", "content-encoding");

    public static Map<String, String> eventRequest(HttpHeaders headers, String id) {
        Map<String, String> map = new HashMap<>();
        headers.forEach((k,v)->{
            if(k.startsWith("fw-") || k.startsWith("x-b3-")){
                    map.put(k, v.getFirst());
            } else if(headerNamesRequest.contains(k)){
                map.put(k, v.getFirst());
            }
        });
        map.put(H_ID, id);
        return map;
    }
}
