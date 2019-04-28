package org.forkjoin.scrat.thproxy.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class RegWebSocketHandler implements WebSocketHandler {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    private ProxyManager proxyManager;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<Void> output = session.receive()
                .doOnError(e -> {
                    proxyManager.unreg(session);
                })
                .flatMap(m -> {
                    try {
                        String json = m.getPayloadAsText(StandardCharsets.UTF_8);
                        proxyManager.handler(json, session);
                        return Mono.empty();
                    } catch (Throwable th) {
                        return Mono.error(th);
                    }
                });

        return output.then();
    }
}
