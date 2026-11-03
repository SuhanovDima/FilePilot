package io.github.filepilot.execution;

import io.github.filepilot.config.*;
import io.github.filepilot.scan.ScanService;
import io.github.filepilot.workspace.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

@Component
public class NioFileSystem implements FileSystemPort {
    private final WorkspacePolicy policy;
    public NioFileSystem(WorkspacePolicy policy) { this.policy=policy; }
    @Override public Evidence inspect(Workspace w, String rel, String expected) throws IOException {
        Path p=policy.resolve(w,rel); var a=attributes(p);
        PilotException.require(a.isRegularFile() && !a.isOther() && !a.isSymbolicLink(), "UNSAFE_SOURCE");
        String fingerprint=ScanService.fingerprint(a);
        if (expected != null) PilotException.require(fingerprint.equals(expected), "STALE_PLAN");
        PilotException.require(a.size() <= PilotSettings.HASH_LIMIT, "RESOURCE_LIMIT");
        String digest=hash(p, PilotSettings.HASH_LIMIT);
        PilotException.require(ScanService.fingerprint(attributes(policy.resolve(w,rel))).equals(fingerprint), "STALE_PLAN");
        return new Evidence(fingerprint,digest);
    }
    @Override public boolean exists(Workspace w, String rel) throws IOException { return Files.exists(policy.resolve(w,rel),NOFOLLOW_LINKS); }
    @Override public boolean matches(Workspace w, String rel, Evidence evidence) throws IOException {
        return inspect(w,rel,evidence.fingerprint()).equals(evidence);
    }
    @Override public void move(Workspace w, String source, String destination, Evidence evidence) throws IOException {
        Path src=policy.resolve(w,source), dst=policy.resolve(w,destination);
        PilotException.require(!Files.exists(dst,NOFOLLOW_LINKS), "CONFLICT");
        Path parent=dst.getParent();
        // Only a deterministic category directory can be newly created. Undo parents must already exist.
        if (!Files.exists(parent,NOFOLLOW_LINKS)) {
            PilotException.require(parent.getParent().equals(policy.root(w)), "MISSING_PARENT");
            try { Files.createDirectory(parent); } catch (FileAlreadyExistsException e) { policy.checkComponents(parent); }
        }
        src=policy.resolve(w,source); dst=policy.resolve(w,destination);
        PilotException.require(Files.isDirectory(dst.getParent(),NOFOLLOW_LINKS) && Files.isWritable(dst.getParent())
            && Files.isWritable(src.getParent()) && Files.isReadable(src), "ACCESS_DENIED");
        PilotException.require(Files.getFileStore(src).equals(Files.getFileStore(dst.getParent())), "UNSUPPORTED_CROSS_VOLUME");
        PilotException.require(matches(w,source,evidence), "STALE_PLAN");
        // Re-resolve immediately before the syscall. ATOMIC_MOVE permits provider-specific overwrite,
        // so the no-overwrite contract deliberately takes priority over atomic-rename preference.
        src=policy.resolve(w,source); dst=policy.resolve(w,destination);
        PilotException.require(!Files.exists(dst,NOFOLLOW_LINKS), "CONFLICT");
        Files.move(src,dst);
    }
    private BasicFileAttributes attributes(Path p) throws IOException { return Files.readAttributes(p,BasicFileAttributes.class,NOFOLLOW_LINKS); }
    public static String hash(Path path, long limit) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try (SeekableByteChannel channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,NOFOLLOW_LINKS))) {
                ByteBuffer buffer=ByteBuffer.allocate(64*1024); long count=0;
                while (channel.read(buffer) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new PilotException("CANCELLED");
                    count+=buffer.position(); PilotException.require(count<=limit,"RESOURCE_LIMIT");
                    buffer.flip(); digest.update(buffer); buffer.clear();
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
