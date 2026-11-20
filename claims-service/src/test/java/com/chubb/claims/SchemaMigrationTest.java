package com.chubb.claims;

import com.chubb.claims.domain.*;
import com.chubb.claims.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full context against a real PostgreSQL. This proves that:
 * - Flyway V1/V2 apply cleanly,
 * - Hibernate's schema validation (ddl-auto=validate) accepts every entity mapping,
 * - the seed data is present, and JSONB + optimistic locking behave as intended.
 */
@SpringBootTest
@Testcontainers
class SchemaMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MarketRepository markets;
    @Autowired UserRepository users;
    @Autowired PolicyRepository policies;
    @Autowired ClaimRepository claims;

    @Test
    void seedDataIsLoaded() {
        assertThat(markets.count()).isEqualTo(6);
        assertThat(users.count()).isEqualTo(6);

        User officer = users.findByEmail("officer.my1@example.com").orElseThrow();
        assertThat(officer.getRole()).isEqualTo(Role.OFFICER);
        assertThat(officer.getMarketCode()).isEqualTo("MY");
        assertThat(officer.getPasswordHash()).startsWith("$2");

        User claimant = users.findByEmail("claimant.my@example.com").orElseThrow();
        assertThat(policies.findByHolderId(claimant.getId())).hasSize(2);
    }

    @Test
    void claimPersistsWithJsonbDetailsAndReferenceSequence() {
        User claimant = users.findByEmail("claimant.my@example.com").orElseThrow();
        Policy motor = policies.findByHolderId(claimant.getId()).stream()
                .filter(p -> p.getPolicyType() == ClaimType.MOTOR).findFirst().orElseThrow();

        Claim claim = new Claim();
        claim.setReference("CLM-MY-TEST-%06d".formatted(claims.nextReferenceNumber()));
        claim.setPolicyId(motor.getId());
        claim.setClaimantId(claimant.getId());
        claim.setMarketCode("MY");
        claim.setClaimType(ClaimType.MOTOR);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(LocalDate.of(2026, 11, 1));
        claim.setDescription("Rear-ended at traffic light");
        claim.setIncidentDetails(Map.of("vehiclePlate", "WXY1234", "thirdPartyInvolved", true));
        claim.setCurrency("MYR");
        claim.setClaimedAmount(new BigDecimal("4500.00"));

        Claim saved = claims.saveAndFlush(claim);
        Claim loaded = claims.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getIncidentDetails()).containsEntry("vehiclePlate", "WXY1234");
        assertThat(loaded.getClaimedAmount()).isEqualByComparingTo("4500.00");
        assertThat(loaded.getSubmittedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void foreignKeysRejectUnknownPolicy() {
        User claimant = users.findByEmail("claimant.sg@example.com").orElseThrow();

        Claim claim = new Claim();
        claim.setReference("CLM-SG-BAD-" + UUID.randomUUID().toString().substring(0, 8));
        claim.setPolicyId(UUID.randomUUID());   // does not exist
        claim.setClaimantId(claimant.getId());
        claim.setMarketCode("SG");
        claim.setClaimType(ClaimType.MOTOR);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setIncidentDate(LocalDate.of(2026, 11, 2));
        claim.setDescription("x");
        claim.setCurrency("SGD");
        claim.setClaimedAmount(BigDecimal.TEN);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> claims.saveAndFlush(claim));
    }
}
