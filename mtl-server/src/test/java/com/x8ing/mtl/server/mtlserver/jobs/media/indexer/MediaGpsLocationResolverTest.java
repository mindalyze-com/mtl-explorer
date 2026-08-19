package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.drew.metadata.Metadata;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.xmp.XmpDirectory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaGpsLocationResolverTest {

    private static final double LATITUDE = 46.94833;
    private static final double LONGITUDE = 7.44785;

    @Test
    void resolvesMp4LatitudeAndLongitude() {
        Metadata metadata = new Metadata();
        Mp4Directory directory = new Mp4Directory();
        directory.setDouble(Mp4Directory.TAG_LATITUDE, LATITUDE);
        directory.setDouble(Mp4Directory.TAG_LONGITUDE, LONGITUDE);
        metadata.addDirectory(directory);

        assertThat(MediaGpsLocationResolver.resolve(metadata))
                .isEqualTo(new MediaGpsLocationResolver.Location(LATITUDE, LONGITUDE));
    }

    @Test
    void resolvesQuickTimeIso6709WithAltitude() {
        Metadata metadata = new Metadata();
        QuickTimeMetadataDirectory directory = new QuickTimeMetadataDirectory();
        directory.setString(
                QuickTimeMetadataDirectory.TAG_LOCATION_ISO6709,
                "+46.94833+007.44785+0550.0/");
        metadata.addDirectory(directory);

        assertThat(MediaGpsLocationResolver.resolve(metadata))
                .isEqualTo(new MediaGpsLocationResolver.Location(LATITUDE, LONGITUDE));
    }

    @Test
    void resolvesXmpExifCoordinatesEmbeddedInAvi() throws XMPException {
        Metadata metadata = new Metadata();
        XmpDirectory directory = new XmpDirectory();
        directory.getXMPMeta().setProperty(XMPConst.NS_EXIF, "GPSLatitude", "46.94833");
        directory.getXMPMeta().setProperty(XMPConst.NS_EXIF, "GPSLongitude", "7.44785");
        metadata.addDirectory(directory);

        assertThat(MediaGpsLocationResolver.resolve(metadata))
                .isEqualTo(new MediaGpsLocationResolver.Location(LATITUDE, LONGITUDE));
    }

    @Test
    void rejectsInvalidOrOutOfRangeCoordinates() {
        assertThat(MediaGpsLocationResolver.parseIso6709("46.94833,7.44785")).isNull();
        assertThat(MediaGpsLocationResolver.parseIso6709("+91.00000+007.44785/")).isNull();
        assertThat(MediaGpsLocationResolver.parseIso6709("+46.94833+181.00000/")).isNull();
    }
}
