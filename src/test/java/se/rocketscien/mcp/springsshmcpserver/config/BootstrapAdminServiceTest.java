package se.rocketscien.mcp.springsshmcpserver.config;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import se.rocketscien.mcp.springsshmcpserver.BaseApplicationTest;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapAdminServiceTest extends BaseApplicationTest {

    @Autowired
    private BootstrapAdminService bootstrapAdminService;

    @Test
    void creates_token_when_no_admin_exists() {
        var created = bootstrapAdminService.ensureBootstrap();

        assertThat(created).isNotNull();
        var saved = authTokenRepository.findByToken(created).orElseThrow();
        assertThat(saved.getCanEdit()).isTrue();
        assertThat(saved.getCanExecute()).isTrue();
        assertThat(saved.getIsTokenAdmin()).isTrue();
    }

    @Test
    void does_nothing_when_admin_already_exists() {
        var existing = new AuthTokenEntity();
        existing.setToken(UUID.randomUUID());
        existing.setCanEdit(true);
        existing.setCanExecute(true);
        existing.setIsTokenAdmin(true);
        authTokenRepository.save(existing);

        var countBefore = authTokenRepository.count();
        var result = bootstrapAdminService.ensureBootstrap();

        assertThat(result).isNull();
        assertThat(authTokenRepository.count()).isEqualTo(countBefore);
    }

    // Deliberate second Spring context: the default test profile disables bootstrap,
    // so verifying the startup path requires `properties=` to override `application-test.yml`.
    @SpringBootTest(properties = "bootstrap.admin-token.enabled=true")
    static class EnabledTest extends BaseApplicationTest {

        @Test
        void enabled_flag_runs_bootstrap_at_startup() {
            assertThat(authTokenRepository.existsByIsTokenAdminTrue()).isTrue();
        }
    }
}
