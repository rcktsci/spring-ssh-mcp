package ru.rcktsci.experiments.ai.sshmcp.ssh;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;

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

import ru.rcktsci.experiments.ai.sshmcp.domain.ServerEntity;
import ru.rcktsci.experiments.ai.sshmcp.service.SecretEncryptionService;

@Service
@Slf4j
@RequiredArgsConstructor
public class SshService {

    private final SecretEncryptionService encryptionService;

    public record SshExecutionResult(String stdout, int exitCode, boolean timedOut) {
    }

    public SshExecutionResult executeCommand(ServerEntity server, String command, int timeoutSeconds) {
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

            return executeInternal(sshClient, command, timeoutSeconds);
        } catch (Exception ex) {
            log.error("SSH execution failed on {}:{} - {}", host, port, ex.getMessage(), ex);
            throw new RuntimeException("SSH execution failed: " + ex.getMessage(), ex);
        }
    }

    @SneakyThrows
    @NonNull
    private SshExecutionResult executeInternal(SSHClient sshClient, String command, int timeoutSeconds) {
        log.info("Executing command: '{}' with timeout {} seconds", command, timeoutSeconds);
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
