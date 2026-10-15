package com.x8ing.mtl.server.mtlserver.testsupport;

import com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentReadScope;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Geometry tests own their source or rollback transaction; read-scope isolation has separate database tests.
 */
public final class SegmentReadTestSupport {
    private SegmentReadTestSupport() {
    }

    public static SegmentReadScope directReads() {
        var scope = mock(SegmentReadScope.class);
        directReads(scope);
        return scope;
    }

    public static void directReads(SegmentReadScope scope) {
        doAnswer(call -> call.<Supplier<?>>getArgument(0).get()).when(scope).read(any());
    }
}
