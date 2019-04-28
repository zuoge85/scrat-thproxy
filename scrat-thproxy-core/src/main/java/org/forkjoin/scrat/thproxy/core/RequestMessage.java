package org.forkjoin.scrat.thproxy.core;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RequestMessage implements Message{
    private String requestId;

    private String uri;
    private String method;
    private String body;
    private String host;
    private int port;

    private List<Map.Entry<String, String>> headers;
}
