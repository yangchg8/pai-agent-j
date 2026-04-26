package code.chg.agent.lib.tool.patch;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.annotation.ToolPermissionChecker;
import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.lib.auth.FileLevelPermissionChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ApplyPatchTool
 * @description apply_patch implementation.
 */
public class ApplyPatchTool {

    private static final String BEGIN_PATCH_MARKER = "*** Begin Patch";
    private static final String END_PATCH_MARKER = "*** End Patch";
    private static final String ADD_FILE_MARKER = "*** Add File: ";
    private static final String DELETE_FILE_MARKER = "*** Delete File: ";
    private static final String UPDATE_FILE_MARKER = "*** Update File: ";
    private static final String MOVE_TO_MARKER = "*** Move to: ";
    private static final String EOF_MARKER = "*** End of File";
    private static final String CHANGE_CONTEXT_MARKER = "@@ ";
    private static final String EMPTY_CHANGE_CONTEXT_MARKER = "@@";

    private static final String DESCRIPTION = """
            Use the `apply_patch` tool to edit files.
            Your patch language is a stripped-down, file-oriented diff format designed to be easy to parse and safe to apply. You can think of it as a high-level envelope:
            
            *** Begin Patch
            [ one or more file sections ]
            *** End Patch
            
            Within that envelope, you get a sequence of file operations.
            You MUST include a header to specify the action you are taking.
            Each operation starts with one of three headers:
            
            *** Add File: <path> - create a new file. Every following line is a + line (the initial contents).
            *** Delete File: <path> - remove an existing file. Nothing follows.
            *** Update File: <path> - patch an existing file in place (optionally with a rename).
            
            May be immediately followed by *** Move to: <new path> if you want to rename the file.
            Then one or more hunks, each introduced by @@ (optionally followed by a hunk header).
            Within a hunk each line starts with:
            
            For instructions on [context_before] and [context_after]:
            - By default, show 3 lines of code immediately above and 3 lines immediately below each change. If a change is within 3 lines of a previous change, do NOT duplicate the first change's [context_after] lines in the second change's [context_before] lines.
            - If 3 lines of context is insufficient to uniquely identify the snippet of code within the file, use the @@ operator to indicate the class or function to which the snippet belongs. For instance, we might have:
            @@ class BaseClass
            [3 lines of pre-context]
            - [old_code]
            + [new_code]
            [3 lines of post-context]
            
            - If a code block is repeated so many times in a class or function such that even a single @@ statement and 3 lines of context cannot uniquely identify the snippet of code, you can use multiple @@ statements to jump to the right context. For instance:
            
            @@ class BaseClass
            @@ def method():
            [3 lines of pre-context]
            - [old_code]
            + [new_code]
            [3 lines of post-context]
            
            The full grammar definition is below:
            Patch := Begin { FileOp } End
            Begin := "*** Begin Patch" NEWLINE
            End := "*** End Patch" NEWLINE
            FileOp := AddFile | DeleteFile | UpdateFile
            AddFile := "*** Add File: " path NEWLINE { "+" line NEWLINE }
            DeleteFile := "*** Delete File: " path NEWLINE
            UpdateFile := "*** Update File: " path NEWLINE [ MoveTo ] { Hunk }
            MoveTo := "*** Move to: " newPath NEWLINE
            Hunk := "@@" [ header ] NEWLINE { HunkLine } [ "*** End of File" NEWLINE ]
            HunkLine := (" " | "-" | "+") text NEWLINE
            
            A full patch can combine several operations:
            
            *** Begin Patch
            *** Add File: hello.txt
            +Hello world
            *** Update File: src/app.py
            *** Move to: src/main.py
            @@ def greet():
            -print("Hi")
            +print("Hello, world!")
            *** Delete File: obsolete.txt
            *** End Patch
            
            It is important to remember:
            
            - You must include a header with your intended action (Add/Delete/Update)
            - You must prefix new lines with `+` even when creating a new file
            - File references can only be relative, NEVER ABSOLUTE.""";

