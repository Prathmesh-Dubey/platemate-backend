package com.mess.app.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Keeps uploads in a folder on disk. Meant for local development only: on hosts with an ephemeral
 * filesystem (Render, Railway, ...) these files are lost on every deploy or restart.
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path root;
    private final String baseUrl;

    public LocalStorageService(
            @Value("${storage.local.upload-dir:uploads/stock-images}") String uploadDir,
            @Value("${storage.local.base-url:/uploads/stock-images/}") String baseUrl) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        log.info("Storage: local folder {} (development only, files do not survive a redeploy on Render)", root);
    }

    @Override
    public String upload(String objectPath, byte[] data, String contentType) throws IOException {
        Path target = resolve(StorageObjectPaths.requireSafe(objectPath));
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, data);
        log.info("File stored locally: {}", objectPath);
        return baseUrl + objectPath;
    }

    @Override
    public void delete(String reference) throws IOException {
        String objectPath = objectPathOf(reference);
        if (objectPath == null) {
            return;
        }
        Path target = resolve(objectPath);
        if (Files.deleteIfExists(target)) {
            log.info("File deleted from local storage: {}", objectPath);
        }
    }

    @Override
    public boolean owns(String reference) {
        return reference != null && !reference.isEmpty() && !reference.startsWith("http");
    }

    /** Maps a stored reference back to its object path, or null when it is not one of ours / not safe. */
    private String objectPathOf(String reference) {
        if (!owns(reference)) {
            return null;
        }
        // Older records may only hold a file name or an unrelated prefix; keep the previous behaviour
        String relative = reference.startsWith(baseUrl)
                ? reference.substring(baseUrl.length())
                : reference.substring(reference.lastIndexOf('/') + 1);
        if (!StorageObjectPaths.isSafe(relative)) {
            log.warn("Refusing to touch an unsafe storage reference");
            return null;
        }
        return relative;
    }

    private Path resolve(String objectPath) {
        Path candidate = root.resolve(objectPath).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return candidate;
    }
}
