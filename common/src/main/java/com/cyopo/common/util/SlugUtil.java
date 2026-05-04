package com.cyopo.common.util;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

public class SlugUtil {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_HYPHENS = Pattern.compile("(^-|-$)");
    private static final int MAX_LENGTH = 50;

    private SlugUtil() {}

    /**
     * Generates a URL-safe slug from a given name.
     * Example: "My Portfolio!" → "my-portfolio"
     */
    public static String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        String slug = normalized.toLowerCase()
                .trim();

        slug = NON_ALPHANUMERIC.matcher(slug).replaceAll("-");
        slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");

        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH);
            slug = LEADING_TRAILING_HYPHENS.matcher(slug).replaceAll("");
        }

        return slug;
    }

    /**
     * Given a base slug and a list of existing slugs that start with that base,
     * returns the next available slug.
     *
     * Example:
     *   base = "my-portfolio"
     *   existing = ["my-portfolio", "my-portfolio-1", "my-portfolio-2"]
     *   returns "my-portfolio-3"
     */
    public static String nextAvailableSlug(String base, List<String> existingSlugs) {
        if (existingSlugs == null || existingSlugs.isEmpty()) {
            return base;
        }

        if (!existingSlugs.contains(base)) {
            return base;
        }

        int max = 0;
        for (String existing : existingSlugs) {
            if (existing.equals(base)) continue;
            String suffix = existing.replace(base + "-", "");
            try {
                int num = Integer.parseInt(suffix);
                if (num > max) max = num;
            } catch (NumberFormatException ignored) {}
        }

        return base + "-" + (max + 1);
    }

    /**
     * Validates a slug format.
     * Must be lowercase, alphanumeric with hyphens, 3-50 chars.
     */
    public static boolean isValidSlug(String slug) {
        if (slug == null) return false;
        if (slug.length() < 3 || slug.length() > MAX_LENGTH) return false;
        return slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    }
}