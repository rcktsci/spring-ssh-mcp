package se.rocketscien.mcp.springsshmcpserver.config;

import java.io.IOException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.ServletRequestPathUtils;

import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;
import se.rocketscien.mcp.springsshmcpserver.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final String MCP_PATH = "/mcp";

    private final AuthTokenRepository authTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            ScopedValue.where(AuthService.CURRENT, token)
                    .call(() -> {
                        filterChain.doFilter(request, response);
                        return null;
                    });
        } catch (ServletException | IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private AuthTokenEntity extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        try {
            String tokenString = authHeader.startsWith("Bearer ")
                                 ? authHeader.substring(7)
                                 : authHeader;
            UUID tokenUuid = UUID.fromString(tokenString);
            return authTokenRepository.findByToken(tokenUuid).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = routedPath(request);
        return !path.equals(MCP_PATH) && !path.startsWith(MCP_PATH + "/");
    }

    /**
     * The path as the {@code DispatcherServlet} routes it: percent-decoded, matrix parameters stripped and
     * without the context path. {@code getRequestURI()} must not be used here, it is the raw undecoded request
     * target, so {@code /m%63p/message} and {@code /mcp;x=1/message} would skip the filter while still reaching
     * the MCP endpoint.
     */
    private static String routedPath(HttpServletRequest request) {
        var path = ServletRequestPathUtils.parse(request).pathWithinApplication();

        var routed = new StringBuilder();
        for (PathContainer.Element element : path.elements()) {
            if (element instanceof PathContainer.PathSegment segment) {
                routed.append('/').append(segment.valueToMatch());
            }
        }
        return routed.isEmpty() ? "/" : routed.toString();
    }
}
