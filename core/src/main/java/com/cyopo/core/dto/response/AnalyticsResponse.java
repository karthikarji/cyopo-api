package com.cyopo.core.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AnalyticsResponse {

    private long totalViews;
    private long uniqueVisitors;
    private long viewsThisWeek;
    private long viewsThisMonth;
    private long totalMessages;
    private List<PortfolioStats> portfolioBreakdown;
    private List<ViewsByPeriod> viewsByPeriod;

    @Getter
    @Builder
    public static class ViewsByPeriod {
        private String period;
        private long views;
        private long uniqueVisitors;
    }

    @Getter
    @Builder
    public static class PortfolioStats {
        private UUID portfolioId;
        private String portfolioName;
        private String portfolioSlug;
        private long   views;
        private long   uniqueVisitors;
        private long   messages;
    }
}