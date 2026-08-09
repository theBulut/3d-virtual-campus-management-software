package de.tudarmstadt.campus.admin.user.service;

import java.security.SecureRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Hashing and generation of passwords. Plaintext is never stored anywhere (FA-02).
 */
@Service
public class PasswordService {

    /**
     * Minimum length for passwords chosen through the API. The specification defines no policy, so this
     * is the deliberate minimum from docs/DECISIONS.md D-20; the seeded default password is exempt but
     * forces a change on first login.
     */
    public static final int MIN_LENGTH = 12;

    private static final String ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_LENGTH = 16;

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public PasswordService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    /**
     * Temporary password for an administrative reset (spec section 5.2). Returned to the caller once and
     * never stored in plaintext; the alphabet leaves out characters that are easy to confuse.
     */
    public String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
