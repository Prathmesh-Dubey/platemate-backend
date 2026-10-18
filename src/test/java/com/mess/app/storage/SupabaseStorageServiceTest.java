package com.mess.app.storage;

import com.mess.app.testsupport.StubHttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseStorageServiceTest {

    private static final String KEY = "service-role-key-for-tests";
    private static final String PREFIX = "/uploads/stock-images/";

    private SupabaseStorageService service(StubHttpServer stub, boolean publicBucket) {
        return new SupabaseStorageService(stub.baseUrl(), KEY, "my-bucket", publicBucket, PREFIX, 3600);
    }

    @Test
    void uploadSendsTheFileToTheBucketWithTheServiceKeyAndReturnsAStableReference() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /storage/v1/object/my-bucket/", 200, "{\"Key\":\"my-bucket/stock/tomato_1.webp\"}");

            String reference = service(stub, false).upload("stock/tomato_1.webp", new byte[]{1, 2, 3}, "image/webp");

            assertThat(reference).isEqualTo("/uploads/stock-images/stock/tomato_1.webp");
            StubHttpServer.Received request = stub.lastRequest();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/storage/v1/object/my-bucket/stock/tomato_1.webp");
            assertThat(request.header("authorization")).isEqualTo("Bearer " + KEY);
            assertThat(request.header("apikey")).isEqualTo(KEY);
            assertThat(request.header("content-type")).isEqualTo("image/webp");
        }
    }

    @Test
    void aFailedUploadIsReportedAndNeverFallsBackToLocalDisk() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /storage/v1/object/", 404, "{\"message\":\"Bucket not found\"}");

            assertThatThrownBy(() -> service(stub, false).upload("stock/a.png", new byte[]{1}, "image/png"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("HTTP 404")
                    .hasMessageContaining("bucket exists")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY));
        }
    }

    @Test
    void authorizationFailuresPointAtTheKeyWithoutRevealingIt() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /storage/v1/object/", 403, "{\"message\":\"Unauthorized\"}");

            assertThatThrownBy(() -> service(stub, false).upload("stock/a.png", new byte[]{1}, "image/png"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("SUPABASE_STORAGE_API_KEY")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY));
        }
    }

    @Test
    void unsafePathsNeverReachTheNetwork() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            SupabaseStorageService service = service(stub, false);

            assertThatThrownBy(() -> service.upload("../x.png", new byte[]{1}, "image/png"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(stub.requests()).isEmpty();
        }
    }

    @Test
    void privateBucketsAreServedThroughShortLivedSignedUrlsThatAreCached() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("POST /storage/v1/object/sign/my-bucket/", 200,
                    "{\"signedURL\":\"/object/sign/my-bucket/stock/a.png?token=abc\"}");
            SupabaseStorageService service = service(stub, false);

            String first = service.resolveUrl("stock/a.png");
            String second = service.resolveUrl("stock/a.png");

            assertThat(first).isEqualTo(stub.baseUrl() + "/storage/v1/object/sign/my-bucket/stock/a.png?token=abc");
            assertThat(second).isEqualTo(first);
            assertThat(stub.requests()).hasSize(1);   // second call served from the cache
            assertThat(stub.lastRequest().body()).contains("\"expiresIn\":3600");
            assertThat(stub.lastRequest().header("authorization")).isEqualTo("Bearer " + KEY);
        }
    }

    @Test
    void publicBucketsUseThePublicUrlWithoutAnyRequest() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            String url = service(stub, true).resolveUrl("stock/a.png");

            assertThat(url).isEqualTo(stub.baseUrl() + "/storage/v1/object/public/my-bucket/stock/a.png");
            assertThat(stub.requests()).isEmpty();
        }
    }

    @Test
    void deleteRemovesTheObjectAndTreatsAMissingObjectAsDone() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("DELETE /storage/v1/object/my-bucket/stock/a.png", 200, "{\"message\":\"Successfully deleted\"}");
            stub.reply("DELETE /storage/v1/object/my-bucket/stock/gone.png", 404, "{\"message\":\"not found\"}");
            SupabaseStorageService service = service(stub, false);

            service.delete("/uploads/stock-images/stock/a.png");
            service.delete("/uploads/stock-images/stock/gone.png");

            assertThat(stub.requests()).hasSize(2);
            assertThat(stub.requests().get(0).method()).isEqualTo("DELETE");
        }
    }

    @Test
    void ignoresReferencesItDoesNotOwnAndRecognisesLegacyPublicUrls() throws Exception {
        try (StubHttpServer stub = new StubHttpServer()) {
            stub.reply("DELETE /storage/v1/object/my-bucket/stock/old.png", 200, "{}");
            SupabaseStorageService service = service(stub, false);

            service.delete("https://other-site.example.com/img.png");
            service.delete("/uploads/stock-images/../secret");
            assertThat(stub.requests()).isEmpty();
            assertThat(service.owns("https://x.supabase.co/storage/v1/object/public/my-bucket/stock/old.png")).isTrue();
            assertThat(service.owns("https://x.supabase.co/storage/v1/object/public/another-bucket/stock/old.png")).isFalse();

            service.delete("https://x.supabase.co/storage/v1/object/public/my-bucket/stock/old.png");
            assertThat(stub.requests()).hasSize(1);
        }
    }

    @Test
    void missingConfigurationFailsClearlyAtStartup() {
        assertThatThrownBy(() -> new SupabaseStorageService("", KEY, "b", false, PREFIX, 3600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_URL")
                .hasMessageContaining("SUPABASE_STORAGE_API_KEY")
                .hasMessageContaining("SUPABASE_STORAGE_BUCKET_NAME");
        assertThatThrownBy(() -> new SupabaseStorageService("https://x.supabase.co", "", "b", false, PREFIX, 3600))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SupabaseStorageService("https://x.supabase.co", KEY, " ", false, PREFIX, 3600))
                .isInstanceOf(IllegalStateException.class);
    }
}
