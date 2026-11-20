package com.chubb.claims.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the demo credentials documented in the README against the V2 seed hash. */
class SeedPasswordHashTest {

    private static final String SEEDED_HASH =
            "$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye";

    @Test
    void seededHashMatchesDocumentedPassword() {
        assertThat(new BCryptPasswordEncoder().matches("Password123!", SEEDED_HASH)).isTrue();
    }
}
