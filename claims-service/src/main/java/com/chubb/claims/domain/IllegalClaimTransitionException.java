package com.chubb.claims.domain;

/** Thrown when a caller attempts a transition that the claim lifecycle does not allow. */
public class IllegalClaimTransitionException extends RuntimeException {

    private final String reference;
    private final ClaimStatus from;
    private final ClaimStatus to;

    public IllegalClaimTransitionException(String reference, ClaimStatus from, ClaimStatus to) {
        super("Claim %s cannot move from %s to %s".formatted(reference, from, to));
        this.reference = reference;
        this.from = from;
        this.to = to;
    }

    public String getReference() {
        return reference;
    }

    public ClaimStatus getFrom() {
        return from;
    }

    public ClaimStatus getTo() {
        return to;
    }
}
