package io.github.jacksonvieira.ratelimiter.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal HTTP backend for tests, built on the JDK's HTTP server.
 *
 * <p>Echoes the request it received so tests can check what the gateway forwarded:
 * {@code /status/{code}} answers with that status, any other path answers 200 (or 201 for POST).
 */
final class StubBackend implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    StubBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    int requests() {
        return requests.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();

        int status = path.startsWith("/status/")
                ? Integer.parseInt(path.substring("/status/".length()))
                : "POST".equals(exchange.getRequestMethod()) ? 201 : 200;

        String echo = exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query)
                + "\nx-forwarded-for: " + exchange.getRequestHeaders().getFirst("X-Forwarded-For")
                + "\ntraceparent: " + exchange.getRequestHeaders().getFirst("traceparent")
                + "\nbody: " + body;
        byte[] bytes = echo.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.getResponseHeaders().add("X-Backend", "stub");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
