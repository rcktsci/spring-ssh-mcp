package se.rocketscien.mcp.springsshmcpserver.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    public SshExecutionResult executeCommand(ServerEntity server, String command, int timeoutSeconds,
                                             Map<String, String> environmentVariables) {
        String effectiveCommand = withEnvironmentVariables(command, environmentVariables);
        ensurePayloadFits(effectiveCommand);
        var host = server.getHost();
        var port = server.getPort();
        log.info("Connecting to {}:{} as {}", host, port, server.getUsername());
        try (SSHClient sshClient = new SSHClient()) {
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            log.info("Attempting connection to {}:{}", host, port);
            sshClient.connect(host, port);
            log.info("Connected successfully to {}:{}", host, port);

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

            int envCount = environmentVariables == null ? 0 : environmentVariables.size();
            log.info("Executing command on {}:{} ({} env var(s)): '{}'", host, port, envCount, abbreviate(command));
            return executeInternal(sshClient, effectiveCommand, timeoutSeconds);
        } catch (IOException ex) {
            log.error("SSH I/O failure on {}:{} - {}", host, port, describe(ex), ex);
            throw new RuntimeException("SSH connection error (connection lost or channel rejected): " + describe(ex), ex);
        } catch (Exception ex) {
            log.error("SSH execution failed on {}:{} - {}", host, port, describe(ex), ex);
            throw new RuntimeException("SSH execution failed: " + describe(ex), ex);
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
