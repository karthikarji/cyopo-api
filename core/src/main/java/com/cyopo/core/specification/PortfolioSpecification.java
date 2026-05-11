package com.cyopo.core.specification;

import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.PortfolioStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class PortfolioSpecification {

    private PortfolioSpecification() {}

    public static Specification<Portfolio> hasUserId(UUID userId) {
        return (root, query, cb) ->
                cb.equal(root.get("userId"), userId);
    }

    public static Specification<Portfolio> hasStatus(
            PortfolioStatus status) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<Portfolio> hasTemplateId(
            UUID templateId) {
        return (root, query, cb) ->
                cb.equal(root.get("templateId"), templateId);
    }

    public static Specification<Portfolio> isPublic() {
        return (root, query, cb) ->
                cb.equal(
                        root.get("settings").get("isPublic"),
                        true);
    }

    public static Specification<Portfolio> nameContains(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.like(cb.lower(root.get("name")), pattern);
        };
    }
}