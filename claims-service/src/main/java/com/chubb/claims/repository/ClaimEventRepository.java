package com.chubb.claims.repository;

import com.chubb.claims.domain.ClaimEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimEventRepository extends JpaRepository<ClaimEvent, UUID> {
    List<ClaimEvent> findByClaimIdOrderByOccurredAtAsc(UUID claimId);
}
