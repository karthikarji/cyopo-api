package com.cyopo.core.scheduler;

import com.cyopo.core.service.WeeklyDigestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyDigestScheduler {

    private final WeeklyDigestService weeklyDigestService;

    // Every Monday at 8:00 AM UTC
    @Scheduled(cron = "${app.scheduler.weekly-digest-cron}")
    public void sendWeeklyDigests() {
        log.info("Weekly digest job started");
        weeklyDigestService.sendDigestToAllUsers();
        log.info("Weekly digest job completed");
    }
}