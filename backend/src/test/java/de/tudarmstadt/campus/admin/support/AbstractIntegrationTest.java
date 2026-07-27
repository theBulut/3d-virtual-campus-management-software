package de.tudarmstadt.campus.admin.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the real schema. Brings up PostgreSQL and Redis once per JVM.
 * <p>
 * The containers are started in a static initializer instead of via {@code @Testcontainers} and
 * {@code @Container}: JUnit would stop them after each test class, while Spring caches the application
 * context across classes. The singleton pattern keeps both in step and avoids restarting the containers
 * for every integration test.
 * <p>
 * Tagged {@code it}, so a run without a Docker daemon is possible with
 * {@code ./mvnw test -DexcludedGroups=it}.
 */
@Tag("it")
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerConnection(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
