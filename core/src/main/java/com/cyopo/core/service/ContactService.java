package com.cyopo.core.service;

import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.core.dto.request.ContactFormRequest;
import com.cyopo.core.event.PortfolioContactReceivedEvent;
import com.cyopo.core.model.Contact;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.repository.ContactRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final PortfolioRepository portfolioRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void sendContact(String slug, ContactFormRequest request) {
        Portfolio portfolio = portfolioRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "slug", slug));

        // Check for duplicate — one message per email per portfolio
        if (contactRepository.existsByPortfolioIdAndEmail(
                portfolio.getId(), request.getEmail())) {
            throw new ConflictException(
                    "You have already sent a message to this portfolio. " +
                            "Please wait for a response before sending another.");
        }

        // Save contact message
        Contact contact = Contact.builder()
                .portfolioId(portfolio.getId())
                .name(request.getName())
                .email(request.getEmail())
                .subject(request.getSubject())
                .message(request.getMessage())
                .build();

        contactRepository.save(contact);

        // Publish event — email sent asynchronously
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
}