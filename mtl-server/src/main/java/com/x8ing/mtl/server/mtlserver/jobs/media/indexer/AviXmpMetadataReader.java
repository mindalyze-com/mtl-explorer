package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpReader;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

/** Reads Adobe XMP stored in an AVI/WAV {@code _PMX} RIFF chunk. */
final class AviXmpMetadataReader {

    private static final String RIFF_FOUR_CC = "RIFF";
    private static final String AVI_FOUR_CC = "AVI ";
    private static final String XMP_FOUR_CC = "_PMX";
    private static final int RIFF_HEADER_SIZE = 12;
    private static final int CHUNK_HEADER_SIZE = 8;
    private static final int MAX_XMP_BYTES = 4 * 1024 * 1024;

    private AviXmpMetadataReader() {
    }

    static void enrich(Path file, Metadata metadata) throws IOException {
        try (FileChannel channel = FileChannel.open(file, READ)) {
            if (channel.size() < RIFF_HEADER_SIZE) return;

            ByteBuffer riffHeader = read(channel, 0, RIFF_HEADER_SIZE);
            if (!RIFF_FOUR_CC.equals(readFourCc(riffHeader))) return;
            long declaredRiffSize = Integer.toUnsignedLong(riffHeader.getInt());
            if (!AVI_FOUR_CC.equals(readFourCc(riffHeader))) return;

            long riffEnd = Math.min(channel.size(), 8L + declaredRiffSize);
            long position = RIFF_HEADER_SIZE;
            while (position + CHUNK_HEADER_SIZE <= riffEnd) {
                ByteBuffer chunkHeader = read(channel, position, CHUNK_HEADER_SIZE);
                String chunkFourCc = readFourCc(chunkHeader);
                long chunkSize = Integer.toUnsignedLong(chunkHeader.getInt());
                long payloadPosition = position + CHUNK_HEADER_SIZE;
                long paddedChunkSize = chunkSize + (chunkSize & 1L);
                long nextPosition = payloadPosition + paddedChunkSize;
                if (nextPosition < payloadPosition || nextPosition > riffEnd) return;

                if (XMP_FOUR_CC.equals(chunkFourCc)) {
                    if (chunkSize == 0 || chunkSize > MAX_XMP_BYTES) return;
                    ByteBuffer xmp = read(channel, payloadPosition, Math.toIntExact(chunkSize));
                    new XmpReader().extract(xmp.array(), metadata);
                    return;
                }
                position = nextPosition;
            }
        }
    }

    private static ByteBuffer read(FileChannel channel, long position, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(position);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw new EOFException("Truncated AVI RIFF metadata");
        }
        buffer.flip();
        return buffer;
    }

    private static String readFourCc(ByteBuffer buffer) {
        byte[] value = new byte[4];
        buffer.get(value);
        return new String(value, StandardCharsets.US_ASCII);
    }
}
