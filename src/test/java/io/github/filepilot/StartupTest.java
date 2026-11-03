package io.github.filepilot;

import io.github.filepilot.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import static org.assertj.core.api.Assertions.assertThat;

class StartupTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void startsWithoutModel() {
        // Spring Test 7's Jupiter extension requires JUnit 6. Keep the requested
        // JUnit 5 engine and start the real Boot context directly instead.
        try (var context = SpringApplication.run(FilePilotApplication.class, "--server.port=0", "--filepilot.data-dir=" + temp,
            "--spring.ai.model.chat=openai", "--spring.ai.model.embedding=openai")) {
            assertThat(context.getBean(HealthController.class).health())
                .containsEntry("appStatus", "UP").containsEntry("llmStatus", "OFFLINE");
            assertThat(context.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class)).isEmpty();
        }
    }
    @Test void enabledButUnavailableLocalModelStillStarts() {
        try (var context=SpringApplication.run(FilePilotApplication.class,"--server.port=0","--filepilot.data-dir="+temp,
            "--filepilot.llm.enabled=true","--filepilot.llm.base-url=http://127.0.0.1:1/v1","--filepilot.llm.timeout-ms=100")) {
            var classifier=context.getBean(io.github.filepilot.classification.ClassifierService.class);
            assertThat(classifier.enrich(java.util.List.of(ClassifierTest.file()),"organize by type",true).status()).startsWith("RULES_FALLBACK");
            assertThat(context.getBean(HealthController.class).health()).containsEntry("appStatus","UP").containsEntry("llmStatus","OFFLINE");
        }
    }
}
