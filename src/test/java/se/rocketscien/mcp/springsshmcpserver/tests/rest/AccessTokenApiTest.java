package se.rocketscien.mcp.springsshmcpserver.tests.rest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenApiTest extends BaseRestTest {

    private static final String COMMENT_TOO_LONG_ERROR = "comment exceeds 255 characters";

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

    @Test
    void create_token_returns_new_uuid() {
        var response = post("/tokens", "{}");

        assertThat(response.statusCode()).isEqualTo(201);
        var token = UUID.fromString(objectMapper.readTree(response.body()).get("token").asText());
        assertThat(authTokenRepository.findByToken(token)).isPresent();
    }

    @Test
    void create_token_without_flags_stores_false_roles() {
        var token = createToken("{}");

        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getCanEdit()).isFalse();
        assertThat(saved.getCanExecute()).isFalse();
        assertThat(saved.getExecuteOnly()).isEmpty();
        assertThat(saved.getComment()).isNull();
        assertThat(saved.getIsTokenAdmin()).isFalse();
    }

    @Test
    void create_token_with_comment_stores_comment() {
        var token = createToken("{\"comment\": \"ops rotation 2026-Q3\"}");

        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getComment()).isEqualTo("ops rotation 2026-Q3");
    }

    @Test
    void create_token_with_comment_too_long_returns_error() {
        var tooLong = "a".repeat(256);

        var response = post("/tokens", "{\"comment\": \"" + tooLong + "\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(COMMENT_TOO_LONG_ERROR);
        assertThat(authTokenRepository.count()).isZero();
    }

    @Test
    void list_tokens_contains_created_token() {
        var token = createToken("""
                {
                  "canExecute": true,
                  "executeOnly": ["web-*"],
                  "comment": "rotated"
                }
                """);

        var response = get("/tokens");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(tokenNames(response.body())).containsExactly(token.toString());
        var listed = tokenView(response.body(), token);
        assertThat(listed.get("canExecute").asBoolean()).isTrue();
        assertThat(listed.get("canEdit").asBoolean()).isFalse();
        assertThat(listed.get("isTokenAdmin").asBoolean()).isFalse();
        assertThat(listed.get("comment").asText()).isEqualTo("rotated");
        assertThat(listed.get("createdAt").asText()).isNotBlank();
        assertThat(listed.get("updatedAt").asText()).isNotBlank();
    }

    @Test
    void update_token_comment_only_keeps_roles() {
        var token = createToken("""
                {
                  "canEdit": true,
                  "canExecute": true,
                  "executeOnly": ["web-*"],
                  "isTokenAdmin": true
                }
                """);

        var response = patch("/tokens/" + token, "{\"comment\": \"rotated\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getComment()).isEqualTo("rotated");
        assertThat(saved.getCanEdit()).isTrue();
        assertThat(saved.getCanExecute()).isTrue();
        assertThat(saved.getExecuteOnly()).containsExactly("web-*");
        assertThat(saved.getIsTokenAdmin()).isTrue();
    }

    @Test
    void update_token_returns_only_the_token() {
        var token = createToken("{\"canExecute\": true, \"comment\": \"keep me\"}");

        var response = patch("/tokens/" + token, "{\"comment\": \"rotated\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readTree(response.body());
        assertThat(body.get("token").asText()).isEqualTo(token.toString());
        // The contract answers 200 {token}, parallel to POST: a full view here would duplicate GET and could
        // drift from it. Nothing but "token" — no canEdit, canExecute, executeOnly, comment, isTokenAdmin,
        // createdAt or updatedAt.
        assertThat(body.properties().stream().map(Map.Entry::getKey)).containsExactly("token");
    }

    @Test
    void update_token_empty_comment_clears_comment() {
        var token = createToken("{\"comment\": \"to be cleared\"}");

        var response = patch("/tokens/" + token, "{\"comment\": \"\"}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(authTokenRepository.findByToken(token).orElseThrow().getComment()).isNull();
    }

    @Test
    void update_token_comment_too_long_returns_error() {
        var token = createToken("{\"comment\": \"keep me\"}");
        var tooLong = "a".repeat(256);

        var response = patch("/tokens/" + token, "{\"comment\": \"" + tooLong + "\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(COMMENT_TOO_LONG_ERROR);
        assertThat(authTokenRepository.findByToken(token).orElseThrow().getComment()).isEqualTo("keep me");
    }

    @Test
    void update_token_execute_only_globs_restricts_servers() {
        var token = createToken("{\"canExecute\": true}");

        var response = patch("/tokens/" + token, "{\"executeOnly\": [\"web-*\", \"db-*\"]}");

        assertThat(response.statusCode()).isEqualTo(200);
        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getExecuteOnly()).containsExactly("web-*", "db-*");
        assertThat(saved.canExecuteOnServer("web-1")).isTrue();
        assertThat(saved.canExecuteOnServer("db-2")).isTrue();
        assertThat(saved.canExecuteOnServer("cache-1")).isFalse();
    }

    @Test
    void update_token_with_empty_execute_only_clears_masks() {
        var token = createToken("""
                {
                  "canExecute": true,
                  "executeOnly": ["web-*"]
                }
                """);

        var response = patch("/tokens/" + token, "{\"executeOnly\": []}");

        assertThat(response.statusCode()).isEqualTo(200);
        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getExecuteOnly()).isEmpty();
        assertThat(saved.canExecuteOnServer("cache-1")).isTrue();
    }

    @Test
    void delete_token_removes_it() {
        var token = createToken("{}");
        var keptToken = createToken("{}");

        var response = delete("/tokens/" + token);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(authTokenRepository.findByToken(token)).isEmpty();
        assertThat(tokenNames(get("/tokens").body())).containsExactly(keptToken.toString());
    }

    @Test
    void delete_last_admin_token_succeeds() {
        var token = createToken("{\"isTokenAdmin\": true}");
        assertThat(authTokenRepository.existsByIsTokenAdminTrue()).isTrue();

        var response = delete("/tokens/" + token);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(authTokenRepository.findByToken(token)).isEmpty();
        assertThat(authTokenRepository.existsByIsTokenAdminTrue()).isFalse();
    }

    @Test
    void create_token_with_is_token_admin_true_stores_role() {
        var token = createToken("{\"isTokenAdmin\": true}");

        assertThat(authTokenRepository.findByToken(token).orElseThrow().getIsTokenAdmin()).isTrue();
    }

    @Test
    void create_token_ignores_token_in_body() {
        var requested = "550e8400-e29b-41d4-a716-446655440000";

        var response = post("/tokens", "{\"token\": \"" + requested + "\", \"canExecute\": true}");

        assertThat(response.statusCode()).isEqualTo(201);
        var created = objectMapper.readTree(response.body()).get("token").asText();
        assertThat(created).isNotEqualTo(requested);
        assertThat(authTokenRepository.findByToken(UUID.fromString(created))).isPresent();
        assertThat(authTokenRepository.findByToken(UUID.fromString(requested))).isEmpty();
    }

    @Test
    void update_unknown_token_returns_404() {
        var unknown = UUID.randomUUID();

        var response = patch("/tokens/" + unknown, "{\"comment\": \"nope\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("Token not found: " + unknown);
    }

    @Test
    void delete_unknown_token_returns_404() {
        var unknown = UUID.randomUUID();

        var response = delete("/tokens/" + unknown);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("Token not found: " + unknown);
    }

    @Test
    void update_token_with_invalid_uuid_returns_404() {
        var response = patch("/tokens/not-a-uuid", "{\"comment\": \"nope\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("Token not found: not-a-uuid");
    }

    @Test
    void delete_token_with_invalid_uuid_returns_404() {
        var response = delete("/tokens/not-a-uuid");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("Token not found: not-a-uuid");
    }

    @Test
    void token_created_through_rest_works_in_mcp() {
        var token = createToken("{\"canExecute\": true}");

        var response = callListServers("Bearer " + token);

        assertThat(response).doesNotContain("\"isError\":true");
        var serverList = objectMapper.readTree(response).get("result").get("content").get(0).get("text").asText();
        assertThat(serverList).isEqualTo("{}");
    }

    private UUID createToken(String json) {
        var response = post("/tokens", json);

        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(objectMapper.readTree(response.body()).get("token").asText());
    }

    private List<String> tokenNames(String responseBody) {
        return StreamSupport.stream(objectMapper.readTree(responseBody).spliterator(), false)
                .map(view -> view.get("token").asText())
                .toList();
    }

    private JsonNode tokenView(String responseBody, UUID token) {
        return StreamSupport.stream(objectMapper.readTree(responseBody).spliterator(), false)
                .filter(view -> token.toString().equals(view.get("token").asText()))
                .findFirst()
                .orElseThrow();
    }

    @SneakyThrows
    private String callListServers(String authorizationHeader) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .header("Authorization", authorizationHeader)
                .POST(HttpRequest.BodyPublishers.ofString(LIST_SERVERS_CALL))
                .build();

        var body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return StreamSupport.stream(body.lines().spliterator(), false)
                .filter(line -> line.startsWith("data:"))
                .findFirst()
                .map(line -> line.substring("data:".length()))
                .orElse(body);
    }
}
