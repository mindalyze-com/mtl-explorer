package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyRecalculationQueue;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EnergyControllerTest {

    @Test
    void unsupportedTimingRejectsAdHocAndWeightScenariosBeforeReadingPoints() {
        var tracks = mock(GpsTrackRepository.class);
        var points = mock(GpsTrackDataPointRepository.class);
        var energy = mock(EnergyService.class);
        var controller = new EnergyController(tracks, points, energy, mock(EnergyRecalculationQueue.class));
        var track = new GpsTrack();
        track.setTimingUnreliable(true);
        when(tracks.findById(42L)).thenReturn(Optional.of(track));
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> controller.calculateEnergy(42L, null, null, java.math.BigDecimal.ZERO, "RAW_OUTLIER_CLEANED")).getStatusCode().value());
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> controller.calculateEnergyWhatIf(42L, 75.0)).getStatusCode().value());
        org.mockito.Mockito.verifyNoInteractions(points, energy);
    }

    @Test
    void saveTrackRiderWeight_recalculatesCurrentTrackWithRequestedWeight() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackDataPointRepository gpsTrackDataPointRepository = mock(GpsTrackDataPointRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        EnergyController controller = new EnergyController(gpsTrackRepository, gpsTrackDataPointRepository, energyService, mock(EnergyRecalculationQueue.class));

        GpsTrack updatedTrack = new GpsTrack();
        updatedTrack.setId(42L);
        updatedTrack.setEnergyWeightKgUsed(92.0);

        when(energyService.getDefaultParameters()).thenReturn(EnergyParameters.builder().riderWeightKg(75.0).build());
        when(energyService.recalculateEnergyForTrack(eq(42L), any(EnergyParameters.class))).thenReturn(true);
        when(gpsTrackRepository.findById(42L)).thenReturn(Optional.of(updatedTrack));

        ResponseEntity<GpsTrack> response = controller.saveTrackRiderWeight(42L, 82.0);

        assertSame(updatedTrack, response.getBody());
        ArgumentCaptor<EnergyParameters> paramsCaptor = ArgumentCaptor.forClass(EnergyParameters.class);
        verify(energyService).recalculateEnergyForTrack(eq(42L), paramsCaptor.capture());
        assertEquals(82.0, paramsCaptor.getValue().getRiderWeightKg(), 0.001);
    }

    @Test
    void saveTrackRiderWeight_rejectsInvalidWeightBeforeRecalculation() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackDataPointRepository gpsTrackDataPointRepository = mock(GpsTrackDataPointRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        EnergyController controller = new EnergyController(gpsTrackRepository, gpsTrackDataPointRepository, energyService, mock(EnergyRecalculationQueue.class));

        assertThrows(ResponseStatusException.class, () -> controller.saveTrackRiderWeight(42L, 12.0));

        verify(energyService, never()).recalculateEnergyForTrack(any(), any());
    }

    @Test
    void previewUsesSharedSavedWeightWithoutInferringItFromNewActivityEquipment() {
        var tracks = mock(GpsTrackRepository.class);
        var points = mock(GpsTrackDataPointRepository.class);
        var energy = mock(EnergyService.class);
        var track = new GpsTrack();
        track.setId(42L);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.CAR);
        track.setEnergyWeightKgUsed(92.0);
        when(tracks.findById(42L)).thenReturn(Optional.of(track));
        when(energy.parametersForTrack(track)).thenReturn(EnergyParameters.builder().riderWeightKg(82).build());
        var summary = com.x8ing.mtl.server.mtlserver.energy.TrackEnergySummary.builder().build();
        when(energy.summaryFromTrack(track)).thenReturn(summary);
        when(energy.calculateSummaryWithoutPersisting(any(), eq(GpsTrack.ACTIVITY_TYPE.CAR), any())).thenReturn(summary);
        when(energy.subtractSummaries(summary, summary)).thenReturn(summary);
        var controller = new EnergyController(tracks, points, energy, mock(EnergyRecalculationQueue.class));
        var result = controller.calculateEnergyWhatIf(42L, null).getBody();
        assertEquals(82, result.baselineRiderWeightKg());
        assertEquals(82, result.requestedRiderWeightKg());
        var params = ArgumentCaptor.forClass(EnergyParameters.class);
        verify(energy).calculateSummaryWithoutPersisting(any(), eq(GpsTrack.ACTIVITY_TYPE.CAR), params.capture());
        assertEquals(82, params.getValue().getRiderWeightKg());
    }
}
