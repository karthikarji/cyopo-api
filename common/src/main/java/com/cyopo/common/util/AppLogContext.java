package com.cyopo.common.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Centralized structured logging utility for all modules.
 * Format: [ClassName] [methodName] - message {key=value, key=value}
 * <p>
 * Usage:
 * AppLogContext.info(CLASS, "methodName", "message", "key1", val1, "key2", val2);
 * AppLogContext.error(CLASS, "methodName", "message", exception, "key1", val1);
 */
@Slf4j
public final class AppLogContext {

    private AppLogContext() {
    }

    // ─── INFO ──────────────────────────────────────────────────────

    public static void info(String className, String method,
                            String message, Object... params) {
        log.info("[{}] [{}] - {}", className, method,
                format(message, params));
    }

    // ─── WARN ──────────────────────────────────────────────────────

    public static void warn(String className, String method,
                            String message, Object... params) {
        log.warn("[{}] [{}] - {}", className, method,
                format(message, params));
    }

    // ─── ERROR (message only) ──────────────────────────────────────

    public static void error(String className, String method,
                             String message, Object... params) {
        log.error("[{}] [{}] - {}", className, method,
                format(message, params));
    }

    // ─── ERROR (with exception) ────────────────────────────────────

    public static void error(String className, String method,
                             String message, Throwable t,
                             Object... params) {
        log.error("[{}] [{}] - {}", className, method,
                format(message, params), t);
    }

    // ─── DEBUG ─────────────────────────────────────────────────────

    public static void debug(String className, String method,
                             String message, Object... params) {
        log.debug("[{}] [{}] - {}", className, method,
                format(message, params));
    }

    // ─── Formatter ─────────────────────────────────────────────────

    /**
     * Formats message with key=value pairs.
     * Params must be alternating key-value pairs.
     * <p>
     * Example:
     * format("User login", "userId", "123", "email", "a@b.com")
     * → "User login {userId=123, email=a@b.com}"
     * <p>
     * If params count is odd — appends as plain array (safe fallback).
     */
    private static String format(String message, Object... params) {
        if (params == null || params.length == 0) return message;

        if (params.length % 2 == 0) {
            StringBuilder sb = new StringBuilder(message).append(" {");
            for (int i = 0; i < params.length; i += 2) {
                if (i > 0) sb.append(", ");
                sb.append(params[i]).append("=").append(params[i + 1]);
            }
            return sb.append("}").toString();
        }

        // Odd number of params — safe fallback
        return message + " " + java.util.Arrays.toString(params);
    }
}