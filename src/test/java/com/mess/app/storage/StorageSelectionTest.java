package com.mess.app.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks which StorageService the STORAGE_TYPE setting selects, without starting the whole application. */
class StorageSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalStorageService.class, SupabaseStorageService.class);

    @Test
    void localStorageIsTheDefaultForDevelopment() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(StorageService.class);
            assertThat(context).hasSingleBean(LocalStorageService.class);
            assertThat(context).doesNotHaveBean(SupabaseStorageService.class);
        });
    }

    @Test
    void supabaseIsSelectedExplicitlyAndLocalIsThenNotUsed() {
        runner.withPropertyValues(
                        "storage.type=supabase",
                        "supabase.url=https://example.supabase.co",
                        "supabase.storage.api-key=test-key",
                        "supabase.storage.bucket-name=uploads")
                .run(context -> {
                    assertThat(context).hasSingleBean(StorageService.class);
                    assertThat(context).hasSingleBean(SupabaseStorageService.class);
                    assertThat(context).doesNotHaveBean(LocalStorageService.class);
                });
    }

    @Test
    void supabaseWithMissingCredentialsStopsStartupInsteadOfFallingBackToLocalDisk() {
        runner.withPropertyValues("storage.type=supabase")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("SUPABASE_STORAGE_API_KEY");
                });
    }

    @Test
    void anUnknownStorageTypeDoesNotSilentlyPickAnImplementation() {
        runner.withPropertyValues("storage.type=s3")
                .run(context -> assertThat(context).doesNotHaveBean(StorageService.class));
    }
}
