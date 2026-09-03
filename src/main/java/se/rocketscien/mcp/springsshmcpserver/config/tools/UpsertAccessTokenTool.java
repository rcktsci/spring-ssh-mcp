package se.rocketscien.mcp.springsshmcpserver.config.tools;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import se.rocketscien.mcp.springsshmcpserver.config.AuthException;
import se.rocketscien.mcp.springsshmcpserver.config.dto.ErrorResult;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;
import se.rocketscien.mcp.springsshmcpserver.service.AuthService;

@Component
@RequiredArgsConstructor
public class UpsertAccessTokenTool {

    private final AuthService authService;
    private final AuthTokenRepository authTokenRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "upsert_access_token",
            description = "Create or update an access token. If `token` UUID is omitted, a new UUID is generated. If `token` exists and `overwrite` is not true, returns an error. On update, only explicitly provided fields are applied (partial update); omitted fields keep their current values. The full token value is returned in the response (visible only here). Requires token-admin role. The current token cannot modify itself."
    )
    public String upsertAccessToken(
            @ToolParam(description = "Token UUID (optional — generates a new UUID if omitted)", required = false) String token,
            @ToolParam(description = "Whether the token can edit server connections", required = false) Boolean canEdit,
            @ToolParam(description = "Whether the token can execute commands", required = false) Boolean canExecute,
            @ToolParam(description = "Glob patterns of server names the token is allowed to execute on (empty = all)", required = false) String[] executeOnly,
            @ToolParam(description = "Whether the token can manage other access tokens", required = false) Boolean isTokenAdmin,
            @ToolParam(description = "Overwrite an existing token with the same UUID (default false)", required = false) Boolean overwrite
    ) {
        try {
            authService.requireTokenAdmin();

            boolean isUpdate = token != null && !token.isBlank();
            UUID tokenUuid;
            AuthTokenEntity entity;

            if (isUpdate) {
                try {
                    tokenUuid = UUID.fromString(token);
                } catch (IllegalArgumentException ex) {
                    return objectMapper.writeValueAsString(new ErrorResult("Invalid token UUID: " + token));
                }
                var existing = authTokenRepository.findByToken(tokenUuid);
                if (existing.isPresent()) {
                    if (!Boolean.TRUE.equals(overwrite)) {
                        return objectMapper.writeValueAsString(new ErrorResult("Token already exists, set overwrite=true to update"));
                    }
                    if (existing.get().getToken().equals(authService.currentToken())) {
                        return objectMapper.writeValueAsString(new ErrorResult("Cannot modify own token"));
                    }
                    entity = existing.get();
                } else {
                    entity = new AuthTokenEntity();
                    entity.setToken(tokenUuid);
                }
            } else {
                tokenUuid = UUID.randomUUID();
                entity = new AuthTokenEntity();
                entity.setToken(tokenUuid);
            }

            if (canEdit != null) {
                entity.setCanEdit(canEdit);
            } else if (!isUpdate) {
                entity.setCanEdit(false);
            }
            if (canExecute != null) {
                entity.setCanExecute(canExecute);
            } else if (!isUpdate) {
                entity.setCanExecute(false);
            }
            if (executeOnly != null) {
                entity.setExecuteOnly(executeOnly);
            } else if (!isUpdate) {
                entity.setExecuteOnly(new String[0]);
            }
            if (isTokenAdmin != null) {
                if (isUpdate && Boolean.TRUE.equals(isTokenAdmin) && entity.getToken().equals(authService.currentToken())) {
                    return objectMapper.writeValueAsString(new ErrorResult("Cannot modify own token"));
                }
                entity.setIsTokenAdmin(isTokenAdmin);
            } else if (!isUpdate) {
                entity.setIsTokenAdmin(false);
            }

            var saved = authTokenRepository.save(entity);

            return objectMapper.writeValueAsString(new SuccessResult(
                    saved.getToken().toString(),
                    saved.getCreatedAt()));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Failed: " + ex.getMessage()));
        }
    }

    private record SuccessResult(
            @JsonProperty("token") String token,
            @JsonProperty("createdAt") java.time.LocalDateTime createdAt) {
    }
}
