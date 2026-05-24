package com.cyopo.admin.dto.request;

import com.cyopo.auth.model.Plan;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChangePlanRequest {

    @NotNull(message = "Plan is required")
    private Plan plan;
}