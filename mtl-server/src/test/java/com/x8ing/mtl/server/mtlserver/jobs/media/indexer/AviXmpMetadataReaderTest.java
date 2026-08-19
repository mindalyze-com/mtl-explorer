package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AviXmpMetadataReaderTest {

    private static final double LATITUDE = 47.3649;
    private static final double LONGITUDE = 8.5158;
    private static final Instant CAPTURE_TIME = Instant.parse("2026-08-18T18:09:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void readsEmbeddedGpsAndCaptureTimeFromAviXmpChunk() throws IOException {
        byte[] xmp = ("""
                <?xpacket begin='' id='W5M0MpCehiHzreSzNTczkc9d'?>
                <x:xmpmeta xmlns:x='adobe:ns:meta/'>
                  <rdf:RDF xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>
                    <rdf:Description rdf:about=''
                      xmlns:exif='http://ns.adobe.com/exif/1.0/'
                      xmlns:xmp='http://ns.adobe.com/xap/1.0/'
                      exif:GPSLatitude='47.3649'
                      exif:GPSLongitude='8.5158'
                      xmp:CreateDate='2026-08-18T18:09:00Z'/>
                  </rdf:RDF>
                </x:xmpmeta>
                <?xpacket end='w'?>
                """).getBytes(StandardCharsets.UTF_8);
        Path avi = tempDirectory.resolve("embedded-gps.avi");
        Files.write(avi, minimalAviWithXmp(xmp));

        Metadata metadata = new Metadata();
        AviXmpMetadataReader.enrich(avi, metadata);

        assertThat(MediaGpsLocationResolver.resolve(metadata))
                .isEqualTo(new MediaGpsLocationResolver.Location(LATITUDE, LONGITUDE));
        assertThat(MediaCaptureDateResolver.resolve(metadata)).hasSameTimeAs(Date.from(CAPTURE_TIME));
    }

    private static byte[] minimalAviWithXmp(byte[] xmp) throws IOException {
        ByteArrayOutputStream riffPayload = new ByteArrayOutputStream();
        riffPayload.write("AVI ".getBytes(StandardCharsets.US_ASCII));
        riffPayload.write("_PMX".getBytes(StandardCharsets.US_ASCII));
        riffPayload.write(littleEndianInt(xmp.length));
        riffPayload.write(xmp);
        if ((xmp.length & 1) == 1) riffPayload.write(0);

        ByteArrayOutputStream avi = new ByteArrayOutputStream();
        avi.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        avi.write(littleEndianInt(riffPayload.size()));
        riffPayload.writeTo(avi);
        return avi.toByteArray();
    }

    private static byte[] littleEndianInt(int value) {
        return ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }
}
