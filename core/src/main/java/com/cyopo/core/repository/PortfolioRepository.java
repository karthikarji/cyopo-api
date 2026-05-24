package com.cyopo.core.repository;

import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.PortfolioStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, UUID>,
        JpaSpecificationExecutor<Portfolio> {

    List<Portfolio> findByUserId(UUID userId);

    Optional<Portfolio> findBySlug(String slug);

    Optional<Portfolio> findByIdAndUserId(UUID id, UUID userId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, PortfolioStatus status);

    @Query("SELECT p.slug FROM Portfolio p WHERE p.slug LIKE :prefix%")
    List<String> findSlugsByPrefix(String prefix);

    Optional<Portfolio> findBySlugAndStatusAndSettingsIsPublic(
            String slug,
            PortfolioStatus status,
            Boolean isPublic
    );
}