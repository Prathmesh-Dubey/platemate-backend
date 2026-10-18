package com.mess.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    @Test
    void allowsTheConfiguredFrontendAndLocalhostButNothingElse() {
        CorsConfiguration config = CorsConfig.buildConfiguration("https://platemate.vercel.app", true, 3600);

        assertThat(config.checkOrigin("https://platemate.vercel.app")).isEqualTo("https://platemate.vercel.app");
        assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(config.checkOrigin("http://127.0.0.1:3000")).isEqualTo("http://127.0.0.1:3000");
        assertThat(config.checkOrigin("https://evil.example.com")).isNull();
        assertThat(config.checkOrigin("https://platemate.vercel.app.evil.com")).isNull();
    }

    @Test
    void credentialsAreEnabledOnlyTogetherWithAnExplicitOriginList() {
        CorsConfiguration config = CorsConfig.buildConfiguration("https://a.example.com", false, 3600);

        assertThat(config.getAllowCredentials()).isTrue();
        assertThat(config.getAllowedOriginPatterns()).containsExactly("https://a.example.com");
        assertThat(config.getAllowedOriginPatterns()).doesNotContain("*");
    }

    @Test
    void supportsSeveralOriginsAndIgnoresBlanksAndTrailingSlashes() {
        List<String> origins = CorsConfig.parseOrigins(" https://a.example.com/ , ,https://b.example.com;https://a.example.com ");

        assertThat(origins).containsExactly("https://a.example.com", "https://b.example.com");
    }

    @Test
    void emptyValuesFromUnsetEnvironmentVariablesAreHarmless() {
        // application.properties builds the value as "${FRONTEND_URL:},${CORS_ALLOWED_ORIGINS:}"
        assertThat(CorsConfig.parseOrigins(",")).isEmpty();
        assertThat(CorsConfig.parseOrigins(null)).isEmpty();
    }

    @Test
    void localhostCanBeSwitchedOffForProduction() {
        CorsConfiguration config = CorsConfig.buildConfiguration("https://platemate.vercel.app", false, 3600);

        assertThat(config.checkOrigin("http://localhost:5173")).isNull();
        assertThat(config.checkOrigin("https://platemate.vercel.app")).isNotNull();
    }

    @Test
    void wildcardSubdomainPatternsWork() {
        CorsConfiguration config = CorsConfig.buildConfiguration("https://*.vercel.app", false, 3600);

        assertThat(config.checkOrigin("https://my-preview-123.vercel.app")).isNotNull();
        assertThat(config.checkOrigin("https://vercel.app.evil.com")).isNull();
    }

    @Test
    void aBareWildcardIsRejectedBecauseCredentialsAreEnabled() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'*' is not allowed");
    }

    @Test
    void anOriginWithoutSchemeIsRejectedWithAHelpfulMessage() {
        assertThatThrownBy(() -> CorsConfig.parseOrigins("my-frontend.vercel.app"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must start with http:// or https://");
    }

    @Test
    void preflightMethodsAreAllowed() {
        CorsConfiguration config = CorsConfig.buildConfiguration("https://a.example.com", false, 3600);

        assertThat(config.checkHttpMethod(org.springframework.http.HttpMethod.OPTIONS)).isNotNull();
        assertThat(config.checkHttpMethod(org.springframework.http.HttpMethod.PATCH)).isNotNull();
        assertThat(config.checkHeaders(List.of("authorization", "content-type"))).isNotEmpty();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }
}
