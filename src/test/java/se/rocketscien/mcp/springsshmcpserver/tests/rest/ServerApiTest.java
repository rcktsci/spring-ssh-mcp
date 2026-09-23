package se.rocketscien.mcp.springsshmcpserver.tests.rest;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.StreamSupport;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;

import se.rocketscien.mcp.springsshmcpserver.config.UiKeyFilter;
import se.rocketscien.mcp.springsshmcpserver.domain.ServerEntity;
import se.rocketscien.mcp.springsshmcpserver.service.SecretEncryptionService;

import static org.assertj.core.api.Assertions.assertThat;

class ServerApiTest extends BaseRestTest {

    private static final String EXACTLY_ONE_SECRET = "Exactly one of password or private key must be provided";

    @Autowired
    private SecretEncryptionService encryptionService;

    @Test
    void create_server_with_password_returns_auth_type_password() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret"));

        assertThat(created.get("name").asText()).isEqualTo("web-1");
        assertThat(serverRepository.findById(created.get("id").asLong()).orElseThrow().getPassword()).isNotNull();
        assertThat(serverView("web-1").get("authType").asText()).isEqualTo("PASSWORD");
    }

    @Test
    void create_server_without_port_defaults_to_22() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret"));

        var saved = serverRepository.findById(created.get("id").asLong()).orElseThrow();
        assertThat(saved.getPort()).isEqualTo(22);
        assertThat(serverView("web-1").get("port").asInt()).isEqualTo(22);
    }

    @Test
    void create_server_with_private_key_returns_auth_type_private_key() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "privateKey", TEST_PRIVATE_KEY));

        assertThat(serverRepository.findById(created.get("id").asLong()).orElseThrow().getPrivateKey())
                .startsWith("aes:");
        assertThat(serverView("web-1").get("authType").asText()).isEqualTo("PRIVATE_KEY");
    }

    @Test
    void create_server_with_encrypted_key_and_passphrase_returns_auth_type_private_key_with_pass() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "privateKey", TEST_PRIVATE_KEY_WITH_PASSPHRASE,
                "privateKeySecret", "testpassphrase"));

        var saved = serverRepository.findById(created.get("id").asLong()).orElseThrow();
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY_WITH_PASSPHRASE);
        assertThat(encryptionService.decrypt(saved.getPrivateKeySecret())).isEqualTo("testpassphrase");
        assertThat(serverView("web-1").get("authType").asText()).isEqualTo("PRIVATE_KEY_WITH_PASS");
    }

    @Test
    void create_server_without_password_and_key_returns_error() {
        var response = post("/servers", requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(EXACTLY_ONE_SECRET);
        assertThat(serverRepository.count()).isZero();
    }

    @Test
    void create_server_with_both_password_and_key_returns_error() {
        var response = post("/servers", requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret",
                "privateKey", TEST_PRIVATE_KEY));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(EXACTLY_ONE_SECRET);
        assertThat(serverRepository.count()).isZero();
    }

    @Test
    void create_server_with_blank_password_stores_empty_secret() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", ""));

        var saved = serverRepository.findById(created.get("id").asLong()).orElseThrow();
        assertThat(saved.getPassword()).startsWith("aes:");
        assertThat(encryptionService.decrypt(saved.getPassword())).isEmpty();
        assertThat(serverView("web-1").get("authType").asText()).isEqualTo("PASSWORD");
    }

    @Test
    void create_server_with_duplicate_name_returns_error() {
        createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret"));

        var response = post("/servers", requestBody(
                "name", "web-1",
                "host", "10.0.0.2",
                "username", "deploy",
                "password", "other"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText())
                .isEqualTo("Server 'web-1' already exists");
        assertThat(serverRepository.count()).isEqualTo(1);
        assertThat(encryptionService.decrypt(serverRepository.findByName("web-1").orElseThrow().getPassword()))
                .isEqualTo("secret");
    }

    @Test
    void create_server_without_host_returns_error() {
        var response = post("/servers", requestBody(
                "name", "web-1",
                "username", "deploy",
                "password", "secret"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'host' is required");
        assertThat(serverRepository.count()).isZero();
    }

    @Test
    void create_server_without_name_returns_error() {
        var response = post("/servers", requestBody(
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'name' is required");
        assertThat(serverRepository.count()).isZero();
    }

    @Test
    void create_server_with_blank_username_returns_error() {
        var response = post("/servers", requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "  ",
                "password", "secret"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'username' is required");
        assertThat(serverRepository.count()).isZero();
    }

    @Test
    void list_servers_returns_no_secret_values() {
        createServer(requestBody(
                "name", "pass-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret"));
        createServer(requestBody(
                "name", "key-1",
                "host", "10.0.0.2",
                "username", "deploy",
                "privateKey", TEST_PRIVATE_KEY_WITH_PASSPHRASE,
                "privateKeySecret", "testpassphrase"));

        var response = get("/servers");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .doesNotContain("password")
                .doesNotContain("privateKey")
                .doesNotContain("privateKeySecret")
                .doesNotContain("aes:")
                .doesNotContain("address");
        assertThat(serverView("pass-1").get("authType").asText()).isEqualTo("PASSWORD");
        assertThat(serverView("key-1").get("authType").asText()).isEqualTo("PRIVATE_KEY_WITH_PASS");
    }

    @Test
    void create_password_server_with_stray_passphrase_stores_none() {
        var created = createServer(requestBody(
                "name", "web-1",
                "host", "10.0.0.1",
                "username", "deploy",
                "password", "secret",
                "privateKeySecret", "orphan-passphrase"));

        var saved = serverRepository.findById(created.get("id").asLong()).orElseThrow();
        assertThat(saved.getPrivateKeySecret()).isNull();
        assertThat(serverView("web-1").get("authType").asText()).isEqualTo("PASSWORD");

        // The point of dropping it: a later PATCH of a bare privateKey keeps the stored passphrase, so a
        // passphrase left behind at creation would silently make the switched server PRIVATE_KEY_WITH_PASS.
        patchServer(saved.getId(), requestBody("privateKey", TEST_PRIVATE_KEY));

        var switched = savedServer(saved.getId());
        assertThat(switched.getPrivateKeySecret()).isNull();
        assertThat(switched.getAuthType()).isEqualTo("PRIVATE_KEY");
    }

    @Test
    void list_servers_returns_rows_in_id_order() {
        var first = createPasswordServer("web-1", "secret");
        var second = createPasswordServer("web-2", "secret");
        var third = createPasswordServer("web-3", "secret");

        var response = get("/servers");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(serverIds(response.body())).containsExactly(first, second, third);
    }

    @Test
    void update_server_port_only_keeps_encrypted_password() {
        var id = createPasswordServer();

        patchServer(id, requestBody("port", 2222));

        var saved = savedServer(id);
        assertThat(saved.getPort()).isEqualTo(2222);
        assertThat(saved.getPassword()).startsWith("aes:");
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("secret");
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void update_server_password_replaces_encrypted_password() {
        var id = createPasswordServer();

        patchServer(id, requestBody("password", "new-secret"));

        var saved = savedServer(id);
        assertThat(saved.getPassword()).startsWith("aes:");
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("new-secret");
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void switch_server_from_password_to_key_clears_password() {
        var id = createPasswordServer();

        patchServer(id, requestBody("privateKey", TEST_PRIVATE_KEY));

        var saved = savedServer(id);
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getPrivateKeySecret()).isNull();
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY);
        assertThat(saved.getAuthType()).isEqualTo("PRIVATE_KEY");
    }

    @Test
    void switch_server_from_key_to_password_clears_key() {
        var id = createKeyServer("web-1", TEST_PRIVATE_KEY);

        patchServer(id, requestBody("password", "new-secret"));

        var saved = savedServer(id);
        assertThat(saved.getPrivateKey()).isNull();
        assertThat(saved.getPrivateKeySecret()).isNull();
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("new-secret");
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void update_server_without_secrets_keeps_auth_type() {
        var id = createPasswordServer();

        patchServer(id, requestBody("host", "10.0.0.9"));

        var saved = savedServer(id);
        assertThat(saved.getHost()).isEqualTo("10.0.0.9");
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("secret");
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void update_server_private_key_only_keeps_existing_passphrase() {
        var id = createKeyServerWithPass("web-1", TEST_PRIVATE_KEY_WITH_PASSPHRASE, "testpassphrase");

        patchServer(id, requestBody("privateKey", TEST_PRIVATE_KEY));

        var saved = savedServer(id);
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY);
        assertThat(encryptionService.decrypt(saved.getPrivateKeySecret())).isEqualTo("testpassphrase");
        assertThat(saved.getAuthType()).isEqualTo("PRIVATE_KEY_WITH_PASS");
    }

    @Test
    void update_server_key_with_new_passphrase_switches_password_server() {
        var id = createPasswordServer();

        patchServer(id, requestBody("privateKey", TEST_PRIVATE_KEY_WITH_PASSPHRASE,
                                   "privateKeySecret", "testpassphrase"));

        var saved = savedServer(id);
        assertThat(saved.getPassword()).isNull();
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY_WITH_PASSPHRASE);
        assertThat(encryptionService.decrypt(saved.getPrivateKeySecret())).isEqualTo("testpassphrase");
        assertThat(saved.getAuthType()).isEqualTo("PRIVATE_KEY_WITH_PASS");
    }

    @Test
    void update_server_passphrase_only_on_password_server_changes_nothing() {
        var id = createPasswordServer();

        patchServer(id, requestBody("privateKeySecret", "orphan-passphrase"));

        var saved = savedServer(id);
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("secret");
        assertThat(saved.getPrivateKey()).isNull();
        assertThat(saved.getPrivateKeySecret()).isNull();
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void update_server_passphrase_only_replaces_stored_passphrase() {
        var id = createKeyServerWithPass("web-1", TEST_PRIVATE_KEY_WITH_PASSPHRASE, "old-passphrase");

        patchServer(id, requestBody("privateKeySecret", "new-passphrase"));

        var saved = savedServer(id);
        assertThat(encryptionService.decrypt(saved.getPrivateKeySecret())).isEqualTo("new-passphrase");
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY_WITH_PASSPHRASE);
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getAuthType()).isEqualTo("PRIVATE_KEY_WITH_PASS");
    }

    @Test
    void rename_server_stores_new_name() {
        var id = createPasswordServer();

        var updated = patchServer(id, requestBody("name", "web-2"));

        assertThat(updated.get("id").asLong()).isEqualTo(id);
        assertThat(updated.get("name").asText()).isEqualTo("web-2");
        assertThat(savedServer(id).getName()).isEqualTo("web-2");
    }

    @Test
    void rename_server_to_existing_name_returns_error() {
        var id = createPasswordServer();
        createPasswordServer("web-2", "other");

        var response = patch("/servers/" + id, requestBody("name", "web-2"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText())
                .isEqualTo("Server 'web-2' already exists");
        assertThat(savedServer(id).getName()).isEqualTo("web-1");
    }

    @Test
    void update_server_with_blank_port_returns_error() {
        var id = createPasswordServer();

        var response = patch("/servers/" + id, requestBody("port", ""));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'port' is required");
        assertThat(savedServer(id).getPort()).isEqualTo(22);
    }

    @Test
    void update_server_with_non_numeric_port_returns_error() {
        var id = createPasswordServer();

        var response = patch("/servers/" + id, requestBody("port", "ssh"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'port' must be a number");
        assertThat(savedServer(id).getPort()).isEqualTo(22);
    }

    @Test
    void update_server_with_out_of_range_port_returns_error() {
        var id = createPasswordServer();

        assertThat(patch("/servers/" + id, requestBody("port", 0)).statusCode()).isEqualTo(400);
        assertThat(patch("/servers/" + id, requestBody("port", -1)).statusCode()).isEqualTo(400);
        assertThat(patch("/servers/" + id, requestBody("port", 65536)).statusCode()).isEqualTo(400);

        assertThat(savedServer(id).getPort()).isEqualTo(22);
    }

    @Test
    void update_server_with_unchanged_name_succeeds() {
        var id = createPasswordServer();

        var updated = patchServer(id, requestBody("name", "web-1"));

        assertThat(updated.get("name").asText()).isEqualTo("web-1");
        assertThat(savedServer(id).getName()).isEqualTo("web-1");
    }

    @Test
    void update_server_with_both_password_and_private_key_returns_error() {
        var id = createPasswordServer();

        var response = patch("/servers/" + id, requestBody("password", "new-secret",
                                                        "privateKey", TEST_PRIVATE_KEY));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(EXACTLY_ONE_SECRET);
        var saved = savedServer(id);
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("secret");
        assertThat(saved.getPrivateKey()).isNull();
    }

    @Test
    void update_server_with_blank_host_returns_error() {
        var id = createPasswordServer();

        var response = patch("/servers/" + id, requestBody("host", "  "));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("'host' is required");
        assertThat(savedServer(id).getHost()).isEqualTo("10.0.0.1");
    }

    @Test
    void update_server_with_blank_password_keeps_password() {
        var id = createPasswordServer();

        patchServer(id, requestBody("password", ""));

        var saved = savedServer(id);
        assertThat(encryptionService.decrypt(saved.getPassword())).isEqualTo("secret");
        assertThat(saved.getAuthType()).isEqualTo("PASSWORD");
    }

    @Test
    void update_server_ignores_id_in_body() {
        var id = createPasswordServer();

        var updated = patchServer(id, requestBody("id", 4242, "host", "10.0.0.9"));

        assertThat(updated.get("id").asLong()).isEqualTo(id);
        assertThat(savedServer(id).getHost()).isEqualTo("10.0.0.9");
        assertThat(serverRepository.count()).isEqualTo(1);
    }

    @Test
    void switch_key_with_pass_to_password_then_to_key_removes_pass() {
        var id = createKeyServerWithPass("web-1", TEST_PRIVATE_KEY_WITH_PASSPHRASE, "testpassphrase");

        patchServer(id, requestBody("password", "new-secret"));
        var afterPassword = savedServer(id);
        assertThat(afterPassword.getAuthType()).isEqualTo("PASSWORD");
        assertThat(encryptionService.decrypt(afterPassword.getPassword())).isEqualTo("new-secret");
        assertThat(afterPassword.getPrivateKeySecret()).isNull();

        patchServer(id, requestBody("privateKey", TEST_PRIVATE_KEY));

        var saved = savedServer(id);
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getPrivateKeySecret()).isNull();
        assertThat(encryptionService.decrypt(saved.getPrivateKey())).isEqualTo(TEST_PRIVATE_KEY);
        assertThat(saved.getAuthType()).isEqualTo("PRIVATE_KEY");
    }

    @Test
    void malformed_json_body_returns_error_body() {
        var id = createPasswordServer();

        var response = patch("/servers/" + id, "{\"host\": ");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo("Malformed request");
    }

    @SneakyThrows
    @Test
    void api_rejects_bearer_token_instead_of_ui_key() {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl("/servers")))
                .header("Authorization", "Bearer 550e8400-e29b-41d4-a716-446655440000")
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).get("error").asText()).isEqualTo(UiKeyFilter.ACCESS_DENIED);
    }

    @Test
    void delete_server_removes_it_from_list() {
        var id = createPasswordServer();

        var response = delete("/servers/" + id);

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(serverRepository.findById(id)).isEmpty();
        assertThat(get("/servers").body()).doesNotContain("web-1");
    }

    @Test
    void update_unknown_server_returns_404() {
        var response = patch("/servers/4242", requestBody("host", "10.0.0.9"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText())
                .isEqualTo("Server not found: 4242");
    }

    @Test
    void delete_unknown_server_returns_404() {
        var response = delete("/servers/4242");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.body()).get("error").asText())
                .isEqualTo("Server not found: 4242");
    }

    private JsonNode serverView(String name) {
        var response = get("/servers");

        assertThat(response.statusCode()).isEqualTo(200);
        return StreamSupport.stream(objectMapper.readTree(response.body()).spliterator(), false)
                .filter(view -> name.equals(view.get("name").asText()))
                .findFirst()
                .orElseThrow();
    }

    private List<Long> serverIds(String responseBody) {
        return StreamSupport.stream(objectMapper.readTree(responseBody).spliterator(), false)
                .map(view -> view.get("id").asLong())
                .toList();
    }

    private JsonNode createServer(String json) {
        var response = post("/servers", json);

        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body());
    }

    private long createPasswordServer() {
        return createPasswordServer("web-1", "secret");
    }

    private long createPasswordServer(String name, String password) {
        var created = createServer(requestBody(
                "name", name,
                "host", "10.0.0.1",
                "username", "deploy",
                "password", password));

        return created.get("id").asLong();
    }

    private long createKeyServer(String name, String privateKey) {
        var created = createServer(requestBody(
                "name", name,
                "host", "10.0.0.1",
                "username", "deploy",
                "privateKey", privateKey));

        return created.get("id").asLong();
    }

    private long createKeyServerWithPass(String name, String privateKey, String privateKeySecret) {
        var created = createServer(requestBody(
                "name", name,
                "host", "10.0.0.1",
                "username", "deploy",
                "privateKey", privateKey,
                "privateKeySecret", privateKeySecret));

        return created.get("id").asLong();
    }

    /** Answers {@code 200 {id, name}} only; everything else is asserted on the stored row. */
    private JsonNode patchServer(long id, String json) {
        var response = patch("/servers/" + id, json);

        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readTree(response.body());
    }

    private ServerEntity savedServer(long id) {
        return serverRepository.findById(id).orElseThrow();
    }

    /** Field pairs in request order, absent fields stay out of the body. */
    private String requestBody(Object... fieldsAndValues) {
        var request = new LinkedHashMap<String, Object>();
        for (int i = 0; i < fieldsAndValues.length; i += 2) {
            request.put((String) fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        return objectMapper.writeValueAsString(request);
    }
}
