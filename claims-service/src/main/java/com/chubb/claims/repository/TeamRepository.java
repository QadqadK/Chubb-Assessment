package com.chubb.claims.repository;

import com.chubb.claims.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
}
