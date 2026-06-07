package com.cyopo.core.service;

import com.cyopo.auth.repository.UserRepository;
import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.core.dto.request.ContactFormRequest;
import com.cyopo.core.dto.response.ContactResponse;
import com.cyopo.core.event.PortfolioContactReceivedEvent;
import com.cyopo.core.model.Contact;
import com.cyopo.core.model.ContactStatus;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.repository.ContactRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContactService {

    private static final String CLASS = "ContactService";

    private final ContactRepository contactRepository;
    private final PortfolioRepository portfolioRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    // ─── Send Contact ─────────────────────────────────────────────────

    /**
     * Submits a contact message from a portfolio visitor.
     * Each email is allowed only one message per portfolio to prevent spam.
     * Notifies portfolio owner via email if emailOnMessage preference is enabled.
     *
     * @throws ResourceNotFoundException if portfolio slug not found
     * @throws ConflictException         if visitor has already sent a message
     */
    @Transactional
    public void sendContact(String slug, ContactFormRequest request) {
        AppLogContext.info(CLASS, "sendContact",
                "Contact message received",
                "slug", slug,
                "email", request.getEmail());

        Portfolio portfolio = portfolioRepository
                .findBySlug(slug)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "sendContact",
                            "Portfolio not found for slug",
                            "slug", slug);
                    return new ResourceNotFoundException(
                            "Portfolio", "slug", slug);
                });

        // One message per email per portfolio — prevents spam
        if (contactRepository.existsByPortfolioIdAndEmail(
                portfolio.getId(), request.getEmail())) {
            AppLogContext.warn(CLASS, "sendContact",
                    "Duplicate contact attempt rejected",
                    "slug", slug,
                    "email", request.getEmail());
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

        AppLogContext.info(CLASS, "sendContact",
                "Contact message saved",
                "slug", slug,
                "portfolioId", portfolio.getId(),
                "email", request.getEmail());

        // Notify owner if emailOnMessage preference is enabled
        // ifPresent — silently skip notification if owner not found
        userRepository.findById(portfolio.getUserId()).ifPresent(owner -> {
            boolean shouldNotify = owner.getNotificationPreferences() == null
                    || owner.getNotificationPreferences().isEmailOnMessage();

            if (shouldNotify) {
                eventPublisher.publishEvent(new PortfolioContactReceivedEvent(
                        this,
                        portfolio.getProfile().getEmail(),
                        request.getName(),
                        request.getEmail(),
                        request.getSubject(),
                        request.getMessage()
                ));
                AppLogContext.debug(CLASS, "sendContact",
                        "Email notification dispatched",
                        "ownerId", owner.getId());
            } else {
                AppLogContext.debug(CLASS, "sendContact",
                        "Email notification skipped — owner preference off",
                        "ownerId", owner.getId());
            }
        });
    }

    // ─── Get Portfolio Messages ───────────────────────────────────────

    /**
     * Returns paginated messages for a specific portfolio.
     * Verifies ownership before returning data.
     *
     * @throws ResourceNotFoundException if portfolio not found or not owned by user
     */
    @Transactional(readOnly = true)
    public PageResponse<ContactResponse> getPortfolioMessages(
            String userId,
            UUID portfolioId,
            int page,
            int limit) {

        AppLogContext.info(CLASS, "getPortfolioMessages",
                "Fetching portfolio messages",
                "portfolioId", portfolioId,
                "userId", userId,
                "page", page);

        // Verify ownership — throws 404 if not found or not owned
        portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "getPortfolioMessages",
                            "Portfolio not found or not owned by user",
                            "portfolioId", portfolioId,
                            "userId", userId);
                    return new ResourceNotFoundException(
                            "Portfolio", "id", portfolioId);
                });

        Page<Contact> result = contactRepository
                .findByPortfolioIdOrderByCreatedAtDesc(
                        portfolioId,
                        PageRequest.of(page - 1, limit));

        AppLogContext.info(CLASS, "getPortfolioMessages",
                "Messages fetched",
                "portfolioId", portfolioId,
                "total", result.getTotalElements(),
                "page", page);

        return new PageResponse<>(
                result.getContent().stream()
                        .map(ContactResponse::from)
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    // ─── Get Portfolio Stats ──────────────────────────────────────────

    /**
     * Returns message stats (total + unread) for a specific portfolio.
     * Verifies ownership before returning data.
     *
     * @throws ResourceNotFoundException if portfolio not found or not owned by user
     */
    @Transactional(readOnly = true)
    public ContactStatsResponse getPortfolioContactStats(
            String userId, UUID portfolioId) {

        AppLogContext.debug(CLASS, "getPortfolioContactStats",
                "Fetching contact stats",
                "portfolioId", portfolioId,
                "userId", userId);

        portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "getPortfolioContactStats",
                            "Portfolio not found or not owned by user",
                            "portfolioId", portfolioId,
                            "userId", userId);
                    return new ResourceNotFoundException(
                            "Portfolio", "id", portfolioId);
                });

        long total = contactRepository.countByPortfolioId(portfolioId);
        long unread = contactRepository.countByPortfolioIdAndStatus(
                portfolioId, ContactStatus.UNREAD);

        AppLogContext.debug(CLASS, "getPortfolioContactStats",
                "Stats fetched",
                "portfolioId", portfolioId,
                "total", total,
                "unread", unread);

        return new ContactStatsResponse(total, unread);
    }

    // ─── Get All Portfolio Stats ──────────────────────────────────────

    /**
     * Returns message stats for ALL portfolios owned by the user.
     * Single endpoint — replaces N per-portfolio stats calls on the messages page.
     * Frontend calls this once on page load instead of once per portfolio.
     *
     * @param userId authenticated user's ID
     * @return list of stats per portfolio (portfolioId, name, slug, total, unread)
     */
    @Transactional(readOnly = true)
    public List<PortfolioStatsResponse> getAllPortfolioStats(String userId) {
        AppLogContext.info(CLASS, "getAllPortfolioStats",
                "Fetching all portfolio stats",
                "userId", userId);

        List<Portfolio> portfolios = portfolioRepository
                .findByUserId(UUID.fromString(userId));

        List<PortfolioStatsResponse> result = portfolios.stream()
                .map(p -> {
                    long total = contactRepository.countByPortfolioId(p.getId());
                    long unread = contactRepository.countByPortfolioIdAndStatus(
                            p.getId(), ContactStatus.UNREAD);
                    return new PortfolioStatsResponse(
                            p.getId().toString(),
                            p.getName(),
                            p.getSlug(),
                            total,
                            unread
                    );
                })
                .toList();

        AppLogContext.info(CLASS, "getAllPortfolioStats",
                "All portfolio stats fetched",
                "userId", userId,
                "portfolioCount", result.size());

        return result;
    }

    // ─── Mark As Read ─────────────────────────────────────────────────

    /**
     * Marks a contact message as READ.
     * Verifies ownership via portfolio before updating.
     *
     * @throws ResourceNotFoundException if message not found or not accessible
     */
    @Transactional
    public void markAsRead(String userId, UUID messageId) {
        AppLogContext.debug(CLASS, "markAsRead",
                "Marking message as read",
                "messageId", messageId,
                "userId", userId);

        Contact contact = contactRepository.findById(messageId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "markAsRead",
                            "Message not found",
                            "messageId", messageId);
                    return new ResourceNotFoundException(
                            "Message", "id", messageId);
                });

        // Verify ownership via portfolio — return 404 not 403
        // (do not reveal message exists to non-owner)
        portfolioRepository
                .findByIdAndUserId(
                        contact.getPortfolioId(),
                        UUID.fromString(userId))
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "markAsRead",
                            "Message ownership check failed",
                            "messageId", messageId,
                            "userId", userId);
                    return new ResourceNotFoundException(
                            "Message", "id", messageId);
                });

        contactRepository.markAsRead(messageId);

        AppLogContext.debug(CLASS, "markAsRead",
                "Message marked as read",
                "messageId", messageId);
    }

    // ─── Response Records ─────────────────────────────────────────────

    /**
     * Stats for a single portfolio — used by getPortfolioContactStats().
     */
    public record ContactStatsResponse(long total, long unread) {
    }

    /**
     * Stats for all portfolios — used by getAllPortfolioStats().
     * Includes portfolio metadata so frontend does not need a separate call.
     */
    public record PortfolioStatsResponse(
            String portfolioId,
            String portfolioName,
            String portfolioSlug,
            long total,
            long unread
    ) {
    }
}