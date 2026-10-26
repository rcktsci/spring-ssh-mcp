package se.rocketscien.mcp.springsshmcpserver.tests.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import lombok.SneakyThrows;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.keyboard.InteractiveChallenge;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import se.rocketscien.mcp.springsshmcpserver.ssh.TotpGenerator;
import se.rocketscien.mcp.springsshmcpserver.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end OTP flow: the app connects to an embedded MINA SSH server whose keyboard-interactive challenge
 * asks for a password and a verification code. The server validates the code against the same Base32 secret
 * the test registers with the MCP tool, so only a correctly auto-generated TOTP code can pass.
 */
class ExecuteOtpTest extends BaseCallToolTest {

    private static final String OTP_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String OTP_PASSWORD = "testpassword";

    private static SshServer sshd;

    @SneakyThrows
    static void startOtpSshd() {
        if (sshd != null && sshd.isStarted()) {
            return;
        }
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        SimpleGeneratorHostKeyProvider hostKeyProvider =
                new SimpleGeneratorHostKeyProvider(Path.of("target", "otp-test-hostkey.ser"));
        hostKeyProvider.setAlgorithm("RSA");
        sshd.setKeyPairProvider(hostKeyProvider);
        sshd.setKeyboardInteractiveAuthenticator(new KeyboardInteractiveAuthenticator() {
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
                // Accept the current time step and the previous one: the client generates the code a moment
                // before the server validates it, so a test at a 30-second boundary must not be flaky.
                long nowSeconds = System.currentTimeMillis() / 1000;
                String current = TotpGenerator.generateAt(OTP_SECRET, nowSeconds);
                String previous = TotpGenerator.generateAt(OTP_SECRET, nowSeconds - 30);
                return responses.size() == 2
                        && OTP_PASSWORD.equals(responses.get(0))
                        && (current.equals(responses.get(1)) || previous.equals(responses.get(1)));
            }
        });
        // The execute tool runs a command; answer any command with a fixed identity so the test asserts the
        // authentication path, not the command's real output.
        sshd.setCommandFactory((channel, command) -> new Command() {
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
        });
        sshd.start();
    }

    @AfterAll
    static void stopOtpSshd() throws IOException {
        if (sshd != null) {
            sshd.stop();
        }
    }

    @SneakyThrows
    private void registerOtpServer(String name, String secret) {
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", name,
                "host", "127.0.0.1",
                "port", sshd.getPort(),
                "username", "root",
                "password", OTP_PASSWORD,
                "totpSecret", secret));
    }

    @Test
    @SneakyThrows
    void execute_with_otp_autopilot_succeeds() {
        startOtpSshd();
        registerOtpServer("otp-server", OTP_SECRET);

        var r = getExecResult(execute("otp-server", "whoami", "otp-session", 10));

        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
        assertThat(r.get("timed_out").asBoolean()).isFalse();
    }

    @Test
    @SneakyThrows
    void execute_with_wrong_totp_secret_fails_clearly() {
        startOtpSshd();
        registerOtpServer("otp-wrong-secret", "AAAAAAAAAAAAAAAAAAAA");

        var text = getResponseText(execute("otp-wrong-secret", "whoami", "otp-wrong-session", 10));

        assertThat(text).contains("OTP authentication failed");
    }

    @Test
    @SneakyThrows
    void execute_with_otp_secret_but_wrong_password_fails_clearly() {
        startOtpSshd();
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "otp-wrong-password",
                "host", "127.0.0.1",
                "port", sshd.getPort(),
                "username", "root",
                "password", "not-the-password",
                "totpSecret", OTP_SECRET));

        var text = getResponseText(execute("otp-wrong-password", "whoami", "otp-wrong-pass-session", 10));

        assertThat(text).contains("OTP authentication failed");
    }

    @Test
    @SneakyThrows
    void execute_against_otp_server_without_secret_hints_about_otp() {
        startOtpSshd();
        callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "otp-no-secret",
                "host", "127.0.0.1",
                "port", sshd.getPort(),
                "username", "root",
                "password", OTP_PASSWORD));

        var text = getResponseText(execute("otp-no-secret", "whoami", "otp-no-secret-session", 10));

        assertThat(text).contains("set a TOTP secret");
    }

    @Test
    void add_server_rejects_invalid_totp_secret() {
        var text = getResponseText(callTool(FULL_ACCESS_TOKEN, "add_server_connection", Map.of(
                "name", "otp-invalid-secret",
                "host", "127.0.0.1",
                "port", 2222,
                "username", "root",
                "password", OTP_PASSWORD,
                "totpSecret", "not-base32!")));

        assertThat(text).contains("Base32");
    }
}
