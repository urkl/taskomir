package net.urosk.taskomir.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test za TaskomirSchedulingConfig.
 *
 * This test class verifies that TaskomirSchedulingConfig is only active
 * when the property taskomir.primary is set to true.
 *
 * - When taskomir.primary=true, the configuration should be loaded (bean exists).
 * - When taskomir.primary=false, the configuration should not be loaded (bean does not exist).
 */
class TaskomirSchedulingConfigTest {

    // Ustvarimo ApplicationContextRunner, ki uporablja našo konfiguracijo
    // Create an ApplicationContextRunner with our configuration class.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TaskomirSchedulingConfig.class);

    /**
     * Test, ko je taskomir.primary=true.
     *
     * Expect that the TaskomirSchedulingConfig bean is present in the context.
     */
    @Test
    void whenPrimaryIsTrue_thenSchedulingConfigLoaded() {
        contextRunner.withPropertyValues(
                "taskomir.primary=true"
        ).run(context -> {
            // Preverimo, da je bean TaskomirSchedulingConfig naložen.
            // Verify that TaskomirSchedulingConfig bean is loaded.
            assertThat(context).hasSingleBean(TaskomirSchedulingConfig.class);
        });
    }

    /**
     * Test, ko je taskomir.primary=false.
     *
     * Expect that the TaskomirSchedulingConfig bean is not present in the context.
     */
    @Test
    void whenPrimaryIsFalse_thenSchedulingConfigNotLoaded() {
        contextRunner.withPropertyValues(
                "taskomir.primary=false"
        ).run(context -> {
            // Preverimo, da bean TaskomirSchedulingConfig ni naložen.
            // Verify that TaskomirSchedulingConfig bean is not loaded.
            assertThat(context).doesNotHaveBean(TaskomirSchedulingConfig.class);
        });
    }
}
