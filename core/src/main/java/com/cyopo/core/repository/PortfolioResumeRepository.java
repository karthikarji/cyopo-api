package com.cyopo.core.repository;

import com.cyopo.core.model.PortfolioResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioResumeRepository extends JpaRepository<PortfolioResume, UUID> {

    Optional<PortfolioResume> findByPortfolioId(UUID portfolioId);

    @Modifying
    @Query("DELETE FROM PortfolioResume r WHERE r.portfolioId = :portfolioId")
    void deleteByPortfolioId(UUID portfolioId);

    boolean existsByPortfolioId(UUID portfolioId);
}