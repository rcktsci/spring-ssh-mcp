package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.util.Map;

import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class AddServerConnectionTest extends BaseCallToolTest {

    @Test
    void passwords_are_encrypted_in_database() {
        addServerConnection("enc-test");

        var saved = serverRepository.findByName("enc-test").orElseThrow();

        assertThat(saved.getPassword()).startsWith("aes:");
        assertThat(saved.getPassword()).doesNotContain("testpassword");
    }

    @Test
    void private_keys_are_encrypted_in_database() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "enc-key-test", "host", "192.168.1.1", "port", 22, "username", "root",
                "privateKey", TEST_PRIVATE_KEY
        ));

        var saved = serverRepository.findByName("enc-key-test").orElseThrow();

        assertThat(saved.getPrivateKey()).startsWith("aes:");
        assertThat(saved.getPrivateKey()).doesNotContain("BEGIN");
    }

    @Test
    void add_server_with_password_success() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test-pass", "host", "192.168.1.1", "port", 22, "username", "root", "password", "testpassword"
        )));
        assertThat(text).contains("Success");
        assertThat(text).contains("test-pass");
    }

    @Test
    void add_server_validation_no_auth() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test-invalid", "host", "192.168.1.1", "port", 22, "username", "root"
        )));
        assertThat(text).contains("error");
        assertThat(text).contains("Exactly one of password or private key must be provided");
    }

    @Test
    void add_server_validation_duplicate_name() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "dup-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "testpassword"
        ));
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "dup-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "otherpassword"
        )));
        assertThat(text).contains("error");
        assertThat(text).contains("already exists");
    }

    @Test
    void add_server_with_overwrite_true_updates() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "overwrite-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "oldpassword"
        ));
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "overwrite-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "newpassword", "overwrite", true
        )));
        assertThat(text).contains("Success");
        assertThat(text).contains("overwrite-test");
    }

    @Test
    void add_server_with_overwrite_false_fails() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "no-overwrite-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "oldpassword"
        ));
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "no-overwrite-test", "host", "192.168.1.1", "port", 22, "username", "root", "password", "newpassword", "overwrite", false
        )));
        assertThat(text).contains("error");
        assertThat(text).contains("already exists");
    }

    @Test
    void add_server_with_key_no_passphrase() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test-key", "host", "192.168.1.1", "port", 22, "username", "root", "privateKey", TEST_PRIVATE_KEY
        )));
        assertThat(text).contains("Success");
        assertThat(text).contains("test-key");
    }

    @Test
    void add_server_with_key_and_passphrase() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test-key-pass", "host", "192.168.1.1", "port", 22, "username", "root",
                "privateKey", TEST_PRIVATE_KEY_WITH_PASSPHRASE, "privateKeySecret", "testpassphrase"
        )));
        assertThat(text).contains("Success");
        assertThat(text).contains("test-key-pass");
    }

    @Test
    void add_server_validation_requires_password_or_private_key() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test", "host", "192.168.1.1", "port", 22, "username", "admin"
        )));
        assertThat(text).contains("Exactly one of password or private key must be provided");
    }

    @Test
    void add_server_validation_cannot_provide_both() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "test", "host", "192.168.1.1", "port", 22, "username", "admin",
                "password", "secret", "privateKey", "key"
        )));
        assertThat(text).contains("Exactly one of password or private key must be provided");
    }

    @Test
    void add_server_without_edit_role_returns_error() {
        var response = addServerConnection("new-server", EXECUTE_ONLY_TOKEN);
        assertThat(getResponseText(response)).contains("Access denied");
    }

    @Test
    void add_server_with_edit_role_succeeds() {
        var response = addServerConnection("new-server");
        assertThat(getResponseText(response)).contains("Success");
    }
}
