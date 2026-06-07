package com.cyopo.billing.service;

import com.cyopo.common.util.AppLogContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Detects user's country from IP address using ip-api.com (free, no API key).
 * Result is cached per IP for 24 hours to stay within free tier limits (45 req/min).
 * Falls back to India (INR + Razorpay) on any failure — safe default for our market.
 */
@Service
public class GeoLocationService {

    private static final String CLASS = "GeoLocationService";

    // Free tier: 45 req/min, no API key required, HTTP only (not HTTPS on free tier)
    private static final String GEO_API_URL =
            "http://ip-api.com/json/%s?fields=status,countryCode,currency";

    // Safe fallback — India is primary market, Razorpay is active gateway
    private static final CountryInfo FALLBACK =
            new CountryInfo("IN", "INR", "RAZORPAY",
                    List.of("upi", "card", "netbanking"));

    private final RestTemplate restTemplate;

    public GeoLocationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Detects country, currency, gateway and suggested payment methods from IP.
     * Cached by IP — same IP will not hit ip-api.com again for 24 hours.
     * Always returns a valid CountryInfo — never throws, falls back to India on error.
     *
     * @param ip client IP address (IPv4 or IPv6)
     * @return CountryInfo with countryCode, currency, gateway, suggestedMethods
     */
    @Cacheable(value = "geoip", key = "#ip", unless = "#result == null")
    public CountryInfo detectCountry(String ip) {

        // Private / localhost IPs — skip API call, return fallback directly
        if (isPrivateIp(ip)) {
            AppLogContext.debug(CLASS, "detectCountry",
                    "Private IP — using fallback country", "ip", ip);
            return FALLBACK;
        }

        // External HTTP call — try-catch required
        // ip-api.com can be unreachable, timeout, or return unexpected response
        // We must never let a GeoIP failure break the checkout flow
        try {
            String url = String.format(GEO_API_URL, ip);
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !"success".equals(response.get("status"))) {
                AppLogContext.warn(CLASS, "detectCountry",
                        "GeoIP API returned non-success — using fallback",
                        "ip", ip,
                        "status", response != null ? response.get("status") : "null");
                return FALLBACK;
            }

            String countryCode = (String) response.get("countryCode");
            String currency = resolveCurrency(countryCode);
            String gateway = resolveGateway(currency);
            List<String> methods = resolvePaymentMethods(countryCode);

            AppLogContext.info(CLASS, "detectCountry",
                    "GeoIP lookup successful",
                    "ip", ip,
                    "country", countryCode,
                    "currency", currency,
                    "gateway", gateway);

            return new CountryInfo(countryCode, currency, gateway, methods);

        } catch (RestClientException e) {
            // Network error, timeout, connection refused — expected occasionally
            AppLogContext.warn(CLASS, "detectCountry",
                    "GeoIP HTTP call failed — using fallback",
                    "ip", ip,
                    "error", e.getMessage());
            return FALLBACK;

        } catch (Exception e) {
            // Unexpected error — log as error since this should not happen
            AppLogContext.error(CLASS, "detectCountry",
                    "Unexpected GeoIP error — using fallback", e,
                    "ip", ip);
            return FALLBACK;
        }
    }

    /**
     * Extracts the real client IP from the request.
     * Handles proxies, load balancers, CDN (Cloudflare) and ngrok headers.
     * X-Forwarded-For may contain a comma-separated chain — we take the first (client IP).
     *
     * @param request incoming HTTP request
     * @return real client IP address string
     */
    public String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String[] proxyHeaders = {
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",         // Cloudflare
                "X-Original-Forwarded-For"
        };

        for (String header : proxyHeaders) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // X-Forwarded-For: client, proxy1, proxy2 — take first
                String ip = value.split(",")[0].trim();
                AppLogContext.debug(CLASS, "extractClientIp",
                        "IP extracted from header",
                        "header", header, "ip", ip);
                return ip;
            }
        }

        // No proxy headers — use direct remote address
        String ip = request.getRemoteAddr();
        AppLogContext.debug(CLASS, "extractClientIp",
                "IP from remoteAddr", "ip", ip);
        return ip;
    }

    // ─── Private Helpers ──────────────────────────────────────────

    /**
     * Maps country code to currency.
     * Defaults to USD for unrecognised countries — Stripe handles global payments.
     */
    private String resolveCurrency(String countryCode) {
        return switch (countryCode) {
            case "IN" -> "INR";
            case "US" -> "USD";
            case "GB" -> "GBP";
            case "DE", "FR", "IT", "ES",
                 "NL", "BE", "AT", "PT" -> "EUR";
            default -> "USD";
        };
    }

    /**
     * Maps currency to payment gateway.
     * Only INR is live on Razorpay — all other currencies use Stripe (future).
     */
    private String resolveGateway(String currency) {
        return "INR".equals(currency) ? "RAZORPAY" : "STRIPE";
    }

    /**
     * Returns suggested payment methods ordered by popularity for the country.
     * Frontend uses this to configure Razorpay/Stripe checkout UI.
     */
    private List<String> resolvePaymentMethods(String countryCode) {
        return switch (countryCode) {
            case "IN" -> List.of("upi", "card", "netbanking", "wallet");
            case "US" -> List.of("card");
            case "GB" -> List.of("card");
            default -> List.of("card");
        };
    }

    /**
     * Returns true for localhost and private network IPs.
     * These cannot be looked up on ip-api.com.
     */
    private boolean isPrivateIp(String ip) {
        return ip == null
                || ip.startsWith("127.")
                || ip.startsWith("192.168.")
                || ip.startsWith("10.")
                || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equals("::1");
    }

    // ─── CountryInfo Record ───────────────────────────────────────

    /**
     * Immutable result of a GeoIP lookup.
     *
     * @param countryCode      ISO 3166-1 alpha-2 e.g. "IN", "US", "GB"
     * @param currency         ISO 4217 e.g. "INR", "USD", "GBP"
     * @param gateway          "RAZORPAY" | "STRIPE"
     * @param suggestedMethods payment methods ordered by local preference
     */
    public record CountryInfo(
            String countryCode,
            String currency,
            String gateway,
            List<String> suggestedMethods
    ) {
    }
}