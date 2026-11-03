package io.github.filepilot.config;

public class PilotException extends RuntimeException {
    private final String code;
    public PilotException(String code) { super(code); this.code = code; }
    public String code() { return code; }
    public static void require(boolean condition, String code) { if (!condition) throw new PilotException(code); }
}
