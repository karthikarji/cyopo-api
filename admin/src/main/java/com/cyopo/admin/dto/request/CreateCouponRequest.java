package com.cyopo.admin.dto.request;

import com.cyopo.billing.model.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class CreateCouponRequest {

    @NotBlank(message = "Code is required")
    @Size(min = 3, max = 50)
    @Pattern(
            regexp  = "^[A-Z0-9_-]+$",
            message = "Code must be uppercase letters, numbers, hyphens or underscores"
    )
    private String code;

    @Size(max = 255)
    private String description;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    private BigDecimal discountValue;  // null for FULL type

    private Integer maxUses;           // null = unlimited

    @Min(1)
    private Integer perUserLimit;

    private Instant validFrom;
    private Instant validUntil;

    private List<UUID> targetUserIds;  // empty = all users
}