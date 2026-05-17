package com.cyopo.core.dto.response;

import com.cyopo.core.model.Contact;
import com.cyopo.core.model.ContactStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ContactResponse {

    private UUID          id;
    private UUID          portfolioId;
    private String        name;
    private String        email;
    private String        subject;
    private String        message;
    private ContactStatus status;
    private Instant       createdAt;

    public static ContactResponse from(Contact contact) {
        return ContactResponse.builder()
                .id(contact.getId())
                .portfolioId(contact.getPortfolioId())
                .name(contact.getName())
                .email(contact.getEmail())
                .subject(contact.getSubject())
                .message(contact.getMessage())
                .status(contact.getStatus())
                .createdAt(contact.getCreatedAt())
                .build();
    }
}