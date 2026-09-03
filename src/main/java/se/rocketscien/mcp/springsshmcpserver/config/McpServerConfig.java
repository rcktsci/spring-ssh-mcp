package se.rocketscien.mcp.springsshmcpserver.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import se.rocketscien.mcp.springsshmcpserver.config.tools.AddServerTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.DeleteAccessTokenTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.ExecuteTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.GenerateSessionTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.ListAccessTokensTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.ListServersTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.RemoveServerTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.RenameServerTool;
import se.rocketscien.mcp.springsshmcpserver.config.tools.UpsertAccessTokenTool;

@Configuration
@RequiredArgsConstructor
public class McpServerConfig {

    private final ListServersTool listServersTool;
    private final AddServerTool addServerTool;
    private final RemoveServerTool removeServerTool;
    private final RenameServerTool renameServerTool;
    private final GenerateSessionTool generateSessionTool;
    private final ExecuteTool executeTool;
    private final ListAccessTokensTool listAccessTokensTool;
    private final UpsertAccessTokenTool upsertAccessTokenTool;
    private final DeleteAccessTokenTool deleteAccessTokenTool;

    @Bean
    public MethodToolCallbackProvider listServersToolCallbackProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(listServersTool,
                             addServerTool,
                             removeServerTool,
                             renameServerTool,
                             generateSessionTool,
                             executeTool,
                             listAccessTokensTool,
                             upsertAccessTokenTool,
                             deleteAccessTokenTool)
                .build();
    }
}
