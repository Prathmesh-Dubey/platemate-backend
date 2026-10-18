package com.mess.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Static resources for locally stored uploads.
 *
 * <p>CORS is NOT configured here on purpose: the one and only CORS setup is
 * {@link CorsConfig}, used through Spring Security.</p>
 *
 * <p>With {@code storage.type=supabase} the files live in Supabase Storage and
 * {@link com.mess.app.storage.UploadsRedirectController} serves the same URLs, so the local
 * folders are not exposed at all.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${storage.type:local}")
    private String storageType;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!"local".equalsIgnoreCase(storageType)) {
            return;
        }

        String uploadUri = Paths.get("uploads/stock-images").toAbsolutePath().toUri().toString();
        if (!uploadUri.endsWith("/")) {
            uploadUri += "/";
        }
        registry.addResourceHandler("/uploads/stock-images/**")
                .addResourceLocations(uploadUri);

        String rootUploadUri = Paths.get("uploads").toAbsolutePath().toUri().toString();
        if (!rootUploadUri.endsWith("/")) {
            rootUploadUri += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(rootUploadUri);
    }
}
