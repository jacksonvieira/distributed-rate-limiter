package io.github.jacksonvieira.ratelimiter.gateway;

import io.netty.handler.timeout.ReadTimeoutException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Forwards a request to the route's backend and streams the response back unchanged.
 *
 * <p>Bodies are streamed as {@link DataBuffer}s in both directions, so large payloads are never
 * held in memory. Hop-by-hop headers are dropped, as required of proxies by RFC 9110.
 */
public final class ProxyHandler {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host");

    private final GatewayProperties.Route route;
    private final WebClient webClient;

    public ProxyHandler(GatewayProperties.Route route, WebClient webClient) {
        this.route = route;
        this.webClient = webClient;
    }

    public Mono<ServerResponse> forward(ServerRequest request) {
        HttpHeaders incoming = request.headers().asHttpHeaders();

        WebClient.RequestBodySpec spec = webClient.method(request.method())
                .uri(targetUri(request))
                .headers(outgoing -> copyRequestHeaders(request, incoming, outgoing));

        // Only send a body when the client sent one: an empty chunked body on a GET confuses some servers.
        WebClient.RequestHeadersSpec<?> ready = hasBody(incoming)
                ? spec.body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer.class)))
                : spec;

        return ready.retrieve()
                // Backend errors (4xx/5xx) are passed through to the client, not turned into exceptions.
                .onStatus(status -> true, response -> Mono.empty())
                .toEntityFlux(DataBuffer.class)
                .flatMap(entity -> ServerResponse.status(entity.getStatusCode())
                        .headers(outgoing -> copyWithoutHopByHop(entity.getHeaders(), outgoing))
                        .body(BodyInserters.fromDataBuffers(entity.getBody())))
                .onErrorResume(WebClientRequestException.class, ProxyHandler::backendUnavailable);
    }

    private URI targetUri(ServerRequest request) {
        URI incoming = request.uri();
        return UriComponentsBuilder.fromUri(route.uri())
                .path(route.backendPath(incoming.getRawPath()))
                .query(incoming.getRawQuery())
                .build(true)
                .toUri();
    }

    private static boolean hasBody(HttpHeaders headers) {
        return headers.getContentLength() > 0 || headers.containsHeader(HttpHeaders.TRANSFER_ENCODING);
    }

    private static void copyRequestHeaders(ServerRequest request, HttpHeaders incoming, HttpHeaders outgoing) {
        copyWithoutHopByHop(incoming, outgoing);

        String clientIp = request.remoteAddress()
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse(null);
        if (clientIp != null) {
            String previous = incoming.getFirst("X-Forwarded-For");
            outgoing.set("X-Forwarded-For", previous == null ? clientIp : previous + ", " + clientIp);
        }
        String host = incoming.getFirst(HttpHeaders.HOST);
        if (host != null) {
            outgoing.set("X-Forwarded-Host", host);
        }
        outgoing.set("X-Forwarded-Proto", request.uri().getScheme());
    }

    private static void copyWithoutHopByHop(HttpHeaders from, HttpHeaders to) {
        from.forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                to.addAll(name, values);
            }
        });
    }

    private static Mono<ServerResponse> backendUnavailable(WebClientRequestException e) {
        HttpStatus status = e.getCause() instanceof ReadTimeoutException
                ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        return ServerResponse.status(status).build();
    }
}
