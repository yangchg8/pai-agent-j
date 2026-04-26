package code.chg.agent.lib.tool.file;

import code.chg.agent.annotation.Tool;
import code.chg.agent.annotation.ToolParameter;
import code.chg.agent.annotation.ToolPermissionChecker;
import code.chg.agent.core.permission.Permission;
import code.chg.agent.core.permission.ToolPermissionPolicy;
import code.chg.agent.core.tool.ToolPermissionResult;
import code.chg.agent.core.tool.ToolPermissionResultFactory;
import code.chg.agent.lib.auth.FileLevelPermissionChecker;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title FileTool
 * @description File system tool: read, write, list directory contents.
 */
public class FileTool {

    static final int MAX_READ_FILE_RESPONSE_CHARS = 20_000;
    static final String READ_TRUNCATION_MARKER = "\n\n...[read_file output truncated at 20000 chars]";

    private static final String READ_DESCRIPTION = """
            Read the contents of a file at the given path.
            - Use `start_line` and `end_line` to read a specific range (1-based, inclusive).
            - Omit them to read the entire file.
            - Returns the file contents as a line-numbered string.
            - If the returned content exceeds 20000 characters, it is truncated and marked.""";

    private static final String WRITE_DESCRIPTION = """
            Write or overwrite a file with the given content.
            - Creates parent directories if they do not exist.
            - Use `apply_patch` for incremental edits rather than rewriting entire files.""";

    private static final String LIST_DESCRIPTION = """
            List the contents of a directory.
            - Returns file names and types (file/directory).
            - Set `recursive` to true to list all nested files.""";

    @Tool(name = "read_file", description = READ_DESCRIPTION)
    @SuppressWarnings("unused")
    public static FileToolResult readFile(
            @ToolParameter(name = "path", description = "Absolute or relative path to the file to read.")
            String path,
            @ToolParameter(name = "start_line", description = "1-based start line number (inclusive). Omit to read from the beginning.")
            Integer startLine,
            @ToolParameter(name = "end_line", description = "1-based end line number (inclusive). Omit to read to the end of file.")
            Integer endLine
    ) {
        if (path == null || path.isBlank()) {
            return FileToolResult.error("Error: path must be provided");
        }
        Path filePath = Path.of(path);
        if (!Files.exists(filePath)) {
            return FileToolResult.error("Error: file not found: " + path);
        }
        if (Files.isDirectory(filePath)) {
            return FileToolResult.error("Error: path is a directory, use list_dir instead: " + path);
        }
        try {
            List<String> lines = Files.readAllLines(filePath);
            int total = lines.size();
            int start = (startLine != null && startLine > 0) ? startLine - 1 : 0;
            int end = (endLine != null && endLine > 0) ? Math.min(endLine, total) : total;
            if (start >= total) {
                return FileToolResult.error("Error: start_line " + startLine + " exceeds file length " + total);
            }
            List<String> selected = lines.subList(start, end);
            String content = formatLineNumberedContent(selected, start + 1);
            return FileToolResult.ok(truncateReadContent(content), total);
        } catch (IOException e) {
            return FileToolResult.error("Error reading file: " + e.getMessage());
        }
    }

    private static String formatLineNumberedContent(List<String> lines, int firstLineNumber) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(firstLineNumber + i)
                    .append(": ")
                    .append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String truncateReadContent(String content) {
        if (content == null || content.length() <= MAX_READ_FILE_RESPONSE_CHARS) {
            return content;
        }
        int contentLimit = Math.max(0, MAX_READ_FILE_RESPONSE_CHARS - READ_TRUNCATION_MARKER.length());
        return content.substring(0, contentLimit) + READ_TRUNCATION_MARKER;
    }

    @ToolPermissionChecker(toolName = "read_file")
    @SuppressWarnings("unused")
    public static ToolPermissionResult readFilePermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String path = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        if (path == null) {
            return ToolPermissionResultFactory.rejected("read_file requires a path argument");
        }
        return FileLevelPermissionChecker.checkPermission(policy, "FILE:" + path, List.of(Permission.READ));
    }

    @Tool(name = "write_file", description = WRITE_DESCRIPTION)
    @SuppressWarnings("unused")
    public static FileToolResult writeFile(
            @ToolParameter(name = "path", description = "Absolute or relative path to the file to write.")
            String path,
            @ToolParameter(name = "content", description = "The full content to write to the file.")
            String content
    ) {
        if (path == null || path.isBlank()) {
            return FileToolResult.error("Error: path must be provided");
        }
        if (content == null) {
            content = "";
        }
        Path filePath = Path.of(path);
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return FileToolResult.ok("File written successfully: " + path, 0);
        } catch (IOException e) {
            return FileToolResult.error("Error writing file: " + e.getMessage());
        }
    }

    @Tool(name = "list_dir", description = LIST_DESCRIPTION)
    @SuppressWarnings("unused")
    public static FileToolResult listDir(
            @ToolParameter(name = "path", description = "Absolute or relative path to the directory.")
            String path,
            @ToolParameter(name = "recursive", description = "If true, list all nested files and directories recursively. Default: false.")
            Boolean recursive
    ) {
        if (path == null || path.isBlank()) {
            return FileToolResult.error("Error: path must be provided");
        }
        Path dirPath = Path.of(path);
        if (!Files.exists(dirPath)) {
            return FileToolResult.error("Error: directory not found: " + path);
        }
        if (!Files.isDirectory(dirPath)) {
            return FileToolResult.error("Error: path is not a directory: " + path);
        }
        try {
            List<String> entries = new ArrayList<>();
            boolean recurse = Boolean.TRUE.equals(recursive);
            if (recurse) {
                Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        entries.add("[file] " + dirPath.relativize(file));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (!dir.equals(dirPath)) {
                            entries.add("[dir]  " + dirPath.relativize(dir) + "/");
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                try (Stream<Path> stream = Files.list(dirPath)) {
                    stream.sorted().forEach(p -> {
                        String type = Files.isDirectory(p) ? "[dir]  " : "[file] ";
                        entries.add(type + p.getFileName() + (Files.isDirectory(p) ? "/" : ""));
                    });
                }
            }
            return FileToolResult.ok(String.join("\n", entries), entries.size());
        } catch (IOException e) {
            return FileToolResult.error("Error listing directory: " + e.getMessage());
        }
    }

    @ToolPermissionChecker(toolName = "write_file")
    @SuppressWarnings("unused")
    public static ToolPermissionResult writeFilePermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String filePath = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        if (filePath == null) {
            return ToolPermissionResultFactory.rejected("write_file requires a path argument");
        }
        return FileLevelPermissionChecker.checkPermission(policy, "FILE:" + filePath, List.of(Permission.WRITE));
    }

    @ToolPermissionChecker(toolName = "list_dir")
    @SuppressWarnings("unused")
    public static ToolPermissionResult listDirPermissionCheck(ToolPermissionPolicy policy, Object[] arguments) {
        String dirPath = arguments.length > 0 && arguments[0] instanceof String s ? s : null;
        if (dirPath == null) {
            return ToolPermissionResultFactory.rejected("list_dir requires a path argument");
        }
        return FileLevelPermissionChecker.checkPermission(policy, "DIR:" + dirPath, List.of(Permission.READ));
    }
}
