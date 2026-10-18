package com.mess.app.testsupport;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny local HTTP server (JDK only) used to test the Supabase and email clients over real HTTP
 * without calling any external service. Responses are chosen per "METHOD path-prefix".
 */
public class StubHttpServer implements AutoCloseable {

    public record Received(String method, String path, Map<String, String> headers, String body) {
        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();

    public StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
            String path = exchange.getRequestURI().getPath();
            requests.add(new Received(exchange.getRequestMethod(), path, headers,
                    new String(body, StandardCharsets.UTF_8)));

            Reply reply = new Reply(404, "{\"message\":\"no stub registered\"}");
            for (Map.Entry<String, Reply> entry : replies.entrySet()) {
                if ((exchange.getRequestMethod() + " " + path).startsWith(entry.getKey())) {
                    reply = entry.getValue();
                    break;
                }
            }
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
    }

    /** Answer requests whose "METHOD path" starts with {@code methodAndPathPrefix}. */
    public StubHttpServer reply(String methodAndPathPrefix, int status, String body) {
        replies.put(methodAndPathPrefix, new Reply(status, body));
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Received> requests() {
        return new ArrayList<>(requests);
    }

    public Received lastRequest() {
        return requests.get(requests.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
