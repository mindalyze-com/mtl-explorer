package com.x8ing.mtl.server.mtlserver.energy;

import lombok.Builder;
import lombok.Data;

/**
 * Breakdown of physical/mechanical energy for a single track segment (between two consecutive GPS points).
 * All values in Joules. Positive values = energy that must be supplied (work done).
 * Negative gravitational = potential energy released on descent (informational).
 * Negative kinetic = deceleration energy dissipated (braking).
 */
@Data
@Builder
public class EnergyComponents {

    public static final EnergyComponents ZERO = EnergyComponents.builder().build();

    /**
     * Work against gravity: m·g·Δh. Positive on ascent, negative on descent.
     */
    @Builder.Default
    private double gravitationalJoules = 0;

    /**
     * Work against aerodynamic drag: ½·Cd·A·ρ·v²·d
     */
    @Builder.Default
    private double aeroDragJoules = 0;

    /**
     * Work against rolling/surface friction: Cr·m·g·d
     */
    @Builder.Default
    private double rollingResistanceJoules = 0;

    /**
     * Change in kinetic energy: ½·m·(v₂²−v₁²). Positive = acceleration, negative = deceleration.
     */
    @Builder.Default
    private double kineticJoules = 0;

    /**
     * Net forward work for this segment — the mechanical energy that must actually be supplied.
     * Computes the algebraic sum of all components: gravity (negative on descent offsets
     * drag and friction) + aero drag + rolling resistance + kinetic change.
     * If the net sum is negative (e.g. coasting downhill faster than losses), returns 0
     * because no pedaling/pushing work is required (braking energy is dissipated as heat).
     */
    public double totalPositiveJoules() {
        double netWork = gravitationalJoules + aeroDragJoules + rollingResistanceJoules + kineticJoules;
        return Math.max(0, netWork);
    }
}
