package se.rocketscien.mcp.springsshmcpserver.tests;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;

import se.rocketscien.mcp.springsshmcpserver.BaseApplicationTest;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;

public class BaseCallToolTest extends BaseApplicationTest {

    protected static final String FULL_ACCESS_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    protected static final String EXECUTE_ONLY_TOKEN = "550e8400-e29b-41d4-a716-446655440001";
    protected static final String EDIT_ONLY_TOKEN = "550e8400-e29b-41d4-a716-446655440002";
    protected static final String WILDCARD_TOKEN = "550e8400-e29b-41d4-a716-446655440003";
    protected static final String NO_PERMS_TOKEN = "550e8400-e29b-41d4-a716-446655440010";

    @BeforeEach
    void setupTokens() {
        registerAuthToken(FULL_ACCESS_TOKEN, true, true);
        registerAuthToken(EXECUTE_ONLY_TOKEN, false, true, "test-ssh");
        registerAuthToken(EDIT_ONLY_TOKEN, true, false);
        registerAuthToken(WILDCARD_TOKEN, false, true, "vm/*");
        registerAuthToken(NO_PERMS_TOKEN, false, false);
    }

    private void registerAuthToken(String token, boolean canEdit, boolean canExecute, String... executeOnly) {
        var authToken = new AuthTokenEntity();
        authToken.setToken(UUID.fromString(token));
        authToken.setCanEdit(canEdit);
        authToken.setCanExecute(canExecute);
        authToken.setExecuteOnly(executeOnly);
        authTokenRepository.save(authToken);
    }

    protected String listServers() {
        return listServers(FULL_ACCESS_TOKEN);
    }

    protected String listServers(String token) {
        return callTool(token, "list_servers", Map.of());
    }

    protected String addServerConnection(String name) {
        return addServerConnection(name, FULL_ACCESS_TOKEN);
    }

    protected String addServerConnection(String name, String token) {
        return callTool(token, "add_server_connection", Map.of(
                "name", name,
                "host", SSH_CONTAINER.getHost(),
                "port", SSH_CONTAINER.getMappedPort(22),
                "username", "root",
                "password", "testpassword"
        ));
    }

    protected void addServerConnection(String name, Integer port, String privateKey, String passphrase) {
        var args = new HashMap<String, Object>();
        args.put("name", name);
        args.put("host", SSH_CONTAINER.getHost());
        if (port != null) {
            args.put("port", port);
        }
        args.put("username", "root");
        if (privateKey != null) {
            args.put("privateKey", privateKey);
        }
        if (passphrase != null) {
            args.put("privateKeySecret", passphrase);
        }
        args.put("overwrite", false);

        callTool(FULL_ACCESS_TOKEN, "add_server_connection", args);
    }

    protected String renameServerConnection(String oldName, String newName) {
        return renameServerConnection(oldName, newName, FULL_ACCESS_TOKEN);
    }

    protected String renameServerConnection(String oldName, String newName, String token) {
        return callTool(token, "rename_server_connection", Map.of("oldName", oldName, "newName", newName));
    }

    protected String removeServerConnection(String name) {
        return removeServerConnection(name, FULL_ACCESS_TOKEN);
    }

    protected String removeServerConnection(String name, String token) {
        return callTool(token, "remove_server_connection", Map.of("name", name));
    }

    protected String execute(String name, String command, String sessionId, Integer timeoutSeconds) {
        return execute(name, command, sessionId, timeoutSeconds, FULL_ACCESS_TOKEN);
    }

    protected String execute(String name, String command, String sessionId, Integer timeoutSeconds, String token) {
        return callTool(token, "execute", Map.of(
                "reasoningAndExpectations", "test",
                "name", name,
                "command", command,
                "sessionId", sessionId,
                "timeoutSeconds", timeoutSeconds != null ? timeoutSeconds : 30
        ));
    }

    @SneakyThrows
    protected String callTool(String token, String toolName, Object arguments) {
        var body = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "%s",
                    "arguments": %s
                  }
                }
                """.formatted(toolName, objectMapper.writeValueAsString(arguments));

        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl()))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        var request = requestBuilder.build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        var sseBody = response.body();
        if (sseBody.startsWith("id:")) {
            var dataLine = sseBody.lines()
                    .filter(line -> line.startsWith("data:"))
                    .findFirst()
                    .orElse("");
            return dataLine.length() > 5 ? dataLine.substring(5) : sseBody;
        }
        return sseBody;
    }

    @SneakyThrows
    protected Map<String, String> parseServerMap(String rawResponse) {
        return objectMapper.readValue(getResponseText(rawResponse), new TypeReference<>() {});
    }

    @SneakyThrows
    protected String getResponseText(String rawResponse) {
        return objectMapper.readTree(rawResponse)
                .get("result").get("content").get(0).get("text").asText();
    }

    @SneakyThrows
    protected JsonNode getExecResult(String rawResponse) {
        return objectMapper.readTree(getResponseText(rawResponse));
    }
}
