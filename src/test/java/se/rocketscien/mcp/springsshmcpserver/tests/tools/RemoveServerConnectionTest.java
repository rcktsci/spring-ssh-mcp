package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveServerConnectionTest extends BaseCallToolTest {

    @Test
    void remove_server_success() {
        addServerConnection("to-remove");
        var text = getResponseText(removeServerConnection("to-remove"));
        assertThat(text).contains("Success");
        var servers = parseServerMap(listServers());
        assertThat(servers).doesNotContainKey("to-remove");
    }

    @Test
    void remove_server_not_found() {
        var text = getResponseText(removeServerConnection("non-existent"));
        assertThat(text).contains("error");
        assertThat(text).contains("Server not found");
    }

    @Test
    void remove_server() {
        addServerConnection("test-server");
        assertThat(parseServerMap(listServers())).hasSize(1);
        removeServerConnection("test-server");
        assertThat(parseServerMap(listServers())).isEmpty();
    }

    @Test
    void remove_server_without_edit_role_returns_error() {
        assertThat(getResponseText(removeServerConnection("any-server", EXECUTE_ONLY_TOKEN))).contains("Access denied");
    }
}
