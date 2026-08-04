package ru.rcktsci.experiments.ai.sshmcp.config.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import ru.rcktsci.experiments.ai.sshmcp.config.AuthException;
import ru.rcktsci.experiments.ai.sshmcp.config.dto.ErrorResult;
import ru.rcktsci.experiments.ai.sshmcp.domain.ServerEntity;
import ru.rcktsci.experiments.ai.sshmcp.service.AuthService;
import ru.rcktsci.experiments.ai.sshmcp.service.ServerManagementService;

@Component
@RequiredArgsConstructor
public class AddServerTool {

    private final ServerManagementService serverManagementService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "add_server_connection",
            description = "Register a new server connection"
    )
    public String addServer(
            @ToolParam(description = "Server name (must be unique)") String name,
            @ToolParam(description = "Hostname or IP address") String host,
            @ToolParam(description = "SSH port", required = false) Integer port,
            @ToolParam(description = "SSH username") String username,
            @ToolParam(description = "Password for authentication (plain text, stored as base64)", required = false) String password,
            @ToolParam(description = "Private key for authentication (OpenSSH format, plain text, not base64)", required = false) String privateKey,
            @ToolParam(description = "Passphrase for encrypted private key (optional, plain text, not base64)", required = false) String privateKeySecret,
            @ToolParam(description = "Overwrite existing server if name already exists (default false)", required = false) Boolean overwrite
    ) {
        try {
            authService.requireRole("EDIT");
            ServerEntity server = serverManagementService.addServer(name,
                                                                    host,
                                                                    port,
                                                                    username,
                                                                    password,
                                                                    privateKey,
                                                                    privateKeySecret,
                                                                    overwrite != null ? overwrite : false);
            return objectMapper.writeValueAsString(new SuccessResult("Success: " + server.getName()));
        } catch (IllegalArgumentException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Failed: " + ex.getMessage()));
        }
    }

    private record SuccessResult(@JsonProperty("success") String success) {
    }
}
