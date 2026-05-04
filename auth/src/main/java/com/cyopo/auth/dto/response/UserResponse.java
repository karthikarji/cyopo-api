package com.cyopo.auth.dto.response;

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
public class UserResponse {

    private UUID id;
    private String name;
    private String email;
    private Role role;
    private Plan plan;
    private UserStatus status;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .plan(user.getPlan())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}