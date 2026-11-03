package io.github.filepilot.health;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health") public Map<String, String> health() {
        return Map.of("appStatus", "UP", "llmStatus", "OFFLINE", "model", "rules");
    }
}
