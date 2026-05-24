package com.cyopo.admin.dto.request;

import com.cyopo.auth.model.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ChangeStatusRequest {

    @NotNull(message = "Status is required")
    private UserStatus status;
}