package org.forkjoin.scrat.thproxy.client.thproxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.forkjoin.scrat.thproxy.client.ThproxyClient;
import org.forkjoin.scrat.thproxy.core.ResponseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@SpringBootApplication
public class ThproxyClientApplication implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplicationBuilder(ThproxyClientApplication.class).build();
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }

    @Autowired
    private ObjectMapper objectMapper;


    @Override
    public void run(String... args) throws Exception {
        ThproxyClient client = new ThproxyClient();
        try {
            client.execute("abc", (requestMessage, webSocketSession) -> {
                ResponseMessage responseMessage = new ResponseMessage();
                responseMessage.setRequestId(requestMessage.getRequestId());
                responseMessage.setStatus(200);
                responseMessage.setBody(Base64.getEncoder().encodeToString("ok".getBytes(StandardCharsets.UTF_8)));
                return Mono.just(responseMessage);
            }, webSocketSession -> {
                webSocketSession.close().subscribe();
            }).block();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }
}
