package io.github.filepilot.config;

import java.nio.file.Path;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PilotSettings {
    public final Path dataDir;
    public final int maxFiles, maxDepth;
    public static final long HASH_LIMIT = 64L * 1024 * 1024;
    public PilotSettings(Environment env) {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String base = os.contains("win") ? System.getenv("LOCALAPPDATA") : null;
        Path defaultDir = base != null ? Path.of(base, "FilePilot") : os.contains("mac")
            ? Path.of(System.getProperty("user.home"), "Library", "Application Support", "FilePilot")
            : Path.of(System.getProperty("user.home"), ".local", "share", "filepilot");
        dataDir = Path.of(env.getProperty("filepilot.data-dir", defaultDir.toString())).toAbsolutePath().normalize();
        maxFiles = Integer.parseInt(env.getProperty("filepilot.scan.max-files", "2000"));
        maxDepth = Integer.parseInt(env.getProperty("filepilot.scan.max-depth", "4"));
        PilotException.require(maxFiles > 0 && maxFiles <= 10000 && maxDepth >= 1 && maxDepth <= 16, "INVALID_CONFIG");
    }
}
