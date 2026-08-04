package se.rocketscien.mcp.springsshmcpserver.config;

import java.io.IOException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

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
        String uri = request.getRequestURI();
        return !uri.equals("/mcp") && !uri.startsWith("/mcp/");
    }
}
