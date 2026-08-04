package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class RenameServerConnectionTest extends BaseCallToolTest {

    @Test
    void rename_server_connection_success() {
        addServerConnection("old-name");
        var text = getResponseText(renameServerConnection("old-name", "new-name"));
        assertThat(text).contains("Success");
        var servers = parseServerMap(listServers());
        assertThat(servers).containsKey("new-name");
        assertThat(servers).doesNotContainKey("old-name");
    }

    @Test
    void rename_server_connection_not_found() {
        var text = getResponseText(renameServerConnection("non-existent", "new-name"));
        assertThat(text).contains("error");
        assertThat(text).contains("Server not found");
    }

    @Test
    void rename_server_connection_duplicate_name() {
        addServerConnection("source");
        addServerConnection("target");
        var text = getResponseText(renameServerConnection("source", "target"));
        assertThat(text).contains("error");
        assertThat(text).contains("already exists");
    }

    @Test
    void rename_server_connection_keeps_credentials() {
        addServerConnection("old");
        renameServerConnection("old", "renamed");
        var r = getExecResult(execute("renamed", "whoami", "rename-session", 10));
        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
    }

    @Test
    void rename_server_without_edit_role_returns_error() {
        addServerConnection("old-server");
        var response = renameServerConnection("old-server", "new-server", EXECUTE_ONLY_TOKEN);
        assertThat(getResponseText(response)).contains("Access denied");
    }

    @Test
    void rename_server_with_edit_role_succeeds() {
        addServerConnection("old-server");
        var response = renameServerConnection("old-server", "renamed-server");
        assertThat(getResponseText(response)).contains("Success");
    }
}
