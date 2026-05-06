package com.cyopo.core.dto.request;

import com.cyopo.core.model.PortfolioStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class PortfolioStatusRequest {

    @NotNull(message = "Status is required")
    private PortfolioStatus status;
}