package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.util.Map;

import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class ListServersTest extends BaseCallToolTest {

    @Test
    void returns_added_servers() {
        addServerConnection("test-server");
        var servers = parseServerMap(listServers());
        assertThat(servers).containsKey("test-server");
    }

    @Test
    void add_and_list_server() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test-server", "host", "192.168.1.1", "port", 22, "username", "admin", "password", "secret"
        ));
        var servers = parseServerMap(listServers());
        assertThat(servers).hasSize(1).containsKey("test-server");
        assertThat(servers.get("test-server").get("address").asText()).isEqualTo("admin@192.168.1.1:22");
    }

    @Test
    void list_servers_with_wildcard_returns_filtered() {
        addServerConnection("vm/staging");
        addServerConnection("pve/prod");
        var servers = parseServerMap(listServers(WILDCARD_TOKEN));
        assertThat(servers).containsOnlyKeys("vm/staging");
    }

    @Test
    void list_servers_reports_connect_via() {
        addServerConnectionVia("jump-host", null);
        addServerConnectionVia("target-server", "jump-host");

        var servers = parseServerMap(listServers());

        assertThat(servers.get("target-server").get("connectVia").asText()).isEqualTo("jump-host");
        assertThat(servers.get("target-server").get("address").asText()).contains("@");
        assertThat(servers.get("jump-host").get("connectVia").isNull()).isTrue();
    }

    @Test
    void list_servers_reports_connect_via_even_when_the_jump_is_filtered_out() {
        addServerConnection("bastion");
        addServerConnectionVia("test-ssh", "bastion");

        var servers = parseServerMap(listServers(EXECUTE_ONLY_TOKEN));

        assertThat(servers).containsOnlyKeys("test-ssh");
        assertThat(servers.get("test-ssh").get("connectVia").asText()).isEqualTo("bastion");
    }
}
