package com.x8ing.mtl.server.mtlserver.logic.crossing.beans;

/**
 * Represents the state of a track relative to a trigger point.
 */
public enum TriggerState {
    /**
     * The track is currently outside the trigger point's radius
     */
    OUTSIDE,

    /**
     * The track is currently inside the trigger point's radius
     */
    INSIDE
}
