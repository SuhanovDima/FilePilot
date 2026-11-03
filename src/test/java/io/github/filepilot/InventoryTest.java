package io.github.filepilot;

import io.github.filepilot.classification.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class InventoryTest {
    @TempDir Path temp;
    @Test void rules() {
        var r = new RuleClassifier();
        assertThat(r.category("X.JPEG")).isEqualTo(FileCategory.IMAGES);
        assertThat(r.category("x.png")).isEqualTo(FileCategory.IMAGES);
        assertThat(r.category("report.pdf")).isEqualTo(FileCategory.DOCUMENTS);
        assertThat(r.category("договор.pdf")).isEqualTo(FileCategory.CONTRACTS);
        assertThat(r.category("unknown.bin")).isEqualTo(FileCategory.OTHER);
    }
    @Test void allowlistAndReadOnlyScanPersistAcrossRestart() throws Exception {
        UUID id, scanId;
        try (var f = new TestFixture(temp)) {
            assertThat(f.workspaces.list()).isEmpty();
            assertThatThrownBy(() -> f.scans.scan(UUID.randomUUID())).hasMessage("NOT_FOUND");
            assertThatThrownBy(() -> f.workspaces.add(f.inbox.toString(), false)).hasMessage("CONFIRMATION_REQUIRED");
            Files.writeString(f.inbox.resolve("договор.pdf"), "fixture");
            Files.writeString(f.inbox.resolve(".secret.txt"), "hidden fixture");
            var w = f.allow(); id = w.id();
            var s = f.scans.scan(id); scanId = s.id();
            assertThat(s.status()).isEqualTo("COMPLETE");
            assertThat(s.files()).hasSize(1);
            assertThat(s.files().getFirst().category()).isEqualTo(FileCategory.CONTRACTS);
            assertThat(Files.readString(f.inbox.resolve("договор.pdf"))).isEqualTo("fixture");
        }
        try (var f = new TestFixture(temp)) { assertThat(f.scans.get(scanId).workspaceId()).isEqualTo(id); }
    }
    @Test void pathsDenyEscapesReservedAndSymlinks() throws Exception {
        try (var f = new TestFixture(temp)) {
            var w = f.allow();
            for (String p : new String[]{"../outside", "/etc/passwd", "C:/Windows/a", "a\\b", "NUL.txt", "a/CON", "a/../b", "x\0.txt", "file.", "a//b"})
                assertThatThrownBy(() -> f.policy.resolve(w,p)).isInstanceOf(RuntimeException.class);
            Path outside = Files.createDirectory(temp.resolve("outside"));
            try { Files.createSymbolicLink(f.inbox.resolve("link"), outside); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException e) { Assumptions.abort("Symlink privileges unavailable"); }
            Files.writeString(outside.resolve("private.txt"), "fixture");
            assertThatThrownBy(() -> f.policy.resolve(w,"link/private.txt")).hasMessageContaining("DENIED");
            assertThat(f.scans.scan(w.id()).files()).isEmpty();
        }
    }
    @Test void reportsFileAndDepthLimits() throws Exception {
        try (var f = new TestFixture(temp,1,1)) {
            Files.writeString(f.inbox.resolve("a.txt"), "a"); Files.writeString(f.inbox.resolve("b.txt"), "b");
            Files.createDirectories(f.inbox.resolve("deep/nested"));
            assertThat(f.scans.scan(f.allow().id()).status()).isEqualTo("PARTIAL_SCAN");
        }
    }
    @Test void revokeStopsReadAccessAndDataDirectoryCannotBeScanned() throws Exception {
        try (var f = new TestFixture(temp)) {
            assertThatThrownBy(() -> f.workspaces.add(temp.toRealPath().toString(), true)).hasMessage("APP_DATA_OVERLAP");
            var w = f.allow(); f.workspaces.revoke(w.id());
            assertThatThrownBy(() -> f.scans.scan(w.id())).hasMessage("UNAUTHORIZED_ROOT");
        }
    }
}