    @Tool(name = "apply_patch", description = DESCRIPTION)
    public static ApplyPatchResult applyPatch(
            @ToolParameter(name = "patch", description = "The full patch body in the apply_patch format.")
            String patch
    ) {
        if (patch == null || patch.isBlank()) {
            return ApplyPatchResult.error("Invalid patch: patch must not be empty");
        }

        ParseResult parseResult = PatchParser.parse(patch);
        if (!parseResult.success()) {
            return ApplyPatchResult.error(parseResult.error());
        }
        if (parseResult.operations().isEmpty()) {
            return ApplyPatchResult.error("No files were modified.");
        }

        ChangeSummary summary = new ChangeSummary();
        for (FileOperation operation : parseResult.operations()) {
            try {
                applyOperation(operation, summary);
            } catch (IOException e) {
                return ApplyPatchResult.error(e.getMessage());
            }
        }
        return ApplyPatchResult.ok(summary.render());
    }

    @ToolPermissionChecker(toolName = "apply_patch")
    public static ToolPermissionResult applyPatchPermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String patch = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        if (patch == null) {
            return ToolPermissionResultFactory.rejected("apply_patch requires a patch argument");
        }

        ParseResult parseResult = PatchParser.parse(patch);
        if (!parseResult.success()) {
            return ToolPermissionResultFactory.rejected(parseResult.error());
        }

