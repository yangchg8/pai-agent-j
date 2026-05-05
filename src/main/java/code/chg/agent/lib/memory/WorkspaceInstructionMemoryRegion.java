package code.chg.agent.lib.memory;

import code.chg.agent.core.memory.MemoryRegion;
import code.chg.agent.llm.LLMMessage;
import code.chg.agent.llm.MessageType;
import code.chg.agent.llm.message.ContentLLMMessage;
import code.chg.agent.utils.MessageIdGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title WorkspaceInstructionMemoryRegion
 * @description Discovers AGENTS.md docs in the workspace and injects them as a user instruction block.
 */
public class WorkspaceInstructionMemoryRegion implements MemoryRegion {
    private static final Logger LOGGER = Logger.getLogger(WorkspaceInstructionMemoryRegion.class.getName());

    private static final String DEFAULT_AGENTS_MD_FILENAME = "AGENTS.md";
    private static final String LOCAL_AGENTS_MD_FILENAME = "AGENTS.override.md";
    private static final long DEFAULT_PROJECT_DOC_MAX_BYTES = 32L * 1024L;
    private static final String AGENTS_MD_SEPARATOR = "\n\n--- project-doc ---\n\n";
    private static final String HIERARCHICAL_AGENTS_MESSAGE = """
            Files called AGENTS.md commonly appear in many places inside a container - at \"/\", in \"~\", deep within git repositories, or in any other directory; their location is not limited to version-controlled folders.

            Their purpose is to pass along human guidance to you, the agent. Such guidance can include coding standards, explanations of the project layout, steps for building or testing, and even wording that must accompany a GitHub pull-request description produced by the agent; all of it is to be followed.

            Each AGENTS.md governs the entire directory that contains it and every child directory beneath that point. Whenever you change a file, you have to comply with every AGENTS.md whose scope covers that file. Naming conventions, stylistic rules and similar directives are restricted to the code that falls inside that scope unless the document explicitly states otherwise.

            When two AGENTS.md files disagree, the one located deeper in the directory structure overrides the higher-level file, while instructions given directly in the prompt by the system, developer, or user outrank any AGENTS.md content.
            """;

    private final Path cwd;
    private final Path paiHome;
    private final long projectDocMaxBytes;
    private final List<String> projectRootMarkers;
    private final List<String> projectDocFallbackFilenames;
    private final boolean includeHierarchicalMessage;
    private final String messageId;

    public WorkspaceInstructionMemoryRegion(String workDir, String userHome) {
        this(
                Path.of(workDir),
                Path.of(userHome, ".pai-agent"),
                DEFAULT_PROJECT_DOC_MAX_BYTES,
                List.of(".git"),
                Collections.emptyList(),
                true
        );
    }

    public WorkspaceInstructionMemoryRegion(
            Path cwd,
            Path paiHome,
            long projectDocMaxBytes,
            List<String> projectRootMarkers,
            List<String> projectDocFallbackFilenames,
            boolean includeHierarchicalMessage
    ) {
        this.cwd = normalizePath(cwd);
        this.paiHome = normalizePath(paiHome);
        this.projectDocMaxBytes = Math.max(0L, projectDocMaxBytes);
        this.projectRootMarkers = projectRootMarkers == null ? List.of() : List.copyOf(projectRootMarkers);
        this.projectDocFallbackFilenames = projectDocFallbackFilenames == null
                ? List.of() : List.copyOf(projectDocFallbackFilenames);
        this.includeHierarchicalMessage = includeHierarchicalMessage;
        this.messageId = MessageIdGenerator.generateWithPrefix("workspace-instructions-memory");
    }

    @Override
    public String getName() {
        return "WORKSPACE_INSTRUCTION_MEMORY_REGION";
    }

