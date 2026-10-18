package com.mess.app.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The single CORS configuration of the application.
 *
 * <p>It is plugged into Spring Security ({@code http.cors(...)} in {@link SecurityConfig}), so
 * preflight {@code OPTIONS} requests are answered before authentication and the JWT filter.
 * There is intentionally no second CORS setup in Spring MVC or in a servlet filter.</p>
 *
 * <p>Allowed origins come from configuration, never from the request:</p>
 * <ul>
 *   <li>{@code FRONTEND_URL} - the deployed frontend, e.g. {@code https://platemate.vercel.app}</li>
 *   <li>{@code CORS_ALLOWED_ORIGINS} - more origins, comma separated (wildcard sub domains such as
 *       {@code https://*.vercel.app} are allowed, a bare {@code *} is not)</li>
 *   <li>{@code CORS_ALLOW_LOCALHOST} - keeps {@code http://localhost:*} working for local
 *       development (default true; set it to false in production if you do not need it)</li>
 * </ul>
 */
@Configuration
@Slf4j
public class CorsConfig {

    static final List<String> LOCALHOST_ORIGINS = List.of(
            "http://localhost", "http://localhost:*",
            "http://127.0.0.1", "http://127.0.0.1:*");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins:}") String allowedOrigins,
            @Value("${cors.allow-localhost:true}") boolean allowLocalhost,
            @Value("${cors.max-age:3600}") long maxAgeSeconds) {

        CorsConfiguration configuration = buildConfiguration(allowedOrigins, allowLocalhost, maxAgeSeconds);
        log.info("CORS: allowed origin patterns = {}", configuration.getAllowedOriginPatterns());
        if (configuration.getAllowedOriginPatterns() == null || configuration.getAllowedOriginPatterns().isEmpty()) {
            log.warn("CORS: no origin is allowed. Set FRONTEND_URL (or CORS_ALLOWED_ORIGINS) or browsers will block the frontend.");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    static CorsConfiguration buildConfiguration(String allowedOrigins, boolean allowLocalhost, long maxAgeSeconds) {
        List<String> origins = new ArrayList<>(parseOrigins(allowedOrigins));
        if (allowLocalhost) {
            origins.addAll(LOCALHOST_ORIGINS);
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        // Credentials are only ever combined with an explicit origin list, never with "*"
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAgeSeconds);
        return configuration;
    }

    /**
     * Splits a comma / whitespace separated list of origins, drops blanks and a trailing slash
     * and rejects values that would be unsafe or silently never match.
     *
     * @throws IllegalStateException with a clear message for "*" or a value without http(s)://
     */
    static List<String> parseOrigins(String raw) {
        List<String> origins = new ArrayList<>();
        if (raw == null) {
            return origins;
        }
        for (String part : raw.split("[,;\\s]+")) {
            String origin = part.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (origin.equals("*")) {
                throw new IllegalStateException(
                        "CORS: '*' is not allowed because credentials are enabled. "
                                + "List the real frontend origin(s) in FRONTEND_URL / CORS_ALLOWED_ORIGINS.");
            }
            if (!(origin.startsWith("http://") || origin.startsWith("https://"))) {
                throw new IllegalStateException(
                        "CORS: origin '" + origin + "' must start with http:// or https:// "
                                + "(example: https://my-frontend.vercel.app)");
            }
            while (origin.endsWith("/")) {
                // browsers send the Origin header without a trailing slash
                origin = origin.substring(0, origin.length() - 1);
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        return origins;
    }
}
