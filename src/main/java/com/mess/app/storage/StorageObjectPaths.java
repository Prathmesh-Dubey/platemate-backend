package com.mess.app.storage;

import java.util.regex.Pattern;

/**
 * Helpers that keep object paths safe. A path is a relative, slash separated list of plain
 * segments such as {@code receipts/bill_1700000000000.pdf}. Anything else (parent directory
 * references, absolute paths, back slashes, odd characters) is rejected.
 */
public final class StorageObjectPaths {

    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,149}");
    private static final int MAX_PATH_LENGTH = 500;

    private StorageObjectPaths() {
    }

    /**
     * Validates an object path and returns it unchanged.
     *
     * @throws IllegalArgumentException if the path is not a safe relative path
     */
    public static String requireSafe(String path) {
        if (path == null || path.isBlank() || path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        if (path.startsWith("/") || path.endsWith("/") || path.contains("\\") || path.contains("//")) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        for (String segment : path.split("/")) {
            if (!SEGMENT.matcher(segment).matches() || segment.contains("..")) {
                throw new IllegalArgumentException("Invalid storage path");
            }
        }
        return path;
    }

    /** Same check, but returns false instead of throwing. */
    public static boolean isSafe(String path) {
        try {
            requireSafe(path);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Turns arbitrary text (item name, file name, id) into one safe path segment: unsafe characters
     * become underscores, leading punctuation is removed, the length is limited.
     */
    public static String sanitizeSegment(String raw, int maxLength, String fallback) {
        String cleaned = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        cleaned = cleaned.replaceAll("\\.{2,}", "_");
        cleaned = cleaned.replaceAll("^[^A-Za-z0-9]+", "");
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
