package ru.rcktsci.experiments.ai.sshmcp.service;

import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import ru.rcktsci.experiments.ai.sshmcp.config.EncryptionConstants;
import ru.rcktsci.experiments.ai.sshmcp.domain.ExecutionHistoryEntity;
import ru.rcktsci.experiments.ai.sshmcp.domain.ExecutionHistoryRepository;
import ru.rcktsci.experiments.ai.sshmcp.domain.ServerEntity;
import ru.rcktsci.experiments.ai.sshmcp.domain.ServerRepository;
import ru.rcktsci.experiments.ai.sshmcp.ssh.SshService;

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

    public ExecutionHistoryEntity execute(String sessionId, String name, String command, Integer timeoutSeconds) {
        ServerEntity server = serverRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + name));

        int timeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT;

        SshService.SshExecutionResult result = sshService.executeCommand(
                server, command, timeout
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
