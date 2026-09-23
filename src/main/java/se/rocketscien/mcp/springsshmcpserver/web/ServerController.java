package se.rocketscien.mcp.springsshmcpserver.web;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import se.rocketscien.mcp.springsshmcpserver.domain.ServerEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.ServerRepository;
import se.rocketscien.mcp.springsshmcpserver.service.ServerManagementService;

/**
 * REST access is authorized by {@code UiKeyFilter} alone, so unlike the MCP tools this controller applies no
 * token roles: any holder of the UI master key may list, create, update and delete servers.
 */
@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private static final int DEFAULT_PORT = 22;
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final ServerRepository serverRepository;
    private final ServerManagementService serverManagementService;

    /** Sorted by {@code id}: {@code findAll()} does not promise an order, and the table is redrawn on every
     * refresh, so unsorted rows would make the servers jump between requests. Same reason as the token list. */
    @GetMapping
    public List<ServerView> list() {
        return serverRepository.findAll().stream()
                .sorted(Comparator.comparing(ServerEntity::getId))
                .map(ServerController::toView)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServerSummary create(@RequestBody ServerRequest request) {
        requireNonBlank(request.name(), "name");
        requireNonBlank(request.host(), "host");
        requireNonBlank(request.username(), "username");
        var port = parsePort(request.port());

        // Uniqueness is checked here rather than left to addServer, which reports a duplicate with the
        // MCP-only hint "set overwrite=true" and would honour an overwrite request.
        if (serverRepository.findByName(request.name()).isPresent()) {
            throw new IllegalArgumentException("Server '" + request.name() + "' already exists");
        }

        var created = serverManagementService.addServer(request.name(),
                                                       request.host(),
                                                       port != null ? port : DEFAULT_PORT,
                                                       request.username(),
                                                       request.password(),
                                                       request.privateKey(),
                                                       // addServer stores a passphrase unconditionally, so a password
                                                       // server created with one would carry a secret no API call and no
                                                       // authType ever mentions: a later PATCH of a bare privateKey would
                                                       // keep it and silently make the server PRIVATE_KEY_WITH_PASS.
                                                       request.password() != null ? null : request.privateKeySecret(),
                                                       false);
        return new ServerSummary(created.getId(), created.getName());
    }

    @PatchMapping("/{id}")
    public ServerSummary update(@PathVariable Long id, @RequestBody ServerRequest request) {
        requireServer(id);
        var port = parsePort(request.port());

        var updated = serverManagementService.updateServer(id,
                                                          request.name(),
                                                          request.host(),
                                                          port,
                                                          request.username(),
                                                          request.password(),
                                                          request.privateKey(),
                                                          request.privateKeySecret());
        return new ServerSummary(updated.getId(), updated.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        serverRepository.delete(requireServer(id));
    }

    private ServerEntity requireServer(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found: " + id));
    }

    /** The columns are {@code nullable=false}, so an unchecked value would otherwise surface as a 500. */
    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
    }

    /**
     * Parsed here rather than by the mapper, and deliberately so: a {@code port} typed as a number would let
     * Jackson read {@code ""} as "absent", which would turn a PATCH the contract rejects into a silent no-op.
     * The same mapper also deserializes the {@code /mcp} tool arguments, whose behaviour is frozen, so its
     * coercion settings stay untouched. {@code null} means "absent" — the caller leaves the port alone on
     * PATCH and gets the default on POST; anything present has to be a real port, {@code 0} and above
     * {@code 65535} included, because the form's {@code min}/{@code max} are not validation.
     */
    private static Integer parsePort(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException("'port' is required");
        }
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'port' must be a number");
        }
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("'port' must be between " + MIN_PORT + " and " + MAX_PORT);
        }
        return port;
    }

    private static ServerView toView(ServerEntity entity) {
        return new ServerView(
                entity.getId(),
                entity.getName(),
                entity.getHost(),
                entity.getPort(),
                entity.getUsername(),
                entity.getAuthType(),
                entity.getCreatedAt());
    }

    /**
     * Secrets are accepted on write and never returned: {@link ServerView} carries {@code authType} instead.
     * {@code port} is a {@code String} on purpose — see {@link #parsePort(String)}.
     */
    public record ServerRequest(String name, String host, String port, String username, String password,
                                String privateKey, String privateKeySecret) {
    }

    public record ServerView(Long id, String name, String host, Integer port, String username, String authType,
                             LocalDateTime createdAt) {
    }

    /** Both writes answer with the identity alone: the server, and its {@code authType}, are read back from
     * {@code GET /api/servers}. */
    public record ServerSummary(Long id, String name) {
    }
}
