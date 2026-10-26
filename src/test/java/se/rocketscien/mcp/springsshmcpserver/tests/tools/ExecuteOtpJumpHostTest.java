package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.SneakyThrows;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.KeySetPublickeyAuthenticator;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.ssh.TotpGenerator;
import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end OTP + jump host combinations. The jump host is an embedded MINA SSH server configured like an
 * OpenSSH {@code AuthenticationMethods publickey,keyboard-interactive} box: it accepts the SSH key first and
 * then asks for a TOTP code. The target host covers the four auth shapes: password, key, password + OTP, and
 * key + OTP (a different secret), reached through the jump host.
 */
class ExecuteOtpJumpHostTest extends BaseCallToolTest {

    private static final String JUMP_OTP_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String TARGET_OTP_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String TARGET_PASSWORD = "targetpass";

    private static SshServer jumpSshd;
    private static SshServer targetSshd;

    private static KeyPair jumpKeyPair;
    private static KeyPair targetKeyPair;

    private enum TargetMode {
        PASSWORD,
        KEY,
        PASSWORD_OTP,
        KEY_OTP
    }

    @BeforeAll
    static void setUpKeysAndJumpHost() throws Exception {
        jumpKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        targetKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        jumpSshd = newJumpHost();
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (jumpSshd != null) {
            jumpSshd.stop();
        }
        stopTargetServer();
    }

    private static SshServer baseServer(String hostKeyFile) {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        SimpleGeneratorHostKeyProvider hostKeyProvider = new SimpleGeneratorHostKeyProvider(Path.of("target", hostKeyFile));
        hostKeyProvider.setAlgorithm("RSA");
        sshd.setKeyPairProvider(hostKeyProvider);
        sshd.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        return sshd;
    }

    @SneakyThrows
    private static SshServer newJumpHost() {
        SshServer sshd = baseServer("otp-jump-hostkey.ser");
        sshd.setPublickeyAuthenticator(new KeySetPublickeyAuthenticator("jump", Set.of(jumpKeyPair.getPublic())));
        sshd.setKeyboardInteractiveAuthenticator(otpOnlyAuthenticator(JUMP_OTP_SECRET));
        // Like OpenSSH "AuthenticationMethods publickey,keyboard-interactive": both must pass, in order.
        sshd.getProperties().put(CoreModuleProperties.AUTH_METHODS.getName(), "publickey,keyboard-interactive");
        sshd.setCommandFactory(identityCommandFactory());
        sshd.start();
        return sshd;
    }

    @SneakyThrows
    private static void startTargetServer(TargetMode mode) {
        stopTargetServer();
        targetSshd = baseServer("otp-target-hostkey.ser");
        switch (mode) {
            case PASSWORD -> {
                targetSshd.setPasswordAuthenticator(passwordAuthenticator(TARGET_PASSWORD));
            }
            case KEY -> {
                targetSshd.setPublickeyAuthenticator(new KeySetPublickeyAuthenticator("target", Set.of(targetKeyPair.getPublic())));
            }
            case PASSWORD_OTP -> {
                targetSshd.setPasswordAuthenticator(passwordAuthenticator(TARGET_PASSWORD));
                targetSshd.setKeyboardInteractiveAuthenticator(passwordOtpAuthenticator(TARGET_PASSWORD, TARGET_OTP_SECRET));
                targetSshd.getProperties().put(CoreModuleProperties.AUTH_METHODS.getName(), "password,keyboard-interactive");
            }
            case KEY_OTP -> {
                targetSshd.setPublickeyAuthenticator(new KeySetPublickeyAuthenticator("target", Set.of(targetKeyPair.getPublic())));
                targetSshd.setKeyboardInteractiveAuthenticator(otpOnlyAuthenticator(TARGET_OTP_SECRET));
                targetSshd.getProperties().put(CoreModuleProperties.AUTH_METHODS.getName(), "publickey,keyboard-interactive");
            }
        }
        targetSshd.setCommandFactory(identityCommandFactory());
        targetSshd.start();
    }

    private static void stopTargetServer() {
        if (targetSshd != null) {
            try {
                targetSshd.stop();
            } catch (IOException ignored) {
            }
            targetSshd = null;
        }
    }

    private static PasswordAuthenticator passwordAuthenticator(String expected) {
        return (username, password, session) -> expected.equals(new String(password));
    }

