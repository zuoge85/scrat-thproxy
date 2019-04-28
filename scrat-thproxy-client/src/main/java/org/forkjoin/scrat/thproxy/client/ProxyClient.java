package org.forkjoin.scrat.thproxy.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.forkjoin.scrat.thproxy.core.RequestMessage;
import org.forkjoin.scrat.thproxy.core.ResponseMessage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 实现转请求到本地
 */
public class ProxyClient {
    private static final Logger log = LogManager.getLogger();
    private WebClient client = WebClient.builder().build();

    private String url = "http://www.baidu.com/";
    private ObjectMapper objectMapper = new ObjectMapper();


    public Mono<ResponseMessage> proxy(String uri, RequestMessage requestMessage) {
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory();
        UriBuilder uriBuilder = uriBuilderFactory.uriString(url + uri);
        WebClient.RequestBodySpec requestBodySpec = client.method(HttpMethod.resolve(requestMessage.getMethod()))
                .uri(uriBuilder.build())
                .headers(httpHeaders -> {
                    requestMessage.getHeaders().forEach(e -> {
                        httpHeaders.add(e.getKey(), e.getValue());
                    });

                    //
                    String xFowardedFor = httpHeaders.getFirst("X-Forwarded-For");
                    if (StringUtils.isEmpty(xFowardedFor)) {
                        httpHeaders.set("X-Forwarded-For", requestMessage.getHost());
                    } else {
                        httpHeaders.set("X-Forwarded-For", xFowardedFor + ", " + requestMessage.getHost());
                    }
                });

        if (!StringUtils.isEmpty(requestMessage.getBody())) {
            byte[] datas = Base64.getDecoder().decode(requestMessage.getBody());
            requestBodySpec.syncBody(datas);
        }

        return requestBodySpec.exchange().flatMap(clientResponse -> {
            return clientResponse.bodyToMono(byte[].class).map(bytes -> {
                ResponseMessage responseMessage = new ResponseMessage();
                responseMessage.setRequestId(requestMessage.getRequestId());
                responseMessage.setStatus(clientResponse.rawStatusCode());
                if (bytes != null) {
                    responseMessage.setBody(Base64.getEncoder().encodeToString(bytes));
                }
                responseMessage.setHeaders(transformHeader(clientResponse.headers().asHttpHeaders()));
                return responseMessage;
            });
        });
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
