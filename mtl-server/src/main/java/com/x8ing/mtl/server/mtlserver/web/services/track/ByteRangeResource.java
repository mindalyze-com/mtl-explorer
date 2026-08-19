package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.springframework.core.io.AbstractResource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** A repeatable file resource that exposes only one byte range. */
final class ByteRangeResource extends AbstractResource {

    private final Path path;
    private final long position;
    private final long byteCount;

    ByteRangeResource(Path path, long position, long byteCount) {
        this.path = path;
        this.position = position;
        this.byteCount = byteCount;
    }

    @Override
    public String getDescription() {
        return "byte range " + position + "+" + byteCount + " of " + path;
    }

    @Override
    public String getFilename() {
        return path.getFileName().toString();
    }

    @Override
    public long contentLength() {
        return byteCount;
    }

    @Override
    public long lastModified() throws IOException {
        return path.toFile().lastModified();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        try {
            channel.position(position);
            return new LimitedInputStream(Channels.newInputStream(channel), byteCount);
        } catch (RuntimeException | IOException e) {
            channel.close();
            throw e;
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        private LimitedInputStream(InputStream input, long byteCount) {
            super(input);
            remaining = byteCount;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) return -1;
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public long skip(long byteCount) throws IOException {
            long skipped = super.skip(Math.min(byteCount, remaining));
            remaining -= skipped;
            return skipped;
        }
    }
}
