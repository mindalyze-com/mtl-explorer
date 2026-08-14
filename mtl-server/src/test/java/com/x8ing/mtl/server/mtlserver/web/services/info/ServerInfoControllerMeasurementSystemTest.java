package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.measurement.MeasurementSystem;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServerInfoControllerMeasurementSystemTest {

    @Test
    void exposesTheConfiguredDefaultMeasurementSystem() {
        var properties = new MtlAppProperties();
        properties.setDefaultMeasurementSystem(MeasurementSystem.US_CUSTOMARY);
        var controller = new ServerInfoController(
                Optional.empty(),
                mock(SystemLogService.class),
                properties,
                mock(DockerImageInfoService.class),
                mock(ServerIdentityService.class));

        BuildInfoResponse response = controller.getBuild();

        assertThat(response.defaultMeasurementSystem()).isEqualTo(MeasurementSystem.US_CUSTOMARY);
    }
}
