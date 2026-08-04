package se.rocketscien.mcp.springsshmcpserver.config;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class EncryptionConstants {

    public final String ENCRYPTED_PREFIX = "aes:";
    public final String ALGORITHM = "AES/GCM/NoPadding";
    public final int GCM_IV_LENGTH = 12;
    public final int GCM_TAG_LENGTH = 128;
}
