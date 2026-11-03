package io.github.filepilot.commands;

import io.github.filepilot.config.PilotException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CommandInterpreter {
    public enum Intent { ORGANIZE, DUPLICATES }
    public Intent parse(String command) {
        PilotException.require(command != null && command.length() <= 500, "INVALID_COMMAND");
        String c = command.strip().toLowerCase(Locale.ROOT);
        // A deliberately closed grammar. Instructions cannot change authorization or execution rules.
        return switch (c) {
            case "разложи по типам", "разложить по типам", "organize by type", "отдельно договоры",
                 "разложи фотографии, архивы и документы по папкам; договоры выдели отдельно; дубликаты покажи, но не удаляй" -> Intent.ORGANIZE;
            case "показать дубликаты", "покажи дубликаты", "show duplicates" -> Intent.DUPLICATES;
            default -> throw new PilotException("INVALID_COMMAND");
        };
    }
}
