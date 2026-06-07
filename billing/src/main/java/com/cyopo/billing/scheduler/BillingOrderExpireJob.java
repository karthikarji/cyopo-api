package com.cyopo.billing.scheduler;

import com.cyopo.billing.repository.BillingOrderRepository;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Expires PENDING orders that were abandoned by the user.
 * <p>
 * A PENDING order is abandoned when:
 * → User opened checkout but never completed payment
 * → User closed browser mid-checkout
 * → Razorpay order expired (15 minute window)
 * <p>
 * Runs every 15 minutes via app.scheduler.billing-order-expire-cron.
 */
@Component
@RequiredArgsConstructor
public class BillingOrderExpireJob {

    private static final String CLASS = "BillingOrderExpireJob";

    private final BillingOrderRepository orderRepository;

    @Scheduled(cron = "${app.scheduler.billing-order-expire-cron:0 */15 * * * *}")
    @Transactional
    public void expireAbandonedOrders() {
        Instant now = Instant.now();
        var expired = orderRepository.findExpiredPendingOrders(now);

        if (expired.isEmpty()) {
            AppLogContext.debug(CLASS, "expireAbandonedOrders",
                    "No abandoned orders to expire");
            return;
        }

        // If the DB call fails, @Transactional rolls back the entire batch
        // The job will retry successfully on the next 15-minute run
        expired.forEach(order -> order.setStatus("EXPIRED"));
        orderRepository.saveAll(expired);

        AppLogContext.info(CLASS, "expireAbandonedOrders",
                "Abandoned orders expired",
                "count", expired.size());
    }
}