package com.cyopo.template.specification;

import com.cyopo.template.model.Template;
import com.cyopo.template.model.TemplateStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class TemplateSpecification {

    private TemplateSpecification() {}

    public static Specification<Template> hasStatus(TemplateStatus status) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<Template> isPremium(Boolean premium) {
        return (root, query, cb) ->
                cb.equal(root.get("premium"), premium);
    }

    public static Specification<Template> hasTag(String tag) {
        return (root, query, cb) -> {
            Join<Template, String> tags = root.join("tags");
            return cb.equal(tags, tag);
        };
    }

    public static Specification<Template> titleOrDescriptionContains(
            String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}