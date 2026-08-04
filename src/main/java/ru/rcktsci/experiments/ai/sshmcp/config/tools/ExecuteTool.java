package ru.rcktsci.experiments.ai.sshmcp.config.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import ru.rcktsci.experiments.ai.sshmcp.config.AuthException;
import ru.rcktsci.experiments.ai.sshmcp.config.dto.ErrorResult;
import ru.rcktsci.experiments.ai.sshmcp.config.dto.ExecuteResult;
import ru.rcktsci.experiments.ai.sshmcp.domain.ExecutionHistoryEntity;
import ru.rcktsci.experiments.ai.sshmcp.service.AuthService;
import ru.rcktsci.experiments.ai.sshmcp.service.ServerManagementService;

@Component
@RequiredArgsConstructor
public class ExecuteTool {

    private final ServerManagementService serverManagementService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "execute",
            description = "Execute a command on a remote server"
    )
    public String execute(
            @ToolParam(description = "Describe briefly: what the command does? + is it destructive? + what are expectations?") String reasoningAndExpectations,
            @ToolParam(description = "Server name") String name,
            @ToolParam(description = "Command to execute") String command,
            @ToolParam(description = "Generate once itself (alphanumeric 10+) and reuse the same value") String sessionId,
            @ToolParam(description = "Timeout in seconds (optional, default 30)", required = false) Integer timeoutSeconds
    ) {
        try {
            authService.requireRole("EXECUTE");
            if (!authService.canExecuteOnServer(name)) {
                throw new AuthException("Server not allowed for this token");
            }
            ExecutionHistoryEntity result = serverManagementService.execute(sessionId, name, command, timeoutSeconds);
            return objectMapper.writeValueAsString(new ExecuteResult(
                    result.getResult(),
                    result.getExitCode(),
                    result.getTimedOut()
            ));
        } catch (IllegalArgumentException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Execution failed: " + ex.getMessage()));
        }
    }
}
