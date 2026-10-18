package com.mess.app.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageObjectPathsTest {

    @Test
    void acceptsNormalRelativePaths() {
        assertThat(StorageObjectPaths.requireSafe("stock/tomato_1700000000000.webp")).isEqualTo("stock/tomato_1700000000000.webp");
        assertThat(StorageObjectPaths.isSafe("receipts/bill-1.pdf")).isTrue();
    }

    @Test
    void rejectsPathTraversalAndOddPaths() {
        for (String bad : new String[]{
                "../secret.txt", "stock/../../etc/passwd", "/absolute/file.png", "stock//file.png",
                "stock\\file.png", "stock/.hidden", "..", ".", "", "  ", "a/b/", "stock/fi le.png", "stock/%2e%2e/x"}) {
            assertThat(StorageObjectPaths.isSafe(bad)).as("path '%s'", bad).isFalse();
        }
        assertThatThrownBy(() -> StorageObjectPaths.requireSafe("../x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StorageObjectPaths.isSafe(null)).isFalse();
    }

    @Test
    void sanitizeSegmentRemovesUnsafeCharactersAndLeadingDots() {
        assertThat(StorageObjectPaths.sanitizeSegment("../../Evil Name.png", 40, "x")).doesNotContain("/").doesNotContain("..");
        assertThat(StorageObjectPaths.sanitizeSegment("Chicken Curry (1kg)", 40, "x")).isEqualTo("Chicken_Curry__1kg_");
        assertThat(StorageObjectPaths.sanitizeSegment("...", 40, "fallback")).isEqualTo("fallback");
        assertThat(StorageObjectPaths.sanitizeSegment(null, 40, "fallback")).isEqualTo("fallback");
        assertThat(StorageObjectPaths.sanitizeSegment("abcdefghij", 4, "x")).isEqualTo("abcd");
        assertThat(StorageObjectPaths.isSafe("stock/" + StorageObjectPaths.sanitizeSegment("../../x y", 40, "item") + "_1.png")).isTrue();
    }
}
