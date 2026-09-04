package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class UpsertAccessTokenTest extends BaseCallToolTest {

    @Test
    void upsert_access_token_without_token_generates_new_uuid() {
        var text = getResponseText(upsertAccessToken(Map.of("canExecute", true)));
        var json = objectMapper.readTree(text);
        var token = UUID.fromString(json.get("token").asText());
        assertThat(token).isNotNull();

        var saved = authTokenRepository.findByToken(token).orElseThrow();
        assertThat(saved.getCanExecute()).isTrue();
        assertThat(saved.getCanEdit()).isFalse();
        assertThat(saved.getIsTokenAdmin()).isFalse();
    }

    @Test
    void upsert_access_token_with_explicit_uuid_creates() {
        var newToken = UUID.randomUUID().toString();
        var text = getResponseText(upsertAccessToken(Map.of(
                "token", newToken,
                "canEdit", true,
                "canExecute", true,
                "isTokenAdmin", true)));
        var json = objectMapper.readTree(text);
        assertThat(json.get("token").asText()).isEqualTo(newToken);

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getCanEdit()).isTrue();
        assertThat(saved.getCanExecute()).isTrue();
        assertThat(saved.getIsTokenAdmin()).isTrue();
    }

    @Test
    void upsert_access_token_duplicate_without_overwrite_returns_error() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of("token", newToken, "canExecute", true));

        var text = getResponseText(upsertAccessToken(Map.of("token", newToken, "canExecute", false)));
        assertThat(text).contains("error");
        assertThat(text).contains("already exists");

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getCanExecute()).isTrue();
    }

    @Test
    void upsert_access_token_with_overwrite_updates() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of("token", newToken, "canExecute", true));
        var text = getResponseText(upsertAccessToken(Map.of(
                "token", newToken,
                "canExecute", false,
                "canEdit", true,
                "overwrite", true)));
        assertThat(text).contains("token");

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getCanExecute()).isFalse();
        assertThat(saved.getCanEdit()).isTrue();
    }

    @Test
    void upsert_access_token_invalid_uuid_returns_error() {
        var text = getResponseText(upsertAccessToken(Map.of("token", "not-a-uuid", "canExecute", true)));
        assertThat(text).contains("error");
        assertThat(text).contains("Invalid token UUID");
    }

    @Test
    void upsert_access_token_without_token_admin_role_returns_error() {
        var text = getResponseText(upsertAccessToken(Map.of("canExecute", true), FULL_ACCESS_TOKEN));
        assertThat(text).contains("Access denied");
    }

    @Test
    void upsert_access_token_without_auth_returns_error() {
        var text = getResponseText(callTool(null, "upsert_access_token", Map.of("canExecute", true)));
        assertThat(text).contains("error");
    }

    @Test
    void upsert_access_token_cannot_modify_own_token() {
        var text = getResponseText(upsertAccessToken(Map.of(
                "token", TOKEN_ADMIN_TOKEN,
                "canExecute", false,
                "overwrite", true)));
        assertThat(text).contains("error");
        assertThat(text).contains("own token");

        var saved = authTokenRepository.findByToken(UUID.fromString(TOKEN_ADMIN_TOKEN)).orElseThrow();
        assertThat(saved.getCanExecute()).isTrue();
    }

    @Test
    void upsert_access_token_with_execute_only_persisted() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of(
                "token", newToken,
                "canExecute", true,
                "executeOnly", new String[]{"web-*", "db-*"}));

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getExecuteOnly()).containsExactly("web-*", "db-*");
        assertThat(saved.canExecuteOnServer("web-1")).isTrue();
        assertThat(saved.canExecuteOnServer("db-2")).isTrue();
        assertThat(saved.canExecuteOnServer("cache-1")).isFalse();
    }

    @Test
    void upsert_access_token_with_comment_persisted() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of(
                "token", newToken,
                "canExecute", true,
                "comment", "ops rotation 2026-Q3"));

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getComment()).isEqualTo("ops rotation 2026-Q3");
    }

    @Test
    void upsert_access_token_partial_update_omitted_comment_preserves_value() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of("token", newToken, "canExecute", true, "comment", "keep me"));
        upsertAccessToken(Map.of(
                "token", newToken,
                "canExecute", false,
                "overwrite", true));

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getCanExecute()).isFalse();
        assertThat(saved.getComment()).isEqualTo("keep me");
    }

    @Test
    void upsert_access_token_empty_string_clears_comment() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(Map.of("token", newToken, "canExecute", true, "comment", "to be cleared"));
        upsertAccessToken(Map.of(
                "token", newToken,
                "comment", "",
                "overwrite", true));

        var saved = authTokenRepository.findByToken(UUID.fromString(newToken)).orElseThrow();
        assertThat(saved.getComment()).isNull();
    }

    @Test
    void upsert_access_token_comment_exceeding_255_chars_returns_error() {
        var tooLong = "a".repeat(256);
        var text = getResponseText(upsertAccessToken(Map.of("canExecute", true, "comment", tooLong)));
        assertThat(text).contains("error");
        assertThat(text).contains("255");
    }
}
