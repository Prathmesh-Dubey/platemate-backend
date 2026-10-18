package com.mess.app.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service() {
        return new LocalStorageService(tempDir.resolve("uploads").toString(), "/uploads/stock-images/");
    }

    @Test
    void storesAFileAndReturnsTheSameReferenceShapeAsBefore() throws IOException {
        String reference = service().upload("stock/tomato_1.webp", new byte[]{1, 2, 3}, "image/webp");

        assertThat(reference).isEqualTo("/uploads/stock-images/stock/tomato_1.webp");
        assertThat(Files.readAllBytes(tempDir.resolve("uploads/stock/tomato_1.webp"))).containsExactly(1, 2, 3);
    }

    @Test
    void deletesAStoredFileAndIgnoresMissingOrForeignReferences() throws IOException {
        LocalStorageService service = service();
        String reference = service.upload("profiles/user_1.png", new byte[]{9}, "image/png");

        service.delete(reference);
        assertThat(Files.exists(tempDir.resolve("uploads/profiles/user_1.png"))).isFalse();

        service.delete(reference);                                  // already gone: no error
        service.delete("https://cdn.example.com/other.png");        // not ours: ignored
        service.delete(null);
    }

    @Test
    void refusesToWriteOutsideTheUploadFolder() {
        LocalStorageService service = service();

        assertThatThrownBy(() -> service.upload("../escape.txt", new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upload("/etc/passwd", new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(tempDir.resolve("escape.txt"))).isFalse();
    }

    @Test
    void refusesToDeleteOutsideTheUploadFolder() throws IOException {
        Path outside = Files.writeString(tempDir.resolve("keep.txt"), "important");

        service().delete("/uploads/stock-images/../../keep.txt");

        assertThat(Files.exists(outside)).isTrue();
    }

    @Test
    void ownsOnlyRelativeReferences() {
        LocalStorageService service = service();

        assertThat(service.owns("/uploads/stock-images/stock/a.png")).isTrue();
        assertThat(service.owns("https://abc.supabase.co/storage/v1/object/public/b/a.png")).isFalse();
        assertThat(service.owns("")).isFalse();
    }
}
