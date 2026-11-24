package io.github.jacksonvieira.ratelimiter.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        // Reactor's boundedElastic scheduler, used for the blocking limiter call, then runs on virtual threads.
        System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");
        SpringApplication.run(GatewayApplication.class, args);
    }
}
