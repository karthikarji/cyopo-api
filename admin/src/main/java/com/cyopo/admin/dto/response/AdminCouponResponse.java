package com.cyopo.admin.dto.response;

import com.cyopo.billing.model.Coupon;
import com.cyopo.billing.model.DiscountType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminCouponResponse {

    private UUID         id;
    private String       code;
    private String       description;
    private DiscountType discountType;
    private BigDecimal   discountValue;
    private Integer      maxUses;
    private Integer      usedCount;
    private Integer      perUserLimit;
    private Instant      validFrom;
    private Instant      validUntil;
    private Boolean      isActive;
    private List<UUID>   targetUserIds;
    private boolean      isPublic;
    private Instant      createdAt;

    public static AdminCouponResponse from(Coupon coupon) {
        return AdminCouponResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .maxUses(coupon.getMaxUses())
                .usedCount(coupon.getUsedCount())
                .perUserLimit(coupon.getPerUserLimit())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .isActive(coupon.getIsActive())
                .targetUserIds(coupon.getTargetUserIds())
                .isPublic(coupon.getTargetUserIds().isEmpty())
                .createdAt(coupon.getCreatedAt())
                .build();
    }
}