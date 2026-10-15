package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.SupportedTrackFormat;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrackSourceConversionModeTest {
    @Test
    void importAndInteractiveAccessRouteToTheirOwnLimitsWithTheSameConvertedBytes() throws Exception {
        var converter = mock(TrackFileConverterService.class);
        var access = new TrackSourceAccess(mock(IndexerRepository.class), converter, "/tmp");
        var file = new IndexedFile();
        file.setId(1L);
        byte[] original = "synthetic source".getBytes(StandardCharsets.UTF_8);
        var source = new TrackSourceAccess.Source(file, Path.of("/tmp/synthetic.tcx"), original,
                GpxDocument.hash(original), SupportedTrackFormat.TCX);
        String xml = "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"synthetic\"/>";
        when(converter.convertToGpx(source.path(), source.format())).thenReturn(xml);
        when(converter.convertToGpxForImport(source.path(), source.format())).thenReturn(xml);

        assertThat(access.gpx(source)).isEqualTo(access.gpxForImport(source)).isEqualTo(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(source.bytes()).isEqualTo(original);
        verify(converter).convertToGpx(source.path(), source.format());
        verify(converter).convertToGpxForImport(source.path(), source.format());
    }

    @Test
    void nativeGpxDoesNotInvokeTheExternalConverterInEitherMode() {
        var converter = mock(TrackFileConverterService.class);
        var access = new TrackSourceAccess(mock(IndexerRepository.class), converter, "/tmp");
        byte[] bytes = "<gpx/>".getBytes(StandardCharsets.UTF_8);
        var source = new TrackSourceAccess.Source(new IndexedFile(), Path.of("/tmp/synthetic.gpx"), bytes,
                GpxDocument.hash(bytes), SupportedTrackFormat.GPX);

        assertThat(access.gpx(source)).isSameAs(bytes);
        assertThat(access.gpxForImport(source)).isSameAs(bytes);
        verifyNoInteractions(converter);
    }
}
