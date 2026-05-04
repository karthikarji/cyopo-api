package com.cyopo.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserRegisteredEvent extends ApplicationEvent {

    private final String email;
    private final String name;
    private final String userId;

    public UserRegisteredEvent(Object source, String email,
                               String name, String userId) {
        super(source);
        this.email = email;
        this.name = name;
        this.userId = userId;
    }
}