package com.cyopo.billing.repository;

import com.cyopo.billing.model.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CouponRedemptionRepository
        extends JpaRepository<CouponRedemption, UUID> {

    List<CouponRedemption> findByCouponIdOrderByRedeemedAtDesc(UUID couponId);

    long countByCouponIdAndUserId(UUID couponId, UUID userId);

    long countByCouponId(UUID couponId);
}