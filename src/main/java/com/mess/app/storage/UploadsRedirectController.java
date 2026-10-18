package com.mess.app.storage;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * Serves uploaded files when they live in Supabase Storage.
 *
 * <p>Saved references look like {@code /uploads/stock-images/stock/tomato_1.webp} (same as local
 * storage), so {@code <img src>} tags in the frontend keep working. This endpoint answers such a
 * request with a redirect to the file in Supabase: a signed URL for a private bucket, the public
 * URL for a public bucket. The service-role key never leaves the backend.</p>
 *
 * <p>Like the old static {@code /uploads/**} handler, this endpoint needs no login (see
 * {@link com.mess.app.config.SecurityConfig}): browsers cannot attach a JWT to an {@code <img>} tag.
 * Anyone who knows an exact file path can open that file, but the bucket itself stays private
 * and cannot be listed.</p>
 */
@RestController
@ConditionalOnProperty(name = "storage.type", havingValue = "supabase")
@Slf4j
public class UploadsRedirectController {

    private final SupabaseStorageService storage;

    public UploadsRedirectController(SupabaseStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("${storage.local.base-url:/uploads/stock-images/}**")
    public ResponseEntity<Void> redirectToObject(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }

        String prefix = storage.referencePrefix();
        if (!path.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String objectPath = path.substring(prefix.length());
        if (!StorageObjectPaths.isSafe(objectPath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.status(302)
                    .header(HttpHeaders.LOCATION, URI.create(storage.resolveUrl(objectPath)).toString())
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                    .build();
        } catch (Exception e) {
            log.warn("Could not resolve stored file '{}': {}", objectPath, e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }
}
