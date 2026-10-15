package com.x8ing.mtl.server.mtlserver.energy;

import com.x8ing.mtl.server.mtlserver.db.entity.config.ConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnergyWeightTest {
    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "34.9", "180.1", "bad", ""})
    void invalidConfigurationFallsBackToDefault(String value) {
        assertThat(service(value).getDefaultParameters().getRiderWeightKg()).isEqualTo(75);
    }

    @ParameterizedTest
    @ValueSource(strings = {"35", "82.5", "180"})
    void validConfigurationIncludesBoundaries(String value) {
        assertThat(service(value).getDefaultParameters().getRiderWeightKg()).isEqualTo(Double.parseDouble(value));
    }

    @Test
    void savedBodyWeightWinsOverMechanicalMassAfterActivityChange() {
        var service = service("75");
        var track = new GpsTrack();
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.CAR);
        track.setEnergyWeightKgUsed(92.0);
        var metabolic = new MetabolicEnergy();
        metabolic.setBodyWeightKg(82.0);
        track.setMetabolicEnergy(metabolic);
        assertThat(service.parametersForTrack(track).getRiderWeightKg()).isEqualTo(82);
        metabolic.setBodyWeightKg(Double.NaN);
        assertThat(service.parametersForTrack(track).getRiderWeightKg()).isEqualTo(75);
        track.setActivityType(GpsTrack.ACTIVITY_TYPE.BICYCLE);
        assertThat(service.parametersForTrack(track).getRiderWeightKg()).isEqualTo(82);
    }

    private EnergyService service(String value) {
        var config = mock(ConfigRepository.class);
        var entry = new ConfigEntity();
        entry.setValue(value);
        when(config.findConfigEntitiesByDomain1AndDomain2AndDomain3("energy", "user", "riderWeightKg")).thenReturn(List.of(entry));
        var factory = new EnergyCalculatorFactory(List.of(new com.x8ing.mtl.server.mtlserver.energy.impl.CarEnergyCalculator(),
                new com.x8ing.mtl.server.mtlserver.energy.impl.BicycleEnergyCalculator()));
        factory.init();
        return new EnergyService(factory, config, null, null, null);
    }
}
