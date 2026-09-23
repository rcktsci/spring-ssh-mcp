package se.rocketscien.mcp.springsshmcpserver.tests.rest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "ui.master-key=")
class EmptyUiKeyTest extends BaseRestTest {

    @SneakyThrows
    @Test
    void empty_ui_master_key_returns_401_for_every_api_request() {
        assertThat(getWithoutUiKey("/tokens").statusCode()).isEqualTo(401);
        assertThat(postWithoutUiKey("/tokens", "{}").statusCode()).isEqualTo(401);
        assertThat(getWithoutUiKey("/servers").statusCode()).isEqualTo(401);
        assertThat(postWithoutUiKey("/servers", "{}").statusCode()).isEqualTo(401);
    }

    @SneakyThrows
    private HttpResponse<String> getWithoutUiKey(String path) {
        var request = HttpRequest.newBuilder().uri(URI.create(apiUrl(path))).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SneakyThrows
    private HttpResponse<String> postWithoutUiKey(String path, String json) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
