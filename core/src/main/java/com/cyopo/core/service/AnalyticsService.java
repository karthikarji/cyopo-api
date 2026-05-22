package com.cyopo.core.service;

import com.cyopo.core.dto.response.AnalyticsResponse;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.PortfolioAnalytic;
import com.cyopo.core.repository.ContactRepository;
import com.cyopo.core.repository.PortfolioAnalyticRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PortfolioAnalyticRepository analyticRepository;
    private final PortfolioRepository         portfolioRepository;
    private final ContactRepository           contactRepository;

    @Transactional
    public void recordView(
            UUID portfolioId,
            UUID ownerId,
            UUID viewerUserId,
            String sessionToken,
            String viewerIp) {

        // Deduplicate — skip if same viewer visited in last 24 hours
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long recentViews = analyticRepository.countRecentView(
                portfolioId,
                viewerUserId,
                sessionToken,
                since);

        if (recentViews > 0) {
            log.debug("Duplicate view skipped for portfolio: {}", portfolioId);
            return;
        }

        // TODO: RabbitMQ - high traffic portfolios can generate
        // thousands of view events per minute. Replace direct DB
        // insert with publishing to "portfolio.view" queue.
        // Worker batch-inserts for better performance.
        PortfolioAnalytic analytic = PortfolioAnalytic.builder()
                .portfolioId(portfolioId)
                .ownerId(ownerId)
                .viewerUserId(viewerUserId)
                .sessionToken(sessionToken)
                .viewerIp(viewerIp)
                .build();

        analyticRepository.save(analytic);
        log.debug("View recorded for portfolio: {}", portfolioId);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(
            String userId,
            List<UUID> portfolioIds,
            Instant startDate,
            Instant endDate,
            String interval) {

        UUID userUUID = UUID.fromString(userId);

        // Fetch all user portfolios if none specified
        List<Portfolio> portfolios = portfolioIds != null && !portfolioIds.isEmpty()
                ? portfolioRepository.findAllById(portfolioIds)
                : portfolioRepository.findByUserId(userUUID);

        if (portfolios.isEmpty()) {
            return AnalyticsResponse.builder()
                    .totalViews(0)
                    .uniqueVisitors(0)
                    .viewsThisWeek(0)
                    .viewsThisMonth(0)
                    .totalMessages(0)
                    .viewsByPeriod(new ArrayList<>())
                    .portfolioBreakdown(new ArrayList<>())
                    .build();
        }

        List<UUID> ids = portfolios.stream()
                .map(Portfolio::getId)
                .toList();

        UUID[] idsArray = ids.toArray(new UUID[0]);

        // ─── Total views (excludes self-views via repository query) ──
        long totalViews = ids.stream()
                .mapToLong(analyticRepository::countByPortfolioId)
                .sum();

        // ─── Unique visitors ─────────────────────────────────────────
        long uniqueVisitors = analyticRepository
                .countUniqueVisitorsByPortfolioIds(idsArray);

        // ─── Views this week and this month ──────────────────────────
        Instant weekAgo  = Instant.now().minus(7,  ChronoUnit.DAYS);
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        long viewsThisWeek = analyticRepository
                .countByPortfolioIdsAndCreatedAtAfter(ids, weekAgo);

        long viewsThisMonth = analyticRepository
                .countByPortfolioIdsAndCreatedAtAfter(ids, monthAgo);

        // ─── Total messages across all portfolios ────────────────────
        long totalMessages = ids.stream()
                .mapToLong(contactRepository::countByPortfolioId)
                .sum();

        // ─── Per portfolio breakdown ──────────────────────────────────
        List<Object[]> breakdownRaw = analyticRepository
                .getViewsGroupedByPortfolio(idsArray);

        // Map portfolioId → raw stats
        Map<UUID, long[]> viewsMap = breakdownRaw.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new long[]{
                                ((Number) row[1]).longValue(),
                                ((Number) row[2]).longValue()
                        }
                ));

        List<AnalyticsResponse.PortfolioStats> breakdown = portfolios.stream()
                .map(p -> {
                    long[] stats = viewsMap.getOrDefault(
                            p.getId(), new long[]{0, 0});
                    long messages = contactRepository
                            .countByPortfolioId(p.getId());
                    return AnalyticsResponse.PortfolioStats.builder()
                            .portfolioId(p.getId())
                            .portfolioName(p.getName())
                            .portfolioSlug(p.getSlug())
                            .views(stats[0])
                            .uniqueVisitors(stats[1])
                            .messages(messages)
                            .build();
                })
                .toList();

        // ─── Views over time ──────────────────────────────────────────
        String validInterval = List.of("day", "week", "month", "year")
                .contains(interval) ? interval : "day";

        LocalDateTime start = startDate != null
                ? LocalDateTime.ofInstant(startDate, ZoneOffset.UTC)
                : LocalDateTime.ofInstant(monthAgo, ZoneOffset.UTC);

        LocalDateTime end = endDate != null
                ? LocalDateTime.ofInstant(endDate, ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        List<Object[]> rawResults = analyticRepository
                .getAnalyticsByInterval(validInterval, idsArray, start, end);

        List<AnalyticsResponse.ViewsByPeriod> viewsByPeriod =
                rawResults.stream()
                        .map(row -> AnalyticsResponse.ViewsByPeriod.builder()
                                .period(row[0].toString())
                                .views(((Number) row[1]).longValue())
                                .uniqueVisitors(((Number) row[2]).longValue())
                                .build())
                        .toList();

        return AnalyticsResponse.builder()
                .totalViews(totalViews)
                .uniqueVisitors(uniqueVisitors)
                .viewsThisWeek(viewsThisWeek)
                .viewsThisMonth(viewsThisMonth)
                .totalMessages(totalMessages)
                .portfolioBreakdown(breakdown)
                .viewsByPeriod(viewsByPeriod)
                .build();
    }
}