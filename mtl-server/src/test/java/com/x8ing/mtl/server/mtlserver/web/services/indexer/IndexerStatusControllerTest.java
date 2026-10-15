package com.x8ing.mtl.server.mtlserver.web.services.indexer;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class IndexerStatusControllerTest {
    @Test
    void normalizesTheIndexAndPassesPagingToTheService() {
        var service = mock(IndexerStatusService.class);
        var result = new IndexerStatusService.FileFailuresDto(List.of(), 0, 0, 25);
        when(service.getFileOutcomes("MEDIA", 0, 25, com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus.FAILED)).thenReturn(result);
        var controller = new IndexerStatusController(service, null, null, new DemoWriteAccess(new MtlAppProperties()));
        assertThat(controller.getFileFailures(" media ", 0, 25, com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus.FAILED)).isSameAs(result);
    }

    @Test
    void rejectsUnboundedOrInvalidPagesBeforeQueryingTheDatabase() {
        var service = mock(IndexerStatusService.class);
        var controller = new IndexerStatusController(service, null, null, new DemoWriteAccess(new MtlAppProperties()));
        for (int[] paging : List.of(new int[]{-1, 25}, new int[]{0, 0}, new int[]{0, 101})) {
            assertThatThrownBy(() -> controller.getFileFailures("GPS", paging[0], paging[1], com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile.IndexerStatus.FAILED))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(service);
    }
}
