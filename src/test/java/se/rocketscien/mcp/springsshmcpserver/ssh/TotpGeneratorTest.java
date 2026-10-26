package se.rocketscien.mcp.springsshmcpserver.ssh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 6238 Appendix B test vectors (SHA-1, 8 digits) against {@link TotpGenerator}, plus Base32 edge cases.
 * The vector secret "12345678901234567890" is the ASCII seed; its Base32 form is what a user would paste
 * from an authenticator app.
 */
class TotpGeneratorTest {

    /** Base32 of the ASCII seed "12345678901234567890". */
    private static final String VECTOR_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void rfc6238_sha1_vectors() {
        // Counters are time steps (seconds / 30), per RFC 6238 Appendix B. The seed is the ASCII
        // "12345678901234567890" in Base32.
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 1L, 8)).isEqualTo("94287082");
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 37_037_036L, 8)).isEqualTo("07081804");
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 37_037_037L, 8)).isEqualTo("14050471");
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 41_152_263L, 8)).isEqualTo("89005924");
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 66_666_666L, 8)).isEqualTo("69279037");
        assertThat(TotpGenerator.generate(VECTOR_SECRET_BASE32, 666_666_666L, 8)).isEqualTo("65353130");
    }

    @Test
    void six_digits_by_default() {
        String code = TotpGenerator.generate("JBSWY3DPEHPK3PXP");
        assertThat(code).hasSize(6).matches("\\d{6}");
    }

    @Test
    void base32_lowercase_whitespace_and_padding_are_ignored() {
        assertThat(TotpGenerator.generate("gezdgnbvgy3tqojqgezdgnbvgy3tqojq", 1L, 8))
                .isEqualTo("94287082");
        assertThat(TotpGenerator.generate("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ====", 1L, 8))
                .isEqualTo("94287082");
        assertThat(TotpGenerator.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ=", 1L, 8))
                .isEqualTo("94287082");
    }

    @Test
    void invalid_secrets_are_rejected() {
        assertThat(TotpGenerator.isValidSecret(null)).isFalse();
        assertThat(TotpGenerator.isValidSecret("")).isFalse();
        assertThat(TotpGenerator.isValidSecret("   ")).isFalse();
        assertThat(TotpGenerator.isValidSecret("not-base32!")).isFalse();
        assertThat(TotpGenerator.isValidSecret("JBSWY3DPEHPK3PXP")).isTrue();
        assertThatThrownBy(() -> TotpGenerator.generate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> TotpGenerator.generate("not-base32!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Base32");
    }
}
