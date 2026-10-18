package com.mess.app.storage;

import java.io.IOException;

/**
 * Where uploaded files (stock images, expense receipts, profile pictures) are kept.
 *
 * <p>Two implementations exist and exactly one is active, chosen with {@code STORAGE_TYPE}:</p>
 * <ul>
 *   <li>{@code local}    - a folder on the machine running the backend (development)</li>
 *   <li>{@code supabase} - a Supabase Storage bucket (production, e.g. on Render)</li>
 * </ul>
 *
 * <p>The reference returned by {@link #upload} is what gets saved in the database. It has the same
 * shape in both modes (for example {@code /uploads/stock-images/stock/tomato_1700000000000.webp}),
 * so the frontend and the stored data do not depend on the storage provider.</p>
 */
public interface StorageService {

    /**
     * Stores the bytes under {@code objectPath} (for example {@code stock/tomato_1.webp}).
     *
     * @return the reference to persist
     * @throws IOException               when the storage could not be written
     * @throws IllegalArgumentException  when {@code objectPath} is not a safe relative path
     */
    String upload(String objectPath, byte[] data, String contentType) throws IOException;

    /**
     * Deletes the object behind a reference returned earlier by {@link #upload}.
     * References that do not belong to this storage are ignored. A missing object is not an error.
     */
    void delete(String reference) throws IOException;

    /** True when the reference points at an object managed by this storage. */
    boolean owns(String reference);
}