        List<FileLevelPermissionChecker.ResourceRequirement> requirements = extractPatchRequirements(parseResult.operations());
        if (requirements.isEmpty()) {
            return ToolPermissionResultFactory.granted();
        }
        return FileLevelPermissionChecker.checkPermissions(policy, requirements);
    }

    private static List<FileLevelPermissionChecker.ResourceRequirement> extractPatchRequirements(List<FileOperation> operations) {
        List<FileLevelPermissionChecker.ResourceRequirement> requirements = new ArrayList<>();
        for (FileOperation operation : operations) {
            Path resolvedPath = resolvePath(operation.path());
            switch (operation.type()) {
                case ADD -> requirements.add(FileLevelPermissionChecker.ResourceRequirement.of(
                        "FILE:" + resolvedPath, Permission.WRITE));
                case DELETE -> requirements.add(FileLevelPermissionChecker.ResourceRequirement.of(
                        "FILE:" + resolvedPath, Permission.WRITE));
                case UPDATE -> {
                    requirements.add(new FileLevelPermissionChecker.ResourceRequirement(
                            "FILE:" + resolvedPath, List.of(Permission.READ, Permission.WRITE)));
                    if (operation.moveTo() != null && !operation.moveTo().isBlank()) {
                        requirements.add(FileLevelPermissionChecker.ResourceRequirement.of(
                                "FILE:" + resolvePath(operation.moveTo()), Permission.WRITE));
                    }
                }
            }
        }
        return requirements;
    }

    private static void applyOperation(FileOperation operation, ChangeSummary summary) throws IOException {
        switch (operation.type()) {
            case ADD -> applyAdd(operation, summary);
            case DELETE -> applyDelete(operation, summary);
            case UPDATE -> applyUpdate(operation, summary);
        }
    }

    private static void applyAdd(FileOperation operation, ChangeSummary summary) throws IOException {
        Path target = resolvePath(operation.path());
        createParentDirectories(target, "Failed to create parent directories for " + target);
        Files.writeString(target, operation.content(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        summary.added.add(operation.path());
    }

    private static void applyDelete(FileOperation operation, ChangeSummary summary) throws IOException {
        Path target = resolvePath(operation.path());
        ensureExistingRegularFile(target, "Failed to delete file " + target);
        Files.delete(target);
        summary.deleted.add(operation.path());
    }

    private static void applyUpdate(FileOperation operation, ChangeSummary summary) throws IOException {
        Path source = resolvePath(operation.path());
        ensureExistingRegularFile(source, "Failed to read file to update " + source);

        String newContents = deriveNewContents(source, operation.chunks());
        if (operation.moveTo() != null && !operation.moveTo().isBlank()) {
            Path destination = resolvePath(operation.moveTo());
            createParentDirectories(destination, "Failed to create parent directories for " + destination);
            Files.writeString(destination, newContents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            ensureExistingRegularFile(source, "Failed to remove original " + source);
            Files.delete(source);
            summary.modified.add(operation.moveTo());
            return;
        }

        Files.writeString(source, newContents, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        summary.modified.add(operation.path());
    }

    private static String deriveNewContents(Path source, List<UpdateChunk> chunks) throws IOException {
        List<String> originalLines = new ArrayList<>(Files.readAllLines(source));
        List<Replacement> replacements = computeReplacements(originalLines, source, chunks);
        List<String> updatedLines = applyReplacements(new ArrayList<>(originalLines), replacements);
        if (updatedLines.isEmpty() || !updatedLines.get(updatedLines.size() - 1).isEmpty()) {
            updatedLines.add("");
        }
        return String.join("\n", updatedLines);
    }

    private static List<Replacement> computeReplacements(List<String> originalLines, Path path, List<UpdateChunk> chunks)
            throws IOException {
        List<Replacement> replacements = new ArrayList<>();
        int lineIndex = 0;

        for (UpdateChunk chunk : chunks) {
            if (chunk.changeContext() != null) {
                Integer contextIndex = seekSequence(originalLines, List.of(chunk.changeContext()), lineIndex, false);
                if (contextIndex == null) {
                    throw new IOException("Failed to find context '" + chunk.changeContext() + "' in " + path);
                }
                lineIndex = contextIndex + 1;
            }

            if (chunk.oldLines().isEmpty()) {
                replacements.add(new Replacement(originalLines.size(), 0, new ArrayList<>(chunk.newLines())));
                continue;
            }

            List<String> pattern = new ArrayList<>(chunk.oldLines());
            List<String> replacementLines = new ArrayList<>(chunk.newLines());
            Integer startIndex = seekSequence(originalLines, pattern, lineIndex, chunk.endOfFile());

            if (startIndex == null && !pattern.isEmpty() && pattern.get(pattern.size() - 1).isEmpty()) {
                pattern = new ArrayList<>(pattern.subList(0, pattern.size() - 1));
                if (!replacementLines.isEmpty() && replacementLines.get(replacementLines.size() - 1).isEmpty()) {
                    replacementLines = new ArrayList<>(replacementLines.subList(0, replacementLines.size() - 1));
                }
                startIndex = seekSequence(originalLines, pattern, lineIndex, chunk.endOfFile());
            }

            if (startIndex == null) {
                throw new IOException("Failed to find expected lines in " + path + ":\n" + String.join("\n", chunk.oldLines()));
            }

            replacements.add(new Replacement(startIndex, pattern.size(), replacementLines));
            lineIndex = startIndex + pattern.size();
        }

        replacements.sort(Comparator.comparingInt(Replacement::startIndex));
        return replacements;
    }

    private static List<String> applyReplacements(List<String> lines, List<Replacement> replacements) {
        List<Replacement> descending = new ArrayList<>(replacements);
        descending.sort(Comparator.comparingInt(Replacement::startIndex).reversed());
        for (Replacement replacement : descending) {
            int start = replacement.startIndex();
            int end = Math.min(lines.size(), start + replacement.oldLength());
            lines.subList(start, end).clear();
            lines.addAll(start, replacement.newLines());
        }
        return lines;
    }

    private static Integer seekSequence(List<String> haystack, List<String> needle, int startIndex, boolean endOfFile) {
        if (needle.isEmpty()) {
            return haystack.size();
        }
        int maxStart = haystack.size() - needle.size();
        if (maxStart < startIndex) {
            return null;
        }

        outer:
        for (int index = Math.max(0, startIndex); index <= maxStart; index++) {
            if (endOfFile && index + needle.size() != haystack.size()) {
                continue;
            }
            for (int offset = 0; offset < needle.size(); offset++) {
                if (!haystack.get(index + offset).equals(needle.get(offset))) {
                    continue outer;
                }
            }
            return index;
        }
        return null;
    }

    private static void ensureExistingRegularFile(Path path, String context) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException(context + ": file does not exist");
        }
        if (Files.isDirectory(path)) {
            throw new IOException(context + ": path is a directory");
        }
    }

    private static void createParentDirectories(Path path, String context) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IOException(context + ": " + e.getMessage(), e);
            }
        }
    }

    private static Path resolvePath(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    static final class PatchParser {
        private final List<String> lines;

        private PatchParser(List<String> lines) {
            this.lines = lines;
        }

        static ParseResult parse(String patch) {
            String normalized = patch.replace("\r\n", "\n").replace('\r', '\n').trim();
            List<String> rawLines = normalized.isEmpty()
                    ? List.of()
                    : List.of(normalized.split("\n", -1));

            BoundaryResult boundary = checkPatchBoundaries(rawLines);
            if (!boundary.valid()) {
                return ParseResult.error("Invalid patch: " + boundary.error());
            }

            List<String> activeLines = boundary.lines();
            List<FileOperation> operations = new ArrayList<>();
            int index = 1;
            int lastIndex = activeLines.size() - 1;
            int lineNumber = 2;

            while (index < lastIndex) {
                ParseOneResult result = new PatchParser(activeLines.subList(index, lastIndex)).parseOneHunk(lineNumber);
                if (!result.success()) {
                    return ParseResult.error(result.error());
                }
                operations.add(result.operation());
                index += result.linesConsumed();
                lineNumber += result.linesConsumed();
            }
            return ParseResult.ok(operations);
        }

        private ParseOneResult parseOneHunk(int lineNumber) {
            if (lines.isEmpty()) {
                return ParseOneResult.error("Invalid patch: No files were modified.");
            }

            String firstLine = lines.get(0).trim();
            if (firstLine.startsWith(ADD_FILE_MARKER)) {
                String path = firstLine.substring(ADD_FILE_MARKER.length());
                StringBuilder contents = new StringBuilder();
                int parsedLines = 1;
                for (int i = 1; i < lines.size(); i++) {
                    String addLine = lines.get(i);
                    if (addLine.startsWith("+")) {
                        contents.append(addLine.substring(1)).append("\n");
                        parsedLines++;
                    } else {
                        break;
                    }
                }
                return ParseOneResult.ok(FileOperation.add(path, contents.toString()), parsedLines);
            }

            if (firstLine.startsWith(DELETE_FILE_MARKER)) {
                String path = firstLine.substring(DELETE_FILE_MARKER.length());
                return ParseOneResult.ok(FileOperation.delete(path), 1);
            }

            if (firstLine.startsWith(UPDATE_FILE_MARKER)) {
                String path = firstLine.substring(UPDATE_FILE_MARKER.length());
                int parsedLines = 1;
                int cursor = 1;
                String moveTo = null;

                if (cursor < lines.size()) {
                    String moveLine = lines.get(cursor).trim();
                    if (moveLine.startsWith(MOVE_TO_MARKER)) {
                        moveTo = moveLine.substring(MOVE_TO_MARKER.length());
                        cursor++;
                        parsedLines++;
                    }
                }

                List<UpdateChunk> chunks = new ArrayList<>();
                while (cursor < lines.size()) {
                    String line = lines.get(cursor);
                    if (line.trim().isEmpty()) {
                        cursor++;
                        parsedLines++;
                        continue;
                    }
                    if (line.trim().startsWith("***")) {
                        break;
                    }

                    ChunkParseResult chunkResult = parseUpdateChunk(lines.subList(cursor, lines.size()), lineNumber + parsedLines,
                            chunks.isEmpty());
                    if (!chunkResult.success()) {
                        return ParseOneResult.error(chunkResult.error());
                    }
                    chunks.add(chunkResult.chunk());
                    cursor += chunkResult.linesConsumed();
                    parsedLines += chunkResult.linesConsumed();
                }

                if (chunks.isEmpty()) {
                    return ParseOneResult.error(invalidHunk(lineNumber,
                            "Update file hunk for path '" + path + "' is empty"));
                }
                return ParseOneResult.ok(FileOperation.update(path, moveTo, chunks), parsedLines);
            }

            return ParseOneResult.error(invalidHunk(lineNumber,
                    "'" + firstLine + "' is not a valid hunk header. Valid hunk headers: '*** Add File: {path}', '*** Delete File: {path}', '*** Update File: {path}'"));
        }

        private static ChunkParseResult parseUpdateChunk(List<String> lines, int lineNumber, boolean allowMissingContext) {
            if (lines.isEmpty()) {
                return ChunkParseResult.error(invalidHunk(lineNumber, "Update hunk does not contain any lines"));
            }

            String firstLine = lines.get(0).trim();
            String changeContext = null;
            int startIndex = 0;
            if (EMPTY_CHANGE_CONTEXT_MARKER.equals(firstLine)) {
                startIndex = 1;
            } else if (firstLine.startsWith(CHANGE_CONTEXT_MARKER)) {
                changeContext = firstLine.substring(CHANGE_CONTEXT_MARKER.length());
                startIndex = 1;
            } else if (!allowMissingContext) {
                return ChunkParseResult.error(invalidHunk(lineNumber,
                        "Expected update hunk to start with a @@ context marker, got: '" + lines.get(0) + "'"));
            }

            if (startIndex >= lines.size()) {
                return ChunkParseResult.error(invalidHunk(lineNumber + 1, "Update hunk does not contain any lines"));
            }

            List<String> oldLines = new ArrayList<>();
            List<String> newLines = new ArrayList<>();
            boolean endOfFile = false;
            int parsedLines = 0;

            for (int i = startIndex; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (EOF_MARKER.equals(trimmed)) {
                    if (parsedLines == 0) {
                        return ChunkParseResult.error(invalidHunk(lineNumber + 1, "Update hunk does not contain any lines"));
                    }
                    endOfFile = true;
                    parsedLines++;
                    break;
                }

                if (line.isEmpty()) {
                    oldLines.add("");
                    newLines.add("");
                    parsedLines++;
                    continue;
                }

                char prefix = line.charAt(0);
                String text = line.substring(1);
                switch (prefix) {
                    case ' ' -> {
                        oldLines.add(text);
                        newLines.add(text);
                    }
                    case '+' -> newLines.add(text);
                    case '-' -> oldLines.add(text);
                    default -> {
                        if (parsedLines == 0) {
                            return ChunkParseResult.error(invalidHunk(lineNumber + 1,
                                    "Unexpected line found in update hunk: '" + line
                                            + "'. Every line should start with ' ' (context line), '+' (added line), or '-' (removed line)"));
                        }
                        return ChunkParseResult.ok(new UpdateChunk(changeContext, oldLines, newLines, endOfFile),
                                parsedLines + startIndex);
                    }
                }
                parsedLines++;
            }

            return ChunkParseResult.ok(new UpdateChunk(changeContext, oldLines, newLines, endOfFile),
                    parsedLines + startIndex);
        }

        private static BoundaryResult checkPatchBoundaries(List<String> lines) {
            BoundaryResult strictResult = checkPatchBoundariesStrict(lines);
            if (strictResult.valid()) {
                return strictResult;
            }
            return checkPatchBoundariesLenient(lines, strictResult.error());
        }

        private static BoundaryResult checkPatchBoundariesStrict(List<String> lines) {
            String firstLine = lines.isEmpty() ? null : lines.get(0).trim();
            String lastLine = lines.isEmpty() ? null : lines.get(lines.size() - 1).trim();
            if (BEGIN_PATCH_MARKER.equals(firstLine) && END_PATCH_MARKER.equals(lastLine)) {
                return BoundaryResult.valid(lines);
            }
            if (!BEGIN_PATCH_MARKER.equals(firstLine)) {
                return BoundaryResult.invalid("The first line of the patch must be '*** Begin Patch'");
            }
            return BoundaryResult.invalid("The last line of the patch must be '*** End Patch'");
        }

        private static BoundaryResult checkPatchBoundariesLenient(List<String> originalLines, String originalError) {
            if (originalLines.size() >= 4) {
                String firstLine = originalLines.get(0).trim();
                String lastLine = originalLines.get(originalLines.size() - 1).trim();
                if (("<<EOF".equals(firstLine) || "<<'EOF'".equals(firstLine) || "<<\"EOF\"".equals(firstLine))
                        && lastLine.endsWith("EOF")) {
                    List<String> innerLines = originalLines.subList(1, originalLines.size() - 1);
                    BoundaryResult strictInner = checkPatchBoundariesStrict(innerLines);
                    if (strictInner.valid()) {
                        return strictInner;
                    }
                    return strictInner;
                }
            }
            return BoundaryResult.invalid(originalError);
        }
    }

    private static String invalidHunk(int lineNumber, String message) {
        return "Invalid patch hunk on line " + lineNumber + ": " + message;
    }

    record FileOperation(OperationType type, String path, String moveTo, String content, List<UpdateChunk> chunks) {

        static FileOperation add(String path, String content) {
            return new FileOperation(OperationType.ADD, path, null, content, List.of());
        }

        static FileOperation delete(String path) {
            return new FileOperation(OperationType.DELETE, path, null, null, List.of());
        }

        static FileOperation update(String path, String moveTo, List<UpdateChunk> chunks) {
            return new FileOperation(OperationType.UPDATE, path, moveTo, null, List.copyOf(chunks));
        }
    }

    enum OperationType {
        ADD,
        UPDATE,
        DELETE
    }

    record UpdateChunk(String changeContext, List<String> oldLines, List<String> newLines, boolean endOfFile) {
    }

    record Replacement(int startIndex, int oldLength, List<String> newLines) {
    }

    record ParseResult(boolean success, String error, List<FileOperation> operations) {

        static ParseResult ok(List<FileOperation> operations) {
            return new ParseResult(true, null, List.copyOf(operations));
        }

        static ParseResult error(String error) {
            return new ParseResult(false, error, List.of());
        }
    }

    record ParseOneResult(boolean success, String error, FileOperation operation, int linesConsumed) {

        static ParseOneResult ok(FileOperation operation, int linesConsumed) {
            return new ParseOneResult(true, null, operation, linesConsumed);
        }

        static ParseOneResult error(String error) {
            return new ParseOneResult(false, error, null, 0);
        }
    }

    record ChunkParseResult(boolean success, String error, UpdateChunk chunk, int linesConsumed) {

        static ChunkParseResult ok(UpdateChunk chunk, int linesConsumed) {
            return new ChunkParseResult(true, null, chunk, linesConsumed);
        }

        static ChunkParseResult error(String error) {
            return new ChunkParseResult(false, error, null, 0);
        }
    }

    record BoundaryResult(boolean valid, String error, List<String> lines) {

        static BoundaryResult valid(List<String> lines) {
            return new BoundaryResult(true, null, lines);
        }

        static BoundaryResult invalid(String error) {
            return new BoundaryResult(false, error, List.of());
        }
    }

    static final class ChangeSummary {
        private final List<String> added = new ArrayList<>();
        private final List<String> modified = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        String render() {
            StringBuilder out = new StringBuilder("Success. Updated the following files:\n");
            for (String path : added) {
                out.append("A ").append(path).append("\n");
            }
            for (String path : modified) {
                out.append("M ").append(path).append("\n");
            }
            for (String path : deleted) {
                out.append("D ").append(path).append("\n");
            }
            return out.toString().trim();
        }
    }
}
