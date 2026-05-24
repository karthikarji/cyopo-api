package com.cyopo.admin.dto.request;

import com.cyopo.auth.model.Plan;
import com.cyopo.auth.model.UserStatus;
import lombok.Getter;

@Getter
public class UpdateUserRequest {
    private String     name;
    private Plan       plan;
    private UserStatus status;
}