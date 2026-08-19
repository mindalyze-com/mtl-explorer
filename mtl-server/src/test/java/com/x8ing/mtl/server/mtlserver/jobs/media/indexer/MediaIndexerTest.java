package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MediaIndexerTest {

    private static final long FILE_ID = 7L;
    private static final double SYNTHETIC_LATITUDE = 47.3769;
    private static final double SYNTHETIC_LONGITUDE = 8.5417;
    private static final String SYNTHETIC_CAPTURE_TIME = "2026:08:17 12:34:56";
    private static final String EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss";
    private static final Instant SYNTHETIC_GPS_CAPTURE_INSTANT = Instant.parse("2026-08-17T12:34:56Z");
    private static final Instant SYNTHETIC_VIDEO_CAPTURE_INSTANT = Instant.parse("2026-08-17T12:34:56Z");
    private static final long QUICK_TIME_UNIX_EPOCH_OFFSET_SECONDS = 2_082_844_800L;
    private static final byte[] SYNTHETIC_VP8_DATA = HexFormat.of().parseHex(
            "9001009d012a0100010002003425880274ba00039800fef09b43e280523873bf37d8f6980000");

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaIndexer mediaIndexer = new MediaIndexer(mediaRepository);

    @Test
    void indexesWebpAndExtractsExifGpsAndCaptureTime(@TempDir Path tempDirectory) throws IOException {
        Path webpPath = tempDirectory.resolve("synthetic.webp");
        Files.write(webpPath, createSyntheticWebpWithExif());

        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(FILE_ID);
        indexedFile.setName(webpPath.getFileName().toString());
        indexedFile.setFullPath(webpPath.toString());

        mediaIndexer.indexFile(indexedFile);

        ArgumentCaptor<MediaFile> savedMediaCaptor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaRepository).save(savedMediaCaptor.capture());
        MediaFile savedMedia = savedMediaCaptor.getValue();
        assertThat(savedMedia.getIndexedFile()).isSameAs(indexedFile);
        assertThat(savedMedia.getExifGpsLocationLat()).isCloseTo(SYNTHETIC_LATITUDE, within(0.000001));
        assertThat(savedMedia.getExifGpsLocationLong()).isCloseTo(SYNTHETIC_LONGITUDE, within(0.000001));
        assertThat(savedMedia.getExifGpsLocation().getCoordinate().getX())
                .isCloseTo(SYNTHETIC_LONGITUDE, within(0.000001));
        assertThat(savedMedia.getExifGpsLocation().getCoordinate().getY())
                .isCloseTo(SYNTHETIC_LATITUDE, within(0.000001));
        assertThat(savedMedia.getExifGpsDate()).hasSameTimeAs(Date.from(SYNTHETIC_GPS_CAPTURE_INSTANT));
        assertThat(new SimpleDateFormat(EXIF_DATE_PATTERN).format(savedMedia.getExifDateImageTaken()))
                .isEqualTo(SYNTHETIC_CAPTURE_TIME);
        assertThat(savedMedia.getWidthPixels()).isEqualTo(1);
        assertThat(savedMedia.getHeightPixels()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "mp4, 'isom'",
            "m4v, 'M4V '",
            "3gp, '3gp5'",
            "mov, 'qt  '"
    })
    void indexesIsoBaseMediaFormatsAndExtractsContainerCreationTime(
            String extension,
            String majorBrand,
            @TempDir Path tempDirectory) throws IOException {
        Path videoPath = tempDirectory.resolve("synthetic." + extension);
        Files.write(videoPath, createSyntheticIsoBaseMedia(majorBrand, SYNTHETIC_VIDEO_CAPTURE_INSTANT));

        mediaIndexer.indexFile(indexedFile(videoPath));

        MediaFile savedMedia = captureSavedMedia();
        assertThat(savedMedia.getExifDateImageTaken()).hasSameTimeAs(Date.from(SYNTHETIC_VIDEO_CAPTURE_INSTANT));
    }

    @Test
    void indexesMp4AndExtractsEmbeddedLocation(@TempDir Path tempDirectory) throws IOException {
        Path videoPath = tempDirectory.resolve("synthetic-gps.mp4");
        Files.write(videoPath, createSyntheticIsoBaseMediaWithGps(
                "isom",
                SYNTHETIC_VIDEO_CAPTURE_INSTANT,
                SYNTHETIC_LATITUDE,
                SYNTHETIC_LONGITUDE));

        mediaIndexer.indexFile(indexedFile(videoPath));

        MediaFile savedMedia = captureSavedMedia();
        assertThat(savedMedia.getExifGpsLocationLat()).isCloseTo(SYNTHETIC_LATITUDE, within(0.000001));
        assertThat(savedMedia.getExifGpsLocationLong()).isCloseTo(SYNTHETIC_LONGITUDE, within(0.000001));
        assertThat(savedMedia.getExifDateImageTaken()).hasSameTimeAs(Date.from(SYNTHETIC_VIDEO_CAPTURE_INSTANT));
    }

    @Test
    void indexesAviAndExtractsIditCaptureTime(@TempDir Path tempDirectory) throws IOException {
        String aviCaptureTime = "Mon Aug 17 12:34:56 2026";
        Path videoPath = tempDirectory.resolve("synthetic.avi");
        Files.write(videoPath, createSyntheticAvi(aviCaptureTime));

        mediaIndexer.indexFile(indexedFile(videoPath));

        Date expected = Date.from(LocalDateTime.of(2026, 8, 17, 12, 34, 56)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        assertThat(captureSavedMedia().getExifDateImageTaken()).hasSameTimeAs(expected);
    }

    @Test
    void indexesBroadVideoContainerWithoutInspectingItsContents(@TempDir Path tempDirectory) throws IOException {
        Path videoPath = tempDirectory.resolve("synthetic.mkv");
        Files.writeString(videoPath, "synthetic Matroska placeholder");
        IndexedFile indexedFile = indexedFile(videoPath);

        mediaIndexer.indexFile(indexedFile);

        MediaFile savedMedia = captureSavedMedia();
        assertThat(savedMedia.getIndexedFile()).isSameAs(indexedFile);
        assertThat(savedMedia.getExifDateImageTaken()).isNull();
        assertThat(savedMedia.getVideoCodec()).isNull();
    }

    @Test
    void refreshesExistingMediaWithoutReplacingItsIdentity(@TempDir Path tempDirectory) throws IOException {
        Path videoPath = tempDirectory.resolve("synthetic.mp4");
        Files.write(videoPath, createSyntheticIsoBaseMedia("isom", SYNTHETIC_VIDEO_CAPTURE_INSTANT));
        IndexedFile indexedFile = indexedFile(videoPath);
        MediaFile existingMedia = new MediaFile();
        existingMedia.setId(400_007L);
        existingMedia.setIndexedFile(indexedFile);
        existingMedia.setCameraMake("Existing camera");
        existingMedia.setWidthPixels(1920);
        when(mediaRepository.findAllByIndexedFileId(FILE_ID)).thenReturn(List.of(existingMedia));

        mediaIndexer.refreshFile(indexedFile);

        verify(mediaRepository).save(existingMedia);
        verify(mediaRepository, never()).deleteByIndexedFileId(FILE_ID);
        assertThat(existingMedia.getId()).isEqualTo(400_007L);
        assertThat(existingMedia.getExifDateImageTaken())
                .hasSameTimeAs(Date.from(SYNTHETIC_VIDEO_CAPTURE_INSTANT));
        assertThat(existingMedia.getCameraMake()).isNull();
        assertThat(existingMedia.getWidthPixels()).isNull();
    }

    @Test
    void leavesExistingMediaUntouchedWhenMetadataCannotBeRead() {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(FILE_ID);
        indexedFile.setName("unreadable.mp4");
        indexedFile.setFullPath("\0");
        MediaFile existingMedia = new MediaFile();
        existingMedia.setId(400_007L);
        existingMedia.setIndexedFile(indexedFile);
        existingMedia.setCameraMake("Existing camera");
        when(mediaRepository.findAllByIndexedFileId(FILE_ID)).thenReturn(List.of(existingMedia));

        assertThatThrownBy(() -> mediaIndexer.refreshFile(indexedFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not extract metadata");

        verify(mediaRepository, never()).save(any(MediaFile.class));
        verify(mediaRepository, never()).deleteByIndexedFileId(FILE_ID);
        assertThat(existingMedia.getId()).isEqualTo(400_007L);
        assertThat(existingMedia.getCameraMake()).isEqualTo("Existing camera");
    }

    @Test
    void deletesEveryDerivedRowForIndexedFile() {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(FILE_ID);
        indexedFile.setName("synthetic.jpg");
        when(mediaRepository.deleteByIndexedFileId(FILE_ID)).thenReturn(2);

        mediaIndexer.deleteFilesForIndexedFile(indexedFile);

        verify(mediaRepository).deleteByIndexedFileId(FILE_ID);
    }

    @Test
    void ignoresIndexedFileWithoutId() {
        mediaIndexer.deleteFilesForIndexedFile(new IndexedFile());

        verifyNoInteractions(mediaRepository);
    }

    @Test
    void storesLongitudeAndLatitudeInTheCorrectScalarColumnsAndGeometryOrder() {
        MediaFile mediaFile = new MediaFile();

        MediaIndexer.setGpsLocation(mediaFile, 47.3769, 8.5417);

        assertThat(mediaFile.getExifGpsLocationLat()).isEqualTo(47.3769);
        assertThat(mediaFile.getExifGpsLocationLong()).isEqualTo(8.5417);
        assertThat(mediaFile.getExifGpsLocation().getCoordinate().getX()).isEqualTo(8.5417);
        assertThat(mediaFile.getExifGpsLocation().getCoordinate().getY()).isEqualTo(47.3769);
    }

    private static IndexedFile indexedFile(Path mediaPath) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(FILE_ID);
        indexedFile.setName(mediaPath.getFileName().toString());
        indexedFile.setFullPath(mediaPath.toString());
        return indexedFile;
    }

    private MediaFile captureSavedMedia() {
        ArgumentCaptor<MediaFile> savedMediaCaptor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaRepository).save(savedMediaCaptor.capture());
        return savedMediaCaptor.getValue();
    }

    private static byte[] createSyntheticIsoBaseMedia(String majorBrand, Instant creationTime) throws IOException {
        return createSyntheticIsoBaseMedia(majorBrand, creationTime, null);
    }

    private static byte[] createSyntheticIsoBaseMediaWithGps(
            String majorBrand,
            Instant creationTime,
            double latitude,
            double longitude) throws IOException {
        String iso6709 = String.format(java.util.Locale.ROOT, "%+09.5f%+010.5f/", latitude, longitude);
        byte[] locationBytes = iso6709.getBytes(StandardCharsets.UTF_8);
        ByteBuffer locationPayload = ByteBuffer.allocate(4 + locationBytes.length).order(ByteOrder.BIG_ENDIAN);
        locationPayload.putShort((short) locationBytes.length);
        locationPayload.putShort((short) 0);
        locationPayload.put(locationBytes);
        return createSyntheticIsoBaseMedia(
                majorBrand,
                creationTime,
                isoBox("\u00a9xyz", locationPayload.array()));
    }

    private static byte[] createSyntheticIsoBaseMedia(
            String majorBrand,
            Instant creationTime,
            byte[] userDataPayload) throws IOException {
        long quickTimeSeconds = creationTime.getEpochSecond() + QUICK_TIME_UNIX_EPOCH_OFFSET_SECONDS;
        ByteBuffer movieHeaderPayload = ByteBuffer.allocate(100).order(ByteOrder.BIG_ENDIAN);
        movieHeaderPayload.putInt(0); // version and flags
        movieHeaderPayload.putInt((int) quickTimeSeconds);
        movieHeaderPayload.putInt((int) quickTimeSeconds);
        movieHeaderPayload.putInt(1_000); // time scale
        movieHeaderPayload.putInt(1_000); // duration
        movieHeaderPayload.putInt(0x0001_0000); // preferred rate 1.0
        movieHeaderPayload.putShort((short) 0x0100); // preferred volume 1.0
        movieHeaderPayload.put(new byte[10]);
        movieHeaderPayload.putInt(0x0001_0000).putInt(0).putInt(0);
        movieHeaderPayload.putInt(0).putInt(0x0001_0000).putInt(0);
        movieHeaderPayload.putInt(0).putInt(0).putInt(0x4000_0000);
        for (int index = 0; index < 6; index++) movieHeaderPayload.putInt(0);
        movieHeaderPayload.putInt(1); // next track id

        byte[] fileTypePayload = ByteBuffer.allocate(12)
                .order(ByteOrder.BIG_ENDIAN)
                .put(majorBrand.getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put(majorBrand.getBytes(StandardCharsets.US_ASCII))
                .array();
        byte[] fileTypeBox = isoBox("ftyp", fileTypePayload);
        ByteArrayOutputStream moviePayload = new ByteArrayOutputStream();
        moviePayload.write(isoBox("mvhd", movieHeaderPayload.array()));
        if (userDataPayload != null) moviePayload.write(isoBox("udta", userDataPayload));
        byte[] movieBox = isoBox("moov", moviePayload.toByteArray());

        ByteArrayOutputStream video = new ByteArrayOutputStream();
        video.write(fileTypeBox);
        video.write(movieBox);
        return video.toByteArray();
    }

    private static byte[] isoBox(String type, byte[] payload) throws IOException {
        ByteArrayOutputStream box = new ByteArrayOutputStream();
        writeBigEndianInt(box, payload.length + 8);
        box.write(type.getBytes(StandardCharsets.ISO_8859_1));
        box.write(payload);
        return box.toByteArray();
    }

    private static byte[] createSyntheticAvi(String captureTime) throws IOException {
        byte[] captureTimeBytes = captureTime.getBytes(StandardCharsets.US_ASCII);
        int iditPayloadSize = captureTimeBytes.length + 2;
        ByteArrayOutputStream riffPayload = new ByteArrayOutputStream();
        riffPayload.write("AVI ".getBytes(StandardCharsets.US_ASCII));
        riffPayload.write("IDIT".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(riffPayload, iditPayloadSize);
        riffPayload.write(captureTimeBytes);
        riffPayload.write('\n');
        riffPayload.write(0);
        if ((iditPayloadSize & 1) != 0) riffPayload.write(0);

        ByteArrayOutputStream avi = new ByteArrayOutputStream();
        avi.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(avi, riffPayload.size());
        riffPayload.writeTo(avi);
        return avi.toByteArray();
    }

    private static byte[] createSyntheticWebpWithExif() throws IOException {
        byte[] exifData = createExifTiff();
        ByteArrayOutputStream riffPayload = new ByteArrayOutputStream();
        riffPayload.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        writeRiffChunk(riffPayload, "VP8X", new byte[]{8, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        writeRiffChunk(riffPayload, "VP8 ", SYNTHETIC_VP8_DATA);
        writeRiffChunk(riffPayload, "EXIF", exifData);

        ByteArrayOutputStream webp = new ByteArrayOutputStream();
        webp.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(webp, riffPayload.size());
        riffPayload.writeTo(webp);
        return webp.toByteArray();
    }

    private static byte[] createExifTiff() {
        final int ifd0Offset = 8;
        final int ifd0DateOffset = 50;
        final int exifIfdOffset = 70;
        final int exifDateOffset = 88;
        final int gpsIfdOffset = 108;
        final int latitudeOffset = 186;
        final int longitudeOffset = 210;
        final int gpsTimeOffset = 234;
        final int gpsDateOffset = 258;
        final int tiffSize = 269;

        ByteBuffer tiff = ByteBuffer.allocate(tiffSize).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I');
        tiff.putShort((short) 42);
        tiff.putInt(ifd0Offset);

        tiff.position(ifd0Offset);
        tiff.putShort((short) 3);
        writeIfdEntry(tiff, 0x0132, 2, 20, ifd0DateOffset);
        writeIfdEntry(tiff, 0x8769, 4, 1, exifIfdOffset);
        writeIfdEntry(tiff, 0x8825, 4, 1, gpsIfdOffset);
        tiff.putInt(0);
        putNullTerminatedAscii(tiff, ifd0DateOffset, SYNTHETIC_CAPTURE_TIME);

        tiff.position(exifIfdOffset);
        tiff.putShort((short) 1);
        writeIfdEntry(tiff, 0x9003, 2, 20, exifDateOffset);
        tiff.putInt(0);
        putNullTerminatedAscii(tiff, exifDateOffset, SYNTHETIC_CAPTURE_TIME);

        tiff.position(gpsIfdOffset);
        tiff.putShort((short) 6);
        writeIfdEntry(tiff, 0x0001, 2, 2, 'N');
        writeIfdEntry(tiff, 0x0002, 5, 3, latitudeOffset);
        writeIfdEntry(tiff, 0x0003, 2, 2, 'E');
        writeIfdEntry(tiff, 0x0004, 5, 3, longitudeOffset);
        writeIfdEntry(tiff, 0x0007, 5, 3, gpsTimeOffset);
        writeIfdEntry(tiff, 0x001D, 2, 11, gpsDateOffset);
        tiff.putInt(0);

        tiff.position(latitudeOffset);
        putRational(tiff, 47, 1);
        putRational(tiff, 22, 1);
        putRational(tiff, 3_684, 100);
        tiff.position(longitudeOffset);
        putRational(tiff, 8, 1);
        putRational(tiff, 32, 1);
        putRational(tiff, 3_012, 100);
        tiff.position(gpsTimeOffset);
        putRational(tiff, 12, 1);
        putRational(tiff, 34, 1);
        putRational(tiff, 56, 1);
        putNullTerminatedAscii(tiff, gpsDateOffset, "2026:08:17");
        return tiff.array();
    }

    private static void writeIfdEntry(ByteBuffer target, int tag, int type, int count, int valueOrOffset) {
        target.putShort((short) tag);
        target.putShort((short) type);
        target.putInt(count);
        target.putInt(valueOrOffset);
    }

    private static void putNullTerminatedAscii(ByteBuffer target, int offset, String value) {
        target.position(offset);
        target.put(value.getBytes(StandardCharsets.US_ASCII));
        target.put((byte) 0);
    }

    private static void putRational(ByteBuffer target, int numerator, int denominator) {
        target.putInt(numerator);
        target.putInt(denominator);
    }

    private static void writeRiffChunk(ByteArrayOutputStream target, String type, byte[] data) throws IOException {
        target.write(type.getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(target, data.length);
        target.write(data);
        if ((data.length & 1) != 0) {
            target.write(0);
        }
    }

    private static void writeLittleEndianInt(ByteArrayOutputStream target, int value) {
        target.write(value & 0xff);
        target.write((value >>> 8) & 0xff);
        target.write((value >>> 16) & 0xff);
        target.write((value >>> 24) & 0xff);
    }

    private static void writeBigEndianInt(ByteArrayOutputStream target, int value) {
        target.write((value >>> 24) & 0xff);
        target.write((value >>> 16) & 0xff);
        target.write((value >>> 8) & 0xff);
        target.write(value & 0xff);
    }
}
