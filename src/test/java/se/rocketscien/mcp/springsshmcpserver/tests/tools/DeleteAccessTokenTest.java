package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteAccessTokenTest extends BaseCallToolTest {

    @Test
    void delete_access_token_success() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(java.util.Map.of("token", newToken, "canExecute", true));
        assertThat(authTokenRepository.findByToken(UUID.fromString(newToken))).isPresent();

        var text = getResponseText(deleteAccessToken(newToken));
        assertThat(text).contains("Success");
        assertThat(authTokenRepository.findByToken(UUID.fromString(newToken))).isEmpty();
    }

    @Test
    void delete_access_token_not_found_returns_error() {
        var text = getResponseText(deleteAccessToken(UUID.randomUUID().toString()));
        assertThat(text).contains("error");
        assertThat(text).contains("not found");
    }

    @Test
    void delete_access_token_invalid_uuid_returns_error() {
        var text = getResponseText(deleteAccessToken("not-a-uuid"));
        assertThat(text).contains("error");
        assertThat(text).contains("Invalid token UUID");
    }

    @Test
    void delete_access_token_without_token_admin_role_returns_error() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(java.util.Map.of("token", newToken, "canExecute", true));

        var text = getResponseText(deleteAccessToken(newToken, FULL_ACCESS_TOKEN));
        assertThat(text).contains("Access denied");
        assertThat(authTokenRepository.findByToken(UUID.fromString(newToken))).isPresent();
    }

    @Test
    void delete_access_token_without_auth_returns_error() {
        var newToken = UUID.randomUUID().toString();
        upsertAccessToken(java.util.Map.of("token", newToken, "canExecute", true));

        var text = getResponseText(callTool(null, "delete_access_token", java.util.Map.of("token", newToken)));
        assertThat(text).contains("error");
        assertThat(authTokenRepository.findByToken(UUID.fromString(newToken))).isPresent();
    }

    @Test
    void delete_access_token_cannot_delete_own_token() {
        var text = getResponseText(deleteAccessToken(TOKEN_ADMIN_TOKEN));
        assertThat(text).contains("error");
        assertThat(text).contains("own token");
        assertThat(authTokenRepository.findByToken(UUID.fromString(TOKEN_ADMIN_TOKEN))).isPresent();
    }
}
