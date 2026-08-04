package se.rocketscien.mcp.springsshmcpserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import se.rocketscien.mcp.springsshmcpserver.service.SecretEncryptionService;

@Configuration
public class CryptoConfig {

    @Value("${encryption.master-key}")
    private String masterKey;

    @Bean
    public SecretEncryptionService secretEncryptionService() {
        return new SecretEncryptionService(masterKey);
    }
}
