package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class SourceFileRevisionTest {
    @Test
    void atomicReplacementWithMatchingSizeAndTimeIsStillADifferentRevision(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("source.jpg");
        Files.writeString(path, "before");
        SourceFileRevision revision = SourceFileRevision.read(indexed(path));
        Path replacement = directory.resolve("replacement.jpg");
        Files.writeString(replacement, "after!");
        Files.setLastModifiedTime(replacement, Files.getLastModifiedTime(path));
        Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        assertThatThrownBy(revision::requireUnchanged).isInstanceOf(SourceFileChangedException.class);
        assertThat(Files.readString(path)).isEqualTo("after!");
    }

    @Test
    void unavailableRootCannotConfirmRemoval(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("source.jpg");
        Files.writeString(path, "synthetic");
        IndexedFile file = indexed(path);
        Files.delete(path);
        Files.delete(directory);
        assertThatThrownBy(() -> SourceFileRevision.requireAbsent(file))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("confirm source removal");
    }

    @Test
    void restoredFileCannotConfirmRemoval(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("source.jpg");
        Files.writeString(path, "synthetic");
        assertThatThrownBy(() -> SourceFileRevision.requireAbsent(indexed(path)))
                .isInstanceOf(SourceFileChangedException.class);
        assertThat(Files.readString(path)).isEqualTo("synthetic");
    }

    @Test
    void sourceMustMatchItsScheduledAttributes(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("source.jpg");
        Files.writeString(path, "before");
        IndexedFile file = indexed(path);
        Files.writeString(path, "changed since scheduling");
        assertThatThrownBy(() -> SourceFileRevision.read(file)).isInstanceOf(SourceFileChangedException.class);
    }

    private static IndexedFile indexed(Path path) throws Exception {
        IndexedFile file = new IndexedFile();
        file.setFullPath(path.toString());
        file.setBasePath(path.getParent().toString());
        file.setSize(Files.size(path));
        file.setLastModifiedDate(new Date(Files.getLastModifiedTime(path).toMillis()));
        return file;
    }
}
