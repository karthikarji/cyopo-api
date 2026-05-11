package com.cyopo.core.repository;

import com.cyopo.core.model.PortfolioAnalytic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PortfolioAnalyticRepository
        extends JpaRepository<PortfolioAnalytic, UUID> {

    long countByPortfolioId(UUID portfolioId);

    @Query("SELECT COUNT(DISTINCT a.viewerUserId) " +
            "FROM PortfolioAnalytic a " +
            "WHERE a.portfolioId IN :portfolioIds")
    long countUniqueVisitorsByPortfolioIds(List<UUID> portfolioIds);

    @Query(value = """
    SELECT
        DATE_TRUNC(:interval, created_at) as period,
        COUNT(*) as views,
        COUNT(DISTINCT viewer_user_id) as unique_visitors
    FROM portfolio.portfolio_analytics
    WHERE portfolio_id = ANY(:portfolioIds)
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR created_at >= CAST(:startDate AS TIMESTAMP))
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR created_at <= CAST(:endDate AS TIMESTAMP))
    GROUP BY period
    ORDER BY period
    """, nativeQuery = true)
    List<Object[]> getAnalyticsByInterval(
            @Param("interval") String interval,
            @Param("portfolioIds") UUID[] portfolioIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}