package se.rocketscien.mcp.springsshmcpserver.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import se.rocketscien.mcp.springsshmcpserver.domain.ServerEntity;
import se.rocketscien.mcp.springsshmcpserver.service.SecretEncryptionService;

@Service
@Slf4j
@RequiredArgsConstructor
public class SshService {

    private final SecretEncryptionService encryptionService;

    public record SshExecutionResult(String stdout, int exitCode, boolean timedOut) {
    }

    /**
     * Runs {@code command} on {@code target}, reached through {@code chain}. The chain holds every server the
     * connection passes through, in connect order, with the target last; a single entry is a direct connection.
     * Each hop is a separate SSH connection authenticated with that server's own credentials.
     */
    public SshExecutionResult executeCommand(List<ServerEntity> chain, String command, int timeoutSeconds,
                                             Map<String, String> environmentVariables) {
        String effectiveCommand = withEnvironmentVariables(command, environmentVariables);
        ensurePayloadFits(effectiveCommand);

        var target = chain.get(chain.size() - 1);
        var host = target.getHost();
        var port = target.getPort();
        int envCount = environmentVariables == null ? 0 : environmentVariables.size();
        log.info("Executing command on {}:{} as {} through {} ({} env var(s)): '{}'", host, port, target.getUsername(),
                 chain.size() == 1 ? "a direct connection" : (chain.size() - 1) + " jump host(s)", envCount,
                 abbreviate(command));

        try {
            return executeChain(chain, effectiveCommand, timeoutSeconds);
        } catch (IOException ex) {
            log.error("SSH I/O failure on {}:{} - {}", host, port, describe(ex), ex);
            throw new RuntimeException("SSH connection error (connection lost or channel rejected): " + describe(ex), ex);
        } catch (Exception ex) {
            log.error("SSH execution failed on {}:{} - {}", host, port, describe(ex), ex);
            throw new RuntimeException("SSH execution failed: " + describe(ex), ex);
        }
    }

    /**
     * Opens one connection per hop and runs the command on the last one. The clients are pushed before their
     * first fallible call, so the {@code finally} always owns them, and popped in reverse: the target is
     * closed before the jump hosts it was tunnelled through.
     */
    private SshExecutionResult executeChain(List<ServerEntity> chain, String command, int timeoutSeconds)
            throws IOException {
        Deque<SSHClient> clients = new ArrayDeque<>();
        try {
            SSHClient previous = null;
            for (ServerEntity hop : chain) {
                SSHClient client = newClient();
                clients.push(client);
                if (previous == null) {
                    log.info("Connecting to {}:{} as {}", hop.getHost(), hop.getPort(), hop.getUsername());
                    client.connect(hop.getHost(), hop.getPort());
                } else {
                    log.info("Tunnelling to {}:{} as {}", hop.getHost(), hop.getPort(), hop.getUsername());
                    client.connectVia(previous.newDirectConnection(hop.getHost(), hop.getPort()));
                }
                log.info("Connected successfully to {}:{}", hop.getHost(), hop.getPort());
                authenticate(client, hop);
                previous = client;
            }

            return executeInternal(clients.peek(), command, timeoutSeconds);
        } finally {
            while (!clients.isEmpty()) {
                closeQuietly(clients.pop());
            }
        }
    }

    private static SSHClient newClient() {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        return client;
    }

    /** Closing a jump host reports the same errors as a failed command, so they are logged and dropped. */
    private static void closeQuietly(SSHClient client) {
        try {
            client.close();
        } catch (IOException ex) {
            log.warn("Error while closing an SSH connection: {}", describe(ex));
        }
    }

    private void authenticate(SSHClient sshClient, ServerEntity server) throws IOException {
        if (server.getPassword() != null) {
            String password = encryptionService.decrypt(server.getPassword());
            log.info("Authenticating with password for user '{}'", server.getUsername());
            sshClient.authPassword(server.getUsername(), password);
        } else if (server.getPrivateKey() != null) {
            String privateKey = encryptionService.decrypt(server.getPrivateKey());
            String rawPassphrase = server.getPrivateKeySecret();
            log.info("Authenticating with key for user '{}'", server.getUsername());
            KeyProvider keyProvider;
            if (rawPassphrase == null || rawPassphrase.isEmpty()) {
                keyProvider = sshClient.loadKeys(privateKey, null, null);
            } else {
                String passphrase = encryptionService.decrypt(rawPassphrase);
                keyProvider = sshClient.loadKeys(privateKey, null, new SshPasswordFinder(passphrase));
            }
            sshClient.authPublickey(server.getUsername(), keyProvider);
        }
    }

    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    private static void ensurePayloadFits(String effectiveCommand) {
        int size = effectiveCommand.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Command payload too large: " + size + " bytes (max "
                    + MAX_PAYLOAD_BYTES + "). Shorten the command or reduce environment variable values.");
        }
    }

    private static String describe(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private static String withEnvironmentVariables(String command, Map<String, String> environmentVariables) {
        if (environmentVariables == null || environmentVariables.isEmpty()) {
            return command;
        }
        String assignments = environmentVariables.entrySet().stream()
                .map(entry -> {
                    String name = entry.getKey();
                    if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        throw new IllegalArgumentException("Invalid environment variable name: " + name);
                    }
                    return name + "='" + escapeSingleQuotes(entry.getValue()) + "'";
                })
                .collect(Collectors.joining(" "));
        return "export " + assignments + "; " + command;
    }

    private static String escapeSingleQuotes(String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }

    private static final int MAX_LOGGED_COMMAND_CHARS = 200;

    private static String abbreviate(String command) {
        if (command == null || command.length() <= MAX_LOGGED_COMMAND_CHARS) {
            return command;
        }
        return command.substring(0, MAX_LOGGED_COMMAND_CHARS) + "... (" + command.length() + " chars)";
    }

    @SneakyThrows
    @NonNull
    private SshExecutionResult executeInternal(SSHClient sshClient, String command, int timeoutSeconds) {
        final boolean[] timedOut = {false};
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Session session = sshClient.startSession();
        Session.Command sessionCommand = session.exec(command);

        Thread readerThread = Thread.startVirtualThread(() -> {
            try {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = sessionCommand.getInputStream().read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                while ((bytesRead = sessionCommand.getErrorStream().read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
            } catch (Exception ignored) {
            }
        });

        try {
            sessionCommand.join(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ex) {
            timedOut[0] = true;
            session.close();
        }

        readerThread.join(100);

        return new SshExecutionResult(
                baos.toString(),
                sessionCommand.getExitStatus() != null ? sessionCommand.getExitStatus() : -1,
                timedOut[0]
        );
    }

    private record SshPasswordFinder(String passphrase) implements PasswordFinder {

        @Override
        public char[] reqPassword(Resource resource) {
            return passphrase.toCharArray();
        }

        @Override
        public boolean shouldRetry(Resource resource) {
            return false;
        }
    }
}
