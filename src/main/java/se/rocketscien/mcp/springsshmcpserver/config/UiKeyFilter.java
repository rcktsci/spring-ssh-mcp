package se.rocketscien.mcp.springsshmcpserver.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

import tools.jackson.databind.ObjectMapper;
import se.rocketscien.mcp.springsshmcpserver.config.dto.ErrorResult;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class UiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-UI-Key";
    public static final String ACCESS_DENIED = "Access denied";

    private static final String API_PATH = "/api";

    private final ObjectMapper objectMapper;
    private final String masterKey;

    public UiKeyFilter(ObjectMapper objectMapper, @Value("${ui.master-key:}") String masterKey) {
        this.objectMapper = objectMapper;
        this.masterKey = masterKey;
    }

    @PostConstruct
    void warnIfNotConfigured() {
        if (masterKey.isEmpty()) {
            log.warn("UI_MASTER_KEY is not set: {} will reject every request", HEADER);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = routedPath(request);
        return !path.equals(API_PATH) && !path.startsWith(API_PATH + "/");
    }

    /**
     * The path as the {@code DispatcherServlet} routes it: percent-decoded, matrix parameters stripped and
     * without the context path. {@code getRequestURI()} must not be used here, it is the raw undecoded request
     * target, so {@code /a%70i/tokens} and {@code /api;x=1/tokens} would skip the filter while still reaching
     * an {@code /api/**} handler.
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        if (!hasMasterKey(request)) {
            deny(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasMasterKey(HttpServletRequest request) {
        if (masterKey.isEmpty()) {
            return false;
        }

        String providedKey = request.getHeader(HEADER);
        return providedKey != null
               && MessageDigest.isEqual(masterKey.getBytes(StandardCharsets.UTF_8),
                                        providedKey.getBytes(StandardCharsets.UTF_8));
    }

    private void deny(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResult(ACCESS_DENIED)));
    }
}