package io.github.filepilot;

import io.github.filepilot.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import static org.assertj.core.api.Assertions.assertThat;

class StartupTest {
    @Test void startsWithoutModel() {
        // Spring Test 7's Jupiter extension requires JUnit 6. Keep the requested
        // JUnit 5 engine and start the real Boot context directly instead.
        try (var context = SpringApplication.run(FilePilotApplication.class, "--server.port=0")) {
            assertThat(context.getBean(HealthController.class).health())
                .containsEntry("appStatus", "UP").containsEntry("llmStatus", "OFFLINE");
        }
    }
}
