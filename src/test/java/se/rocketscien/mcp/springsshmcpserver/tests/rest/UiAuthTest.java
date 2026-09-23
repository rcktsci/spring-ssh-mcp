package se.rocketscien.mcp.springsshmcpserver.tests.rest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

import se.rocketscien.mcp.springsshmcpserver.config.UiKeyFilter;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;

import static org.assertj.core.api.Assertions.assertThat;

class UiAuthTest extends BaseRestTest {

    private static final String LIST_SERVERS_CALL = """
            {
              "jsonrpc": "2.0",
              "id": 2,
              "method": "tools/call",
              "params": {
                "name": "list_servers",
                "arguments": {}
              }
            }
            """;

    @SneakyThrows
    @Test
    void request_without_ui_key_returns_401() {
        var request = HttpRequest.newBuilder().uri(URI.create(apiUrl("/tokens"))).GET().build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * The header is set by the filter itself, so it has to be asserted on a real response: without it the
     * browser would keep {@code /api/servers} and its connection metadata in its cache.
     */
    @Test
    void successful_response_is_not_cached() {
        var response = get("/servers");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @SneakyThrows
    @Test
    void unauthorized_response_is_not_cached() {
        var request = HttpRequest.newBuilder().uri(URI.create(apiUrl("/servers"))).GET().build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @SneakyThrows
    @Test
    void request_with_wrong_ui_key_returns_401() {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl("/tokens")))
                .header(UiKeyFilter.HEADER, "not-the-master-key")
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @SneakyThrows
    @Test
    void unauthorized_response_body_is_json() {
        var request = HttpRequest.newBuilder().uri(URI.create(apiUrl("/tokens"))).GET().build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).contains("application/json"));
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(UiKeyFilter.ACCESS_DENIED);
    }

    @SneakyThrows
    @Test
    void encoded_api_path_without_ui_key_returns_401() {
        var response = getRaw("/a%70i/tokens");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @SneakyThrows
    @Test
    void encoded_slash_api_path_is_never_served_without_ui_key() {
        var response = getRaw("/api%2Ftokens");

        // Tomcat rejects an encoded slash in the path with 400 before the filter ever runs
        // (ALLOW_ENCODED_SLASH is off by default). If that ever changes, the filter answers 401 instead,
        // because the routed segment decodes "/api%2Ftokens" to "api/tokens". Either way: never served.
        assertThat(response.statusCode()).isIn(400, 401);
    }

    @SneakyThrows
    @Test
    void matrix_parameter_api_path_without_ui_key_returns_401() {
        var response = getRaw("/api;x=1/tokens");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @SneakyThrows
    @Test
    void request_with_correct_ui_key_is_not_denied() {
        var response = get("/tokens");

        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    /**
     * The page has to be reachable without the key: its JS is what asks for it and shows the login form.
     * Asserts {@code 200} rather than "not 401" because the latter passes on any other status, including
     * a 404 from a missing {@code static/index.html}.
     */
    @SneakyThrows
    @Test
    void root_page_is_served_without_ui_key() {
        var response = getRaw("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("<html");
    }

    @SneakyThrows
    @Test
    void mcp_endpoint_is_not_filtered() {
        var response = postRaw("/mcp", LIST_SERVERS_CALL, false);

        assertThat(response.statusCode()).isNotEqualTo(401);
    }

    @SneakyThrows
    @Test
    void ui_key_does_not_authorize_mcp_endpoint() {
        var response = postRaw("/mcp", LIST_SERVERS_CALL, true);

        assertThat(response.body()).contains("\"isError\":true");
    }

    private static final String MCP_BEARER_TOKEN = "550e8400-e29b-41d4-a716-4466554400aa";

    @BeforeEach
    void registerMcpToken() {
        var token = new AuthTokenEntity();
        token.setToken(UUID.fromString(MCP_BEARER_TOKEN));
        token.setCanEdit(true);
        token.setCanExecute(true);
        authTokenRepository.save(token);
    }

    @Test
    void encoded_mcp_path_with_bearer_token_reaches_the_endpoint() {
        var response = postRaw("/m%63p", LIST_SERVERS_CALL, false, MCP_BEARER_TOKEN);

        assertThat(response.statusCode()).isNotEqualTo(404);
        assertThat(response.body()).doesNotContain("ScopedValue");
        assertThat(response.body()).doesNotContain("\"isError\":true");
    }

    @Test
    void matrix_parameter_mcp_path_with_bearer_token_reaches_the_endpoint() {
        var response = postRaw("/mcp;x=1", LIST_SERVERS_CALL, false, MCP_BEARER_TOKEN);

        assertThat(response.statusCode()).isNotEqualTo(404);
        assertThat(response.body()).doesNotContain("ScopedValue");
        assertThat(response.body()).doesNotContain("\"isError\":true");
    }

    @Test
    void matrix_parameter_mcp_path_without_token_is_not_authorized() {
        var response = postRaw("/mcp;x=1", LIST_SERVERS_CALL, false, null);

        assertThat(response.body()).contains("\"isError\":true");
    }

    @SneakyThrows
    private HttpResponse<String> getRaw(String rawPath) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + localServerPort + rawPath))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @SneakyThrows
    private HttpResponse<String> postRaw(String rawPath, String body, boolean withUiKey) {
        return postRaw(rawPath, body, withUiKey, null);
    }

    @SneakyThrows
    private HttpResponse<String> postRaw(String rawPath, String body, boolean withUiKey, String bearerToken) {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + localServerPort + rawPath))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (withUiKey) {
            requestBuilder.header(UiKeyFilter.HEADER, UI_KEY);
        }
        if (bearerToken != null) {
            requestBuilder.header("Authorization", "Bearer " + bearerToken);
        }

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }
}