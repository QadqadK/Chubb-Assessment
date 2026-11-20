package com.chubb.insights.repository;

import com.chubb.insights.domain.ClaimReadModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimReadModelRepository extends JpaRepository<ClaimReadModel, UUID> {

    List<ClaimReadModel> findByClosedAtIsNullOrderBySubmittedAtAsc();
}
