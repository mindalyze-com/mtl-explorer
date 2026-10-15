package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * Source identity checks shared by transactional GPS and media consumers.
 */
public record SourceFileRevision(Path path, long size, FileTime modified, Object fileKey) {
    public static SourceFileRevision read(IndexedFile file) {
        Path path = Path.of(file.getFullPath());
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()
                || file.getSize() != null && file.getSize() != attributes.size()
                || file.getLastModifiedDate() != null && file.getLastModifiedDate().getTime() != attributes.lastModifiedTime().toMillis()) {
                throw changed(path);
            }
            return new SourceFileRevision(path, attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
        } catch (NoSuchFileException exception) {
            throw changed(path);
        } catch (IOException exception) {
            throw new SourceUnreadableException(exception);
        }
    }

    public void requireUnchanged() {
        try {
            BasicFileAttributes current = Files.readAttributes(path, BasicFileAttributes.class);
            if (!current.isRegularFile() || current.size() != size || !current.lastModifiedTime().equals(modified)
                || !Objects.equals(current.fileKey(), fileKey)) throw changed(path);
        } catch (NoSuchFileException exception) {
            throw changed(path);
        } catch (IOException exception) {
            throw new SourceUnreadableException(exception);
        }
    }

    public static BasicFileAttributes readAttributes(Path root, Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException exception) {
            // A missing source is authoritative only while its configured root is accessible.
            requireAvailableRoot(root);
            return null;
        }
    }

    public static void requireAvailableRoot(Path root) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(root, BasicFileAttributes.class);
        if (!attributes.isDirectory()) throw new IOException("Source root is not a directory: " + root);
        try (var directory = Files.newDirectoryStream(root)) {
            directory.iterator().hasNext();
        }
    }

    public static void requireAbsent(IndexedFile file) {
        Path path = Path.of(file.getFullPath());
        try {
            if (readAttributes(Path.of(file.getBasePath()), path) != null) throw changed(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not confirm source removal: " + path, exception);
        }
    }

    private static SourceFileChangedException changed(Path path) {
        return new SourceFileChangedException("Source changed during processing: " + path.getFileName());
    }
}
