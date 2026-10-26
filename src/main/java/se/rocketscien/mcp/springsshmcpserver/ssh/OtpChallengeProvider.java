package se.rocketscien.mcp.springsshmcpserver.ssh;

import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider;
import net.schmizz.sshj.userauth.password.Resource;

/**
 * Answers an SSH keyboard-interactive challenge on behalf of the stored credentials: a password prompt gets
 * the saved password, an OTP prompt ("Verification code:", "One-time password:", ...) gets a fresh TOTP code
 * generated from the stored Base32 secret. Any other prompt is a hard error with the prompt text in it, so
 * an unexpected challenge is never silently answered with the wrong guess.
 */
@Slf4j
final class OtpChallengeProvider implements ChallengeResponseProvider {

    private static final List<String> OTP_PROMPT_MARKERS = List.of(
            "verification code", "one-time password", "one time password", "otp", "token",
            "authenticator", "two-factor", "2fa", "passcode", "google authenticator",
            "код подтверждения", "верификационный код", "токен", "одноразовый", "otp-код");

    private static final List<String> PASSWORD_PROMPT_MARKERS = List.of("password", "пароль");

    private final String password;
    private final String totpSecret;

    OtpChallengeProvider(String password, String totpSecret) {
        this.password = password;
        this.totpSecret = totpSecret;
    }

    @Override
    public List<String> getSubmethods() {
        return List.of();
    }

    @Override
    public void init(Resource resource, String name, String instruction) {
        // Challenge metadata is informational; prompts carry everything the responder needs.
        log.debug("Keyboard-interactive challenge '{}': {}", name, instruction);
    }

    @Override
    public char[] getResponse(String prompt, boolean echo) {
        String normalized = prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Server sent an empty keyboard-interactive prompt");
        }
        if (isOtpPrompt(normalized)) {
            String code = TotpGenerator.generate(totpSecret);
            log.info("Answering OTP prompt '{}' with a generated code", prompt.trim());
            return code.toCharArray();
        }
        if (isPasswordPrompt(normalized)) {
            if (password == null) {
                throw new IllegalStateException(
                        "Server asked for a password, but only a key is stored for this server");
            }
            return password.toCharArray();
        }
        throw new IllegalStateException(
                "Server asked an unknown keyboard-interactive prompt: \"" + prompt.trim() + "\"");
    }

    @Override
    public boolean shouldRetry() {
        return false;
    }

    private static boolean isOtpPrompt(String normalized) {
        return OTP_PROMPT_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static boolean isPasswordPrompt(String normalized) {
        return PASSWORD_PROMPT_MARKERS.stream().anyMatch(normalized::contains);
    }
}
