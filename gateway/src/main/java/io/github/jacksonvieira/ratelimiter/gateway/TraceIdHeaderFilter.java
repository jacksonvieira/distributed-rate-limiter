package io.github.jacksonvieira.ratelimiter.gateway;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Returns the id of the current trace in the {@code X-Trace-Id} response header, so clients can quote it. */
final class TraceIdHeaderFilter implements WebFilter, Ordered {

    static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    TraceIdHeaderFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Span span = tracer.currentSpan();
        if (span != null) {
            exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, span.context().traceId());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
