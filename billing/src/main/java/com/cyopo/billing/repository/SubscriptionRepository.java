package com.cyopo.billing.repository;

import com.cyopo.billing.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    // Active subscription for a user
    Optional<Subscription> findByUserIdAndStatus(UUID userId, String status);

    // Used by expiry job
    @Query("SELECT s FROM Subscription s WHERE s.currentPeriodEnd < :now AND s.status = 'ACTIVE' AND s.cancelAtPeriodEnd = true")
    List<Subscription> findExpiredSubscriptions(Instant now);

    // Used by renewal retry job
    @Query("SELECT s FROM Subscription s WHERE s.status = 'PAST_DUE' AND (s.gracePeriodEnd IS NULL OR s.gracePeriodEnd > :now)")
    List<Subscription> findPastDueWithinGracePeriod(Instant now);

    // Used by expiry job for grace period exceeded
    @Query("SELECT s FROM Subscription s WHERE s.status = 'PAST_DUE' AND s.gracePeriodEnd < :now")
    List<Subscription> findPastDueGraceExpired(Instant now);

    long countByStatus(String status);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.cancelledAt >= :since")
    long countCancelledSince(@Param("since") Instant since);

    Page<Subscription> findByStatus(String status, Pageable pageable);
}