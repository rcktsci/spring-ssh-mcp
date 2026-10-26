package se.rocketscien.mcp.springsshmcpserver.ssh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpChallengeProviderTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void password_prompt_gets_the_stored_password() {
        var provider = new OtpChallengeProvider("hunter2", SECRET);
        assertThat(provider.getResponse("Password: ", false)).isEqualTo("hunter2".toCharArray());
        assertThat(provider.getResponse("Пароль: ", false)).isEqualTo("hunter2".toCharArray());
        assertThat(provider.getResponse("пароль для root@host: ", false)).isEqualTo("hunter2".toCharArray());
    }

    @Test
    void otp_prompt_gets_a_valid_current_totp_code() {
        var provider = new OtpChallengeProvider(null, SECRET);
        char[] answer = provider.getResponse("Verification code: ", false);
        // The code must match the current time step, exactly what the server would compute.
        assertThat(new String(answer)).isEqualTo(TotpGenerator.generate(SECRET));
    }

    @Test
    void otp_prompt_markers_are_recognized() {
        var provider = new OtpChallengeProvider(null, SECRET);
        assertThat(new String(provider.getResponse("One-time password: ", false)))
                .isEqualTo(TotpGenerator.generate(SECRET));
        assertThat(new String(provider.getResponse("OTP: ", false)))
                .isEqualTo(TotpGenerator.generate(SECRET));
        assertThat(new String(provider.getResponse("Токен: ", false)))
                .isEqualTo(TotpGenerator.generate(SECRET));
        assertThat(new String(provider.getResponse("Введите код подтверждения: ", false)))
                .isEqualTo(TotpGenerator.generate(SECRET));
    }

    @Test
    void unknown_prompt_is_a_hard_error() {
        var provider = new OtpChallengeProvider(null, SECRET);
        assertThatThrownBy(() -> provider.getResponse("Challenge question: ", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown keyboard-interactive prompt");
    }

    @Test
    void empty_prompt_is_a_hard_error() {
        var provider = new OtpChallengeProvider(null, SECRET);
        assertThatThrownBy(() -> provider.getResponse("   ", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty keyboard-interactive prompt");
    }

    @Test
    void password_prompt_without_stored_password_is_a_hard_error() {
        var provider = new OtpChallengeProvider(null, SECRET);
        assertThatThrownBy(() -> provider.getResponse("Password: ", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only a key is stored");
    }

    @Test
    void no_retries() {
        assertThat(new OtpChallengeProvider(null, SECRET).shouldRetry()).isFalse();
    }
}
