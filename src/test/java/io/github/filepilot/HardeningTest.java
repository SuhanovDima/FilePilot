package io.github.filepilot;

import io.github.filepilot.history.JournalEntry;
import io.github.filepilot.execution.NioFileSystem;
import io.github.filepilot.planning.Plan;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HardeningTest {
    @TempDir Path temp;
    private Plan plan(TestFixture f) throws Exception { return f.plans.create(f.scans.scan(f.allow().id()).id(),"organize by type",false); }
    @Test void partialBatchRetainsSuccessfulJournalAndUndo() throws Exception {
        try (var f=new TestFixture(temp)) {
            Files.writeString(f.inbox.resolve("a.txt"),"first"); Files.writeString(f.inbox.resolve("b.jpg"),"second");
            var p=plan(f); Files.writeString(f.inbox.resolve("b.jpg"),"changed");
            assertThat(f.executions.execute(p.id(),true).status()).isEqualTo("PARTIAL_FAILED");
            assertThat(f.executions.history(p.workspaceId())).extracting(JournalEntry::status).containsExactly("DONE","FAILED");
            f.executions.undo(p.id(),true);
            assertThat(Files.readString(f.inbox.resolve("a.txt"))).isEqualTo("first");
            assertThat(Files.readString(f.inbox.resolve("b.jpg"))).isEqualTo("changed");
        }
    }
    @Test void rootReplacementAndRevocationInvalidatePendingPlans() throws Exception {
        try (var f=new TestFixture(temp)) {
            Files.writeString(f.inbox.resolve("a.txt"),"first"); var p=plan(f);
            Files.move(f.inbox,f.inbox.resolveSibling("old-inbox")); Files.createDirectory(f.inbox);
            Files.writeString(f.inbox.resolve("a.txt"),"replacement");
            assertThat(f.executions.execute(p.id(),true).status()).isEqualTo("PARTIAL_FAILED");
            assertThat(Files.readString(f.inbox.resolve("a.txt"))).isEqualTo("replacement");
            f.workspaces.revoke(p.workspaceId());
            assertThatThrownBy(() -> f.plans.get(p.id())).hasMessage("UNAUTHORIZED_ROOT");
        }
    }
    @Test void largeFilesAndTooManyActionsRefusedWithoutMove() throws Exception {
        try (var f=new TestFixture(temp)) {
            try (var file=FileChannel.open(f.inbox.resolve("large.bin"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                file.position(64L*1024*1024); file.write(ByteBuffer.wrap(new byte[]{1}));
            }
            var p=plan(f); assertThat(f.executions.execute(p.id(),true).actions().getFirst().status()).isEqualTo("RESOURCE_LIMIT");
            assertThat(Files.exists(f.inbox.resolve("large.bin"))).isTrue();
            for (int i=0;i<201;i++) Files.writeString(f.inbox.resolve("small"+i+".txt"),"x");
            var next=plan(f);
            assertThatThrownBy(() -> f.executions.execute(next.id(),true)).hasMessage("BATCH_RESOURCE_LIMIT");
            assertThat(f.plans.get(next.id()).status()).isEqualTo("DRAFT");
        }
    }
    @Test void pendingBeforeSyscallIsReconciledAsNotMoved() throws Exception {
        try (var f=new TestFixture(temp)) {
            Files.writeString(f.inbox.resolve("a.txt"),"first"); var p=plan(f); var a=p.actions().getFirst(); var w=f.workspaces.get(p.workspaceId());
            var pending=new JournalEntry(UUID.randomUUID(),p.id(),a.id(),w.id(),"MOVE",a.sourceRelative(),a.destinationRelative(),f.fs.inspect(w,a.sourceRelative(),a.expectedFingerprint()),"PENDING",null,null,Instant.now(),null);
            f.repo.put("operation_journal",pending.id(),pending); f.plans.save(p.status("EXECUTING"));
            f.executions.reconcile(); f.executions.execute(p.id(),true);
            assertThat(f.executions.history(w.id())).hasSize(1).allMatch(j -> j.status().equals("FAILED") && j.errorCode().equals("MOVE_NOT_OBSERVED"));
            assertThat(Files.exists(f.inbox.resolve("a.txt"))).isTrue();
        }
    }
    @Test void hashesCanBeCancelledAndCaseCollisionsAreConservative() throws Exception {
        try (var f=new TestFixture(temp)) {
            Path p=Files.writeString(f.inbox.resolve("a.txt"),"cancel fixture");
            assertThatThrownBy(() -> NioFileSystem.hash(p,100,() -> true)).hasMessage("CANCELLED");
            Files.createDirectory(f.inbox.resolve("one")); Files.createDirectory(f.inbox.resolve("two"));
            Files.writeString(f.inbox.resolve("one/A.JPG"),"1"); Files.writeString(f.inbox.resolve("two/a.jpg"),"2");
            assertThat(plan(f).actions().stream().filter(a -> a.status().equals("CONFLICT"))).hasSize(1);
        }
    }
}
