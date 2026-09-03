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
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;
import se.rocketscien.mcp.springsshmcpserver.service.AuthService;

@Component
@RequiredArgsConstructor
public class DeleteAccessTokenTool {

    private final AuthService authService;
    private final AuthTokenRepository authTokenRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "delete_access_token",
            description = "Delete an access token by UUID. Requires token-admin role. The current token cannot delete itself."
    )
    public String deleteAccessToken(
            @ToolParam(description = "Token UUID to delete") String token
    ) {
        try {
            authService.requireTokenAdmin();

            UUID tokenUuid;
            try {
                tokenUuid = UUID.fromString(token);
            } catch (IllegalArgumentException ex) {
                return objectMapper.writeValueAsString(new ErrorResult("Invalid token UUID: " + token));
            }

            var existing = authTokenRepository.findByToken(tokenUuid);
            if (existing.isEmpty()) {
                return objectMapper.writeValueAsString(new ErrorResult("Token not found: " + token));
            }
            if (existing.get().getToken().equals(authService.currentToken())) {
                return objectMapper.writeValueAsString(new ErrorResult("Cannot delete own token"));
            }

            authTokenRepository.delete(existing.get());

            return objectMapper.writeValueAsString(new SuccessResult("Success: token " + token + " deleted"));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Failed: " + ex.getMessage()));
        }
    }

    private record SuccessResult(@JsonProperty("success") String success) {
    }
}
