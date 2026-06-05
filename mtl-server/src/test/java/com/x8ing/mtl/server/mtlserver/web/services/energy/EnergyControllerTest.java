package com.x8ing.mtl.server.mtlserver.web.services.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.energy.EnergyParameters;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnergyControllerTest {

    @Test
    void saveTrackRiderWeight_recalculatesCurrentTrackWithRequestedWeight() {
        GpsTrackRepository gpsTrackRepository = mock(GpsTrackRepository.class);
        GpsTrackDataPointRepository gpsTrackDataPointRepository = mock(GpsTrackDataPointRepository.class);
        EnergyService energyService = mock(EnergyService.class);
        EnergyController controller = new EnergyController(gpsTrackRepository, gpsTrackDataPointRepository, energyService);

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
        EnergyController controller = new EnergyController(gpsTrackRepository, gpsTrackDataPointRepository, energyService);

        assertThrows(ResponseStatusException.class, () -> controller.saveTrackRiderWeight(42L, 12.0));

        verify(energyService, never()).recalculateEnergyForTrack(any(), any());
    }
}
