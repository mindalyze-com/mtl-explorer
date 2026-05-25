package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackFileExportServiceTest {

    private static final long TRACK_ID = 42L;

    @TempDir
    private Path gpsRoot;

    @Mock
    private GpsTrackRepository gpsTrackRepository;

    @Mock
    private TrackFileConverterService converterService;

    private TrackFileExportService service;

    @BeforeEach
    void setUp() {
        service = new TrackFileExportService(gpsTrackRepository, converterService, gpsRoot.toString());
    }

    @Test
    void sourceFileStreamsOriginalIndexedFile() throws Exception {
        Path sourceFile = writeSourceFile("activity.fit", "fit-bytes");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));

        TrackFileExportService.TrackFileDownload download = service.sourceFile(TRACK_ID);

        assertThat(download.getFileName()).isEqualTo("activity.fit");
        assertThat(download.getMediaType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(download.getContentLength()).isEqualTo("fit-bytes".length());
        assertThat(resourceText(download)).isEqualTo("fit-bytes");
    }

    @Test
    void gpxDownloadStreamsNativeGpxSource() throws Exception {
        Path sourceFile = writeSourceFile("activity.gpx", "<gpx />");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));

        TrackFileExportService.TrackFileDownload download = service.gpx(TRACK_ID);

        assertThat(download.getFileName()).isEqualTo("activity.gpx");
        assertThat(download.getMediaType()).isEqualTo(TrackFileExportService.GPX_MEDIA_TYPE);
        assertThat(resourceText(download)).isEqualTo("<gpx />");
    }

    @Test
    void gpxDownloadConvertsSupportedNonGpxSource() throws Exception {
        Path sourceFile = writeSourceFile("activity.fit", "fit-bytes");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));
        when(converterService.convertToGpx(sourceFile.toRealPath(), SupportedTrackFormat.FIT)).thenReturn("<gpx />");

        TrackFileExportService.TrackFileDownload download = service.gpx(TRACK_ID);

        assertThat(download.getFileName()).isEqualTo("activity.gpx");
        assertThat(download.getMediaType()).isEqualTo(TrackFileExportService.GPX_MEDIA_TYPE);
        assertThat(resourceText(download)).isEqualTo("<gpx />");
        verify(converterService).convertToGpx(sourceFile.toRealPath(), SupportedTrackFormat.FIT);
    }

    @Test
    void missingTrackReturnsNotFound() {
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.empty());

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void trackWithoutIndexedFileReturnsNotFound() {
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(new GpsTrack()));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void nonGpsIndexedFileReturnsNotFound() throws Exception {
        Path sourceFile = writeSourceFile("activity.gpx", "<gpx />");
        IndexedFile indexedFile = indexedFile(sourceFile);
        indexedFile.setIndex("MEDIA");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile)));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void removedIndexedFileReturnsNotFound() throws Exception {
        Path sourceFile = writeSourceFile("activity.gpx", "<gpx />");
        IndexedFile indexedFile = indexedFile(sourceFile);
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.REMOVED);
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile)));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void excludedIndexedFileReturnsNotFound() throws Exception {
        Path sourceFile = writeSourceFile("activity.gpx", "<gpx />");
        IndexedFile indexedFile = indexedFile(sourceFile);
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.EXCLUDED);
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile)));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void missingDiskFileReturnsNotFound() {
        Path sourceFile = gpsRoot.resolve("missing.gpx");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void pathOutsideGpsRootReturnsNotFound(@TempDir Path outsideRoot) throws Exception {
        Path sourceFile = outsideRoot.resolve("activity.gpx");
        Files.writeString(sourceFile, "<gpx />");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.NOT_FOUND);
    }

    @Test
    void unsupportedSourceFormatReturnsUnprocessableEntity() throws Exception {
        Path sourceFile = writeSourceFile("activity.txt", "not a track");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));

        assertStatus(() -> service.sourceFile(TRACK_ID), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void converterFailureReturnsServerError() throws Exception {
        Path sourceFile = writeSourceFile("activity.fit", "fit-bytes");
        when(gpsTrackRepository.findById(TRACK_ID)).thenReturn(Optional.of(track(indexedFile(sourceFile))));
        when(converterService.convertToGpx(sourceFile.toRealPath(), SupportedTrackFormat.FIT))
                .thenThrow(new IOException("gpsbabel failed"));

        assertStatus(() -> service.gpx(TRACK_ID), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Path writeSourceFile(String name, String content) throws IOException {
        Path sourceFile = gpsRoot.resolve(name);
        Files.writeString(sourceFile, content);
        return sourceFile;
    }

    private static GpsTrack track(IndexedFile indexedFile) {
        GpsTrack track = new GpsTrack();
        track.setId(TRACK_ID);
        track.setIndexedFile(indexedFile);
        return track;
    }

    private IndexedFile indexedFile(Path sourceFile) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setId(7L);
        indexedFile.setIndex(GPXDirectoryWatcherService.INDEX_GPS);
        indexedFile.setName(sourceFile.getFileName().toString());
        indexedFile.setBasePath(gpsRoot.toString());
        indexedFile.setPath("");
        indexedFile.setFullPath(sourceFile.toString());
        indexedFile.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        return indexedFile;
    }

    private static String resourceText(TrackFileExportService.TrackFileDownload download) throws IOException {
        return new String(download.getResource().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void assertStatus(Runnable operation, HttpStatus expectedStatus) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expectedStatus));
    }
}
