package com.cyopo.common.util;

public class SanitizationUtil {

    private SanitizationUtil() {}

    /**
     * Strips HTML tags, encoded entities, control characters,
     * and trims whitespace from input strings.
     * Mirrors the sanitize() function from the Next.js zod-helper.ts
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        if (input.isBlank()) return "";

        String result = input
                // Remove HTML tags
                .replaceAll("<[^>]*>?", "")
                // Remove angle brackets
                .replaceAll("[<>]", "")
                // Remove encoded HTML entities
                .replaceAll("&[a-zA-Z]+;", "")
                // Remove control characters
                .replaceAll("[\\u0000-\\u001F\\u007F]", "")
                // Remove quotes and parentheses
                .replaceAll("[\"'()]", "")
                .trim();

        // Cap at 1000 characters
        if (result.length() > 1000) {
            result = result.substring(0, 1000);
        }

        return result;
    }

    /**
     * Sanitizes and truncates to a specific max length.
     */
    public static String sanitize(String input, int maxLength) {
        String result = sanitize(input);
        if (result == null) return null;
        return result.length() > maxLength
                ? result.substring(0, maxLength)
                : result;
    }

    /**
     * Returns true if the input contains potentially dangerous content.
     */
    public static boolean hasForbiddenContent(String input) {
        if (input == null) return false;
        return input.matches(".*<[^>]*>.*")
                || input.contains("<")
                || input.contains(">")
                || input.matches(".*&[a-zA-Z]+;.*");
    }
}