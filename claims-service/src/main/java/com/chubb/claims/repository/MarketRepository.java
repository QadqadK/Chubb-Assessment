package com.chubb.claims.repository;

import com.chubb.claims.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, String> {
}
