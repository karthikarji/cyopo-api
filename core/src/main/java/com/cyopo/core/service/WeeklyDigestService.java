package com.cyopo.core.service;

import com.cyopo.auth.model.User;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.repository.ContactRepository;
import com.cyopo.core.repository.PortfolioAnalyticRepository;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cyopo.core.event.WeeklyDigestEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyDigestService {

    private final UserRepository              userRepository;
    private final PortfolioRepository         portfolioRepository;
    private final PortfolioAnalyticRepository analyticRepository;
    private final ContactRepository           contactRepository;
    private final ApplicationEventPublisher   eventPublisher;

    @Transactional(readOnly = true)
    public void sendDigestToAllUsers() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // Fetch only users who have weekly digest enabled
        List<User> users = userRepository
                .findUsersWithWeeklyDigestEnabled();

        log.info("Sending weekly digest to {} users", users.size());

        for (User user : users) {
            try {
                sendDigestToUser(user, weekAgo);
            } catch (Exception ex) {
                log.error("Failed to send digest to {}: {}",
                        user.getEmail(), ex.getMessage());
            }
        }
    }

    private void sendDigestToUser(User user, Instant weekAgo) {
        List<Portfolio> portfolios = portfolioRepository
                .findByUserId(user.getId());

        if (portfolios.isEmpty()) return;

        List<java.util.UUID> portfolioIds = portfolios.stream()
                .map(Portfolio::getId)
                .toList();

        // Views this week
        long viewsThisWeek = analyticRepository
                .countByPortfolioIdsAndCreatedAtAfter(
                        portfolioIds, weekAgo);

        // Messages this week
        long messagesThisWeek = contactRepository
                .countByPortfolioIdsAndCreatedAtAfter(
                        portfolioIds, weekAgo);

        // Skip if nothing to report
        if (viewsThisWeek == 0 && messagesThisWeek == 0) {
            log.debug("No activity for user {} — skipping digest",
                    user.getEmail());
            return;
        }

        eventPublisher.publishEvent(new WeeklyDigestEvent(
                this,
                user.getEmail(),
                user.getName(),
                viewsThisWeek,
                messagesThisWeek,
                portfolios.size()
        ));
    }
}