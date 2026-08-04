package ru.rcktsci.experiments.ai.sshmcp.tests.tools;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ru.rcktsci.experiments.ai.sshmcp.tests.BaseCallToolTest;

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
        assertThat(servers)
                .hasSize(1)
                .containsKey("test-server")
                .containsValue("admin@192.168.1.1:22");
    }

    @Test
    void list_servers_with_wildcard_returns_filtered() {
        addServerConnection("vm/staging");
        addServerConnection("pve/prod");
        var servers = parseServerMap(listServers(WILDCARD_TOKEN));
        assertThat(servers).containsOnlyKeys("vm/staging");
    }
}
