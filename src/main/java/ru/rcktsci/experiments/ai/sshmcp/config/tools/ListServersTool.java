package ru.rcktsci.experiments.ai.sshmcp.config.tools;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import ru.rcktsci.experiments.ai.sshmcp.service.AuthService;
import ru.rcktsci.experiments.ai.sshmcp.service.ServerManagementService;

@Component
@RequiredArgsConstructor
public class ListServersTool {

    private final ServerManagementService serverManagementService;

    @Tool(
            name = "list_servers",
            description = "List all registered server connections (name -> user@host:port)"
    )
    public Map<String, String> listServers() {
        var all = serverManagementService.listServers();

        var token = AuthService.CURRENT.get();
        if (token.getExecuteOnly() == null || token.getExecuteOnly().length == 0) {
            return all;
        }

        return all.entrySet().stream()
                .filter(e -> token.canExecuteOnServer(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
