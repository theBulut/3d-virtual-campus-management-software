package com.campus.backend.config;

import com.campus.backend.model.Admin;
import com.campus.backend.repository.AdminRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the default admin account so the editor has an operator to act as.
 * Authentication is not wired up yet; this is the placeholder identity.
 */
@Configuration
public class DataInitializer {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    @Bean
    public CommandLineRunner seedDefaultAdmin(AdminRepository adminRepository) {
        return args -> adminRepository.findByUsername(DEFAULT_ADMIN_USERNAME)
                .orElseGet(() -> adminRepository.save(
                        new Admin(null, DEFAULT_ADMIN_USERNAME, "Campus Administrator")));
    }
}
