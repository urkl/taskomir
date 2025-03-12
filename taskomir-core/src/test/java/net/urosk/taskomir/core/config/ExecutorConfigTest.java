package net.urosk.taskomir.core.config;

import net.urosk.taskomir.core.config.ExecutorConfig;
import net.urosk.taskomir.core.config.TaskomirProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test za konfiguracijo ExecutorConfig.
 * Test for the ExecutorConfig.
 *
 * Ta razred uporablja ApplicationContextRunner, da ustvari minimalen Spring kontekst z določenimi lastnostmi
 * in preveri, ali se bean ThreadPoolExecutor ustvari ali ne.
 *
 * This class uses ApplicationContextRunner to create a minimal Spring context with specific properties
 * and verify whether the ThreadPoolExecutor bean is created or not.
 */
class ExecutorConfigTest {

    // Ustvarimo ApplicationContextRunner z našo konfiguracijo.
    // Create an ApplicationContextRunner with our configuration.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ExecutorConfig.class, TaskomirProperties.class);

    /**
     * Test, ko je taskomir.primary nastavljen na true.
     *
     * Test when taskomir.primary is set to true.
     *
     * Pričakujemo, da se bo bean ThreadPoolExecutor ustvaril.
     * We expect that the ThreadPoolExecutor bean will be created.
     */
    @Test
    void whenPrimaryIsTrue_thenExecutorBeanCreated() {
        contextRunner.withPropertyValues(
                "taskomir.primary=true",
                "taskomir.poolSize=2",
                "taskomir.queueCapacity=1000"
        ).run(context -> {
            // Preverimo, da v kontekstu obstaja točno en bean tipa ThreadPoolExecutor.
            // Verify that the context contains a single bean of type ThreadPoolExecutor.
            assertThat(context).hasSingleBean(ThreadPoolExecutor.class);
            // Dodatni komentar: Ta test potrjuje, da se na primarni instanci ustvari ThreadPoolExecutor.
            // Additional note: This test confirms that on the primary instance, the ThreadPoolExecutor is created.
        });
    }

    /**
     * Test, ko je taskomir.primary nastavljen na false.
     *
     * Test when taskomir.primary is set to false.
     *
     * Pričakujemo, da bean ThreadPoolExecutor NI ustvarjen.
     * We expect that the ThreadPoolExecutor bean is NOT created.
     */
    @Test
    void whenPrimaryIsFalse_thenExecutorBeanNotCreated() {
        contextRunner.withPropertyValues(
                "taskomir.primary=false",
                "taskomir.poolSize=2",
                "taskomir.queueCapacity=1000"
        ).run(context -> {
            // Preverimo, da v kontekstu ni bean-a tipa ThreadPoolExecutor.
            // Verify that the context does not contain a bean of type ThreadPoolExecutor.
            assertThat(context).doesNotHaveBean(ThreadPoolExecutor.class);
            // Dodatni komentar: Ta test zagotavlja, da na sekundarni instanci (kjer primary=false) ne bo izvajanja urnikovanih opravil.
            // Additional note: This test ensures that on a secondary instance (where primary=false), no executor is created.
        });
    }
}
