package code.chg.agent.lib.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title SkillsManager
 * @description Provides the SkillsManager implementation.
 */
public class SkillsManager {
    private final Path userSkillDir;
    private final Path workspaceSkillDir;
    private final SkillLoader loader;
    private CachedSkills cachedSkills;

    public SkillsManager(String workDir) {
        this(Path.of(System.getProperty("user.home"), ".pai-agent", "skills"),
                Path.of(workDir, ".pai-agent", "skills"),
                new SkillLoader());
    }

    public SkillsManager(Path userSkillDir, Path workspaceSkillDir, SkillLoader loader) {
        this.userSkillDir = userSkillDir.toAbsolutePath().normalize();
        this.workspaceSkillDir = workspaceSkillDir.toAbsolutePath().normalize();
        this.loader = loader;
    }

    public synchronized List<SkillMetadata> skills() {
        String signature = signature();
        if (cachedSkills == null || !cachedSkills.signature().equals(signature)) {
            cachedSkills = new CachedSkills(signature, loader.load(skillRoots()));
        }
        return cachedSkills.skills();
    }

    public synchronized List<SkillMetadata> reload() {
        cachedSkills = new CachedSkills(signature(), loader.load(skillRoots()));
        return cachedSkills.skills();
    }

    private List<Path> skillRoots() {
        return List.of(workspaceSkillDir, userSkillDir);
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (Path root : List.of(workspaceSkillDir, userSkillDir)) {
            if (!Files.exists(root)) continue;
            try {
                long modified = Files.getLastModifiedTime(root).toMillis();
                sb.append(root.toRealPath()).append('|').append(modified).append('\n');
            } catch (IOException ignored) {
            }
        }
        return sb.toString();
    }

    private record CachedSkills(String signature, List<SkillMetadata> skills) {
    }
}