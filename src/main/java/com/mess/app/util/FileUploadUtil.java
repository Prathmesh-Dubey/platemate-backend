package com.mess.app.util;

import com.mess.app.storage.StorageObjectPaths;
import com.mess.app.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Entry point for every file upload and delete in the application.
 *
 * <p>This class validates (type, size) and names the file safely, then hands the bytes to the active
 * {@link StorageService} (local folder in development, Supabase Storage in production). The public
 * methods and the references they return are unchanged, so controllers, services and the frontend
 * do not need to know where files are kept.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileUploadUtil {

    /** Image content types accepted for profile pictures (users, candidates, staff). */
    public static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");

    /** Image file extensions accepted for profile pictures. */
    public static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final Set<String> RECEIPT_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "pdf");

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "application/pdf", "pdf");

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif",
            "pdf", "application/pdf");

    private final StorageService storageService;

    /** Maximum accepted size for a profile image upload, in megabytes. */
    @Value("${app.upload.profile-image.max-size-mb:5}")
    private long profileImageMaxSizeMb;

    /** Maximum accepted size for stock images and expense receipts, in megabytes. */
    @Value("${app.upload.max-size-mb:10}")
    private long generalMaxSizeMb;

    /**
     * Upload a stock item image.
     *
     * @throws IllegalArgumentException when the file is not an accepted image or is too large
     */
    public String uploadStockImage(MultipartFile file, String fileName) throws IOException {
        String extension = validate(file, ALLOWED_IMAGE_EXTENSIONS, generalMaxSizeMb, "Image",
                "Unsupported image type. Allowed formats: JPG, JPEG, PNG, WebP, GIF");
        String safeName = StorageObjectPaths.sanitizeSegment(fileName, 80, "item");
        return store(file, "stock/" + safeName + "_" + System.currentTimeMillis() + "." + extension, extension);
    }

    /**
     * Upload an expense receipt / invoice (image or PDF).
     *
     * @throws IllegalArgumentException when the file is not an accepted image/PDF or is too large
     */
    public String uploadExpenseReceipt(MultipartFile file) throws IOException {
        String extension = validate(file, RECEIPT_EXTENSIONS, generalMaxSizeMb, "File",
                "Unsupported file type. Allowed formats: JPG, JPEG, PNG, WebP, GIF, PDF");

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String baseName = originalName.contains(".")
                ? originalName.substring(0, originalName.lastIndexOf('.'))
                : originalName;
        String safeBaseName = StorageObjectPaths.sanitizeSegment(baseName, 30, "receipt");

        return store(file, "receipts/" + safeBaseName + "_" + System.currentTimeMillis() + "." + extension, extension);
    }

    // ==================== PROFILE IMAGES ====================

    /**
     * Validates that the uploaded file is an image of an accepted type and size.
     *
     * @throws IllegalArgumentException with a user-friendly message (mapped to HTTP 400)
     */
    public void validateImageFile(MultipartFile file) {
        validate(file, ALLOWED_IMAGE_EXTENSIONS, profileImageMaxSizeMb, "Image",
                "Unsupported image type. Allowed formats: JPG, JPEG, PNG, WebP, GIF");
    }

    /**
     * Stores a profile image for any owner type (user, candidate, staff) with the same storage as
     * the other uploads.
     *
     * @param file      uploaded image
     * @param ownerType e.g. "user", "candidate", "staff"
     * @param ownerKey  a stable identifier of the owner (id / code)
     * @return the stored image reference, e.g. {@code /uploads/stock-images/profiles/user_1_1700000000000.jpg}
     */
    public String uploadProfileImage(MultipartFile file, String ownerType, String ownerKey) throws IOException {
        String extension = validate(file, ALLOWED_IMAGE_EXTENSIONS, profileImageMaxSizeMb, "Image",
                "Unsupported image type. Allowed formats: JPG, JPEG, PNG, WebP, GIF");
        if ("jpeg".equals(extension)) {
            extension = "jpg";
        }

        String safeType = StorageObjectPaths.sanitizeSegment(ownerType, 20, "profile");
        String safeKey = StorageObjectPaths.sanitizeSegment(ownerKey, 40, "unknown");
        return store(file, "profiles/" + safeType + "_" + safeKey + "_" + System.currentTimeMillis() + "." + extension,
                extension);
    }

    /**
     * Checks that a user supplied image reference is acceptable to persist:
     * an absolute http(s) URL or a path served by this backend (/uploads/...).
     * Temporary browser references (blob:, data:) are rejected because they
     * would not survive a page reload.
     */
    public static boolean isValidImageReference(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim();
        if (v.isEmpty() || v.length() > 2048) {
            return false;
        }
        if (v.startsWith("/uploads/")) {
            return true;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:") || lower.startsWith("javascript:")) {
            return false;
        }
        try {
            URI uri = new URI(v);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Deletes a previously stored file if it lives in our own storage.
     * Never throws - a failed cleanup must not break the profile update.
     */
    public void deleteStoredImageQuietly(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            storageService.delete(imageUrl);
        } catch (Exception e) {
            log.warn("Could not delete old file {}: {}", imageUrl, e.getMessage());
        }
    }

    /** Delete a stored image. References that are not stored by us (external URLs) are ignored. */
    public void deleteStockImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        storageService.delete(imageUrl);
    }

    // ==================== INTERNALS ====================

    private String store(MultipartFile file, String objectPath, String extension) throws IOException {
        String contentType = CONTENT_TYPE_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");
        return storageService.upload(objectPath, file.getBytes(), contentType);
    }

    /**
     * Checks size and type and returns the extension to store under. The stored extension always comes
     * from the allow-list (taken from the content type when the file name has none), so names like
     * {@code evil.exe} or {@code x.php} can never end up in storage.
     */
    private String validate(MultipartFile file, Set<String> allowedExtensions, long maxSizeMb, String noun,
                            String typeMessage) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image".equals(noun) ? "Image file is required" : "File is required");
        }

        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(noun + " is too large. Maximum size is " + maxSizeMb + " MB");
        }

        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT).trim() : "";
        String extension = extensionOf(file.getOriginalFilename());

        if (allowedExtensions.contains(extension)) {
            return extension;
        }
        String fromType = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (fromType != null && allowedExtensions.contains(fromType)) {
            return fromType;
        }
        throw new IllegalArgumentException(typeMessage);
    }

    /** Lower-case extension of a file name, restricted to letters and digits ("" when there is none). */
    private static String extensionOf(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,5}") ? extension : "";
    }
}
