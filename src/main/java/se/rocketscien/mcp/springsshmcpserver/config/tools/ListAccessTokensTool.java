package se.rocketscien.mcp.springsshmcpserver.config.tools;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import se.rocketscien.mcp.springsshmcpserver.config.AuthException;
import se.rocketscien.mcp.springsshmcpserver.config.dto.ErrorResult;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;
import se.rocketscien.mcp.springsshmcpserver.service.AuthService;

@Component
@RequiredArgsConstructor
public class ListAccessTokensTool {

    private final AuthService authService;
    private final AuthTokenRepository authTokenRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Tool(
            name = "list_access_tokens",
            description = "List all access tokens (token value, canEdit, canExecute, isTokenAdmin, executeOnly, createdAt). Requires token-admin role."
    )
    public String listAccessTokens() {
        try {
            authService.requireTokenAdmin();

            var tokens = authTokenRepository.findAll().stream()
                    .map(t -> new TokenView(
                            t.getToken().toString(),
                            Boolean.TRUE.equals(t.getCanEdit()),
                            Boolean.TRUE.equals(t.getCanExecute()),
                            Boolean.TRUE.equals(t.getIsTokenAdmin()),
                            t.getExecuteOnly(),
                            t.getCreatedAt()))
                    .toList();

            return objectMapper.writeValueAsString(new SuccessResult(tokens));
        } catch (AuthException ex) {
            return objectMapper.writeValueAsString(new ErrorResult(ex.getMessage()));
        } catch (Exception ex) {
            return objectMapper.writeValueAsString(new ErrorResult("Failed: " + ex.getMessage()));
        }
    }

    private record SuccessResult(@JsonProperty("tokens") List<TokenView> tokens) {
    }

    private record TokenView(
            @JsonProperty("token") String token,
            @JsonProperty("canEdit") boolean canEdit,
            @JsonProperty("canExecute") boolean canExecute,
            @JsonProperty("isTokenAdmin") boolean isTokenAdmin,
            @JsonProperty("executeOnly") String[] executeOnly,
            @JsonProperty("createdAt") java.time.LocalDateTime createdAt) {
    }
}
