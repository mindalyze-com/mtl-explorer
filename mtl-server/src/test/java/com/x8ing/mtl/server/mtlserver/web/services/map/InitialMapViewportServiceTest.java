package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackBounds;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialMapViewportServiceTest {

    @Test
    void configuredBoundsTakePrecedenceOverTrackBounds() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        InitialMapViewportService service = new InitialMapViewportService(repository);
        MapServerProperties properties = new MapServerProperties();
        MapBoundsDto configuredBounds = new MapBoundsDto(7.1, 46.2, 7.4, 46.5);
        properties.setInitialBounds(configuredBounds);

        MapBoundsDto bounds = service.resolve(properties);

        assertThat(bounds).usingRecursiveComparison().isEqualTo(configuredBounds);
        verify(repository, never()).findImportedTrackBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void trackBoundsAreUsedWhenNoInitialBoundsAreConfigured() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        when(repository.findImportedTrackBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TestTrackBounds(8.4, 47.2, 8.8, 47.6));
        InitialMapViewportService service = new InitialMapViewportService(repository);

        MapBoundsDto bounds = service.resolve(new MapServerProperties());

        assertThat(bounds).usingRecursiveComparison()
                .isEqualTo(new MapBoundsDto(8.4, 47.2, 8.8, 47.6));
    }

    @Test
    void emptyTrackBoundsFallBackToDefaultBounds() {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        when(repository.findImportedTrackBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new TestTrackBounds(null, null, null, null));
        InitialMapViewportService service = new InitialMapViewportService(repository);

        MapBoundsDto bounds = service.resolve(new MapServerProperties());

        assertThat(bounds.getMinLng()).isCloseTo(7.86635, within(0.0000001));
        assertThat(bounds.getMinLat()).isCloseTo(46.88048, within(0.0000001));
        assertThat(bounds.getMaxLng()).isCloseTo(8.74635, within(0.0000001));
        assertThat(bounds.getMaxLat()).isCloseTo(47.22048, within(0.0000001));
    }

    private record TestTrackBounds(Double minLng, Double minLat, Double maxLng, Double maxLat)
            implements GpsTrackBounds {

        @Override
        public Double getMinLng() {
            return minLng;
        }

        @Override
        public Double getMinLat() {
            return minLat;
        }

        @Override
        public Double getMaxLng() {
            return maxLng;
        }

        @Override
        public Double getMaxLat() {
            return maxLat;
        }
    }
}
