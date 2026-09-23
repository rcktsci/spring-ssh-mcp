package se.rocketscien.mcp.springsshmcpserver.tests.rest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import lombok.SneakyThrows;

import se.rocketscien.mcp.springsshmcpserver.BaseApplicationTest;
import se.rocketscien.mcp.springsshmcpserver.config.UiKeyFilter;

public class BaseRestTest extends BaseApplicationTest {

    protected static final String UI_KEY = "test-ui-master-key";

    protected String apiUrl(String path) {
        return "http://localhost:" + localServerPort + "/api" + path;
    }

    @SneakyThrows
    protected HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder().uri(URI.create(apiUrl(path))).GET());
    }

    @SneakyThrows
    protected HttpResponse<String> post(String path, String json) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    @SneakyThrows
    protected HttpResponse<String> patch(String path, String json) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json)));
    }

    @SneakyThrows
    protected HttpResponse<String> delete(String path) {
        return send(HttpRequest.newBuilder().uri(URI.create(apiUrl(path))).DELETE());
    }

    @SneakyThrows
    private HttpResponse<String> send(HttpRequest.Builder requestBuilder) {
        var request = requestBuilder.header(UiKeyFilter.HEADER, UI_KEY).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}