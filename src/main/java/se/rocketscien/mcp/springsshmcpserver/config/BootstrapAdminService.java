package se.rocketscien.mcp.springsshmcpserver.config;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminService implements ApplicationRunner {

    private final AuthTokenRepository authTokenRepository;

    @Value("${bootstrap.admin-token.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        ensureBootstrap();
    }

    public UUID ensureBootstrap() {
        if (authTokenRepository.existsByIsTokenAdminTrue()) {
            return null;
        }

        var token = UUID.randomUUID();
        var entity = new AuthTokenEntity();
        entity.setToken(token);
        entity.setCanEdit(true);
        entity.setCanExecute(true);
        entity.setIsTokenAdmin(true);
        authTokenRepository.save(entity);

        log.warn("BOOTSTRAP-ADMIN-TOKEN: {} -- store this UUID now, it will not be shown again", token);
        return token;
    }
}
