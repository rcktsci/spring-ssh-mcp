package ru.rcktsci.experiments.ai.sshmcp.service;

import java.security.SecureRandom;
import java.util.Base64;

import lombok.SneakyThrows;

import ru.rcktsci.experiments.ai.sshmcp.config.EncryptionConstants;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecretEncryptionService {

    private final SecretKeySpec secretKey;

    public SecretEncryptionService(String masterKey) {
        byte[] keyBytes;
        if (masterKey.length() == 64) {
            keyBytes = hexToBytes(masterKey);
        } else {
            keyBytes = masterKey.getBytes();
            if (keyBytes.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
                keyBytes = padded;
            }
        }
        this.secretKey = new SecretKeySpec(keyBytes, 0, 32, "AES");
    }

    @SneakyThrows
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[EncryptionConstants.GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(EncryptionConstants.ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(EncryptionConstants.GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    @SneakyThrows
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        String value = ciphertext;
        if (value.startsWith(EncryptionConstants.ENCRYPTED_PREFIX)) {
            value = value.substring(EncryptionConstants.ENCRYPTED_PREFIX.length());
        }
        byte[] combined = Base64.getDecoder().decode(value);

        byte[] iv = new byte[EncryptionConstants.GCM_IV_LENGTH];
        byte[] encrypted = new byte[combined.length - EncryptionConstants.GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, EncryptionConstants.GCM_IV_LENGTH);
        System.arraycopy(combined, EncryptionConstants.GCM_IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(EncryptionConstants.ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(EncryptionConstants.GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                  + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
