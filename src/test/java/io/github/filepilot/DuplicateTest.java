package io.github.filepilot;

import io.github.filepilot.scan.DuplicateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class DuplicateTest {
    @TempDir Path temp;
    @Test void hashesEqualSizeCandidatesAndKeepsAllFiles() throws Exception {
        try (var f=new TestFixture(temp)) {
            Files.writeString(f.inbox.resolve("a.txt"),"same"); Files.writeString(f.inbox.resolve("b.txt"),"same");
            Files.writeString(f.inbox.resolve("c.txt"),"diff"); Files.writeString(f.inbox.resolve("unique.txt"),"different size");
            var scan=f.scans.scan(f.allow().id()); var service=new DuplicateService(f.scans,f.workspaces,f.policy);
            var result=service.compute(scan.id());
            assertThat(result.status()).isEqualTo("COMPLETE"); assertThat(result.hashedBytes()).isEqualTo(12);
            assertThat(result.groups()).hasSize(1); assertThat(result.groups().getFirst().status()).isEqualTo("VERIFIED");
            assertThat(result.groups().getFirst().fileIds()).hasSize(2);
            try (var files=Files.list(f.inbox)) { assertThat(files.count()).isEqualTo(4); }
        }
    }
}
