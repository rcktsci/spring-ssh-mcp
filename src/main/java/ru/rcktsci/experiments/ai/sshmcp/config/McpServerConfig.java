package ru.rcktsci.experiments.ai.sshmcp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.rcktsci.experiments.ai.sshmcp.config.tools.AddServerTool;
import ru.rcktsci.experiments.ai.sshmcp.config.tools.ExecuteTool;
import ru.rcktsci.experiments.ai.sshmcp.config.tools.GenerateSessionTool;
import ru.rcktsci.experiments.ai.sshmcp.config.tools.ListServersTool;
import ru.rcktsci.experiments.ai.sshmcp.config.tools.RemoveServerTool;
import ru.rcktsci.experiments.ai.sshmcp.config.tools.RenameServerTool;

@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    private final ListServersTool listServersTool;
    private final AddServerTool addServerTool;
    private final RemoveServerTool removeServerTool;
    private final RenameServerTool renameServerTool;
    private final GenerateSessionTool generateSessionTool;
    private final ExecuteTool executeTool;

    @Bean
    public MethodToolCallbackProvider listServersToolCallbackProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(listServersTool, addServerTool, removeServerTool, renameServerTool, generateSessionTool, executeTool)
                .build();
    }
}
