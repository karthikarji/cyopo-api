package com.cyopo.admin.dto.request;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class UpdateCouponRequest {

    private String     description;
    private Integer    maxUses;
    private Integer    perUserLimit;
    private Instant    validFrom;
    private Instant    validUntil;
    private Boolean    isActive;
    private List<UUID> targetUserIds;
}