    /** Accepts the current and the previous time step, so a test at a 30-second boundary is not flaky. */
    private static boolean validOtp(String secret, String response) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        return TotpGenerator.generateAt(secret, nowSeconds).equals(response)
                || TotpGenerator.generateAt(secret, nowSeconds - 30).equals(response);
    }

    private static KeyboardInteractiveAuthenticator otpOnlyAuthenticator(String secret) {
        return new KeyboardInteractiveAuthenticator() {
            @Override
            public InteractiveChallenge generateChallenge(org.apache.sshd.server.session.ServerSession session,
                                                          String username, String name, String instruction) {
                InteractiveChallenge challenge = new InteractiveChallenge();
                challenge.addPrompt("Verification code: ", false);
                return challenge;
            }

            @Override
            public boolean authenticate(org.apache.sshd.server.session.ServerSession session, String username,
                                        List<String> responses) {
                return responses.size() == 1 && validOtp(secret, responses.get(0));
            }
        };
    }

    private static KeyboardInteractiveAuthenticator passwordOtpAuthenticator(String password, String secret) {
        return new KeyboardInteractiveAuthenticator() {
            @Override
            public InteractiveChallenge generateChallenge(org.apache.sshd.server.session.ServerSession session,
                                                          String username, String name, String instruction) {
                InteractiveChallenge challenge = new InteractiveChallenge();
                challenge.addPrompt("Password: ", false);
                challenge.addPrompt("Verification code: ", false);
                return challenge;
            }

            @Override
            public boolean authenticate(org.apache.sshd.server.session.ServerSession session, String username,
                                        List<String> responses) {
                return responses.size() == 2
                        && password.equals(responses.get(0))
                        && validOtp(secret, responses.get(1));
            }
        };
    }

    /** Answers any command with a fixed identity, so tests assert the auth path, not command output. */
    private static org.apache.sshd.server.command.CommandFactory identityCommandFactory() {
        return (channel, command) -> new Command() {
            private ExitCallback callback;
            private OutputStream out;

            @Override
            public void setInputStream(InputStream in) {
            }

            @Override
            public void setOutputStream(OutputStream out) {
                this.out = out;
            }

            @Override
            public void setErrorStream(OutputStream err) {
            }

            @Override
            public void setExitCallback(ExitCallback callback) {
                this.callback = callback;
            }

            @Override
            public void start(ChannelSession channel, Environment env) throws IOException {
                out.write("root\n".getBytes());
                out.flush();
                callback.onExit(0);
            }

            @Override
            public void destroy(ChannelSession channel) {
            }
        };
    }

    private static String toPem(KeyPair keyPair) {
        String b64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        StringBuilder sb = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        return sb.append("-----END PRIVATE KEY-----\n").toString();
    }

    private void registerJumpHost() {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "otp-jump",
                "host", "127.0.0.1",
                "port", jumpSshd.getPort(),
                "username", "root",
                "privateKey", toPem(jumpKeyPair),
                "totpSecret", JUMP_OTP_SECRET));
    }

    private void registerTarget(TargetMode mode) {
        var args = new java.util.HashMap<String, Object>();
        args.put("name", "otp-target");
        args.put("host", "127.0.0.1");
        args.put("port", targetSshd.getPort());
        args.put("username", "root");
        args.put("connectVia", "otp-jump");
        switch (mode) {
            case PASSWORD -> args.put("password", TARGET_PASSWORD);
            case KEY -> args.put("privateKey", toPem(targetKeyPair));
            case PASSWORD_OTP -> {
                args.put("password", TARGET_PASSWORD);
                args.put("totpSecret", TARGET_OTP_SECRET);
            }
            case KEY_OTP -> {
                args.put("privateKey", toPem(targetKeyPair));
                args.put("totpSecret", TARGET_OTP_SECRET);
            }
        }
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", args);
    }

    private void assertExecThroughJumpSucceeds() {
        var r = getExecResult(execute("otp-target", "whoami", "otp-jump-session", 15));
        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
        assertThat(r.get("timed_out").asBoolean()).isFalse();
    }

    @Test
    void jump_key_otp_target_password_succeeds() {
        startTargetServer(TargetMode.PASSWORD);
        registerJumpHost();
        registerTarget(TargetMode.PASSWORD);

        assertExecThroughJumpSucceeds();
    }

    @Test
    void jump_key_otp_target_key_succeeds() {
        startTargetServer(TargetMode.KEY);
        registerJumpHost();
        registerTarget(TargetMode.KEY);

        assertExecThroughJumpSucceeds();
    }

    @Test
    void jump_key_otp_target_password_otp_succeeds() {
        startTargetServer(TargetMode.PASSWORD_OTP);
        registerJumpHost();
        registerTarget(TargetMode.PASSWORD_OTP);

        assertExecThroughJumpSucceeds();
    }

    @Test
    void jump_key_otp_target_key_otp_succeeds() {
        startTargetServer(TargetMode.KEY_OTP);
        registerJumpHost();
        registerTarget(TargetMode.KEY_OTP);

        assertExecThroughJumpSucceeds();
    }

    @Test
    void jump_requires_otp_secret_and_fails_clearly_without_it() {
        startTargetServer(TargetMode.PASSWORD);
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "otp-jump",
                "host", "127.0.0.1",
                "port", jumpSshd.getPort(),
                "username", "root",
                "privateKey", toPem(jumpKeyPair)));
        registerTarget(TargetMode.PASSWORD);

        var text = getResponseText(execute("otp-target", "whoami", "otp-jump-no-secret", 15));

        assertThat(text).contains("set a TOTP secret");
    }
}
