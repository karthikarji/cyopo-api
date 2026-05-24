package com.cyopo.admin.dto.response;

import com.cyopo.auth.model.Plan;
import com.cyopo.auth.model.Role;
import com.cyopo.auth.model.User;
import com.cyopo.auth.model.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {

    private UUID       id;
    private String     name;
    private String     email;
    private Role       role;
    private Plan       plan;
    private UserStatus status;
    private int        portfolioCount;
    private Instant    createdAt;

    public static AdminUserResponse from(User user, int portfolioCount) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .plan(user.getPlan())
                .status(user.getStatus())
                .portfolioCount(portfolioCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}