package de.tudarmstadt.campus.admin.security;

/**
 * Value of the {@code typ} claim. The distinction matters: a refresh token must never be accepted as an
 * access token (spec section 4.3, step 3).
 */
public enum TokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    TokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    static TokenType fromClaim(String value) {
        for (TokenType type : values()) {
            if (type.claimValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown token type: " + value);
    }
}
