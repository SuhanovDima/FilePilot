package io.github.filepilot.scan;

import io.github.filepilot.config.*;
import io.github.filepilot.execution.NioFileSystem;
import io.github.filepilot.workspace.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

@Service
public class DuplicateService {
    public record Group(String status,List<UUID> fileIds) { public Group { fileIds=List.copyOf(fileIds); } }
    public record Result(String status,long hashedBytes,List<Group> groups) { public Result { groups=List.copyOf(groups); } }
    private final ScanService scans;
    private final WorkspaceService workspaces;
    private final WorkspacePolicy policy;
    private final ConcurrentMap<UUID,AtomicBoolean> running=new ConcurrentHashMap<>();
    private final Map<UUID,Result> cache=Collections.synchronizedMap(new LinkedHashMap<>());
    public DuplicateService(ScanService scans,WorkspaceService workspaces,WorkspacePolicy policy) { this.scans=scans; this.workspaces=workspaces; this.policy=policy; }
    public void cancel(UUID id) { scans.get(id); var flag=running.get(id); if (flag!=null) flag.set(true); }
    public Result get(UUID id) { scans.get(id); return cache.get(id); }
    public Result compute(UUID id) throws IOException {
        var scan=scans.get(id); var w=workspaces.get(scan.workspaceId()); var cancelled=new AtomicBoolean();
        PilotException.require(running.putIfAbsent(id,cancelled)==null,"RESOURCE_BUSY");
        long budget=128L*1024*1024, used=0; int hashed=0; boolean partial=false;
        var bySize=new LinkedHashMap<Long,List<FileEntry>>();
        for (var f : scan.files()) bySize.computeIfAbsent(f.size(),n -> new ArrayList<>()).add(f);
        var groups=new ArrayList<Group>();
        try {
            for (var candidates : bySize.values()) {
                if (candidates.size()<2) continue;
                var byHash=new LinkedHashMap<String,List<UUID>>(); var unknown=new ArrayList<UUID>();
                for (var f : candidates) {
                    if (cancelled.get() || used+f.size()>budget || f.size()>PilotSettings.HASH_LIMIT || hashed>=200) { unknown.add(f.id()); partial=true; continue; }
                    try {
                        var path=policy.resolve(workspaces.get(w.id()),f.relativePath());
                        var before=Files.readAttributes(path,BasicFileAttributes.class,NOFOLLOW_LINKS);
                        PilotException.require(before.isRegularFile() && ScanService.fingerprint(before).equals(f.fingerprint()),"STALE_SCAN");
                        used+=f.size(); hashed++;
                        var hash=NioFileSystem.hash(path,f.size(),cancelled::get);
                        PilotException.require(ScanService.fingerprint(Files.readAttributes(policy.resolve(workspaces.get(w.id()),f.relativePath()),BasicFileAttributes.class,NOFOLLOW_LINKS)).equals(f.fingerprint()),"STALE_SCAN");
                        byHash.computeIfAbsent(hash,k -> new ArrayList<>()).add(f.id());
                    } catch (IOException | PilotException e) { unknown.add(f.id()); partial=true; }
                }
                byHash.values().stream().filter(ids -> ids.size()>1).forEach(ids -> groups.add(new Group("VERIFIED",ids)));
                if (!unknown.isEmpty()) groups.add(new Group("UNVERIFIED_CANDIDATES",candidates.stream().map(FileEntry::id).toList()));
            }
            var result=new Result(cancelled.get() ? "CANCELLED" : partial ? "PARTIAL" : "COMPLETE",used,groups);
            synchronized (cache) { if (cache.size()>=32) cache.remove(cache.keySet().iterator().next()); cache.put(id,result); }
            return result;
        } finally { running.remove(id,cancelled); }
    }
}
