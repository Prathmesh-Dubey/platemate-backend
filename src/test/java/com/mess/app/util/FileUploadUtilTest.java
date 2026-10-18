package com.mess.app.util;

import com.mess.app.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileUploadUtilTest {

    private StorageService storage;
    private FileUploadUtil util;

    @BeforeEach
    void setUp() throws IOException {
        storage = mock(StorageService.class);
        when(storage.upload(anyString(), any(), anyString())).thenAnswer(i -> "/uploads/stock-images/" + i.getArgument(0));
        util = new FileUploadUtil(storage);
        ReflectionTestUtils.setField(util, "profileImageMaxSizeMb", 5L);
        ReflectionTestUtils.setField(util, "generalMaxSizeMb", 10L);
    }

    private MockMultipartFile file(String name, String type, int size) {
        return new MockMultipartFile("file", name, type, new byte[size]);
    }

    @Test
    void stockImagesGoToTheStorageUnderStockWithTheSameReferenceShape() throws IOException {
        String reference = util.uploadStockImage(file("tomato.webp", "image/webp", 100), "Tomato Red_0");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(path.capture(), any(), org.mockito.ArgumentMatchers.eq("image/webp"));
        assertThat(path.getValue()).matches("stock/Tomato_Red_0_\\d+\\.webp");
        assertThat(reference).isEqualTo("/uploads/stock-images/" + path.getValue());
    }

    @Test
    void stockImageNamesCannotEscapeTheFolder() throws IOException {
        util.uploadStockImage(file("a.png", "image/png", 10), "../../etc/passwd");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(path.capture(), any(), anyString());
        assertThat(path.getValue()).startsWith("stock/").doesNotContain("..").doesNotContain("//");
    }

    @Test
    void receiptsAcceptPdfAndImagesAndSanitizeTheFileName() throws IOException {
        util.uploadExpenseReceipt(file("../../My Bill (final).pdf", "application/pdf", 100));

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(path.capture(), any(), org.mockito.ArgumentMatchers.eq("application/pdf"));
        assertThat(path.getValue()).matches("receipts/[A-Za-z0-9_.-]+_\\d+\\.pdf").doesNotContain("..");
    }

    @Test
    void dangerousFileTypesAreRejectedAndNothingIsStored() throws IOException {
        assertThatThrownBy(() -> util.uploadExpenseReceipt(file("virus.exe", "application/octet-stream", 10)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> util.uploadStockImage(file("page.html", "text/html", 10), "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported image type");
        assertThatThrownBy(() -> util.uploadProfileImage(file("doc.pdf", "application/pdf", 10), "user", "1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).upload(anyString(), any(), anyString());
    }

    @Test
    void aSafeExtensionIsDerivedFromTheContentTypeWhenTheNameHasNone() throws IOException {
        util.uploadStockImage(file("blob", "image/png", 10), "x");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(path.capture(), any(), org.mockito.ArgumentMatchers.eq("image/png"));
        assertThat(path.getValue()).endsWith(".png");
    }

    @Test
    void sizeLimitsApplyAndKeepTheOldMessages() {
        assertThatThrownBy(() -> util.uploadProfileImage(file("a.png", "image/png", 6 * 1024 * 1024), "user", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Image is too large. Maximum size is 5 MB");
        assertThatThrownBy(() -> util.uploadExpenseReceipt(file("a.pdf", "application/pdf", 11 * 1024 * 1024)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File is too large. Maximum size is 10 MB");
        assertThatThrownBy(() -> util.validateImageFile(file("a.png", "image/png", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Image file is required");
    }

    @Test
    void profileImagesKeepTheirNamingAndNormaliseJpeg() throws IOException {
        util.uploadProfileImage(file("me.jpeg", "image/jpeg", 10), "candidate", "CAD 01");

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).upload(path.capture(), any(), org.mockito.ArgumentMatchers.eq("image/jpeg"));
        assertThat(path.getValue()).matches("profiles/candidate_CAD_01_\\d+\\.jpg");
    }

    @Test
    void deleteQuietlyNeverThrowsEvenWhenTheStorageFails() throws IOException {
        org.mockito.Mockito.doThrow(new IOException("boom")).when(storage).delete("/uploads/stock-images/stock/a.png");

        util.deleteStoredImageQuietly("/uploads/stock-images/stock/a.png");   // must not throw
        util.deleteStoredImageQuietly(null);
        util.deleteStoredImageQuietly("");
    }

    @Test
    void storageFailuresDuringUploadAreSurfacedToTheCaller() throws IOException {
        when(storage.upload(anyString(), any(), anyString())).thenThrow(new IOException("Supabase Storage upload failed (HTTP 403)"));

        assertThatThrownBy(() -> util.uploadStockImage(file("a.png", "image/png", 10), "x"))
                .isInstanceOf(IOException.class).hasMessageContaining("HTTP 403");
    }

    @Test
    void imageReferenceValidationIsUnchanged() {
        assertThat(FileUploadUtil.isValidImageReference("/uploads/stock-images/stock/a.png")).isTrue();
        assertThat(FileUploadUtil.isValidImageReference("https://cdn.example.com/a.png")).isTrue();
        assertThat(FileUploadUtil.isValidImageReference("blob:http://x/1")).isFalse();
        assertThat(FileUploadUtil.isValidImageReference("data:image/png;base64,AAA")).isFalse();
        assertThat(FileUploadUtil.isValidImageReference("javascript:alert(1)")).isFalse();
        assertThat(FileUploadUtil.isValidImageReference(null)).isFalse();
    }
}
