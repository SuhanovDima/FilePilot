package io.github.filepilot.classification;

import io.github.filepilot.scan.FileEntry;
import io.github.filepilot.config.PilotException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ClassifierService implements AutoCloseable {
    public record Result(Map<UUID,ClassificationSuggestion> suggestions, String status) {}
    private final ClassificationPort ai;
    private final Duration timeout;
    private final String modelName;
    private volatile String status="OFFLINE";
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r -> { var t=new Thread(r,"filepilot-classifier"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    @Autowired public ClassifierService(Environment env) {
        this.timeout=Duration.ofMillis(Long.parseLong(env.getProperty("filepilot.llm.timeout-ms","8000")));
        PilotException.require(timeout.toMillis()>=100 && timeout.toMillis()<=30000,"INVALID_CONFIG");
        modelName=env.getProperty("filepilot.llm.model","qwen3.5:2b");
        ai=Boolean.parseBoolean(env.getProperty("filepilot.llm.enabled","false"))
            ? new SpringAiLocalClassifier(env.getProperty("filepilot.llm.base-url","http://127.0.0.1:11434/v1"),modelName,timeout) : null;
    }
    public ClassifierService(ClassificationPort ai, Duration timeout) { this.ai=ai; this.timeout=timeout; this.modelName="fake"; }
    public Result enrich(List<FileEntry> files,String instruction,boolean useAI) {
        if (!useAI || files.isEmpty()) return new Result(Map.of(),"RULES");
        if (ai==null) return new Result(Map.of(),"RULES_FALLBACK_OFFLINE");
        var sample=files.stream().limit(20).toList(); Future<List<ClassificationSuggestion>> task=null;
        try {
            task=worker.submit(() -> ai.classify(sample,instruction));
            var values=new SuggestionValidator().validate(task.get(timeout.toMillis(),TimeUnit.MILLISECONDS),sample);
            var result=new HashMap<UUID,ClassificationSuggestion>(); values.forEach(v -> result.put(v.fileId(),v));
            status="ONLINE"; return new Result(Map.copyOf(result),files.size()>sample.size() ? "LLM_PARTIAL_RULES_REMAINDER" : "LLM");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); status="OFFLINE"; return new Result(Map.of(),"RULES_FALLBACK_INTERRUPTED");
        } catch (ExecutionException | TimeoutException | RejectedExecutionException | PilotException e) {
            status="OFFLINE"; return new Result(Map.of(),"RULES_FALLBACK_OFFLINE_OR_INVALID");
        } finally { if (task!=null && !task.isDone()) task.cancel(true); }
    }
    public String status() { return status; }
    public String modelName() { return ai==null ? "rules" : modelName; }
    @Override public void close() throws Exception { worker.shutdownNow(); if (ai instanceof AutoCloseable c) c.close(); }
}
