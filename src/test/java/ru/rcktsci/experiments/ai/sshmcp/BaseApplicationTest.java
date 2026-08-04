package ru.rcktsci.experiments.ai.sshmcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

import ru.rcktsci.experiments.ai.sshmcp.domain.AuthTokenRepository;
import ru.rcktsci.experiments.ai.sshmcp.domain.ExecutionHistoryRepository;
import ru.rcktsci.experiments.ai.sshmcp.domain.ServerRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BaseApplicationTest {

    @ServiceConnection
    @Container
    private static final PostgreSQLContainer DATABASE_CONTAINER = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("mcp")
            .withUsername("mcp")
            .withPassword("mcp");

    protected static String TEST_PRIVATE_KEY;
    protected static String TEST_PUBLIC_KEY;
    protected static String TEST_PRIVATE_KEY_WITH_PASSPHRASE;

    @SuppressWarnings("resource")
    @Container
    protected static final GenericContainer<?> SSH_CONTAINER = new GenericContainer<>("hermsi/alpine-sshd:latest")
            .withExposedPorts(22)
            .withEnv("ROOT_PASSWORD", "testpassword");

    @SneakyThrows
    @BeforeAll
    static void setupSshKeys() {
        SSH_CONTAINER.start();

        SSH_CONTAINER.execInContainer("sh", "-c", "ssh-keygen -t rsa -b 2048 -f /tmp/test_key -N '' -C 'test-key' -m PEM");
        SSH_CONTAINER.execInContainer("sh", "-c", "ssh-keygen -t rsa -b 2048 -f /tmp/test_key_encrypted -N 'testpassphrase' -C 'test-key-encrypted' -m PEM");

        TEST_PRIVATE_KEY = SSH_CONTAINER.execInContainer("cat", "/tmp/test_key").getStdout();
        TEST_PRIVATE_KEY_WITH_PASSPHRASE = SSH_CONTAINER.execInContainer("cat", "/tmp/test_key_encrypted").getStdout();
        TEST_PUBLIC_KEY = SSH_CONTAINER.execInContainer("cat", "/tmp/test_key.pub").getStdout().trim();

        SSH_CONTAINER.execInContainer("sh", "-c", "mkdir -p /root/.ssh && chmod 700 /root/.ssh");
        SSH_CONTAINER.execInContainer("sh", "-c", "echo '" + TEST_PUBLIC_KEY + "' >> /root/.ssh/authorized_keys");
        SSH_CONTAINER.execInContainer("sh",
                                      "-c",
                                      "echo '" + SSH_CONTAINER.execInContainer("cat", "/tmp/test_key_encrypted.pub")
                                              .getStdout()
                                              .trim() + "' >> /root/.ssh/authorized_keys");
        SSH_CONTAINER.execInContainer("sh", "-c", "chmod 600 /root/.ssh/authorized_keys");
    }

    @Autowired
    protected AuthTokenRepository authTokenRepository;
    @Autowired
    protected ServerRepository serverRepository;
    @Autowired
    private ExecutionHistoryRepository executionHistoryRepository;

    @Autowired
    protected ObjectMapper objectMapper;

    @LocalServerPort
    private int localServerPort;

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected String mcpUrl() {
        return "http://localhost:" + localServerPort + "/mcp";
    }

    @AfterEach
    final void cleanup() {
        executionHistoryRepository.deleteAllInBatch();
        serverRepository.deleteAllInBatch();
        authTokenRepository.deleteAllInBatch();
    }
}
