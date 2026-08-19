package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCaptureDateResolverTest {

    private static final Instant HEADER_CAPTURE_INSTANT = Instant.parse("2025-05-04T03:02:01Z");
    private static final Instant EXPLICIT_CAPTURE_INSTANT = Instant.parse("2026-08-17T10:34:56Z");

    @Test
    void prefersExplicitQuickTimeCreationDateOverContainerHeader() {
        Metadata metadata = new Metadata();
        Mp4Directory containerDirectory = new Mp4Directory();
        containerDirectory.setDate(Mp4Directory.TAG_CREATION_TIME, Date.from(HEADER_CAPTURE_INSTANT));
        metadata.addDirectory(containerDirectory);
        QuickTimeMetadataDirectory quickTimeMetadata = new QuickTimeMetadataDirectory();
        quickTimeMetadata.setString(
                QuickTimeMetadataDirectory.TAG_CREATION_DATE,
                "2026-08-17T12:34:56+02:00");
        metadata.addDirectory(quickTimeMetadata);

        assertThat(MediaCaptureDateResolver.resolve(metadata)).hasSameTimeAs(Date.from(EXPLICIT_CAPTURE_INSTANT));
    }

    @Test
    void readsAviIditLocalCaptureTime() {
        Metadata metadata = new Metadata();
        AviDirectory aviDirectory = new AviDirectory();
        aviDirectory.setString(AviDirectory.TAG_DATETIME_ORIGINAL, "Mon Aug 17 12:34:56 2026");
        metadata.addDirectory(aviDirectory);
        Date expected = Date.from(LocalDateTime.of(2026, 8, 17, 12, 34, 56)
                .atZone(ZoneId.systemDefault())
                .toInstant());

        assertThat(MediaCaptureDateResolver.resolve(metadata)).hasSameTimeAs(expected);
    }

    @Test
    void readsXmpCreateDateFallback() throws XMPException {
        Metadata metadata = new Metadata();
        XmpDirectory xmpDirectory = new XmpDirectory();
        xmpDirectory.getXMPMeta().setProperty(
                XMPConst.NS_XMP,
                "CreateDate",
                "2026-08-17T12:34:56+02:00");
        metadata.addDirectory(xmpDirectory);

        assertThat(MediaCaptureDateResolver.resolve(metadata)).hasSameTimeAs(Date.from(EXPLICIT_CAPTURE_INSTANT));
    }

    @Test
    void ignoresUnsetQuickTimeEpoch() {
        Metadata metadata = new Metadata();
        QuickTimeDirectory quickTimeDirectory = new QuickTimeDirectory();
        quickTimeDirectory.setDate(
                QuickTimeDirectory.TAG_CREATION_TIME,
                Date.from(Instant.parse("1904-01-01T00:00:00Z")));
        metadata.addDirectory(quickTimeDirectory);

        assertThat(MediaCaptureDateResolver.resolve(metadata)).isNull();
    }
}
