package io.github.jacksonvieira.ratelimiter.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MetricsConfiguration {

    @Bean
    RateLimitObserver rateLimitObserver(MeterRegistry registry) {
        return new MicrometerRateLimitObserver(registry);
    }
}
