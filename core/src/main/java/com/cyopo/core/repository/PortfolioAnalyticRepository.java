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

    // Total views excluding self-views
    @Query("SELECT COUNT(a) FROM PortfolioAnalytic a " +
            "WHERE a.portfolioId = :portfolioId " +
            "AND (a.viewerUserId IS NULL OR a.viewerUserId != a.ownerId)")
    long countByPortfolioId(@Param("portfolioId") UUID portfolioId);

    @Query("SELECT COUNT(a) FROM PortfolioAnalytic a " +
            "WHERE a.portfolioId = :portfolioId " +
            "AND a.createdAt > :since " +
            "AND (" +
            "  (a.viewerUserId IS NOT NULL AND a.viewerUserId = :viewerUserId) " +
            "  OR " +
            "  (a.viewerUserId IS NULL AND a.sessionToken = :sessionToken)" +
            ")")
    long countRecentView(
            @Param("portfolioId")   UUID portfolioId,
            @Param("viewerUserId")  UUID viewerUserId,
            @Param("sessionToken")  String sessionToken,
            @Param("since")         Instant since);

    // Unique visitors — uses IP as fallback for anonymous users
    @Query(value = """
        SELECT COUNT(DISTINCT COALESCE(viewer_user_id::text, viewer_ip))
        FROM portfolio.portfolio_analytics
        WHERE portfolio_id = ANY(:portfolioIds)
        AND (viewer_user_id IS NULL OR viewer_user_id != owner_id)
        """, nativeQuery = true)
    long countUniqueVisitorsByPortfolioIds(
            @Param("portfolioIds") UUID[] portfolioIds);


    // Views over time by interval
    @Query(value = """
        SELECT
            DATE_TRUNC(:interval, created_at) as period,
            COUNT(*) as views,
            COUNT(DISTINCT COALESCE(viewer_user_id::text, viewer_ip)) as unique_visitors
        FROM portfolio.portfolio_analytics
        WHERE portfolio_id = ANY(:portfolioIds)
        AND (viewer_user_id IS NULL OR viewer_user_id != owner_id)
        AND (CAST(:startDate AS TIMESTAMP) IS NULL OR created_at >= CAST(:startDate AS TIMESTAMP))
        AND (CAST(:endDate AS TIMESTAMP) IS NULL OR created_at <= CAST(:endDate AS TIMESTAMP))
        GROUP BY period
        ORDER BY period
        """, nativeQuery = true)
    List<Object[]> getAnalyticsByInterval(
            @Param("interval") String interval,
            @Param("portfolioIds") UUID[] portfolioIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);


    // Views in last N days — for this week / this month convenience fields
    @Query("SELECT COUNT(a) FROM PortfolioAnalytic a " +
            "WHERE a.portfolioId IN :portfolioIds " +
            "AND a.createdAt >= :since " +
            "AND (a.viewerUserId IS NULL OR a.viewerUserId != a.ownerId)")
    long countByPortfolioIdsAndCreatedAtAfter(
            @Param("portfolioIds") List<UUID> portfolioIds,
            @Param("since") Instant since);

    // Per portfolio breakdown
    @Query(value = """
        SELECT
            portfolio_id,
            COUNT(*) as views,
            COUNT(DISTINCT COALESCE(viewer_user_id::text, viewer_ip)) as unique_visitors
        FROM portfolio.portfolio_analytics
        WHERE portfolio_id = ANY(:portfolioIds)
        AND (viewer_user_id IS NULL OR viewer_user_id != owner_id)
        GROUP BY portfolio_id
        """, nativeQuery = true)
    List<Object[]> getViewsGroupedByPortfolio(
            @Param("portfolioIds") UUID[] portfolioIds);
}