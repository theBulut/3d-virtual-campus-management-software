package de.tudarmstadt.campus.admin.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec section 4.1: startup must fail fast when the development secret survives into another profile.
 */
class JwtSecretValidatorTest {

    private static final String STRONG_SECRET = "a-real-secret-with-more-than-32-characters";

    @Test
    void failsWhenTheDevelopmentSecretIsUsedOutsideTheDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");

        assertThatThrownBy(() -> validator(AppProperties.DEV_SECRET, environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsWhenNoProfileIsActiveAtAll() {
        assertThatThrownBy(() -> validator(AppProperties.DEV_SECRET, new MockEnvironment()).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsTheDevelopmentSecretInTheDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatCode(() -> validator(AppProperties.DEV_SECRET, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAnOverriddenSecretInAnyProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("docker");

        assertThatCode(() -> validator(STRONG_SECRET, environment).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static JwtSecretValidator validator(String secret, MockEnvironment environment) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(7)),
                new AppProperties.InitialAdmin("admin", "admin", "admin@localhost"),
                false,
                "./data/media");
        return new JwtSecretValidator(properties, environment);
    }
}