    @Override
    public List<LLMMessage> messages() {
        String instructions = buildWorkspaceInstructions();
        if (instructions == null || instructions.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(ContentLLMMessage.of(messageId, MessageType.HUMAN, instructions));
    }

    private String buildWorkspaceInstructions() {
        String agentsDocs;
        try {
            agentsDocs = readAgentsMd();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to discover AGENTS.md docs", e);
            agentsDocs = null;
        }

        StringBuilder output = new StringBuilder();
        String globalInstructions = loadGlobalInstructions();
        if (globalInstructions != null && !globalInstructions.isBlank()) {
            output.append(globalInstructions);
        }

        if (agentsDocs != null && !agentsDocs.isBlank()) {
            if (!output.isEmpty()) {
                output.append(AGENTS_MD_SEPARATOR);
            }
            output.append(agentsDocs);
        }

        if (includeHierarchicalMessage) {
            if (!output.isEmpty()) {
                output.append("\n\n");
            }
            output.append(HIERARCHICAL_AGENTS_MESSAGE);
        }

        if (output.isEmpty()) {
            return null;
        }

        return "# AGENTS.md instructions for " + cwd + "\n\n<INSTRUCTIONS>\n"
                + output
                + "\n</INSTRUCTIONS>";
    }

    private String loadGlobalInstructions() {
        if (paiHome == null) {
            return null;
        }
        for (String candidate : List.of(LOCAL_AGENTS_MD_FILENAME, DEFAULT_AGENTS_MD_FILENAME)) {
            Path path = paiHome.resolve(candidate);
            try {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String contents = Files.readString(path, StandardCharsets.UTF_8);
                String trimmed = contents == null ? null : contents.trim();
                if (trimmed != null && !trimmed.isBlank()) {
                    return trimmed;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private String readAgentsMd() throws IOException {
        if (projectDocMaxBytes == 0L) {
            return null;
        }

        List<Path> paths = agentsMdPaths();
        if (paths.isEmpty()) {
            return null;
        }

        long remaining = projectDocMaxBytes;
        List<String> parts = new ArrayList<>();

        for (Path path : paths) {
            if (remaining == 0L) {
                break;
            }
            if (!Files.isRegularFile(path)) {
                continue;
            }

            byte[] data;
            try {
                data = Files.readAllBytes(path);
            } catch (IOException e) {
                if (Files.exists(path)) {
                    throw e;
                }
                continue;
            }

            long originalSize = data.length;
            if (originalSize > remaining) {
                byte[] truncated = new byte[(int) remaining];
                System.arraycopy(data, 0, truncated, 0, truncated.length);
                data = truncated;
                LOGGER.warning("Project doc `" + path + "` exceeds remaining budget ("
                        + remaining + " bytes) - truncating.");
            }

            String text = new String(data, StandardCharsets.UTF_8);
            if (!text.trim().isEmpty()) {
                parts.add(text);
                remaining = Math.max(0L, remaining - data.length);
            }
        }

        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", parts);
    }

    private List<Path> agentsMdPaths() throws IOException {
        if (projectDocMaxBytes == 0L) {
            return List.of();
        }
        Path searchStart = normalizePath(cwd);
        Path projectRoot = findProjectRoot(searchStart);

        List<Path> searchDirs = collectSearchDirs(searchStart, projectRoot);
        List<String> candidateNames = candidateFilenames();
        List<Path> found = new ArrayList<>();

        for (Path dir : searchDirs) {
            for (String name : candidateNames) {
                Path candidate = dir.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    found.add(candidate);
                    break;
                }
            }
        }
        return found;
    }

    private Path findProjectRoot(Path start) {
        if (start == null || projectRootMarkers.isEmpty()) {
            return null;
        }
        Path cursor = start;
        while (cursor != null) {
            for (String marker : projectRootMarkers) {
                if (marker == null || marker.isBlank()) {
                    continue;
                }
                if (Files.exists(cursor.resolve(marker))) {
                    return cursor;
                }
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static List<Path> collectSearchDirs(Path start, Path root) {
        if (start == null) {
            return List.of();
        }
        if (root == null) {
            return List.of(start);
        }

        List<Path> dirs = new ArrayList<>();
        Path cursor = start;
        while (cursor != null) {
            dirs.add(cursor);
            if (cursor.equals(root)) {
                break;
            }
            cursor = cursor.getParent();
        }
        Collections.reverse(dirs);
        return dirs;
    }

    private List<String> candidateFilenames() {
        Set<String> names = new LinkedHashSet<>();
        names.add(LOCAL_AGENTS_MD_FILENAME);
        names.add(DEFAULT_AGENTS_MD_FILENAME);
        for (String fallback : projectDocFallbackFilenames) {
            if (fallback != null && !fallback.isBlank()) {
                names.add(fallback);
            }
        }
        return new ArrayList<>(names);
    }

    private static Path normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        try {
            return path.toRealPath().normalize();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }
}