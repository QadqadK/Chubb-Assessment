package com.chubb.claims.repository;

import com.chubb.claims.domain.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    List<Assessment> findByClaimIdOrderByCreatedAtDesc(UUID claimId);
}
