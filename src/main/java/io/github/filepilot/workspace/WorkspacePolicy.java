package io.github.filepilot.workspace;

import io.github.filepilot.config.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import org.springframework.stereotype.Component;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

@Component
public class WorkspacePolicy {
    private final PilotSettings settings;
    public WorkspacePolicy(PilotSettings settings) { this.settings = settings; }

    public Path authorizeRoot(String input) throws IOException {
        PilotException.require(input != null && input.length() < 4096 && input.indexOf('\0') < 0, "INVALID_PATH");
        Path root = Path.of(input);
        PilotException.require(root.isAbsolute() && !root.toString().startsWith("\\\\"), "INVALID_PATH");
        root = root.normalize();
        checkComponents(root);
        PilotException.require(Files.isDirectory(root, NOFOLLOW_LINKS) && Files.isReadable(root) && Files.isWritable(root), "UNAUTHORIZED_ROOT");
        PilotException.require(root.getParent() != null && !root.equals(Path.of(System.getProperty("user.home")).toRealPath()), "UNAUTHORIZED_ROOT");
        PilotException.require(!root.startsWith(settings.dataDir) && !settings.dataDir.startsWith(root), "APP_DATA_OVERLAP");
        // Existing ordinary local directories only. Junctions/reparse points are rejected below.
        PilotException.require(root.equals(root.toRealPath(NOFOLLOW_LINKS)), "UNAUTHORIZED_ROOT");
        return root;
    }
    public Path root(Workspace w) throws IOException {
        PilotException.require(w.enabled(), "UNAUTHORIZED_ROOT");
        Path root = authorizeRoot(w.rootPath());
        PilotException.require(identity(root).equals(w.identity()), "ROOT_CHANGED");
        return root;
    }
    public String identity(Path p) throws IOException {
        var a = Files.readAttributes(p, BasicFileAttributes.class, NOFOLLOW_LINKS);
        return String.valueOf(a.fileKey()) + ":" + a.creationTime();
    }
    public Path resolve(Workspace w, String relative) throws IOException {
        validateRelative(relative);
        Path root = root(w), path = root.resolve(relative).normalize();
        PilotException.require(path.startsWith(root) && !path.equals(root), "PATH_ESCAPE");
        checkComponents(path);
        return path;
    }
    public void validateRelative(String input) {
        PilotException.require(input != null && !input.isBlank() && input.length() <= 2048 && !input.contains("\\") && !input.contains(":") && !input.startsWith("/"), "INVALID_PATH");
        for (String part : input.split("/", -1)) {
            String base = part.split("\\.", -1)[0].toUpperCase(Locale.ROOT);
            PilotException.require(!part.isBlank() && !part.equals(".") && !part.equals("..") && !part.endsWith(".") && !part.endsWith(" ")
                && part.chars().noneMatch(c -> c < 32 || "<>\"|?*".indexOf(c) >= 0)
                && !base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"), "INVALID_PATH");
        }
    }
    public void checkComponents(Path absolute) throws IOException {
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (!Files.exists(current, NOFOLLOW_LINKS)) continue;
            var a = Files.readAttributes(current, BasicFileAttributes.class, NOFOLLOW_LINKS);
            PilotException.require(!a.isSymbolicLink() && !a.isOther() && !Files.isSymbolicLink(current), "SYMLINK_DENIED");
            // On Windows a junction may be exposed as a directory by the provider.
            PilotException.require(current.toRealPath().equals(current.toRealPath(NOFOLLOW_LINKS)), "REPARSE_POINT_DENIED");
        }
    }
}
