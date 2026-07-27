package de.tudarmstadt.campus.admin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Application settings under the {@code campus} prefix. See docs/spec/01_ARCHITEKTUR_SPEC.md section 7.1.
 */
@Validated
@ConfigurationProperties(prefix = "campus")
public record AppProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid InitialAdmin admin,
        boolean seedDemo,
        @NotBlank String mediaPath) {

    /**
     * The secret shipped for local development. Any other profile must override it; the check lives in
     * {@link JwtSecretValidator}.
     */
    public static final String DEV_SECRET = "dev-only-secret-change-me-in-every-other-profile";

    public record Jwt(
            @NotBlank @Size(min = 32, message = "campus.jwt.secret must be at least 32 characters") String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {
    }

    public record InitialAdmin(
            @NotBlank String username,
            @NotBlank String password,
            @NotBlank @Email String email) {
    }
}
