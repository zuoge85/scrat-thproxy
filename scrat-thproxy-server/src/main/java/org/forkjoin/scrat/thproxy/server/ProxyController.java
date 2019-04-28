package org.forkjoin.scrat.thproxy.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账号相关接口
 *
 * @author zuoge85 on 15/6/11.
 */
@RestController
public class ProxyController {
    private static final Logger log = LogManager.getLogger();

    @Autowired
    private ProxyManager proxyManager;

    @PostConstruct
    private void init() {
        Schedulers.onHandleError((t, e) -> {
            log.info("Schedulers Error[Thread:{},error:{}]", t, e);
        });
    }

    /**
     * 代理接口
     */
    @RequestMapping("/{id}/**")
    public Mono<ResponseEntity> proxy(
            @PathVariable String id,
            ServerHttpRequest request, @RequestBody(required = false) byte[] body
    ) {
        String uri = request.getURI().toString();
        String method = request.getMethodValue();
        String host = request.getRemoteAddress().getHostString();
        int port = request.getRemoteAddress().getPort();

        return proxyManager.proxy(id, method, uri, body, host, port, request.getHeaders());
    }
}