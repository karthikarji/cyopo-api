package com.cyopo.admin.service;

import com.cyopo.admin.dto.request.CreateCouponRequest;
import com.cyopo.admin.dto.request.UpdateCouponRequest;
import com.cyopo.admin.dto.response.AdminCouponResponse;
import com.cyopo.admin.dto.response.AdminCouponRedemptionResponse;
import com.cyopo.billing.model.Coupon;
import com.cyopo.billing.repository.CouponRedemptionRepository;
import com.cyopo.billing.repository.CouponRepository;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminCouponResponse> getAll(
            String search, int page, int limit) {

        PageRequest pageable = PageRequest.of(
                page - 1, limit,
                Sort.by(Sort.Direction.DESC, "created_at"));

        Page<Coupon> result = couponRepository.searchCoupons(
                (search != null && !search.isBlank()) ? search : null,
                pageable
        );

        return new PageResponse<>(
                result.getContent().stream()
                        .map(AdminCouponResponse::from)
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public AdminCouponResponse getById(UUID id) {
        return AdminCouponResponse.from(findById(id));
    }

    @Transactional
    public AdminCouponResponse create(UUID adminId,
                                      CreateCouponRequest request) {
        if (couponRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new ConflictException(
                    "A coupon with this code already exists");
        }

        if (request.getDiscountType().name().equals("PERCENTAGE")) {
            if (request.getDiscountValue() == null
                    || request.getDiscountValue().doubleValue() <= 0
                    || request.getDiscountValue().doubleValue() > 100) {
                throw new BadRequestException(
                        "Percentage discount must be between 1 and 100");
            }
        }

        if (!request.getDiscountType().name().equals("FULL")
                && request.getDiscountValue() == null) {
            throw new BadRequestException(
                    "Discount value is required for " +
                            request.getDiscountType() + " type");
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxUses(request.getMaxUses())
                .perUserLimit(request.getPerUserLimit() != null
                        ? request.getPerUserLimit() : 1)
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .targetUserIds(request.getTargetUserIds() != null
                        ? new ArrayList<>(request.getTargetUserIds())
                        : new ArrayList<>())
                .createdBy(adminId)
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created: {} by admin: {}",
                saved.getCode(), adminId);
        return AdminCouponResponse.from(saved);
    }

    @Transactional
    public AdminCouponResponse update(UUID id,
                                      UpdateCouponRequest request) {
        Coupon coupon = findById(id);

        if (request.getDescription() != null)
            coupon.setDescription(request.getDescription());
        if (request.getMaxUses() != null)
            coupon.setMaxUses(request.getMaxUses());
        if (request.getPerUserLimit() != null)
            coupon.setPerUserLimit(request.getPerUserLimit());
        if (request.getValidFrom() != null)
            coupon.setValidFrom(request.getValidFrom());
        if (request.getValidUntil() != null)
            coupon.setValidUntil(request.getValidUntil());
        if (request.getIsActive() != null)
            coupon.setIsActive(request.getIsActive());
        if (request.getTargetUserIds() != null)
            coupon.setTargetUserIds(
                    new ArrayList<>(request.getTargetUserIds()));

        Coupon updated = couponRepository.save(coupon);
        log.info("Coupon updated: {}", updated.getCode());
        return AdminCouponResponse.from(updated);
    }

    @Transactional
    public AdminCouponResponse toggleActive(UUID id) {
        Coupon coupon = findById(id);

        // If trying to activate — check expiry
        if (!coupon.getIsActive()
                && coupon.getValidUntil() != null
                && coupon.getValidUntil().isBefore(Instant.now())) {
            throw new BadRequestException(
                    "Cannot activate an expired coupon. " +
                            "Please update the expiry date first.");
        }

        coupon.setIsActive(!coupon.getIsActive());
        Coupon updated = couponRepository.save(coupon);
        log.info("Coupon {} toggled to active={}",
                updated.getCode(), updated.getIsActive());
        return AdminCouponResponse.from(updated);
    }

    @Transactional
    public void delete(UUID id) {
        Coupon coupon = findById(id);
        couponRepository.delete(coupon);
        log.info("Coupon deleted: {}", coupon.getCode());
    }

    @Transactional(readOnly = true)
    public List<AdminCouponRedemptionResponse> getRedemptions(UUID id) {
        findById(id);
        return redemptionRepository
                .findByCouponIdOrderByRedeemedAtDesc(id)
                .stream()
                .map(AdminCouponRedemptionResponse::from)
                .toList();
    }

    private Coupon findById(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Coupon", "id", id));
    }
}