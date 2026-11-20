package com.chubb.claims.repository;

import com.chubb.claims.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByClaimId(UUID claimId);
}
