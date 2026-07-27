package de.tudarmstadt.campus.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Fails startup when the development JWT secret is still in place outside the {@code dev} profile
 * (spec section 4.1). Implemented as {@link InitializingBean} so the failure happens during context
 * refresh, before the application ever serves a request.
 */
@Component
class JwtSecretValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

    private static final String DEV_PROFILE = "dev";
    private static final String SECRET_ENV_VARIABLE = "JWT_SECRET";

    private final AppProperties properties;
    private final Environment environment;

    JwtSecretValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean devProfileActive = Arrays.asList(environment.getActiveProfiles()).contains(DEV_PROFILE);

        if (AppProperties.DEV_SECRET.equals(properties.jwt().secret())) {
            if (!devProfileActive) {
                throw new IllegalStateException(
                        "campus.jwt.secret still holds the development default while the active profiles are "
                                + Arrays.toString(environment.getActiveProfiles())
                                + ". Set the JWT_SECRET environment variable to a value of at least 32 characters.");
            }
            return;
        }

        // Profiles that fall back to a generated secret (see application-docker.yml) work, but every
        // restart invalidates all issued tokens. Worth saying out loud.
        if (!devProfileActive && !environment.containsProperty(SECRET_ENV_VARIABLE)) {
            log.warn("No {} was supplied, so a random JWT secret was generated for this run. "
                    + "All issued tokens become invalid on restart. Set {} to keep sessions across restarts.",
                    SECRET_ENV_VARIABLE, SECRET_ENV_VARIABLE);
        }
    }
}
