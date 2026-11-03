package io.github.filepilot.health;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    private final io.github.filepilot.classification.ClassifierService classifiers;
    public HealthController(io.github.filepilot.classification.ClassifierService classifiers) { this.classifiers=classifiers; }
    @GetMapping("/api/v1/health") public Map<String, String> health() {
        return Map.of("appStatus", "UP", "llmStatus", classifiers.status(), "model", classifiers.modelName());
    }
}
