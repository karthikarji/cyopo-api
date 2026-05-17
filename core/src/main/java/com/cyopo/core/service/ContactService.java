package com.cyopo.core.service;

import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.dto.request.ContactFormRequest;
import com.cyopo.core.dto.response.ContactResponse;
import com.cyopo.core.event.PortfolioContactReceivedEvent;
import com.cyopo.core.model.Contact;
import com.cyopo.core.model.ContactStatus;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.repository.ContactRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository    contactRepository;
    private final PortfolioRepository  portfolioRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendContact(String slug, ContactFormRequest request) {
        Portfolio portfolio = portfolioRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "slug", slug));

        if (contactRepository.existsByPortfolioIdAndEmail(
                portfolio.getId(), request.getEmail())) {
            throw new ConflictException(
                    "You have already sent a message to this portfolio. " +
                            "Please wait for a response before sending another.");
        }

        Contact contact = Contact.builder()
                .portfolioId(portfolio.getId())
                .name(request.getName())
                .email(request.getEmail())
                .subject(request.getSubject())
                .message(request.getMessage())
                .build();

        contactRepository.save(contact);

        eventPublisher.publishEvent(new PortfolioContactReceivedEvent(
                this,
                portfolio.getProfile().getEmail(),
                request.getName(),
                request.getEmail(),
                request.getSubject(),
                request.getMessage()
        ));

        log.info("Contact message saved for portfolio: {}", slug);
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> getPortfolioMessages(
            String userId,
            UUID portfolioId,
            int page,
            int limit) {

        // Verify ownership
        portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        Page<Contact> result = contactRepository
                .findByPortfolioIdOrderByCreatedAtDesc(
                        portfolioId,
                        PageRequest.of(page - 1, limit));

        return new PageResponse<>(
                result.getContent().stream()
                        .map(ContactResponse::from)
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public ContactStatsResponse getPortfolioContactStats(
            String userId, UUID portfolioId) {

        portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        long total  = contactRepository.countByPortfolioId(portfolioId);
        long unread = contactRepository.countByPortfolioIdAndStatus(
                portfolioId, ContactStatus.UNREAD);

        return new ContactStatsResponse(total, unread);
    }

    @Transactional
    public void markAsRead(String userId, UUID messageId) {
        Contact contact = contactRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message", "id", messageId));

        // Verify ownership via portfolio
        portfolioRepository
                .findByIdAndUserId(
                        contact.getPortfolioId(),
                        UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Message", "id", messageId));

        contactRepository.markAsRead(messageId);
    }

    public record ContactStatsResponse(long total, long unread) {}
}