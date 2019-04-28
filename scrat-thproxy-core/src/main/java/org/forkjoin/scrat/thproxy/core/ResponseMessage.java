package org.forkjoin.scrat.thproxy.core;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ResponseMessage implements Message{
    private String requestId;

    private String body;
    private int status;

    private List<Map.Entry<String, String>> headers;
}
