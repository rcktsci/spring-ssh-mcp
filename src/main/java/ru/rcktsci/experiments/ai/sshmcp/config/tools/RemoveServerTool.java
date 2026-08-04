package ru.rcktsci.experiments.ai.sshmcp.config.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import ru.rcktsci.experiments.ai.sshmcp.config.AuthException;
import ru.rcktsci.experiments.ai.sshmcp.config.dto.ErrorResult;
import ru.rcktsci.experiments.ai.sshmcp.service.AuthService;
import ru.rcktsci.experiments.ai.sshmcp.service.ServerManagementService;

@Component
@RequiredArgsConstructor
public class RemoveServerTool {

    private final ServerManagementService serverManagementService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "remove_server_connection",
            description = "Unregister a server connection"
    )
    public String removeServer(
            @ToolParam(description = "Server name to remove") String name
    ) {
        try {
            authService.requireRole("EDIT");
            serverManagementService.removeServer(name);
            return objectMapper.writeValueAsString(new SuccessResult("Success"));
        } catch (IllegalArgumentException ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Server not found: " + name));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Failed: " + ex.getMessage()));
        }
    }

    private record SuccessResult(@JsonProperty("success") String success) {
    }
}
