package com.x8ing.mtl.server.mtlserver.sourceissues;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceIssueControllerTest {
    @Test
    void acknowledgedSnapshotUsesTheCanonicalAuthenticatedAccountAndRequestedSource() {
        var repository = mock(SourceIssueRepository.class);
        var snapshot = new SourceIssueTargetsSnapshot(0, List.of(new SourceIssueTarget(17, "sha256:synthetic")));
        when(repository.targets("reviewer", "GPS", 3L, State.ACKNOWLEDGED)).thenReturn(snapshot);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("Reviewer", "", List.of()));
        try {
            assertSame(snapshot, new SourceIssueController(repository, new DemoWriteAccess(new MtlAppProperties())).targets("GPS", 3L, State.ACKNOWLEDGED));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void informationalAndResolvedStatesCannotSupplyMutationTargets() {
        var repository = mock(SourceIssueRepository.class);
        var controller = new SourceIssueController(repository, new DemoWriteAccess(new MtlAppProperties()));
        for (State state : List.of(State.INFORMATION, State.RESOLVED)) {
            var error = assertThrows(ResponseStatusException.class, () -> controller.targets(null, null, state));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(repository);
    }
}
