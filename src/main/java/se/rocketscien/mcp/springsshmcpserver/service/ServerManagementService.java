package se.rocketscien.mcp.springsshmcpserver.service;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import se.rocketscien.mcp.springsshmcpserver.config.EncryptionConstants;
import se.rocketscien.mcp.springsshmcpserver.domain.ExecutionHistoryEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.ExecutionHistoryRepository;
import se.rocketscien.mcp.springsshmcpserver.domain.ServerEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.ServerRepository;
import se.rocketscien.mcp.springsshmcpserver.ssh.SshService;

@Service
@RequiredArgsConstructor
public class ServerManagementService {

    private static final int DEFAULT_TIMEOUT = 30;

    private final ServerRepository serverRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final SshService sshService;
    private final SecretEncryptionService encryptionService;

    public Map<String, String> listServers() {
        return serverRepository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        ServerEntity::getName,
                        ServerEntity::getAddress
                ));
    }

    public ServerEntity addServer(
            String name, String host, Integer port, String username,
            String password, String privateKey, String privateKeySecret, boolean overwrite
    ) {
        if ((password == null && privateKey == null) || (password != null && privateKey != null)) {
            throw new IllegalArgumentException("Exactly one of password or private key must be provided");
        }

        var existing = serverRepository.findByName(name);
        if (existing.isPresent()) {
            if (!overwrite) {
                throw new IllegalArgumentException("Server '" + name + "' already exists, use another name or set overwrite=true");
            }
            ServerEntity server = existing.get();
            server.setHost(host);
            server.setPort(port != null ? port : 22);
            server.setUsername(username);
            server.setPassword(password != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(password) : null);
            server.setPrivateKey(privateKey != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(privateKey) : null);
            server.setPrivateKeySecret(privateKeySecret != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(privateKeySecret) : null);
            return serverRepository.save(server);
        }

        ServerEntity server = new ServerEntity();
        server.setName(name);
        server.setHost(host);
        server.setPort(port != null ? port : 22);
        server.setUsername(username);
        server.setPassword(password != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(password) : null);
        server.setPrivateKey(privateKey != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(privateKey) : null);
        server.setPrivateKeySecret(privateKeySecret != null ? EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(privateKeySecret) : null);

        return serverRepository.save(server);
    }

    public void removeServer(String name) {
        ServerEntity server = serverRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + name));
        serverRepository.delete(server);
    }

    public void renameServer(String oldName, String newName) {
        ServerEntity server = serverRepository.findByName(oldName)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + oldName));
        if (serverRepository.findByName(newName).isPresent()) {
            throw new IllegalArgumentException("Server '" + newName + "' already exists");
        }
        server.setName(newName);
        serverRepository.save(server);
    }

    /**
     * Partial update: a {@code null} argument means "leave as is", a blank one is rejected. The secrets are
     * applied in a fixed order, and this is the part that must not lose data:
     * <ul>
     *   <li>a {@code password} switches the server to {@code PASSWORD} and drops key and passphrase;</li>
     *   <li>a {@code privateKey} drops the password and keeps the stored passphrase, unless a
     *       {@code privateKeySecret} sent with it replaces that one;</li>
     *   <li>a lone {@code privateKeySecret} replaces the passphrase of a key server and is ignored on a
     *       password server: honoring it there would clear the password the caller never mentioned and leave
     *       the server with no secret at all, the {@code authType} of {@code NONE} that no API call can
     *       produce.</li>
     * </ul>
     */
    public ServerEntity updateServer(
            Long id, String name, String host, Integer port, String username,
            String password, String privateKey, String privateKeySecret
    ) {
        var server = serverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + id));

        // A blank secret is an unfilled field, not "set the secret to empty": addServer stores an empty
        // password as a real one, but here it would overwrite a working secret with nothing.
        var newPassword = blankToNull(password);
        var newPrivateKey = blankToNull(privateKey);
        var newPrivateKeySecret = blankToNull(privateKeySecret);
        if (newPassword != null && newPrivateKey != null) {
            throw new IllegalArgumentException("Exactly one of password or private key must be provided");
        }

        // Validated before anything is written, so a rejected request leaves the entity untouched.
        rejectBlank(name, "name");
        rejectBlank(host, "host");
        rejectBlank(username, "username");
        if (name != null) {
            var sameName = serverRepository.findByName(name);
            // The edited server matches itself, otherwise the form's prefilled name would reject every edit.
            if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
                throw new IllegalArgumentException("Server '" + name + "' already exists");
            }
        }

        if (name != null) {
            server.setName(name);
        }
        if (host != null) {
            server.setHost(host);
        }
        if (username != null) {
            server.setUsername(username);
        }
        if (port != null) {
            server.setPort(port);
        }

        applySecrets(server, newPassword, newPrivateKey, newPrivateKeySecret);

        return serverRepository.save(server);
    }

    private void applySecrets(ServerEntity server, String password, String privateKey, String privateKeySecret) {
        if (password != null) {
            server.setPassword(encryptSecret(password));
            server.setPrivateKey(null);
            server.setPrivateKeySecret(null);
            return;
        }
        if (privateKey != null) {
            server.setPassword(null);
            server.setPrivateKey(encryptSecret(privateKey));
            if (privateKeySecret != null) {
                server.setPrivateKeySecret(encryptSecret(privateKeySecret));
            }
            return;
        }
        if (privateKeySecret != null && server.getPrivateKey() != null) {
            server.setPrivateKeySecret(encryptSecret(privateKeySecret));
        }
    }

    private String encryptSecret(String value) {
        return EncryptionConstants.ENCRYPTED_PREFIX + encryptionService.encrypt(value);
    }

    /** {@code null} means "leave as is", so only a value that is present and blank is rejected. */
    private static void rejectBlank(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public ExecutionHistoryEntity execute(String sessionId, String name, String command, Integer timeoutSeconds,
                                          Map<String, String> environmentVariables) {
        ServerEntity server = serverRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + name));

        int timeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT;

        SshService.SshExecutionResult result = sshService.executeCommand(
                server, command, timeout, environmentVariables
        );

        ExecutionHistoryEntity exec = new ExecutionHistoryEntity();
        exec.setSessionId(sessionId);
        exec.setName(name);
        exec.setServer(server.getAddress());
        exec.setCommand(command);
        exec.setResult(result.stdout());
        exec.setExitCode(result.exitCode());
        exec.setTimedOut(result.timedOut());

        return executionHistoryRepository.save(exec);
    }
}
