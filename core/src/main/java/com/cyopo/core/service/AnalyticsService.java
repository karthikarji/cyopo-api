package com.cyopo.core.service;

import com.cyopo.core.dto.response.AnalyticsResponse;
import com.cyopo.core.model.PortfolioAnalytic;
import com.cyopo.core.repository.PortfolioAnalyticRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PortfolioAnalyticRepository analyticRepository;
    private final PortfolioRepository portfolioRepository;

    @Transactional
    public void recordView(
            UUID portfolioId,
            UUID ownerId,
            UUID viewerUserId,
            String viewerIp) {

        // TODO: RabbitMQ - high traffic portfolios can generate
        // thousands of view events per minute. Replace direct DB
        // insert with publishing to "portfolio.view" queue.
        // Worker batch-inserts for better performance.
        PortfolioAnalytic analytic = PortfolioAnalytic.builder()
                .portfolioId(portfolioId)
                .ownerId(ownerId)
                .viewerUserId(viewerUserId)
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

        // If no specific portfolios requested get all user portfolios
        List<UUID> ids = portfolioIds != null && !portfolioIds.isEmpty()
                ? portfolioIds
                : portfolioRepository.findByUserId(userUUID)
                .stream()
                .map(p -> p.getId())
                .toList();

        if (ids.isEmpty()) {
            return AnalyticsResponse.builder()
                    .totalViews(0)
                    .uniqueVisitors(0)
                    .viewsByPeriod(new ArrayList<>())
                    .build();
        }

        // Total views
        long totalViews = ids.stream()
                .mapToLong(analyticRepository::countByPortfolioId)
                .sum();

        // Unique visitors
        long uniqueVisitors = analyticRepository
                .countUniqueVisitorsByPortfolioIds(ids);

        // Views by interval
        String validInterval = List.of(
                        "day", "week", "month", "year")
                .contains(interval) ? interval : "day";

        List<Object[]> rawResults = analyticRepository
                .getAnalyticsByInterval(
                        ids.toArray(new UUID[0]),
                        validInterval,
                        startDate,
                        endDate
                );

        List<AnalyticsResponse.ViewsByPeriod> viewsByPeriod =
                rawResults.stream()
                        .map(row -> AnalyticsResponse.ViewsByPeriod
                                .builder()
                                .period(row[0].toString())
                                .views(((Number) row[1]).longValue())
                                .uniqueVisitors(
                                        ((Number) row[2]).longValue())
                                .build())
                        .toList();

        return AnalyticsResponse.builder()
                .totalViews(totalViews)
                .uniqueVisitors(uniqueVisitors)
                .viewsByPeriod(viewsByPeriod)
                .build();
    }
}