package code.chg.agent.lib.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillLoader
 * @description Discovers SKILL.md files from a list of root directories and parses
 */
public class SkillLoader {
    private static final String SKILL_FILENAME = "SKILL.md";
    private static final int MAX_SCAN_DEPTH = 6;

    public List<SkillMetadata> load(List<Path> roots) {
        List<SkillMetadata> skills = new ArrayList<>();
        Set<Path> seenPaths = new HashSet<>();
        for (Path root : roots) {
            discoverSkills(root, skills, seenPaths);
        }
        skills.sort(Comparator.comparing(SkillMetadata::name));
        return skills;
    }

    private void discoverSkills(Path root, List<SkillMetadata> skills, Set<Path> seenPaths) {
        Path canonicalRoot;
        try {
            canonicalRoot = root.toRealPath();
        } catch (IOException e) {
            return;
        }
        if (!Files.isDirectory(canonicalRoot)) {
            return;
        }
        Deque<DirEntry> queue = new ArrayDeque<>();
        Set<Path> visited = new HashSet<>();
        queue.add(new DirEntry(canonicalRoot, 0));
        visited.add(canonicalRoot);

        while (!queue.isEmpty()) {
            DirEntry current = queue.removeFirst();
            try (var stream = Files.list(current.path())) {
                List<Path> entries = stream.sorted().toList();
                for (Path entry : entries) {
                    String name = entry.getFileName() == null ? "" : entry.getFileName().toString();
                    if (name.startsWith(".")) {
                        continue;
                    }
                    BasicFileAttributes attrs;
                    try {
                        attrs = Files.readAttributes(entry, BasicFileAttributes.class,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    } catch (IOException ignored) {
                        continue;
                    }
                    if (attrs.isSymbolicLink()) {
                        try {
                            Path realPath = entry.toRealPath();
                            if (Files.isDirectory(realPath) && current.depth() < MAX_SCAN_DEPTH
                                    && visited.add(realPath)) {
                                queue.addLast(new DirEntry(realPath, current.depth() + 1));
                            }
                        } catch (IOException ignored) {
                        }
                        continue;
                    }
                    if (attrs.isDirectory()) {
                        if (current.depth() < MAX_SCAN_DEPTH) {
                            try {
                                Path realPath = entry.toRealPath();
                                if (visited.add(realPath)) {
                                    queue.addLast(new DirEntry(realPath, current.depth() + 1));
                                }
                            } catch (IOException ignored) {
                            }
                        }
                        continue;
                    }
                    if (attrs.isRegularFile() && SKILL_FILENAME.equals(name)) {
                        parseSkillFile(entry).ifPresent(skill -> {
                            if (seenPaths.add(skill.pathToSkillMd())) {
                                skills.add(skill);
                            }
                        });
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    private Optional<SkillMetadata> parseSkillFile(Path path) {
        String contents;
        try {
            contents = Files.readString(path);
        } catch (IOException e) {
            return Optional.empty();
        }
        String name = null;
        String description = "";
        String[] lines = contents.split("\\R", -1);
        // Require YAML frontmatter (--- ... ---); skip files without it
        if (lines.length < 2 || !"---".equals(lines[0].trim())) {
            return Optional.empty();
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if ("---".equals(line.trim())) {
                break;
            }
            if (name == null && line.startsWith("name:")) {
                name = line.substring("name:".length()).trim();
            } else if (description.isEmpty() && line.startsWith("description:")) {
                description = line.substring("description:".length()).trim();
            }
        }
        if (name == null || name.isBlank()) {
            // Fallback: use parent directory name
            Path parent = path.getParent();
            name = (parent != null && parent.getFileName() != null)
                    ? parent.getFileName().toString() : "skill";
        }
        Path canonical;
        try {
            canonical = path.toRealPath();
        } catch (IOException e) {
            canonical = path.toAbsolutePath().normalize();
        }
        return Optional.of(new SkillMetadata(name.strip(), description.strip(), canonical));
    }

    private record DirEntry(Path path, int depth) {
    }
}
