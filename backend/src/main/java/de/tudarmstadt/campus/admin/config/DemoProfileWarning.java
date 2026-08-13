package de.tudarmstadt.campus.admin.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Says out loud that this instance carries demo accounts with a password published in the repository.
 * <p>
 * The seed itself is already tied to the {@code demo} profile, so it cannot reach a production database
 * by accident. What this adds is the second half: if somebody ever does start a production instance with
 * the profile, the log shows it at once rather than after the first unexplained login.
 */
@Configuration
@Profile("demo")
public class DemoProfileWarning {

    private static final Logger log = LoggerFactory.getLogger(DemoProfileWarning.class);

    @PostConstruct
    void warn() {
        log.warn("""

                ****************************************************************
                Profil 'demo' ist aktiv. Es werden Demo-Konten mit dem öffentlich
                bekannten Passwort 'demo-passwort' angelegt.
                Ausschließlich für Vorführungen und Tests verwenden.
                ****************************************************************""");
    }
}
