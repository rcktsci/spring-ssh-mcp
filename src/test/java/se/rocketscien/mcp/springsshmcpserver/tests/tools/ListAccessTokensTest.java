package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class ListAccessTokensTest extends BaseCallToolTest {

    @Test
    void list_access_tokens_returns_all_tokens_with_full_uuids() {
        var response = listAccessTokens();
        var json = objectMapper.readTree(getResponseText(response));
        var tokens = json.get("tokens");

        assertThat(tokens.isArray()).isTrue();
        assertThat(tokens.size()).isGreaterThanOrEqualTo(6);

        boolean foundAdmin = false;
        boolean foundFullAccess = false;
        for (JsonNode t : tokens) {
            String token = t.get("token").asText();
            assertThat(token).hasSize(36);
            if (TOKEN_ADMIN_TOKEN.equals(token)) {
                foundAdmin = true;
                assertThat(t.get("isTokenAdmin").asBoolean()).isTrue();
                assertThat(t.get("canEdit").asBoolean()).isTrue();
                assertThat(t.get("canExecute").asBoolean()).isTrue();
            }
            if (FULL_ACCESS_TOKEN.equals(token)) {
                foundFullAccess = true;
                assertThat(t.get("isTokenAdmin").asBoolean()).isFalse();
                assertThat(t.get("canEdit").asBoolean()).isTrue();
            }
        }
        assertThat(foundAdmin).isTrue();
        assertThat(foundFullAccess).isTrue();
    }

    @Test
    void list_access_tokens_without_token_admin_role_returns_error() {
        assertThat(getResponseText(listAccessTokens(FULL_ACCESS_TOKEN))).contains("Access denied");
        assertThat(getResponseText(listAccessTokens(EXECUTE_ONLY_TOKEN))).contains("Access denied");
        assertThat(getResponseText(listAccessTokens(EDIT_ONLY_TOKEN))).contains("Access denied");
        assertThat(getResponseText(listAccessTokens(NO_PERMS_TOKEN))).contains("Access denied");
    }

    @Test
    void list_access_tokens_without_auth_returns_error() {
        var text = getResponseText(callTool(null, "list_access_tokens", java.util.Map.of()));
        assertThat(text).contains("error");
    }

    @Test
    void list_access_tokens_includes_comment_field() {
        var newToken = java.util.UUID.randomUUID().toString();
        upsertAccessToken(java.util.Map.of(
                "token", newToken,
                "canExecute", true,
                "comment", "owner-X"));

        var json = objectMapper.readTree(getResponseText(listAccessTokens()));
        JsonNode found = null;
        for (JsonNode t : json.get("tokens")) {
            if (newToken.equals(t.get("token").asText())) {
                found = t;
                break;
            }
        }
        assertThat(found).isNotNull();
        assertThat(found.get("comment").asText()).isEqualTo("owner-X");
    }
}
