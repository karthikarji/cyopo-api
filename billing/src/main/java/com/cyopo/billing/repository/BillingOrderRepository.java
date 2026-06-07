package com.cyopo.billing.repository;

import com.cyopo.billing.model.BillingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BillingOrderRepository extends JpaRepository<BillingOrder, UUID> {
    Optional<BillingOrder> findByGatewayOrderId(String gatewayOrderId);

    Optional<BillingOrder> findByIdempotencyKey(String idempotencyKey);

    // Used by order expiry job
    @Query("SELECT o FROM BillingOrder o WHERE o.status = 'PENDING' AND o.expiresAt < :now")
    List<BillingOrder> findExpiredPendingOrders(Instant now);

    Page<BillingOrder> findByStatus(String status, Pageable pageable);
}