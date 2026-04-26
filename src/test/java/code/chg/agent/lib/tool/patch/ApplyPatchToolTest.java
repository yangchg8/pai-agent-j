package code.chg.agent.lib.tool.patch;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title ApplyPatchToolTest
 * @description Provides the ApplyPatchToolTest implementation.
 */
public class ApplyPatchToolTest {

    @Test
    public void addFileCreatesContentAndSummary() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-add-");
        try {
            Path file = tempDir.resolve("nested/hello.txt");
            String patch = """
                    *** Begin Patch
                    *** Add File: %s
                    +Hello
                    +world
                    *** End Patch
                    """.formatted(file);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertTrue(result.isSuccess());
            assertEquals("Hello\nworld\n", Files.readString(file));
            assertEquals("Success. Updated the following files:\nA " + file, result.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void updateFileMoveToRenamesAndModifies() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-move-");
        try {
            Path source = tempDir.resolve("old/name.txt");
            Path destination = tempDir.resolve("renamed/dir/name.txt");
            Files.createDirectories(source.getParent());
            Files.writeString(source, "alpha\nbeta\n");

            String patch = """
                    *** Begin Patch
                    *** Update File: %s
                    *** Move to: %s
                    @@
                     alpha
                    -beta
                    +gamma
                    *** End Patch
                    """.formatted(source, destination);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertTrue(result.isSuccess());
            assertFalse(Files.exists(source));
            assertEquals("alpha\ngamma\n", Files.readString(destination));
            assertEquals("Success. Updated the following files:\nM " + destination, result.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void rejectEmptyUpdateHunk() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-empty-");
        try {
            Path file = tempDir.resolve("foo.txt");
            Files.writeString(file, "value\n");

            String patch = """
                    *** Begin Patch
                    *** Update File: %s
                    *** End Patch
                    """.formatted(file);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertFalse(result.isSuccess());
            assertEquals("Invalid patch hunk on line 2: Update file hunk for path '" + file + "' is empty", result.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void whitespacePaddedMarkersAreAccepted() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-space-");
        try {
            Path file = tempDir.resolve("file.txt");
            Files.writeString(file, "one\n");

            String patch = """
                     *** Begin Patch
                    *** Update File: %s
                    @@
                    -one
                    +two
                    *** End Patch 
                    """.formatted(file);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertTrue(result.isSuccess());
            assertEquals("two\n", Files.readString(file));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void endOfFileMarkerConstrainsReplacement() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-eof-");
        try {
            Path file = tempDir.resolve("tail.txt");
            Files.writeString(file, "keep\nold\ntail\n");

            String patch = """
                    *** Begin Patch
                    *** Update File: %s
                    @@
                    -old
                    -tail
                    +new
                    +tail
                    *** End of File
                    *** End Patch
                    """.formatted(file);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertTrue(result.isSuccess());
            assertEquals("keep\nnew\ntail\n", Files.readString(file));
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void deleteMissingFileFails() throws IOException {
        Path tempDir = Files.createTempDirectory("apply-patch-delete-");
        try {
            Path file = tempDir.resolve("missing.txt");
            String patch = """
                    *** Begin Patch
                    *** Delete File: %s
                    *** End Patch
                    """.formatted(file);

            ApplyPatchResult result = ApplyPatchTool.applyPatch(patch);

            assertFalse(result.isSuccess());
            assertEquals("Failed to delete file " + file + ": file does not exist", result.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}