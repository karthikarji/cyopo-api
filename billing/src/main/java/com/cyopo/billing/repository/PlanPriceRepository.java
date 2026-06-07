package com.cyopo.billing.repository;

import com.cyopo.billing.model.PlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanPriceRepository extends JpaRepository<PlanPrice, UUID> {
    // Find active price for a plan in a specific currency
    Optional<PlanPrice> findByPlanIdAndCurrencyAndIsActiveTrue(UUID planId, String currency);

    // All active prices for a currency (for pricing page)
    List<PlanPrice> findByCurrencyAndIsActiveTrue(String currency);
}