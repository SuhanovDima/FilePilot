package io.github.filepilot.execution;

import io.github.filepilot.workspace.Workspace;
import java.io.IOException;

public interface FileSystemPort {
    record Evidence(String fingerprint, String sha256) {}
    Evidence inspect(Workspace workspace, String relative, String expectedFingerprint) throws IOException;
    void move(Workspace workspace, String sourceRelative, String destinationRelative, Evidence evidence) throws IOException;
    boolean matches(Workspace workspace, String relative, Evidence evidence) throws IOException;
    boolean exists(Workspace workspace, String relative) throws IOException;
}
