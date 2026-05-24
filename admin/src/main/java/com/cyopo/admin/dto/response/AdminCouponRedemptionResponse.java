package com.cyopo.admin.dto.response;

import com.cyopo.billing.model.CouponRedemption;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminCouponRedemptionResponse {

    private UUID    id;
    private UUID    userId;
    private String  planBefore;
    private String  planAfter;
    private Instant redeemedAt;

    public static AdminCouponRedemptionResponse from(CouponRedemption r) {
        return AdminCouponRedemptionResponse.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .planBefore(r.getPlanBefore())
                .planAfter(r.getPlanAfter())
                .redeemedAt(r.getRedeemedAt())
                .build();
    }
}