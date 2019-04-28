package org.forkjoin.scrat.thproxy.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.forkjoin.scrat.thproxy.core.MessageType;
import org.forkjoin.scrat.thproxy.core.RegMessage;
import org.forkjoin.scrat.thproxy.core.RequestMessage;
import org.forkjoin.scrat.thproxy.core.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 代理管理类
 */
@Component
public class ProxyManager {
    private static final Logger log = LogManager.getLogger();

    private ConcurrentMap<String, WebSocketSession> sessionConcurrentMap = new ConcurrentHashMap<>();
    private ConcurrentMap<String, String> sessionIdConcurrentMap = new ConcurrentHashMap<>();

    @Autowired
    private ObjectMapper objectMapper;

    public void reg(String id, WebSocketSession session) {
        log.info("reg[id:{},session:{}]", id, session);
        WebSocketSession old = sessionConcurrentMap.put(id, session);
        sessionIdConcurrentMap.put(session.getId(), session.getId());
        if (old != null) {
            log.info("regCloseOld[id:{},session:{}]", id, old);
            old.close().subscribe();
        }
    }

    public void unreg(WebSocketSession session) {
        String id = sessionIdConcurrentMap.remove(session.getId());
        log.info("unreg[id:{},session:{}]", id, session);
        if (id != null) {
            sessionConcurrentMap.remove(id);
        }
    }

    public void handler(String json, WebSocketSession session) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);

            log.info("handler[json:{},session:{}]", json, session);

            String type = jsonNode.get("type").asText();
            JsonNode data = jsonNode.get("data");
            if (MessageType.REG.name().equals(type)) {
                RegMessage regMessage = objectMapper.treeToValue(data, RegMessage.class);
                reg(regMessage.getId(), session);
            } else if (MessageType.RESPONSE.name().equals(type)) {
                ResponseMessage responseMessage = objectMapper.treeToValue(data, ResponseMessage.class);
                handlerResponse(json, session, responseMessage);
            }

        } catch (IOException e) {
            log.error("handler error", e);
        }
    }

    /**
     * 注意request 和response 通过 requestId关联
     * <p>
     * 处理response，并且通过monoSink 发送到指定请求
     */
    private void handlerResponse(String json, WebSocketSession session, ResponseMessage responseMessage) {
        @SuppressWarnings("unchecked")
        MonoSink<ResponseEntity> monoSink = (MonoSink<ResponseEntity>) session.getAttributes().get(responseMessage.getRequestId());
        if (monoSink != null) {

            byte[] bodyBytes = null;
            String body = responseMessage.getBody();
            if (body != null) {
                bodyBytes = Base64.getDecoder().decode(body);
            }

            MultiValueMap<String, String> headersMaps = new LinkedMultiValueMap<>();
            if (!CollectionUtils.isEmpty(responseMessage.getHeaders())) {
                responseMessage.getHeaders().forEach(e -> {
                    headersMaps.add(e.getKey(), e.getValue());
                });
            }
            ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(bodyBytes, headersMaps, HttpStatus.valueOf(responseMessage.getStatus()));
            monoSink.success(responseEntity);
            monoSink.onDispose(() -> {
                log.info("handler monoSink success[responseEntity:{}]", responseEntity);
            });
        } else {
            log.info("handler notFound monoSink[json:{}]", json);
        }
    }

    /**
     * proxy 代理请求，把请求根据id适配到指定的websocket session
     * 然后通过 responseEntityMonoSink 等待响应到达
     * 注意requestId
     */
    public Mono<ResponseEntity> proxy(String id, String method, String uri, byte[] body, String host, int port, HttpHeaders headers) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        WebSocketSession webSocketSession = sessionConcurrentMap.get(id);
        if (webSocketSession != null) {
            RequestMessage requestMessage = new RequestMessage();
            if (body != null) {
                requestMessage.setBody(Base64.getEncoder().encodeToString(body));
            }
            requestMessage.setRequestId(requestId);
            requestMessage.setUri(uri);
            requestMessage.setMethod(method);
            requestMessage.setHost(host);
            requestMessage.setPort(port);

            List<Map.Entry<String, String>> headersList = transformHeader(headers);
            requestMessage.setHeaders(headersList);

            log.info("proxy[id:{},requestId:{},method:{},uri:{},body:{},host:{},port:{}]", id, requestId, method, uri, body, host, port);

            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put("type", MessageType.REQUEST.name());
            objectNode.putPOJO("data", requestMessage);
            try {
                String json = objectMapper.writeValueAsString(objectNode);
                webSocketSession.send(Mono.just(webSocketSession.textMessage(json))).subscribe();
                return Mono.create(responseEntityMonoSink -> webSocketSession.getAttributes().put(requestId, responseEntityMonoSink));
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }
        } else {
            log.info("not found session[id:{},method:{},uri:{},body:{},host:{},port:{}]", id, method, uri, body, host, port);
            return Mono.just(ResponseEntity.notFound().build());
        }
    }

    private List<Map.Entry<String, String>> transformHeader(HttpHeaders headers) {
        List<Map.Entry<String, String>> headersList = new ArrayList<>();
        headers.forEach((k, list) -> {
            list.forEach(v -> {
                headersList.add(new AbstractMap.SimpleImmutableEntry<>(k, v));
            });
        });
        return headersList;
    }
}
