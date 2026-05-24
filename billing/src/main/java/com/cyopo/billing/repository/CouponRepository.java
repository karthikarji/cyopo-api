package com.cyopo.billing.repository;

import com.cyopo.billing.model.Coupon;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = """
    SELECT * FROM billing.coupons
    WHERE (
        :search IS NULL
        OR LOWER(code::text) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(description::text) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    ORDER BY created_at DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM billing.coupons
    WHERE (
        :search IS NULL
        OR LOWER(code::text) LIKE LOWER(CONCAT('%', :search, '%'))
        OR LOWER(description::text) LIKE LOWER(CONCAT('%', :search, '%'))
    )
    """,
            nativeQuery = true)
    Page<Coupon> searchCoupons(@Param("search") String search, Pageable pageable);


    @Modifying
    @Transactional
    @Query("UPDATE Coupon c SET c.isActive = false " +
            "WHERE c.validUntil IS NOT NULL " +
            "AND c.validUntil < :now " +
            "AND c.isActive = true")
    int deactivateExpired(@Param("now") Instant now);
}