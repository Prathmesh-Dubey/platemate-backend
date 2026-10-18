package com.mess.app.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps uploads in a Supabase Storage bucket through the Storage REST API.
 *
 * <p><b>Credentials.</b> The backend talks to Storage with the project's service-role (secret) key,
 * because it must write to a private bucket. That key bypasses Row Level Security, so it must only
 * ever live in the backend's environment (never in the frontend, never in Git).</p>
 *
 * <p><b>Privacy.</b> The bucket's own privacy setting is respected:</p>
 * <ul>
 *   <li>private bucket (default): files are never public. {@link UploadsRedirectController} serves
 *       them by redirecting to a short lived signed URL created with the service key.</li>
 *   <li>public bucket ({@code SUPABASE_STORAGE_PUBLIC=true}): the redirect goes to the public URL.</li>
 * </ul>
 *
 * <p>The reference saved in the database is {@code <storage.local.base-url><objectPath>}, the same
 * shape the local storage uses, so existing records and the frontend keep working.</p>
 *
 * <p>The bucket is NOT created here. Create it once in the Supabase dashboard.</p>
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "supabase")
@Slf4j
public class SupabaseStorageService implements StorageService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long RESIGN_MARGIN_MILLIS = 5 * 60 * 1000L;
    private static final int MAX_CACHED_URLS = 2000;

    private final String storageBase;
    private final String apiKey;
    private final String bucket;
    private final boolean publicBucket;
    private final String referencePrefix;
    private final long signedUrlTtlSeconds;
    private final OkHttpClient http;
    private final Map<String, SignedUrl> signedUrls = new ConcurrentHashMap<>();

    private record SignedUrl(String url, long expiresAtMillis) {
    }

    public SupabaseStorageService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.storage.api-key:}") String apiKey,
            @Value("${supabase.storage.bucket-name:}") String bucket,
            @Value("${supabase.storage.public:false}") boolean publicBucket,
            @Value("${storage.local.base-url:/uploads/stock-images/}") String referencePrefix,
            @Value("${supabase.storage.signed-url-ttl-seconds:3600}") long signedUrlTtlSeconds) {

        if (isBlank(supabaseUrl) || isBlank(apiKey) || isBlank(bucket)) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=supabase needs SUPABASE_URL, SUPABASE_STORAGE_API_KEY and "
                            + "SUPABASE_STORAGE_BUCKET_NAME to be set (see .env.example)");
        }
        String base = supabaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.storageBase = base.endsWith("/storage/v1") ? base : base + "/storage/v1";
        this.apiKey = apiKey.trim();
        this.bucket = bucket.trim();
        this.publicBucket = publicBucket;
        this.referencePrefix = referencePrefix.endsWith("/") ? referencePrefix : referencePrefix + "/";
        this.signedUrlTtlSeconds = Math.max(60, signedUrlTtlSeconds);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(20))
                .writeTimeout(Duration.ofSeconds(20))
                .build();

        log.info("Storage: Supabase bucket '{}' ({})", this.bucket, publicBucket ? "public" : "private, signed URLs");
    }

    @Override
    public String upload(String objectPath, byte[] data, String contentType) throws IOException {
        StorageObjectPaths.requireSafe(objectPath);
        MediaType mediaType = MediaType.parse(contentType == null ? "application/octet-stream" : contentType);

        Request request = authorized(new Request.Builder()
                .url(objectUrl(objectPath))
                .header("x-upsert", "false")
                .post(RequestBody.create(data, mediaType)))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String reason = describe(response);
                log.error("Supabase upload of '{}' failed: HTTP {} {}", objectPath, response.code(), reason);
                throw new IOException("Supabase Storage upload failed (HTTP " + response.code() + "). "
                        + hint(response.code()));
            }
        }
        log.info("File stored in Supabase bucket '{}': {} ({} bytes)", bucket, objectPath, data.length);
        return referencePrefix + objectPath;
    }

    @Override
    public void delete(String reference) throws IOException {
        String objectPath = objectPathOf(reference);
        if (objectPath == null) {
            return;
        }
        Request request = authorized(new Request.Builder().url(objectUrl(objectPath)).delete()).build();
        try (Response response = http.newCall(request).execute()) {
            if (response.isSuccessful() || response.code() == 404) {
                signedUrls.remove(objectPath);
                log.info("File deleted from Supabase bucket '{}': {}", bucket, objectPath);
            } else {
                throw new IOException("Supabase Storage delete failed (HTTP " + response.code() + "). "
                        + hint(response.code()));
            }
        }
    }

    @Override
    public boolean owns(String reference) {
        return objectPathOf(reference) != null;
    }

    /**
     * URL the browser should be sent to for an object: the public URL for a public bucket,
     * otherwise a signed URL that expires. Signed URLs are cached until shortly before they expire.
     */
    public String resolveUrl(String objectPath) throws IOException {
        StorageObjectPaths.requireSafe(objectPath);
        if (publicBucket) {
            return storageBase + "/object/public/" + bucket + "/" + objectPath;
        }

        long now = System.currentTimeMillis();
        SignedUrl cached = signedUrls.get(objectPath);
        if (cached != null && cached.expiresAtMillis() - now > RESIGN_MARGIN_MILLIS) {
            return cached.url();
        }

        String body = "{\"expiresIn\":" + signedUrlTtlSeconds + "}";
        Request request = authorized(new Request.Builder()
                .url(storageBase + "/object/sign/" + bucket + "/" + objectPath)
                .post(RequestBody.create(body, MediaType.parse("application/json"))))
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Supabase could not sign '{}': HTTP {}", objectPath, response.code());
                throw new IOException("Supabase Storage could not create a signed URL (HTTP " + response.code() + ")");
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            String signed = json.path("signedURL").asText("");
            if (signed.isEmpty()) {
                throw new IOException("Supabase Storage returned no signed URL");
            }
            String url = signed.startsWith("http") ? signed : storageBase + (signed.startsWith("/") ? "" : "/") + signed;
            if (signedUrls.size() > MAX_CACHED_URLS) {
                signedUrls.clear();
            }
            signedUrls.put(objectPath, new SignedUrl(url, now + signedUrlTtlSeconds * 1000L));
            return url;
        }
    }

    /** The prefix that URLs saved in the database start with (also the path served by the redirect controller). */
    public String referencePrefix() {
        return referencePrefix;
    }

    // ---------------------------------------------------------------- helpers

    /** Accepts our own references and legacy absolute public URLs of this bucket. */
    private String objectPathOf(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        String candidate = null;
        if (reference.startsWith(referencePrefix)) {
            candidate = reference.substring(referencePrefix.length());
        } else {
            String legacy = "/object/public/" + bucket + "/";
            int at = reference.indexOf(legacy);
            if (reference.startsWith("http") && at >= 0) {
                candidate = reference.substring(at + legacy.length());
            }
        }
        return candidate != null && StorageObjectPaths.isSafe(candidate) ? candidate : null;
    }

    private String objectUrl(String objectPath) {
        return storageBase + "/object/" + bucket + "/" + objectPath;
    }

    private Request.Builder authorized(Request.Builder builder) {
        return builder.header("Authorization", "Bearer " + apiKey).header("apikey", apiKey);
    }

    /** Short provider message for the log. Never contains our credentials. */
    private static String describe(Response response) {
        try {
            if (response.body() == null) {
                return "";
            }
            JsonNode json = MAPPER.readTree(response.body().string());
            String message = json.path("message").asText(json.path("error").asText(""));
            return message.length() > 200 ? message.substring(0, 200) : message;
        } catch (Exception e) {
            return "";
        }
    }

    private static String hint(int status) {
        return switch (status) {
            case 400, 404 -> "Check that the bucket exists and SUPABASE_STORAGE_BUCKET_NAME is spelled exactly like it.";
            case 401, 403 -> "Check SUPABASE_STORAGE_API_KEY (it must be the service-role / secret key).";
            case 409 -> "An object with this path already exists.";
            case 413 -> "The file is larger than the bucket's file size limit.";
            default -> "See the backend log for details.";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
