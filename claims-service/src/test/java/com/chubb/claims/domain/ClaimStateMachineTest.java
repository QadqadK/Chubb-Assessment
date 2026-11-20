package com.chubb.claims.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for the claim lifecycle. No Spring context and no database.
 * The expected matrix is written out independently of the enum so the test can fail
 * if the production transition table drifts from the documented workflow.
 */
class ClaimStateMachineTest {

    @Test
    void newClaimStartsSubmitted() {
        Claim claim = new Claim();

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(claim.isTerminal()).isFalse();
        assertThat(claim.getClosedAt()).isNull();
    }

    static Stream<Arguments> allStatusPairs() {
        return Stream.of(ClaimStatus.values())
                .flatMap(from -> Stream.of(ClaimStatus.values())
                        .map(to -> Arguments.of(from, to, isAllowed(from, to))));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @MethodSource("allStatusPairs")
    void transitionMatrixMatchesDocumentedLifecycle(ClaimStatus from, ClaimStatus to, boolean allowed) {
        Claim claim = claimIn(from);

        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);

        if (allowed) {
            claim.transitionTo(to);
            assertThat(claim.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> claim.transitionTo(to))
                    .isInstanceOf(IllegalClaimTransitionException.class)
                    .hasMessageContaining(from.name())
                    .hasMessageContaining(to.name());
            assertThat(claim.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void askingForInformationAndAnsweringReturnsToReview() {
        Claim claim = claimIn(ClaimStatus.UNDER_REVIEW);

        claim.transitionTo(ClaimStatus.INFO_REQUESTED);
        claim.transitionTo(ClaimStatus.UNDER_REVIEW);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getClosedAt()).isNull();
    }

    @Test
    void rejectionClosesTheClaim() {
        Claim claim = claimIn(ClaimStatus.UNDER_REVIEW);

        claim.transitionTo(ClaimStatus.REJECTED);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.isTerminal()).isTrue();
        assertThat(claim.getClosedAt()).isNotNull();
    }

    @Test
    void settlementClosesAnApprovedClaim() {
        Claim claim = claimIn(ClaimStatus.APPROVED);

        claim.transitionTo(ClaimStatus.SETTLED);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.isTerminal()).isTrue();
        assertThat(claim.getClosedAt()).isNotNull();
    }

    @Test
    void terminalClaimsHaveNoOutgoingTransitions() {
        for (ClaimStatus terminal : new ClaimStatus[]{ClaimStatus.REJECTED, ClaimStatus.SETTLED}) {
            Claim claim = claimIn(terminal);

            assertThat(terminal.allowedTransitions()).isEmpty();
            assertThatThrownBy(() -> claim.transitionTo(ClaimStatus.UNDER_REVIEW))
                    .isInstanceOf(IllegalClaimTransitionException.class);
            assertThat(claim.getStatus()).isEqualTo(terminal);
        }
    }

    @Test
    void nullTargetIsRejected() {
        Claim claim = new Claim();

        assertThatThrownBy(() -> claim.transitionTo(null))
                .isInstanceOf(IllegalClaimTransitionException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void exceptionCarriesTransitionDetails() {
        Claim claim = claimIn(ClaimStatus.SUBMITTED);

        assertThatThrownBy(() -> claim.transitionTo(ClaimStatus.SETTLED))
                .isInstanceOfSatisfying(IllegalClaimTransitionException.class, ex -> {
                    assertThat(ex.getFrom()).isEqualTo(ClaimStatus.SUBMITTED);
                    assertThat(ex.getTo()).isEqualTo(ClaimStatus.SETTLED);
                });
    }

    private static boolean isAllowed(ClaimStatus from, ClaimStatus to) {
        return switch (from) {
            case SUBMITTED -> to == ClaimStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> to == ClaimStatus.INFO_REQUESTED
                    || to == ClaimStatus.ASSESSED
                    || to == ClaimStatus.REJECTED;
            case INFO_REQUESTED -> to == ClaimStatus.UNDER_REVIEW;
            case ASSESSED -> to == ClaimStatus.APPROVED || to == ClaimStatus.REJECTED;
            case APPROVED -> to == ClaimStatus.SETTLED;
            case REJECTED, SETTLED -> false;
        };
    }

    private static Claim claimIn(ClaimStatus target) {
        Claim claim = new Claim();
        claim.setReference("CLM-MY-000001");
        if (target == ClaimStatus.SUBMITTED) {
            return claim;
        }

        claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        switch (target) {
            case UNDER_REVIEW -> {
            }
            case INFO_REQUESTED -> claim.transitionTo(ClaimStatus.INFO_REQUESTED);
            case ASSESSED -> claim.transitionTo(ClaimStatus.ASSESSED);
            case APPROVED -> {
                claim.transitionTo(ClaimStatus.ASSESSED);
                claim.transitionTo(ClaimStatus.APPROVED);
            }
            case REJECTED -> claim.transitionTo(ClaimStatus.REJECTED);
            case SETTLED -> {
                claim.transitionTo(ClaimStatus.ASSESSED);
                claim.transitionTo(ClaimStatus.APPROVED);
                claim.transitionTo(ClaimStatus.SETTLED);
            }
            default -> throw new IllegalArgumentException("Unsupported target " + target);
        }
        return claim;
    }
}
