package se.rocketscien.mcp.springsshmcpserver.ssh;

import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238 TOTP (Time-based One-Time Password), the scheme Google Authenticator, FreeOTP and friends use.
 * The generator is deliberately small and dependency-free: it only needs a Base32 secret, HMAC-SHA1 and the
 * current time. {@link #isValidSecret} exists for the save-time validation, {@link #generate} is called at
 * connect time when the server asks for the verification code.
 */
public final class TotpGenerator {

    /** Standard TOTP period, in seconds (RFC 6238 default). */
    private static final int PERIOD_SECONDS = 30;

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpGenerator() {
    }

    /**
     * @param secret Base32 secret, e.g. the {@code secret=...} part of an {@code otpauth://totp/...} URI.
     *               Whitespace and padding characters are ignored.
     * @return {@code true} if the secret is non-blank and decodes from Base32 to at least 10 bytes (80
     *         bits) — the size of a typical Google Authenticator / FreeOTP secret, and the length the
     *         generator below handles correctly.
     */
    public static boolean isValidSecret(String secret) {
        try {
            return decodeBase32(secret).length >= 10;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Generates the current 6-digit code. Uses the current time, so the client clock must be in sync with the
     * server's (the usual 30-second window applies).
     */
    public static String generate(String secret) {
        return generate(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS, 6);
    }

    /** Generates the 6-digit code for a specific unix timestamp (seconds), for tests that pin the window. */
    public static String generateAt(String secret, long unixTimeSeconds) {
        return generate(secret, unixTimeSeconds / PERIOD_SECONDS, 6);
    }

    /**
     * The RFC 6238 core: {@code code = HOTP(secret, counter)} truncated to {@code digits} decimal digits. The
     * counter is the time step, so tests can pin it to the RFC 6238 Appendix B vectors.
     */
    static String generate(String secret, long counter, int digits) {
        byte[] key = decodeBase32(secret);
        byte[] message = new byte[8];
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(message);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int code = binary % (int) Math.pow(10, digits);
            return String.format(Locale.ROOT, "%0" + digits + "d", code);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP generation failed: " + ex.getMessage(), ex);
        }
    }

    private static byte[] decodeBase32(String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("TOTP secret is missing");
        }
        String cleaned = secret.toUpperCase(Locale.ROOT).replaceAll("[\\s=]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("TOTP secret is empty");
        }
        if (!cleaned.matches("[" + BASE32_ALPHABET + "]+")) {
            throw new IllegalArgumentException("TOTP secret contains characters outside the Base32 alphabet");
        }

        int buffer = 0;
        int bitsLeft = 0;
        byte[] out = new byte[cleaned.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            buffer = (buffer << 5) | BASE32_ALPHABET.indexOf(cleaned.charAt(i));
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[index++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return index == out.length ? out : java.util.Arrays.copyOf(out, index);
    }
}
