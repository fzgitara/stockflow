package com.stockflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application-wide configuration, sourced from environment variables
 * (see application.yml for the env var names).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        String taxRate,
        Cors cors,
        Seed seed) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(java.util.List<String> allowedOrigins) {
    }

    public record Seed(boolean enabled) {
    }

    public java.math.BigDecimal taxRateAsDecimal() {
        return new java.math.BigDecimal(taxRate);
    }
}
