package code.chg.agent.utils;

import code.chg.agent.config.OpenAIConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ToolResponseFiles
 * @description Helper for persisting oversized tool responses to external files.
 */
public final class ToolResponseFiles {

    private static final Set<String> INLINE_ONLY_TOOL_WHITELIST = Set.of("read_file");
    private static final String TRUNCATION_MARKER = "\n\n...[middle content omitted]...\n\n";

    private ToolResponseFiles() {
    }

    public static PreparedToolResponse prepare(String toolName, String toolCallId, String responseJson) {
        int originalSizeChars = responseJson == null ? 0 : responseJson.length();
        int originalLineCount = countLines(responseJson);
        int originalCodePointCount = countCodePoints(responseJson);
        if (shouldKeepInline(toolName)) {
            return new PreparedToolResponse(responseJson, false, null, originalSizeChars);
        }
        int maxInlineChars = OpenAIConfig.getMaxInlineToolResponseChars();
        if (responseJson == null || originalCodePointCount <= maxInlineChars) {
            return new PreparedToolResponse(responseJson, false, null, originalSizeChars);
        }

        try {
            Path outputFile = Path.of(OpenAIConfig.getOversizedToolResponseDir(), toolName + "_" + toolCallId);
            Files.writeString(outputFile, responseJson, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new PreparedToolResponse(buildTruncatedPreview(responseJson,
                    outputFile,
                    originalLineCount,
                    originalSizeChars,
                    maxInlineChars),
                    true,
                    outputFile,
                    originalSizeChars);
        } catch (IOException ignored) {
            return new PreparedToolResponse(responseJson, false, null, originalSizeChars);
        }
    }

    private static boolean shouldKeepInline(String toolName) {
        return toolName != null && INLINE_ONLY_TOOL_WHITELIST.contains(toolName);
    }

    private static String buildTruncatedPreview(String responseJson,
                                                Path outputFile,
                                                int originalLineCount,
                                                int originalSizeChars,
                                                int maxInlineChars) {
        int previewHeadChars = previewHeadChars(maxInlineChars);
        int previewTailChars = previewTailChars(maxInlineChars, previewHeadChars);
        String head = firstCodePoints(responseJson, previewHeadChars);
        String tail = lastCodePoints(responseJson, previewTailChars);

        return head +
                TRUNCATION_MARKER +
                tail +
                "\n\n[TRUNCATED] Full tool result saved to " +
                outputFile.toAbsolutePath().normalize() +
                ". Total lines: " +
                originalLineCount +
                ", total chars: " +
                originalSizeChars +
                '.';
    }

    private static int previewHeadChars(int maxInlineChars) {
        int normalizedLimit = Math.max(1, maxInlineChars);
        return Math.max(1, (normalizedLimit * 2) / 3);
    }

    private static int previewTailChars(int maxInlineChars, int previewHeadChars) {
        int normalizedLimit = Math.max(1, maxInlineChars);
        return Math.max(1, normalizedLimit - previewHeadChars);
    }

    private static int countCodePoints(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.codePointCount(0, value.length());
    }

    private static int countLines(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String firstCodePoints(String value, int count) {
        if (value == null || value.isEmpty() || count <= 0) {
            return "";
        }
        int safeCount = Math.min(count, countCodePoints(value));
        int endIndex = value.offsetByCodePoints(0, safeCount);
        return value.substring(0, endIndex);
    }

    private static String lastCodePoints(String value, int count) {
        if (value == null || value.isEmpty() || count <= 0) {
            return "";
        }
        int totalCodePoints = countCodePoints(value);
        int safeCount = Math.min(count, totalCodePoints);
        int startIndex = value.offsetByCodePoints(0, totalCodePoints - safeCount);
        return value.substring(startIndex);
    }

    public record PreparedToolResponse(String inlineResponse, boolean redirectedToFile, Path path,
                                       int originalSizeChars) {
    }
}