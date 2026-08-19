package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.drew.metadata.Metadata;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4SoundDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTechnicalMetadataResolverTest {

    @Test
    void resolvesPhotoDimensionsExposureLensAndAltitude() {
        Metadata metadata = new Metadata();
        ExifSubIFDDirectory exif = new ExifSubIFDDirectory();
        exif.setInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH, 4032);
        exif.setInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT, 3024);
        exif.setDouble(ExifSubIFDDirectory.TAG_FNUMBER, 1.8);
        exif.setDouble(ExifSubIFDDirectory.TAG_EXPOSURE_TIME, 1.0 / 250.0);
        exif.setInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT, 50);
        exif.setDouble(ExifSubIFDDirectory.TAG_FOCAL_LENGTH, 6.86);
        exif.setInt(ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH, 26);
        exif.setString(ExifSubIFDDirectory.TAG_LENS_MODEL, "Example 6.86mm f/1.8");
        metadata.addDirectory(exif);

        GpsDirectory gps = new GpsDirectory();
        gps.setDouble(GpsDirectory.TAG_ALTITUDE, 488.4);
        gps.setInt(GpsDirectory.TAG_ALTITUDE_REF, 0);
        metadata.addDirectory(gps);

        MediaTechnicalMetadataResolver.TechnicalMetadata result =
                MediaTechnicalMetadataResolver.resolve(metadata);

        assertThat(result.widthPixels()).isEqualTo(4032);
        assertThat(result.heightPixels()).isEqualTo(3024);
        assertThat(result.apertureFNumber()).isEqualTo(1.8);
        assertThat(result.exposureTimeSeconds()).isEqualTo(1.0 / 250.0);
        assertThat(result.isoSpeed()).isEqualTo(50);
        assertThat(result.focalLengthMm()).isEqualTo(6.86);
        assertThat(result.focalLength35Mm()).isEqualTo(26);
        assertThat(result.lensModel()).isEqualTo("Example 6.86mm f/1.8");
        assertThat(result.gpsAltitudeMeters()).isEqualTo(488.4);
    }

    @Test
    void resolvesMp4VideoAndAudioDetails() {
        Metadata metadata = new Metadata();
        Mp4Directory container = new Mp4Directory();
        container.setDouble(Mp4Directory.TAG_DURATION_SECONDS, 84.25);
        metadata.addDirectory(container);

        Mp4VideoDirectory video = new Mp4VideoDirectory();
        video.setInt(Mp4VideoDirectory.TAG_WIDTH, 3840);
        video.setInt(Mp4VideoDirectory.TAG_HEIGHT, 2160);
        video.setDouble(Mp4VideoDirectory.TAG_FRAME_RATE, 29.97);
        video.setString(Mp4VideoDirectory.TAG_COMPRESSION_TYPE, "hvc1");
        metadata.addDirectory(video);

        Mp4SoundDirectory audio = new Mp4SoundDirectory();
        audio.setString(Mp4SoundDirectory.TAG_AUDIO_FORMAT, "mp4a");
        metadata.addDirectory(audio);

        MediaTechnicalMetadataResolver.TechnicalMetadata result =
                MediaTechnicalMetadataResolver.resolve(metadata);

        assertThat(result.widthPixels()).isEqualTo(3840);
        assertThat(result.heightPixels()).isEqualTo(2160);
        assertThat(result.durationSeconds()).isEqualTo(84.25);
        assertThat(result.frameRate()).isEqualTo(29.97);
        assertThat(result.videoCodec()).isEqualTo("hvc1");
        assertThat(result.audioCodec()).isEqualTo("mp4a");
    }

    @Test
    void resolvesAviClockDurationAndBelowSeaLevelAltitude() {
        Metadata metadata = new Metadata();
        AviDirectory avi = new AviDirectory();
        avi.setString(AviDirectory.TAG_DURATION, "01:02:03");
        metadata.addDirectory(avi);

        GpsDirectory gps = new GpsDirectory();
        gps.setDouble(GpsDirectory.TAG_ALTITUDE, 12.5);
        gps.setInt(GpsDirectory.TAG_ALTITUDE_REF, 1);
        metadata.addDirectory(gps);

        MediaTechnicalMetadataResolver.TechnicalMetadata result =
                MediaTechnicalMetadataResolver.resolve(metadata);

        assertThat(result.durationSeconds()).isEqualTo(3723.0);
        assertThat(result.gpsAltitudeMeters()).isEqualTo(-12.5);
    }
}
