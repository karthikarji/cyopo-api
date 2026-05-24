package com.cyopo.admin.scheduler;

import com.cyopo.billing.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponScheduler {

    private final CouponRepository couponRepository;

    // Runs every hour at minute 0
    @Scheduled(cron = "${app.scheduler.coupon-expiry-cron:0 0 * * * *}")
    @Transactional
    public void deactivateExpiredCoupons() {
        log.info("Coupon Deactivation Job started!");
        int count = couponRepository.deactivateExpired(Instant.now());
        if (count > 0) {
            log.info("Deactivated {} expired coupon(s)", count);
        }
    }
}