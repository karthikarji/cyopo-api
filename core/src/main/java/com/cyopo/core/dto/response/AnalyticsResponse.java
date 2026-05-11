package com.cyopo.core.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnalyticsResponse {

    private long totalViews;
    private long uniqueVisitors;
    private List<ViewsByPeriod> viewsByPeriod;

    @Getter
    @Builder
    public static class ViewsByPeriod {
        private String period;
        private long views;
        private long uniqueVisitors;
    }
}