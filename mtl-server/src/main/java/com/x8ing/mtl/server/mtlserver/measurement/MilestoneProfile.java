package com.x8ing.mtl.server.mtlserver.measurement;

import java.util.List;

/**
 * Ordered milestone definitions for one measurement system.
 */
public record MilestoneProfile(List<MilestoneDefinition> definitions) {

    public MilestoneProfile {
        definitions = List.copyOf(definitions);
    }
}
