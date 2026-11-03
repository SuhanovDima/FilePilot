package io.github.filepilot.classification;

public enum FileCategory {
    IMAGES("Images"), DOCUMENTS("Documents"), CONTRACTS("Contracts"), VIDEO("Video"), AUDIO("Audio"), ARCHIVES("Archives"), CODE("Code"), OTHER("Other");
    private final String folder;
    FileCategory(String folder) { this.folder = folder; }
    public String folder() { return folder; }
}
