package org.forkjoin.scrat.thproxy.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.forkjoin.scrat.thproxy.core.*;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ThproxyClient {
    private static final Logger log = LogManager.getLogger();
    private WebSocketClient client = new ReactorNettyWebSocketClient();
    private String url = "ws://localhost:8080/reg";
    private ObjectMapper objectMapper = new ObjectMapper();


    public Mono<Void> execute(String id,
                              BiFunction<RequestMessage, WebSocketSession, Mono<ResponseMessage>> handler,
                              Consumer<WebSocketSession> completeHandler
    ) throws URISyntaxException {
        log.info("connect start[url:{},id:{}]", url, id);
        return client.execute(new URI(url), session -> {
            log.info("connect ok[url:{},id:{}, sessionId:{}]", url, id, session.getId());
            Command command = new Command();
            command.setType(MessageType.REG);

            RegMessage regMessage = new RegMessage();
            regMessage.setId(id);

            command.setData(regMessage);
            try {
                Flux<WebSocketMessage> messageFlux = Flux.just(session.textMessage(objectMapper.writeValueAsString(command)));
                return session.send(messageFlux)
                        .thenMany(session.receive().map(WebSocketMessage::getPayloadAsText))
                        .flatMap(r -> handlerRequest(handler, session, r).doOnSuccess(v->{
                            //发送完毕
                            completeHandler.accept(session);
                        }))
                        .then();
            } catch (JsonProcessingException e) {
                return Mono.error(e);
            }
        });
    }

    private Mono<Void> handlerRequest(BiFunction<RequestMessage, WebSocketSession, Mono<ResponseMessage>> handler, WebSocketSession session, String r) {
        try {
            JsonNode requestCommand = objectMapper.readTree(r);
            String type = requestCommand.get("type").asText();
            if (MessageType.REQUEST.name().equals(type)) {
                log.info("request:{}", requestCommand);
                JsonNode data = requestCommand.get("data");
                RequestMessage requestMessage = objectMapper.treeToValue(data, RequestMessage.class);
                return handler.apply(requestMessage, session).flatMap(responseMessage -> {
                    Command responseCommand = new Command();
                    responseCommand.setType(MessageType.RESPONSE);
                    responseCommand.setData(responseMessage);
                    try {
                        log.info("response start:{}", responseCommand);
                        return session.send(Flux.just(session.textMessage(objectMapper.writeValueAsString(responseCommand))))
                                .doOnSuccess(v -> {
                                    log.info("response ok:{}", responseCommand);
                                });
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
            } else {
                return Mono.empty();
            }
        } catch (IOException e) {
            return Mono.error(e);
        }
    }
}
