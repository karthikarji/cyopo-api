package com.cyopo.billing.repository;

import com.cyopo.billing.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    // Check idempotency — has this event been received before?
    Optional<WebhookEvent> findByGatewayAndEventId(String gateway, String eventId);

    boolean existsByGatewayAndEventId(String gateway, String eventId);

    long countByProcessed(boolean processed);

    Page<WebhookEvent> findByProcessed(boolean processed, Pageable pageable);
}