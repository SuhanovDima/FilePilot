package io.github.filepilot.classification;

import io.github.filepilot.scan.FileEntry;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RuleClassifier implements ClassificationPort {
    public String extension(String name) { int n = name.lastIndexOf('.'); return n < 0 ? "" : name.substring(n+1).toLowerCase(Locale.ROOT); }
    public FileCategory category(String name) {
        String ext = extension(name), lower = name.toLowerCase(Locale.ROOT);
        if (Set.of("pdf", "docx", "txt").contains(ext) && (lower.contains("договор") || lower.contains("contract"))) return FileCategory.CONTRACTS;
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg" -> FileCategory.IMAGES;
            case "pdf", "doc", "docx", "txt", "odt", "rtf", "xls", "xlsx", "ppt", "pptx" -> FileCategory.DOCUMENTS;
            case "mp4", "mov", "mkv", "avi", "webm" -> FileCategory.VIDEO;
            case "mp3", "wav", "flac", "aac", "ogg", "m4a" -> FileCategory.AUDIO;
            case "zip", "gz", "tar", "7z", "rar" -> FileCategory.ARCHIVES;
            case "java", "kt", "py", "js", "ts", "json", "xml", "html", "css", "md", "c", "cpp", "rs", "go" -> FileCategory.CODE;
            default -> FileCategory.OTHER;
        };
    }
    @Override public List<ClassificationSuggestion> classify(List<FileEntry> files, String instruction) {
        return files.stream().map(f -> new ClassificationSuggestion(f.id(), category(f.relativePath()), "Filename/extension rule; contents not read", 0.8)).toList();
    }
}
