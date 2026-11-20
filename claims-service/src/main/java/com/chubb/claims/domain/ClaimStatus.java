package com.chubb.claims.domain;

/** Allowed transitions are added in Phase 2 (state machine). */
public enum ClaimStatus {
    SUBMITTED, UNDER_REVIEW, INFO_REQUESTED, ASSESSED, APPROVED, REJECTED, SETTLED
}
