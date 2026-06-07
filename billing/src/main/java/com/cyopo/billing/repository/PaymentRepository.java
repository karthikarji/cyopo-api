package com.cyopo.billing.repository;

import com.cyopo.billing.model.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Used by reconciliation job — CAPTURED payments with no subscription yet
    @Query("SELECT p FROM Payment p WHERE p.status = 'CAPTURED' AND p.subscription IS NULL AND p.createdAt > :since")
    List<Payment> findCapturedWithoutSubscription(Instant since);

    // Used by IP masking job
    @Query("SELECT p FROM Payment p WHERE p.createdAt < :cutoff AND p.ipMaskedAt IS NULL AND p.ipAddress IS NOT NULL")
    List<Payment> findPaymentsForIpMasking(Instant cutoff);


    // Revenue stats
    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Payment p WHERE p.status = 'CAPTURED'")
    long sumCapturedPayments();

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM Payment p WHERE p.status = 'CAPTURED' AND p.createdAt >= :since")
    long sumCapturedPaymentsSince(@Param("since") Instant since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.createdAt >= :since")
    long countByStatusSince(@Param("status") String status, @Param("since") Instant since);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.refundedAt >= :since")
    long countRefundedSince(@Param("since") Instant since);

    // Admin list
    Page<Payment> findByStatus(String status, Pageable pageable);
}